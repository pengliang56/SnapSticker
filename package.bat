@echo off
setlocal enabledelayedexpansion

echo Building SnapSticker application...

REM Check Java version and set JAVA_HOME
echo Checking Java installation...
java -version > java_version.txt 2>&1
set JAVA_VERSION=
for /f "tokens=3" %%g in ('type java_version.txt ^| findstr /i "version"') do (
    set JAVA_VERSION=%%g
)
del java_version.txt

REM Remove quotes from version number
set JAVA_VERSION=%JAVA_VERSION:"=%

echo Found Java version: %JAVA_VERSION%

REM Check if Java version is 17 or higher
for /f "tokens=1 delims=." %%a in ("%JAVA_VERSION%") do (
    set MAJOR_VERSION=%%a
)

if %MAJOR_VERSION% LSS 17 (
    echo Error: Java 17 or higher is required. Found version %JAVA_VERSION%
    pause
    exit /b 1
)

echo Java version check passed.

REM Check if Maven is installed
echo Checking Maven installation...
where mvn >nul 2>&1
if errorlevel 1 (
    echo Error: Maven is not found in PATH
    echo Please ensure Maven is installed and added to PATH
    pause
    exit /b 1
)

echo Maven found in PATH

REM Clean and package the application
echo Running Maven package...
echo Current directory: %CD%
echo Running command: mvn clean package
call mvn clean package
if errorlevel 1 (
    echo Error: Maven build failed
    pause
    exit /b 1
)
echo Maven build completed.

echo Successfully built the application
echo You can now run the application using: mvn javafx:run
pause 