# Elevated RabbitMQ Server Installation & Setup Script

$logFile = "D:\Ashish_P\Project X\UMIS\tools\rabbitmq-install.log"
"Starting RabbitMQ installation script..." | Out-File $logFile

# 1. Update Machine ERLANG_HOME and Machine Path
[System.Environment]::SetEnvironmentVariable("ERLANG_HOME", "C:\Program Files\Erlang OTP", [System.EnvironmentVariableTarget]::Machine)
$machinePath = [System.Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::Machine)
if ($null -eq $machinePath) {
    $machinePath = ""
}
if (-not $machinePath.Contains("C:\Program Files\Erlang OTP\bin")) {
    $newMachinePath = $machinePath.TrimEnd(";") + ";C:\Program Files\Erlang OTP\bin"
    [System.Environment]::SetEnvironmentVariable("Path", $newMachinePath, [System.EnvironmentVariableTarget]::Machine)
}
"ERLANG_HOME and Machine Path updated." | Out-File $logFile -Append

# 2. Run installer silently
$installer = "C:\Users\ajeevanesan\.gemini\tmp\umis\rabbitmq-server-4.4.0.exe"
"Running installer $installer /S..." | Out-File $logFile -Append
$proc = Start-Process -FilePath $installer -ArgumentList "/S" -PassThru -Wait
"Installer finished with exit code $($proc.ExitCode)." | Out-File $logFile -Append

Start-Sleep -Seconds 8

# 3. Locate RabbitMQ installation
$rabbitDirs = Get-ChildItem "C:\Program Files\RabbitMQ Server" -Directory -Filter "rabbitmq_server-*" -ErrorAction SilentlyContinue
if ($null -eq $rabbitDirs -or $rabbitDirs.Count -eq 0) {
    "ERROR: RabbitMQ Server directory not found in C:\Program Files\RabbitMQ Server." | Out-File $logFile -Append
    exit 1
}
$rabbitDir = $rabbitDirs[0].FullName
$sbinDir = Join-Path $rabbitDir "sbin"
"Found RabbitMQ sbin at $sbinDir." | Out-File $logFile -Append

# Add sbin to Machine Path
$currentMachinePath = [System.Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::Machine)
if (-not $currentMachinePath.Contains($sbinDir)) {
    $updatedPath = $currentMachinePath.TrimEnd(";") + ";" + $sbinDir
    [System.Environment]::SetEnvironmentVariable("Path", $updatedPath, [System.EnvironmentVariableTarget]::Machine)
}

# 4. Set current process env
$env:ERLANG_HOME = "C:\Program Files\Erlang OTP"
$env:PATH = "C:\Program Files\Erlang OTP\bin;$sbinDir;" + $env:PATH

# 5. Enable management plugin
$pluginsBat = Join-Path $sbinDir "rabbitmq-plugins.bat"
"Enabling rabbitmq_management plugin..." | Out-File $logFile -Append
& $pluginsBat enable rabbitmq_management *>> $logFile

Start-Sleep -Seconds 5

# 6. Ensure service is installed and started
$serviceBat = Join-Path $sbinDir "rabbitmq-service.bat"
$svc = Get-Service RabbitMQ -ErrorAction SilentlyContinue
if ($null -eq $svc) {
    "Installing RabbitMQ service..." | Out-File $logFile -Append
    & $serviceBat install *>> $logFile
    Start-Sleep -Seconds 5
}

$svc = Get-Service RabbitMQ -ErrorAction SilentlyContinue
if ($null -ne $svc -and $svc.Status -ne "Running") {
    "Starting RabbitMQ service..." | Out-File $logFile -Append
    & $serviceBat start *>> $logFile
    Start-Sleep -Seconds 8
}

# 7. Configure user 'umis'
$ctlBat = Join-Path $sbinDir "rabbitmqctl.bat"
"Configuring user umis..." | Out-File $logFile -Append
& $ctlBat add_user umis 9c6a7e0d8b22b87a5b401b6049b291a8 *>> $logFile
& $ctlBat set_user_tags umis administrator *>> $logFile
& $ctlBat set_permissions -p / umis ".*" ".*" ".*" *>> $logFile
& $ctlBat list_users *>> $logFile
"RabbitMQ configuration script complete." | Out-File $logFile -Append
