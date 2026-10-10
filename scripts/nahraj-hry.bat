@echo off
rem Dvojklikom spustí nahrávanie hier na USB v RPi (vyberieš priečinok s hrami).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0nahraj-hry.ps1" %*
pause
