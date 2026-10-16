# Shared helpers for TMP Pilot scripts. Dot-source only; do not execute directly.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-TmpPilotPaths {
    $credDir = Join-Path $env:LOCALAPPDATA "TMP-Pilot"
    [pscustomobject]@{
        CredDir      = $credDir
        CredFile     = Join-Path $credDir "credentials.env"
        BackupDir    = Join-Path $credDir "backups"
        Container    = "tmp-stage5-pg"
        ExpectedDb   = "tmp_pilot"
        ExpectedUser = "tmp_pilot_app"
        StageDb      = "tmp_gui_stage5"
        HostPort     = "55432"
    }
}

function Import-TmpPilotCredentials {
    param(
        [Parameter(Mandatory = $true)]
        [string] $CredFile
    )
    if (-not (Test-Path -LiteralPath $CredFile)) {
        throw "Pilot credentials file not found: $CredFile. Create it outside the repository (see docs/pilot/PILOT-ENVIRONMENT.md)."
    }
    $map = @{}
    $utf8Bom = [char]65279
    Get-Content -LiteralPath $CredFile -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim().TrimStart($utf8Bom)
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1)
        $map[$key] = $val
    }
    $required = @(
        "TMP_DB_URL",
        "TMP_DB_USERNAME",
        "TMP_DB_PASSWORD",
        "TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN",
        "TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME",
        "TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD"
    )
    foreach ($key in $required) {
        if (-not $map.ContainsKey($key) -or [string]::IsNullOrWhiteSpace([string]$map[$key])) {
            throw "Missing required credential key: $key"
        }
    }
    return $map
}

function Assert-TmpPilotDatabaseTarget {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable] $Credentials,
        [Parameter(Mandatory = $true)]
        [string] $ExpectedDb,
        [Parameter(Mandatory = $true)]
        [string] $ExpectedUser,
        [Parameter(Mandatory = $true)]
        [string] $StageDb
    )
    $url = [string]$Credentials["TMP_DB_URL"]
    $user = [string]$Credentials["TMP_DB_USERNAME"]
    $password = [string]$Credentials["TMP_DB_PASSWORD"]

    if ([string]::IsNullOrWhiteSpace($password)) {
        throw "Pilot DB password is missing (TMP_DB_PASSWORD)."
    }
    if ($user -ne $ExpectedUser) {
        throw "Rejected DB user '$user'. Expected only '$ExpectedUser' (not cluster admin)."
    }
    if ($user -eq "tmp" -or $user -eq "postgres") {
        throw "Pilot must not run as PostgreSQL cluster administrator."
    }
    if ($url -notmatch '(?i)jdbc:postgresql://') {
        throw "TMP_DB_URL must be a PostgreSQL JDBC URL."
    }
    if ($url -match [regex]::Escape($StageDb)) {
        throw "Rejected TMP_DB_URL: contains stage database $StageDb."
    }
    if ($url -notmatch '(?i)jdbc:postgresql://[^/]+/([^?;]+)') {
        throw "Could not parse database name from TMP_DB_URL."
    }
    $dbName = $Matches[1]
    if ($dbName -ne $ExpectedDb) {
        throw "Rejected database name '$dbName'. Only '$ExpectedDb' is allowed."
    }
}

function Get-RepoRootFromPilotScript {
    param([string] $PilotScriptRoot)
    return (Resolve-Path (Join-Path $PilotScriptRoot "..\..")).Path
}

function Write-TmpPilotBanner {
    param([string] $Action)
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host " TMP PILOT - $Action" -ForegroundColor Cyan
    Write-Host " Environment: PILOT (tmp_pilot)" -ForegroundColor Cyan
    Write-Host " NOT stage (tmp_gui_stage5)" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host ""
}