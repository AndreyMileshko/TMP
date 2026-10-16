# Launch packaged TMP against the isolated pilot database (tmp_pilot).
# Usage (from repo root):
#   powershell -ExecutionPolicy Bypass -File .\scripts\pilot\run-tmp-pilot.ps1
# Controlled schema migrate (explicit only):
#   powershell -ExecutionPolicy Bypass -File .\scripts\pilot\run-tmp-pilot.ps1 -ApplyMigrations
#
# Credentials: %LOCALAPPDATA%\TMP-Pilot\credentials.env (outside Git).
# Does not modify scripts\run-tmp-package.ps1 and does not stop existing TMP processes.

[CmdletBinding()]
param(
    [switch] $ApplyMigrations
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_PilotCommon.ps1")

Write-TmpPilotBanner -Action "LAUNCH"

$paths = Get-TmpPilotPaths
$creds = Import-TmpPilotCredentials -CredFile $paths.CredFile
Assert-TmpPilotDatabaseTarget -Credentials $creds -ExpectedDb $paths.ExpectedDb -ExpectedUser $paths.ExpectedUser -StageDb $paths.StageDb

$root = Get-RepoRootFromPilotScript -PilotScriptRoot $PSScriptRoot
$exe = Join-Path $root "dist\jpackage\TMP\TMP.exe"
$workdir = Join-Path $root "dist\jpackage\TMP"

if (-not (Test-Path -LiteralPath $exe)) {
    throw "TMP.exe not found: $exe. Build with: mvn -pl :tmp-bootstrap-app -am pre-integration-test -Ppackage -DskipTests"
}

# Default: do not silently apply new Flyway migrations on ordinary pilot launches.
# Initial / controlled migration requires -ApplyMigrations.
$flywayEnabled = if ($ApplyMigrations) { "true" } else { "false" }

if ($ApplyMigrations) {
    Write-Host "Flyway: EXPLICIT migrate ENABLED (-ApplyMigrations)." -ForegroundColor Yellow
    Write-Host "After successful init, launch without -ApplyMigrations." -ForegroundColor Yellow
} else {
    Write-Host "Flyway: migrate DISABLED (normal pilot launch)." -ForegroundColor Green
    Write-Host "For controlled schema update use -ApplyMigrations." -ForegroundColor Green
}

Write-Host "DB target: $($paths.ExpectedDb) @ localhost:$($paths.HostPort)"
Write-Host "App role: $($paths.ExpectedUser) (non-superuser)"
Write-Host "Credentials file: $($paths.CredFile)"
Write-Host "Container: $($paths.Container)"
Write-Host ""

$startInfo = New-Object System.Diagnostics.ProcessStartInfo
$startInfo.FileName = $exe
$startInfo.WorkingDirectory = $workdir
$startInfo.UseShellExecute = $false
$startInfo.EnvironmentVariables["TMP_DB_URL"] = [string]$creds["TMP_DB_URL"]
$startInfo.EnvironmentVariables["TMP_DB_USERNAME"] = [string]$creds["TMP_DB_USERNAME"]
$startInfo.EnvironmentVariables["TMP_DB_PASSWORD"] = [string]$creds["TMP_DB_PASSWORD"]
$startInfo.EnvironmentVariables["TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN"] = [string]$creds["TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN"]
$startInfo.EnvironmentVariables["TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME"] = [string]$creds["TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME"]
$startInfo.EnvironmentVariables["TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD"] = [string]$creds["TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD"]
$startInfo.EnvironmentVariables["SPRING_FLYWAY_ENABLED"] = $flywayEnabled

$proc = [System.Diagnostics.Process]::Start($startInfo)
if ($null -eq $proc) {
    throw "Failed to start TMP.exe"
}

Write-Host "PILOT session started. PID=$($proc.Id)" -ForegroundColor Cyan
Write-Host "Verify JDBC URL points to tmp_pilot (not tmp_gui_stage5)."
Write-Host "App login: TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN from credentials.env"
Write-Host "Done."