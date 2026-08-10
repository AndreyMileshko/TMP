# Warehouse Regression Analysis

**Date:** 2026-08-10  
**Commit under analysis:** `e2e77ecadf7f6a4473f98de4b74622214a0dd818` — *Stage 6 fix broken* (Warehouse Material Display Information)  
**Mode:** Diagnostic only — no code fixes applied  
**Git operations:** NOT EXECUTED (except read-only `git show` for commit inspection)

---

## Summary

После коммита Material Display Information **цепочка выполнения складских операций на уровне Domain / Application / Public API не изменена** и **автоматические integration-тесты операций проходят** (`WarehouseApiIntegrationTest`, `DefaultWarehouseApiTest`).

Регрессия проявляется на уровне **UI + permissions + read-path обогащения StockView**, а также **нарушений архитектурных границ** (CI gate). Наиболее вероятная причина жалобы «операции не работают» для пользователя — **невозможность заполнить формы операций или проверить результат** из‑за связки `warehouse.stock.view` с загрузкой складов/ячеек и отображением материала, а не падение `executeWarehouseOperation`.

---

## Regression Cause

### Primary (user-visible)

1. **Permission coupling в UI (существовала до коммита, усилена display-слоем)**  
   `WarehouseWorkbenchViewModel` загружает склады и ячейки только при `canView` (`warehouse.stock.view`):
   - `ensureWarehouseChoicesLoaded()` — строка 910–912: выход без загрузки, если `!canView`
   - `reloadCells()` — строка 943–944: выход без загрузки ячеек, если `!canView`

   Роль с правами только на операции (`warehouse.receipt.create`, `warehouse.move.create`, …) **без** `warehouse.stock.view` видит пустые ComboBox «Склад» / «Ячейка» и получает ошибку валидации при submit (`Выберите склад` / `Выберите ячейка`). Операция до API не доходит.

2. **Новый display API дублирует то же ограничение**  
   `DefaultWarehouseApi.getMaterialReferenceDisplay()` требует `warehouse.stock.view`.  
   `refreshMaterialDisplay()` в ViewModel дополнительно проверяет `canView` и молча скрывает описание материала. Submit не блокируется, но UX создаёт впечатление «материал/склад не работает».

3. **Изменён read-path StockView (верификация после операции)**  
   `DefaultWarehouseApi.toStockView()` теперь вызывает `MaterialReferenceDisplayPort.resolve()` и lookup каталога (`resolveWarehouseLabel`, `resolveStorageCellLabel`).  
   `executeWarehouseOperation` **не затронут**, но экран «Остатки склада» после Receipt/Move может показывать ошибку или пустые display-поля → пользователь трактует как «операция не сработала».

### Secondary (CI / quality gate)

4. **Architecture test failures** — полный `mvn test` падает на `tmp-architecture-tests` (2 нарушения), хотя модульные тесты warehouse/ui-shell/bootstrap проходят.

---

## Affected Components

| Layer | Component | Impact |
|---|---|---|
| `tmp-warehouse` | `WarehouseApi.StockView`, `MaterialReferenceDisplayView` | Contract extended; breaking constructor change |
| `tmp-warehouse` | `DefaultWarehouseApi` | New dependency `MaterialReferenceDisplayPort`; enriched `getStock`; new `getMaterialReferenceDisplay` |
| `tmp-warehouse` | `MaterialReferenceDisplayPort`, `CodeOnlyMaterialReferenceDisplayPort` | New display resolution on stock reads |
| `tmp-order-management` | `MaterialReferenceDisplayReadPort`, `JdbcMaterialReferenceDisplayReadAdapter` | Read-only spec lookup for display |
| `tmp-bootstrap-app` | `WarehouseMaterialDisplayConfiguration` | Bridges Order query port → Warehouse display port |
| `tmp-ui-shell` | `WarehouseWorkbenchViewModel`, `Controller`, `FXML` | Extended stock table; material display labels on operation forms |
| `tmp-architecture-tests` | `Stage5OrderManagementArchitectureTest`, `Stage6WarehouseArchitectureTest` | **FAIL** after commit |

**NOT affected (operation write path):**

- `WarehouseOperationEngine`
- `WarehouseReceiptService`, `WarehouseMoveService`, `WarehouseTransferService`, `WarehouseConsumptionService`, `WarehouseAdjustmentService`
- `ExecuteOperationCommand`, `OperationResult`
- Persistence / domain aggregates

---

## API Changes

### StockView (breaking contract extension)

**Before (`e2e77ec^`):**
```text
materialCode, warehouseId, storageCellId, quantity, stockState
```

**After:**
```text
article, materialName, color, size, unitOfMeasure,
warehouse, storageCell, quantity, stockState,
materialCode (alias=article), warehouseId, storageCellId
```

- Все новые string-поля **non-null** (пустая строка допустима).
- Factory: `StockView.of(...)` — 11 параметров.
- Старый compact constructor **удалён** → compile break для прямых `new StockView(materialCode, warehouseId, ...)`.

### New API method

```java
MaterialReferenceDisplayView getMaterialReferenceDisplay(String materialCode);
```

- Требует permission: `warehouse.stock.view`
- Не участвует в `executeWarehouseOperation`

### Operation DTO

`ExecuteOperationCommand`, `OperationResult` — **без изменений** в коммите.

### MaterialReference (domain)

`MaterialReference` — **без изменений** (только `materialCode`).

---

## Failed Operations

### Automated — operation execution

| Operation | API / Service tests | Result |
|---|---|---|
| Receipt | `WarehouseApiIntegrationTest.receiptThenGetStockAndAvailabilityThroughPublicApi` | **PASS** |
| Consumption | `WarehouseApiIntegrationTest.consumptionThroughPublicApiUpdatesStock` | **PASS** |
| Move / Transfer / Adjustment / Reservation | Module unit & integration suites in `tmp-warehouse` | **PASS** (full module `mvn test`) |
| Inventory | Via Adjustment path | Not separately regressed in commit |

### Automated — full reactor

| Test | Result |
|---|---|
| `mvn test` (reactor) | **FAIL** — `tmp-architecture-tests` (2 failures) |
| `tmp-warehouse`, `tmp-order-management`, `tmp-ui-shell`, `tmp-bootstrap-app` | **PASS** |

### UI / role-based (inferred from code analysis)

| Operation | Admin (all permissions) | Operator (operation-only, no stock.view) |
|---|---|---|
| Receipt | Expected **works** (API proven) | **Blocked at UI** — empty warehouse/cell combos (pre-existing gating) |
| Move | Expected **works** | **Blocked at UI** |
| Transfer Send/Receive | Expected **works** | **Blocked at UI** |
| Consumption | Expected **works** | **Blocked at UI** |
| Adjustment | Expected **works** | **Blocked at UI** |
| Reservation Link | Unchanged | Depends on reservation permission |
| Stock load (verification) | Works; display fields may be empty + WARN in logs | **Denied** (`loadStock` requires `canView`) |

### Manual run (2026-08-10, prior session)

| Step | Result |
|---|---|
| App startup (JAR, dev profile, Docker PG :55432) | **PASS** — Flyway v18, Spring started, JavaFX launched |
| Warehouse operations GUI | **Not instrumented** in this diagnostic session (no GUI automation) |
| App shutdown | Normal exit (exit code 0) |

---

## Stack Traces

### 1. Architecture — UI → Warehouse application layer

```
Stage6WarehouseArchitectureTest.uiShellUsesOnlyWarehousePublicApi
Architecture Violation: com.tmp.ui.shell.. must not depend on com.tmp.warehouse.application..
Method WarehouseWorkbenchViewModel.formatMaterialDisplay(...)
  calls MaterialDisplayFormatting.formatDescription(...)
  (WarehouseWorkbenchViewModel.java:876)
```

### 2. Architecture — Bootstrap → Order application layer

```
Stage5OrderManagementArchitectureTest.externalModulesUseOnlyOrderPublicApi
Architecture Violation: external modules may depend only on com.tmp.order.api..
WarehouseMaterialDisplayConfiguration.specificationBackedMaterialReferenceDisplayPort(...)
  parameter/calls MaterialReferenceDisplayReadPort (application.query, not api)
  (WarehouseMaterialDisplayConfiguration.java:22-26)
```

### 3. Runtime WARN (non-fatal, stock read path)

```
CodeOnlyMaterialReferenceDisplayPort — Material display fields unavailable from current source: materialCode=...
```

Observed during `WarehouseApiIntegrationTest` when material not found in ACTIVE Specification.

---

## Root Cause

**Операции записи (Receipt/Move/Transfer/Consumption/Adjustment) не сломаны на уровне бизнес-логики и Public API.**

Регрессия воспринимается пользователем из‑за комбинации:

1. **UI gating по `warehouse.stock.view`** для загрузки складов/ячеек в формах операций (логика **не изменена** коммитом, но блокирует role-only сценарии).
2. **Новый display-слой** привязан к тому же permission (`getMaterialReferenceDisplay` + `refreshMaterialDisplay`), усиливая ощущение неработоспособности.
3. **Обогащение StockView** меняет экран остатков; ошибки/пустые поля при проверке трактуются как сбой операции.
4. **Архитектурные нарушения** ломают полный CI gate (`mvn test`), что может блокировать доставку сборки.

---

## Recommended Fix Direction

*(Direction only — not implemented in this diagnostic task.)*

1. **Decouple operation UI from `warehouse.stock.view`:**
   - `ensureWarehouseChoicesLoaded` / `reloadCells` должны использовать permission, достаточный для операции (или отдельный read-only catalogue permission), не только `canView`.
   - `getMaterialReferenceDisplay` — либо отдельный read permission для display, либо разрешить вызывающим операциям без stock.view.

2. **Move display formatting to Public API layer:**
   - Перенести `formatDescription` в `com.tmp.warehouse.api` (или inline в ViewModel) — устранит `Stage6WarehouseArchitectureTest` failure.

3. **Expose display lookup through Order Public API:**
   - Перенести `MaterialReferenceDisplayReadPort` контракт в `com.tmp.order.api` (DTO уже там: `MaterialReferenceDisplayDto`).
   - Bootstrap wiring должен зависеть только от `com.tmp.order.api..`.

4. **Keep operation write path isolated:**
   - Не вызывать `MaterialReferenceDisplayPort` из `executeWarehouseOperation` / operation services.
   - Display enrichment только в read DTO mapping (`toStockView`, `getMaterialReferenceDisplay`).

5. **Role matrix verification:**
   - Добавить UI/Integration test: user with `warehouse.receipt.create` only can complete Receipt end-to-end.

6. **Rebuild packaged app** after fixes (`mvn -pl tmp-bootstrap-app -am package` or `-Ppackage`) — убедиться, что runtime binary обновлён.

---

## Confirmations

| Item | Status |
|---|---|
| Material Master created | **NO** |
| Material owner changed | **NO** |
| Git write operations | **NOT EXECUTED** |
| Production code modified in this analysis | **NO** |
