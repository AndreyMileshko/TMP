# Stage Manifest — Reference Data / Colors (COL track)

## Source

- TMP-SPEC-015 — `docs/TMP/TMP_Initial_Documents/architecture/Reference book/Colors/TMP-Reference-Data-Colors-Specification.md` (**v1.0 Accepted**)
- ADR-039 — Reference Data Capability Architecture (Accepted; `05-ADR/TMP-Architecture-Decisions.md` v1.24)
- `TMP-Colors-Implementation-Plan.md` (**v1.0**) — полные определения задач
- `COL-000-Architecture-Audit.md` — технический baseline (HEAD `d29a4fcdc9299758fecfb4dbc3bcee9aaedfeaa1`)
- Order Management Specification v1.12 (뿯½27.6, 뿯½28.5); Warehouse Specification v1.20 (뿯½4, 뿯½15.1.1)
- Database Specification; Security Specification; Capability Engine Specification; TMP UI Standard
- Начальные данные: `TMP_Цвета_и_сопоставления_СО7_согласовано.xlsx`

## Status

**START GATE PASSED (COL-005, 2026-11-08)**
**Implementation: IN PROGRESS** — COL-010/011/023/020A/021A/020B/021B/022A/022B/030A = DONE (COL-030A closed 2026-11-10; PO GUI PASS on tmp_pilot V59; matching OFF); next COL-030B PLANNED (deps satisfied; do not start until READY). UI-NAV-001 registered PLANNED outside COL track.
**Active blockers:** NONE

## Start Gate

| Item | Requirement | Result |
|---|---|---|
| Audit | COL-000 read-only audit | PASS (READY WITH CONDITIONS) |
| Business decisions | Q-01 … Q-07, Q-05-bis утверждены product owner | PASS |
| ADR | ADR-039 Accepted | PASS |
| Specification | TMP-SPEC-015 v1.0 Accepted | PASS |
| Related specs | OM Spec 뿯½28.5 / 뿯½27.6; Warehouse Spec 뿯½4 / 뿯½15.1.1 уточнены | PASS |
| Migrations | V58 / V59 свободны и зарезервированы | PASS |
| Tasks | COL-010 … COL-060B зарегистрированы в WORK-QUEUE | PASS |

## Planning domains

1. Модуль `tmp-reference-data`, Capability `reference`, схема `reference`, ArchUnit-границы (COL-010).
2. Начальные данные 860 / 245 / OFF и нормализация ключей (COL-011).
3. Права `reference.*` и выдача Security Administrator (COL-023).
4. Каталог цветов: домен, persistence, API, атомарное архивирование (COL-020A, COL-020B).
5. Соответствия СО7: домен, persistence, API, сохранение из импорта (COL-021A, COL-021B).
6. Переключатель и API разрешения для импорта (COL-022A, COL-022B).
7. JavaFX 뿯½Справочники 뿯↽ Цвета뿯½ (COL-030A, COL-030B).
8. Контракт ограничений цвета OM, координатор и окно разрешения, интеграция в импорт (COL-040A, COL-040B, COL-041A, COL-041B).
9. Регрессия Warehouse / Production (COL-050A, COL-050B).
10. Подготовка среды приёмки (Q-01) и пакетная приёмка (COL-060A, COL-060B).

## Task order

COL-010 뿯↽ COL-011 뿯↽ COL-023 뿯↽ COL-020A 뿯↽ COL-021A 뿯↽ COL-020B 뿯↽ COL-021B 뿯↽ COL-022A 뿯↽ COL-022B 뿯↽ COL-030A 뿯↽ COL-030B 뿯↽ COL-040A 뿯↽ COL-040B 뿯↽ COL-041A 뿯↽ COL-041B 뿯↽ COL-050A 뿯↽ COL-050B 뿯↽ COL-060A 뿯↽ COL-060B (19 задач).

## Explicitly out of scope

- Material Master; изменение точного сравнения цвета в Warehouse / Production; изменение production-кода Warehouse / Production.
- Изменение парсера STXT, `OrderImportValidator`, `DefaultOrderImportService`, транзакции confirm.
- Зависимость OM / Warehouse / Production от Reference Data.
- Изменение кода и физическое удаление сохранённых цветов; карточки и наименования цветов; каталог комбинаций.
- Соответствие `Без цвета 뿯↽ 00` по умолчанию; особая логика для обобщённых названий.
- Firebird / `CLRNO`; 393 / 725 аналитических строк.
- Конвертация исторических цветов; слои совместимости; ретроспективные изменения.
- Платформенный Settings framework; generic framework справочников; новые типы документов / Domain Events; новые бизнес-роли.
- Destructive-операции с БД без резервной копии и отдельного разрешения (только COL-060A).

## Exit criteria

| # | Criterion | Result |
|---|---|---|
| 1 | Reference Data владеет цветами, соответствиями и переключателем в схеме `reference` | PENDING |
| 2 | Seed 860 / 245 / OFF воспроизводим и не перезаписывает пользовательские изменения | PASS (COL-011; Testcontainers) |
| 3 | OFF-путь импорта без изменений; ON — единое окно по TMP-SPEC-015 뿯½8 | PENDING |
| 4 | Соответствия из импорта — только после успешного confirm, отдельной TX, без перезаписи | PENDING |
| 5 | Права `reference.*` проверяются на сервере; Security Administrator имеет все четыре | PASS (COL-023; registration + ensure; enforcement at API tasks) |
| 6 | ArchUnit: нет зависимостей OM / Warehouse / Production 뿯↽ Reference Data | PENDING |
| 7 | Регрессия Warehouse / Production PASS без изменений их production-кода | PENDING |
| 8 | Ручная приёмка COL-AC-01 … 33 на свежем `TMP.exe` подтверждена пользователем | PENDING |
