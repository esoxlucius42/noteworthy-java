@echo off
setlocal

set "APP_JAR=%~dp0target\noteworthy-1.0.0.jar"

where java >nul 2>nul
if errorlevel 1 (
    echo Java 21 or newer is required and must be available on PATH. 1>&2
    exit /b 1
)

if not exist "%APP_JAR%" (
    echo Application JAR not found: "%APP_JAR%" 1>&2
    echo Run "mvn package" from the project directory first. 1>&2
    exit /b 1
)

java -jar "%APP_JAR%" %*
exit /b %ERRORLEVEL%