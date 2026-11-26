@echo off
if not exist "%~dp0..\.campusflow-demo\accounts.txt" (
    echo Start the CampusFlow demo to generate the local demo accounts.
    exit /b 1
)
start "" notepad.exe "%~dp0..\.campusflow-demo\accounts.txt"
