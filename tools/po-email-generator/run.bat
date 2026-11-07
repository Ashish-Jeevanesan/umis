@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   UMIS - PO Email and PDF Generator Utility
echo ========================================================
echo.

if not exist "node_modules" (
    echo [INFO] Installing required dependencies...
    call npm install
    if errorlevel 1 (
        echo [ERROR] Failed to install dependencies.
        pause
        exit /b 1
    )
)

if not exist ".env" (
    echo [WARN] .env file not found. Creating from .env.example...
    copy .env.example .env
    echo [WARN] Please update .env with your SMTP credentials for email delivery.
    echo.
)

echo [INFO] Starting PO Email Generator server on port 3050...
echo [INFO] URL: http://localhost:3050
echo.

start http://localhost:3050
node server.js

pause
