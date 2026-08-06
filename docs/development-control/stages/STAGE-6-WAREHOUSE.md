# Stage 6 Manifest — Warehouse

**Stage:** 6 — Warehouse  
**Primary specification:** `docs/TMP/TMP_Initial_Documents/architecture/11-Warehouse/Warehouse-Specification.md` (v1.3)  
**Status:** READY (planning complete), implementation NOT STARTED

---

## 1. Цель Stage 6

Реализовать Warehouse capability строго в границах `Warehouse-Specification.md`:

- Warehouse отвечает только за складское состояние материалов;
- единственный механизм изменения склада — `Warehouse Operation`;
- складская история фиксируется через неизменяемый `Warehouse Movement`;
- интеграция с Production выполняется через проверку доступности и выполнение операций.

---

## 2. Область реализации

В Stage 6 входят:

- модульный каркас Warehouse capability;
- доменная модель: `Warehouse`, `Storage Cell`, `Stock Position`, `Warehouse Operation`, `Warehouse Movement`;
- persistence/schema для складской модели;
- операции v1.0: `Receipt`, `Move`, `Transfer`, `Consumption`, `Adjustment`, `Inventory`;
- `Stock Position` и правила `Stock State` (`AVAILABLE`, `IN_TRANSIT`, `BLOCKED`, optional `SCRAPPED`);
- информационный reservation link (без изменения остатка);
- минимальный Public API Warehouse;
- интеграция с Security Capability;
- UI для базовых складских сценариев v1.0.

---

## 3. Запрещённый scope

Запрещено включать в Stage 6:

- отдельный `Material Master`;
- `Batch`, `Supplier Batch`, партии;
- `FIFO`, `FEFO`, стратегии выбора партии;
- WMS, штрихкодирование, advanced logistics;
- сложные резервы (lock/release workflow с изменением stock state);
- расчёт производственной потребности (владение Production);
- изменение данных спецификаций Order Management.

---

## 4. Документы-источники

Обязательные источники:

1. `docs/TMP/TMP_Initial_Documents/architecture/11-Warehouse/Warehouse-Specification.md`
2. `docs/TMP/TMP_Initial_Documents/architecture/02-Order-Management/Order-Management-Specification.md`
3. `docs/TMP/TMP_Initial_Documents/architecture/TMP-Architecture-Decisions.md` (ADR-032)
4. `docs/TMP/TMP_Initial_Documents/architecture/TMP-Architecture-Overview.md`
5. `docs/TMP/TMP_Initial_Documents/architecture/TMP-Database-Specification.md`
6. `docs/TMP/TMP_Initial_Documents/architecture/TMP-Development-Guide.md`
7. `docs/TMP/TMP_Initial_Documents/architecture/TMP-Code-Quality-Standards.md`

---

## 5. Правила контекста Cursor

Для каждой задачи Stage 6 читать только:

- запись задачи в `docs/development-control/WORK-QUEUE.md`;
- `Warehouse-Specification.md` + явно указанные required documents;
- код в `Allowed code scope` текущей задачи;
- публичные контракты прямых зависимостей (Order/Production/Security);
- последние релевантные записи `IMPLEMENTATION-LOG.md`.

Запрещено:

- читать весь репозиторий без необходимости;
- выполнять будущие задачи Stage 6;
- расширять scope задачи за пределы acceptance criteria.

---

## 6. Stage 6 структура выполнения

Порядок реализации:

1. `STAGE6-001..003` — foundation + domain + schema;
2. `STAGE6-004..006` — core warehouse behavior;
3. `STAGE6-007..011` — business operations v1.0;
4. `STAGE6-012..014` — API + security + UI.

Переход к следующей задаче только после `DONE` и успешной verification текущей задачи.
