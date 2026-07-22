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
| 5 | Order Management Specification | Document Engine public API; Database Specification |
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