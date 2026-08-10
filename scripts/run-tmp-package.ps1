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

# Fail fast with a clear message if DB env is missing when double-clicking TMP.exe.
$env:TMP_DB_URL = if ($env:TMP_DB_URL) { $env:TMP_DB_URL } else { "jdbc:postgresql://localhost:55432/tmp_gui_stage5" }
$env:TMP_DB_USERNAME = if ($env:TMP_DB_USERNAME) { $env:TMP_DB_USERNAME } else { "tmp" }
$env:TMP_DB_PASSWORD = if ($env:TMP_DB_PASSWORD) { $env:TMP_DB_PASSWORD } else { "TestDb123!" }
$env:TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN } else { "admin" }
$env:TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME } else { "Administrator" }
$env:TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD = if ($env:TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD) { $env:TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD } else { "admin" }

Write-Host "Starting TMP with package profile against $($env:TMP_DB_URL)"
Start-Process -FilePath $exe -WorkingDirectory $workdir
