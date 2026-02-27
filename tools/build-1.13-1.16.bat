@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build-1.13-1.16.ps1" %*
exit /b %ERRORLEVEL%

