@ECHO OFF
TITLE APK
REM # Repast Simphony Model Starter
REM # By Michael J. North and Jonathan Ozik
REM # 11/12/2007
REM # Note the Repast Simphony Directories.

REM "Starting APK ... "
REM ""

REM CWD=`pwd`
REM BATCHFILE=$1
REM if [ -z "$BATCHFILE" ]
REM then
REM    BATCHFILE=$CWD/APK/batch/batch_experiment.xml
REM fi


set BATCHFILE=batch/batch_params.xml
echo Running batch from: %BATCHFILE%

REM Change to the Default Repast Simphony Directory
cd APK

set REPAST_SIMPHONY_ROOT=../repast.simphony/repast.simphony.runtime_2.8.0/
set REPAST_SIMPHONY_LIB=%REPAST_SIMPHONY_ROOT%lib/

REM Define the Core Repast Simphony Directories and JARs
SET CP=%CP%;%REPAST_SIMPHONY_ROOT%bin
SET CP=%CP%;%REPAST_SIMPHONY_LIB%saf.core.runtime.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%commons-logging-1.1.2.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%javassist-3.17.1-GA.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%jpf.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%jpf-boot.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%log4j-1.2.16.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%xpp3_min-1.1.4c.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%xstream-1.4.7.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%xmlpull-1.1.3.1.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%commons-cli-1.3.1.jar
SET CP=%CP%;../groovylib/org.codehaus.groovy_2.4.20.v202009301404-e2006-RELEASE\lib\groovy-all-2.4.20.jar


echo Start the Model
REM echo "java -Xss10M -Xmx1000M -cp $CP repast.simphony.runtime.RepastBatchMain -params $BATCHFILE $CWD/APK/APK.rs"
REM java -Xss10M -Xmx1000M -cp $CP repast.simphony.runtime.RepastBatchMain -params $BATCHFILE $CWD/APK/APK.rs
REM cd ../

REM Change to the Default Repast Simphony Directory
REM CD APK

REM Start the Model
java -Xss10M -Xmx1000M -XX:+IgnoreUnrecognizedVMOptions --add-modules=ALL-SYSTEM --add-exports=java.base/jdk.internal.ref=ALL-UNNAMED -cp %CP% repast.simphony.runtime.RepastBatchMain -params %BATCHFILE% ./APK.rs

cd ../