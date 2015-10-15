@echo on
if "%JAVA_HOME%"=="" call:FIND_JAVA_HOME
echo "Java home: %JAVA_HOME%"
rem Start your java application or perform other actions here
"%JAVA_HOME%"\java -version
goto:END

REM :FIND_JAVA_HOME
FOR /F "skip=2 tokens=2*" %%A IN ('REG QUERY "HKLM\Software\JavaSoft\Java Runtime Environment" /v CurrentVersion') DO set CurVer=%%B

FOR /F "skip=2 tokens=2*" %%A IN ('REG QUERY "HKLM\Software\JavaSoft\Java Runtime Environment\%CurVer%" /v JavaHome') DO set JAVA_HOME=%%B;
REM set PATH=%PATH%;%%B
goto:EOF

:END