'''
 * Copyright (c) 2011-2014, Alexander Gutfraind
 *  All rights reserved.
 *  Details in license.txt

Implementation of lower level code 
* analysis of network data

'''

import sys
import numpy
import numpy as np
import numpy.random as npr
import networkx as nx
import random
import re, os, sys, time, pickle, subprocess, pdb, cPickle
import elementtree.ElementTree as ET

timeNow = lambda : time.strftime('%Y_%m_%d__%H_%M_%S', time.localtime())
def echo(s,f):
    sys.stdout.write(s + os.linesep)
    f.write(s + os.linesep)

def analyze_networks(net_paths, params):
    print '------------------------'
    print '--  NETWORK ANALYSIS  --'
    print '------------------------'
    networks = [parse_network(path, params) for path in net_paths]
    print [net.name for net in networks]
    #nx.write_edgelist('/tmp/1.edges', networks[0])
    #G = nx.read_edgelist('/tmp/1.edges', create_using=nx.DiGraph())

    for G in networks:
        report_stats(G, params)

    return networks

def parse_network(path, params):
    print 'Reading: ' + path
    
    G = nx.DiGraph()
    paramNamesIdx  = 1
    edgeHeaderIdx  = 4
    paramNames     = []
    paramVals      = []
    headerNames    = None
    rawData        = []
    simParams      = {}

    try:
        f = open(path, 'r')
    except Exception, inst:
        print inst
        raise IOError, 'Can\'t read run report: ' + path
    edges = []
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
                    simParams[param] = paramVals[paramNum]  #'string' is incorrect but has no effect
                continue
            elif idx == edgeHeaderIdx:
                headerNames = re.split(',', line)
                headerNames = [s.replace('"','') for s in headerNames if len(s) > 0 and s!='\n']
                numSeries = len(headerNames)
                #for col in xrange(numSeries):
                #    rawData.append([])
                continue
            elif idx > edgeHeaderIdx:
                if len(line.strip()) == 0:
                    continue #empty line error. wishlist: debug APK
                ar = re.split(',', line)
                if 'NaN' in ar:
                    print 'Warning: NaN in %s at line %d'%(path,idx)
                    continue
                edge_description = {}
                for col_idx, name in enumerate(headerNames):
                    val = ar[col_idx]
                    try:
                        val = long(val)
                    except:
                        try:
                            val = float(val)
                        except:
                            pass
                    edge_description[name] = val
                edges.append(edge_description)
                continue
            elif idx < edgeHeaderIdx:
                continue  #empty lines in the header
            else:
                raise IOError, 'Malformed header structure!'
    except Exception, inst:
        print 'Parse error in line #'+str(idx)
        print line
        raise
    finally:
        f.close()

    for param in simParams:
        val = simParams[param]
        try:
            val = long(val)
            paramType = 'long'
        except:
            try:
                val = float(val)
                paramType = 'float'
            except:
                paramType = 'str'
        simParams[param] = {'type':paramType, 'value':val}

    for edge in edges:
        node1 = edge['Agent1']
        node2 = edge['Agent2']
        if node1 not in G:
            G.add_node(node1)
        if node2 not in G:
            G.add_node(node2)
        G.add_edge(node1,node2)
        #wishlist: add various details from the dict to the nodes ...

    target_new_nodes = simParams['initial_pwid_count']['value'] - G.number_of_nodes()
    num_new_nodes = 0
    while num_new_nodes < target_new_nodes:
        node = 'singleton%d'%npr.randint(1E9)
        if node not in G:
            G.add_node(node)
            num_new_nodes += 1

    G.name = 'Graph_'+path
    G.simParams = simParams

    print 'Loaded G.  Nodes: %d.  Edges: %d'%(G.number_of_nodes(), G.number_of_edges())
    print 'Loaded %d parameters'%len(paramNames)
    return G

def report_stats(G, params):
    print 'Nodes: %d.  Edges: %d'%(G.number_of_nodes(), G.number_of_edges())
    sccs = nx.strongly_connected_components(G)
    wccs = nx.weakly_connected_components(G)
    print 'Strongly ccs: %d, Weakly ccs: %d'%(len(sccs), len(wccs))

    sizes_sccs, sizes_wccs = ([len(c) for c in sccs], [len(c) for c in wccs])
    print 'Singletons. Strongly: %d, Weakly: %d'%(sum(np.array(sizes_sccs)==1), sum(np.array(sizes_wccs)==1))
    print [len(c) for c in sccs[:10]]
    avg_degree = np.average(G.degree().values())
    print 'AvgDegree: %.3f'%(avg_degree,)



