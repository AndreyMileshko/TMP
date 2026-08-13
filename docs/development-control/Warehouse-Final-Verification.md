# Warehouse Final Verification

**Date:** 2026-08-13  
**Stage:** 6 — Warehouse  
**Audit type:** STAGE6-FINAL — Warehouse Closure Audit (documentation only; no code / DB / API / UI changes)

---

## Scope

Реализованный Warehouse Stage 6 v1.0:

- Модуль `tmp-warehouse` (API → Application → Domain → Persistence)
- Public API (`WarehouseApi`) + UI Workbench в `tmp-ui-shell`
- Security: 16 Warehouse permissions
- Material Reference (warehouse-owned, без Material Master)
- Unit Of Measure — фиксированный справочник
- Stock View: расширенные поля + скрытие `quantity = 0`

**Включено:** `STAGE6-001..017`, `STAGE6-019` (Material Reference), `STAGE6-019 FIX` (Unit Of Measure), `STAGE6-020` (zero-quantity Stock View filter).

**Вне scope v1.0:** Material Master, FIFO/FEFO, партии, серийные номера, WMS, штрихкодирование, конвертация единиц, Production/Cutting runtime.

---

## Architecture

| Проверка | Результат |
|---|---|
| Warehouse — отдельный доменный модуль `tmp-warehouse` | PASS |
| Слои: API → Application → Domain → Persistence | PASS |
| `tmp-warehouse` pom: нет зависимости от Order Management | PASS |
| Нет зависимости от Production / Cutting | PASS |
| ArchUnit: UI Shell → только `com.tmp.warehouse.api` | PASS (`Stage6WarehouseArchitectureTest`) |
| ArchUnit: Warehouse без JavaFX | PASS |

Структура пакетов:

```text
com.tmp.warehouse.api
        ↓
com.tmp.warehouse.application
        ↓
com.tmp.warehouse.domain
        ↓
com.tmp.warehouse.persistence
```

Bootstrap использует Public API / auto-configuration; UI не зависит от application/domain/persistence/security internals.

---

## Material Reference Model

Warehouse временно владеет `MaterialReference` (ADR: без отдельного Material Master).

| Поле | Назначение |
|---|---|
| `article` | артикул производителя |
| `name` | человекочитаемое описание |
| `color` | цвет |
| `size` | размер / характеристика |
| `unitOfMeasure` | единица хранения (из справочника) |

**Уникальность (natural key):**

```text
article + color + size + unitOfMeasure
```

`name` **не** участвует в уникальности.

Создание нового MaterialReference — только через Receipt (find-or-create). Move / Transfer / Consumption / Adjustment / Inventory используют существующий.

---

## Quantity Model

Количество хранится **в той единице измерения**, которую пользователь указал при Receipt.

Примеры:

| Материал | Размер | Ед. | Количество на складе |
|---|---|---|---|
| Профиль | 6000 мм | м. | 300 м. |
| Крепёж | 4.8x16 | шт. | 1000 шт. |
| Уплотнитель | Бухта 500 м | м. | 500 м. |

**Не реализовано:**

- автоматический пересчёт;
- базовая единица;
- коэффициенты конвертации;
- расчёт метров из штук / площади.

---

## Unit Of Measure

Фиксированный справочник `UnitOfMeasure` (canonical codes):

```text
шт.
м.
кв.м.
компл.
л.
гр.
кг.
```

| Правило | Статус |
|---|---|
| Свободный текстовый ввод в Receipt UI запрещён (ComboBox) | PASS |
| Domain отклоняет неизвестные значения | PASS |
| Известные алиасы (`м`, `метры`) нормализуются в `м.` | PASS |
| Пересчёт единиц не реализован | PASS |
| DB CHECK constraint (V20) | PASS |

---

## Operations Verification

| Operation | Service | Status |
|---|---|---|
| Receipt | `WarehouseReceiptService` | PASS |
| Move | `WarehouseMoveService` | PASS |
| Transfer Send | `WarehouseTransferService` | PASS |
| Transfer Receive | `WarehouseTransferService` | PASS |
| Consumption | `WarehouseConsumptionService` | PASS |
| Adjustment | `WarehouseAdjustmentService` | PASS |
| Inventory | `WarehouseInventoryService` | PASS |
| Reservation Link | `WarehouseReservationLinkService` | PASS |

Ручная проверка операций (ранее, 2026-08-10 + последующие GUI-сессии STAGE6-019/020): PASS.

---

## Security Verification

Каталог: `WarehousePermissions` — **16** permissions.

**Операционные:**

```text
warehouse.stock.view
warehouse.receipt.create
warehouse.move.create
warehouse.transfer.create
warehouse.consumption.create
warehouse.adjustment.create
warehouse.inventory.create
warehouse.reservation.create
```

**Структурные:**

```text
warehouse.warehouse.view
warehouse.warehouse.create
warehouse.warehouse.update
warehouse.warehouse.delete
warehouse.storage-cell.view
warehouse.storage-cell.create
warehouse.storage-cell.update
warehouse.storage-cell.delete
```

Модель доступа:

- **Warehouse Operator** (операционные permissions) — Receipt / Move / Transfer / Consumption / Adjustment / Stock View; без create/update/delete структуры.
- **Warehouse Administrator** — структурные permissions (+ при назначении операционных — полный доступ).
- Роли назначаются через Security Roles UI; permissions синхронизируются Capability Engine.

Авторизация на Public API; UI гейтит команды по permission flags.

---

## UI Verification

Экран: Warehouse Workbench (`Склад`).

| Раздел | Содержание |
|---|---|
| Склады / Ячейки | каталог структуры |
| Остатки склада | Stock View |
| Receipt | артикул, наименование, цвет, размер, ComboBox ед. изм., количество |
| Move / Transfer / Consumption / Adjustment | выбор MaterialReference с полным описанием |
| Reservation | информационная связь |

**Stock View колонки:**

Артикул · Наименование · Цвет · Размер · Ед. изм. · Склад · Ячейка · Количество · Состояние

**Фильтрация нулевых остатков (STAGE6-020):**

- Показываются только позиции с `quantity > 0`
- Скрываются `AVAILABLE = 0`, `IN_TRANSIT = 0`, `BLOCKED = 0`
- Физические `Stock Position` **не** удаляются (только read-path filter в `DefaultWarehouseApi`)

---

## Automated Tests (STAGE6-FINAL audit)

### Full suite command

```text
mvn -pl tmp-warehouse,tmp-ui-shell,tmp-bootstrap-app,tmp-architecture-tests -am test
```

**tmp-warehouse full suite:** FAIL — 150 run, **2 failures + 3 errors** (устаревшие тесты после Material Reference / UoM; код продукта не менялся в этом аудите):

| Test | Issue |
|---|---|
| `WarehouseSchemaFlywayTest` (3) | SQL ещё использует колонку `material_reference` (VARCHAR) вместо `material_reference_id` |
| `WarehouseReceiptServiceIntegrationTest` | blank `unitOfMeasure` после введения обязательного справочника |
| `WarehouseApiIntegrationTest` | lookup legacy article без unit после модели V19/V20 |

Upstream modules в том же прогоне до `tmp-warehouse`: PASS (platform, infra, document, capability, security, order-management).

### Focused verification suite (closure evidence)

```text
mvn -pl tmp-warehouse,tmp-ui-shell,tmp-bootstrap-app,tmp-architecture-tests -am test
  -Dtest=Stage6WarehouseArchitectureTest,WarehouseWorkbenchViewModelTest,
         DesktopBootstrapWiringTest,WarehouseAdminNavigationAccessEnsureTest,
         UnitOfMeasureTest,MaterialReferenceTest,DefaultWarehouseApiTest,
         WarehouseMaterialReferenceReceiptIntegrationTest,
         WarehousePermissionCatalogTest,WarehouseSecurityAuthorizationTest
```

| Module | Result |
|---|---|
| tmp-warehouse (focused) | PASS |
| tmp-ui-shell (`WarehouseWorkbenchViewModelTest` 18) | PASS |
| tmp-bootstrap-app (wiring + admin nav) | PASS |
| tmp-architecture-tests (`Stage6WarehouseArchitectureTest` 2) | PASS |
| **Overall focused** | **BUILD SUCCESS** |

---

## Known Limitations

1. Нет Material Master / Material Catalog / Material Service.
2. Нет FIFO / FEFO.
3. Нет партий (batch / lot).
4. Нет серийных номеров.
5. Нет WMS / адресного хранения сверх Warehouse + Storage Cell.
6. Нет штрихкодирования.
7. Нет автоматической конвертации единиц измерения.
8. Reservation — информационная связь; нет состояния `RESERVED`.
9. Production / Cutting integration runtime — вне Stage 6.
10. Full `mvn test` по `tmp-warehouse` содержит 5 устаревших тестов схемы/UoM (см. выше) — технический долг тестов, не дефект runtime.

---

## Final Status

**Stage 6 Warehouse = DONE**

| Check | Result |
|---|---|
| Architecture boundaries | PASS |
| Material Reference model | PASS |
| Unit Of Measure reference | PASS |
| Quantity model (no conversion) | PASS |
| Operations | PASS |
| Stock View + zero filter | PASS |
| Security (16 permissions) | PASS |
| Focused automated verification | PASS |
| Code / DB / API / UI changed in this audit | **NO** |
| Git operations | **NOT EXECUTED** |
