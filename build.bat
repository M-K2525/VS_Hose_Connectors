@echo off
@echo Starting Gradle build...

set DIRNAME=%cd%
cd /d %DIRNAME%

rem Set environment variables
set DIRNAME=%cd%
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

set DEFAULT_JVM_OPTS="-Xmx4G" "-Xms1G"
set JAVA_HOME="C:\Program Files\Java\jdk-21.0.10"
set JAVA_EXE=%JAVA_HOME%\bin\java.exe

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain clean build -x test

echo Build complete!
