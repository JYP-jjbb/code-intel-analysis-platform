param(
    [string]$Server = "localhost",
    [int]$Port = 1433,
    [string]$AdminUser = $env:SQLSERVER_ADMIN_USER,
    [string]$AdminPassword = $env:SQLSERVER_ADMIN_PASSWORD
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AdminUser)) {
    throw "Missing admin username. Pass -AdminUser or set SQLSERVER_ADMIN_USER."
}
if ([string]::IsNullOrWhiteSpace($AdminPassword)) {
    throw "Missing admin password. Pass -AdminPassword or set SQLSERVER_ADMIN_PASSWORD."
}

$sqlcmd = Get-Command sqlcmd -ErrorAction SilentlyContinue
if (-not $sqlcmd) {
    throw "sqlcmd is not installed or not in PATH. Please install SQL Server command-line tools."
}

$scriptPath = Join-Path $PSScriptRoot "init-code-intel-analysis.sql"
if (-not (Test-Path $scriptPath)) {
    throw "Initialization SQL file not found: $scriptPath"
}

$target = "$Server,$Port"
Write-Host "Initializing SQL Server at $target ..."
Write-Host "Admin user: $AdminUser"

& sqlcmd -S $target -U $AdminUser -P $AdminPassword -i $scriptPath -b -C

Write-Host "Initialization completed."
