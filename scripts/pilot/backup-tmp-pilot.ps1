# Binary-safe backup of tmp_pilot (pg_dump custom format) via Docker.
# Usage:
#   powershell -ExecutionPolicy Bypass -File .\scripts\pilot\backup-tmp-pilot.ps1
#
# Backups: %LOCALAPPDATA%\TMP-Pilot\backups\tmp_pilot_yyyyMMdd_HHmmss.dump
# Never overwrites an existing dump. Never prints credentials.

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "_PilotCommon.ps1")

Write-TmpPilotBanner -Action "BACKUP"

$paths = Get-TmpPilotPaths
if (-not (Test-Path -LiteralPath $paths.BackupDir)) {
    New-Item -ItemType Directory -Force -Path $paths.BackupDir | Out-Null
}

$container = $paths.Container
$running = docker inspect -f "{{.State.Running}}" $container 2>$null
if ($running -ne "true") {
    throw "Контейнер PostgreSQL '$container' не запущен. Запустите: docker start $container (без recreate)."
}

$dbCheck = docker exec $container psql -U tmp -d postgres -t -A -c "SELECT 1 FROM pg_database WHERE datname='tmp_pilot';"
if ("$dbCheck".Trim() -ne "1") {
    throw "База tmp_pilot не найдена. Резервное копирование отменено."
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$fileName = "tmp_pilot_$stamp.dump"
$localPath = Join-Path $paths.BackupDir $fileName
if (Test-Path -LiteralPath $localPath) {
    throw "Файл резервной копии уже существует, перезапись запрещена: $localPath"
}

$containerDump = "/tmp/$fileName"

Write-Host "Источник: база tmp_pilot в контейнере $container"
Write-Host "Формат: pg_dump custom (-Fc)"
Write-Host "Назначение: $localPath"
Write-Host ""

docker exec $container pg_dump -U tmp -d tmp_pilot -Fc -f $containerDump
if (-not $?) {
    throw "ОШИБКА: pg_dump завершился с ошибкой."
}

docker cp "${container}:${containerDump}" $localPath
if (-not $?) {
    throw "ОШИБКА: не удалось скопировать дамп из контейнера."
}

docker exec $container rm -f $containerDump | Out-Null

if (-not (Test-Path -LiteralPath $localPath)) {
    throw "ОШИБКА: файл резервной копии не создан."
}

$item = Get-Item -LiteralPath $localPath
if ($item.Length -lt 1024) {
    throw "ОШИБКА: файл резервной копии подозрительно мал ($($item.Length) байт)."
}

docker cp $localPath "${container}:/tmp/verify_$fileName"
docker exec $container pg_restore -l "/tmp/verify_$fileName" | Out-Null
if (-not $?) {
    docker exec $container rm -f "/tmp/verify_$fileName" | Out-Null
    throw "ОШИБКА: архив не проходит проверку pg_restore -l."
}
docker exec $container rm -f "/tmp/verify_$fileName" | Out-Null

Write-Host "УСПЕХ: резервная копия tmp_pilot создана." -ForegroundColor Green
Write-Host "Файл: $localPath"
Write-Host "Размер: $($item.Length) байт"
Write-Host "Проверка TOC архива: OK"
Write-Host "Готово."