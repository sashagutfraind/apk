""" Perform analysis on the APK model output data.

	Run in parallel on each output folder.
	
	The input rootpath argument is the experiment folder containing 
	instance folders. The instance folders are assumed to contain an 
	/output folder with model output files.
"""

import os,sys
from os import listdir
from os.path import isdir, join
from joblib import Parallel, delayed
import time

from trial_analysis_improved import trial_analysis
from parse_events_function import parse_events

if ( len(sys.argv)) < 2:
	print('Please provide experiment folder argument.')
	sys.exit(2)
	
mypath = sys.argv[1]  # [0] is this file	
print('Processing folder ' + mypath)

dirs = [mypath+'/'+f for f in listdir(mypath) if isdir(join(mypath, f))]

dirs.sort()
num_cpu = os.cpu_count()
start = time.time()

# NOTE: do not use prefer="threads" option as this causes issues in the 
#       analysis scripts, probably with one of the libraries.  The default
#       parallelism uses separate python worker processes that wont clash.

# Analyze populations files
#Parallel(n_jobs=num_cpu)(delayed(trial_analysis)(d) for d in dirs)

# Analyze events files
Parallel(n_jobs=num_cpu)(delayed(parse_events)(d) for d in dirs)

# Serial
# for d in dirs:
	# trial_analysis(d)
#	parse_events(d)


# parse_events(dirs[1])
end = time.time()
print(f'Elapsed time: {end - start}')



	