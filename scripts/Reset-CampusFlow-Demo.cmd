@echo off
powershell.exe -NoLogo -NoProfile -File "%~dp0CampusFlow-Demo.ps1" -Action Reset
exit /b %errorlevel%
