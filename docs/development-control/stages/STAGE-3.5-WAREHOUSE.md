# Stage 3.5 Manifest — Warehouse Architecture / Operational UX

**Stage:** 3.5 — Warehouse (UI Modernization + operational workflow alignment)  
**Primary specification:** `docs/TMP/TMP_Initial_Documents/architecture/11-Warehouse/Warehouse-Specification.md` (**v1.8**)  
**Governing ADR:** ADR-037  
**Status:** **IN PROGRESS**

---

## 1. Цель Stage 3.5

Привести Warehouse operational UX и document workflow в соответствие с согласованной моделью ADR-037 **над** уже реализованным Stage 6 inventory foundation — без второго inventory engine.

---

## 2. Substages

| Substage | Name | Status |
|---|---|---|
| 3.5.0 | Architecture Alignment (docs / ADR / specs) | **COMPLETE** |
| 3.5.1 | Warehouse Responsibility (User↔Warehouse many-to-many) | **COMPLETE** |
| 3.5.2 | Transfer Document Foundation (Document Engine + typed payload) | **COMPLETE** |
| 3.5.3 | Deferred Destination Cell at Receive | **COMPLETE** |
| 3.5.4 | Automatic Source Routing + Source Cell Suggestion | **COMPLETE** |
| 3.5.5 | Tasks / Operational Inbox | **COMPLETE** |
| 3.5.6 | Physical Multi-Line Send | **COMPLETE** |
| 3.5.7 | Shortfall / Continuation | **COMPLETE** |
| 3.5.8 | Partial Receive + Reject + Return | **IN PROGRESS** |
| 3.5.8.1 | Settlement + Full Document Receive + Receiver Task | **COMPLETE** |
| 3.5.8.2 | Partial Acceptance + RECEIVE_SHORTFALL + RETURN_PENDING | **COMPLETE** |
| 3.5.8.3 | Reject + TRANSFER_RETURN + Return Task + Close | **NEXT / NOT STARTED** |

**Locked business decision (for 3.5.8.3):** Full reject MUST NOT automatically create a continuation. Full reject → accepted=0, rejection reason required, RETURN_PENDING, sender return task only.

---

## 3. 3.5.0 Scope (this Start Gate)

Documentation / architecture only:

- ADR-037 Accepted;
- ADR-013 / ADR-014 Superseded;
- ADR-035 qualified;
- Warehouse Spec v1.8;
- Production Spec v2.6 Warehouse boundary (CURRENT vs TARGET);
- UI Standard §39B Warehouse principles;
- Stage control sync.

Forbidden in 3.5.0:

- Java / FXML / CSS changes;
- Flyway migrations / schema changes;
- starting 3.5.1 implementation;
- automatic commit.

---

## 4. Preserved foundation

Must remain true through Stage 3.5:

- Warehouse owns stock state;
- StockPosition not mutated directly;
- WarehouseOperation execution primitive;
- WarehouseMovement immutable;
- AVAILABLE / IN_TRANSIT / BLOCKED;
- StorageCell, quantity/locking/negative-stock guards;
- Receipt / Move / Consumption / Adjustment / Inventory;
- existing Warehouse Query foundation.

---

## 5. Documents

1. Warehouse Specification v1.8
2. Production Specification v2.6 (§13–§14 boundary)
3. TMP Architecture Decisions (ADR-037; ADR-013/014/035)
4. TMP UI Standard §39B
5. Stage 6 Manifest (foundation reference)
6. Stage 7 Manifest (CURRENT Production integration reference)

---

## 6. Exit criteria (Stage 3.5 overall)

Not claimed by 3.5.0. Stage 3.5 closes only after responsibility, operational transfer workflow, routing/continuation/reject UX and primary Warehouse Tasks workspace are implemented and verified per later substages.
