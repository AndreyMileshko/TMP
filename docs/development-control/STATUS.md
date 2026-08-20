# TMP Development Status

**Mode:** Autonomous Cursor Agent
**Project status:** Stage 6 complete; Stage 7 Start Gate PASSED; Stage 7 implementation IN PROGRESS
**Current Stage:** Stage 7 - IN PROGRESS / 32% (Start Gate PASSED)
**Current Task:** none
**Last completed task:** STAGE7-005A (Whole-Order Production Launch Correction & Reactor Recovery)
**Active blockers:** NONE
**Stage 6 Warehouse:** DONE
**Stage 7 Production:** IN PROGRESS / 32%
**Stage 7 Start Gate:** PASSED
**Full verify baseline:** GREEN
**First READY implementation task:** STAGE7-006


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
STAGE6-004 = DONE
STAGE6-005 = DONE
STAGE6-006 = DONE
STAGE6-007 = DONE
STAGE6-008 = DONE
STAGE6-009 = DONE
STAGE6-010 = DONE
STAGE6-011 = DONE
STAGE6-012 = DONE (Information Reservation Link)
STAGE6-013 = DONE (Warehouse Public API)
STAGE6-014 = DONE (Warehouse Security Integration)
STAGE6-015 = DONE (Warehouse UI)
STAGE6-016 = DONE (Warehouse UI + Security Roles UI final UX fix)
STAGE6-017 = DONE (Warehouse structure permissions security fix)
STAGE6-019 = DONE (Warehouse Material Reference Management)
STAGE6-019-FIX = DONE (Warehouse Unit Of Measure Reference)
STAGE6-020 = DONE (Warehouse Stock View Zero Quantity Filtering)
STAGE6-FINAL = DONE (Warehouse Closure Audit)
Stage 6 = DONE
Stage 6 Warehouse = DONE
STAGE7-000 = DONE (Start Gate; BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY RESOLVED)
STAGE7-000A = DONE (Restore Green Reactor Baseline; Warehouse test debt)
STAGE7-000B = DONE (Warehouse Production Integration Readiness)
STAGE7-000C = DONE (Warehouse Transfer Integrity)
STAGE7-001 = DONE (Production module foundation)
STAGE7-002 = DONE (Production identifiers and item-owned state model)
STAGE7-003 = DONE (Production persistence schema)
STAGE7-004 = DONE (Production Launch document; originally item-level)
STAGE7-004A = DONE (OM stable SpecificationId public contract)
STAGE7-004B = DONE (SpecificationId Stability Fix)
STAGE7-005 = DONE (Production Foundation — Specification Reference Freeze)
STAGE7-005A = DONE (Whole-Order Production Launch Correction & Reactor Recovery)
Active blockers = NONE
Stage 7 Production = IN PROGRESS / 32%
Stage 7 Start Gate = PASSED
Full reactor baseline = GREEN
First READY implementation task = STAGE7-006
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
| 6 | Warehouse | DONE | 100% |
| 7 | Production | IN PROGRESS | 32% |
| 8 | Cutting Optimization | NOT STARTED | 0% |
| 9 | Analytics | NOT STARTED | 0% |

---

## Stage 5 — Final Closure (2026-08-06)

Stage 5 Order Management and Order Intake Extension complete. All STAGE5 tasks DONE.

## Stage 6 — Final Closure (2026-08-09)

Stage 6 Warehouse complete. All STAGE6 tasks DONE.

## Stage 7 — Start Gate (2026-08-17)

- Start Gate PASSED (`STAGE7-000`).
- ADR-036 Accepted (cross-capability atomicity).
- `BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY` RESOLVED.
- Pre-implementation baseline restored (`STAGE7-000A`).
- Warehouse integration readiness closed (`STAGE7-000B`).
- Warehouse Transfer integrity closed (`STAGE7-000C`): exactly-once receive, positive quantity, logical DRAFT/SENT/RECEIVED.
- `STAGE7-001` completed: `tmp-production` module foundation, package boundaries, reactor wiring, Stage 7 architecture guard.
- `STAGE7-002` completed: Production domain identifiers, item-owned state model, quantity invariants, unit tests; Stage7ProductionArchitectureTest Cutting typo fixed; STAGE7-004A queued for OM SpecificationId alignment.
- `STAGE7-003` completed: Production persistence schema (`production.production_item_states`), JDBC repository, entity↔domain mapping, Flyway V23, persistence/integration tests; no Production Order / Revision tables.
- `STAGE7-004` completed: Production Launch as Business Document — ProductionLaunchProcessor, ProductionLaunchPayload, ProductionLaunched event, AlreadyLaunchedForProductionException, ProductionLaunchService; Document Engine integration; duplicate Launch guard; after-commit domain event; architecture rule (only Processor uses Repository); unit tests PASS.
- `STAGE7-004A` completed: OM stable SpecificationId public contract — Flyway V24 adds `specification_id` UUID to `item_specifications`; `SpecificationId`, `ProductionSpecificationDto` DTOs; `getCurrentItemSpecification()` + `getSpecificationById()` on `OrderQueryService`; JDBC adapter; deterministic UUID derivation; legacy revision API preserved; no RevisionNumber in Production-facing DTO; 366 OM tests PASS.
- `STAGE7-004B` completed: SpecificationId Stability Fix — V25 migration corrects V24's `md5()::uuid` to proper UUID v3 (version/variant bits matching Java `UUID.nameUUIDFromBytes()`); `SpecificationIdMigrationConsistencyIT` proves SQL and Java produce identical UUIDs; Flyway version assertions updated; full `mvn verify` PASS.
- `STAGE7-005` completed: Production Foundation — `ProductionFoundation` domain value object frozen at Launch; `ProductionLaunchService` resolves current spec once via OM Public Query; `ProductionFoundationQueryService` reads frozen spec via `getSpecificationById` only; no specification content snapshot duplication (reference-only per Production Spec §127); architecture rules enforce post-launch boundary.
- `STAGE7-005A` completed: corrective whole-order Launch. STAGE7-004 originally launched a single Order Item; audit found this violated Production Spec (one user operation accepts the entire ACTIVE Order). Launch now takes `SourceOrderId` only; OM Public Query supplies all ACTIVE items, quantities and SpecificationId; one multi-line Launch document is atomic; `OrderAcceptedIntoProduction` is the after-commit business event. `tmp-ui-shell` OrderQueryService test doubles restored to compile against the extended public contract. Full `mvn verify` BUILD SUCCESS.
- Production implementation IN PROGRESS (32% by task-count: 8/25 implementation tasks done, including 004A/004B/005A).
- Next task: **STAGE7-006** (Computed Order Production View) — READY.
