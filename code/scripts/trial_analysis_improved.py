
# coding: utf-8

# ## Analyze trial results

# In[50]:

import pandas as pd
import os, sys
import os.path
import numpy as np
import scipy
import scipy.stats
import matplotlib.pyplot as plt
from IPython.display import HTML, display
import tabulate, csv
#get_ipython().magic('matplotlib inline')


# In[3]:

file_signature = 'populations.csv'


# In[4]:

#run_output_dir = get_ipython().getoutput('cd')
#run_output_dir = run_output_dir[0]
run_output_dir = os.getcwd() 


# In[5]:

trial_end_date = int(365*3.5)-200 #the last normal recruit completes on day 958 


# In[6]:

pops_file = [s for s in os.listdir(run_output_dir) if s.find(file_signature)>-1][0]


# ### Study details

# In[7]:

header_output = pd.read_csv(os.path.join(run_output_dir, pops_file), nrows=1, header=1)
print(header_output[['vaccine_schedule', 'initial_pwid_count', 'vaccine_study_arm_n', 'vaccine_annual_loss', 'vaccine_dose2_day', 'vaccine_dose3_day', 'vaccine_enrollment_duration_days', 'vaccine_followup_purge_with_rna', 'vaccine_enrollment_launch_day', 'vaccine_enrollment_probability_positiveinnetwork', 'vaccine_enrollment_probability_unbiased', 'vaccine_followup1_periods', 'vaccine_followup2_periods', 'vaccine_followup_weeks']
])


# ### Populations

# In[8]:

pops_output = pd.read_csv(os.path.join(run_output_dir, pops_file), header=3)


# In[9]:

pops_output.columns


# ### Infection status

# In[10]:

infections = pops_output[['Tick', 'BurnInMode', 'ABpreval_ALL', 'RNApreval_ALL']]


# In[11]:

tmp = infections.Tick/365
infections["year"] = tmp


# In[12]:

#infections.plot(x="year", y=['BurnInMode', 'ABpreval_ALL', 'RNApreval_ALL'])


# ### Total population

# In[13]:

simulated_population = pops_output[['Tick', 'population_ALL']]


# In[14]:

tmp2 = simulated_population.Tick/365
simulated_population["year"] = tmp2


# In[15]:

simulated_population.iloc[[0,1000,2000,-1]]


# In[16]:

#simulated_population.plot(x="year", y="population_ALL")


# ### Total recruited (i.e. started trials)

# In[17]:

started_pops_fields = ['started_study_aggregate_vaccine', 'started_placebo_aggregate_vaccine']
started_pops=np.array(pops_output.loc[trial_end_date, started_pops_fields])
pops_output.loc[trial_end_date, ['Tick'] + started_pops_fields]


# ### Total recruited (i.e. completed doses)

# In[18]:

enrolled_pops_fields = ['recr_study_aggregate_vaccine', 'recr_placebo_aggregate_vaccine']
enrolled_pops=np.array(pops_output.loc[trial_end_date, enrolled_pops_fields])
pops_output.loc[trial_end_date, ['Tick'] + enrolled_pops_fields]


# ### Still in follow-up (should be small)

# In[19]:

active_pops_fields = ['vaccinetrial_VaccineArm=study', 'vaccinetrial_VaccineArm=placebo']
active_pops=np.array(pops_output.loc[trial_end_date, active_pops_fields])
pops_output.loc[trial_end_date, ['Tick'] + active_pops_fields]


# ### Infection status

# In[20]:

## note: cmpl_ variables are accumulators inside the simulation, and are valid even if individuals leave the simulation


# #### chronic

# In[21]:

chronic_pops=np.array(pops_output.loc[trial_end_date, ['cmpl_study_chronic_aggregate_vaccine', 'cmpl_placebo_chronic_aggregate_vaccine']])
pops_output.loc[trial_end_date, ['Tick', 'cmpl_study_chronic_aggregate_vaccine', 'cmpl_placebo_chronic_aggregate_vaccine']]


# #### not chronic

# In[22]:

nonchronic_pops=np.array(pops_output.loc[trial_end_date, ['cmpl_study_notchronic_aggregate_vaccine', 'cmpl_placebo_notchronic_aggregate_vaccine']])
pops_output.loc[trial_end_date, ['Tick', 'cmpl_study_notchronic_aggregate_vaccine', 'cmpl_placebo_notchronic_aggregate_vaccine']]


# #### infected before completing doses (chronic or not)

# In[23]:

incompl_inf_pops=np.array(pops_output.loc[trial_end_date, ['incompl_infected_study_aggregate_vaccine', 'incompl_infected_placebo_aggregate_vaccine']])
pops_output.loc[trial_end_date, ['Tick', 'incompl_infected_study_aggregate_vaccine', 'incompl_infected_placebo_aggregate_vaccine']]


# ### Workload

# In[24]:

#TODO = monthly work by category: recuitment, follow-up, follow-up2
pops_output['Month'] = pops_output['Tick'] / 30
pops_output['Month'] = round(pops_output['Month'] + 1)
#pops_output['Month']


# In[25]:

#TODO: we need daily statistics in APK for totals by stage


# ### Recruitment and Drop out

# In[26]:

recruited = pops_output[['Tick', 'started_study_aggregate_vaccine', 'started_study_aggregate_vaccine']]
recruited.columns = ['day', 'study_total', 'placebo_total']
recruited.set_index('day')
recruited = recruited.iloc[0:600]


# In[27]:

#ax=recruited.plot(y='study_total', x='day')
#recruited.plot(y='placebo_total', x='day', ax=ax)
#plt.show()


# In[28]:

dropouts = pops_output[['Tick', 'quit_study_aggregate_vaccine', 'quit_placebo_aggregate_vaccine']]
dropouts.columns = ['day', 'study_total_quit', 'placebo_total_quit']
dropouts.set_index('day');


# In[29]:

d_study = dropouts.iloc[trial_end_date]['study_total_quit'] / pops_output.loc[trial_end_date]['started_study_aggregate_vaccine']
d_study


# In[30]:

d_placebo = dropouts.iloc[trial_end_date]['placebo_total_quit'] / pops_output.loc[trial_end_date]['started_placebo_aggregate_vaccine']
d_placebo


# In[31]:

dropouts = dropouts.iloc[0:600]


# In[32]:

ddropouts = dropouts.diff()
ddropouts.drop(['day'], axis=1, inplace=True)
ddropouts.set_index(dropouts['day'])
ddropouts.columns = ['study', 'placebo']


# In[33]:

dropouts.columns = ['day', 'study', 'placebo']
#ax=dropouts.plot(y='study', x='day', legend='study')
#dropouts.plot(y='placebo', x='day', ax=ax, legend='placebo')
#plt.show()


# In[34]:

#fig = dropouts.plot('quit_study_aggregate_vaccine')
#ax=ddropouts.plot(y='study')
#ddropouts.plot(y='placebo', ax=ax)
#plt.show()


# In[35]:

#dropouts.plot()


# ### Total completed trial per protocol

# In[36]:

total_completed = chronic_pops + nonchronic_pops
print('Study: %.0f, Placebo: %.0f'%(total_completed[0], total_completed[1]))


# ### Results

# In[37]:

trial_contingency_table = np.reshape(np.concatenate((nonchronic_pops, chronic_pops), axis=0), newshape=(2,2))


# In[38]:

print('Chi-square')
chi2, pvalchisq, dof, expected = scipy.stats.chi2_contingency(trial_contingency_table)


# In[41]:

print('Fisher Exact')
fisher, fisherpval = scipy.stats.fisher_exact(trial_contingency_table)
fisherpval


# ## Vaccine efficacy:
# $$ VE_𝑎 = 1 − 𝑎_{vaccine}/𝑎_{placebo} $$

# In our case, we are interested in the prevention of chronic infection.  Therefore, we calculate:
# $a_{vaccine} = $(HCV chronic vaccinees) / (total vaccinees)
# 
# and similarly for the placebo group

# In[42]:

a_study = (chronic_pops[0] / total_completed[0])
a_study


# In[43]:

a_placebo = (chronic_pops[1] / total_completed[1])
a_placebo


# In[44]:

VE = 1 - (a_study/a_placebo)


# ## Summary of results

# In[65]:

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
display(HTML(tabulate.tabulate(summarytable, tablefmt='html')))


# In[66]:

with open('summarytable.csv', 'w') as csvfile:
    writer = csv.writer(csvfile)
    [writer.writerow(r) for r in summarytable]


# In[47]:

print('Fisher\'s exact test gives p=%.6f, which is %s.  The estimated vaccine efficacy is %.1f%%'%(fisher, 'statistically significant' if fisherpval<0.05 else 'not statistically significant', 100*VE,))

