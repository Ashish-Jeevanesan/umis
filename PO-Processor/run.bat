@echo off
setlocal
cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1" %*

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] PO Processor execution halted with exit code %ERRORLEVEL%.
    pause
)
