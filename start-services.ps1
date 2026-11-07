<#
.SYNOPSIS
    Starts the UMIS Backend and Frontend services with automated port discovery.

.DESCRIPTION
    1. Scans for available network ports for backend and frontend starting from default ports.
    2. Dynamically updates frontend/vite.config.ts with the open frontend port and backend proxy target.
    3. Launches the Spring Boot backend in a dedicated visible PowerShell console.
    4. Launches the React Vite frontend in a dedicated visible PowerShell console.
    5. Displays operational access URLs.

.PARAMETER BackendPreferredPort
    Starting port for backend port discovery (default: 8080).

.PARAMETER FrontendPreferredPort
    Starting port for frontend port discovery (default: 3000).
#>

[CmdletBinding()]
param (
    [int]$BackendPreferredPort = 8080,
    [int]$FrontendPreferredPort = 3000
)

# Set strict execution safety
$ErrorActionPreference = "Stop"

# Resolve absolute project directories
$rootDirectory = $PSScriptRoot
if (-not $rootDirectory) {
    $rootDirectory = (Get-Location).Path
}

$backendDirectory = Join-Path $rootDirectory "backend"
$frontendDirectory = Join-Path $rootDirectory "frontend"

Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host "       Universal Mailbox Integration Service (UMIS)         " -ForegroundColor Cyan
Write-Host "          Port Discovery & Dual-Console Launcher             " -ForegroundColor Cyan
Write-Host "=============================================================" -ForegroundColor Cyan
Write-Host ""

# 0. Load ONLY the OAuth provider credential keys from the root .env file, if present.
#    mvn spring-boot:run does NOT read .env files on its own - Spring Boot's
#    @Value("${GOOGLE_CLIENT_ID:...}") style bindings only see real OS environment
#    variables. Without this step, GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET / AZURE_*
#    in .env are silently ignored and every OAuth provider falls back to its
#    built-in mock-*-client-id default, which Google/Microsoft reject as invalid_client.
#
#    IMPORTANT: this is a narrow allowlist, not a full .env import. Keys like
#    POSTGRES_* / SPRING_RABBITMQ_HOST / UMIS_STORAGE_ENDPOINT in .env are wired for the
#    Docker Compose stack (e.g. host "postgres"/"rabbitmq"/"minio", which don't resolve
#    locally). Spring's relaxed env-var binding gives OS environment variables higher
#    precedence than application-local.yml/application-dev.yml, so importing those wholesale
#    would silently override and break the local H2/standalone profile's broker/storage config.
#
#    SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD ARE forwarded, deliberately: these are how you
#    point the 'dev' profile at an external Postgres (e.g. Supabase) instead of Docker's own
#    postgres container or the local H2 file. See the profile-selection step below, which
#    switches to 'dev' automatically once SPRING_DATASOURCE_URL is set this way.
#
#    UMIS_STORAGE_TYPE / UMIS_STORAGE_LOCAL_BASE_PATH are also forwarded: no application*.yml
#    profile sets these (type defaults to LOCAL in application.yml's own placeholder), so
#    there's nothing for an OS env var to collide with - same reasoning as the OAuth keys.
$oauthEnvKeys = @(
    "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET", "GOOGLE_REDIRECT_URI",
    "AZURE_CLIENT_ID", "AZURE_CLIENT_SECRET", "AZURE_TENANT_ID", "AZURE_REDIRECT_URI"
)
$datasourceEnvKeys = @(
    "SPRING_DATASOURCE_URL", "SPRING_DATASOURCE_USERNAME", "SPRING_DATASOURCE_PASSWORD"
)
$storageEnvKeys = @(
    "UMIS_STORAGE_TYPE", "UMIS_STORAGE_LOCAL_BASE_PATH"
)
$forwardedEnvKeys = $oauthEnvKeys + $datasourceEnvKeys + $storageEnvKeys
$envFile = Join-Path $rootDirectory ".env"
if (Test-Path $envFile) {
    Write-Host "Step 0: Loading OAuth credentials and datasource settings from .env..." -ForegroundColor Cyan
    $loadedKeys = @()
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            $value = $parts[1].Trim().Trim('"')
            if ($key -and ($forwardedEnvKeys -contains $key) -and $value) {
                Set-Item -Path "Env:$key" -Value $value
                $loadedKeys += $key
            }
        }
    }
    if ($loadedKeys.Count -gt 0) {
        # Key names only - never print values, since these may be secrets/passwords.
        Write-Host "[OK] Loaded from .env: $($loadedKeys -join ', ')" -ForegroundColor Green
    } else {
        Write-Host "[-] .env exists but none of the recognized keys were set in it." -ForegroundColor Yellow
    }
    Write-Host ""
} else {
    Write-Host "[-] No root .env file found. OAuth providers will use built-in mock credentials," -ForegroundColor Yellow
    Write-Host "    and the backend will fall back to its local embedded H2 database." -ForegroundColor Yellow
    Write-Host ""
}

# Function to test if a TCP port is open and available
function Test-PortAvailable {
    param (
        [int]$PortNumber
    )
    # Check active system TCP listeners (covers 127.0.0.1, ::1, and 0.0.0.0)
    $activeListeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    $isListening = ($activeListeners | Where-Object { $_.Port -eq $PortNumber })
    if ($isListening) {
        return $false
    }

    # Verify socket binding capability on loopback
    $tcpListener = $null
    try {
        $tcpListener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $PortNumber)
        $tcpListener.Start()
        return $true
    } catch {
        return $false
    } finally {
        if ($tcpListener -ne $null) {
            try {
                $tcpListener.Stop()
            } catch {
                # Ignore errors during stop/cleanup
            }
        }
    }
}

# Function to find the next available port starting from a given number
function Get-NextAvailablePort {
    param (
        [int]$InitialPort,
        [string]$ServiceName
    )
    $candidatePort = $InitialPort
    $maxAttempts = 50
    $attemptCounter = 0

    while ($attemptCounter -lt $maxAttempts) {
        if (Test-PortAvailable -PortNumber $candidatePort) {
            Write-Host "[+] Found free port for $ServiceName : $candidatePort" -ForegroundColor Green
            return $candidatePort
        }
        Write-Host "[-] Port $candidatePort is currently in use, trying next..." -ForegroundColor Yellow
        $candidatePort++
        $attemptCounter++
    }
    throw "Unable to find an open port for $ServiceName after $maxAttempts attempts."
}

# Function to discover active JDK 21 installation
function Get-JdkPath {
    if (Test-Path "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8") {
        return "C:\Program Files (x86)\Android\openjdk\jdk-21.0.8"
    }
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        return $env:JAVA_HOME
    }
    if (Test-Path "C:\Program Files\Java\jdk-17") {
        return "C:\Program Files\Java\jdk-17"
    }
    return $null
}

# Function to update frontend/vite.config.ts with open ports
function Update-FrontendConfiguration {
    param (
        [string]$TargetFrontendDir,
        [int]$TargetFrontendPort,
        [int]$TargetBackendPort
    )
    $viteConfigFile = Join-Path $TargetFrontendDir "vite.config.ts"
    $viteConfigContent = @"
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: $TargetFrontendPort,
    proxy: {
      '/api': {
        target: 'http://localhost:$TargetBackendPort',
        changeOrigin: true,
      },
    },
  },
});
"@
    Set-Content -Path $viteConfigFile -Value $viteConfigContent -Encoding UTF8
    Write-Host "[OK] Updated configuration: Port $TargetFrontendPort, Proxy $TargetBackendPort" `
        -ForegroundColor Green

    # Also update frontend .env file for full environment consistency
    $envConfigFile = Join-Path $TargetFrontendDir ".env"
    Set-Content -Path $envConfigFile -Value "VITE_API_BASE_URL=http://localhost:$TargetBackendPort" -Encoding UTF8
}

# 1. Discover Free Ports
Write-Host "Step 1: Checking for open ports..." -ForegroundColor Cyan
$backendFreePort = Get-NextAvailablePort -InitialPort $BackendPreferredPort -ServiceName "Backend Service"
$frontendFreePort = Get-NextAvailablePort -InitialPort $FrontendPreferredPort -ServiceName "Frontend Dashboard"
Write-Host ""

# 2. Update Frontend Configuration
Write-Host "Step 2: Updating frontend configuration..." -ForegroundColor Cyan
Update-FrontendConfiguration `
    -TargetFrontendDir $frontendDirectory `
    -TargetFrontendPort $frontendFreePort `
    -TargetBackendPort $backendFreePort
Write-Host ""

# 3. Locate Java 21 Home
$jdkDirectory = Get-JdkPath
if (-not $jdkDirectory) {
    throw "Java 21 / 17 JDK was not found. Please set JAVA_HOME."
}
Write-Host "[OK] Using JDK Path: $jdkDirectory" -ForegroundColor Green
Write-Host ""

# 4. Select the Spring profile: an external Postgres configured via .env (e.g. Supabase)
#    takes priority, since that's an explicit choice; otherwise fall back to detecting a
#    local Postgres service, and finally to the embedded H2 standalone database.
$springProfile = "local"
if ($env:SPRING_DATASOURCE_URL -and $env:SPRING_DATASOURCE_URL.Trim()) {
    Write-Host "[+] SPRING_DATASOURCE_URL configured via .env (e.g. Supabase). Launching with 'dev' profile." -ForegroundColor Green
    $springProfile = "dev"
} else {
    $isPostgreRunning = -not (Test-PortAvailable -PortNumber 5432)
    if ($isPostgreRunning) {
        Write-Host "[+] Active PostgreSQL detected on port 5432. Launching with 'dev' profile." -ForegroundColor Green
        $springProfile = "dev"
    } else {
        Write-Host "[-] PostgreSQL not detected on port 5432 and no SPRING_DATASOURCE_URL in .env." -ForegroundColor Yellow
        Write-Host "    -> Launching in standalone 'local' mode (embedded H2 file db in backend/data/)." -ForegroundColor Yellow
        $springProfile = "local"
    }
}
Write-Host ""

# 5. Launch Backend in dedicated console window
Write-Host "Step 3: Launching Backend console window..." -ForegroundColor Cyan
$backendLaunchScript = @"
[Console]::Title = '[UMIS] Backend Service (Port $backendFreePort)'
Clear-Host
Write-Host '=============================================================' -ForegroundColor Cyan
Write-Host '      UMIS Backend Service (Spring Boot 3 + Java 21)         ' -ForegroundColor Cyan
Write-Host '      Port: $backendFreePort | Profile: $springProfile        ' -ForegroundColor Green
Write-Host '=============================================================' -ForegroundColor Cyan
Write-Host ''
Set-Location -Path '$backendDirectory'
`$env:JAVA_HOME = '$jdkDirectory'
`$env:PATH = '$jdkDirectory\bin;' + `$env:PATH
`$env:SERVER_PORT = '$backendFreePort'
Write-Host 'Starting Maven Spring Boot execution...' -ForegroundColor Yellow
mvn spring-boot:run '-Dspring-boot.run.arguments=--server.port=$backendFreePort --spring.profiles.active=$springProfile'
"@

Start-Process powershell -ArgumentList "-NoExit", "-Command", $backendLaunchScript
Write-Host "[OK] Backend console opened." -ForegroundColor Green
Write-Host ""

# 5. Launch Frontend in dedicated console window
Write-Host "Step 4: Launching Frontend console window..." -ForegroundColor Cyan
$frontendLaunchScript = @"
[Console]::Title = '[UMIS] Frontend Dashboard (Port $frontendFreePort)'
Clear-Host
Write-Host '=============================================================' -ForegroundColor Magenta
Write-Host '      UMIS Frontend Dashboard (React + Vite + Tailwind)      ' -ForegroundColor Magenta
Write-Host '      Port: $frontendFreePort | Proxy -> http://localhost:$backendFreePort' -ForegroundColor Green
Write-Host '=============================================================' -ForegroundColor Magenta
Write-Host ''
Set-Location -Path '$frontendDirectory'
Write-Host 'Starting Vite development server...' -ForegroundColor Yellow
npm run dev -- --host --port $frontendFreePort
"@

Start-Process powershell -ArgumentList "-NoExit", "-Command", $frontendLaunchScript
Write-Host "[OK] Frontend console opened." -ForegroundColor Green
Write-Host ""

# 6. Display Summary to user & Launch Browser
Write-Host "=============================================================" -ForegroundColor Green
Write-Host "                  Services Launched Successfully!            " -ForegroundColor Green
Write-Host "=============================================================" -ForegroundColor Green
Write-Host "  * Frontend Dashboard : http://localhost:$frontendFreePort" -ForegroundColor White
Write-Host "  * Backend Swagger UI : http://localhost:$backendFreePort/swagger-ui.html" -ForegroundColor White
Write-Host "  * Actuator Health    : http://localhost:$backendFreePort/actuator/health" -ForegroundColor White
Write-Host "=============================================================" -ForegroundColor Green
Write-Host "Two separate console windows have been opened for live logs." -ForegroundColor Cyan

# Automatically launch browser to frontend dashboard
Start-Process "http://localhost:$frontendFreePort"
