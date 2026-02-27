@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%build-1.17-1.20.ps1" %*
exit /b %ERRORLEVEL%

