# Launch packaged TMP.exe with required package-profile environment.
# Usage (from repo root):
#   powershell -ExecutionPolicy Bypass -File .\scripts\run-tmp-package.ps1

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$exe = Join-Path $root "dist\jpackage\TMP\TMP.exe"
$workdir = Join-Path $root "dist\jpackage\TMP"

if (-not (Test-Path $exe)) {
    throw "TMP.exe not found at $exe. Build with: mvn pre-integration-test -Ppackage -DskipTests"
}

$dbUrl = if ($env:TMP_DB_URL) { $env:TMP_DB_URL } else { "jdbc:postgresql://localhost:55432/tmp_gui_stage5" }
$dbUser = if ($env:TMP_DB_USERNAME) { $env:TMP_DB_USERNAME } else { "tmp" }
$dbPassword = if ($env:TMP_DB_PASSWORD) { $env:TMP_DB_PASSWORD } else { "TestDb123!" }
$adminLogin = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN } else { "admin" }
$adminDisplayName = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME } else { "Administrator" }
$adminPassword = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD } else { "admin" }
# Production warehouse scope is required when Production is on the classpath (no magic defaults).
$mainWh = if ($env:TMP_PRODUCTION_WAREHOUSE_MAIN_WAREHOUSE_ID) { $env:TMP_PRODUCTION_WAREHOUSE_MAIN_WAREHOUSE_ID } else { "11111111-1111-4111-8111-111111111111" }
$prodWh = if ($env:TMP_PRODUCTION_WAREHOUSE_PRODUCTION_WAREHOUSE_ID) { $env:TMP_PRODUCTION_WAREHOUSE_PRODUCTION_WAREHOUSE_ID } else { "22222222-2222-4222-8222-222222222222" }

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
$startInfo.EnvironmentVariables["TMP_PRODUCTION_WAREHOUSE_MAIN_WAREHOUSE_ID"] = $mainWh
$startInfo.EnvironmentVariables["TMP_PRODUCTION_WAREHOUSE_PRODUCTION_WAREHOUSE_ID"] = $prodWh

[System.Diagnostics.Process]::Start($startInfo) | Out-Null
