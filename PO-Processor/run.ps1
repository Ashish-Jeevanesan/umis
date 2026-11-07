# PowerShell Launcher for PO Processor Microservice

Set-Location -Path $PSScriptRoot

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  PO Processor - Downstream Microservice Launcher       " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

if (Test-Path "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8") {
    $env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"
    $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
}

if (-not (Test-Path ".env")) {
    if (Test-Path ".env.example") {
        Write-Host "[WARN] .env not found. Creating from .env.example..." -ForegroundColor Yellow
        Copy-Item ".env.example" ".env"
        Write-Host "[WARN] Please update .env with your PostgreSQL and RabbitMQ credentials." -ForegroundColor Yellow
    }
} else {
    Write-Host "[INFO] Loading configuration from .env..." -ForegroundColor Green
    Get-Content ".env" | ForEach-Object {
        $trimmedLine = $_.Trim()
        if ($trimmedLine -and -not $trimmedLine.StartsWith("#") -and $trimmedLine.Contains("=")) {
            $splitParts = $trimmedLine.Split("=", 2)
            $envKey = $splitParts[0].Trim()
            $envVal = $splitParts[1].Trim()
            if (($envVal.StartsWith('"') -and $envVal.EndsWith('"')) -or ($envVal.StartsWith("'") -and $envVal.EndsWith("'"))) {
                $envVal = $envVal.Substring(1, $envVal.Length - 2)
            }
            [System.Environment]::SetEnvironmentVariable($envKey, $envVal, "Process")
        }
    }
}

Write-Host "[INFO] Launching PO Processor on port 8085..." -ForegroundColor Green
mvn spring-boot:run
