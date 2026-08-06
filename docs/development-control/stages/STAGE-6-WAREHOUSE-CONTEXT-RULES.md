# Stage 6 Warehouse Context Rules

**Stage:** 6 — Warehouse  
**Purpose:** Полный контекст разработки Warehouse через Cursor без старта разработки  
**Status:** Active for Stage 6 execution

---

## 1. Общие правила контекста

- Использовать только документы, необходимые для текущей задачи.
- Ограничивать контекст границами текущей задачи `STAGE6-xxx`.
- Запрещено загружать/читать весь проект целиком.
- Запрещено читать модули и документы будущих задач без прямой необходимости.
- Работать только в рамках текущей задачи из `WORK-QUEUE.md`.
- Перед началом кода выполнить Question Gate и проверить scope/acceptance/verification.

---

## 2. Интеграционные границы Warehouse

Warehouse НЕ имеет доступа к:

- внутренней логике Order Management;
- расчётам Production;
- алгоритмам Cutting Optimization.

Warehouse работает только через:

- Public API;
- Domain Events.

---

## 3. Архитектурные ограничения Stage 6

Запрещено создавать или добавлять в scope:

- `Material Master`;
- `Batch`;
- `FIFO`;
- `FEFO`;
- сложное резервирование (с изменением stock state и lifecycle release).

Также запрещено:

- перенос бизнес-логики в UI/Infrastructure;
- прямую мутацию `StockPosition` вне `WarehouseOperation`;
- обход модульных границ прямыми зависимостями на внутренние классы чужих capability.

---

## 4. Правила тестирования Stage 6

Каждая задача Stage 6 обязана включать:

- unit tests для локальных инвариантов/правил;
- integration tests для module/API/persistence взаимодействий;
- verification steps (конкретные команды проверки и ожидаемый PASS).

Минимальные правила:

- без PASS verification задача не может получить `DONE`;
- при FAIL фиксируется причина и выполняется исправление в пределах текущего scope;
- переход к следующей задаче только после `VERIFYING -> DONE`.

---

## 5. Task Context Matrix

## STAGE6-001 — Warehouse module foundation

- **Разрешённые документы:** `STAGE-6-WAREHOUSE.md`, `Warehouse-Specification.md`, `TMP-Architecture-Overview.md`, `TMP-Development-Guide.md`
- **Разрешённые модули:** `tmp-warehouse`, root module descriptors, `tmp-architecture-tests` (read for rules/tests)
- **Запрещённый контекст:** внутренние реализации `tmp-order-management`, `tmp-production`, `tmp-cutting-optimization`; весь репозиторий целиком
- **Ожидаемый результат:** каркас модуля и package boundaries готовы, сборка проходит
- **Критерии проверки:** unit smoke, architecture boundary tests, module compile/test PASS

## STAGE6-002 — Warehouse domain model

- **Разрешённые документы:** `Warehouse-Specification.md` (§1-10, §20-21), `TMP-Code-Quality-Standards.md`
- **Разрешённые модули:** `tmp-warehouse` (domain/application contracts), `tmp-warehouse-api` (contracts only)
- **Запрещённый контекст:** внутренние доменные модели Order/Production/Cutting, не относящиеся capability
- **Ожидаемый результат:** доменные сущности и инварианты Warehouse реализованы
- **Критерии проверки:** unit tests домена + integration tests application-domain PASS

## STAGE6-003 — Database schema

- **Разрешённые документы:** `Warehouse-Specification.md` (§5-10, §19), `TMP-Database-Specification.md`
- **Разрешённые модули:** `tmp-warehouse` persistence/db migration слой
- **Запрещённый контекст:** чужие схемы вне прямой интеграционной зависимости, полный скан всех SQL файлов проекта
- **Ожидаемый результат:** schema/migrations для Warehouse применяются корректно
- **Критерии проверки:** migration tests + persistence integration tests + verification command PASS

## STAGE6-004 — Stock Position

- **Разрешённые документы:** `Warehouse-Specification.md` (§6-8), `STAGE-6-WAREHOUSE.md`
- **Разрешённые модули:** `tmp-warehouse` domain/application
- **Запрещённый контекст:** попытки реализовать reservation lifecycle > v1.0, внутренности Production planning
- **Ожидаемый результат:** `StockPosition` lifecycle соответствует invariants
- **Критерии проверки:** unit tests stock rules + integration tests operation path PASS

## STAGE6-005 — Warehouse Movement

- **Разрешённые документы:** `Warehouse-Specification.md` (§9), `TMP-Database-Specification.md`
- **Разрешённые модули:** `tmp-warehouse` domain/persistence/application
- **Запрещённый контекст:** mutable history patterns из других модулей, изменение чужих audit моделей
- **Ожидаемый результат:** immutable `WarehouseMovement` history для каждого stock изменения
- **Критерии проверки:** unit immutability tests + persistence integration tests PASS

## STAGE6-006 — Warehouse Operation

- **Разрешённые документы:** `Warehouse-Specification.md` (§10), `STAGE-6-WAREHOUSE.md`
- **Разрешённые модули:** `tmp-warehouse` application/domain/api
- **Запрещённый контекст:** bypass сервисов с прямой записью в `StockPosition`, чужие transaction orchestration
- **Ожидаемый результат:** единый write path через `WarehouseOperation`
- **Критерии проверки:** unit validation tests + integration transaction tests + verification PASS

## STAGE6-007 — Receipt

- **Разрешённые документы:** `Warehouse-Specification.md` (§11-12)
- **Разрешённые модули:** `tmp-warehouse` application/domain/api
- **Запрещённый контекст:** procurement/supplier/price workflows
- **Ожидаемый результат:** корректный Receipt flow с movement фиксацией
- **Критерии проверки:** unit operation tests + integration receipt tests PASS

## STAGE6-008 — Move

- **Разрешённые документы:** `Warehouse-Specification.md` (§13.1)
- **Разрешённые модули:** `tmp-warehouse` application/domain/api
- **Запрещённый контекст:** межскладские transfer сценарии до STAGE6-009, batch selection logic
- **Ожидаемый результат:** внутреннее перемещение между ячейками без потери инвариантов
- **Критерии проверки:** unit move rules + integration move workflow PASS

## STAGE6-009 — Transfer

- **Разрешённые документы:** `Warehouse-Specification.md` (§13.2), `STAGE-6-WAREHOUSE.md`
- **Разрешённые модули:** `tmp-warehouse` application/domain/api
- **Запрещённый контекст:** advanced logistics/WMS routing
- **Ожидаемый результат:** двухэтапный transfer с состоянием `IN_TRANSIT`
- **Критерии проверки:** unit transition rules + integration ship/receive tests PASS

## STAGE6-010 — Consumption

- **Разрешённые документы:** `Warehouse-Specification.md` (§14-15), Production public API contracts
- **Разрешённые модули:** `tmp-warehouse`, production API contracts (read-only)
- **Запрещённый контекст:** расчёт потребности и оптимизации Production, изменение production internal model
- **Ожидаемый результат:** consumption выполняется по входу Production без takeover production logic
- **Критерии проверки:** unit consumption rules + integration tests with production-like requests PASS

## STAGE6-011 — Inventory

- **Разрешённые документы:** `Warehouse-Specification.md` (§11, §19)
- **Разрешённые модули:** `tmp-warehouse` application/domain/api
- **Запрещённый контекст:** batch/FIFO/FEFO inventory strategies, advanced counting workflows
- **Ожидаемый результат:** inventory/adjustment операции v1.0 работают auditable способом
- **Критерии проверки:** unit inventory rules + integration adjustment tests PASS

## STAGE6-012 — Public API

- **Разрешённые документы:** `Warehouse-Specification.md` (§17), `TMP-Architecture-Overview.md`
- **Разрешённые модули:** `tmp-warehouse-api`, `tmp-warehouse`
- **Запрещённый контекст:** раскрытие internal persistence через API, прямой доступ к internal services других capability
- **Ожидаемый результат:** минимальный публичный API Warehouse соответствует spec
- **Критерии проверки:** contract unit tests + integration API tests + verification PASS

## STAGE6-013 — Security integration

- **Разрешённые документы:** `Warehouse-Specification.md` (§18), security capability public contracts
- **Разрешённые модули:** `tmp-warehouse`, `tmp-security` extension points
- **Запрещённый контекст:** изменение ядра security вне extension scope, обход permission checks
- **Ожидаемый результат:** `WAREHOUSE_*` permissions интегрированы во все релевантные операции
- **Критерии проверки:** unit permission mapping tests + integration authorization tests PASS

## STAGE6-014 — UI

- **Разрешённые документы:** `Warehouse-Specification.md` (§11-18), `STAGE-6-WAREHOUSE.md`
- **Разрешённые модули:** `tmp-ui-shell` warehouse UI scope, `tmp-warehouse-api` contracts
- **Запрещённый контекст:** бизнес-логика Warehouse в UI, доступ UI к внутренним сервисам Warehouse без API
- **Ожидаемый результат:** UI покрывает базовые warehouse сценарии v1.0 через Public API
- **Критерии проверки:** unit/viewmodel tests + integration UI tests + verification steps PASS

---

## 6. Execution Note

Этот документ готовит контекст разработки Stage 6 и не означает старт разработки.  
Stage 6 development remains **NOT STARTED** до явного перехода первой implementation-задачи в `IN_PROGRESS`.
