# Script to set ERLANG_HOME and update PATH in User and System environment

$erlangHome = "C:\Program Files\Erlang OTP"
$erlangBin = "C:\Program Files\Erlang OTP\bin"

# 1. Set in User Environment
[System.Environment]::SetEnvironmentVariable("ERLANG_HOME", $erlangHome, [System.EnvironmentVariableTarget]::User)
$userPath = [System.Environment]::GetEnvironmentVariable("Path", [System.EnvironmentVariableTarget]::User)
if ($null -eq $userPath) {
    $userPath = ""
}
if (-not $userPath.Contains($erlangBin)) {
    $newUserPath = $userPath.TrimEnd(";") + ";" + $erlangBin
    if ($userPath.Length -eq 0) {
        $newUserPath = $erlangBin
    }
    [System.Environment]::SetEnvironmentVariable("Path", $newUserPath, [System.EnvironmentVariableTarget]::User)
}

# 2. Set in Current Process
$env:ERLANG_HOME = $erlangHome
if (-not $env:PATH.Contains($erlangBin)) {
    $env:PATH = $erlangBin + ";" + $env:PATH
}

Write-Host "[OK] User environment and current process updated with ERLANG_HOME and PATH."
