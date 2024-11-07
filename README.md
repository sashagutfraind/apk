Summary
-------
HepCEP (formerly APK) simulates the PWID population in metropolitan Chicago, including the social interactions that result in HCV infection

References: 
- Gutfraind, Boodram et al. 2015 "Agent-Based Model Forecasts Aging of the Population of People Who Inject Drugs in Metropolitan Chicago and Changing Prevalence of Hepatitis C Infections". PLoS ONE https://doi.org/10.1371/journal.pone.0137993 
- Mackesy-Amiti, Gutfraind et al. 2024. "Modeling of randomized hepatitis C vaccine trials: bridging the gap between controlled human infection models and real-word testing" to appear.

Installing HepCEP
--------------------------------------
To install the software, click on the setup jar file, or from the command line use java -jar APK_SETUP.jar

System Requirements
* Java SE (JDK 11)
* Compiled in Eclipse 2021-06 (4.20.0) with RepastSimphony-2.9.1
* For optional graphics (geographic network visualization): 
	- Live Internet connection
	- Certain java graphics libraries (Java3D,jogl)
	- Graphics card and drive that supports advanced features (textures, OpenGL)

Warning: the authors normally run the simulation in batch mode from cnep data
Other execution modes and functionalities are stale

Data files
----------
* the simulation normally runs from CNEP+ data: data/cnep_plus_all_2018.02.13.csv
* alternatively, it can run from raw data in NEP and NHBS files 
* datagen folder constructs synthetic NEP and NHBS files


Running in batch mode
-------------------------
1. adjust the parameters file and save them in batch/batch_params.xml

2. run batch.bat (Windows) or batch.command (Unix) 
See the output in APK\output

Make sure to copy the files from this directory, otherwise


Running from the GUI (deprecated)
-------------------------
* Start the program by running start_model.bat (Windows) or start_model.command (Unix and Mac)

* Before starting the simulation, you can change the parameters in the parameters tab.

* To set up a new simulation, click on the "Power" button on the top bar.  
Now you can use the buttons Play, Pause, Fast, Stop, and Reset.

* The globe window supports three layers of the simulation.  
It is recommended that the map display is turned off when running large-scale simulations.
IDU - the persons who inject drugs, also termed "agents" or "IDUs".  Clicking on one of them would bring out his or her individual profile.
ZoneAgent - ZIP codes showing the geographic areas.  Clicking on one of them would show the prevalence in that area
LinkAgent - the person-to-person relationships in the injecting drug user network.

* Agent color indicates HCV status: 
Blue (susceptible), 
Red (HCV RNA+ in either acute or chronic stage), 
Green (experienced resistant)

* See the overall state of a running simulation with the Epidemiology tab

* input population data
** NEP-plus dataset (not for public distribution only) should be selected for most tasks
** The datagen folder contains synthetic data and the data generator that created it.
Original (HIPAA-protected) data might be available upon written agreement from the developers

* Simulation output is saved in APK/output.  
** Configure the output by using the "verbosity" parameter (e.g. population+events)
** events.csv: major events occurring to each agent (most detailed)
** population.csv: daily summary of the population status
** statusRegular.csv: states of all the agents, reported each year (i.e. on ticks 365, 2*365, 3*365 etc)
** status.csv: status of all the agents reported each year from the time they were activated

Questions:
------------
Please report any problem to Alexander Gutfraind:  agutfraind.research@gmail.com

Indicate
1. your operating system and version of java (java -version)
2. the steps you took to reach the error, to the extent you can remember
3. is the error reproducible?
4. the error log information, if available or the output of the pgroam 
(The error log records certain errors.  You can examine it by clicking on the bottom right of the graphical screen).


Troubleshooting
--------------------------------------
Installer does not start
* check that you have Java version 7 or higher

Simulation does not start
* check that you have Java version 7 or higher
* java must be on your system PATH.  Please get help online.
If you can access the command line, check java by running:
java -version

Graphics problems
* Make sure you have OpenGL and Java3D libraries (Jogl and Java3D)
http://repast.sourceforge.net/download-extras.html

* In Linux and other systems, the visualization might not work due to special problems with licensing of graphic libraries.
Consider running with "-force_s3tc_enable=true"

* You need an active Internet connection to see the globe and other maps.

* The map might not work if you do not have a suitable GPU or Graphics Card.
In such case you can disable the "ZoneAgent" layer

* Speed up the simulation by disabling some of the complex layers of the visualization, including Landsat, Blue Marble, and others

* Errors are reported in the error log.  If any errors occur you can them by opening it.  
To do that click on the spinning blue icon on the bottom right.  Each error could be clicked.


General notes for developers
-----------------------------------------------
The entry point into the simulation is APKBuilder.java.
Repast is provided with "context.xml" and the parameter file name, and loads them.
In GUI mode, it only uses the context.xml file is used.
Events are controlled by the Repast Symphony scheduler.
The agents sit in the "context" as well as "geography" and "network" objects, which are self-explanatory.
The Statistics.java has a singleton class responsible for recording statistical data and simulation events in several different formats.

The code is divided into two packages: edu.uic.apkSynth and edu.uic.apk 
apkSynth contains only code used in loading and generating agents, while the later has the main simulation.
IDUBuilder1 class supports a variety of options for handling generation, including multiple sources and techniques some no longer used.
Usually, it calls generate() from the apkSynth, and then checks the output.
To insure correctness, there is intentional duplication of functionality between IDU.java and  DrugUser.java

DrugUser.java is designed to store profile data, as constructed from a database.  
IDU.java is initialized from DrugUser.java, and then proceeds with the dynamics.

IDU.java handles the activities of the Agent.  
Immunology.java is entirely responsible for health, including new infections.  One Immunology is assigned per agnet.

ZoneAgent is a mostly static class that represents a ZIP code.  Each IDU is assigned a single ZoneAgent.

The class LinkAgent.java that represents network connections is only used in GUI mode, to work around Repast.  
Otherwise, the network is only stored in the singleton "network" class.  

Activity_profile.java implements a feature that might be introduced into the future: the agents going to prison.
Generally, we know that empirically there are almost no HCV infections occurring in prison. 
Although imprisonment rates are quite high among IDUs, we do not have good data.
For now, all the agents stay within the community for the entire simulation.

HepCEP (APK) has a number of dependencies:
* Repast Simphony 2.9
* Java SE 11 or higher
* JODA for calendar functionality
* WEKA for machine learning / imputation of the synthetic population


