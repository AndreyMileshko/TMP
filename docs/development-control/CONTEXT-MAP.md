# TMP Context Map

## Назначение

Файл определяет, какие документы Cursor имеет право загружать для конкретного типа задачи.
Cursor обновляет реальные пути во время CONTROL-001.

---

# Global Mandatory Documents

Загружаются не полностью, а только релевантные разделы:

| Document | Path | Status |
|---|---|---|
| TMP Constitution | `docs/TMP/TMP_Initial_Documents/architecture/00-Constitution/TMP-Constitution.md` | FOUND |
| Architecture Decisions | `docs/TMP/TMP_Initial_Documents/architecture/05-ADR/TMP-Architecture-Decisions.md` | FOUND |
| Architecture Overview | `docs/TMP/TMP_Initial_Documents/architecture/04-Architecture-Overview/TMP-004-Architecture-Overview.md` | FOUND |
| Database Specification | `docs/TMP/TMP_Initial_Documents/architecture/15-Database/Database-Specification.md` | FOUND |
| Development Guide | `docs/TMP/TMP_Initial_Documents/architecture/17-Development-Guide/Development-Guide.md` | FOUND |
| Code Quality Standards | `docs/TMP/TMP_Initial_Documents/architecture/17-Development-Guide/Code Quality Standards.md` | FOUND |
| Master Implementation Plan | `docs/TMP/TMP_Initial_Documents/architecture/17-Development-Guide/Master-Implementation-Plan.md` | FOUND |
| Cursor AI Guide | `docs/TMP/TMP_Initial_Documents/architecture/18-Cursor-AI-Guide/Cursor-AI-Guide.md` | FOUND |

---

# Stage Context

| Stage | Primary specification | Additional context |
|---:|---|---|
| 0 | Architecture Overview; Database Specification; UI/UX bootstrap rules | technology ADR; build rules |
| 1 | Platform Core Specification | core ADR; Architecture Overview |
| 2 | Document Engine Specification | Database Specification; document ADR |
| 3 | Capability Engine Specification | Platform Core public API; capability ADR |
| 4 | Security Specification | Database Specification; audit and permission ADR |
| 5 | Order Management Specification (v1.1) | Document Engine public API; Platform Core Event API; Capability Engine; Security public API; Database Specification; Production public contracts (boundary only) |
| 6 | Warehouse Specification | Order public API; Production contracts; Database Specification |
| 7 | Production Specification | Order and Warehouse public APIs; Database Specification |
| 8 | Cutting Optimization Specification | Production contracts; algorithm requirements |
| 9 | Analytics Specification | Warehouse read-only Public API; Capability Engine registration API; Security permission API; Order Management read-only references; UI/UX report screen rules |
| 10 | all public contracts only | integration scenarios; no internal implementations unless defect requires it |
| 11 | release and packaging documents | verification logs and known limitations |

---

# Task-Type Context Rules

## Domain task

Read only:

- relevant Capability Specification section;
- relevant Constitution principles and ADR;
- Domain contracts in current module;
- public contracts of direct dependencies;
- related tests.

## Application task

Read only:

- specific Use Case requirements;
- Domain public API;
- ports used by the Use Case;
- transaction rule;
- related tests.

## Infrastructure task

Read only:

- relevant port/interface;
- Database Specification section;
- migration conventions;
- adapter tests.

## UI task

Read only:

- exact UI/UX screen section;
- Application API/DTO;
- navigation rules;
- related view tests.

## Integration task

Read only:

- public APIs/events of participating modules;
- scenario acceptance criteria;
- integration test infrastructure.

# Stage 4 — Security Context

Основные документы:

- `docs/TMP/TMP_Initial_Documents/architecture/09-Security/Security-Specification.md` (весь документ — небольшой);
- `docs/development-control/stages/STAGE-4-SECURITY.md`;
- "Design decisions fixed for this Stage" preamble в `WORK-QUEUE.md` перед задачами `STAGE4-001..040` (обязательна к прочтению перед любой задачей Stage 4 — фиксирует пакетную структуру `tmp-security`, выбор персистентности, размещение UI-экранов, порядок запуска и другие решения, принятые без blocker).

Разрешённый минимальный code context (публичные API только):

- `com.tmp.core.api..` (`tmp-platform-core`);
- `com.tmp.capability.api..` (`tmp-capability-engine`), включая `Capability`/`CapabilityEngine`/`CapabilityDescriptor`/`PermissionDescriptor`/`CommandDescriptor`/`NavigationContribution`/`ViewDescriptor`;
- Database Specification — только разделы: Schema per Module, Идентификаторы, Общие технические поля, Optimistic Locking, Транзакции, Flyway, Правила именования, Связи между модулями, Аудит изменений;
- Architecture Decisions — только ADR-001..003, ADR-019..022 (module boundaries; computed state not stored; immutable event history) — нет ADR, специфичного для Security/audit/транзакций;
- UI/UX Specification — только разделы: Главное окно, Навигация, Экраны, FXML, Controller, ViewModel, Сообщения пользователю, Обязательные технические экраны;
- существующий код `tmp-ui-shell`/`tmp-bootstrap-app`/`tmp-infra-db` — только файлы, перечисленные в конкретной задаче `WORK-QUEUE.md`.

Запрещается загружать полную реализацию Platform Core, Document Engine или Capability Engine без необходимости (только их публичные API и, при необходимости, конкретный внутренний файл, прямо упомянутый как precedent в задаче).

---

# Stage 5 — Order Management Context

Основные документы:

- `docs/TMP/TMP_Initial_Documents/architecture/10-Order-Management/Order-Management-Specification.md` (v1.1);
- `docs/development-control/stages/STAGE-5-ORDER-MANAGEMENT.md` (полный Stage Manifest);
- релевантные ADR: ADR-003, ADR-004, ADR-017, ADR-018, ADR-019, ADR-020, ADR-021, ADR-022;
- Production Specification (v1.1) — **только** разделы владения производственным состоянием, связи с `Order Item ID`/`Revision`, Public API, Domain Events и интеграции с Order Management (для корректной границы; не для реализации Production).

Разрешённый минимальный code context (публичные API только):

- `com.tmp.core.api..` (Platform Core, включая Event API);
- `com.tmp.capability.api..` (Capability Engine: `Capability`/`CapabilityDescriptor`/`PermissionDescriptor`/`CommandDescriptor`/`NavigationContribution`/`ViewDescriptor`);
- `com.tmp.document.api..` (Document Engine: document lifecycle, Document Processor контракт);
- `com.tmp.security.api..` (Security: `PermissionId` формат, authorization контракт);
- Database Specification — только: Schema per Module, Идентификаторы, Общие технические поля, Optimistic Locking, Транзакции, Flyway, Правила именования, Связи между модулями, Аудит изменений;
- UI/UX Specification — только: Главное окно, Навигация, Экраны, FXML, Controller, ViewModel, Сообщения пользователю;
- существующий код `tmp-ui-shell`/`tmp-bootstrap-app`/`tmp-infra-db` — только файлы, прямо перечисленные в конкретной задаче `WORK-QUEUE.md`.

Запрещается загружать полную реализацию Production, Warehouse, Cutting Optimization и Analytics. Разрешены только их публичные контракты и Domain Events там, где это прямо требуется для границы интеграции.

## Контекст по группам задач Stage 5

| Группа задач | Обязательные документы | Разрешённые публичные контракты | Запрещённые реализации |
|---|---|---|---|
| module bootstrap (`STAGE5-001`) | Manifest §3; Database Spec (Schema per Module); root `pom.xml` конвенции | reactor pom; `com.tmp.*.api` пакеты как зависимости | любая реализация Production/Warehouse/Cutting/Analytics |
| architecture rules (`STAGE5-002`, `STAGE5-035`) | Manifest §3/§13; ADR-003/004/019 | `tmp-architecture-tests` конвенции; `com.tmp.*.api` | внутренние пакеты других Capability |
| domain model (`STAGE5-003..007`) | Spec §8/§9/§10; ADR-017/018 | нет внешних; только собственный домен | persistence/UI/другие Capability |
| immutable specification (`STAGE5-008`) | Spec §9/§10; ADR-018 | собственный домен | persistence/UI |
| repositories & ports (`STAGE5-009`) | Spec §8; Database Spec (Optimistic Locking) | собственный домен | конкретные adapters |
| query API (`STAGE5-010`, `STAGE5-026`) | Spec §15.1; DTO-ownership | `com.tmp.order.api` собственный; вызывающие Capability — только контракт | mutating операции |
| document types & processors (`STAGE5-013..016`) | Spec §16/§16.1; ADR-004 | `com.tmp.document.api` (Document Processor) | бизнес-логика других Capability |
| application commands (`STAGE5-011`, `STAGE5-017`) | Spec §15.2/§16.1 | собственный домен/ports | внешний вызов mutating API |
| domain events (`STAGE5-012`, `STAGE5-018`) | Spec §17; ADR-021; Platform Core Event API | `com.tmp.core.api` (Event API) | события Production/Warehouse |
| capability registration (`STAGE5-020`, `STAGE5-021`) | Spec §18; Security `PermissionId` | `com.tmp.capability.api`, `com.tmp.security.api` | внутренняя реализация Security |
| persistence & migrations (`STAGE5-022..025`) | Spec §19; Database Spec; Flyway (highest = V5 → V6) | `tmp-infra-db` конвенции | хранение production/warehouse/cutting данных |
| transaction boundaries & idempotency (`STAGE5-018`, `STAGE5-019`) | Spec §8 (границы), §16.1 (idempotency) | собственный application/persistence | — |
| UI (`STAGE5-027..032`) | UI/UX Spec (экраны/навигация); Spec §11 (UI scope) | `com.tmp.order.api` (Query/DTO), Document Engine контракт | прямые мутации агрегатов из UI |
| integration tests (`STAGE5-025`, `STAGE5-026`, `STAGE5-034`) | Spec §12 (Testing); Testcontainers инфраструктура | публичные API участников | внутренняя реализация других Capability |
| architecture tests (`STAGE5-035`) | Manifest §13; ADR-003/004/019 | `com.tmp.*.api` | — |
| final verification (`STAGE5-036..038`) | Manifest §16/§17; `RUN-DEVELOPMENT.md` | packaged app | — |

---

# Stage 9 — Analytics Context

Основные документы:

- `docs/TMP/TMP_Initial_Documents/architecture/14-Analytics/Analytics-Specification.md`;
- `docs/development-control/stages/STAGE-9-ANALYTICS.md`.

Основные зависимости:

- Warehouse read-only Public API;
- Capability Engine Public API;
- Security Public API;
- read-only ссылки Order Management;
- UI/UX Specification.

Cursor должен загружать только разделы и публичные контракты, необходимые для текущей задачи.

Запрещается загружать полную реализацию Warehouse, Production, Order Management или Cutting Optimization.

Подробные правила контекста Stage 9 определены в:

```text
docs/development-control/stages/STAGE-9-ANALYTICS.md