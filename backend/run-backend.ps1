# Dedicated UMIS backend launcher with full .env forwarding

Set-Location -Path $PSScriptRoot

$envFile = Join-Path (Split-Path $PSScriptRoot -Parent) ".env"
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"')
            if ($key -and $value) {
                Set-Item -Path "Env:$key" -Value $value
            }
        }
    }
}

if (Test-Path "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8") {
    $env:JAVA_HOME = "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"
    $env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
}

mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=8080 --spring.profiles.active=dev'
