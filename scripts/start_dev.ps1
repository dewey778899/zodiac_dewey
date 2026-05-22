$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $repoRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Host ".env not found. Copy .env.example to .env first." -ForegroundColor Red
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#")) {
        return
    }

    $parts = $line -split "=", 2
    if ($parts.Count -eq 2) {
        [System.Environment]::SetEnvironmentVariable($parts[0], $parts[1])
    }
}

if (-not $env:AI_API_KEY) {
    Write-Host "AI_API_KEY is empty. The app will start, but report generation will fail until you fill it in." -ForegroundColor Yellow
}

$backendDir = Join-Path $repoRoot "backend"
Set-Location $backendDir

if (-not $env:SERVER_PORT) {
    $env:SERVER_PORT = "8080"
}

Write-Host "Starting Spring Boot backend on http://localhost:$env:SERVER_PORT (SQLite)" -ForegroundColor Green
mvn spring-boot:run
