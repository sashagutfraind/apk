
# ## Analyze trial results

import pandas as pd
import os, sys
import os.path
import numpy as np
import scipy
import scipy.stats
import matplotlib.pyplot as plt
from IPython.display import HTML, display
import tabulate, csv

def trial_analysis(experiment_dir):
	print('Processing folder ' + experiment_dir)
	file_signature = 'populations.csv'

	run_number = experiment_dir.split('instance_')[-1]	
	
	run_output_dir = experiment_dir + '/output'
	
	trial_end_date = int(365*3.5)-200 #the last normal recruit completes on day 958 
	pops_file = [s for s in os.listdir(run_output_dir) if s.find(file_signature)>-1][0]
	
	# ### Study details
	header_output = pd.read_csv(os.path.join(run_output_dir, pops_file), nrows=1, header=1)
	header_output
#	print(header_output[['vaccine_schedule', 'initial_pwid_count', 'vaccine_study_arm_n', 'vaccine_annual_loss', 'vaccine_dose2_day', 'vaccine_dose3_day', 'vaccine_enrollment_duration_days', 'vaccine_followup_purge_with_rna', 'vaccine_enrollment_launch_day', 'vaccine_enrollment_probability_positiveinnetwork', 'vaccine_enrollment_probability_unbiased', 'vaccine_followup1_periods', 'vaccine_followup2_periods', 'vaccine_followup_weeks']])

	# ### Populations
	pops_output = pd.read_csv(os.path.join(run_output_dir, pops_file), header=3)
	pops_output.columns

	# ### Infection status
	infections = pops_output[['Tick', 'BurnInMode', 'ABpreval_ALL', 'RNApreval_ALL']]

	tmp = infections.Tick/365
	infections["year"] = tmp

	# ### Total population
	simulated_population = pops_output[['Tick', 'population_ALL']]
  
	tmp2 = simulated_population.Tick/365
	simulated_population["year"] = tmp2

	simulated_population.iloc[[0,1000,2000,-1]]

	# ### Total recruited (i.e. started trials)
	started_pops_fields = ['started_study_aggregate_vaccine', 'started_placebo_aggregate_vaccine']
	started_pops=np.array(pops_output.loc[trial_end_date, started_pops_fields])
	pops_output.loc[trial_end_date, ['Tick'] + started_pops_fields]

	# ### Total recruited (i.e. completed doses)
	enrolled_pops_fields = ['recr_study_aggregate_vaccine', 'recr_placebo_aggregate_vaccine']
	enrolled_pops=np.array(pops_output.loc[trial_end_date, enrolled_pops_fields])
	pops_output.loc[trial_end_date, ['Tick'] + enrolled_pops_fields]

	# ### Still in follow-up (should be small)

	active_pops_fields = ['vaccinetrial_VaccineArm=study', 'vaccinetrial_VaccineArm=placebo']
	active_pops=np.array(pops_output.loc[trial_end_date, active_pops_fields])
	pops_output.loc[trial_end_date, ['Tick'] + active_pops_fields]

	# ### Infection status
	## note: cmpl_ variables are accumulators inside the simulation, and are valid even if individuals leave the simulation

	# #### chronic
	chronic_pops=np.array(pops_output.loc[trial_end_date, ['cmpl_study_chronic_aggregate_vaccine', 'cmpl_placebo_chronic_aggregate_vaccine']])
	pops_output.loc[trial_end_date, ['Tick', 'cmpl_study_chronic_aggregate_vaccine', 'cmpl_placebo_chronic_aggregate_vaccine']]

	# #### not chronic	
	nonchronic_pops=np.array(pops_output.loc[trial_end_date, ['cmpl_study_notchronic_aggregate_vaccine', 'cmpl_placebo_notchronic_aggregate_vaccine']])
	pops_output.loc[trial_end_date, ['Tick', 'cmpl_study_notchronic_aggregate_vaccine', 'cmpl_placebo_notchronic_aggregate_vaccine']]

	# #### infected before completing doses (chronic or not)

	incompl_inf_pops=np.array(pops_output.loc[trial_end_date, ['incompl_infected_study_aggregate_vaccine', 'incompl_infected_placebo_aggregate_vaccine']])
	pops_output.loc[trial_end_date, ['Tick', 'incompl_infected_study_aggregate_vaccine', 'incompl_infected_placebo_aggregate_vaccine']]

	# ### Workload

	#TODO = monthly work by category: recuitment, follow-up, follow-up2
	pops_output['Month'] = pops_output['Tick'] / 30
	pops_output['Month'] = round(pops_output['Month'] + 1)
	#pops_output['Month']

	#TODO: we need daily statistics in APK for totals by stage

	# ### Recruitment and Drop out
	recruited = pops_output[['Tick', 'started_study_aggregate_vaccine', 'started_study_aggregate_vaccine']]
	recruited.columns = ['day', 'study_total', 'placebo_total']
	recruited.set_index('day')
	recruited = recruited.iloc[0:600]

	dropouts = pops_output[['Tick', 'quit_study_aggregate_vaccine', 'quit_placebo_aggregate_vaccine']]
	dropouts.columns = ['day', 'study_total_quit', 'placebo_total_quit']
	dropouts.set_index('day');

	d_study = dropouts.iloc[trial_end_date]['study_total_quit'] / pops_output.loc[trial_end_date]['started_study_aggregate_vaccine']
	d_study

	d_placebo = dropouts.iloc[trial_end_date]['placebo_total_quit'] / pops_output.loc[trial_end_date]['started_placebo_aggregate_vaccine']
	d_placebo

	dropouts = dropouts.iloc[0:600]

	ddropouts = dropouts.diff()
	ddropouts.drop(['day'], axis=1, inplace=True)
	ddropouts.set_index(dropouts['day'])
	ddropouts.columns = ['study', 'placebo']

	dropouts.columns = ['day', 'study', 'placebo']

	# ### Total completed trial per protocol
	total_completed = chronic_pops + nonchronic_pops
#	print('Study: %.0f, Placebo: %.0f'%(total_completed[0], total_completed[1]))

	# ### Results

	trial_contingency_table = np.reshape(np.concatenate((nonchronic_pops, chronic_pops), axis=0), newshape=(2,2))

#	print('Chi-square')
	chi2, pvalchisq, dof, expected = scipy.stats.chi2_contingency(trial_contingency_table)

#	print('Fisher Exact')
	fisher, fisherpval = scipy.stats.fisher_exact(trial_contingency_table)
	fisherpval

	# ## Vaccine efficacy:
	# $$ VE_𝑎 = 1 − 𝑎_{vaccine}/𝑎_{placebo} $$

	# In our case, we are interested in the prevention of chronic infection.  Therefore, we calculate:
	# $a_{vaccine} = $(HCV chronic vaccinees) / (total vaccinees)
	# 
	# and similarly for the placebo group

	a_study = (chronic_pops[0] / total_completed[0])
	a_study

	a_placebo = (chronic_pops[1] / total_completed[1])
	a_placebo

	VE = 1 - (a_study/a_placebo)

	# ## Summary of results

	summarytable = [["RESULTS (day=%d)"%(trial_end_date,),"Study","Placebo"],
         ['Recruited'] + ['%d'%(x,) for x in started_pops],
         ['Received all doses'] + ['%d'%(x,) for x in enrolled_pops],
         ['Infected before final']  + ['%d'%(x,) for x in incompl_inf_pops],
         ['Dropout rate']  + ['%.0f%%'%(d_study*100), '%.0f%%'%(d_placebo*100)],
         ['Still in follow-up at day %d'%(trial_end_date)] + ['%d'%(x,) for x in active_pops], 
         ["    Chronic"] + ['%d'%(x,) for x in chronic_pops],
         ["    Not chronic"] + ['%d'%(x,) for x in nonchronic_pops],
         ["Attack rate"] + ['%.1f%%'%(a_study*100), '%.1f%%'%(a_placebo*100)],
         ["VE"] + ["%.2f%%"%(100*VE), ""],
         ["Fishers exact pval", "%.6f"%fisherpval,""]
        ]
#	display(HTML(tabulate.tabulate(summarytable, tablefmt='html')))


	with open(run_output_dir+'/summarytable_'+run_number+'.csv', 'w') as csvfile:
		writer = csv.writer(csvfile)
		writer.writerow(header_output)
		writer.writerow(header_output.iloc[0])
		[writer.writerow(r) for r in summarytable]

#	print('Fisher\'s exact test gives p=%.6f, which is %s.  The estimated vaccine efficacy is %.1f%%'%(fisher, 'statistically significant' if fisherpval<0.05 else 'not statistically significant', 100*VE,))

	return