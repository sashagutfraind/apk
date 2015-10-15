all: clean

compress-short: #only what we need for sensitivity analysis
	echo "compressing to: results-summaries.tgz"
	find APK/output/ | grep "populations.csv" > flist
	tar czf results.tgz lhs* jobList* -T flist
compress:
	echo "compressing to: results.tgz"
	tar czf results.tgz lhs* jobList* APK*txt out* APK/output*

summarize:
	echo "summarizing output"
	python sweep_driver.py -m a -d APK/output >& out.txt
	cp APK/output/dossier_summaries* ./

generate:
	python sweep_driver.py -m g -b lhs.ini -d . 

install:
	java -Xss10M -Xmx1000M -jar apt_setup.jar

run_all:
	python sweep_driver.py -m j -f ENTER_JOBSLIST -d . 

run_one:
	sbatch -J APK -n 1 -p normal -t 12:00:00 -A TG-IBM120015 pack.sh 
clean:
	rm -f APK*txt
	rm -f out*
	rm -f pack*
