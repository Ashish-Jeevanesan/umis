# PowerShell Launcher for PO Email & PDF Generator

Set-Location -Path $PSScriptRoot

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "  UMIS - PO Email and PDF Generator Utility" -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path "node_modules")) {
    Write-Host "[INFO] Installing required dependencies..." -ForegroundColor Yellow
    npm install
}

if (-not (Test-Path ".env")) {
    Write-Host "[WARN] .env file not found. Creating from .env.example..." -ForegroundColor Yellow
    Copy-Item ".env.example" ".env"
    Write-Host "[WARN] Please configure SMTP credentials in tools/po-email-generator/.env" -ForegroundColor Yellow
}

Write-Host "[INFO] Launching server on http://localhost:3050..." -ForegroundColor Green
Start-Process "http://localhost:3050"
node server.js
