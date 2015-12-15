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
import elementtree.ElementTree as ET

timeNow = lambda : time.strftime('%Y_%m_%d__%H_%M_%S', time.localtime())
def echo(s,f):
    sys.stdout.write(s + os.linesep)
    f.write(s + os.linesep)

SouthSide=frozenset([60608,60609,60615,60616,60617,60619,60620,60621,60628,60629,60632,60633,60636,60637,60638,60643,60649,60652,60653,60655,60827,])
WestSide=frozenset([60606,60607,60612,60622,60623,60624,60639,60642,60644,60647,60651,60661,60601])  #60707 captures part of Chicago, although it is mostly in the Suburbs
NorthSide=frozenset([60602,60603,60604,60605,60610,60611,60613,60614,60618,60625,60626,60630,60631,60634,60640,60641,60645,60646,60654,60656,60657,60659,60660,60666])
#selecting in QGIS
#SouthSide
#"NAME" = 60608 OR "NAME" = 60609 OR "NAME" = 60615 OR "NAME" = 60616 OR "NAME" = 60617 OR "NAME" = 60619 OR "NAME" = 60620 OR "NAME" = 60621 OR "NAME" = 60628 OR "NAME" = 60629 OR "NAME" = 60632 OR "NAME" = 60633 OR "NAME" = 60636 OR "NAME" = 60637 OR "NAME" = 60638 OR "NAME" = 60643 OR "NAME" = 60649 OR "NAME" = 60652 OR "NAME" = 60653 OR "NAME" = 60655 OR "NAME" = 60827 OR "ZCTA" = 606HH
#WestSide
#"NAME" = 60606 OR "NAME" = 60607 OR "NAME" = 60612 OR "NAME" = 60622 OR "NAME" = 60623 OR "NAME" = 60624 OR "NAME" = 60639 OR "NAME" = 60642 OR "NAME" = 60644 OR "NAME" = 60647 OR "NAME" = 60651 OR "NAME" = 60661 OR "NAME" = 60601  #60707 captures part of Chicago, although mostly in the Suburbs
#NorthSide
#"NAME" = 60602 OR "NAME" = 60603 OR "NAME" = 60604 OR "NAME" = 60605 OR "NAME" = 60610 OR "NAME" = 60611 OR "NAME" = 60613 OR "NAME" = 60614 OR "NAME" = 60618 OR "NAME" = 60625 OR "NAME" = 60626 OR "NAME" = 60630 OR "NAME" = 60631 OR "NAME" = 60634 OR "NAME" = 60640 OR "NAME" = 60641 OR "NAME" = 60645 OR "NAME" = 60646 OR "NAME" = 60654 OR "NAME" = 60656 OR "NAME" = 60657 OR "NAME" = 60659 OR "NAME" = 60660 OR "NAME" = 60666

focus_categories = {
    'ALL=ALL':{'fmt':'k^-',                                     'round':[1,2,3,4], 'sorting':0},
    'Race=NHWhite':{'fmt':'yD-',                                'round':[1], 'sorting':1},
    'Race=Hispanic':{'fmt':'rv-',                               'round':[1], 'sorting':2},
    'Race=NHBlack':{'fmt':'b+-',                                'round':[1], 'sorting':3}, 
    'Area=City':{'fmt':'g*-',           'altName':'City of Chicago', 'round':[2], 'sorting':4},
    'Area=Suburban':{'fmt':'g1-',                               'round':[2], 'sorting':5},
    'Age=LEQ30':{'fmt':'b<-',           'altName':'Under 30',   'round':[2], 'sorting':6},
    'Age=Over30':{'fmt':'y>-',          'altName':'Over 30',    'round':[2], 'sorting':7},
    'SyringeSource=HR':{'fmt':'k-o',    'altName':'HR',         'round':[3], 'sorting':8}, 
    'SyringeSource=nonHR':{'fmt':'r-.', 'altName':'nonHR',      'round':[3], 'sorting':9},
    'Gender=Female':{'fmt':'ms-',                               'round':[4], 'sorting':10}, 
    'Gender=Male':{'fmt':'gd-',                                 'round':[4], 'sorting':11}, 
    'AgeDec=AgeLEQ20':{'fmt':'r-',      'altName':u'\u226420',      'round':[5], 'sorting':12}, # unichr(2264) + '20' #' <=20'  #
    'AgeDec=Age21to30':{'fmt':'bD-',    'altName':'21-30',      'round':[5], 'sorting':13},
    'AgeDec=Age31to40':{'fmt':'go-',    'altName':'31-40',      'round':[5], 'sorting':14},
    'AgeDec=Age41to50':{'fmt':'k*-',    'altName':'41-50',      'round':[5], 'sorting':15},
    'AgeDec=Age51to60':{'fmt':'md-',    'altName':'51-60',      'round':[5], 'sorting':16},
    #'AgeDec=AgeOver60':{'fmt':'k-.',   'altName':'61+',        'round':[5], 'sorting':17},
     'mean-age_ALL':{'fmt':'k^-', 'altName':'mean age (years)', 'round':['means'], 'sorting':18},
}


def analyzeRunData(timeSeries, burnin, fName=None):
    means, stds = {}, {}
    #import numpy as np #doesn't work through pp

    if len(timeSeries.values()[0]) <= burnin:
        print 'Data is too short to use burnin pruning'
    for col in timeSeries:
        data = timeSeries[col]
        if len(data) > burnin:
            data = data[burnin:]
        means[col] = numpy.average(data)
        try:
            stds[col] = numpy.std(data,ddof=1)
        except:
            stds[col] = numpy.std(data)
    return means, stds


def calculate_incidence(event_reports):
    times_from_start = event_reports['times_from_start']
    times_to_inf     = event_reports['times_to_inf']
    cutoff_year      = event_reports['cutoff_year']
    num_activations  = event_reports['num_activations']
    num_preexisting  = event_reports['num_preexisting']
    num_first_infections = event_reports['num_first_infections']
    total_pre_exp_PY = event_reports['total_pre_exp_PY']
    num_activations = event_reports['num_activations']
    num_preexisting = event_reports['num_preexisting']

    num_infections   = sum(1 for t in times_to_inf if t < cutoff_year)
    num_never_inf    = num_activations-num_infections-num_preexisting
    pc_never_inf     = 100*float(num_never_inf)/(num_activations-num_preexisting)
    total_incidence  = float(num_infections) /(num_activations-num_preexisting)  #the basis is the population w/o preexisting infection
    incidence_rate = num_first_infections / total_pre_exp_PY

    #not used: estimate of effective population is a bit crude, b/c the effective susceptible population increases over the simulation: initial are mixed, but all new arrivals are susceptible
    #annual_incidence = total_incidence / cutoff_year
    #effective_susceptible_population = float(mean_population)*float(num_activations-num_preexisting)/num_activations

    ret = {'num_infections':num_infections, 'num_never_inf':num_never_inf,
            'pc_never_inf':pc_never_inf, 'incidence_rate_per100':(100*incidence_rate), 'total_incidence_per100':(100*total_incidence)}
    return ret
    
def collectEventStats(eventFileNames, dataDir, params):
    #process event statistics use -m e option
    #Finds: 1. incidence rates, 
    #       2. activation stats (BIG TABLE in paper)
    if len(eventFileNames) == 0:
        print('   0 event files.  Exiting ...')
        sys.exit(1)

    outputDir = os.path.join(dataDir, '_analysis')
    if not os.path.exists(outputDir):
        os.mkdir(outputDir)

    
    if params.get('report_multisieve', False):
        print
        print 'Activations ...'
        #FIXME report_activations_multisieve(eventFileNames, outputDir, params)
        print
        print 'Incidence ...'
        report_incidence_multisieve(eventFileNames, outputDir, params)
        sys.exit(1) 

    #buckets generates a string.  it must match, or a new bucket is made
    #categories are like dimensions, and buckets are like values along that dimension
    categories = [{'name':'Age',                        'buckets':lambda row:'Age='+('%02d'%((int(row['Age'])/10)*10)+'->'+'%02d'%((1+int(row['Age'])/10)*10))},  
                  {'name':'AgeDec',                   'buckets':lambda row:'AgeDec='+str(row['AgeDec'])}, 
                  {'name':'Gender',                     'buckets':lambda row:'Gender='+str(row['Gender'])}, 
                  {'name':'Syringe_source',             'buckets':lambda row:'SyringeSource='+str(row['Syringe_source'])}, 
                  {'name':'Race',                       'buckets':lambda row:'Race='+str(row['Race'])}, 
                  {'name':'Zip',                        'buckets':lambda row:'Zip='+str(row['Zip'])}, 
                  {'name':'Age_Started',                'buckets':lambda row:'Age_Started='+('%02d'%((int(row['Age_Started'])/10)*10)+'->'+'%02d'%((1+int(row['Age_Started'])/10)*10))}, 
                  {'name':'Drug_in_degree',             'buckets':lambda row:'Drug_in_degree='+'%05.1f'%(row['Drug_in_degree'])}, 
                  {'name':'Drug_out_degree',            'buckets':lambda row:'Drug_out_degree='+'%05.1f'%(row['Drug_out_degree'])}, 
                  {'name':'Daily_injection_intensity',  'buckets':lambda row:'Daily_injection_intensity='+'%04.1f'%(int(round(row['Daily_injection_intensity']*2)*0.5))},
                  {'name':'Fraction_recept_sharing',    'buckets':lambda row:'Fraction_recept_sharing='+'%05.2f'%(np.round(row['Fraction_recept_sharing']*8)*0.125)}, 
                  {'name':'YearsIDU',                   'buckets':lambda row:'YearsIDU='+('%02d'%((int(row['Age'])-int(row['Age_Started']))))},  
                  ]

    #CHOOSE a sieve with a parameter
    sieve_name        = params.get('sieve_name', 'ALL')
    population_filter = get_population_filter(sieve_name, params)    
    print 'Sieve: ' + sieve_name
    print
    infection_filter  = lambda x, p: x['Event']=='infected' 

    event_reports = load_event_files(eventFileNames, population_filter, infection_filter, sieve_name, params)
    event_header        = event_reports['event_header']
    all_infos           = event_reports['all_infos']
    all_activations     = event_reports['all_activations']
    pure_exposures      = event_reports['pure_exposures']
    first_exposures     = event_reports['first_exposures']
    sim_end_times       = event_reports['sim_end_times']
    sim_populations     = event_reports['sim_populations']
    num_activations     = event_reports['num_activations']
    num_preexisting     = event_reports['num_preexisting']
    total_pre_exp_PY    = event_reports['total_pre_exp_PY']
    mean_population    = event_reports['mean_population']
    num_first_infections    = event_reports['num_first_infections']
    cutoff_year    = event_reports['cutoff_year']

    stats_string  = 'Mean population, %d\n'%(mean_population,)
    stats_string += 'Num activations, %d, Num exposures, %d, num_preexisting, %d\n'%(num_activations,len(pure_exposures),num_preexisting)
    stats_string += 'Population and events: %s\n'%sieve_name

    report_activations(os.path.join(outputDir, 'activations_dossier_sieve=' +sieve_name + '_' + timeNow() +'.csv'), all_activations, categories, stats_string)
    report_prevalence_by_durations(os.path.join(outputDir, 'prevalences_dossier_sieve=' +sieve_name + '_' + timeNow() +'.csv'), all_activations, all_infos, stats_string, cutoff_year)

    with open(os.path.join(outputDir, 'exposures_dossier_sieve=' +sieve_name + '_' + timeNow() +'.csv'), 'w') as dossier_file:
        dossier = {}
        for row in pure_exposures:
            for cat in categories:
                try:
                    bucket = cat['buckets'](row)
                    if bucket in dossier:
                        dossier[bucket].append(row)
                    else:
                        dossier[bucket] = [row]
                except:
                    print 'Skipping bucket %s and its category: '+str(bucket)

        dossier_file.write('Mean population, %d\n'%(mean_population,))
        dossier_file.write('Num activations, %d, Num exposures, %d, num_preexisting, %d\n'%(num_activations,len(pure_exposures),num_preexisting))
        dossier_file.write('Population and events: %s\n'%sieve_name)

        report_exposure_histogram_incidence(event_reports, outputDir, dossier_file)
        report_exposure_histogram_waiting(event_reports, outputDir, dossier_file)

        header = 'Class,ClassLimits,#Agents,#Events,Median time to exposure,Shortest 5pc time to exposure,'
        echo(header, dossier_file)
        bucket_names = dossier.keys()
        bucket_names.sort()
        for bucket in bucket_names:
            arr = [row['TimeToInf'] for row in dossier[bucket]]
            bucket_size = len(dossier[bucket])
            bucket_val = bucket.split('=')[1]
            line = '%s,%s,%d,%d,%.5f,%.5f,'%(bucket,bucket_val,bucket_size,len(arr), np.median(arr), np.percentile(arr, 5))
            echo(line, dossier_file)
        print 'writing: %s'%dossier_file.name

    try:
        pklFilename = outputDir + 'events_' + timeNow() +'.pkl'
        outputFile  = open(pklFilename, 'wb')
        pickle.dump(dossier, outputFile)
        outputFile.close()
        print 'Pickle: ' + pklFilename + ' written!'
    except Exception, inst:
        print inst
        print 'Unable to pickle...'

    return dossier

def collectStatusStats(statusFileNames, dataDir, params):
    #process event statistics use -m s option
    #Finds: 1. prevalence over time for a cohort

    import pylab
    if len(statusFileNames) == 0:
        print('   0 status files.  Exiting ...')
        sys.exit(1)

    #buckets generates a string NAME.  it must match an existing bucket's NAME, or a new bucket is made with NAME
    #categories are like dimensions, and buckets are like values along that dimension
    categories = [#{'name':'Age',                        'buckets':lambda row:'Age='+('%02d'%((int(row['Age'])/10)*10)+'->'+'%02d'%((1+int(row['Age'])/10)*10))},  
                  {'name':'AgeClass',                    'buckets':lambda row:('Age=LEQ30' if float(row['Age']) <= 30 else 'Age=Over30')}, 
                  {'name':'Gender',                     'buckets':lambda row:'Gender='+str(row['Gender'])}, 
                  {'name':'Race',                       'buckets':lambda row:'Race='+str(row['Race'])}, 
                  #{'name':'Zip',                        'buckets':lambda row:'Zip='+str(row['Zip'])}, 
                  #{'name':'Age_Started',                'buckets':lambda row:'Age_Started='+('%02d'%((int(row['Age_Started'])/10)*10)+'->'+'%02d'%((1+int(row['Age_Started'])/10)*10))}, 
                  {'name':'Drug_in_degree',             'buckets':lambda row:'Drug_in_degree='+'%05.1f'%(row['Drug_in_degree'])}, 
                  {'name':'Drug_out_degree',            'buckets':lambda row:'Drug_out_degree='+'%05.1f'%(row['Drug_out_degree'])}, 
                  {'name':'Daily_injection_intensity',  'buckets':lambda row:'Daily_injection_intensity='+'%04.1f'%(int(round(row['Daily_injection_intensity']*2)*0.5))},
                  {'name':'Fraction_recept_sharing',    'buckets':lambda row:'Fraction_recept_sharing='+'%05.2f'%(np.round(row['Fraction_recept_sharing']*8)*0.125)}, 
                  {'name':'YearsIDU',                   'buckets':lambda row:'YearsIDU='+('%02d'%((int(row['Age'])-int(row['Age_Started']))))},  
                  {'name':'ALL',                        'buckets':lambda row:'ALL=ALL'},
                  {'name':'Area',                       'buckets':lambda row:('Area=City' if str(int(row['Zip']))[:3] == '606' else 'Area=Suburban')},
                  #{'name':'Syringe_source',             'buckets':lambda row:'SyringeSource='+str(row['Syringe_source'])},  #obsolete
                  {'name':'HR',                         'buckets':lambda row:('SyringeSource=HR' if row['Syringe_source'].strip() in ['NEP', 'HR', 'COIP'] else 'SyringeSource=nonHR')},
                  ]
    #CHOOSE a sieve
    sieve_name = params.get('sieve_name', 'ALL')
    infection_filter  = lambda x: x['Event']=='infected' 
    population_filter = get_population_filter(sieve_name, params)

    print 'Sieve: ' + sieve_name
    print

    status_reports = load_status_files(statusFileNames, population_filter, infection_filter)
    event_header        = status_reports['event_header']
    all_histories       = status_reports['all_histories']
    all_baselines       = status_reports['all_baselines']
    sim_end_times       = status_reports['sim_end_times']
    max_time            = status_reports['max_time']
    
    if len(all_histories) == 0:
        print 'Warning: no agent were found in the event/status files'

    #stats_string  = 'Mean population, %d\n'%(mean_population,)
    #stats_string += 'Num activations, %d, Num exposures, %d, num_preexisting, %d\n'%(num_activations,len(pure_exposures),num_preexisting)
    #stats_string += 'Population and events: %s\n'%sieve_name

    outputDir = os.path.join(dataDir, '_analysis')
    if not os.path.exists(outputDir):
        os.mkdir(outputDir)

    reports = {}
    arriving_only = params.get('arriving_only', True)
    print 'SubFilter: arriving_only: %d'%arriving_only
    dossier_file = open(os.path.join(outputDir, 'histories_dossier_sieve=' +sieve_name + '_' + 'arrivingOnly=%d'%arriving_only + '_' + timeNow() +'.csv'), 'w')
    dossier = {}
    for agent_id in all_baselines:
        agent_baseline = all_baselines[agent_id]
        if arriving_only and (not agent_baseline['_arriving']):
            continue
        for cat in categories:
            try:
                mybucket = cat['buckets'](agent_baseline)
                if mybucket in dossier:
                    dossier[mybucket].append(agent_id)
                else:
                    dossier[mybucket] = [agent_id]
            except:
                print 'Skipping bucket %s and its category: '+str(mybucket)

    dossier_file.write('Population and events: %s\n'%sieve_name)

    header = 'Class,ClassLimits,'
    for t in range(max_time):
        header += '#t%d_Agents,#t%d_abpos,t%d_ABprevalence,'%(t,t,t)
    echo(header, dossier_file)
    bucket_names = dossier.keys()
    bucket_names.sort()
    for bucket in bucket_names:
        if '=' in bucket:
            bucket_val = bucket.split('=')[1]
        else:
            bucket_val = ''
        line = '%s,%s,'%(bucket,bucket_val,)
        #we record histories only if we have the full early history (i.e. only in newly-initiating agents)
        bucket_histories = [all_histories[agent_id] for agent_id in dossier[bucket] if all_baselines[agent_id]['_arriving']]
        agent_counts = [0.] * max_time
        abpos_counts = [0.] * max_time
        preval_vals  = [0.] * max_time
        for individual_hist in bucket_histories:
            #parsing arrays of the form [['s', 'a', 'a'], ['i', 'i'] ...]
            #if len(individual_hist) == 0 or individual_hist[0] != 'susceptible': 
            #some agents might initiate into injecting drug while already infected from previous casual use
            if len(individual_hist) == 0:
                continue
            for t,v in enumerate(individual_hist):
                agent_counts[t] += 1
                abpos_counts[t] += 1. if v!='susceptible' else 0.
        for t in range(max_time):            
            preval_vals[t] = abpos_counts[t]/(1E-20 + agent_counts[t])
        reports[bucket] = {'agent_counts':agent_counts, 'abpos_counts':abpos_counts, 'preval_vals':preval_vals}
        for t in range(max_time):            
            line += '%.5f,%.5f,%.5f,'%(agent_counts[t],abpos_counts[t],preval_vals[t])
        echo(line, dossier_file)
    print 'writing: %s'%dossier_file.name
    #wishlist: hypothetically, we could even juxtapose groups for absolute time (over the simulation) for each group to be infected.

    start_pop = reports['ALL=ALL']['agent_counts'][0]
    for t in range(1,max_time):
        if reports['ALL=ALL']['agent_counts'][t] < 0.25 * start_pop:
            break

    pylab.figure()
    pylab.hold(True)
    plotting_round = params.get('plotting_round', None)
    buckets = focus_categories.items()
    buckets.sort(key=lambda x:x[1].get('sorting', 0))
    if plotting_round != None:
        buckets = [(bucket, bucket_data) for bucket, bucket_data in buckets if not set(plotting_round).isdisjoint(bucket_data.get('round', []))]
    for bucket, bucket_data in buckets:
        if bucket not in reports:
            print 'No agent with bucket: %s'%str(bucket)
            continue
        print 'Bucket %s, #agents: %s'%(bucket, str(reports[bucket]['agent_counts']))
        print '           #infect: %s'%(        str(reports[bucket]['abpos_counts']))
        print '           PC.prev: %s'%(        str(reports[bucket]['preval_vals']))
        label = bucket.split('=')[-1]
        label = bucket_data.get('altName', label)
        #max_time_axis = (reports[bucket]['agent_counts']+[0]).index(0)
        #max_time_axis -= 1 #throw away the last time due to low counts
        max_time_axis = len(reports[bucket]['agent_counts']) + 1
        for idx,cnt in enumerate(reports[bucket]['agent_counts']):
            if cnt < 0.30*max(reports[bucket]['agent_counts']):
                max_time_axis = idx
                break
        print max_time_axis
        Ts, Ys = np.arange(max_time_axis), np.array(reports[bucket]['preval_vals'][:max_time_axis])
        pylab.plot(Ts, Ys, bucket_data.get('fmt', 'k'), label=label, markersize=10)
        yerr = np.sqrt(np.ones(max_time_axis)-Ys)/np.array(reports[bucket]['abpos_counts'][:max_time_axis]) #binomial
        pylab.errorbar(x=Ts, y=Ys, yerr=yerr, fmt=bucket_data.get('fmt', 'k-'), markersize=10)
    if 'Ts' not in dir():
        return
        
    #wishlist: strangely, pylab refuses to have different scales for the different series ...
    pylab.xlabel('Years from initiation into injection drug use', fontsize=19)
    pylab.ylabel('Prevalence (HCV antibody+)', fontsize=19)
    pylab.tick_params(labelsize=20)
    pylab.xlim(Ts[0],Ts[-1]+0.1)
    pylab.ylim(0,0.31)
    pylab.legend(loc='best')#'lower right')
    #figpath = dossier_file.name + '.png'
    figpath = dossier_file.name + '.eps'
    pylab.savefig(figpath)
    pylab.hold(False)
    #pylab.show()
    print 'Written: ' + figpath


def displayLHS_BatchStats(dataDir, dossier, params):
    #aggregate statistics for the population over time (-m a option)
    #supports aggregation of multiple simulation runs
    #Finds: 1. composition
    #       2. prevalence

    #FIGU
    #dossier is just a list of samples where each sample is a tuple: sample,metrics
    timeNow = lambda : time.strftime('%Y_%m_%d__%H_%M_%S', time.localtime())
    #report is just dict of samples, keyed by filename, where each sample is a tuple: params,metrics

    print
    print 'Starting data analysis and display...'
    if (dossier == None or len(dossier) == 0) and ('dossier_summaries_path' not in params):
        print 'Dossier is empty!'
        return

    outputDir = os.path.join(dataDir, '_analysis')
    if not os.path.exists(outputDir):
        os.mkdir(outputDir)

    dossierSummariesPath = params.get('dossier_summaries_path', None)
    if dossierSummariesPath != None:
        with open(dossierSummariesPath, 'r') as dossier_summaries_file:
            dossierSummaries = cPickle.load(dossier_summaries_file)
    else:
        dossierSeries = {}
        dossierSeriesLength = {}
        total_reports_with_errors = 0
        for rep_num, report in enumerate(dossier):
            timeSeries = report['timeSeries']
            report_with_error = False
            print rep_num
            for colname in timeSeries:
                thisSeries = timeSeries[colname]
                if colname not in dossierSeries:
                    dossierSeries[colname]       = [thisSeries]
                    dossierSeriesLength[colname] = len(thisSeries)
                else:
                    l = len(thisSeries)
                    if l != dossierSeriesLength[colname]:
                        print 'Warning: series %s is different in length (%d vs %d before).  Check file %s'%(colname, l, dossierSeriesLength[colname], report['fName'])
                        if l < dossierSeriesLength[colname]:
                            report_with_error = True
                            print 'Dropping!'
                        else:
                            thisSeries = thisSeries[:dossierSeriesLength[colname]]
                        #wishlist: often find different series length.  see 2014-05-31--10.25.17-203175068 (3675 vs 3676 most others)
                    else:
                        dossierSeries[colname].append(thisSeries)
                    
            total_reports_with_errors += 1 if report_with_error else 0
            if params.get('plot_individual_datafiles', False) or len(dossier) == 1:
                plot_means(report, outputDir, params)
                plot_pop_composition(report, outputDir, params)
                plot_prevalences(report, outputDir, params)
        print 'Reports with errors: %d  total reports: %d'%(total_reports_with_errors, len(dossier))

        if len(dossier) > 1:
            print 'Writing dossier ... '
            dossierSummariesPath = 'dossier_summaries-' + timeNow() + '.pkl'
            dossierSummaries = {'fName':dossierSummariesPath, 'timeSeries':{}}  #structured like a report, with keys: fName, timeSeries
            for colname in dossierSeries:
                avg_data = np.average(dossierSeries[colname], axis=0)
                std_data = np.std(dossierSeries[colname], axis=0)

                dossierSummaries['timeSeries'][colname]             = avg_data
                dossierSummaries['timeSeries'][colname+'_ERRORBAR'] = std_data
            with open(os.path.join(outputDir, dossierSummariesPath), 'w') as dossier_summaries_file:
                cPickle.dump(dossierSummaries, dossier_summaries_file)
                print 'Written: '+dossierSummariesPath
            
            dossierSummariesCSVPath = os.path.join(outputDir, 'dossier_summaries-' + timeNow() + '.csv')
            data_array = []
            colnames = [colname for colname in dossierSummaries['timeSeries']]
            colnames.sort()
            for colname in colnames:
                data_array.append(dossierSummaries['timeSeries'][colname])
            numpy.savetxt(dossierSummariesCSVPath, np.array(data_array).T, delimiter=",")
            dossier_summaries_csv_file = open(dossierSummariesCSVPath, 'a')
            dossier_summaries_csv_file.write(','.join(colnames))
            dossier_summaries_csv_file.close()
            print 'Written: '+dossierSummariesCSVPath

    if 'dossierSummaries' in dir():
        plot_means(dossierSummaries, outputDir, params)
        plot_pop_composition(dossierSummaries, outputDir, params)
        plot_prevalences(dossierSummaries, outputDir, params)


    
    
def getJobAndDataFilePaths(dataDir, params):
#all .pf are jobs, .cvs files are report
    jobFilePaths = []
    dataFilePaths = []
    eventFilePaths = []
    networkFilePaths = []
    statusFilePaths = []
    
    names = []
    for name in os.listdir(dataDir):
        abs_name = os.path.join(dataDir, name)
        if not os.path.isdir(abs_name):
            names.append(abs_name)
        else:
            for nested_name in os.listdir(abs_name):
                names.append(os.path.join(abs_name, nested_name))

    for name in names:
        if '_analysis' in name:
            print 'Skipping: '+name
            continue
        fileExt = os.path.splitext(name)[1]
        if fileExt == '.pf':
            jobFilePaths.append(name)
        elif fileExt == '.pkl':
            dataFilePaths.append(name)
        #elif fileExt == '.csv':
        elif name.endswith('populations.csv'):
            dataFilePaths.append(name)
        elif name.endswith('events.csv'):
            eventFilePaths.append(name)
        elif name.endswith('network.csv'):
            networkFilePaths.append(name)
        elif name.endswith('status.csv'):
            statusFilePaths.append(name)
    jobFilePaths.sort()
    dataFilePaths.sort()
    eventFilePaths.sort()
    networkFilePaths.sort()
    statusFilePaths.sort()

    print 'Found %d job files, %d data files, %d event files, %d network files, %d status files.'%(len(jobFilePaths),len(dataFilePaths),len(eventFilePaths),len(networkFilePaths),len(statusFilePaths))
    return {'jobFilePaths':jobFilePaths, 'dataFilePaths':dataFilePaths, 'eventFilePaths':eventFilePaths, 
            'statusFilePaths':statusFilePaths, 'networkFilePaths':networkFilePaths}

#def getJobFileDataDir(jobFilePath):
##assumes that the output goes to the directory containing the job file
#    if '-' in jobFilePath:
#        print 'Warning: job file path contains "-" - repast will fail!'
#    return os.path.splitext(jobFilePath)[0]


def get_population_filter(sieve_name, params):
    #currently, none of the filters uses the global params
    if sieve_name == 'ALL':
        population_filter = lambda x, p: True #any criterion for inclusion
    elif sieve_name == 'Initial':
        population_filter = lambda x, p: float(x['Time']) <= p.get('start_time', 0.01) #at the beginning of the simulation
    elif sieve_name == 'Arriving':
        population_filter = lambda x, p: float(x['Time']) > p.get('start_time', 0.01) #arrive during the course of the simulation
    elif sieve_name == 'NorthSide':
        population_filter = lambda x, p: int(x['Zip']) in NorthSide
    elif sieve_name == 'SouthSide':
        population_filter = lambda x, p: int(x['Zip']) in SouthSide
    elif sieve_name == 'WestSide':
        population_filter = lambda x, p: int(x['Zip']) in WestSide
    elif sieve_name == 'Suburban':
        population_filter = lambda x, p: not str(int(x['Zip'])).startswith("606")
    elif sieve_name == 'YCC':
        population_filter = lambda x, p: str(int(x['Zip'])).startswith("606") and (float(x['Age']) <= 30)
    elif sieve_name == 'YoungArriving':
        population_filter = lambda x, p: (float(x['Age']) <= 30) and float(x['Time']) > p.get('start_time', 0.01) 
    elif sieve_name == 'YoungWhiteArriving':
        population_filter = lambda x, p: (float(x['Age']) <= 30) and float(x['Time']) > p.get('start_time', 0.01) and  (x['Race'] == 'NHWhite')
    elif sieve_name == 'YoungWhiteArrivingNet':
        population_filter = lambda x, p: (float(x['Age']) <= 30) and float(x['Time']) > p.get('start_time', 0.01) and  (x['Race'] == 'NHWhite') and (x['Drug_in_degree'] >= 1.0)
    elif sieve_name == 'YoungArrivingNet':
        population_filter = lambda x, p: (float(x['Age']) <= 30) and float(x['Time']) > p.get('start_time', 0.01) and (x['Drug_in_degree'] >= 1.0)
    elif sieve_name == 'Network':
        population_filter = lambda x, p: (x['Drug_in_degree'] >= 1.0)
    elif sieve_name == 'NetworkArriving':
        population_filter = lambda x, p: (float(x['Time']) > p.get('start_time', 0.01)) and (x['Drug_in_degree'] >= 1.0)
    elif sieve_name == 'City of Chicago':
        population_filter = lambda x, p: str(int(x['Zip'])).startswith("606")
    elif sieve_name == 'NEP' or sieve_name == 'HR':
        population_filter = lambda x, p: (x['Syringe_source'] in ['NEP', 'HR'])
    elif sieve_name == 'nonNEP' or sieve_name == 'nonHR':
        population_filter = lambda x, p: (x['Syringe_source'] not in ['NEP', 'HR'])
    elif sieve_name == 'Under 30':
        population_filter = lambda x, p: (float(x['Age']) <= 30)
    elif sieve_name == 'Over 30':
        population_filter = lambda x, p: (float(x['Age']) > 30)
    elif sieve_name == 'NHBlack':
        population_filter = lambda x, p: (x['Race'] == 'NHBlack' or x['Race'] == 'Black'  )
    elif sieve_name == 'Hispanic':
        population_filter = lambda x, p: (x['Race'] == 'Hispanic')
    elif sieve_name == 'NHWhite':
        population_filter = lambda x, p: (x['Race'] == 'NHWhite')
    elif sieve_name == 'Male':
        population_filter = lambda x, p: (x['Gender'] == 'Male')
    elif sieve_name == 'Female':
        population_filter = lambda x, p: (x['Gender'] == 'Female')
    else:
        raise Exception, 'Sieve not recognized: %s'%sieve_name
    #elif sieve_name == 'just susceptible':  #WARNING: might be obsolete
    #    population_filter = lambda x, p: x['Event']=='activated' and float(x['Time'])<2009.0 and x['HCV']=='susceptible'
    #    infection_filter  = lambda x, p: x['Event']=='infected' and x['D2']=="infection started"
    #elif sieve_name == 'injecting < 5 years'
    #    population_filter = lambda x, p: x['Event']=='activated' and float(x['Time'])<2009.0 and (float(x['Age']) - float(x['Age_Started']) < 5)
    #    infection_filter  = lambda x, p: x['Event']=='infected'

    return population_filter

def load_event_files(eventFileNames, population_filter, infection_filter, sieve_name, params): 
    event_header = None
    all_activations = []
    all_infos       = []
    pure_exposures  = []
    first_exposures = {}
    sim_end_times   = []
    sim_populations = []
    #all_activations = {}

    num_activations = 0
    num_preexisting = 0
    total_pre_exp_PY = 0.

    invalid_rows = []
    def valid(x, fparams):
        try:
            i = int(x['Zip'])
        except:
            invalid_rows.append(x)
            return False 
        return True

    for jobNum,fName in enumerate(eventFileNames):
        try:
            fileExt = os.path.splitext(fName)[1]
            assert fileExt == '.csv'

            rows, new_event_header = parseEventFile(fName, qualifying_event = lambda x: True)  #all cases
            for row in rows:
                agent_id = '%d_%d'%(jobNum,int(row['Agent']))
                row['Agent'] = agent_id
            
            #reset the time to skip the burnin
            absolute_sim_start_time = float(rows[0]['Time'])
            sim_start_time = 0
            sim_end_time   = float(rows[-1]['Time']) - absolute_sim_start_time
            for row in rows:
                row['Time'] = row['Time'] - absolute_sim_start_time
            
            fparams = params.copy()
            print 'Processing simulation %s. \nRunning length = %.3f'%(fName,sim_end_time)
            sim_end_times.append(sim_end_time)
            if event_header != None:
                assert event_header == new_event_header
            else:
                event_header = new_event_header
            activations = [x for x in rows if (x['Event']=='activated') and valid(x,fparams) and population_filter(x,fparams)]
            first_activation_time = activations[-1]['Time'] 
            all_activations += activations
            activations = {row['Agent']:row for row in activations}
            print 'activations: %d'%len(activations)

            num_activations += len(activations)
            num_preexisting += sum(1 for x in activations.values() if (x['HCV'] != 'susceptible') and (x['HCV'] != 'recovered'))
            print 'pre-existing hcv: %d'%num_preexisting
    
            #a filter on rows cannot be used for some events, since the timing might be relevant for membership of the sieve
            #  for example, a member of the initial population might be deactivated at time 5.6, which would made the event non-initial
            deactivations = {x['Agent']:x for x in rows if (x['Event']=='deactivated') and valid(x,fparams) and (x['Agent'] in activations)}
            print 'deactivations: %d'%len(deactivations)

            inforeports = [x for x in rows if (x['Event']=='info') and valid(x,fparams) and (x['Agent'] in activations)]
            print 'infos: %d'%len(inforeports)
            inforeports = [x for x in inforeports if (x['Agent'] in activations)]
            print 'infos(purified): %d'%len(inforeports)
            all_infos += inforeports
    
            sim_populations.append(sum(1.0 for x in activations.itervalues() if x['Time'] < 0.001))  #based on the mean

            exposures = [x for x in rows if valid(x,fparams) and (x['Agent'] in activations) and infection_filter(x,fparams)]
            print 'exposures: %d'%len(exposures)
            for expo in exposures:
                #expo['TimeToInf'] = float(expo['N1']) #only for later events: - absolute_sim_start_time 
                #this does not consider burn in: burn-in should be accounted for only in agents which joined after the burn-in closed

                expo['TimeToInf'] = expo['Time'] - activations[expo['Agent']]['Time']

                if expo['Agent'] not in first_exposures:
                    first_exposures[expo['Agent']] = expo
                    total_pre_exp_PY += expo['TimeToInf']
            print 'first exposures: %d'%len(first_exposures)
            total_unexposed = len(activations) - len(first_exposures)

            #count the total pre-exposure person-years
            #consider only primary HCV, and not secondary cases (complex situation b/c of recovery duration)
            for agent_id in activations:
                if agent_id in first_exposures:
                    continue
                if agent_id in deactivations:
                    sojourn_time = deactivations[agent_id]['Time'] - activations[agent_id]['Time']
                else:
                    sojourn_time = sim_end_time - activations[agent_id]['Time']
                total_pre_exp_PY += sojourn_time
            print 'total pre-exposure PY: %.1f'%total_pre_exp_PY
            
            #this is a listing of all events
            pure_exposures += exposures

        except Exception, inst:
            print 'Could not open or parse the events file: %s'%fName
            print inst
            raise
            #return None

    #num_activations = len(all_activations)
    if len(sim_end_times) > 1 and (max(sim_end_times) > 1.1*np.average(sim_end_times) or min(sim_end_times) < 0.9*np.average(sim_end_times)):
        print 'Warning: simulation durations are not uniform: ' + str(sim_end_times)
        print '.. Might cause misclassification because of censoring.'
    if len(sim_populations) > 1 and (max(sim_populations) > 1.5*np.average(sim_populations) or min(sim_populations) < 0.5*np.average(sim_populations)):
        print 'Warning: population sizes are not uniform: ' + str(sim_populations)
        print '.. Might cause misclassification of incidence.'

    if len(pure_exposures) == 0:
        print 'Warning: no exposures were found in the event files'
    if sieve_name != 'Arriving':
        assert num_preexisting > 10 and num_preexisting < num_activations

    mean_population             = np.average(sim_populations)
    num_first_infections        = len(first_exposures)
    cutoff_year                 = 0.95*np.min(sim_end_times)

    times_to_inf     = []
    times_from_start = []
    for row in pure_exposures:
        times_from_start.append(row['Time'])
        times_to_inf.append(row['TimeToInf'])


    ret = {    
        'event_header':event_header,
        'all_infos':all_infos,
        'all_activations':all_activations,
        'pure_exposures':pure_exposures,
        'first_exposures':first_exposures,
        'first_exposures':first_exposures,
        'sim_end_times':sim_end_times,
        'sim_populations':sim_populations,
        'num_activations':num_activations,
        'num_preexisting':num_preexisting,
        'total_pre_exp_PY':total_pre_exp_PY,
    }
    ret['cutoff_year']         = cutoff_year
    ret['sieve_name']          = sieve_name 
    ret['mean_population']     = mean_population 
    ret['num_first_infections'] = num_first_infections 
    ret['times_from_start'] = times_from_start 
    ret['times_to_inf']     = times_to_inf 
    
    return ret;


def load_status_files(statusFileNames, population_filter, infection_filter): 
    event_header = None
    all_baselines = {}  #the description of the agent
    all_histories = {}  #the changes in HCV states
    sim_end_times   = []

    max_time = 1
    for jobNum,fName in enumerate(statusFileNames):
        try:
            rows, new_event_header = parseEventFile(fName, qualifying_event = lambda x: True)  #all cases
            if event_header != None:
                assert event_header == new_event_header
            else:
                event_header = new_event_header
            #reset the time to skip the burnin
            absolute_sim_start_time = float(rows[0]['Time'])
            sim_start_time = 0
            sim_end_time   = float(rows[-1]['Time']) - absolute_sim_start_time
            for row in rows:
                row['Time'] = row['Time'] - absolute_sim_start_time

            print 'Processing simulation %s. \nRunning length = %.3f'%(fName,sim_end_time)
            sim_end_times.append(sim_end_time)
            for row in rows:
                agent_id = '%d_%d'%(jobNum,int(row['Agent']))
                row['Agent'] = agent_id
                agent_time = row['Time']
                if agent_id not in all_baselines:
                    baseline = row.copy()
                    all_baselines[agent_id] = baseline
                    all_histories[agent_id] = [(agent_time,baseline['HCV'])]
                    baseline['_arriving'] = agent_time > 0.01
                else:
                    all_histories[agent_id].append((agent_time,row['HCV']))
                #wishlist: support reports that are out of chronological order or not regular in time

        except Exception, inst:
            print 'Could not open or parse the events file: %s'%fName
            print inst
            raise
            #return None

    max_time = reduce(lambda l1,l2: max(l1,l2), (len(x) for x in all_histories.itervalues()))

    for agent_id in all_histories:
        hist = all_histories[agent_id]
        assert abs((hist[-1][0] - hist[0][0]) - (len(hist)-1)) < 0.1
        all_histories[agent_id] = [hcv_state for t,hcv_state in hist]
    ret = {    
        'event_header':event_header,
        'all_baselines':all_baselines,
        'all_histories':all_histories,
        'sim_end_times':sim_end_times,
        'max_time':max_time,
    }

    return ret


def parseDataFile(path):
#gets the simulation params and data from a populations file
#the population file contains size of various populations, the number of infected and prevalence
    simParams = {}
    timeSeries   = {}

    print 'Reading data file:'
    print path
    try:
        f = open(path, 'r')
    except Exception, inst:
        print inst
        raise IOError, 'Can\'t read run report: ' + path

    paramNamesIdx  = 1
    metricNamesIdx = 4
    paramNames     = []
    paramVals      = []
    metricNames    = None
    rawData        = []
    burninFlagColNum = -1
    NaN_found = False
    try:
        for idx, line in enumerate(f):
            if idx == paramNamesIdx:
                paramNames = re.split(',', line)
                if paramNames[-1].strip() == '':
                    paramNames = paramNames[:-1]
                paramNames = [s.replace('"','') for s in paramNames]
                continue
            elif idx == paramNamesIdx+1:
                paramVals = re.split(',', line)
                if paramVals[-1].strip() == '':
                    paramVals = paramVals[:-1]
                for paramNum, param in enumerate(paramNames):
                    simParams[param] = {'type':'string', 'value':paramVals[paramNum]}  #'string' is incorrect but has no effect
                continue
            elif idx == metricNamesIdx:
                metricNames = re.split(',', line)
                metricNames = [s.replace('"','') for s in metricNames if len(s) > 0 and s!='\n']
                burninFlagColNum = metricNames.index('BurnInMode')
                numSeries = len(metricNames)
                for col in xrange(numSeries):
                    rawData.append([])
                continue
            elif idx > metricNamesIdx:
                if len(line.strip()) == 0:
                    continue #empty line error. wishlist: debug APK
                ar = re.split(',', line)
                if 'NaN' in ar:
                    NaN_found = True
                #    print 'Warning: NaN in %s at line %d'%(path,idx)
                #    #continue
                if ar[burninFlagColNum].strip() == '1':
                    continue #still in burn in
                for col in xrange(numSeries):
                    rawData[col].append(float(ar[col]))
                continue
            elif idx < metricNamesIdx:
                continue  #empty lines in the header
            else:
                raise IOError, 'Malformed header structure!'
        if NaN_found:
            print 'Warning: NaN detected parsing events in %s'%(path,)
    except Exception, inst:
        print 'Parse error in line #'+str(idx)
        print line
        raise
    finally:
        f.close()

    for col in xrange(numSeries):
        timeSeries[metricNames[col]] = rawData[col]

    print 'Loaded %d parameters'%len(paramNames)
    print 'Loaded %d metrics and %d tics'%(len(metricNames),len(rawData[0]))
    return simParams, timeSeries
    #wishlist: diagnoze idiosyncratic parse errors like
    #2014-05-31--09.40.50-288841708/2014-05-31--09.40.50.populations.csv


def parseEventFile(path, qualifying_event):
#gets the simulation events that meet a criterion
    rows = []
    
    print 'Reading events file:'
    print path
    try:
        f = open(path, 'r')
    except Exception, inst:
        print inst
        raise IOError, 'Can\'t read run report: ' + path

    paramNamesIdx  = 1
    metricNamesIdx = np.inf
    metricNames    = []
    rawData        = []
    NaN_found = False
    try:
        for idx, line in enumerate(f):
            if 'Time,EventClass' in line:
                metricNamesIdx = idx
            if idx < metricNamesIdx:
                continue
            elif idx == metricNamesIdx:
                metricNames = re.split(',', line)
                try:
                    metricNames.remove('\n')
                except:pass
                numSeries = len(metricNames)
            elif idx > metricNamesIdx:
                ar       = re.split(',', line)
                ar_float = []
                for val in ar:
                    if val == '\n':
                        continue
                    try:
                        ar_float.append(float(val))
                    except:
                        ar_float.append(val)
                line_dict = dict(zip(metricNames, ar_float))
                if qualifying_event(line_dict):
                    rows.append(line_dict)
                if 'NaN' in ar:
                    NaN_found = True
            else:
                raise IOError, 'Malformed header structure!'
        if NaN_found:
            print 'Warning: NaN detected parsing events in %s'%(path,)
    except IOError, inst:
        print 'Parse error in line #'+str(idx)
    finally:
        f.close()


    print 'Loaded %d metrics and %d events'%(len(metricNames),len(rows))
    return rows, metricNames


def plot_means(report, outputDir, params):
    import pylab
    fName  = report['fName']
    timeSeries = report['timeSeries']
    start_year = params.get('start_year', 2010.0)

    plotting_round = ['means']
    buckets = focus_categories.items()
    buckets.sort(key=lambda x:x[1].get('sorting', 0))
    if plotting_round != None:
        buckets = [(bucket, bucket_data) for bucket, bucket_data in buckets if not set(plotting_round).isdisjoint(bucket_data.get('round', []))]
    for bucket, bucket_data in buckets:
        figpath = os.path.join(outputDir, 'means_' + os.path.split(fName)[1] + '_'+timeNow()+'.eps') if fName != None else os.path.join(outputDir, 'pop_composition_'+timeNow()+'.eps')
        fig=pylab.figure()
        pylab.hold(True)
        max_time = 0
        days_per_Taxis_tick = 365 #182.5 

        col = bucket
        assert col in timeSeries
        Ys = timeSeries[col][::int(days_per_Taxis_tick)]
        Ts = start_year + np.arange(0, len(timeSeries[col])/365., days_per_Taxis_tick/float(365), dtype=float)
        pylab.plot(Ts, Ys, focus_categories[bucket].get('fmt', 'k-'), markersize=10)
        print '%s: initial=%.4f, final=%.4f, avg=%.4f, Rate=%.4f'%(col, Ys[0], Ys[-1], np.average(Ys), (Ys[-1]-Ys[0])/(Ts[-1]-Ts[0]))
        error_bar_name = col+'_ERRORBAR'
        if error_bar_name in timeSeries:
            yerr = timeSeries[error_bar_name][::int(days_per_Taxis_tick)]
            pylab.errorbar(x=Ts, y=Ys, yerr=yerr, fmt=bucket_data.get('fmt', 'k-'))
        if 'Ts' not in dir():
            return
        pylab.xlabel('Year', fontsize=18)
        #avoid minimization of years in tick labels
        int_years = list(set(round(x) for x in Ts))
        int_years.sort()
        pylab.axes().set_xticks(int_years)
        pylab.axes().set_xticklabels(['%.0f'%(t) for t in int_years])
        #pylab.ylim(0,1)
        label = bucket.split('=')[-1]
        label = bucket_data.get('altName', label)
        pylab.ylabel(label, fontsize=18)
        padding = 0.1*(Ts[-1]-Ts[-2])
        pylab.axes().set_xlim(Ts[0]-padding, Ts[-1]+padding)
        #pylab.xlim(Ts[0],Ts[-1])
        pylab.savefig(figpath)
        pylab.hold(False)
        #pylab.show()
        print 'Written: ' + figpath


def plot_means_joined(report, outputDir, params):
    import pylab
    fName  = report['fName']
    timeSeries = report['timeSeries']
    start_year = params.get('start_year', 2010.0)

    #figpath = os.path.join(outputDir, 'means_' + os.path.split(fName)[1] + '_'+timeNow()+'.png') if fName != None else os.path.join(outputDir, 'pop_composition_'+timeNow()+'.png')
    figpath = os.path.join(outputDir, 'means_' + os.path.split(fName)[1] + '_'+timeNow()+'.eps') if fName != None else os.path.join(outputDir, 'pop_composition_'+timeNow()+'.eps')
    fig=pylab.figure()
    pylab.hold(True)
    max_time = 0
    days_per_Taxis_tick = 365 #182.5 
    plotting_round = ['means']
    buckets = focus_categories.items()
    buckets.sort(key=lambda x:x[1].get('sorting', 0))
    if plotting_round != None:
        buckets = [(bucket, bucket_data) for bucket, bucket_data in buckets if not set(plotting_round).isdisjoint(bucket_data.get('round', []))]
    for bucket, bucket_data in buckets:
        col = bucket
        assert col in timeSeries
        Ys = timeSeries[col][::int(days_per_Taxis_tick)]
        Ts = start_year + np.arange(0, len(timeSeries[col])/365., days_per_Taxis_tick/float(365), dtype=float)
        label = bucket.split('=')[-1]
        label = bucket_data.get('altName', label)
        pylab.plot(Ts, Ys, focus_categories[bucket].get('fmt', 'k-'), label=label, markersize=10)
        print '%s: initial=%.4f, final=%.4f, avg=%.4f, Rate=%.4f'%(col, Ys[0], Ys[-1], np.average(Ys), (Ys[-1]-Ys[0])/(Ts[-1]-Ts[0]))
        error_bar_name = col+'_ERRORBAR'
        if error_bar_name in timeSeries:
            yerr = timeSeries[error_bar_name][::int(days_per_Taxis_tick)]
            pylab.errorbar(x=Ts, y=Ys, yerr=yerr, fmt=bucket_data.get('fmt', 'k-'))
    if 'Ts' not in dir():
        return
    pylab.xlabel('Year', fontsize=18)
    #avoid minimization of years in tick labels
    int_years = list(set(round(x) for x in Ts))
    int_years.sort()
    pylab.axes().set_xticks(int_years)
    pylab.axes().set_xticklabels(['%.0f'%(t) for t in int_years])
    #pylab.ylim(0,1)
    #pylab.ylabel('Fraction of population', fontsize=18)
    pylab.legend(loc='best', prop={'size':12})#'lower right')
    padding = 0.1*(Ts[-1]-Ts[-2])
    pylab.axes().set_xlim(Ts[0]-padding, Ts[-1]+padding)
    #pylab.xlim(Ts[0],Ts[-1])
    pylab.savefig(figpath)
    pylab.hold(False)
    #pylab.show()
    print 'Written: ' + figpath


def plot_pop_composition(report, outputDir, params):
    import pylab
    fName  = report['fName']
    timeSeries = report['timeSeries']
    start_year = params.get('start_year', 2010.0)

    figpath = os.path.join(outputDir, 'pop_composition_' + os.path.split(fName)[1] + '_'+timeNow()+'.eps') if fName != None else os.path.join(outputDir, 'pop_composition_'+timeNow()+'.eps')
    #figpath = os.path.join(outputDir, 'pop_composition_' + os.path.split(fName)[1] + '_'+timeNow()+'.png') if fName != None else os.path.join(outputDir, 'pop_composition_'+timeNow()+'.png')
    fig=pylab.figure()
    pylab.hold(True)
    max_time = 0
    days_per_Taxis_tick = 365 #182.5 
    plotting_round = params.get('plotting_round', None)
    buckets = focus_categories.items()
    buckets.sort(key=lambda x:x[1].get('sorting', 0))
    if plotting_round != None:
        buckets = [(bucket, bucket_data) for bucket, bucket_data in buckets if not set(plotting_round).isdisjoint(bucket_data.get('round', []))]
    for bucket, bucket_data in buckets:
        col = 'fraction_' + bucket
        if bucket == 'ALL=ALL':
            continue 
        if col not in timeSeries:
            print col + ' not in time series. skipping'
            continue
        if 'Other' in col:
            continue  #very small population
        Ys = timeSeries[col][::int(days_per_Taxis_tick)]
        Ts = start_year + np.arange(0, len(timeSeries[col])/365., days_per_Taxis_tick/float(365), dtype=float)
        label = bucket.split('=')[-1]
        label = bucket_data.get('altName', label)
        pylab.plot(Ts, Ys, focus_categories[bucket].get('fmt', 'k-'), label=label, markersize=10)
        print '%s: initial=%.4f, final=%.4f, avg=%.4f, Rate=%.4f'%(col, Ys[0], Ys[-1], np.average(Ys), (Ys[-1]-Ys[0])/(Ts[-1]-Ts[0]))
        error_bar_name = col+'_ERRORBAR'
        if error_bar_name in timeSeries:
            yerr = timeSeries[error_bar_name][::int(days_per_Taxis_tick)]
            pylab.errorbar(x=Ts, y=Ys, yerr=yerr, fmt=bucket_data.get('fmt', 'k-'))
    if 'Ts' not in dir():
        return
    pylab.xlabel('Year', fontsize=20)
    #pylab.axes().ax.xaxis.set_tick_params(labelsize=20)
    #avoid minimization of years in tick labels
    int_years = list(set(round(x) for x in Ts))
    int_years.sort()
    pylab.axes().set_xticks(int_years)
    pylab.axes().set_xticklabels(['%.0f'%(t) for t in int_years])
    pylab.tick_params(labelsize=18)
    pylab.xticks(rotation=50)
    pylab.ylim(0,1)
    pylab.ylabel('Fraction of population', fontsize=18)
    pylab.legend(loc='best', prop={'size':12})#'lower right')
    padding = 0.1*(Ts[-1]-Ts[-2])
    pylab.axes().set_xlim(Ts[0]-padding, Ts[-1]+padding)
    #pylab.xlim(Ts[0],Ts[-1])
    pylab.savefig(figpath)
    pylab.hold(False)
    #pylab.show()
    print 'Written: ' + figpath


def plot_prevalences(report, outputDir, params):
    import pylab
    fName  = report['fName']
    timeSeries = report['timeSeries']
    start_year = params.get('start_year', 2010.0)

    #figpath = os.path.join(outputDir, 'prevalences_' + os.path.split(fName)[1] + '_'+timeNow()+'.png') if fName != None else os.path.join(outputDir, 'prevalences_'+timeNow()+'.png')
    figpath = os.path.join(outputDir, 'prevalences_' + os.path.split(fName)[1] + '_'+timeNow()+'.eps') if fName != None else os.path.join(outputDir, 'prevalences_'+timeNow()+'.eps')
    fig=pylab.figure()
    pylab.hold(True)
    max_time = 0
    days_per_Taxis_tick = 365 #182.5
    plotting_round = params.get('plotting_round', None)
    buckets = focus_categories.items()
    buckets.sort(key=lambda x:x[1].get('sorting', 0))
    if plotting_round != None:
        buckets = [(bucket, bucket_data) for bucket, bucket_data in buckets if not set(plotting_round).isdisjoint(bucket_data.get('round', []))]
    for bucket, bucket_data in buckets:
        col = 'prevalence_' + bucket
        if bucket == 'ALL=ALL':
            col = 'prevalence_ALL'
        if col not in timeSeries:
            print col + ' not in time series. skipping'
            continue
        if bucket not in focus_categories:
            continue
        if plotting_round != None and (set(plotting_round).isdisjoint(focus_categories[bucket].get('round', []))):
            continue
        Ys = timeSeries[col][::int(days_per_Taxis_tick)]
        Ts = start_year + np.arange(0, len(timeSeries[col])/365., days_per_Taxis_tick/float(365), dtype=float)
        label = bucket.split('=')[-1]
        label = bucket_data.get('altName', label)
        pylab.plot(Ts, Ys, bucket_data.get('fmt', 'k-'), label=label, markersize=10)
        print '%s: initial=%.4f, final=%.4f, avg=%.4f, Rate=%.4f'%(col, Ys[0], Ys[-1], np.average(Ys), (Ys[-1]-Ys[0])/(Ts[-1]-Ts[0]))
        error_bar_name = col+'_ERRORBAR'
        if error_bar_name in timeSeries:
            yerr = timeSeries[error_bar_name][::int(days_per_Taxis_tick)]
            pylab.errorbar(x=Ts, y=Ys, yerr=yerr, fmt=bucket_data.get('fmt', 'k-'), markersize=10)
    if 'Ts' not in dir():
        return
    pylab.xlabel('Year', fontsize=18)
    pylab.tick_params(labelsize=18)
    #avoid minimization of years in tick labels
    int_years = list(set(round(x) for x in Ts))
    int_years.sort()
    pylab.axes().set_xticks(int_years)
    pylab.axes().set_xticklabels(['%.0f'%(t) for t in int_years])
    pylab.ylabel('Prevalence (HCV antibody +)', fontsize=18)
    padding = 0.1*(Ts[-1]-Ts[-2])
    pylab.axes().set_xlim(Ts[0]-padding, Ts[-1]+padding)
    #pylab.axes().set_ylim(0, 0.5)
    pylab.xticks(rotation=50)
    pylab.ylim(0,0.6)
    pylab.legend(loc='best', prop={'size':12})#'lower right')
    pylab.savefig(figpath)
    pylab.hold(False)
    #pylab.show()
    print 'Written: ' + figpath



def report_activations(fname, all_activations, categories, stats_string):
    num_activations = len(all_activations)
    metrics = [
               {'name':'Female',               'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Gender'].strip() == 'Female'),                      'val':lambda x:1 },
               {'name':'Male',                 'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Gender'].strip() == 'Male'  ),                      'val':lambda x:1 },
               {'name':'NHBlack',                'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Race'].strip() in ['NHBlack', 'Black']  ),                       'val':lambda x:1 },
               {'name':'Hispanic',             'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Race'].strip() == 'Hispanic'  ),                       'val':lambda x:1 },
               {'name':'NHWhite',              'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Race'].strip() == 'NHWhite'  ),                       'val':lambda x:1 },
               {'name':'Age',                     'func':lambda ar:'%1.1f IQR: %.1f-%.1f'%(np.average(ar),np.percentile(ar,25),np.percentile(ar,75)),                  'filter':lambda x:True,                                                   'val':lambda x:(x['Age']) },
               #{'name':'Age_Started',             'func':lambda ar:'%1.1f IQR: %.1f-%.1f'%(np.average(ar),np.percentile(ar,25),np.percentile(ar,75)),                  'filter':lambda x:True,                                                   'val':lambda x:(x['Age_Started']) },
               {'name':'Length of career',             'func':lambda ar:'%1.1f IQR: %.1f-%.1f'%(np.average(ar),np.percentile(ar,25),np.percentile(ar,75)),                  'filter':lambda x:True,                                                   'val':lambda x:(x['Age']-x['Age_Started']) },
               {'name':'Drug_in_degree',          'func':lambda ar:'%1.1f IQR: %.1f-%.1f'%(np.average(ar),np.percentile(ar,25),np.percentile(ar,75)),                   'filter':lambda x:True,                                                   'val':lambda x:(x['Drug_in_degree']) },
               {'name':'Drug_out_degree',          'func':lambda ar:'%1.1f IQR: %.1f-%.1f'%(np.average(ar),np.percentile(ar,25),np.percentile(ar,75)),                   'filter':lambda x:True,                                                   'val':lambda x:(x['Drug_out_degree']) },
               {'name':'Daily_injection_intensity','func':lambda ar:'%1.2f IQR: %.2f-%.2f'%(np.average(ar),np.percentile(ar,25),np.percentile(ar,75)),                  'filter':lambda x:True,                                                 'val':lambda x:(x['Daily_injection_intensity']) },
               {'name':'Fraction_recept_sharing', 'func':lambda ar:'%1.2f IQR: %.2f-%.2f'%(np.average(ar),np.percentile(ar,25),np.percentile(ar,75)),                   'filter':lambda x:True,                                                   'val':lambda x:(x['Fraction_recept_sharing']) },
               {'name':'HCV Infected (RNA+)',       'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['HCV'].strip() not in ['susceptible', 'recovered']), 'val':lambda x:1 },
               {'name':'HCV Recovered (RNA-)',      'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['HCV'].strip() == 'recovered'),                      'val':lambda x:1 },
               {'name':'North Side',           'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Zip'] in NorthSide),                      'val':lambda x:1 },
               {'name':'South Side',           'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Zip'] in SouthSide),                      'val':lambda x:1 },
               {'name':'West Side',           'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Zip'] in WestSide),                      'val':lambda x:1 },
               {'name':'Suburban',           'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(str(int(x['Zip']))[:3] != '606'),                      'val':lambda x:1 },
               {'name':'City of Chicago',    'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(str(int(x['Zip']))[:3] == '606'),                      'val':lambda x:1 },
               {'name':'HR',               'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Syringe_source'].strip() in ['NEP', 'HR', 'COIP']),  'val':lambda x:1 },
               {'name':'nonHR',               'func':lambda ar:(float(len(ar))/num_activations),  'filter':lambda x:(x['Syringe_source'].strip() not in ['NEP', 'HR', 'COIP']),  'val':lambda x:1 },
              ]
    with open(fname, 'w') as dossier_file:
        dossier_file.write(stats_string)
        header = 'Class,ClassLimits,#Agents,Values'
        echo(header, dossier_file)
        
        for metric in metrics:
            filtered = [metric['val'](x) for x in all_activations if metric['filter'](x)]
            line = '%s,%s,%d,%s,'%(metric['name'],'',len(filtered),metric['func'](filtered))
            echo(line, dossier_file)

        dossier = {}
        for row in all_activations:
            for cat in categories:  
                try:
                    bucket = cat['buckets'](row)
                    if bucket in dossier:
                        dossier[bucket].append(row)
                    else:
                        dossier[bucket] = [row]
                except:
                    print 'Skipping bucket: '+bucket

        bucket_names = dossier.keys()
        bucket_names.sort()
        for bucket in bucket_names:
            bucket_val = bucket.split('=')[1]
            bucket_size = len(dossier[bucket])
            line = '%s,%s,%d,%.5f,'%(bucket,bucket_val,bucket_size,float(bucket_size)/num_activations)
            echo(line, dossier_file)


        print 'writing: %s'%dossier_file.name
        #wishlist: hypothetically, we could even juxtapose groups for absolute time (over the simulation) for each group to be infected.

def report_activations_multisieve(eventFileNames, outputDir, params):
    if len(eventFileNames) == 0:
        print('   0 event files.  Exiting ...')
        sys.exit(1)

    #buckets generates a string.  it must match, or a new bucket is made
    #categories are like dimensions, and buckets are like values along that dimension
    metrics = [{'name':'Total',                   'func':lambda ar,na:na,                               'filter':lambda x:(x['HCV'].strip() not in ['susceptible', 'recovered']), 'val':lambda x:1 },
               {'name':'Female pc',                  'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),  'filter':lambda x:(x['Gender'].strip() == 'Female'),                      'val':lambda x:1 },
               {'name':'Male pc',                    'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),  'filter':lambda x:(x['Gender'].strip() == 'Male'  ),                      'val':lambda x:1 },
               {'name':'NHBlack pc',                   'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),  'filter':lambda x:(x['Race'].strip() in ['Black', 'NHBlack']  ),        'val':lambda x:1 },
               {'name':'Hispanic pc',                'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),  'filter':lambda x:(x['Race'].strip() == 'Hispanic'  ),                       'val':lambda x:1 },
               {'name':'NHWhite pc',                 'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),  'filter':lambda x:(x['Race'].strip() == 'NHWhite'  ),                       'val':lambda x:1 },
               {'name':'EAge',                     'func':lambda ar,na:'%0.1f'%(np.average(ar),),         'filter':lambda x:True,                                                   'val':lambda x:(x['Age']) },
               #{'name':'EAge Started',             'func':lambda ar,na:'%0.1f'%(np.average(ar),),         'filter':lambda x:True,                                                   'val':lambda x:(x['Age_Started']) },
               {'name':'ELength of career',         'func':lambda ar,na:'%0.1f'%(np.average(ar),),         'filter':lambda x:True,                                                   'val':lambda x:(x['Age']-x['Age_Started']) },
               {'name':'EDaily injections',         'func':lambda ar,na:'%0.2f'%(np.average(ar),),         'filter':lambda x:True,                                       'val':lambda x:(x['Daily_injection_intensity']) },
               {'name':'EFraction recept sharing', 'func':lambda ar,na:'%0.3f'%(np.average(ar),),         'filter':lambda x:True,                                        'val':lambda x:(x['Fraction_recept_sharing']) },
               {'name':'EDrug in network',          'func':lambda ar,na:'%0.2f'%(np.average(ar),),         'filter':lambda x:True,                                                   'val':lambda x:(x['Drug_in_degree']) },
               {'name':'EDrug out network',          'func':lambda ar,na:'%0.2f'%(np.average(ar),),         'filter':lambda x:True,                                                   'val':lambda x:(x['Drug_out_degree']) },
               #{'name':'North Side',              'func':lambda ar,na:(float(len(ar))/na),  'filter':lambda x:(x['Zip'] in NorthSide),                      'val':lambda x:1 },
               #{'name':'South Side',              'func':lambda ar,na:(float(len(ar))/na),  'filter':lambda x:(x['Zip'] in SouthSide),                      'val':lambda x:1 },
               #{'name':'West Side',               'func':lambda ar,na:(float(len(ar))/na),  'filter':lambda x:(x['Zip'] in WestSide),                      'val':lambda x:1 },
               #{'name':'City of Chicago',                    'func':lambda ar,na:(float(len(ar))/na),  'filter':lambda x:(str(int(x['Zip']))[:3] == '606'),                      'val':lambda x:1 },
               #{'name':'Suburban',                'func':lambda ar,na:(float(len(ar))/na),  'filter':lambda x:(str(int(x['Zip']))[:3] != '606'),                      'val':lambda x:1 },
               {'name':'HR pc',                  'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),  'filter':lambda x:(x['Syringe_source'].strip() in ['NEP', 'HR', 'COIP']),  'val':lambda x:1 },
               {'name':'nonHR pc',                  'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),  'filter':lambda x:(x['Syringe_source'].strip() not in ['NEP', 'HR', 'COIP']),  'val':lambda x:1 },
               {'name':'Infected pc',             'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),          'filter':lambda x:(x['HCV'].strip() not in ['susceptible', 'recovered']), 'val':lambda x:1 },
               {'name':'Recovered pc',            'func':lambda ar,na:'%0.1f%%'%(100*(float(len(ar))/na)),          'filter':lambda x:(x['HCV'].strip() == 'recovered'),                      'val':lambda x:1 },
              ]

    sieves = [#{'sieve_name':'ALL'},
               {'sieve_name':'Initial'},
               {'sieve_name':'Arriving'},
               {'sieve_name':'NorthSide'},
               {'sieve_name':'SouthSide'},
               #{'sieve_name':'WestSide'},
               #{'sieve_name':'City of Chicago'},
               {'sieve_name':'Suburban'},
               {'sieve_name':'Under 30'},
               {'sieve_name':'Over 30'},
               {'sieve_name':'HR'},
               {'sieve_name':'nonHR'},
             ]

    infection_filter  = lambda x, p: x['Event']=='infected' 

    header = [s['sieve_name'] for s in sieves]
    row_names = [m['name'] for m in metrics]
    rows = [[] for m in metrics]
    for sieve_data in sieves:
        sieve_name          = sieve_data['sieve_name']
        print sieve_name
        population_filter   = get_population_filter(sieve_name, params)    
        event_reports       = load_event_files(eventFileNames, population_filter, infection_filter, sieve_name, params)
        all_activations     = event_reports['all_activations']
        num_activations     = len(all_activations)

        for metidx, metric in enumerate(metrics):
            filtered = [metric['val'](x) for x in all_activations if metric['filter'](x)]
            val = metric['func'](filtered, num_activations)
            rows[metidx].append(val)

    table_str = writeHtmlTable(header=header, row_names=row_names, rows=rows)
    with open(os.path.join(outputDir, 'activations_multisieve_'+timeNow()+'.html'), 'w') as f:
        f.write(table_str)
        print 'Written: '+f.name


def report_incidence_multisieve(eventFileNames, outputDir, params):
    if len(eventFileNames) == 0:
        print('   0 event files.  Exiting ...')
        sys.exit(1)

    sieves = [{'sieve_name':'ALL'},
               {'sieve_name':'Initial'},
               {'sieve_name':'Arriving'},
               {'sieve_name':'NorthSide'},
               {'sieve_name':'SouthSide'},
               {'sieve_name':'WestSide'},
               {'sieve_name':'City of Chicago'},
               {'sieve_name':'Suburban'},
               {'sieve_name':'NHBlack'},
               {'sieve_name':'Hispanic'},
               {'sieve_name':'NHWhite'},
               {'sieve_name':'Male'},
               {'sieve_name':'Female'},
               {'sieve_name':'Under 30'},
               {'sieve_name':'Over 30'},
               {'sieve_name':'HR'},
               {'sieve_name':'nonHR'},
               #{'sieve_name':'YCC'},
               #{'sieve_name':'YoungArriving'},
               #{'sieve_name':'YoungWhiteArriving'},
               #{'sieve_name':'YoungWhiteArrivingNet'},
               #{'sieve_name':'YoungArrivingNet'},  #most interesting
               {'sieve_name':'Network'},
               #{'sieve_name':'NetworkArriving'},
             ]
    sieve_short_names = {
               'NorthSide':'N.Side',
               'SouthSide':'S.Side',
               'WestSide':'W.Side',
               'City of Chicago':'City',
    }

    metrics = [{'name':'IncidencePer100PY',     'func':lambda d:d['incidence_rate_per100']},
               {'name':'TotalIncidencePer100P', 'func':lambda d:d['total_incidence_per100']}]

    infection_filter  = lambda x, p: x['Event']=='infected' 

    header = [s['sieve_name'] for s in sieves]
    row_names = [m['name'] for m in metrics]
    rows = [[] for m in metrics]
    incidence_rates = []
    for sieve_data in sieves:
        sieve_name          = sieve_data['sieve_name']
        print sieve_name
        population_filter   = get_population_filter(sieve_name, params)    
        event_reports       = load_event_files(eventFileNames, population_filter, infection_filter, sieve_name, params)
        incidence_data      = calculate_incidence(event_reports)
        incidence_rates.append(incidence_data['incidence_rate_per100'])
        for metidx, metric in enumerate(metrics):
            val = metric['func'](incidence_data)
            rows[metidx].append(val)

    table_str = writeHtmlTable(header=header, row_names=row_names, rows=rows)
    with open(os.path.join(outputDir, 'incidence_multisieve_'+timeNow()+'.html'), 'w') as f:
        f.write(table_str)
        print 'Written: '+f.name

    #https://stackoverflow.com/questions/7423445/how-can-i-display-text-over-columns-in-a-bar-chart-in-matplotlib
    figpath = os.path.join(outputDir, 'incidence_multisieve_'+timeNow()) + '.eps'
    import matplotlib.pyplot as plt
    plt.figure()
    rects = plt.barh(range(len(sieves),0,-1), incidence_rates, color='grey',)
    for ii,rect in enumerate(rects):
        #plt.text(rect.get_x()+rect.get_width()/2., 1.02*height, '%s'% (sieves[ii]['sieve_name']), ha='center', va='bottom', )
        sname = sieve_short_names.get(sieves[ii]['sieve_name'], sieves[ii]['sieve_name'])
        #xtext = xtext = rect.get_width()+0.01
        #txtcolor = 'k'
        if rect.get_width() > 0.3*max(rect.get_width() for rect in rects):
            xtext = 0.02
            txtcolor = 'w'
        else:
            xtext = rect.get_width() + 0.01
            txtcolor = 'k'
        plt.text(xtext, rect.get_y()+rect.get_height()/2., sname, ha='left', va='center', color=txtcolor, fontsize=12)
    #pylab.axes().set_xticklabels([s['sieve_name'] for s_data in sieves])
    plt.tick_params(labelsize=18)
    plt.yticks([])
    plt.xlabel('HCV incidence per 100 person-years', fontsize=18)
    plt.savefig(figpath)
    print 'Written: ' + figpath
    plt.show()

    return sieves, metrics, rows

def report_exposure_histogram_incidence(event_reports, outputDir, dossier_file):
    import pylab
    num_activations  = event_reports['num_activations']
    num_preexisting  = event_reports['num_preexisting']
    mean_population  = event_reports['mean_population']
    cutoff_year      = event_reports['cutoff_year']

    incidence_data = calculate_incidence(event_reports)
    echo(str(incidence_data), dossier_file)
    num_infections         = incidence_data['num_infections']
    num_never_inf          = incidence_data['num_never_inf']
    incidence_rate_per100  = incidence_data['incidence_rate_per100']
    total_incidence_per100 = incidence_data['total_incidence_per100']
    pc_never_inf           = incidence_data['pc_never_inf']

    #wishlist: incidence over time could be estimated from age_started-age of the agent
    echo('Activations: %d, Preexisting: %d, Naive never infected: %d\t(%.1f%%)'%(num_activations, num_preexisting, num_never_inf, pc_never_inf),  dossier_file)
    echo('Total incidence (per100), %.4f, Incidence (per 100PY), %.4f, '%(total_incidence_per100,incidence_rate_per100,),  dossier_file)

    bins_per_year = 3.
    times_from_start = event_reports['times_from_start']
    exposure_bins = np.arange(0, cutoff_year+0.01, 1.0/bins_per_year)
    exposure_hist = np.histogram(times_from_start, exposure_bins)[0]
    echo('Incidence', dossier_file)
    echo('<Time(y),Count,Rate(\%)\n', dossier_file)
    for b_idx, b in enumerate(exposure_bins[1:]):
        echo('%.5f,%d,%.5f,pc'%(b, exposure_hist[b_idx], 100*float(exposure_hist[b_idx])/mean_population), dossier_file)
    dossier_file.write('\n')
    pylab.figure()
    pylab.bar(exposure_bins[:-1], exposure_hist/float(sum(exposure_hist)), width=4.0/len(exposure_bins))
    pylab.xlabel('Months from initiation', fontsize=18)
    pylab.ylabel('Fraction of events', fontsize=18)
    text_pos = max(exposure_hist)/float(sum(exposure_hist))
    pos_bin  = len(exposure_bins)/4
    #pylab.text(exposure_bins[pos_bin], text_pos*0.95, 'Cross-section: ' + event_reports['sieve_name'])
    #pylab.text(exposure_bins[pos_bin], text_pos*0.90, 'Mean population: %d, num infections: %d'%(mean_population,num_infections))
    #pylab.text(exposure_bins[pos_bin], text_pos*0.85, 'incidence (per100): total %.2f, rate %.2f, '%(total_incidence_per100,incidence_rate_per100,))
    pylab.xlim(-(exposure_bins[2]-exposure_bins[0]), cutoff_year)

    xticks = np.array(np.arange(1/bins_per_year, 1.001, 1/bins_per_year).tolist() + range(2,int(cutoff_year)))
    colwidth = 5.0/len(exposure_bins)
    pylab.axes().set_xticks(xticks - colwidth)
    #pylab.axes().set_xticks(xticks)
    #pylab.axes().set_xticklabels([int(12*(x)) for x in np.array(xticks)])
    xticks = np.array(np.arange(1/bins_per_year, 1.001, 1/bins_per_year).tolist() + range(2,int(cutoff_year)))
    pylab.axes().set_xticks(xticks - colwidth)
    pylab.axes().set_xticklabels([int(12*(x)) for x in np.array(xticks)])

    figpath = os.path.join(outputDir, 'infections_over_time_sieve=' + event_reports['sieve_name']+ '_' + timeNow()) + '.eps'
    pylab.savefig(figpath)
    #pylab.show()
    print 'Written: ' + figpath
    return exposure_bins, exposure_hist, 

def report_exposure_histogram_waiting(event_reports, outputDir, dossier_file):
    import pylab
    num_activations  = event_reports['num_activations']
    num_preexisting  = event_reports['num_preexisting']
    mean_population  = event_reports['mean_population']
    cutoff_year      = event_reports['cutoff_year']

    incidence_data = calculate_incidence(event_reports)
    num_infections         = incidence_data['num_infections']
    num_never_inf          = incidence_data['num_never_inf']
    incidence_rate_per100  = incidence_data['incidence_rate_per100']
    total_incidence_per100 = incidence_data['total_incidence_per100']
    pc_never_inf           = incidence_data['pc_never_inf']

    times_to_inf = event_reports['times_to_inf']
    bins_per_year = 3.
    waiting_bins = np.arange(0, cutoff_year+0.01, 1.0/bins_per_year)
    waiting_hist = np.histogram(times_to_inf, waiting_bins)[0]
    echo('Waiting time to exposure', dossier_file)
    echo('<Time(y),Count,Rate(\%)\n', dossier_file)
    for b_idx, b in enumerate(waiting_bins[1:]):
        echo('%.5f,%d,%.5f,pc'%(b, waiting_hist[b_idx], 100*float(waiting_hist[b_idx])/num_infections), dossier_file)
    dossier_file.write('\n')
    pylab.figure()
    colwidth = 5.0/len(waiting_bins)
    pylab.bar(waiting_bins[:-1], waiting_hist/float(sum(waiting_hist)), width=colwidth)
    pylab.xlabel('Months from initiation', fontsize=18)
    #pylab.xlabel('Years from initiation into injection drug use', fontsize=18)
    pylab.ylabel('Fraction of cases', fontsize=18)
    text_pos = max(waiting_hist)/float(sum(waiting_hist))
    pos_bin  = len(waiting_bins)/4
    #pylab.text(waiting_bins[pos_bin], text_pos*0.95, 'Cross-section: ' + event_reports['sieve_name'])
    #pylab.text(waiting_bins[pos_bin], text_pos*0.90, 'Mean population: %d, num infections: %d'%(mean_population,num_infections))
    #pylab.text(waiting_to_exposure_bins[pos_bin], text_pos*0.80, 'annual incidence (per 100): %.2f'%(100*annual_incidence, ))
    #pylab.xlim(-(waiting_bins[2]-waiting_bins[0]), cutoff_year)
    pylab.xlim(0, cutoff_year)

    xticks = np.array(np.arange(1/bins_per_year, 1.001, 1/bins_per_year).tolist() + range(2,int(cutoff_year)))
    pylab.axes().set_xticks(xticks - colwidth)
    pylab.axes().set_xticklabels([int(12*(x)) for x in np.array(xticks)])
    pylab.tick_params(labelsize=19)

    figpath = os.path.join(outputDir, 'waiting_distrib_sieve=' + event_reports['sieve_name'] + '_' + timeNow()) + '.eps'
    pylab.savefig(figpath)
    print 'Written: ' + figpath

    #pylab.show()
    return waiting_bins, waiting_hist


def report_prevalence_by_durations(fname, all_activations, all_infos, stats_string, cutoff_year):
#obsolete for the paper
#parse the status report at the turn of every simulation year
    categories = [{'name':'YearsIDU',                'buckets':lambda row:'YearsIDU='+('%02d'%((int(row['Age'])-int(row['Age_Started']))))}
                 ]
    num_reports = len(all_infos)
    with open(fname, 'w') as dossier_file:
        dossier_file.write(stats_string)
        header = 'Class,ClassLimits,#Infected,#Agents,#preval'
        echo(header, dossier_file)
        try:
            for check_time in xrange(1,int(np.floor(cutoff_year))):
                echo('---YEAR=%d---'%check_time, dossier_file)
                end_year_info_reports = [row for row in all_infos if abs(row['Time']-check_time)<0.1]
                dossier = {}
                for row in end_year_info_reports:
                    for cat in categories:
                        try:
                            bucket = cat['buckets'](row)
                            if bucket in dossier:
                                dossier[bucket].append(row)
                            else:
                                dossier[bucket] = [row]
                        except:
                            print 'Skipping category: '+bucket

                bucket_names = dossier.keys()
                bucket_names.sort()
                for bucket in bucket_names:
                    bucket_val = bucket.split('=')[1]
                    bucket_size     = len(dossier[bucket])
                    bucket_infected = sum(1.0 for row in dossier[bucket] if row['HCV'].strip().lower() not in ['susceptible', 'recovered'])
                    prevalence = bucket_infected/bucket_size if bucket_size > 0 else np.nan
                    line = '%s,%s,%d,%.5f,%.5f'%(bucket,bucket_val,bucket_infected,bucket_size,prevalence)
                    echo(line, dossier_file)
        except Exception, inst:
            print inst
            raise


        print 'writing: %s'%dossier_file.name
        #wishlist: hypothetically, we could even juxtapose groups for absolute time (over the simulation) for each group to be infected.


def reportLHS_BatchStats(pklFileNames, params):
    dossier = []
    if 'dossier_summaries_path' in params:
        print 'Skipping loading of individual files.  Will load summaries from pkl'
        return []

    for jobNum,fName in enumerate(pklFileNames):
        try:
            fileExt = os.path.splitext(fName)[1]
            if fileExt == '.pkl':
                pFile     = open(fName, 'rb')
                report    = pickle.load(pFile)
            elif fileExt == '.csv':
                raise Exception, 'Not implemented'                
            dossier.append(report)
        except Exception, inst:
            print 'Couln\'t open data in file: ' + fName
            print inst
            raise
        finally:
            pFile.close()

    if len(dossier) == 0:
        print 'Dossier is empty!'
        return

    return dossier


def reportBatchStats(dataFileNames, params):
    dossier = []
    if 'dossier_summaries_path' in params:
        print 'Skipping loading of individual files.  Will load summaries from pkl'
        return []

    burnin = params.get('burnin', 0)
    total_errors = 0
    for jobNum,fName in enumerate(dataFileNames):
        sys.stdout.write('%d: '%jobNum)
        try:
            fileExt = os.path.splitext(fName)[1]
            assert fileExt == '.csv'
            
            simParams, timeSeries = parseDataFile(fName)
            means, stds           = analyzeRunData(timeSeries, burnin, fName)
            report      = {'sample': simParams,
                           'timeSeries': timeSeries,
                           'fName': fName,
                           'metrics':{'means':means, 'stds':stds}
                          }

            dossier.append(report)

        except Exception, inst:
            print 'Could not open the data file: %s'%fName
            print inst
            #return None
            total_errors += 1
    print 'Total errors: %d out of %d'%(total_errors, len(dataFileNames))
    if float(total_errors)/(len(dataFileNames)+0.01) > 0.05:
        print 'Too many errors - existing'
        sys.exit(1)
    time.sleep(1)
    return dossier



def reportStatus(runData, progressFraction, params):
    import time

    try:
        fname   = 'jobStatus_%s__Completion=%.2f'%(timeNow(),progressFraction)
        print
        print fname

        f       = open(os.path.join(runData['dataDir'], fname), 'w')
        #creates a file
        f.close()
    except Exception, inst:
        print inst
    return 0

    '''
    #these work only if a mail server is available. 
    import smtplib
    import email.Message
    emailTxt = 'Job is now %f%% complete'%progressFraction

    sender ='LHS batch'
    to     ='myemail@gmail.com' 
    subject='batch progress'
    serverURL='localhost'
    message = email.Message.Message()
    message["To"]      = to
    message["From"]    = sender
    message["Subject"] = subject
    message.set_payload(emailTxt)
    mailServer = smtplib.SMTP(serverURL)
    mailServer.sendmail(sender, to, message.as_string())
    mailServer.quit()
    '''

def writeHtmlTable(header, rows, row_names=None, numRows=-1):
    # writes a table in LaTeX.  
    #header  = a list of strings
    #rows    = a list of list of strings
    #numRows = maximal row (to truncate) or -1 to write all of them
    
    if row_names != None:
        row_names = [''] + row_names
    outStr = r'<table WIDTH=100% BORDER=0 CELLPADDING=4 CELLSPACING=0>' + os.linesep
    for rowNum,row in enumerate([header] + rows):
        outStr += r'<tr>' + os.linesep
        if rowNum == numRows: 
            break
        outStr += r'   <td>%s</td>'%('' if row_names == None else row_names[rowNum])
        for s in row:
            outStr += r'   <td>%s</td>'%str(s) + os.linesep
        outStr += r'</tr>' + os.linesep
    outStr += r'</table>' + os.linesep
    
    return outStr

