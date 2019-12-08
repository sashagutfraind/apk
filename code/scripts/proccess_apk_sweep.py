import os,sys
from os import listdir
from os.path import isdir, join
from joblib import Parallel, delayed

from trial_analysis_improved import trial_analysis

# The rootpath argument is the experiment folder containing instance folders
# The instance folders are assumed to contain an /output folder with model output files
if ( len(sys.argv)) < 2:
	print('Please provide experiment folder argument.')
	sys.exit(2)
	
mypath = sys.argv[1]  # [0] is this file	
print('Processing folder ' + mypath)


dirs = [mypath+'/'+f for f in listdir(mypath) if isdir(join(mypath, f))]

# Parallel
Parallel(n_jobs=8,prefer="threads")(delayed(trial_analysis)(d) for d in dirs)

# Serial
#for d in dirs:
#	print(d)
#	trial_analysis(d)
	



