# TMP Development Status

**Mode:** Autonomous Cursor Agent  
**Project status:** Stage 6 implementation in progress  
**Current Stage:** Stage 6 - IN PROGRESS  
**Current Task:** none  
**Last completed task:** STAGE6-003 (Warehouse Database Schema)  
**Active blockers:** NONE  

```text
STAGE5-050 = DONE
STAGE5-051 = DONE
STAGE5-052 = DONE
STAGE5-052A = DONE
STAGE5-053 = DONE
STAGE5-054 = DONE
STAGE5-055 = DONE
STAGE5-056 = DONE
STAGE5-057 = DONE
STAGE5-058 = DONE
Stage 5 Core Order Management = DONE
Stage 5 Order Intake Extension = DONE
Stage 5 = DONE
STAGE6-000 = DONE (Start Gate documentation only)
STAGE6-001 = DONE
STAGE6-002 = DONE
STAGE6-003 = DONE
STAGE6-004..015 implementation queue = READY
Stage 6 = IN PROGRESS
Active blockers = NONE
```

---

# Stage Progress

| Stage | Name | Status | Progress |
|---:|---|---|---:|
| 0 | Development Foundation | DONE | 100% |
| 1 | Platform Core | DONE | 100% |
| 2 | Document Engine | DONE | 100% |
| 3 | Capability Engine | DONE | 100% |
| 4 | Security | DONE | 100% |
| 5 | Order Management | DONE | 100% |
| 6 | Warehouse | IN PROGRESS | 20% |
| 7 | Production | NOT STARTED | 0% |
| 8 | Cutting Optimization | NOT STARTED | 0% |
| 9 | Analytics | NOT STARTED | 0% |

---

## Stage 5 — Final Closure (2026-08-06)

| Part | Status |
|---|---|
| Core Order Management (`STAGE5-001..050`) | DONE |
| Order Intake Extension (`STAGE5-051..057`) | DONE |
| Imported Order Lifecycle + Final STXT (`STAGE5-058`) | DONE |
| STAGE5-058 Manual GUI Smoke | PASS |
| Stage 5 overall | **DONE** |

### Delivered functionality

- Order create / Item create / Revision management / Specification management / approve lifecycle
- STXT import (multi-order, multi-item, specification per item)
- Import → Document approve/activate → uniform `ACTIVE` (Order / Item / Revision / Specification)
- No `ImportMetadata` / `sourceType` / `creationSource` / separate import lifecycle
- ACTIVE read-only; changes only via Revision
- Stage 6 = **NOT STARTED** (Warehouse / Production / Cutting / Analytics not started)

**Далее:** Stage 6 implementation — только по явному решению пользователя после Start Gate. Git — только пользователь.

---

## Stage 6 — Start Gate (2026-08-06)

| Item | Status |
|---|---|
| ADR-032 Material Responsibility — No Separate Material Master | **Accepted** |
| Warehouse Specification v1.3 | **Updated** |
| Order Management Specification v1.8 (§28) | **Updated** |
| Stage 6 Manifest | **Updated** |
| Material Master | **Not created** (final decision) |
| Stage 6 code | **NOT STARTED** |

### Material Handling Decision (summary)

- Нет отдельного Material Master.
- Order Management: материалы в контексте ACTIVE Specification.
- Warehouse: только складское состояние; MaterialReference из Specification.
- Material Mapping: пользовательское сопоставление внешних наименований; не блокирует импорт; не Warehouse.
- Production → Warehouse: Production рассчитывает потребность; Warehouse отвечает по наличию/резервированию.

### Warehouse doc refresh (2026-08-06)

- Warehouse spec упрощён до модели Stage 6 Start Gate (v1.3).
- Batch / FIFO / FEFO / Supplier Batch исключены из Warehouse v1.0.
- Reservation зафиксирован как информационная связь без изменения `Stock Position`.
- Stage 6 code remains **NOT STARTED**.
