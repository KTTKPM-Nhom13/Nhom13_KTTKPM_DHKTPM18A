@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup batch script, version 3.3.4
@REM
@REM Optional ENV vars
@REM -----------------
@REM   JAVA_HOME - location of a JDK home dir, required when download maven via java source
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for downloading maven
@REM   MVNW_VERBOSE - true: enable verbose log; debug: trace the mvnw script; others: silence the output
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case MAVEN_BATCH_ECHO is 'on'
@echo off
@REM set title of command window
title %0
@REM enable echoing by setting MAVEN_BATCH_ECHO to 'on'
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_BATCH_ECHO%

@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set "HOME=%HOMEDRIVE%%HOMEPATH%")

@REM Execute a user defined script before this script
if exist "%USERPROFILE%\mavenrc_pre.bat" call "%USERPROFILE%\mavenrc_pre.bat" %*
if exist "%USERPROFILE%\mavenrc_pre.cmd" call "%USERPROFILE%\mavenrc_pre.cmd" %*

set ERROR_CODE=0

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal enableDelayedExpansion

set DIRNAME=%~dp0
if "%DIRNAME%" == "" (set "DIRNAME=." @REM Read forerunning backslash issue)
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@REM Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@REM Add default JVM options here. You can also use JAVA_OPTS and MAVEN_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@REM Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:execute
@REM Setup the command line

set MAVEN_OPTS=%MAVEN_OPTS% %JPDA_OPTS%

@REM Execute MAVEN
set PWD=%CD%
set MAVEN_JAVA_EXE="%JAVA_EXE%"
set CLASSPATH=%APP_HOME%\maven-wrapper\maven-wrapper.jar

set WRAPPER_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar
set WRAPPER_JAR=%APP_HOME%\maven-wrapper\maven-wrapper.jar
set WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain

if exist "%WRAPPER_JAR%" (
    goto runMaven
) else (
    echo.
    echo Downloading Maven Wrapper JAR...
    powershell -Command "& {" ^
        "$webclient = new-object System.Net.WebClient; " ^
        "$webclient.DownloadFile('%WRAPPER_URL%', '%WRAPPER_JAR%')" ^
    "}"
    if exist "%WRAPPER_JAR%" goto runMaven
    echo.
    echo ERROR: Failed to download maven-wrapper.jar
    goto fail
)

:runMaven
cd "%APP_HOME%"

"%MAVEN_JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %MAVEN_OPTS% "-Dmaven.multiModuleProjectDirectory=%APP_HOME%" "-Dclassworlds.conf=%APP_HOME%\maven-wrapper\maven-wrapper.properties" "-Dmaven.home=%APP_HOME%\maven-wrapper\maven-wrapper-3.9.12" "-Dwrapper.jar=%WRAPPER_JAR%" "-Dmaven.wrapper.downloadUrl=%WRAPPER_URL%" %*

if ERRORLEVEL 1 goto fail
goto end

:fail
set ERROR_CODE=1

:end
@endlocal & set ERROR_CODE=%ERROR_CODE%

if not "%MAVEN_SKIP_RC%"=="" goto end
:end
cmd /C exit /B %ERROR_CODE%
