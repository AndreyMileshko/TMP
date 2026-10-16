# Restore-check for a tmp_pilot backup into a temporary database only.
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\pilot\verify-tmp-pilot-backup.ps1
#   powershell -ExecutionPolicy Bypass -File .\scripts\pilot\verify-tmp-pilot-backup.ps1 -DumpFile <path>
#
# Restores ONLY into tmp_pilot_restore_check.
# NEVER restores over tmp_pilot or tmp_gui_stage5.
# If tmp_pilot_restore_check already exists - STOP.

[CmdletBinding()]
param(
    [string] $DumpFile
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_PilotCommon.ps1")

Write-TmpPilotBanner -Action "RESTORE VERIFY"

$paths = Get-TmpPilotPaths
$container = $paths.Container
$restoreDb = "tmp_pilot_restore_check"

$running = docker inspect -f "{{.State.Running}}" $container 2>$null
if ($running -ne "true") {
    throw "Контейнер PostgreSQL '$container' не запущен."
}

if ([string]::IsNullOrWhiteSpace($DumpFile)) {
    $latest = Get-ChildItem -LiteralPath $paths.BackupDir -Filter "tmp_pilot_*.dump" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $latest) {
        throw "Дамп не найден в $($paths.BackupDir). Сначала выполните backup-tmp-pilot.ps1."
    }
    $DumpFile = $latest.FullName
}

if (-not (Test-Path -LiteralPath $DumpFile)) {
    throw "Файл дампа не найден: $DumpFile"
}

Write-Host "Дамп: $DumpFile"
Write-Host "Цель проверки: $restoreDb (временная БД)"
Write-Host "Запрещено восстанавливать в: tmp_pilot, tmp_gui_stage5"
Write-Host ""

$exists = docker exec $container psql -U tmp -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname='$restoreDb';"
if ("$exists".Trim() -eq "1") {
    throw "База $restoreDb уже существует. Перезапись запрещена."
}

Write-Host "Создание пустой $restoreDb..."
docker exec $container psql -U tmp -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $restoreDb;"
if (-not $?) {
    throw "Не удалось создать $restoreDb"
}

$remoteDump = "/tmp/restore_check_input.dump"
docker cp $DumpFile "${container}:${remoteDump}"
if (-not $?) {
    docker exec $container psql -U tmp -d postgres -c "DROP DATABASE $restoreDb;" | Out-Null
    throw "Не удалось скопировать дамп в контейнер"
}

Write-Host "Восстановление (pg_restore) в $restoreDb..."
docker exec $container pg_restore -U tmp -d $restoreDb --no-owner --no-acl $remoteDump
$restoreOk = $?
docker exec $container rm -f $remoteDump | Out-Null

if (-not $restoreOk) {
    Write-Host "ОШИБКА восстановления. Удаляю только $restoreDb..." -ForegroundColor Red
    docker exec $container psql -U tmp -d postgres -c "DROP DATABASE $restoreDb;"
    throw "ОШИБКА: проверка восстановления НЕ ПРОЙДЕНА."
}

$flywayMax = docker exec $container psql -U tmp -d $restoreDb -t -A -c "SELECT COALESCE(MAX(version),'NONE') FROM flyway_schema_history WHERE success;"
$flywayCount = docker exec $container psql -U tmp -d $restoreDb -t -A -c "SELECT COUNT(*) FROM flyway_schema_history WHERE success;"
$hasSecurity = docker exec $container psql -U tmp -d $restoreDb -t -A -c "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='security';"
$hasWarehouse = docker exec $container psql -U tmp -d $restoreDb -t -A -c "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='warehouse';"
$hasProduction = docker exec $container psql -U tmp -d $restoreDb -t -A -c "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='production';"

Write-Host "Метаданные после restore:"
Write-Host "  flyway success count: $("$flywayCount".Trim())"
Write-Host "  flyway max version:   $("$flywayMax".Trim())"
Write-Host "  schema security:      $("$hasSecurity".Trim())"
Write-Host "  schema warehouse:     $("$hasWarehouse".Trim())"
Write-Host "  schema production:    $("$hasProduction".Trim())"

$ok = (("$flywayCount".Trim() -as [int]) -gt 0) -and
      ("$hasSecurity".Trim() -eq "1") -and
      ("$hasWarehouse".Trim() -eq "1") -and
      ("$hasProduction".Trim() -eq "1")

$pilotStill = docker exec $container psql -U tmp -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname='tmp_pilot';"
$stageStill = docker exec $container psql -U tmp -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname='tmp_gui_stage5';"
if ("$pilotStill".Trim() -ne "1" -or "$stageStill".Trim() -ne "1") {
    throw "КРИТИЧНО: tmp_pilot или tmp_gui_stage5 недоступны. Временную БД не удаляю автоматически."
}

Write-Host "Удаление только временной БД $restoreDb..."
docker exec $container psql -U tmp -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE $restoreDb;"
if (-not $?) {
    throw "Не удалось удалить временную $restoreDb. Проверьте вручную. tmp_pilot и tmp_gui_stage5 не трогались."
}

if (-not $ok) {
    throw "ОШИБКА: restore выполнен, но метаданные схемы неудовлетворительны."
}

Write-Host "УСПЕХ: резервная копия восстановима. Временная БД удалена." -ForegroundColor Green
Write-Host "tmp_pilot и tmp_gui_stage5 не изменялись."
Write-Host "Готово."