""" Perform analysis on the APK model output events data.
"""

import os
import numpy as np
import pandas as pd

time_between_bleeds = 28
vaccine_trial_end_date_days = 1400

def parse_events(experiment_dir):
    print('Events processing folder ' + experiment_dir)

    run_output_dir = f'{experiment_dir}/output'
    events_fpath = f'{run_output_dir}/events.csv'
    run_number = experiment_dir.split('instance_')[-1]
    output_fpath = f'{run_output_dir}/events_history_{run_number}.csv'

    vaccine_trial_end_date = vaccine_trial_end_date_days/365.0 #external default

    # print("Reading events from: " + events_fpath)
    # print(f"Time between simulated bleeds (days): {time_between_bleeds}")
    # print(f"vaccine_trial_end_date (years): {vaccine_trial_end_date}")
    reporting_frequency = time_between_bleeds/365

    # print(f"Output file: {output_fpath}")

    trial_header = pd.read_csv(events_fpath, nrows=2, header=1)

    burn_in_days = trial_header.burn_in_days[0]
    vaccine_schedule = trial_header.vaccine_schedule[0]
    vaccine_enrollment_launch_day = trial_header.vaccine_enrollment_launch_day

    # print(f'vaccine_schedule: {vaccine_schedule}')

    events = pd.read_csv(events_fpath, header=4)
    events.rename(columns={'Num Buddies':'total_network_size'}, inplace=True)

    trial_starts = events[events.Event == 'trialstarted'].copy()
    trial_starts['RNA_at_start_of_trial'] = trial_starts.Info.str.contains('RNA=true')
    trial_starts = trial_starts.drop(columns=['EC','L1','L2','N1','N2','C','Info','Event','DBLabel','HCV'])
    trial_starts = trial_starts.rename(columns={'Time':'time_entered_trial'})

    agent_baselines = {}
    for row in trial_starts.iterrows():
        agent_data = row[1]
        agent = agent_data.Agent
        agent_baselines[agent] = agent_data

    events2 = events[events.Agent.isin(agent_baselines)]

    vaccinations = events2[(events2.Time <= vaccine_trial_end_date ) & (events2.Event == 'vaccinated')]
    agent_vaccinations = vaccinations.groupby(['Agent']).size().to_dict()

    ever_infected = events2[(events2.Time <= vaccine_trial_end_date ) & (events2.Event == 'infected')]
    infected_roster = ever_infected.groupby(['Agent']).size().to_dict()

    #for agents with infections, count of the number of infections

    #ever_recovered = events2[(events2.Time <= vaccine_trial_end_date ) & (events2.Event == 'recovered')]
    #recovered_count = ever_recovered.groupby(['Agent']).size().to_dict()

    #the following events do not provide new information about the RNA state or the censorship, so we drop them
    # if the agent becomes infected or recovered, we will retrieve it from "infected" and "recovered" events below
    #  remove to simplify the parsing
    # added infectious and chronic for safety's sake
    events2 = events2[~events2.Event.isin
                    (['activated','enteredfollowup','infollowup','infollowup2','vaccinated'])]

    agent_events = {}
    longest_history = 0 #needed for padding the data
    for agent in agent_baselines:
        a_events = events2[events2.Agent == agent][['Event','HCV','Time']]
        a_events = a_events[a_events.Time <= vaccine_trial_end_date]
        a_events = a_events.sort_values(by="Time") #just in case
        a_events = a_events.append(pd.DataFrame([['fieldworkcompleted', '-', vaccine_trial_end_date]], columns=list(['Event','HCV', 'Time'])))
        a_events["StepsFromEnrollmentStart"] = (a_events.Time - agent_baselines[agent]["time_entered_trial"])/reporting_frequency
        agent_events[agent] = a_events
        longest_history = max(longest_history, a_events.Time.iloc[-1]-a_events.Time.iloc[0])  
                            #min(vaccine_trial_end_date-a_events.Time.iloc[0], 
                            #    a_events.Time.iloc[-1]-a_events.Time.iloc[0]))

    longest_history_len = longest_history/reporting_frequency

    for agent in agent_events:
        agent_baselines[agent]['doses_received'] = agent_vaccinations[agent]


    #print(longest_history)
    #longest_history_len = longest_history/reporting_frequency
    #print(longest_history_len)

    #for agent, a_events in agent_events.items():
    #    enrollment_start = agent_events[agent].Time.iloc[0]
    #    if a_events['Time'].iloc[-1] < longest_history:
    #        agent_events[agent] = agent_events[agent].append(pd.DataFrame([['padding', 'padding', longest_history]], columns=list(['Event','HCV', 'Time'])))
    #    agent_events[agent].sort_values(by="Time", inplace=True)


    '''
    produce a sequence [+,-,-,...,lost,lost,...,padding,padding,..]
    the first status is the RNA at the start of the trial
    the history records all infection episodes through the end of the trial or loss 
    '''
    def estimate_history(agent_id, my_events, RNA_positive_at_start_of_trial, target_length):
        current_hcv = 'RNA_positive' if RNA_positive_at_start_of_trial else 'RNA_negative'
        last_hcv_reading = current_hcv #last known hcv status
        in_tracking = True
        h = [] #test the correct count [current_hcv]
        #assert my_events.shape[0]>1 #at least one event in history
        if my_events.shape[0]==0:
            print(f'WARNING: history for agent {agent_id} contains just one event')
            return h, last_hcv_reading
        for i, new_event in enumerate(my_events.Event):
            if i == 0:
                if new_event != 'trialstarted':
                    return [], None
                continue
            full_steps_to_new = int(my_events.StepsFromEnrollmentStart.iloc[i] -                                 my_events.StepsFromEnrollmentStart.iloc[i-1])
            h = h + [current_hcv] * full_steps_to_new
            
            #full_steps_to_new might be less than 1, in which case we will add nothing, but the state will change
            if new_event in frozenset(['infected', 'infectious', 'chronic']) and in_tracking:
                current_hcv = 'RNA_positive'
                last_hcv_reading = current_hcv
            elif new_event == 'recovered' and in_tracking:
                current_hcv = 'RNA_negative'
                last_hcv_reading = current_hcv
            elif new_event in frozenset(['trialabandoned', 'deactivated']):
                current_hcv = 'lost_to_followup'
                in_tracking = False
            elif new_event in frozenset(['trialcompleted', 'fieldworkcompleted']): 
                break
            elif not in_tracking:
                continue
            else:
                print(new_event)
                if my_events.shape[0]==0:
                    print(f'WARNING: history for agent {agent_id} has unknown event {new_event}')
                    return h, last_hcv_reading
                #assert False
            #invariant: h contains all the records up to my_events.StepsFrom[i]. current_hcv is the new state
        
        h = h + ['padding']*int(target_length-len(h))
        
        #pdb.set_trace()
        #print(len(h))
        return h, last_hcv_reading

    agent_histories = {}
    for agent, a_events in agent_events.items():
        regular_history, last_hcv_reading = estimate_history(agent, 
                                                            a_events, 
                                                            agent_baselines[agent]['RNA_at_start_of_trial'],
                                                            longest_history_len)
        if regular_history == []: #very rare - e.g. rarely first event is "deactivated" (bug in APK)
            continue
        
        agent_baselines[agent]['nonchronic_abpos_at_final_reading']   =                (agent_baselines[agent]['trial_arm'] == 'study' and last_hcv_reading == 'RNA_negative') or                (agent_baselines[agent]['trial_arm'] == 'placebo' and (agent in infected_roster) and last_hcv_reading == 'RNA_negative')
        #print(agent_baselines[agent]['nonchronic_abpos_at_final_reading'])
        #print(agent_baselines[agent]['trial_arm'])
        #print(agent in infected_count)
        #print(last_hcv_reading)
        
        agent_histories[agent] = agent_baselines[agent].append(pd.Series(regular_history))


    # print('total agents parsed: %d'%len(agent_histories))

    output_df = pd.DataFrame.from_dict(agent_histories, orient='columns')


    #agent_0 = agent_histories.keys()[0]
    #output_df.set_index(agent_baselines[agent_0].keys() + range(longest_history), inplace=True)

    output_df = output_df.transpose()

    output_df.to_csv(output_fpath)

    # print(f'Written: {output_fpath}')
