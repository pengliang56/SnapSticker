@echo off
echo -----------------------------------------------
echo Copyright (c) 2025 Luka. Licensed under MIT.
echo -----------------------------------------------
echo Running: mvn clean javafx:run

cd /d %~dp0..

mvn clean javafx:run

pause
