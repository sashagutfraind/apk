#!/usr/bin/python
'''
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt

Usage help
----------

python sweep_driver.py -h

    
Release Notes
-------------
The code is quite modular, and can work with multiple simulations.
To do so, create suitable parsing methods within 
parse_analyze.py

''' 

import sys
import numpy as np
import numpy.random as npr
import random
import re, os, subprocess, sys, time, pickle, pdb
import ConfigParser
import elementtree.ElementTree as ET  #XML parser

import matplotlib
matplotlib.use('PS')

import parse_analyze
try:
    import network_analysis
except ImportError, obj:
    print 'Couldn\'t import.  no network analysis'

timeNow = lambda : time.strftime('%Y_%m_%d__%H_%M_%S', time.localtime())

try:
    import rpy2
    import rpy2.robjects as robj
except ImportError, obj:
    print obj
    print 'Couldn\'t import RPy. Samples cannot be generated.'
    #sys.exit(1)

defaultServerList = ('localhost', 'localhost',)

defaultSecret = 'cheb'

def copyBatchFile(filepath, targetDir):
    #copy batch into the target dir: purely for backup
    import shutil

    filename = os.path.split(filepath)[1]
    newFilepath = targetDir + os.sep + filename + '_' + timeNow()
    shutil.copyfile(filepath, newFilepath)

def create_simulated_job_server():
    class Struct:
        def __init__(self, **entries): self.__dict__.update(entries)

    job_server = Struct()
    job_server.submit = lambda func,args,depfuncs,modules:lambda :func(*args) #creates a lambda fn which takes no arguments and returns the evaluated number  
    job_server.get_ncpus = lambda : -10
    job_server.get_active_nodes = lambda : {}
    job_server.wait = lambda : ''
    job_server.destroy = lambda : ''
    job_server.print_stats = lambda : ''

    return job_server

def initialize():
    import getopt
    opts, args = getopt.getopt(sys.argv[1:], 'b:c:d:f:m:hp:', [''])

    cmdStr    = None
    batchFile = ''
    dataDir   = ''
    jobListFilepath = ''
    execMode = set()
    detailedParams = {}

    for o, a in opts:
       if o in ('-h'):
          usage()
          sys.exit(0)
       elif o in ('-b'):
          batchFile = a
          if not os.path.isabs(batchFile):
             batchFile = os.path.join(os.getcwd(), batchFile)
          print 'Using batch file:'
          print batchFile
       elif o in ('-c'):
          cmdStr = a
       elif o in ('-d'):
          dataDir = a
          if not os.path.isabs(dataDir):
             dataDir = os.path.join(os.getcwd(), dataDir)
       elif o in ('-f'):
          jobListFilepath = a
          if not os.path.isabs(jobListFilepath):
              jobListFilepath = os.path.join(os.getcwd(), jobListFilepath)
       elif o in ('-m'):  #multiple modes are allowed
          if 'a' in a:
              execMode.update(['analyze'])
          if 'b' in a:
              execMode.update(['parseBatch', 'generate', 'runJobList', 'analyze'])
          if 'e' in a:
              execMode.update(['analyzeEvents'])
          if 'g' in a:
              execMode.update(['parseBatch', 'generate'])
          if 'j' in a:
              execMode.update(['runJobList'])
          if 'n' in a:
              execMode.update(['analyzeNetwork'])
          if 's' in a:
              execMode.update(['analyzeStatus'])
       elif o in ('-p'):
          try:
              detailedParams.update(eval(a.strip()))
          except Exception, inst:
              print 'Error parsing parameters!  Given:'
              print a
              raise
        

    print 'Execution mode: ' 
    print list(execMode)

    if len(execMode) == 0:
        print 'Nothing to do...'
        print 
        usage()

    if 'parseBatch' in execMode and (not os.path.exists(batchFile) or not os.path.isfile(batchFile)):
        usage()
        raise ValueError, 'Batch file "%s" does not exists'%batchFile

    if 'analyze' in execMode and 'parseBatch' not in execMode and (not os.path.exists(dataDir) or not os.path.isdir(dataDir)):
        usage()
        raise ValueError, 'Invalid or empty output directory "%s". Review -d option'%dataDir

    if 'runJobList' in execMode and 'parseBatch' not in execMode and (not os.path.exists(jobListFilepath) or not os.path.isfile(jobListFilepath)):
        usage()
        raise ValueError, 'Invalid or empty jobs list file "%s". Review -f option'%jobListFilepath

    if 'runJobList' in execMode and 'parseBatch' not in execMode and (not os.path.exists(dataDir) or not os.path.isdir(dataDir)):
        #usage()
        #raise ValueError, 'Invalid or empty output directory "%s" (needed for task output .txt files). Review -d option'%dataDir
        print 'Attempting to create data directory: ' + dataDir
        os.mkdir(dataDir)
        
    driverParams = {}
    driverParams['batchFile']       = batchFile
    driverParams['cmdStr']          = cmdStr
    driverParams['dataDir']         = dataDir
    driverParams['detailedParams']  = detailedParams
    driverParams['execMode']        = execMode
    driverParams['jobListFilepath'] = jobListFilepath
    return driverParams


def parseLHSbatch(path):
    config = ConfigParser.ConfigParser()
    config.read(path)

    runData    = {}
    paramsData = {}
    for section in config.sections():
        if section == 'LHSconfig':
            runData['technique']  = str.lower(config.get(section, 'technique')) #random or lhs
            runData['seed']       = config.getint  (section, 'seed')
            runData['numSamples'] = config.getint  (section, 'numSamples')
            #wishlist runData['offset']     = config.getfloat(section, 'seed')
            runData['dataDir']    = config.get(section, 'dataDir')
            #runData['serverList'] = config.get(section, 'serverList')
            #runData['burnin']     = config.getint(section, 'burnin')
            #runData['scanMinima'] = config.getboolean(section, 'scanMinima')
            #runData['scanMinimaSamplesPerParam'] = config.getint(section, 'scanMinimaSamplesPerParam')
            #must be done on each machine in advance
            #runData['serverCaps'] = config.get(section, 'serverCaps')
            continue

        param = {}
        paramsData[section.lower()] = param
        #note: we enforce that some 'min' exists in every numerical parameter
        param['type'] = str.lower(config.get(section, 'type')) #string, boolean, double, int[note: rounded]
        param['dist'] = str.lower(config.get(section, 'dist')) 
        if param['dist'] == 'uniform':
            param['max']   = config.getfloat(section, 'max')
            param['min']   = config.getfloat(section, 'min')
        elif param['dist'] == 'uniformrange':
            s = config.get(section, 'range')
            param['range'] = [int(x) for x in re.split(',', s)]
            param['min']   = min(param['range'])
        elif param['dist'] == 'normal':
            param['max']   = config.getfloat(section, 'max')
            param['min']   = config.getfloat(section, 'min')
            param['mean']  = config.getfloat(section, 'mean')
            if(config.has_option(section, 'sd')):
                param['sd'] = config.getfloat(section, 'sd')
            else:
                param['sd'] = (param['max'] - param['min'])/2.
        elif param['dist'] == 'power':
            param['power'] = config.getfloat(section, 'power')
            param['min']   = config.getfloat(section, 'min')
            #wishlist: add support.  in principle just like pnorm below.
        elif param['dist'] == 'zipf':
            param['power'] = config.getfloat(section, 'power')
            param['min']   = config.getfloat(section, 'min')
        elif param['dist'] == 'fixed':
            if param['type'] == 'boolean' or param['type'] == 'string':
                param['value'] = config.get(section, 'value')
            elif param['type'] == 'int' :
                param['value'] = config.getint(section, 'value')
            else:
                param['value'] = config.getfloat(section, 'value')
            param['min']   = param['value']
        else:
            raise ValueError, '%s: unknown distribution'%section

    if not os.path.isabs(runData['dataDir']):
        runData['dataDir'] = os.path.join(os.getcwd(), runData['dataDir'])

    if not os.path.exists(runData['dataDir']):
       os.mkdir(runData['dataDir'])
    else:
       if os.path.exists(runData['dataDir']):
            print '!!!!!!!!!!!!!! WARNING !!!!!!!!!!!!!'
            print ''
            print '    data directory already exists'
            print ''
            print '!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!'
            print 'Dir:'
            print  runData['dataDir'] 
            print '1) For a new batch, you probably want to change the dataDir parameter'
            print '2) If you are repeating a run, make sure RNG seeds are different'
            print ''
            userIn = raw_input('Do you want to continue [Y]/N?: ')
            if userIn == 'N' or userIn == 'n' or userIn == 'No' or userIn == 'NO':
                sys.exit(0)

    if not os.path.isdir(runData['dataDir']):
       raise ValueError, 'Data directory "%s" path already exists and is not a directory'%runData['dataDir']
    
    random.seed(runData['seed'])
    npr   .seed(runData['seed'])

    #runData['serverList'] = re.split(',', runData['serverList'])

    return runData, paramsData


def prepareSamples(runData, paramsData):
    if runData['technique'] != 'random':
        return prepareSamplesLHS(runData, paramsData)
    else:
        return prepareSamplesRandom(runData, paramsData)

def prepareSamplesLHS(runData, paramsData):
    #generate the samples
    #wishlist: use runData['offset'] to create some randomization
    #see also R.set.seed(42)
    slices = {} #shuffled lists of values, keys by parameter
    for param in paramsData:
        slice = []
        pData = paramsData[param]
        if pData['dist'] == 'fixed':
            slice      = [pData['value']]*runData['numSamples']
        elif pData['dist'] == 'uniformrange':
            numRepeats = runData['numSamples'] / len(pData['range']) + 1 #too much is ok
            slice      = numRepeats*pData['range']
        elif pData['dist'] == 'uniform':
            mn         = pData.get('min', -1E60)
            mx         = pData.get('max', +1E60)
            slice      = np.arange(mn, mx, (mx-mn)/runData['numSamples'], dtype=np.double)
            #maybe use 'offset'
        elif pData['dist'] == 'normal':
            mn         = pData.get('min', -1E60)
            mx         = pData.get('max', +1E60)
            mean       = pData['mean']
            sd         = pData['sd']
            mnquantile = robj.r.pnorm(mn, mean=mean, sd=sd)[0]
            mxquantile = robj.r.pnorm(mx, mean=mean, sd=sd)[0]
            jump       = (mxquantile-mnquantile)/runData['numSamples']
            quantiles  = [mnquantile + jump*(0.5+x) for x in xrange(runData['numSamples'])]
            slice      = map(lambda x: robj.r.qnorm(x, mean=mean, sd=sd)[0], quantiles)

        npr.shuffle(slice)
        if pData['type'] == 'int':
            slice = [int(val) for val in slice]
        slices[param] = slice
        #wishlist: plot histograph of slices

    samples = []
    if not runData.get('scanMinima', False):
        for i in xrange(runData['numSamples']):
            sample = {}
            for param in slices:
                sample[param] = {'type':paramsData[param]['type'], 'value':slices[param][i]}
            samples.append(sample)
    else:
        for flattenedParam in paramsData:
            flattenedValue = paramsData[flattenedParam]['min'] 
            for i in xrange(runData['scanMinimaSamplesPerParam']):
                sample = {}
                for param in slices:
                    if param == flattenedParam:
                        val = flattenedValue
                    else:
                        val = slices[param][i]
                    sample[param] = {'type':paramsData[param]['type'], 'value':val}
                samples.append(sample)

    return samples

def prepareSamplesRandom(runData, paramsData):
    raise NotImplementedError, 'Random sampling not implemented!'
    
def prepareSamplesTest():
#debugging assistance for normal variates
    import pylab
    mn         = -50
    mx         = +50
    mean       = 0.
    sd         = 5.0
    runData    = {'numSamples':30}
    mnquantile = robj.r.pnorm(mn, mean=mean, sd=sd)[0]
    mxquantile = robj.r.pnorm(mx, mean=mean, sd=sd)[0]
    jump       = (mxquantile-mnquantile)/runData['numSamples']
    quantiles  = [mnquantile + jump*(0.5+x) for x in xrange(runData['numSamples'])]
    slice      = map(lambda x: robj.r.qnorm(x, mean=mean, sd=sd)[0], quantiles)
    
    print quantiles
    print slice
    pylab.plot(quantiles, slice, '.')
    #x-axis runs at most 0..1
    #y-axis grows when sd increase, or when the interval is expanded
    #increasing sd should make the curve more steep (or expand the y range), 
    #since it ranges over more different values of the allowed interval


def runJobs(driverParams, serverList=('localhost',), cmdStr=None, burnin=500):
    #note: the jobFileNames refer to file containing instructions for each job, not the LHS instructions
    max_hrs_per_pack = driverParams.get('max_hrs_per_pack' , 48)  #TACC limit on running time
    max_packs        = driverParams.get('max_packs', 50)          #TACC limit on the number of jobs 
    hrs_per_job      = driverParams.get('hrs_per_job', 2)         #this is an estimate 
    if 'jobListFilepath' not in driverParams:
        raise ValueError, 'No job file provided...'
    
    jobFilePaths  = []
    runJobsFile = open(driverParams['jobListFilepath'], 'r')
    for idx, line in enumerate(runJobsFile):
        while not line[-1].isalnum(): 
            line=line[:-1]
        filePath = line
        if not os.path.exists(filePath):
            raise ValueError, 'Job file "%s" does not exist!'%filePath
        jobFilePaths.append(filePath)
    runJobsFile.close()

    #submission line for Texas Advanced Computing Center
    import joblib
    Parallel = joblib.Parallel
    delayed  = joblib.delayed

    num_jobs_per_pack = 10
    hrs = hrs_per_job*num_jobs_per_pack
    if hrs > max_hrs_per_pack:
        print 'Too many hrs per execution pack (%d) - will not attempt to start'%(hrs,)
        sys.exit(1)
    
    timeStr = timeNow()
    try:
        packs = []
        pack_num = -1
        for jobNum,jobFilePath in enumerate(jobFilePaths):
            if jobNum%num_jobs_per_pack == 0:
                pack_num += 1
                if pack_num > 0:
                    packfile.close()
                if pack_num > max_packs:
                    print 'Cannot run > %d packs. Cannot start job %d...'%(max_packs, jobNum)
                    break
                packfile = open('pack_num%d_%s'%(pack_num,timeStr), 'w')
                packfile.write('#!/bin/sh' + os.linesep)
                packcmdl = ['sbatch', '-J', 'APK']  #sbatch is not capped.  srun is capped at 4
                packcmdl+= ['-o', 'APK.pack%d_%s.txt'%(pack_num,timeStr)]
                packcmdl+= ['-n', '1']
                packcmdl+= ['-p', 'normal']
                packcmdl+= ['--mail-user=agutfraind.research@gmail.com']
                packcmdl+= ['--mail-user='+os.environ['USER']]
                packcmdl+= ['--mail-type=ALL']
                packcmdl+= ['-t', '%d:00:00'%hrs]
                packcmdl+= ['-A', 'TG-IBN120015']
                packcmdl+= [packfile.name]
                packs.append(packcmdl)
            cmdl = ['bash', 'batch.command', os.path.join(os.getcwd(), jobFilePath), ]#'&'] #forking is not possible on TACC
            packfile.write(os.linesep + 'echo "Starting job %s"'%jobFilePath + os.linesep)
            packfile.write(' '.join(cmdl) + os.linesep)
    except:
        print 'Failed to prepare packs'
    finally:
        packfile.close()
        
    max_submission_workers = 5
    try:
        return_vals = Parallel(n_jobs=max_submission_workers, verbose=True)(delayed(os.system)(' '.join(params)) for params in packs)
        
        for pack, ret in zip(packs, return_vals):
            if type(ret) == int:
                print 'Pack returned: %d'%ret
            else:
                print 'Error: job failed to return a value!'
            print
        print 'Finished submitting.'
    except Exception, inst:
        print 'Couldn\'t run batch: '
        print inst
        raise



def usage():
    print 'Script for running the Agent-Based Model in LHS mode and analyzing the data'
    print
    print 'The script can be used to:'
    print '1) prepare, run and analyze a batch specified by an ini file, or'
    print '2) prepare a batch'
    print '3) run a prepared batch'
    print '4) analyze a completed batch'
    print 
    print 'eg.'
    print 'To run and analyze a batch file'
    print 'python lhsdriver.py -m b -b lhs.ini'
    print
    print 'To analyze without running the batch file(data generated in the past)'
    print 'python lhsdriver.py -m a -d myOutputDir'
    print
    print 'Supported options are:'
    print '[-b  <file name>]   LHS batch file to execute (default: lhs.ini). The batch file describes in INI format the LHS run, including the sample space. '
    print '[-c  <Command>]     The command to execute when running java. Should contain "%s" at the position of the job file name.'
    print '[-d  <dataDir>]     Perform analysis on dataDir containing simulation output files. Overrided by value in batch file (if specified)'
    print '[-f  <fileOfJobs>]  Load jobs specified by fileOfJobs, one job file name per line.  Used with -m j'
    print '[-h              ]  Displays this'
    print '-m  <a|b|e|g|j|s>   Modes (not exclusive):'
    print '                       a=analyze existing output .pkl files.  Requires -d  <directoryToRead>'
    print '                       b=read a batch file, generate samples, run them and then analyze (default). Requires -b <batchFileName>'
    print '                       e=analyze existing output event.csv files.  Requires -d  <directoryToRead>'
    print '                       g=generate job files for lhs task (eg. .pf).  Equivalent to -m gja.  Requires -b <batchFileName>'
    print '                       j=load jobs and run them.  Requires -f <jobListFileName>'
    print '                       s=analyze existing output status.csv files.  Requires -d  <directoryToRead>'
    print '-p, --params        Input paremeters.  Surround the argument with double quotes:'
    print '                    e.g. -p "{\'p1_name\':p1_value, \'p2_name\':p2_value}"'


def writeJobFile(dataDir, xml_template, sample, jobNum, timeNow):
#writes the job file. the output directory is indicated by jobNum
    jobFilePath    = os.path.join(dataDir, 'job_t=' + timeNow + '_sample=' + str(jobNum) + '.xml')
    jobFilePath    = os.path.relpath(jobFilePath)

    #jobFileDataDir = os.path.splitext(jobFilePath)[0] #will be missed by getJobAndDataFilePaths()
    jobFileDataDir = dataDir

    #repast can create the directory itself
    #os.mkdir(jobDataFileDir)

    outputDir = jobFileDataDir 
    if ' ' in outputDir or '"' in outputDir:
        print 'WARNING: In OutputDirectory parameter (%s): repast does not accept spaces or quotes in parameter values...'%outputDir
        print 'Attempting to fix...'
        if outputDir.startswith(os.getcwd()):
            outputDir = outputDir[len(os.getcwd())+1:]
    #outputDir = outputDir.replace(os.getcwd()+os.sep, '') #note: os.path.relpath is available but only in Python >= 2.6
    outputDir = os.path.join('..', os.path.relpath(outputDir)) #needed to return from APK
    outputDir = outputDir.replace('\\', '/')

    tree = xml_template  #wishlist: deepcopy
    try:
        tree.getroot().set('jobNum', str(jobNum))
        for parameter_info in tree.getroot().getchildren():
            param_name  = parameter_info.get('name')
            if param_name.lower() not in sample:
                print 'Warning: parameter "%s" not in sample.'%param_name
                continue 
            param_value = sample[param_name.lower()]['value']
            parameter_info.set('value', str(param_value))
        tree.write(jobFilePath)
    except Exception, inst:
        print 'Unable to write job file...'
        print inst
        raise
    
    return jobFilePath, jobFileDataDir

def writeJobs(runData, samples, driverParams):
    jobPaths = []
    timeStr  = timeNow()
    xml_template_path = driverParams.get('xml_template_path', '../code/batch/batch_experiment.xml')
    if not os.path.exists(xml_template_path): 
        xml_template_path = '../batch/batch_experiment.xml'
    template_job_tree = ET.parse(xml_template_path)
    try:
        for jobNum,sample in enumerate(samples):
            jobFilePath, jobDataFileDir = writeJobFile(runData['dataDir'], template_job_tree, sample, jobNum, timeStr)
            jobPaths.append(jobFilePath)
            sys.stdout.write('.'); 
            sys.stdout.flush()
        print
    except Exception, inst:
        print 'Couldn\'t write sample #' + str(jobNum)
        print inst

    try:
        jobsListFilepath = 'jobList_'+timeStr
        f = open(jobsListFilepath, 'w')
        for lineNum, jobFile in enumerate(jobPaths):
            f.write(os.path.relpath(jobFile) +  '\n')
        print 'Written samples.  List: ' + jobsListFilepath
    except Exception, inst:
        print 'Error writing job file list '
        print 'see line %d'%lineNum
        print inst
        raise
    finally:
        f.close()

    return jobsListFilepath

if __name__ == '__main__':
    driverParams = initialize()
    runData = {}

    if 'parseBatch' in driverParams['execMode']:
       print 'Parsing batch file:'
       print  driverParams['batchFile']
       runData, paramsData             = parseLHSbatch(driverParams['batchFile']) 
       driverParams['dataDir']         = runData['dataDir'] #overrides command line value

    if 'generate' in driverParams['execMode']:
       print 'Generating samples and job files..'
       samples = prepareSamples(runData, paramsData)
       jobsListFilepath = writeJobs(runData, samples, driverParams)
       driverParams['jobListFilepath'] = jobsListFilepath
       copyBatchFile(driverParams['batchFile'], driverParams['dataDir'])

    if 'runJobList' in driverParams['execMode']:
       print 'Running jobs from the jobs file:'
       print  driverParams['jobListFilepath']
       runJobs(driverParams, serverList=runData.get('serverList', defaultServerList), cmdStr=driverParams['cmdStr'], burnin=runData.get('burnin', 500))

    if 'analyze' in driverParams['execMode']:
       print 'Starting data analysis on directory:'
       print driverParams['dataDir']
       outputFilePaths  = parse_analyze.getJobAndDataFilePaths(driverParams['dataDir'], driverParams['detailedParams'])
       dossier          = parse_analyze.reportBatchStats(outputFilePaths['dataFilePaths'], driverParams['detailedParams'])
       parse_analyze.displayLHS_BatchStats(driverParams['dataDir'], dossier, driverParams['detailedParams'])

    if 'analyzeEvents' in driverParams['execMode']:
       print 'Starting event analysis on directory:'
       print driverParams['dataDir']
       outputFilePaths  = parse_analyze.getJobAndDataFilePaths(driverParams['dataDir'], driverParams['detailedParams'])
       dossier          = parse_analyze.collectEventStats(outputFilePaths['eventFilePaths'], driverParams['dataDir'], driverParams['detailedParams'])

    if 'analyzeStatus' in driverParams['execMode']:
       print 'Starting status analysis on directory:'
       print driverParams['dataDir']
       outputFilePaths  = parse_analyze.getJobAndDataFilePaths(driverParams['dataDir'], driverParams['detailedParams'])
       dossier          = parse_analyze.collectStatusStats(outputFilePaths['statusFilePaths'], driverParams['dataDir'], driverParams['detailedParams'])

    if 'analyzeNetwork' in driverParams['execMode']:
       print 'Starting data analysis on directory:'
       print driverParams['dataDir']
       outputFilePaths = parse_analyze.getJobAndDataFilePaths(driverParams['dataDir'], driverParams['detailedParams'])
       netDossier      = network_analysis.analyze_networks(outputFilePaths['networkFilePaths'], driverParams['detailedParams'])

