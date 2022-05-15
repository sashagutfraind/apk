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
    reporting_frequency = time_between_bleeds/365

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
    agent_vaccination_doses = vaccinations.groupby(['Agent']).size().to_dict()
    
    agent_abpos_at_enrollment = {}
    agent_vaccination_time = events2[(events2.Event == 'vaccinated')].groupby(['Agent'])['Time'].first().to_dict()
    agent_vaccination_time = {a: float(v) for a, v in agent_vaccination_time.items()}
    for event in events2.itertuples():
        if float(event.Time) < agent_vaccination_time[event.Agent]:
            agent_abpos_at_enrollment[event.Agent] = 'abneg' if event.HCV == 'susceptible' else 'abpos'

    agent_abpos_at_enrollment = {}
    agent_vaccination_time = events2[(events2.Event == 'vaccinated')].groupby(['Agent'])['Time'].first().to_dict()
    agent_vaccination_time = {a: float(v) for a, v in agent_vaccination_time.items()}
    for event in events2.itertuples():
        if float(event.Time) < agent_vaccination_time[event.Agent]:
            agent_abpos_at_enrollment[event.Agent] = 'abneg' if event.HCV == 'susceptible' else 'abpos'
            
    for agent in agent_baselines:
        agent_baselines[agent]['ABpos_at_start_of_trial'] = agent_abpos_at_enrollment[agent]
        agent_baselines[agent]['doses_received'] = agent_vaccination_doses[agent]

    ever_infected = events2[(events2.Time <= vaccine_trial_end_date ) & (events2.Event == 'infected')]
    infected_roster = ever_infected.groupby(['Agent']).size().to_dict()

    events3 = events2[~events2.Event.isin(['activated','vaccinated'])]
 
    agent_events = {}
    longest_history = 0 #needed for padding the data
    for agent in agent_baselines:
        a_events = events3[events3.Agent == agent][['Event','HCV','Info','Time']]
        a_events = a_events[a_events.Time <= vaccine_trial_end_date]
        a_events = a_events.sort_values(by="Time") #just in case
        a_events = a_events.append(pd.DataFrame([['fieldworkcompleted', '-', vaccine_trial_end_date]], columns=list(['Event','HCV', 'Time'])))
        a_events["StepsFromEnrollmentStart"] = (a_events.Time - agent_baselines[agent]["time_entered_trial"])/reporting_frequency
        agent_events[agent] = a_events
        if len(a_events[a_events.Event=='enteredfollowup']) > 0:
            longest_history = max(longest_history, a_events.Time.iloc[-1]-a_events[a_events.Event=='enteredfollowup'].Time.iloc[0])  
                            #min(vaccine_trial_end_date-a_events.Time.iloc[0], 
                            #    a_events.Time.iloc[-1]-a_events.Time.iloc[0]))

    longest_history_len = longest_history/reporting_frequency


    '''
    produce a sequence [+,-,-,...,lost,lost,...,padding,padding,..]
    the history records all infection episodes through the end of the trial or loss with padding
    '''
    def estimate_history(agent_id, my_events, target_length):
        h = [] #test the correct count [current_hcv]
        last_hcv_reading = None
        final_status = 'padding'
        if my_events.iloc[0].Event != 'trialstarted':
            print(f'WARNING: history for agent {agent_id} does not contain trialstarted event')
            return h, 'Unknown'
        for new_event in my_events.itertuples():
            if new_event.Event in frozenset(['enteredfollowup','infollowup','infollowup2']): #involve bleeding
                last_hcv_reading = 'RNA_positive' if 'RNA=true' in new_event.Info else 'RNA_negative'
                h = h + [last_hcv_reading]
            elif new_event.Event in frozenset(['trialabandoned', 'deactivated', 'fieldworkcompleted']):
                final_status = 'lost_to_followup'
                h = h + [final_status]
                break
            elif new_event.Event in frozenset(['trialcompleted']): #does not involve bleeding (19th event, normally)
                break
            elif new_event.Event in frozenset(['trialstarted', 'infected', 'infectious', 'recovered', 'chronic']):
                continue
            else:
                print(f'WARNING: history for agent {agent_id} has unknown event {new_event.Event}')
        
        h = h + [final_status]*int(target_length-len(h))
        
        #pdb.set_trace()
        return h, last_hcv_reading

    agent_histories = {}
    for agent, a_events in agent_events.items():
        regular_history, last_hcv_reading = estimate_history(agent, 
                                                            a_events, 
                                                            longest_history_len)
        if regular_history == []: #very rare - e.g. rarely first event is "deactivated" (bug in APK)
            continue
        
        agent_baselines[agent]['notinfected_and_abpos_at_final_reading']   = \
                (agent_baselines[agent]['trial_arm'] == 'study' and last_hcv_reading == 'RNA_negative') or \
                (agent_baselines[agent]['trial_arm'] == 'placebo' and (agent in infected_roster) and last_hcv_reading == 'RNA_negative')
        agent_histories[agent] = agent_baselines[agent].append(pd.Series(regular_history))

    output_df = pd.DataFrame.from_dict(agent_histories, orient='columns')


    output_df = output_df.transpose()

    output_df.to_csv(output_fpath)

