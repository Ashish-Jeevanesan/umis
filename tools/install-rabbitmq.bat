@echo off
setlocal
cd /d "%~dp0"

echo ========================================================
echo   RabbitMQ Native Windows Service Installer & Setup
echo ========================================================
echo.

:: Check for Administrator privileges
net session >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] This installer requires Administrator privileges.
    echo Please right-click "install-rabbitmq.bat" and select "Run as administrator".
    echo.
    pause
    exit /b 1
)

echo [1/5] Setting ERLANG_HOME and updating system PATH...
setx ERLANG_HOME "C:\Program Files\Erlang OTP" /M >nul 2>&1
powershell -NoProfile -Command "$p = [Environment]::GetEnvironmentVariable('Path', 'Machine'); if ($p -notlike '*C:\Program Files\Erlang OTP\bin*') { [Environment]::SetEnvironmentVariable('Path', $p.TrimEnd(';') + ';C:\Program Files\Erlang OTP\bin', 'Machine') }"

set "ERLANG_HOME=C:\Program Files\Erlang OTP"
set "PATH=C:\Program Files\Erlang OTP\bin;%PATH%"

echo [2/5] Running RabbitMQ 4.4.0 official installer...
if not exist "C:\Users\ajeevanesan\.gemini\tmp\umis\rabbitmq-server-4.4.0.exe" (
    echo Downloading RabbitMQ installer...
    curl.exe -L -o "C:\Users\ajeevanesan\.gemini\tmp\umis\rabbitmq-server-4.4.0.exe" "https://github.com/rabbitmq/rabbitmq-server/releases/download/v4.4.0/rabbitmq-server-4.4.0.exe"
)

start /wait "" "C:\Users\ajeevanesan\.gemini\tmp\umis\rabbitmq-server-4.4.0.exe" /S

timeout /t 5 /nobreak >nul

echo [3/5] Locating installed RabbitMQ sbin...
set "RABBITMQ_SBIN="
for /d %%D in ("C:\Program Files\RabbitMQ Server\rabbitmq_server-*") do (
    if exist "%%D\sbin\rabbitmq-service.bat" set "RABBITMQ_SBIN=%%D\sbin"
)

if "%RABBITMQ_SBIN%"=="" (
    echo [ERROR] Could not find RabbitMQ installation in C:\Program Files\RabbitMQ Server.
    pause
    exit /b 1
)

echo Found RabbitMQ at: %RABBITMQ_SBIN%
set "PATH=%RABBITMQ_SBIN%;%PATH%"
powershell -NoProfile -Command "$p = [Environment]::GetEnvironmentVariable('Path', 'Machine'); if ($p -notlike '*%RABBITMQ_SBIN%*') { [Environment]::SetEnvironmentVariable('Path', $p.TrimEnd(';') + ';%RABBITMQ_SBIN%', 'Machine') }"

echo [4/5] Enabling RabbitMQ Management Plugin...
call "%RABBITMQ_SBIN%\rabbitmq-plugins.bat" enable rabbitmq_management

echo [5/5] Ensuring RabbitMQ service is started...
call "%RABBITMQ_SBIN%\rabbitmq-service.bat" install
call "%RABBITMQ_SBIN%\rabbitmq-service.bat" start

echo.
echo [INFO] Waiting for RabbitMQ node to become ready...
timeout /t 5 /nobreak >nul

echo [INFO] Configuring dedicated 'umis' application user...
call "%RABBITMQ_SBIN%\rabbitmqctl.bat" add_user umis 9c6a7e0d8b22b87a5b401b6049b291a8
call "%RABBITMQ_SBIN%\rabbitmqctl.bat" set_user_tags umis administrator
call "%RABBITMQ_SBIN%\rabbitmqctl.bat" set_permissions -p / umis ".*" ".*" ".*"

echo.
echo ========================================================
echo   RabbitMQ Setup Complete!
echo   Management UI: http://localhost:15672
echo   AMQP Port:     5672
echo   User:          umis
echo ========================================================
echo.
pause
