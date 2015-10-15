'''
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt

Implementation of lower level code 
* Latin Hypercube Sampling tasks
* analysis of data from multiple simulations
* parsing of various data files
* plotting

'''

import sys
import numpy
import numpy as np
import numpy.random as npr
import random
import re, os, sys, time, pickle, subprocess, pdb, cPickle


#HPC_data  = "2014-08-21_STAMPEDE"
#MAIN_data = "2014-10-03--definitive"

'''
#Fig COMPOSITON
str_compositionA = "python sweep_driver.py -m a -d 2014-08-21_STAMPEDE -p \"{'dossier_summaries_path':'2014-08-21_STAMPEDE/dossier_summaries-2014_08_21__14_39_48.pkl', 'plotting_round':[2]}\""
print(str_compositionA)
os.system(str_compositionA)

###not reported
##str_compositionB = "python sweep_driver.py -m a -d 2014-08-21_STAMPEDE -p \"{'dossier_summaries_path':'2014-08-21_STAMPEDE/dossier_summaries-2014_08_21__14_39_48.pkl', 'plotting_round':['means']}\""
##print(str_compositionB)
##os.system(str_compositionB)

str_compositionC = "python sweep_driver.py -m a -d 2014-08-21_STAMPEDE -p \"{'dossier_summaries_path':'2014-08-21_STAMPEDE/dossier_summaries-2014_08_21__14_39_48.pkl', 'plotting_round':[5]}\""
print(str_compositionC)
os.system(str_compositionC)

str_compositionD = "python sweep_driver.py -m a -d 2014-08-21_STAMPEDE -p \"{'dossier_summaries_path':'2014-08-21_STAMPEDE/dossier_summaries-2014_08_21__14_39_48.pkl', 'plotting_round':[1,3]}\""
print(str_compositionD)
os.system(str_compositionD)

#Fig PREVALENCE (some covered in previous rounds)
str_prevalenceA = "python sweep_driver.py -m a -d 2014-08-21_STAMPEDE -p \"{'dossier_summaries_path':'2014-08-21_STAMPEDE/dossier_summaries-2014_08_21__14_39_48.pkl', 'plotting_round':[2]}\""
print(str_prevalenceA)
os.system(str_prevalenceA)

str_prevalenceB = "python sweep_driver.py -m a -d 2014-08-21_STAMPEDE -p \"{'dossier_summaries_path':'2014-08-21_STAMPEDE/dossier_summaries-2014_08_21__14_39_48.pkl', 'plotting_round':[1,3]}\""
print(str_prevalenceB)
os.system(str_prevalenceB)

str_prevalenceC = "python sweep_driver.py -m a -d 2014-08-21_STAMPEDE -p \"{'dossier_summaries_path':'2014-08-21_STAMPEDE/dossier_summaries-2014_08_21__14_39_48.pkl', 'plotting_round':[4]}\""
print(str_prevalenceC)
os.system(str_prevalenceC)


#
#Fig INCIDENCE
str_incidence = "python sweep_driver.py -m e -d 2014-10-03--definitive -p \"{'report_multisieve':True}\""
print(str_incidence)
os.system(str_incidence)


#Fig incidence histograph
#str_incidence = "python sweep_driver.py -m e -d 2014-10-03--definitive -p \"{'report_multisieve':False}\""
#print(str_incidence)
#os.system(str_incidence)
'''


#Fig cohort data
str_cohortA = "python sweep_driver.py -m s -d 2014-10-03--definitive -p \"{'plotting_round':[1]}\""
print(str_cohortA)
os.system(str_cohortA)

str_cohortB = "python sweep_driver.py -m s -d 2014-10-03--definitive -p \"{'plotting_round':[3,4]}\""
print(str_cohortB)
os.system(str_cohortB)


