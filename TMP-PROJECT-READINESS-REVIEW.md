# TMP Project Readiness Review — Before Stage 6 Warehouse

**Date:** 2026-08-06  
**Scope:** Full readiness audit (read-only). No production code changes. No architecture changes. No Git operations. Stage 6 not started.  
**Overall verdict:** **PASS** — Stage 5 closed; Stage 6 Start Gate documented (ADR-032); ready for Stage 6 implementation planning after user decision.

| Area | Result |
|---|---|
| Architecture | **PASS** |
| Documentation | **PASS** |
| Database | **PASS** (doc WARN) |
| Order Management | **PASS** |
| Import | **PASS** |
| UI | **PASS** |
| Tests | **PASS** (coverage WARN) |
| Stage 6 Readiness | **PASS** (post Start Gate 2026-08-06) |
| Build / Package / Launch | **PASS** |
| Blockers (Stage 5) | **NONE** |

---

# Summary

**PASS**

Stage 0–5 are marked **DONE**. Stage 5 Final Closure (2026-08-06) and `STAGE5-058` Manual GUI Smoke are recorded as **PASS**. Stage 6 Warehouse remains **NOT STARTED**.

Production stack is coherent: layered Maven DAG, ArchUnit Stages 0–5, Flyway V1–V14, uniform ACTIVE lifecycle (ADR-031), STXT import → Document Engine approve/activate, ACTIVE read-only UI, Public Query API exposing ACTIVE specification lines (`materialCode`/`materialName`, `lineQuantity`, `unitOfMeasure`, `lengthMm`).

This audit re-verified:

- `mvn -DskipTests validate` — PASS  
- Module tests (`tmp-order-management`, `tmp-ui-shell`, `tmp-architecture-tests`, `tmp-document-engine`, `tmp-security`, `tmp-bootstrap-app` + `-am`) — PASS (`TEST_EXIT=0`); Flyway clean migrate reaches **v14**  
- `mvn -DskipTests -pl tmp-bootstrap-app -am package -Ppackage` — PASS; `dist/jpackage/TMP/TMP.exe` present  
- Smoke launch of `TMP.exe` — process stayed alive (then stopped by auditor)

**Main outcome (Stage 6 Start Gate 2026-08-06):** ADR-032 — **no Material Master**. Warehouse consumes ACTIVE Specification fields via Production (`materialCode`, `materialName`, `color`, `unitOfMeasure`, `lengthMm`, `lineQuantity`). Material Mapping is a separate user-configured mechanism; does not block import.

---

# Architecture

**PASS**

## Modules

Reactor (`pom.xml`): `tmp-platform-core`, `tmp-document-engine`, `tmp-capability-engine`, `tmp-security`, `tmp-order-management`, `tmp-bootstrap-app`, `tmp-ui-shell`, `tmp-infra-db`, `tmp-architecture-tests`.

| Layer | Module | Role |
|---|---|---|
| Core | `tmp-platform-core` | Shared platform mechanisms; no business logic |
| Infra | `tmp-infra-db` | JDBC/Flyway/PostgreSQL baseline |
| Document Engine | `tmp-document-engine` | Document lifecycle / posting |
| Capability | `tmp-capability-engine` | Discovery / registration / navigation |
| Security | `tmp-security` | Authn/authz, roles, audit |
| Order Management | `tmp-order-management` | Commercial order lifecycle + import |
| UI | `tmp-ui-shell` | JavaFX shell (API-only imports) |
| Composition | `tmp-bootstrap-app` | Spring + JavaFX wiring |
| Quality | `tmp-architecture-tests` | ArchUnit Stages 0–5 |

## Dependencies

```text
platform-core, infra-db
        ↓
document-engine → capability-engine → security → order-management → ui-shell → bootstrap-app
```

- **Cycles:** none (DAG).  
- **Order → Document Engine:** via `com.tmp.document.api` only.  
- **UI → Order/Security:** via `com.tmp.order.api*` / `com.tmp.security.api` only.  
- **ArchUnit:** `Stage0`…`Stage5OrderManagementArchitectureTest` (+ negatives) enforce boundaries; Warehouse packages correctly absent.

## Soft WARN (non-blocking)

1. Maven depends on whole JARs (not published `*-api` artifacts); package discipline is ArchUnit-enforced.  
2. `tmp-bootstrap-app` gets `tmp-security` transitively via `tmp-ui-shell` (no direct POM edge).

---

# Documentation

**PASS**

| Document | Finding |
|---|---|
| `STATUS.md` | Stage 5 = DONE; Stage 6 = NOT STARTED; blockers = NONE; last task `STAGE5-058` |
| `WORK-QUEUE.md` | `STAGE5-050..058` = DONE; Stage 5 Final Closure = DONE; no READY/IN_PROGRESS Stage 6 tasks; only `BACKLOG-001` PLANNED (non-blocking) |
| `IMPLEMENTATION-LOG.md` | Final Closure 2026-08-06 records delivered OM + Import + STXT + uniform ACTIVE |
| `VERIFICATION-LOG.md` | Latest = STAGE5-058 Manual GUI Smoke + Final Closure = PASS |
| ADR (v1.8) | ADR-028/030/031 final present and aligned with code (uniform ACTIVE; no ImportMetadata) |
| Stage 5 Manifest | Status DONE; exit criteria marked complete |
| Stage 6 Manifest | Exists as planning outline only; **NOT STARTED** |

**Confirmed:** Stage 5 fully closed. Stage 6 NOT STARTED.

---

# Database

**PASS** (documentation WARN)

## Flyway inventory (V1–V14)

| Ver | Owner | Summary |
|---:|---|---|
| V1 | infra-db | `platform` schema |
| V2–V3 | document-engine | `documents` schema + type FK |
| V4–V5 | security | `security` schema + ownership/unique |
| V6–V8 | order-management | payloads, processing, aggregates |
| V9–V11 | order-management | intake contracts, quantity constraints, incomplete item DRAFT |
| V12 | order-management | creates `order_import_metadata` (historical) |
| V13 | order-management | revision/order `ACTIVE`; **DROP** `order_import_metadata` (ADR-031) |
| V14 | order-management | `ORDER_ACTIVATE` document type CHECKs |

Clean Testcontainers migrate in this audit applied **14 migrations → v14**.

## Schema ↔ domain

JDBC mappings (`OrderAggregateSql` / `OrderAggregateMappers`) align with `item_specification_lines`:

- `material_code`, `material_name`, `color`, `length_mm`, `line_quantity`, `unit_of_measure`

No JPA entities (intentional JDBC).

## WARN

1. **Database Specification** still lists example schemas (`production`, `warehouse`) and lags `order_management` naming; physical Order model is owned by Order-Management-Specification §19.  
2. `OrderQueryService` Javadoc still mentions `RevisionStatus#APPROVED`; runtime/DB use `ACTIVE` only.

V12 file retained + V13 drop is correct Flyway practice (do not delete V12).

---

# Order Management

**PASS**

## Order lifecycle

| Status | Present | Notes |
|---|---|---|
| DRAFT | yes | editable commercial header |
| APPROVED | yes | intermediate; required before ACTIVE |
| ACTIVE | yes | downstream baseline; header immutable |
| CANCELLED | yes | only from DRAFT |

Transitions: `DRAFT→APPROVED→ACTIVE`; direct `DRAFT→ACTIVE` forbidden; cancel of APPROVED/ACTIVE forbidden. Document processors: approve / activate / cancel.

## Position lifecycle

`DRAFT` | `ACTIVE` | `CANCELLED` (no item-level APPROVED — intentional). First revision approve → item ACTIVE; later changes via next draft revision.

## Revision lifecycle

`DRAFT` | `ACTIVE` (legacy APPROVED migrated in V13). ACTIVE revision immutable; prior revisions remain historically ACTIVE — Warehouse must follow `activeRevisionNumber`.

## Specification lifecycle

Owned by revision; frozen on activation; mutations after freeze rejected.

## ACTIVE immutability + Revision flow

- Order/Item commercial fields not editable when ACTIVE.  
- Changes only: `ORDER_ITEM_REVISION_CREATE` → draft update → `ORDER_ITEM_REVISION_APPROVE`.  
- Same path for manual and imported ACTIVE (ADR-031).

---

# Import

**PASS**

## STXT parser (`StxtFileAdapter` / `StxtBlockParser`)

| Requirement | Result |
|---|---|
| Multiple orders per file | **PASS** |
| Multiple items | **PASS** |
| Specification per item | **PASS** |
| `@` / `#` line filtering | **PASS** |
| `кв.м.` processing | **PASS** (size → name; `lengthMm=null`; unit → `шт.`) |
| Quantity = norm per 1 product | **PASS** (no multiply by product qty) |

## Import → ACTIVE

`DefaultOrderImportService.confirm` (single TX):

```text
ORDER_CREATE → ORDER_ITEM_CREATE → ORDER_ITEM_REVISION_UPDATE
→ ORDER_ITEM_REVISION_APPROVE → ORDER_APPROVE → ORDER_ACTIVATE
```

No aggregate bypass to ACTIVE. Duplicate guard = `orderNumber` uniqueness only.

## ADR-031 artefacts

- Persistent `ImportMetadata` / checksum table — **dropped (V13)**  
- `creationSource` / origin columns — **absent**  
- Ephemeral `sourceType` on import batch API — still present for adapter binding; **not persisted** (**WARN**, acceptable)

---

# UI

**PASS**

| Screen | Key classes | Result |
|---|---|---|
| Order list/editor | `OrderList*`, `OrderEditor*` | **PASS** |
| Position list/editor | `OrderItemList*`, `OrderItemEditor*` | **PASS** |
| Specification editor | `OrderItemSpecificationEditor*` | **PASS** |
| Import | `OrderImport*` | **PASS** |

## ACTIVE read-only

- Order: APPROVED and ACTIVE force non-editable fields; save/post/approve/cancel disabled.  
- Item: ACTIVE commercial locked; mutations via revision flow.  
- Spec: editable only for DRAFT revision / `!immutable`.  
- Controllers bind disable flags to ViewModel properties.  
- Manual GUI Smoke (2026-08-06): ACTIVE read-only = PASS; Revision N+1 = PASS.

**WARN:** Spec UI messages/tests still say “approved” while status enum is `ACTIVE` (cosmetic).

---

# Tests

**PASS** (gaps = WARN)

## Covered

- Domain lifecycles (order/item/revision/spec; ACTIVE immutability; incomplete DRAFT gates)  
- Document processors + idempotency + transaction/rollback IT  
- Import: STXT unit, preview IT, confirm → ACTIVE IT, end-to-end intake  
- Flyway V9–V14 / metadata absence  
- Query API contracts (no draft leakage; active revision/spec)  
- UI ViewModels: Order/Item/Spec/Import + read-only flags  
- Architecture ArchUnit Stages 0–5  
- Manual packaged GUI smoke Stage 5 core + intake + STAGE5-058

## Missing / thin (WARN, not Stage 5 FAIL)

1. No automated UI E2E: import → open ACTIVE editors (manual smoke only).  
2. `OrderEditorControllerFxTest` mostly FXML load — APPROVED/ACTIVE disable asserted at ViewModel, not Fx.  
3. Zero Warehouse / cross-module Order↔Warehouse tests (expected; Stage 6).  
4. Material Mapping tests — not started (expected; separate track).

---

# Stage 6 Readiness

**PASS** (Start Gate documented 2026-08-06, ADR-032)

## Decision (final)

- **No Material Master** in current TMP architecture.
- Materials defined through **ACTIVE Item Specification** (Order Management).
- **Material Mapping** for SuperOkna name normalization (user-configured; import not blocked).
- Warehouse stores **warehouse state only**; identity = **MaterialReference** from spec fields.

## Ready (Order side)

ACTIVE Specification exposes for Warehouse (via Production):

| Field | Available |
|---|---|
| `materialCode` | yes |
| `materialName` | yes |
| `color` | yes |
| `unitOfMeasure` | yes |
| `lengthMm` | yes (nullable) |
| `lineQuantity` | yes (norm per 1 product) |

Warehouse **does not require** a separate material catalog.

## Interaction schema

```text
Order Management
        |
        v
Specification (ACTIVE)
        |
        v
Production  (demand calculation; Material Mapping)
        |
        v
Warehouse   (stock, availability, reservation)
```

## Remaining before Stage 6 code

| Item | Status |
|---|---|
| ADR-032 accepted | DONE |
| Specs aligned | DONE |
| Stage 6 Manifest | DONE |
| `tmp-warehouse` module | NOT STARTED |
| Material Mapping implementation | NOT STARTED (separate from Warehouse) |
| User explicit go for Stage 6 code | pending |

## Caveats (non-blocking)

1. `lineQuantity` is per-product norm — Production must multiply by `orderedQuantity` for totals.
2. `lengthMm` may be null after `кв.м.` STXT transform.
3. Two-call Query API (`getActiveOrderItemRevision` + `getItemSpecification`).

**Verdict:** Stage 5 data sufficient; Start Gate closed; Stage 6 implementation awaits user decision.

---

# Blockers

## Active blockers (Stage 5 / current control)

**NONE**

- `STATUS.md`: Active blockers = NONE  
- No READY/IN_PROGRESS/BLOCKED Stage 5 or Stage 6 tasks  
- Stage 5 exit criteria met

## Pre–Stage 6 implementation notes (resolved at Start Gate 2026-08-06)

1. ~~Material master conflict~~ — **resolved:** ADR-032, no Material Master.
2. **Quantity semantics** for reservation (norm × copies) — document in Warehouse design during Stage 6.
3. **Nullable length** after square-meter STXT transform — Production/Warehouse must handle.

Stage 6 implementation tasks (`STAGE6-001+`) — **NOT STARTED**; await user decision.

---

# Recommendations

1. **Do not start Stage 6 code** until user explicit implementation decision (Start Gate documentation complete).  
2. At implementation kickoff: decompose Stage 6 Manifest into sized WORK-QUEUE tasks (`STAGE6-001+`).  
3. Document Warehouse consumption formula: `demand = lineQuantity × orderedQuantity` (unless Cutting Plan overrides).  
4. Material Mapping — separate implementation track (not Warehouse).  
5. Optionally: fix `OrderQueryService` javadoc `APPROVED` → `ACTIVE`.

---

# Audit constraints respected

- No production code changes  
- No refactoring / architecture changes  
- No Git operations  
- Stage 6 not started  
- Report only: `TMP-PROJECT-READINESS-REVIEW.md`
