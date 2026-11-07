@echo off
REM =========================================================================
REM Universal Mailbox Integration Service (UMIS) - Service Launcher Wrapper
REM Executes start-services.ps1 bypassing PowerShell execution policies.
REM =========================================================================

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-services.ps1" %*

REM Keep console window open if an error occurs during execution
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Launcher execution halted with exit code %ERRORLEVEL%.
    pause
)
