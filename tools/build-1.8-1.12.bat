@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build-1.8-1.12.ps1" %*
exit /b %ERRORLEVEL%

