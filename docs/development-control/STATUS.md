# TMP Development Status

**Mode:** Autonomous Cursor Agent
**Project status:** Stage 6 complete; Stage 7 Start Gate PASSED; Stage 7 implementation IN PROGRESS
**Current Stage:** Stage 7 - IN PROGRESS / 85% (Start Gate PASSED)
**Current Task:** none
**Last completed task:** STAGE7-016A (Production Public Query API)
**Active blockers:** NONE
**Stage 6 Warehouse:** DONE
**Stage 7 Production:** IN PROGRESS / 85%
**Stage 7 Start Gate:** PASSED
**Full verify baseline:** GREEN
**First READY implementation task:** STAGE7-017


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
STAGE7-006 = DONE (Computed Order Production View)
STAGE7-007 = DONE (Material availability via Warehouse Query)
STAGE7-008 = DONE (Cutting Plan references 0..N)
STAGE7-008A = PLANNED (Cutting Plan Link Association Contract; post-launch)
STAGE7-009 = DONE (Editable Material Transfer Template)
STAGE7-010 = DONE (Warehouse Transfer command integration; template atomic save precondition)
STAGE7-011 = DONE (Receipt confirmation initiation)
STAGE7-012 = DONE (Production Release document and plan/fact)
STAGE7-012A = DONE (Partial Release Material Planning Contract)
STAGE7-013 = DONE (Atomic Release + Consumption orchestration)
STAGE7-013A = DONE (Release Preparation & Concurrency Correctness)
STAGE7-014 = DONE
STAGE7-015 = DONE
STAGE7-015A = DONE
STAGE7-016 = DONE
STAGE7-016A = DONE
Active blockers = NONE
Stage 7 Production = IN PROGRESS / 85%
Stage 7 Start Gate = PASSED
Full reactor baseline = GREEN
First READY implementation task = STAGE7-017
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
| 7 | Production | IN PROGRESS | 85% |
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
- `STAGE7-006` completed: computed Order Production View from item-owned states (`OrderProductionViewCalculator` + `ProductionOrderViewService`); no stored order-level status; empty states → `NOT_ACCEPTED` (not AVAILABLE); Launch readiness distinguishes no ACTIVE items vs missing Specification with correct `OrderItemId`; `OrderAcceptedIntoProduction` count/list/duplicates invariant; STAGE7-007 dependency → STAGE7-005A+006; STAGE7-014 → STAGE7-005A; STAGE7-018 ACID Launch rollback requires real JDBC/PostgreSQL. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-007` completed: read-only Material Availability Check for `IN_PRODUCTION` orders. Frozen Specification path only; `lineQuantity` not multiplied by product quantity; material identity resolved via `WarehouseQueryApi.listMaterialReferences()` (article+color+UoM; `lengthMm` never mapped to Warehouse `size`); unresolved/ambiguous distinct from stock=0; `ProductionWarehouseScope` (injected main/production warehouse ids, no hardcoded roles); deficit from scoped AVAILABLE stock only; no Warehouse writes, no Production status mutation, no document, no Cutting. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-008` completed: Production-owned 0..N Cutting Plan links (`MaterialReferenceId` → `CuttingPlanId`) on `ProductionItemState`; Flyway V26 child table; Launch payload ready with empty default; no Stage 8 dependency / Revision / cross-capability FK; Material Check stays on Specification when mere link present; STAGE7-008A PLANNED for post-launch association; STAGE7-009 READY. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-009` completed: Production-owned editable Material Transfer Template. `prepareMaterialTransferTemplate` uses STAGE7-007 Material Check snapshot; recommended transfer = min(max(required−production,0), main) with uncovered deficit; unresolved/ambiguous blocked; recommended vs requested quantities; exclude via `included=false`; optional CuttingPlanId informational only (`planningSource=SPECIFICATION`); Flyway V27 persistence + optimistic lock; no WarehouseCommandApi / stock mutation / Document Engine document. STAGE7-010 READY. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-010` completed: confirm Material Transfer Template → Warehouse-owned Transfer DRAFTs via `WarehouseCommandApi.createTransferDraft` only. Precondition: atomic template save (TransactionTemplate REQUIRED) + real PostgreSQL child-insert rollback test. Explicit cell allocations (`templateLineId` + source/destination cells + quantity); WarehouseQueryApi cell membership/active validation; no hidden picking; 1 line → 1..N drafts; Production `ProductionMaterialTransfer` grouping + operation refs (V28); DRAFT/CONFIRMED template lifecycle; idempotent confirm; edit blocked after confirm; stock unchanged; send→receive reference resolvable from stored draftOperationId (`getTransferStatus` / same id for `receiveTransfer`). STAGE7-011 READY. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-011` completed: «Подтвердить получение» for Production logical transfer. `ConfirmMaterialReceiptService` loads Warehouse refs from `ProductionMaterialTransferRepository` only (no UI Warehouse operation IDs). Full status snapshot via `WarehouseQueryApi.getTransferStatus` before any mutation; DRAFT/invalid reject; SENT → `WarehouseCommandApi.receiveTransfer`; RECEIVED skipped; all-RECEIVED → `ALREADY_RECEIVED` idempotent. Outer REQUIRED TX; real PostgreSQL partial-failure rollback; stored ref consistency; original send/draft id remains correlation. No Production receipt SoT, Document, history/audit, or stock writes. STAGE7-012 READY. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-012` completed: Production Release document (`production.release`) with durable plan/fact persistence (Flyway V29), `ProductionReleaseProcessor`, internal `ProductionReleaseDocumentService` gateway for STAGE7-013 (no standalone user `releaseProducts`), item-owned `ProductionItemState.release`, full pre-validation, POSTED immutability, UNPOST unsupported. No Warehouse Consumption. Partial-release material plan formula absent from Accepted docs → `BLK-STAGE7-PARTIAL-RELEASE-PLAN` OPEN; STAGE7-013 BLOCKED. Full `mvn test` / `mvn verify` BUILD SUCCESS.
- `STAGE7-012A` completed: Accepted Production Specification **v2.3 §15.1.1** — cumulative proportional allocation for partial/repeated Release plan (`Q` = frozen `lineQuantity` on whole item, `N` = orderedQuantity, no `Q*N` double multiply; scale 6 HALF_UP; final Release closes exact `Q`). `BLK-STAGE7-PARTIAL-RELEASE-PLAN` RESOLVED. STAGE7-013 READY. Documentation-only; no business code. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-013` completed: `ReleaseProductsService` atomic Release + Warehouse Consumption orchestration (outer REQUIRED TX). System-computed partial plan per §15.1.1; confirmed actual + explicit production-warehouse cell allocations; `WarehouseCommandApi.consume`; internal document gateway; PostgreSQL rollback proofs. `ProductionReleased` deferred STAGE7-015. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-013A` completed: corrective Release preview/confirm split (`PrepareReleaseCommand` without actual/cells); plan always recomputed inside outer TX on `SELECT … FOR UPDATE` whole-order lock via `ProductionOrderStateLockService`; `OrderProductionViewCalculator` on locked snapshot; stronger ArchUnit guard for `ProductionReleaseDocumentService`; PostgreSQL concurrent Release tests; existing rollback proofs preserved. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-014` completed: whole-order Production Cancellation (`production.cancellation`) via `CancelOrderProductionService`; durable payload Flyway V30; `ProductionCancellationProcessor`; internal gateway; `ProductionCancellationQuery.hasPostedCancellation`; `ProductionOrderViewService` uses posted cancellation evidence for RELEASED+CANCELLED mix; reuses `ProductionOrderStateLockService`; no Warehouse/Cutting mutation; PostgreSQL atomicity + Release↔Cancel ordering proofs. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-015` completed: Production domain events after-commit via public `TransactionalEventPublisher` — `OrderAcceptedIntoProduction` (existing), `ProductionReleased`, `OrderProductionCancelled`; Release/Cancellation processors schedule one event per document POST; STAGE7-014 verification gap closed with true overlapping Release↔Cancel row-lock serialization proofs; rollback delivers 0 events. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-015A` completed: Production-owned immutable business history (`production.production_history`, Flyway V31). Append-only JDBC store with DB triggers rejecting UPDATE/DELETE. Seven Spec §22 types recorded in the same business transaction as Launch/Check/Transfer/Receipt/Release/Cancellation. Internal `listByOrder` query; no Public Query API, Security Audit, or Analytics. STAGE7-015 domain events unchanged. Full `mvn verify` BUILD SUCCESS.
- `STAGE7-016A` completed: Production Public Query API (`com.tmp.production.api.ProductionQueryApi`) — four read-only operations; `production.order.view` before any downstream read; current side-effect-free material availability via `CurrentMaterialAvailabilityQueryService`; ProductionCapability contributes exactly one public service; Platform ServiceRegistry lookup proof. STAGE7-017 READY. Full `mvn verify` BUILD SUCCESS.
- Production implementation IN PROGRESS (85% by task-count: 23/27 Stage 7 tasks done through STAGE7-016A, including 004A/004B/005A).
- Next task: **STAGE7-017** (Production workbench UI) — READY.
