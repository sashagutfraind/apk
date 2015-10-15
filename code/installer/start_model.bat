@ECHO OFF
TITLE APK

REM TESTCODE SET PATH=C:\Windows\system32;C:\Windows;C:\Windows\System32

SET PATH=%PATH%;"C:\Program files (x86)\Java\jre7\bin";"C:\Program files\Java\jre7\bin";
REM Repast Simphony Model Starter
REM By Michael J. North
REM 
REM Please note that the paths given below use
REM a unusual Linux-like notation. This is a
REM unfortunate requirement of the Java Plugin
REM framework application loader.

REM Note the Repast Simphony Directories.
set REPAST_SIMPHONY_ROOT=../repast.simphony/repast.simphony.runtime_$REPAST_VERSION/
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
SET CP=%CP%;%REPAST_SIMPHONY_LIB%xstream-1.4.4.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%xmlpull-1.1.3.1.jar
SET CP=%CP%;%REPAST_SIMPHONY_LIB%commons-cli-1.2.jar
SET CP=%CP%;../groovylib/$Groovy_All_Jar

REM Change to the Default Repast Simphony Directory
CD APK

if "%JAVA_HOME%"=="" call:FIND_JAVA_HOME

REM wishlist Is "javaw" better than "start javaw"?
START javaw -Xss10M -Xmx400M -cp %CP% repast.simphony.runtime.RepastMain ./APK.rs

CD ..

goto:END

:FIND_JAVA_HOME
FOR /F "skip=2 tokens=2*" %%A IN ('REG QUERY "HKLM\Software\JavaSoft\Java Runtime Environment" /v CurrentVersion') DO set CurVer=%%B
FOR /F "skip=2 tokens=2*" %%A IN ('REG QUERY "HKLM\Software\JavaSoft\Java Runtime Environment\%CurVer%" /v JavaHome') DO set PATH=%PATH%;"%%B\bin;"
goto:EOF

:END
