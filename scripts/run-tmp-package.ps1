# Launch packaged TMP.exe with required package-profile environment.
# Usage (from repo root):
#   $env:TMP_DB_URL = "jdbc:postgresql://localhost:55432/<explicit-db>"
#   powershell -ExecutionPolicy Bypass -File .\scripts\run-tmp-package.ps1
#
# TMP_DB_URL is required. There is no implicit default database
# (neither tmp_gui_stage5 nor tmp_pilot).

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root "dist\jpackage\TMP\TMP.exe"
$workdir = Join-Path $root "dist\jpackage\TMP"

if (-not (Test-Path $exe)) {
    throw "TMP.exe not found at $exe. Build with: mvn pre-integration-test -Ppackage -DskipTests"
}

if ([string]::IsNullOrWhiteSpace($env:TMP_DB_URL)) {
    throw @"
TMP_DB_URL is required. Set an explicit JDBC URL before launching the package.

Example:
  `$env:TMP_DB_URL = 'jdbc:postgresql://localhost:55432/<database>'
  powershell -ExecutionPolicy Bypass -File .\scripts\run-tmp-package.ps1

This script does not default to tmp_gui_stage5 or tmp_pilot.
For the permanent pilot environment use: .\scripts\pilot\run-tmp-pilot.ps1
"@
}

$dbUrl = $env:TMP_DB_URL.Trim()
if ($dbUrl -notmatch '(?i)^jdbc:postgresql://') {
    throw "TMP_DB_URL must be a PostgreSQL JDBC URL. Got: $dbUrl"
}

$dbUser = if ($env:TMP_DB_USERNAME) { $env:TMP_DB_USERNAME } else { "tmp" }
$dbPassword = if ($env:TMP_DB_PASSWORD) { $env:TMP_DB_PASSWORD } else { "TestDb123!" }
$adminLogin = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN } else { "admin" }
$adminDisplayName = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME } else { "Administrator" }
$adminPassword = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD } else { "admin" }
# Production destination warehouse is Warehouse-managed (Settings → Склады → «Склад производства»).
# No TMP_PRODUCTION_WAREHOUSE_* environment UUID is used as source of truth.

Write-Host "Starting TMP with package profile against $dbUrl"

$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = $exe
$startInfo.WorkingDirectory = $workdir
$startInfo.UseShellExecute = $false
$startInfo.EnvironmentVariables["TMP_DB_URL"] = $dbUrl
$startInfo.EnvironmentVariables["TMP_DB_USERNAME"] = $dbUser
$startInfo.EnvironmentVariables["TMP_DB_PASSWORD"] = $dbPassword
$startInfo.EnvironmentVariables["TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN"] = $adminLogin
$startInfo.EnvironmentVariables["TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME"] = $adminDisplayName
$startInfo.EnvironmentVariables["TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD"] = $adminPassword

[System.Diagnostics.Process]::Start($startInfo) | Out-Null
