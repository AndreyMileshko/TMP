# TMP Implementation Log

Новые записи добавляются в начало. История не переписывается.

---

## STAGE7-015 — Production domain events

**Date:** 2026-08-24
**Stage:** 7
**Module:** `tmp-production`
**Status:** DONE

### Context

Publish completed Production business Domain Events after successful transaction commit only (Production Spec §19, Document Engine after-commit, ADR-021). Close STAGE7-014 Release↔Cancel overlapping row-lock verification gap first.

### Delivered

- STAGE7-014 verification correction: `ReleaseCancelOverlapPostgresIT` proves true overlapping transactions serialize via `SELECT … FOR UPDATE` (cancel-wins and partial-release-wins).
- Existing `OrderAcceptedIntoProduction` retained as canonical Launch event.
- New `ProductionReleased` and `OrderProductionCancelled` immutable DomainEvent records in `com.tmp.production.application.event`.
- `ProductionReleaseProcessor` / `ProductionCancellationProcessor` schedule events via public `TransactionalEventPublisher.publishAfterCommit` after state mutations; one event per document POST.
- Event timestamps from business documents; Release/Cancellation include source document IDs.
- Real PostgreSQL after-commit/rollback proofs in `ProductionDomainEventsPostgresIT`.
- ArchUnit rules for event contracts and public publisher usage.
- No History/Audit store; no Security; STAGE7-018 not marked DONE.
- STAGE7-015A = READY.

### Verification

- `mvn -pl :tmp-production -am test` PASS
- `mvn -pl :tmp-document-engine -am test` PASS
- `mvn -pl :tmp-architecture-tests -am test` PASS
- `mvn test` PASS
- `mvn verify` PASS
- `git diff --check` clean

---

## STAGE7-014 — Production Cancellation

**Date:** 2026-08-23
**Stage:** 7
**Module:** `tmp-production`
**Status:** DONE

### Context

Whole-order Production Cancellation per Production Spec §16: cancel unfinished item-owned production via durable Production-owned document; preserve RELEASED items and released quantities; no automatic material return.

### Delivered

- Document type `production.cancellation` + `ProductionCancellationProcessor`.
- Durable payload Flyway V30 (`production_cancellations`, `production_cancellation_item_lines`, UNIQUE `source_order_id`).
- `CancelOrderProductionService` + `CancelOrderProductionCommand` (whole order only; optional reason).
- Internal `ProductionCancellationDocumentService` gateway (ArchUnit bypass guard).
- `ProductionCancellationQuery.hasPostedCancellation` + JDBC adapter.
- `ProductionOrderViewService` auto-supplies `Context.cancelled()` from posted cancellation evidence.
- Reuses `ProductionOrderStateLockService` (same lock domain as Release).
- Domain: `ProductionItemState.cancel()` for unfinished items; `PRESERVED_RELEASED` lines for RELEASED items.
- Tests: unit (service/processor/view), PostgreSQL IT (happy path, rollback, cutting links preserved, Release↔Cancel ordering), ArchUnit rules.
- STAGE7-015 = READY; STAGE7-018 NOT marked DONE.

### Verification

- `mvn -pl :tmp-production -am test` PASS
- `mvn -pl :tmp-document-engine -am test` PASS
- `mvn -pl :tmp-architecture-tests -am test` PASS
- `mvn test` PASS
- `mvn verify` PASS
- `git diff --check` clean

---

## STAGE7-013A — Release Preparation & Concurrency Correctness

**Date:** 2026-08-23
**Stage:** 7
**Module:** `tmp-production`
**Status:** DONE

### Context

Post-review corrective task for STAGE7-013: preview incorrectly required actual/cell allocations; plan was computed before outer TX (stale `releasedBefore`); ArchUnit internal-gateway rule did not cover `tmp-ui-shell`/external callers.

### Delivered

- `PrepareReleaseCommand` — preview-only (orderId + item releases); no `MaterialActualUsage` / `CellAllocation`.
- `prepareRelease` — informational plan + `defaultActual = planned`; unlocked read; zero mutations.
- `releaseProducts` — plan recomputed inside outer REQUIRED TX after `ProductionOrderStateLockService.lockAllItemStates`.
- `ProductionOrderViewService.lockAllItemStates` + `JdbcProductionItemStateRepository.findBySourceOrderIdForUpdate` (`ORDER BY source_order_item_id, specification_id FOR UPDATE`).
- `ProductionOrderStateLockService` — reusable whole-order locking boundary for STAGE7-014 Cancellation.
- `OrderProductionViewCalculator` used on locked snapshot (not stale pre-lock view).
- ArchUnit: global ban on `ProductionReleaseDocumentService` except `ReleaseProductsService` + technical test; `ProductionOrderStateLockService` ViewService-only rule.
- `ProductionReleaseProcessorTest.materialLineReferencingForeignItemRejected` regression.
- Tests: preview without actual; confirm recalculates; PostgreSQL concurrent serialize / cumulative plan / over-release; rollback proofs preserved.
- Fixed `validateConsumptionResult`: `OperationResult.quantity` for CONSUMPTION is post-operation stock level (Warehouse public contract), not consumed delta.

### Queue

- STAGE7-013 = DONE (historical; corrective via 013A).
- STAGE7-013A = DONE.
- STAGE7-014 = READY.
- Active blockers = NONE.

---

## STAGE7-013 — Atomic Release + Consumption orchestration

**Date:** 2026-08-23
**Stage:** 7
**Module:** `tmp-production`
**Status:** DONE

### Delivered

- `ReleaseProductsService` — user-facing «Выпустить изделия» orchestration (outer REQUIRED TX).
- `prepareRelease` preview + `releaseProducts` confirm path.
- `PartialReleaseMaterialPlanCalculator` — cumulative proportional allocation per Spec v2.3 §15.1.1 (scale 6 HALF_UP, final closure exact `Q`).
- `ReleaseMaterialPlanBuilder` — per-Spec-line plan then aggregate by item + MaterialReferenceId.
- `MaterialReferenceResolver` extracted; `CheckMaterialAvailabilityService` reuses it (STAGE7-007 semantics preserved).
- `ProductionReleaseDocumentService` moved to `application.internal` — not a public bypass path.
- Warehouse Consumption via `WarehouseCommandApi.consume` only; explicit production-warehouse cell allocations; no hidden picking.
- ArchUnit guards: internal gateway + Warehouse public API boundary.
- Tests: `ReleaseProductsServiceTest`, `ReleaseProductsPostgresIT` (consumption failure + release failure rollback proofs).

### Deferred

- `ProductionReleased` domain event → STAGE7-015.
- Production security permissions → STAGE7-016.
- STAGE7-018 final public-boundary suite unchanged.

### Queue

- STAGE7-013 = DONE.
- STAGE7-014 = READY.
- Active blockers = NONE.

---

## STAGE7-012A — Partial Release Material Planning Contract

**Date:** 2026-08-23
**Stage:** 7
**Module:** documentation / control only
**Status:** DONE

### Context

- STAGE7-012 implemented Production Release document foundation without computing normative partial-release plan.
- Accepted docs v2.2 did not define scaling formula → `BLK-STAGE7-PARTIAL-RELEASE-PLAN` OPEN (agent did not invent rule).

### Accepted rule (Production Specification v2.3 §15.1.1)

- `Q` = frozen `Specification.lineQuantity` (total normative quantity for whole Order Item material line).
- `N` = `ProductionItemState.orderedQuantity`.
- Forbidden: `Q * N` repeated multiplication.
- Cumulative proportional allocation: `C_before = normalized(Q * R_before / N)`, `C_after = normalized(Q * R_after / N)`, `Plan_current = C_after - C_before`.
- Final Release (`R_after == N`): `C_after = Q` exactly; SUM(plans) == `Q`.
- Precision: scale 6, `RoundingMode.HALF_UP`, `BigDecimal` only.
- Applies to `MaterialPlanningSource.SPECIFICATION` in Stage 7; opaque `CuttingPlanId` does not drive quantity.
- Plan vs fact separation unchanged; Warehouse Consumption uses actualQuantity.

### Outcome

- Production Specification v2.2 → **v2.3 Accepted** with normative examples (10/17 and 3/1 rounding closure).
- No new ADR (business rule owned by Production Spec; ADR-035/036 unchanged).
- `BLK-STAGE7-PARTIAL-RELEASE-PLAN` **RESOLVED** (2026-08-23).
- STAGE7-013 → **READY**.
- No Production/Warehouse/OM Java, migrations, or tests changed.

### Verification

- `git diff --check` PASS
- `mvn verify` PASS
- Git commit / push NOT EXECUTED

---

## STAGE7-012 — Production Release document and plan/fact

**Date:** 2026-08-21
**Stage:** 7
**Module:** `tmp-production`
**Status:** DONE

### Document

- TypeId: `production.release`
- `ProductionReleaseProcessor` — Document Engine lifecycle; loads durable payload; full pre-validation of all item lines before any `ProductionItemState` save; calls domain `release(...)`; marks payload posted.
- UNPOST → `UnsupportedOperationException`.
- No `ProductionReleased` domain event (deferred to STAGE7-015).

### Domain / plan-fact

- `ProductionRelease` aggregate keyed by Document Engine `documentId` (no extra ProductionReleaseId / ProductionOrder).
- Item lines: `sourceOrderItemId` + frozen `specificationId` + `releaseQuantity` (>0); duplicate item rejected.
- Material lines: exact `MaterialReferenceId`, `plannedQuantity`/`actualQuantity` (>=0 independently; fact may be < / = / > plan, including zero fact), `MaterialPlanningSource`, optional opaque `CuttingPlanId`, optional item traceability, optional comment.
- Deviation as computed `actualMinusPlanned` (not stored column).

### Persistence

- Flyway `V29__production_release.sql`: `production.production_releases`, `_item_lines`, `_material_lines`.
- FK only within `production` schema; opaque UUID refs (no OM/Warehouse/Cutting FK); no Revision columns.
- `JdbcProductionReleaseRepository` — durable draft save / markPosted / findByDocumentId; POSTED immutable.

### Application boundary

- Internal `ProductionReleaseDocumentService` + `ProductionReleaseDocumentCommand`: createDraft / updateDraft / post / findReleaseByDocumentId / actualMaterialUsages.
- `@Transactional` REQUIRED (joins ambient TX for STAGE7-013); no REQUIRES_NEW.
- No standalone user-facing `releaseProducts(...)` bypassing Warehouse Consumption.

### Blocker

- Searched Accepted docs: no partial-release material plan scaling formula.
- Created `BLK-STAGE7-PARTIAL-RELEASE-PLAN`; STAGE7-013 = BLOCKED.

### Tests / architecture

- Domain, processor, document service, JDBC durability, PostgreSQL IT, Flyway V29, Stage7ProductionArchitectureTest Release guards (no Warehouse/OM/Cutting; no ReleaseProducts*).

### Verification

- `mvn -pl :tmp-production -am test` PASS
- `mvn -pl :tmp-document-engine -am test` PASS
- `mvn -pl :tmp-architecture-tests -am test` PASS
- `mvn test` PASS
- `mvn verify` PASS
- `git diff --check` clean (CRLF warnings only)
- Git commit / push NOT EXECUTED

---

## STAGE7-011 — Receipt confirmation initiation

**Date:** 2026-08-21
**Stage:** 7
**Module:** `tmp-production`
**Status:** DONE

### Command

- `ConfirmMaterialReceiptCommand(ProductionMaterialTransferId logicalTransferId)` only.
- UI cannot pass Warehouse draft/operation IDs, material, quantity, or cells.
- Refs loaded exclusively from `ProductionMaterialTransferRepository`.

### Status validation (before mutation)

- Query all stored refs via `WarehouseQueryApi.getTransferStatus`.
- DRAFT → reject (not ready); invalid status → reject; SENT → receive; RECEIVED → skip.
- Pre-validation of full snapshot before any `receiveTransfer`.

### Warehouse

- Mutating: `WarehouseCommandApi.receiveTransfer(sendOperationId)` only for SENT refs.
- No Stock Position writes; no Warehouse internals in Production main.
- Original `warehouseDraftOperationId` remains stable correlation; `receiveOperationId` from Warehouse query/result.

### Result

- `MaterialReceiptConfirmationResult` with `RECEIVED` / `ALREADY_RECEIVED` and per-ref outcomes.
- `confirmedAt` = injected Clock use-case completion / idempotent check time (not Warehouse receive timestamp).
- No persisted Production receipt lifecycle, Document Engine document, or history (STAGE7-015A later).

### Transaction

- Outer `TransactionTemplate` REQUIRED.
- Partial failure rolls back new receives in the current command; pre-existing RECEIVED survives.

### Tests / architecture

- Unit: DRAFT block, SENT, idempotent, mixed, multi-ref, prevalidation, consistency mismatch, not-found.
- PostgreSQL: stock effects, rollback, pre-existing RECEIVED + failure, consistency.
- Stage7ProductionArchitectureTest: receipt public-API-only + no Warehouse internals / StockPosition.

### STAGE7-018 note

- Low-level Postgres ITs may use Warehouse internals as fixtures; final STAGE7-018 boundary suite must use public contracts only.

---

## STAGE7-010 — Warehouse Transfer command integration

**Date:** 2026-08-21
**Stage:** 7
**Module:** `tmp-production` (Flyway V28; precondition fix on template JDBC save)
**Status:** DONE

### STAGE7-009 correction (precondition)

- `JdbcMaterialTransferTemplateRepository.save` wraps header + child delete/insert in `TransactionTemplate` (REQUIRED / ADR-036).
- Real PostgreSQL/Testcontainers rollback: controlled trigger fails child insert → header version/updated_at/lines/source-item/cutting refs remain V0.

### Confirmation

- `ConfirmMaterialTransferCommand(templateId, expectedVersion, allocations)`.
- `ConfirmMaterialTransferService.confirmMaterialTransferCreate` loads saved template; material/quantity/warehouses from template only.
- Allocations: `templateLineId` + `sourceStorageCellId` + `destinationStorageCellId` + `quantity`; sum per included line == persisted `requestedQuantity`.
- Cells validated via `WarehouseQueryApi.listStorageCells` (exists, active, warehouse membership). No automatic picking policy.
- Excluded lines: no Warehouse drafts; allocation for excluded → reject.
- Stale `expectedVersion` → reject before Warehouse commands.

### Warehouse

- Calls only `WarehouseCommandApi.createTransferDraft`.
- Does **not** call `sendTransfer` / `receiveTransfer`.
- Draft result validated (`status=DRAFT`, material/qty/cells match).
- Stock unchanged at confirm (AVAILABLE / IN_TRANSIT unchanged).

### Production grouping

- `ProductionMaterialTransfer` + `WarehouseTransferOperationRef` (opaque `warehouseDraftOperationId`).
- Unique logical transfer per `templateId` (DB unique + idempotent re-confirm returns existing).
- Template lifecycle: `DRAFT` → `CONFIRMED` (+ `confirmedAt`); edit/exclude/restore blocked after confirm.

### STAGE7-011 readiness

- Stored draft operation id is the send reference: after Warehouse send, `getTransferStatus(draftId)=SENT` and `receiveTransfer(draftId)` works (same id). Proven in `ConfirmMaterialTransferPostgresIT`.
- Lookup by `logicalTransferId` / `templateId` / `sourceOrderId` via `ProductionMaterialTransferRepository`.

### Persistence

- V28: template `status`/`confirmed_at`; `production.material_transfers`; `production.material_transfer_operation_refs`.
- Production-only FKs; no warehouse/OM/cutting FK.

### Transaction

- Outer confirmation TX (REQUIRED); Warehouse draft writes join ambient TX; partial draft failure rolls back logical transfer + Warehouse drafts (Postgres IT). No REQUIRES_NEW.

### Architecture

- Confirm service depends on Warehouse public API only; Stage7ProductionArchitectureTest rules added.

### Queue

- STAGE7-011 → READY.
- STAGE7-008A remains PLANNED.
- STAGE7-011 receive not implemented in this task.

---

## STAGE7-009 — Editable Material Transfer Template

**Date:** 2026-08-21
**Stage:** 7
**Module:** `tmp-production` (Flyway V27 in production resources)
**Status:** DONE

### Template model

- `MaterialTransferTemplateId` / `MaterialTransferTemplateLineId` — Production-owned UUID identities (not Warehouse Transfer / Document / Order IDs).
- Immutable controlled aggregate `MaterialTransferTemplate` + `MaterialTransferTemplateLine`.
- Warehouses from `ProductionWarehouseScope`: source = main, destination = production (not user-editable).
- No Document Engine document; no status lifecycle beyond persisted editable draft (confirmation = STAGE7-010).

### Preparation

- `MaterialTransferTemplateService.prepareMaterialTransferTemplate(orderId)` calls fresh `CheckMaterialAvailabilityService.check` (same requirement algorithm as STAGE7-007).
- Allowed only when Order Production View = `IN_PRODUCTION` (`MaterialTransferNotAllowedException` otherwise; no Warehouse queries after lifecycle rejection via Material Check guard).
- `MATERIAL_UNRESOLVED` / `MATERIAL_AMBIGUOUS` → `MaterialTransferTemplateNotReadyException` (no null `materialReferenceId` transfer lines).
- Active lines only when recommended transfer quantity > 0 (already-on-production materials omitted).

### Recommendation

- `MaterialTransferRecommendationCalculator`:
  - needToProduction = max(required − productionAvailable, 0)
  - recommended = min(needToProduction, mainAvailable)
  - uncoveredDeficit = max(needToProduction − mainAvailable, 0)
- Other warehouses ignored (scoped Material Check only).

### Editability

- `changeRequestedQuantity` / `excludeLine` / `restoreLine`.
- Exclusion = `included=false` (not quantity=0/-1); included lines require `requestedQuantity > 0`.
- `recommendedQuantity` immutable after create; initial `requestedQuantity = recommendedQuantity`.
- `findTemplateById` for STAGE7-010 confirmation contract.

### Cutting

- Optional informational `CuttingPlanId` / `CuttingLinkStatus` (`NONE` / `SINGLE` / `MULTIPLE_REFERENCES`).
- Conflicting plans → MULTIPLE_REFERENCES, no silent first-pick; quantity and `planningSource` stay SPECIFICATION.
- `MaterialPlanningSource.CUTTING_PLAN` reserved for future Stage 8 read contract (unused now).
- STAGE7-008A not executed.

### Persistence

- V27 tables (production schema only, no cross-capability FK):
  - `material_transfer_templates`
  - `material_transfer_template_lines`
  - `material_transfer_template_line_source_items`
  - `material_transfer_template_line_cutting_refs`
- Optimistic `version` on template header; `MaterialTransferTemplateOptimisticLockException` on conflict.
- JDBC round-trip + optimistic-lock tests.

### Architecture

- Stage7ProductionArchitectureTest: no WarehouseCommandApi / Warehouse internals / Cutting runtime / DocumentProcessor; template service does not depend on `ProductionItemStateRepository` directly.

### Queue

- STAGE7-008A remains PLANNED.
- STAGE7-010 → READY (confirm from saved template id; included lines with requestedQuantity > 0 only).

---

## STAGE7-008 — Cutting Plan references 0..N

**Date:** 2026-08-21
**Stage:** 7
**Module:** `tmp-production` (Flyway V26 in production resources)
**Status:** DONE

### Domain

- `CuttingPlanId` — opaque UUID VO; no revision/status/contents.
- Production-owned `MaterialReferenceId` — opaque external reference (no Warehouse FK / no Warehouse domain import).
- `ProductionCuttingPlanLink` + immutable `CuttingPlanLinks` (max one plan per material per item).
- Links live on `ProductionItemState` (item-owned), not on Foundation / not order-level aggregate.
- Default Launch / rehydrate without links → empty collection; overload `launch(..., CuttingPlanLinks)`.

### Launch

- `ProductionLaunchLine` carries `CuttingPlanLinks` (0..N).
- `ProductionLaunchService` supplies empty links (Stage 8 absent); no Cutting runtime calls.
- `LaunchProductionCommand` unchanged (no UI Cutting IDs).

### Persistence

- V26: `production.production_item_cutting_plan_links`
  - columns: `id`, `production_item_id`, `material_reference_id`, `cutting_plan_id`
  - FK → `production.production_item_states(id)` only
  - UNIQUE (`production_item_id`, `material_reference_id`)
  - index on `cutting_plan_id`
- `JdbcProductionItemStateRepository` save/load links atomically with aggregate (replace-all).

### Compatibility

- Material Check with mere link present remains `planningSource = SPECIFICATION`.
- Existing rows without child links load as empty.
- Post-launch association gap documented as STAGE7-008A PLANNED (not implemented).

### Architecture

- Stage7ProductionArchitectureTest: no `com.tmp.cutting..`; Production-owned link types; no CuttingPlanRevision.

---

## STAGE7-007 — Material availability via Warehouse Query

**Date:** 2026-08-20
**Stage:** 7
**Module:** `tmp-production` (depends on `tmp-warehouse` Public Query API only)
**Status:** DONE

### Use case

- `CheckMaterialAvailabilityService` — read/calculation only for `SourceOrderId`.
- Allowed only when computed Order Production View = `IN_PRODUCTION`.
- Rejected with `MaterialCheckNotAllowedException` for `NOT_ACCEPTED` / `MANUFACTURED` / `CANCELLED` (no Warehouse queries on reject).

### Requirement calculation

- Frozen `SpecificationId` → `ProductionFoundationQueryService` → `resolveById` only.
- Pure `SpecificationMaterialRequirementCalculator`: aggregates by `(materialCode, color, unitOfMeasure)`.
- `lineQuantity` is total planned quantity (OM Spec v1.10) — **not** multiplied by ordered/launched/product quantity.
- `planningSource = SPECIFICATION` (Cutting deferred to STAGE7-008+).

### Material identity

- Resolution via `WarehouseAvailabilityQueryPort` → `WarehouseQueryApi.listMaterialReferences()`.
- Match: article == materialCode, normalized color, unitOfMeasure; **`lengthMm` never used as Warehouse `size`**.
- 0 candidates → `MATERIAL_UNRESOLVED`; >1 → `MATERIAL_AMBIGUOUS`; no silent legacy-article fallback.

### Warehouse scope

- Production-owned `ProductionWarehouseScope(mainWarehouseId, productionWarehouseId)` — distinct non-null ids; validated active via `listWarehouses()`.
- No hardcoded MAIN/PRODUCTION codes or display names.
- Scoped `checkAvailability(materialReferenceId, warehouseId, …)` AVAILABLE quantity only; other warehouses do not mask deficit.

### Persistence / mutations

- Immutable `MaterialAvailabilityCheckResult` snapshot (`Clock`); not persisted as stock source of truth.
- `LastMaterialCheckAt` not updated (deferred to STAGE7-015A/016A document/projection path).
- No WarehouseCommandApi, Transfer, Reservation, Production status change, or Material Check document.

### Architecture

- Port/adapter: `WarehouseAvailabilityQueryPort` / `DefaultWarehouseAvailabilityQueryAdapter`.
- Stage7 ArchUnit: no Warehouse command/internals; no OM current-spec API; pure requirement calculator.

### Queue hygiene

- STAGE7-008 (depends on STAGE7-005) → READY.
- STAGE7-009 remains blocked on STAGE7-007 + STAGE7-008 until 008 completes.

### Verification

- `mvn -pl :tmp-production -am test` / `mvn -pl :tmp-warehouse -am test` / `mvn -pl :tmp-architecture-tests -am test` / `mvn test` / `mvn verify` — BUILD SUCCESS.

---

## STAGE7-006 — Computed Order Production View

**Date:** 2026-08-20
**Stage:** 7
**Module:** `tmp-production`, `tmp-order-management` (Public Query readiness DTO only)
**Status:** DONE

### Corrective Launch readiness

- `OrderForProductionDto` now exposes `missingSpecificationItemIds` alongside `activeItemCount` and producible `items`.
- Invariant: `activeItemCount == items.size() + missingSpecificationItemIds.size()`.
- `ProductionLaunchService`:
  - `activeItemCount == 0` → `OrderNotEligibleForProductionException` ("order has no ACTIVE items");
  - non-empty missing list → `SpecificationNotAvailableForLaunchException` with the first missing `SourceOrderItemId` (not a producible line id).
- `OrderAcceptedIntoProduction`: `acceptedItemCount == sourceOrderItemIds.size()` and no duplicate item IDs.

### Computed Order Production View

- Pure `OrderProductionViewCalculator` + `OrderProductionView` / `OrderProductionViewStatus`.
- Statuses: `NOT_ACCEPTED`, `IN_PRODUCTION`, `MANUFACTURED`, `CANCELLED`.
- Empty item states → `NOT_ACCEPTED` (never vacuous `MANUFACTURED`; not user-facing AVAILABLE without OM ACTIVE confirmation).
- `Context.cancelled()` query input documents STAGE7-014 whole-order cancellation; RELEASED+CANCELLED without it is rejected as ambiguous.
- Duplicate `SourceOrderItemId` rows rejected (no silent latest-spec pick).
- `ProductionOrderViewService` read-only; recalculates every query; does not persist.
- Repository: `findBySourceOrderId`; index already present in V23 (`idx_production_item_states_source_order_id`) — no new Flyway migration.
- No order-level aggregate table / entity / repository.

### Queue hygiene

- STAGE7-007 depends on STAGE7-005A, STAGE7-006 → READY.
- STAGE7-014 depends on STAGE7-005A.
- STAGE7-018 AC: whole-order Launch rollback must use Document Engine + Production JDBC + PostgreSQL/Testcontainers.

### Verification

- `mvn test` / `mvn verify` — BUILD SUCCESS.

---

## STAGE7-005A — Whole-Order Production Launch Correction & Reactor Recovery

**Date:** 2026-08-20
**Stage:** 7
**Module:** `tmp-production`, `tmp-order-management` (read-only Public Query), `tmp-ui-shell` (test doubles)
**Status:** DONE

### History of the correction

STAGE7-004 originally implemented item-level Production Launch (`sourceOrderItemId` + `orderedQuantity` on the command, one line, `ProductionLaunched` event). STAGE7-005 froze SpecificationId at that item Launch. Audit then found two defects:

1. Full reactor `mvn verify` was RED: `OrderQueryService` gained `getCurrentItemSpecification` / `getSpecificationById`, but six `tmp-ui-shell` test doubles did not implement them.
2. Accepted Production Specification requires one user operation "accept the order into production" for the entire ACTIVE Order, not a single Order Item.

STAGE7-005A corrected granularity without changing the item-owned `ProductionItemState` model and without introducing `ProductionOrder`.

### Whole-order Launch

- `LaunchProductionCommand(sourceOrderId, createdBy)` — no item id, quantity, or SpecificationId from the caller.
- OM Public Query `getOrderForProduction(orderId)` returns ACTIVE order status, ACTIVE item count, and Production-facing specifications.
- Non-ACTIVE / missing order → `OrderNotEligibleForProductionException`.
- One `ProductionLaunchPayload` with `List<ProductionLaunchLine>`; processor launches every line in one Document Engine `onPost`.
- Duplicate identity pre-check → `ProductionLaunchConflictException` for the whole document; DB UNIQUE remains race protection.
- After-commit event: `OrderAcceptedIntoProduction` (replaces item-level `ProductionLaunched` as the business event).
- Processor does not query OM.

### Foundation / frozen specification

- STAGE7-005 reference-only freeze retained per line.
- Post-launch reads still use `getSpecificationById`.
- Unavailable frozen specification → `FrozenSpecificationUnavailableException` (not empty material list).

### UI reactor recovery

Test doubles updated to the new `OrderQueryService` contract:

- `EmptyOrderQuery`
- `OrderEditorControllerFxTest.EmptyQuery`
- `OrderEditorViewModelTest.FakeQuery`
- `OrderItemListControllerFxTest.EmptyQuery`
- `OrderItemListViewModelTest.FakeQuery`
- `OrderListViewModelTest.FakeOrderQuery`

### Verification

- `mvn verify` — BUILD SUCCESS (all modules).

---

## STAGE7-004A — Order Management stable SpecificationId public contract alignment

**Date:** 2026-08-19  
**Stage:** 7  
**Module:** `tmp-order-management`, `tmp-infra-db` (Flyway V24)  
**Status:** DONE

### Changes

Stable opaque `SpecificationId` added to OM Public Query API for Production-facing read contract.

**New files (3):**
- `SpecificationId.java` — opaque UUID identifier in `com.tmp.order.api`
- `ProductionSpecificationDto.java` — Production-facing DTO without RevisionNumber
- `V24__item_specification_id.sql` — Flyway migration adding `specification_id` UUID column

**Modified production files (5):**
- `OrderQueryService.java` — added `getCurrentItemSpecification()`, `getSpecificationById()`
- `OrderQueryReadPort.java` — added `findCurrentSpecification()`, `findSpecificationById()`
- `DefaultOrderQueryService.java` — implemented new methods with authorization
- `JdbcOrderQueryReadAdapter.java` — JDBC queries for Production-facing contract
- `OrderAggregateSql.java` + `OrderItemAggregateJdbcSupport.java` — `specification_id` in INSERT, deterministic UUID derivation

**Modified test files (3):**
- `DefaultOrderQueryServiceSecurityTest.java` — EmptyReadPort updated
- `OrderImportMetadataFlywayTest.java` — latest migration version updated
- `JdbcOrderQueryReadAdapterIT.java` — 6 new integration tests

**New test files (3):**
- `ProductionSpecificationDtoTest.java`
- `SpecificationIdTest.java`
- `SpecificationIdDerivationTest.java`

### Architecture decisions

- `specification_id` is derived deterministically from `(orderItemId, revisionNumber)` via `UUID.nameUUIDFromBytes` for stability across delete-reinsert save cycles.
- Legacy revision-based API preserved; Production never uses RevisionNumber.
- Document Engine payload remains in-memory — this is a known limitation. Backlog item created below.

### Verification

- `mvn -pl :tmp-order-management -am test` — 366 tests PASS
- `mvn -pl :tmp-architecture-tests -am test` — BUILD SUCCESS

### Backlog

- **Evaluate persistent capability payload support for Document Engine**: `ProductionLaunchPayload` currently lives in `ProductionLaunchPayloadHolder` (in-memory). If Document Engine should support payload recovery after restart, a separate task is needed. Not blocking for STAGE7-004A.

---

## STAGE7-004 — Production Launch document

**Date:** 2026-08-19  
**Stage:** 7  
**Module:** `tmp-production`  
**Status:** DONE

### Changes

Production Launch implemented as a Business Document through Document Engine.

**New production files (5):**
- `AlreadyLaunchedForProductionException.java` — domain-specific exception for duplicate launch
- `ProductionLaunchPayload.java` — immutable payload record (OrderId, OrderItemId, SpecificationId, OrderedQuantity, LaunchTimestamp, CreatedBy)
- `ProductionLaunchProcessor.java` — DocumentProcessor implementation; only component using ProductionItemStateRepository; delegates to `ProductionItemState.launch()`; publishes `ProductionLaunched` after commit
- `ProductionLaunchPayloadHolder.java` — in-memory payload holder for Document Engine lifecycle bridge
- `ProductionLaunched.java` — DomainEvent published after successful commit
- `LaunchProductionCommand.java` — application command
- `ProductionLaunchService.java` — application service orchestrating create+post through Document Engine

**Modified files:**
- `pom.xml` — added `tmp-platform-core` and `tmp-document-engine` dependencies
- `Stage7ProductionArchitectureTest.java` — added `onlyProcessorUsesRepository` rule

**New test files (5):**
- `ProductionLaunchProcessorTest.java` — launch, duplicate guard, domain event, unpost rejection
- `LaunchProductionCommandTest.java` — validation
- `ProductionLaunchPayloadTest.java` — validation
- `ProductionLaunchedTest.java` — event properties
- `AlreadyLaunchedForProductionExceptionTest.java` — message/accessors

### Architecture

```
Application (LaunchProductionCommand)
  → ProductionLaunchService
    → DocumentEngine.createDocument() + postDocument()
      → ProductionLaunchProcessor.onPost()
        → ProductionItemStateRepository.findByIdentity() [duplicate check]
        → ProductionItemState.launch() [domain]
        → ProductionItemStateRepository.save()
        → TransactionalEventPublisher.publishAfterCommit(ProductionLaunched)
```

### Verification

- `mvn -pl :tmp-production -am test` — 63 tests PASS
- `mvn -pl :tmp-architecture-tests -am test` — BUILD SUCCESS
- No Warehouse/Order/Cutting dependencies
- No Revision usage
- No Material Check

---

## STAGE7-003 — Production persistence schema

**Date:** 2026-08-19  
**Stage:** 7  
**Status:** DONE  
**Type:** Persistence layer only (no business/documents/cross-capability integration)

### Summary

Реализован Production persistence layer для item-owned state: Flyway migration `production.production_item_states`, JDBC repository, entity↔domain mapping без JPA и без зависимости на Warehouse/Order/Cutting internals.

### Persistence deliverables

- Flyway `V23__production_schema.sql`: schema `production`, table `production_item_states`.
- Surrogate `ProductionItemId` (persistence-only); business identity = `SourceOrderId + SourceOrderItemId + SpecificationId`.
- `ProductionItemStateEntity` (mutable persistence representation).
- `ProductionItemStateMapper` + `JdbcProductionItemStateRepository`.
- Domain port `ProductionItemStateRepository`.
- Domain `ProductionItemState.rehydrate(...)` for load path (no business logic in persistence).

### Schema invariants

- `specification_id NOT NULL`; no Revision column/table.
- No `production_orders`, `OrderProductionStatus`, aggregate/batch/revision tables.
- Production-owned columns only (status, quantities, material-check/status timestamps).
- No FK to warehouse/order_management schemas.

### Tests

- Unit: `ProductionItemStateMapperTest`, `ProductionPersistenceContractTest`.
- Integration: `JdbcProductionItemStateRepositoryTest`, `ProductionSchemaFlywayTest`.
- Architecture: `Stage7ProductionArchitectureTest` persistence-layer dependency rule.

### Boundaries

- No Launch/Release/Cancel/Material Check/Transfer/Receipt/Consumption.
- No Warehouse/Order/Cutting/UI/document code changes.
- Git not executed by agent.

---

## STAGE7-002 — Production identifiers and item-owned state model

**Date:** 2026-08-19  
**Stage:** 7  
**Status:** DONE  
**Type:** Domain implementation (no persistence, documents or cross-capability integration)

### Summary

Реализована чистая Production domain-модель item-owned state: opaque identifiers, quantity invariants, status transitions без persistence и без зависимости на OM/Warehouse/Cutting internals. Исправлен typo `com.tmp.cut..` → `com.tmp.cutting..` в Stage7ProductionArchitectureTest. Добавлена future task STAGE7-004A для OM SpecificationId public contract alignment.

### Domain model

- Identifiers: `SourceOrderId`, `SourceOrderItemId`, `SpecificationId` (Production-owned opaque UUID refs).
- Quantity: `ProductionQuantity` (whole-number; positive for ordered, non-negative for launched/active/released).
- Status: `ProductionStatus` enum with accepted vocabulary; `NOT_STARTED` vocabulary-only.
- State: `ProductionItemState` created at Launch with non-null `SpecificationId`; no nullable spec identity.
- Exception: `InvalidProductionStateException`.

### Invariants / behavior

- No Production Order entity; no order-level persisted aggregate.
- No RevisionNumber / OrderItemRevision in Production domain.
- Launch → `IN_PRODUCTION` with full ordered quantity as launched/active.
- Release → `PARTIALLY_RELEASED` / `RELEASED` with quantity guards.
- Cancel → `CANCELLED` for unfinished production; `RELEASED` cannot be cancelled.
- `active + released == launched` enforced except for `CANCELLED`.

### Architecture

- Fixed Cutting internal package patterns in `Stage7ProductionArchitectureTest`.
- Stage4 future-stage guard unchanged (Stage 8+ only).

### Future OM contract gap

- Added `STAGE7-004A` (PLANNED) to align OM Public Query with Accepted OM Spec v1.10 stable `SpecificationId`.
- Updated `STAGE7-005` to depend on `STAGE7-004A`.
- No OM main code changes in this task.

### Boundaries

- Warehouse / OM / Cutting / UI / persistence / documents: unchanged.
- Git not executed.

---

## STAGE7-001 — Production module foundation

**Date:** 2026-08-18  
**Stage:** 7  
**Status:** DONE  
**Type:** Foundation implementation (no Production business logic)

### Summary

Создан минимальный capability-модуль `tmp-production` для Stage 7 foundation: подключение в root reactor, формализация package boundaries (`api`/`application`/`domain`/`infrastructure`) и архитектурная защита от зависимостей на internal-пакеты других capabilities.

### Module foundation

- Added `tmp-production/pom.xml` with existing parent conventions and no speculative dependencies.
- Added `tmp-production` to root reactor modules in `pom.xml`.
- No Production business services, documents, persistence adapters, migrations or UI artifacts introduced.

### Package boundaries

- Added boundary packages under `com.tmp.production` via `package-info.java`:
  - `com.tmp.production.api`
  - `com.tmp.production.application`
  - `com.tmp.production.domain`
  - `com.tmp.production.infrastructure`
- `com.tmp.production.api` explicitly reserved as future public cross-capability boundary; no Stage7-016A API methods added in this task.

### Architecture protection

- Added `Stage7ProductionArchitectureTest` in `tmp-architecture-tests` with invariant:
  `com.tmp.production..` must not depend on `application/domain/persistence` internals of Warehouse/Order/Cutting capabilities.
- Added `tmp-production` dependency to `tmp-architecture-tests/pom.xml` so new package boundaries are part of ArchUnit scan.
- Updated pre-existing Stage 4 guard rule to keep Stage 8+ package prohibition while allowing Stage 7 `com.tmp.production..` existence.

### Boundaries

- Warehouse code: unchanged.
- Order Management code: unchanged.
- Cutting Optimization code: unchanged.
- UI code: unchanged.
- Git not executed.

---

## STAGE7-000C — Warehouse Transfer Integrity

**Date:** 2026-08-18  
**Stage:** 7  
**Status:** DONE  
**Type:** Pre-implementation corrective (no Production module)

### Summary

Закрыты correctness defects Transfer lifecycle перед STAGE7-001: exactly-once receive, positive Transfer quantity, logical DRAFT/SENT/RECEIVED status. Warehouse остаётся line-operation based; STAGE7-010 фиксирует grouping 1 Production template → N Warehouse lines.

### Exactly-once receive

- `TransferOperationContext.receiveOperationId` (nullable);
- Flyway V22 unique partial index on `receive_operation_id`;
- `SELECT … FOR UPDATE` + `UPDATE … WHERE receive_operation_id IS NULL` in the same `REQUIRED` transaction as `IN_TRANSIT → AVAILABLE`;
- sequential duplicate, same-material A/B, concurrent duplicate, and rollback-probe tests.

### Transfer quantity

- `quantity > 0` enforced on `TransferDraftRequest` / `createDraft` and send draft execution;
- `StockQuantity` unchanged (zero still valid for Stock Position).

### Transfer status

- `WarehouseQueryApi.getTransferStatus` returns logical `DRAFT` / `SENT` / `RECEIVED` plus `receiveOperationId`.

### Boundaries

- Query/Command API split unchanged.
- No `tmp-production`.
- Git not executed.

---

## STAGE7-000B — Warehouse Production Integration Readiness

**Date:** 2026-08-17  
**Stage:** 7  
**Status:** DONE  
**Type:** Pre-implementation corrective / Stage 6 integration readiness (no Production module)

### Summary

Закрыты оставшиеся Warehouse readiness defects перед STAGE7-001: green full reactor verify, явные public Query/Command boundaries, exact MaterialReference availability, Transfer draft lifecycle, Consumption command boundary для ADR-036.

### SpotBugs (`WarehouseOperationEngine`)

| Bug | Method | Fix |
|---|---|---|
| NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE | execute, transferSend, transferReceive, move | `requireTransactionResult()` helper throws `InvalidWarehouseStateException` when `TransactionTemplate.execute()` returns null |

Suppression used: **NO**.

### Public API boundaries

- **Query:** `com.tmp.warehouse.api.WarehouseQueryApi` — read-only (stock, availability, transfer status).
- **Command:** `com.tmp.warehouse.api.WarehouseCommandApi` — receive, consume, transfer draft/send/receive, catalogue/reservation commands.
- **UI aggregate:** `WarehouseApi extends WarehouseQueryApi, WarehouseCommandApi`.
- **Display port moved to API:** `MaterialReferenceDisplayPort`, `MaterialReferenceDisplay` in `com.tmp.warehouse.api`.

### Transfer draft lifecycle

- `createTransferDraft` → DRAFT (no stock mutation);
- `sendTransfer` → AVAILABLE → IN_TRANSIT;
- `receiveTransfer` → IN_TRANSIT → AVAILABLE at destination;
- Flyway V21 `warehouse_transfer_operation_context`.

### Material identity / availability

- Primary: `materialReferenceId` or `MaterialIdentityRequest` (article+color+size+unitOfMeasure).
- Legacy: `checkAvailabilityByLegacyArticle` for empty-variant migrated rows only.
- Integration tests: receipt→availability, variant isolation, warehouse split, legacy separation.

### Architecture / bootstrap

- `Stage6WarehouseArchitectureTest.externalModulesUseOnlyWarehousePublicApi`.
- `WarehouseMaterialDisplayConfiguration` uses `@Qualifier("warehouseMaterialDisplayFallback") MaterialReferenceDisplayPort`.
- `PlatformCoreIntegrationIT` capability count updated (6 incl. Warehouse).

### Boundaries

- No `tmp-production` module.
- No Production domain/documents/persistence/UI.
- Git not executed.

---

## STAGE7-000A — Restore Green Reactor Baseline

**Date:** 2026-08-17  
**Stage:** 7  
**Status:** DONE  
**Type:** Pre-implementation baseline / Warehouse test debt + queue hygiene (no Production implementation)

### Summary

Исправлен dependency graph Stage 7, добавлены задачи Audit/History и Public Query API, устранён известный Warehouse test debt (5 failing/error cases), восстановлен green `mvn test` baseline. Production implementation не начиналась.

### Queue audit

- STAGE7-009 → depends on STAGE7-007 + STAGE7-008
- STAGE7-015 → depends on STAGE7-013 + STAGE7-014; events list clarified
- STAGE7-015A added (Production Audit/History)
- STAGE7-016A added (Production Public Query API)
- STAGE7-017 → depends on STAGE7-011, STAGE7-013, STAGE7-014, STAGE7-016, STAGE7-016A; acceptance criteria expanded
- STAGE7-018 → Production Cancellation boundary test required
- STAGE7-020 → depends on STAGE7-014, STAGE7-015, STAGE7-015A, STAGE7-016A, STAGE7-017, STAGE7-018, STAGE7-019
- STAGE7-008 persistence scope clarified (production Flyway only when needed)
- STAGE7-001 → depends on STAGE7-000A

### Warehouse test debt (test-only fixes)

| Test | Root cause | Fix |
|---|---|---|
| `WarehouseSchemaFlywayTest` (2 failures + 1 error) | SQL used dropped column `material_reference`; schema now uses `material_reference_id` FK | Insert via `material_references` helper |
| `WarehouseReceiptServiceIntegrationTest` (1 error) | Receipt missing required `unitOfMeasure` after V20 | Pass canonical UoM `шт.`; align pre-seeded material natural key |
| `WarehouseApiIntegrationTest` (1 error) | `checkAvailability(materialCode)` uses legacy empty-UoM natural key; receipt creates UoM material | Split availability into dedicated test with `legacyArticle` setup |

### Additional verify discovery (test-only, outside original 3-test list)

- `JdbcMaterialReferenceDisplayReadAdapterIT` — fixture SQL out of sync with OM schema (`version`, column names); updated test inserts only.

### Verification

- `mvn -pl :tmp-warehouse -am test` — PASS
- `mvn test` — PASS
- `mvn verify` — FAIL (pre-existing SpotBugs: 4× NP in `WarehouseOperationEngine` production code; not introduced by this task; out of STAGE7-000A scope)

### Unchanged

- Start Gate PASSED; ADR-036 Accepted; atomicity blocker RESOLVED
- No `tmp-production`; Stage 7 implementation 0%
- STAGE7-001 = READY
- Git not executed

---

## STAGE7-000 — Stage 7 Start Gate / Release↔Consumption atomicity proof

**Date:** 2026-08-17  
**Stage:** 7  
**Status:** DONE  
**Type:** Documentation + focused Document Engine integration proof (no Production implementation)

### Summary

Закрыт Start Gate blocker `BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY`. Empirical proof: две lifecycle-операции Document Engine в одной внешней ACID-транзакции либо обе commit, либо обе rollback, включая capability-owned JDBC side effects и after-commit events. Принят ADR-036. Production implementation не начиналась.

### Proof

- Transaction mechanism: local Spring JDBC ACID transaction (`DataSourceTransactionManager`)
- Propagation: `REQUIRED` (class-level `@Transactional` on Document Engine; no `REQUIRES_NEW` for create/post/processor)
- Test: `DocumentEngineMultiDocumentTransactionIT`
- Result: PASS

### Documentation changes

- ADR-036 Accepted; ADR-035 linked, not superseded
- Document Engine Specification v1.2
- Production Specification v2.2 §21
- Warehouse Specification v1.6 pointer
- Cutting Optimization Specification v1.2 residual wording
- STAGE-7 Manifest Start Gate PASSED / implementation 0%
- WORK-QUEUE Stage 7 decomposition prepared; `STAGE7-001` READY

### Code

- Production implementation: NONE
- Document Engine production code: NONE
- Test-only: `DocumentEngineMultiDocumentTransactionIT`, `JdbcSideEffectDocumentProcessor`

### Boundaries

- Platform Core unchanged
- Warehouse business implementation unchanged
- No Saga / eventual consistency / 2PC
- Git commands not executed by agent

---


## Stage 6 Warehouse — Final Closure Audit

**Date:** 2026-08-10  
**Stage:** 6  
**Status:** DONE  
**Type:** Documentation closure only (no code / DB / API changes)

### Summary

Финальный аудит Stage 6 Warehouse после `STAGE6-017`. Manual GUI verification PASS по 10 сценариям. Создан `Warehouse-Final-Verification.md`. Security Specification дополнен 16 Warehouse permissions. Warehouse Specification §18 синхронизирован с реализацией (`PermissionId` codes).

### Documentation changes

- `docs/development-control/Warehouse-Final-Verification.md` — created
- `docs/development-control/VERIFICATION-LOG.md` — Warehouse Manual Verification PASS
- `docs/development-control/STATUS.md` — Stage 6 Final Closure section
- `docs/development-control/stages/STAGE-6-WAREHOUSE.md` — status DONE
- `Security-Specification.md` — Warehouse capability permissions (16)
- `Warehouse-Specification.md` — §18 updated to canonical permission codes

### Boundaries

- Production code, database schema, API contracts not modified
- Git commands not executed by agent

---

## STAGE6-017 — Warehouse structure permissions security fix

**Date:** 2026-08-10  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`, `tmp-ui-shell`, `tmp-bootstrap-app`

### Summary

Добавлены Capability управления складской структурой (склад / ячейка). Создание склада и ячейки больше не завязано на `warehouse.stock.view`; operation permissions не изменялись. Permissions синхронизируются через Capability catalog (Flyway не требуется).

### Changes

- `WarehousePermissions` / `WarehousePermissionCatalog`: +8 structure capabilities (всего 16)
- `DefaultWarehouseApi`: create → structure create; list warehouse/cell → stock.view OR structure view
- Warehouse UI: create buttons gated/hidden by create permissions; delete flags exposed
- Security Administrator ensure includes all 16 warehouse permissions
- Tests: catalog, API auth, ViewModel operator vs administrator, bootstrap ensure

### Note on storage-cell codes

`PermissionId` format allows `[a-z0-9-]`, not underscore. Codes are `warehouse.storage-cell.*` (not `warehouse.storage_cell.*`).

### Boundaries

- Domain / Operation Engine / Stock / Movement / Transfer / Consumption / Reservation not changed
- Existing operation permission codes unchanged
- Git commands not executed by agent

---

## STAGE6-016 — Warehouse UI + Security Roles UI final UX fix

**Date:** 2026-08-10  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-ui-shell`, `tmp-warehouse` (additive Public API catalogue only)

### Summary

Исправлены UX-проблемы ручной проверки Stage 6: создание склада/ячеек через Public API, ComboBox вместо UUID, Transfer Receive, одна навигация (левое меню), простой экран Инвентаризация, layout Roles с фиксированной высотой таблицы и scrollable permissions.

### Changes

- Warehouse Public API: `createWarehouse`, `createStorageCell`, `listStorageCells` (+ DTOs)
- `WarehouseCatalogRepository` / JDBC: `save` warehouse/cell, list cells by warehouse
- Warehouse workbench UI: create forms, dropdowns, Transfer Receive, Inventory hint, nav dedupe
- Roles UI: minHeight role table, ScrollPane for permissions, selection mode SINGLE
- Tests updated for API + ViewModel + Roles FX lookup via ScrollPane content

### Boundaries

- Domain model / Security model / PermissionId / DB schema not changed
- Git commands not executed by agent

---

## Stage 6 — Warehouse documentation finalization

**Date:** 2026-08-10  
**Stage:** 6  
**Status:** DONE  
**Type:** Documentation only

### Summary

Stage 6 Warehouse implementation completed.

Документационные расхождения после Warehouse Final Audit (`Warehouse-Readiness-Review.md`, verdict `READY_TO_CLOSE`) закрыты: статусы Stage 6 в control docs выровнены, устаревший пример `warehouse.issue` в Security Specification заменён на актуальный permission id.

### Changes

- `WORK-QUEUE.md`: STAGE6-012..014 READY → DONE; Stage 6 section status → DONE
- `STATUS.md`: явное `Stage 6 Warehouse = DONE`
- `Security-Specification.md`: пример `warehouse.issue` → `warehouse.stock.view` (WAREHOUSE_VIEW)
- `VERIFICATION-LOG.md`: запись Final Audit

### Boundaries

- Код / SQL / API / UI / Warehouse logic не изменялись
- Git commands not executed by agent

---

## STAGE6-015 — Warehouse UI

**Date:** 2026-08-09  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-ui-shell`, `tmp-warehouse`, `tmp-bootstrap-app`

### Summary

Реализован Warehouse UI v1.0 как workbench с восемью Capability-gated секциями. Все чтение/запись только через Public API (`WarehouseApi`). Бизнес-логика склада в UI отсутствует; прямая мутация Stock Position / Movement запрещена.

### Key deliverables

- UI: `WarehouseWorkbench` (FXML + Controller + ViewModel) — 8 секций
- `WarehouseUiErrorMapper`, `WarehouseSection`
- API extensions for UI reads: `listWarehouses`, `getStockByWarehouse`, `listReservationLinks`
- Spring wiring: `WarehouseAutoConfiguration` (Public API + services), bootstrap screen registration
- Capability navigation: `warehouse.nav.workbench` → `warehouse.view.workbench`
- Tests: ViewModel/capability/API call tests; Stage 6 architecture boundaries

### Screens (sections)

| Section | Capability |
|---|---|
| Список складов | WAREHOUSE_VIEW |
| Остатки склада | WAREHOUSE_VIEW |
| Поступление | WAREHOUSE_RECEIPT |
| Перемещение | WAREHOUSE_MOVE |
| Межскладское перемещение | WAREHOUSE_TRANSFER |
| Списание | WAREHOUSE_CONSUMPTION |
| Корректировка | WAREHOUSE_ADJUSTMENT |
| Информационные связи | WAREHOUSE_RESERVATION |

### Boundaries

- UI → `com.tmp.warehouse.api` only
- No WMS / barcodes / terminals / placement optimization
- Основание/причина списания и корректировки — UI validation fields (не персистятся в v1.0 API)
- Git commands not executed by agent

---

## STAGE6-014 — Warehouse Security Integration

**Date:** 2026-08-09  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Интегрированы `WAREHOUSE_*` permissions с существующей Security Capability. Warehouse декларирует permissions через Capability SPI; проверка доступа идёт через публичный `AuthorizationService`. Пользователи, роли и собственные permission tables не создавались; Security architecture не изменялась.

### Key deliverables

- `WarehousePermissions` — 8 `PermissionId` (`WAREHOUSE_*` → `<area>.<resource>.<action>`)
- `WarehousePermissionCatalog` — `PermissionDescriptor` metadata (RU display names)
- `WarehouseCapability` — Capability contribution (permissions only)
- `WarehouseAutoConfiguration` — Spring bean registration for Capability Engine discovery
- Guards: `DefaultWarehouseApi` + `WarehouseInventoryService`
- Tests: catalog registration + authorization allow/deny

### Permission mapping (Spec §18 → PermissionId)

| Logical | PermissionId |
|---|---|
| WAREHOUSE_VIEW | warehouse.stock.view |
| WAREHOUSE_RECEIPT | warehouse.receipt.create |
| WAREHOUSE_MOVE | warehouse.move.create |
| WAREHOUSE_TRANSFER | warehouse.transfer.create |
| WAREHOUSE_RESERVATION | warehouse.reservation.create |
| WAREHOUSE_CONSUMPTION | warehouse.consumption.create |
| WAREHOUSE_ADJUSTMENT | warehouse.adjustment.create |
| WAREHOUSE_INVENTORY | warehouse.inventory.create |

### Boundaries

- No users/roles/permission tables in Warehouse
- No Security core changes
- No UI
- Git commands not executed by agent

---

## STAGE6-013 — Warehouse Public API

**Date:** 2026-08-09  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализован минимальный Public API Warehouse (Specification §17): `getStock`, `checkAvailability`, `createReservationLink`, `executeWarehouseOperation`. API отдаёт DTO/views, не доменные агрегаты; не позволяет прямую мутацию Stock Position / Movement. Бизнес-логика операций не изменялась — адаптер делегирует существующим application-сервисам.

### Key deliverables

- `com.tmp.warehouse.api.WarehouseApi` — контракт + nested DTOs/commands/results
- `DefaultWarehouseApi` — application adapter (mapping + orchestration)
- Read extension: `StockPositionRepository.findByMaterial` + JDBC query
- Unit: contract/DTO tests + mapping/orchestration tests
- Integration: API receipt/availability/reservation/consumption against PostgreSQL

### Boundaries

- No UI, Security, Events
- No direct Stock/Movement mutation through API
- Reservation link remains informational
- Git commands not executed by agent

---

## STAGE6-012 — Information Reservation Link

**Date:** 2026-08-09  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализована информационная связь `MaterialReservationLink` (Material + Order/Production reference + Quantity) без изменения `Stock Position`, без `Warehouse Movement` и без `StockState.RESERVED`. Снятие резерва не реализовано. Persistence: `warehouse.material_reservation_links` (V18).

### Key deliverables

- Domain: `MaterialReservationLink`, `MaterialReservationLinkId`, `ReservationTargetReference`, `ReservationTargetType`
- `MaterialReservationLinkRepository` + `JdbcMaterialReservationLinkRepository`
- `WarehouseReservationLinkService` — create / findById / findByMaterial / findByTarget
- Flyway `V18__warehouse_reservation_links.sql`
- Unit + integration tests; schema still rejects RESERVED stock state

### Boundaries

- Informational only (§8)
- No stock/movement mutation; no release workflow; no allocation engine
- Git commands not executed by agent

---

## STAGE6-011 — Inventory and Adjustment

**Date:** 2026-08-09  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализованы Adjustment и Inventory reconciliation поверх `WarehouseOperationEngine`: Adjustment Request → ADJUSTMENT Operation → Warehouse Movement → Stock Position; Inventory Count → Difference → Adjustment Operation. Без Reservation/Public API/UI, Batch, Material Master.

### Key deliverables

- `AdjustmentRequest` — non-zero signed `quantityDelta`
- `WarehouseAdjustmentService.adjust` — target = current + delta; rejects negative result; engine create(ADJUSTMENT)+execute
- `InventoryCountRequest` — counted absolute quantity
- `WarehouseInventoryService.reconcile` — difference → Adjustment when non-zero; no-op when counts match
- Unit: positive/negative adjustment, negative stock rejected, inventory short count / matching count
- Integration: operation/movement/stock persisted for adjustment and inventory flow

### Boundaries

- Only Adjustment + Inventory (§11)
- Uses existing Operation Engine (no direct stock mutation)
- Git commands not executed by agent

---

## STAGE6-010 — Material Consumption

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализовано списание `Consumption` поверх `WarehouseOperationEngine`: Consumption Request → CONSUMPTION Operation → Warehouse Movement (negative quantityDelta) → Stock Position decrease. Production определяет материал и количество; Warehouse проверяет наличие и фиксирует историю. Без Adjustment/Inventory/Reservation, REST/UI/Events.

### Key deliverables

- `ConsumptionRequest` — positive quantity; material + warehouse + cell
- `WarehouseConsumptionService.consume` — availability validation; target qty = existing − consumption; engine create(CONSUMPTION)+execute
- Unit: success, insufficient stock, missing stock, zero/negative quantity
- Integration: operation/movement/stock persisted

### Boundaries

- Only Consumption (§14)
- Uses existing Operation Engine (no direct stock mutation)
- Git commands not executed by agent

---

## STAGE6-009 — Inter-Warehouse Transfer

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализован двухэтапный межскладской Transfer поверх `WarehouseOperationEngine`: Send (`AVAILABLE → IN_TRANSIT` на источнике) и Receive (`IN_TRANSIT → AVAILABLE` на назначении). Типы операций `TRANSFER_SEND` / `TRANSFER_RECEIVE`. Количество и материал не меняются; same-warehouse Transfer запрещён. Без Consumption/Adjustment/Inventory/Reservation, REST/UI/Events.

### Key deliverables

- `TransferSendRequest` / `TransferReceiveRequest`
- `WarehouseTransferService.send` / `receive`
- `WarehouseOperationEngine.transferSend` / `transferReceive`
- Domain/schema: `TRANSFER` → `TRANSFER_SEND` + `TRANSFER_RECEIVE` (`V17__warehouse_transfer_operations.sql`)
- Unit: send, receive, insufficient stock, same warehouse
- Integration: operations/movements/stock persisted for full ship→receive

### Boundaries

- Only Transfer (§13.2)
- Uses existing Operation Engine (no direct stock mutation)
- Git commands not executed by agent

---

## STAGE6-008 — Internal Material Move

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализовано внутреннее перемещение `Move` поверх `WarehouseOperationEngine`: Material + Quantity + source/destination cells одного Warehouse → MOVE Operation → Movements (source −qty, destination +qty) → Stock Position updates. Количество/материал/склад не меняются; новый Stock Position создаётся только при отсутствии остатка в destination. Без Transfer/Consumption/Adjustment/Inventory/Reservation, REST/UI/Events.

### Key deliverables

- `MoveRequest` — positive quantity; source/destination warehouse + cell
- `WarehouseMoveService.move` — same-warehouse / distinct-cell validation; delegates to engine
- `WarehouseOperationEngine.move` — atomic dual-leg stock update + two MOVE movements
- Unit: success, insufficient stock, different warehouses
- Integration: operation/movements/stock persisted

### Boundaries

- Only Internal Move (§13.1)
- Uses existing Operation Engine (no direct stock mutation)
- No schema migration (destination encoded via movements)
- Git commands not executed by agent

---

## STAGE6-007 — Warehouse Receipt Operation

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализована операция поступления `Receipt` поверх `WarehouseOperationEngine`: Material + Quantity + Warehouse + Storage Cell → RECEIPT Operation → Movement (quantityDelta > 0) → create/update AVAILABLE Stock Position (old + receipt). Без supplier/procurement/price, Move/Transfer/REST/UI/Events.

### Key deliverables

- `ReceiptRequest` — positive quantity validation
- `WarehouseReceiptService.receive` — target qty = existing + receipt; engine create(RECEIPT)+execute
- Unit: operation RECEIPT, movement delta > 0, quantity increase, reject non-positive
- Integration: operation/movement/stock persisted

### Boundaries

- Only Receipt
- Uses existing Operation Engine (no direct stock mutation)
- Git commands not executed by agent

---

## STAGE6-006 — Warehouse Operation Engine

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализован Warehouse Operation Engine как единственный write path: создание DRAFT, проверка состояния, атомарное выполнение (Stock Position через WarehouseOperation + Warehouse Movement), фиксация COMPLETED/FAILED. Без Receipt/Move/Transfer/Consumption/Adjustment/Inventory, REST/UI/Events, Material Master, Batch, RESERVED.

### Key deliverables

- Domain lifecycle: `WarehouseOperationStatus` (DRAFT / COMPLETED / FAILED), `WarehouseOperation.draft/complete/fail/ensureDraft`
- `WarehouseOperationEngine`: create / canExecute / execute (transactional)
- `WarehouseOperationRepository` + `JdbcWarehouseOperationRepository`
- Flyway `V16__warehouse_operation_engine.sql`: status CREATED→DRAFT, payload columns
- Unit: create, success, failure→FAILED, no re-execute
- Integration: operation persisted, movement created, stock changed

### Boundaries

- Only Operation Engine
- No business warehouse processes
- No REST API, UI, Events
- Git commands not executed by agent

---

## STAGE6-005 — Warehouse Movement Persistence

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализован append-only persistence `WarehouseMovement` между domain и `warehouse.warehouse_movements`: repository port, JDBC adapter, domain ↔ row mapping. Movement immutable (no update/delete). Без Warehouse Operation execution, Receipt/Move/Transfer и REST/UI/Events.

### Key deliverables

- Domain `WarehouseMovement` aligned with persistence: id, stockPositionId, operationType, quantityDelta, createdAt
- `WarehouseMovementRepository` port: append / findHistoryByStockPosition (no update/delete)
- `JdbcWarehouseMovementRepository` + `WarehouseMovementRow.fromDomain` / `toDomain`
- Unit tests: movement creation, immutability, append-only repository contract
- Integration tests: append + ordered history read from PostgreSQL

### Boundaries

- Only Warehouse Movement persistence
- No operation orchestration or business flows
- No REST API, UI, Events
- Git commands not executed by agent

---

## STAGE6-004 — Stock Position Persistence

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Реализован persistence Stock Position между domain и `warehouse.stock_positions`: repository port, JDBC adapter, mapping domain ↔ row. Без Warehouse Operation execution, Movement, Reservation, REST/UI/Events.

### Key deliverables

- `StockPositionId` + domain `StockPosition` with technical id/version and rehydrate
- `StockPositionRepository` domain port: create / findById / findByNaturalKey / updateQuantity / updateState
- `JdbcStockPositionRepository` + `StockPositionRow.fromDomain` / `toDomain`
- Domain rules: quantity >= 0; states AVAILABLE / IN_TRANSIT / BLOCKED only (no RESERVED)
- Unit tests: create, negative quantity rejected, state change
- Integration tests: save/read DB, quantity/state updates, optimistic lock, state mapping

### Boundaries

- Only Stock Position persistence path
- No Receipt/Move/Transfer/Consumption/Reservation
- No REST API, UI, Events
- Git commands not executed by agent

---

## STAGE6-003 — Warehouse Database Schema

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE  
**Module:** `tmp-warehouse`

### Summary

Добавлен persistence-слой Warehouse: Flyway `V15__warehouse_schema.sql` (schema `warehouse`) и JDBC-репозитории для доступа к данным. Без API, Material Master, Batch, Reservation и execution операций.

### Key deliverables

- Migration `tmp-warehouse/src/main/resources/db/migration/V15__warehouse_schema.sql`
- Tables: `warehouses`, `storage_cells`, `stock_positions`, `warehouse_movements`, `warehouse_operations`
- JDBC: `JdbcWarehouseCatalogRepository`, `JdbcWarehouseStockRepository`, `WarehousePersistenceModels`
- Constraints: unique warehouse code; cell FK to warehouse; stock cell+warehouse composite FK; quantity >= 0; stock states AVAILABLE/IN_TRANSIT/BLOCKED only (no RESERVED)
- Tests: `WarehouseSchemaFlywayTest`, `JdbcWarehousePersistenceTest`

### Boundaries

- Only persistence/schema in `tmp-warehouse`
- No operation execution, Public API, UI, Production/Cutting
- Git commands not executed by agent

---

## STAGE6-PREPARATION-002 — Stage 6 Preparation completed

**Date:** 2026-08-06  
**Stage:** 6 (preparation only)  
**Status:** DONE  
**Module:** documentation only (no production code)

### Summary

Подготовлена детальная рабочая очередь реализации Warehouse в `WORK-QUEUE.md` с задачами `STAGE6-001..015` в нормализованном формате: ID, название, цель, описание, required context, allowed files, forbidden scope, implementation steps, verification, tests.

### Scope confirmation

- Stage 5 remains DONE.
- Stage 6 remains READY and NOT STARTED.
- Для `STAGE6-012` зафиксирован reservation information link без `RESERVED` stock state.
- Ограничения подтверждены: без Material Master, Batch, FIFO, FEFO, complex reservation.

### Updated documents

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`

### Boundaries

- Stage 6 code implementation не начата.
- Production code не изменялся.
- Git операции агентом не выполнялись.

---

## STAGE6-PLANNING — Stage 6 Implementation Planning Package

**Date:** 2026-08-06  
**Stage:** 6 (planning only)  
**Status:** DONE  
**Module:** documentation only (no production code)

### Summary

Подготовлен пакет планирования Stage 6 Warehouse по `Warehouse-Specification.md` v1.3: обновлен Stage Manifest, сформирована очередь задач `STAGE6-001..014`, синхронизированы `WORK-QUEUE.md` и `STATUS.md`.

### Scope confirmation

- Stage 5 остается DONE.
- Stage 6 переведен в READY (planning), implementation remains NOT STARTED.
- Stage 6 queue декомпозирована на небольшие задачи с goal/context/mutable files/completion criteria/tests.
- Ограничения подтверждены: без Material Master, Batch, FIFO, FEFO, WMS и сложных резервов.

### Updated documents

- `docs/development-control/stages/STAGE-6-WAREHOUSE.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`

### Boundaries

- Production code не изменялся.
- Разработка Stage 6 не начиналась.
- Git операции агентом не выполнялись.

---

## STAGE6-001 — Stage 6 Start Gate — Warehouse Documentation Update

**Date:** 2026-08-06  
**Stage:** 6 (Start Gate — documentation only)  
**Status:** DONE  
**Module:** documentation only (no production code)

### Summary

Обновлена `Warehouse-Specification.md` до версии **1.3** по решению Stage 6 Start Gate.

### Decision recorded

- Warehouse отвечает только за складское состояние материалов.
- Warehouse владеет: `Warehouse`, `Storage Cell`, `Stock Position`, `Warehouse Operation`, `Warehouse Movement`.
- Warehouse не владеет заказами/позициями/спецификациями и не рассчитывает потребность.
- Materials определяются через `Specification` Order Management.
- Material Mapping зафиксирован как пользовательское сопоставление; не является складской операцией.
- Модель Batch/партии/FIFO/FEFO/Supplier Batch исключена из версии 1.0.
- Reservation заменён на информационную связь без изменения `Stock Position` и без `Warehouse Movement`.
- `Warehouse Operation` зафиксирован как единственный механизм изменения склада.

### Updated documents

- `docs/TMP/TMP_Initial_Documents/architecture/11-Warehouse/Warehouse-Specification.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`

### Boundaries

- Production code **не изменялся**
- Stage 6 implementation **не начат**
- Git операции агентом **не выполнялись**

---

## STAGE6-000 — Stage 6 Start Gate — Material Handling Documentation

**Date:** 2026-08-06  
**Stage:** 6 (Start Gate — documentation only)  
**Status:** DONE  
**Module:** documentation only (no production code)

### Summary

Зафиксировано финальное архитектурное решение **Material Responsibility Decision (ADR-032)**: в текущей версии TMP **отсутствует отдельный Material Master**.

### Decision

**Order Management:**

- владеет заказом, позициями, редакциями, спецификациями;
- определяет, **какие материалы нужны для изготовления изделия** (строки ACTIVE Specification);
- **не** владеет складскими остатками, партиями, ячейками, движениями, резервированием.

**Warehouse:**

- использует материалы из Order Management Specification (MaterialReference);
- хранит только **складское состояние**;
- **не** создаёт/изменяет спецификацию; **не** выполняет Material Mapping.

**Material Mapping:**

- пользовательское сопоставление внешних наименований (СуперОкна) → нормализованные значения TMP;
- отсутствие mapping **не блокирует** импорт;
- Production использует нормализованные данные.

**Production interaction:**

```text
OM: ACTIVE Order → Item → Revision → Specification
  → Production: query orders/items/spec → расчёт потребности
  → Warehouse: availability / reservation
```

### Updated documents

- `TMP-Architecture-Decisions.md` — ADR-032 (v1.10)
- `Warehouse-Specification.md` — v1.2 (§10, Rule 6, Public API)
- `Order-Management-Specification.md` — v1.8 (§20, §28)
- `STAGE-6-WAREHOUSE.md` — Start Gate + readiness
- `STATUS.md`, `WORK-QUEUE.md`, `TMP-PROJECT-READINESS-REVIEW.md`

### Boundaries

- Production code **не изменялся**
- Новые модули **не создавались**
- Stage 6 implementation = **NOT STARTED**
- Git — только пользователь

---

## Stage 5 — FINAL CLOSURE

**Date:** 2026-08-06  
**Stage:** 5  
**Status:** DONE  
**Module:** documentation only (no production code)

### Summary

Formal closure of Stage 5 — Order Management after successful completion of `STAGE5-057` and `STAGE5-058` (including Manual GUI Smoke PASS).

### Delivered functionality (Stage 5)

**Order Management**

- создание заказа;
- создание позиций заказа;
- управление редакциями;
- управление спецификациями;
- approve lifecycle.

**STAGE5-058 — Imported Order Lifecycle**

Импортированные заказы из расчётной программы — доверенные. После успешного импорта:

| Aggregate | Status |
|---|---|
| Order | ACTIVE |
| Order Item | ACTIVE |
| Revision | ACTIVE |
| Specification | ACTIVE |

ACTIVE одинаков независимо от способа создания. Не существует: `ImportMetadata`, `sourceType`, `creationSource`, отдельного import lifecycle.

**STXT Import Contract**

Один файл → несколько заказов. Структура: Order → Item → Specification. Multi-order / multi-item / spec per item.

Импорт полей: номер/даты/клиент; изделие (номер, код, наименование, кол-во); спецификация (артикул, наименование, цвет, размер, единица, кол-во позиции).

Количество изделий = копии; количество позиции = норма на 1 копию; TMP **не** умножает. `@`/`#` не импортируются; `кв.м.` → размер в наименование, единица `шт.`, количество сохраняется.

**Lifecycle**

- Manual: DRAFT → APPROVED → ACTIVE  
- Import: STXT → Import Core → Approve flow → ACTIVE  
- ACTIVE: read-only; изменения только через Revision  

**Not started:** Warehouse, Production, Cutting Optimization, Analytics, Stage 6.

### Code / schema

Не изменялись в рамках Final Closure.

### Git

Git-команды агентом не выполнялись.

---

## STAGE5-058 — FIX REQUIRED: Final STXT Parser Contract Mismatch

**Date:** 2026-08-05  
**Stage:** 5 (post-closure)  
**Status:** DONE  
**Module:** `tmp-order-management` (`StxtBlockParser` / adapter)

### Defect

Ручная проверка `docs/Input/Итоговый формат выгрузки.stxt` → Preview fail. Причины в block parser:

1. SuperOkna повторяет `Номер заказа:` перед **каждым** изделием — parser отклонял как duplicate.
2. `Наименование изделия: 1` + multiline body не собиралось (value после `:` блокировал continuation).
3. SPEC требовал/валидировал табличный header layout; не-mm size и пустое наименование роняли строки.

### Fix (`StxtBlockParser`)

- Merge сегментов с одинаковым `orderNumber` (конфликт только при расхождении header fields).
- Всегда собирать multiline name после label; normalize убирает leading number-only line.
- SPEC: фиксированный порядок 6 полей; header опционален; non-mm size → `length=null`; пустое name → fallback на артикул.
- Сохранены: multi-order, multi-item, qty = норма на 1 изд., `@`/`#` skip, `кв.м.` → `шт.`.

Fixture: `stxt/final-export-format.stxt`. Preview: orders/positions/product qty > 0, errors = 0.

Не добавлено: ImportMetadata, sourceType persistence, creationSource. Stage 6 не начат. Git не выполнялся.

### Verification

- `StxtFileAdapterTest` (20) — PASS  
- `StxtImportPreviewIntegrationTest` (2, incl. final export preview) — PASS

---

## STAGE5-058 — FIX REQUIRED: remove direct ACTIVE; Document approve/activate only

**Date:** 2026-08-05  
**Stage:** 5 (post-closure)  
**Status:** DONE  
**Module:** `tmp-order-management`, `tmp-ui-shell`

### Defect

Import мог опираться на доменные shortcuts `CustomerOrder.activateFromImport` / `OrderItem.activateDraftRevisionForImport` (`DRAFT → ACTIVE` в обход approve flow). Placeholders не отклонялись на уровне Import Validator.

### Fix

1. Import confirm (одна TX): create docs → `ORDER_ITEM_REVISION_APPROVE` → `ORDER_APPROVE` under `ImportApproveGate` (ADR-031: client + ≥1 ACTIVE item; без полного ADR-030 set — STXT не содержит direction/currency/contract/site) → `ORDER_ACTIVATE`.
2. Удалены `activateFromImport` / `activateDraftRevisionForImport`.
3. `OrderImportValidator`: Final STXT поля + запрет placeholders (`UNKNOWN`, `N/A`, …) → импорт отклоняется.
4. E2E: STXT → Adapter → Core → Approve → ACTIVE Order/Item/Revision/Specification.
5. UI: ACTIVE Order / Position / Specification — только просмотр (тесты).
6. ACTIVE одинаков независимо от источника. ImportMetadata не создана. Stage 6 не начат. Git не выполнялся.

### Verification

- Domain/import/STXT unit + `OrderIntakeStxtEndToEndIT` / `OrderImportCoreIT` — PASS
- UI read-only ViewModel tests + `OrderImportViewModelTest` + architecture — PASS

---

## STAGE5-058 — FIX REQUIRED: ACTIVE via Document approve/activate flow

**Date:** 2026-08-05  
**Stage:** 5 (post-closure)  
**Status:** DONE (superseded domain-shortcut removal by FIX above)  
**Module:** `tmp-order-management`, Flyway V14

### Defect

После Final STXT Contract confirm всё ещё мог оставлять долгоживущий DRAFT / опирался на прямой `activateFromImport` / `activateDraftRevisionForImport` в обход Document Engine.

### Fix

Import confirm (одна TX на файл):

1. `ORDER_CREATE` + `ORDER_ITEM_CREATE` + `ORDER_ITEM_REVISION_UPDATE`
2. `ORDER_ITEM_REVISION_APPROVE` → Item / Revision / Specification **ACTIVE**
3. `ORDER_APPROVE` under `ImportApproveGate` → `ApproveOrderUseCase.executeForImport` (client + ≥1 ACTIVE item; без полного ADR-030 commercial set)
4. `ORDER_ACTIVATE` → Order **ACTIVE**

Flyway `V14__order_activate_document_type.sql` — `ORDER_ACTIVATE` в CHECK payload/processing.

ACTIVE imported = ACTIVE manual (UI read-only по статусу). ImportMetadata не создана. Stage 6 не начат. Git не выполнялся.

### Verification

- `DefaultOrderImportServiceTest`, `OrderImportCoreIT`, `OrderIntakeStxtEndToEndIT` (incl. multi-order ACTIVE) — PASS
- STXT adapter unit tests — PASS

---

## STAGE5-058 — Final STXT Contract + Imported Order Lifecycle (completion)

**Date:** 2026-08-05  
**Stage:** 5 (post-closure)  
**Status:** DONE (superseded lifecycle portion by FIX above)  
**Module:** `tmp-order-management`, `tmp-ui-shell`

### Result

Доведён STAGE5-058 до Final STXT Contract поверх уже реализованного uniform ACTIVE (ADR-031):

1. **STXT Contract:** `ORDER BLOCK → ITEM BLOCK → SPECIFICATION LINES`; несколько заказов в одном файле.
2. **Parsing:** «Номер заказа:» / «Изделие:»; поля заказа (номер, дата, готовность, клиент); изделия (`externalPositionNumber`, `productCode`, `name`, `quantity`); спецификация с `unitOfMeasure`.
3. **Нормализация имени:** collapse whitespace; удаление первой number-only строки.
4. **Исключения:** строки `@` / `#` не импортируются.
5. **`кв.м.`:** размер → в наименование; `length=null`; единица `шт.`; quantity без изменений.
6. **quantity:** норма расхода на 1 изделие; без умножения на item quantity.
7. **Validation:** ACTIVE import требует order number/date/client; item productCode/name/quantity; spec code/name/unit/quantity; placeholders запрещены.
8. **Lifecycle:** STXT → Import Core → ACTIVE Order/Item/Revision/Specification; ACTIVE одинаков независимо от источника; дубль только `orderNumber`.
9. **Не создано:** ImportMetadata / sourceType persistence / checksum storage / Stage 6.

### Key code

- `StxtBlockParser`, `StxtFileAdapter` (multi-batch);
- `OrderImportBatch`/`Position`/`SpecificationLine` (новые поля);
- `DefaultOrderImportService.preview(List)` + atomic multi-order confirm;
- fixtures `stxt/sample-utf8.stxt`, `stxt/multi-order.stxt`.

### Verification

- `StxtFileAdapterTest`, `StxtImportPreviewIntegrationTest`, `DefaultOrderImportServiceTest` — PASS
- UI `OrderImportViewModelTest`, `OrderImportControllerFxTest` — PASS
- `OrderIntakeStxtEndToEndIT`, `OrderImportCoreIT` — PASS (Docker)
- `Stage5OrderManagementArchitectureTest` — PASS
- Git-команды агентом **не** выполнялись.

---

## STAGE5-058 — Imported Order Lifecycle Rules (implementation)

**Date:** 2026-08-03  
**Stage:** 5 (post-closure)  
**Status:** DONE  
**Module:** `tmp-order-management`, `tmp-ui-shell`, Flyway V13

### Result

Реализован ADR-031 final:

- Import confirm → Order / Item / Revision / Specification **ACTIVE** (одна TX);
- создание через Document Engine (`ORDER_CREATE`, `ORDER_ITEM_CREATE`, `ORDER_ITEM_REVISION_UPDATE`) + trusted domain activation (без commercial-gated `ORDER_APPROVE` / `REVISION_APPROVE` и без фиктивных коммерческих данных);
- ручной путь: `DRAFT → APPROVED → ACTIVE` (`ORDER_ACTIVATE`); UI «Утвердить» проводит APPROVE затем ACTIVATE;
- `RevisionStatus.APPROVED` → `ACTIVE`;
- удалены ImportMetadata / checksum duplicate protection; дубль только `orderNumber`;
- UI: direct-edit read-only для любого ACTIVE (единый режим);
- Flyway `V13__order_lifecycle_active.sql`;
- ACTIVE одинаков независимо от способа создания.

Stage 6 = NOT STARTED. Git-команды агентом не выполнялись.

### Verification

- `mvn -pl tmp-order-management -am test` — PASS
- `mvn -pl tmp-ui-shell -am test` — PASS
- `mvn -pl tmp-architecture-tests -am test -Dtest=Stage5OrderManagementArchitectureTest` — PASS
- `mvn -pl tmp-order-management,tmp-architecture-tests -am verify` — PASS

---

## STAGE5-058 — Final Architecture Decision (documentary)

**Date:** 2026-08-03  
**Stage:** 5 (post-closure)  
**Status:** READY (implementation NOT STARTED)  
**Module:** none (documentation only)

### Result

Зафиксировано **финальное** ADR-031:

- uniform ACTIVE (нет различий по способу создания);
- запрет OrderOrigin / ImportMetadata / checksum duplicate protection;
- дубли только `orderNumber`;
- IMPORT operation → Order/Item/Revision/Specification ACTIVE;
- изменение ACTIVE только через Revision;
- Spec **v1.5**; ADR **v1.8**; Manifest / WORK-QUEUE / CONTEXT-MAP / STATUS обновлены.

Production code **не изменялся**. Stage 6 = NOT STARTED.

### Supersedes

Interim ADR-031 design with `OrderOrigin` and imported-only revision ban (ADR doc v1.7 / Spec v1.4).

---

## STAGE5-058 — Documentary preparation (Imported Order Lifecycle Rules)

**Date:** 2026-08-03  
**Stage:** 5 (post-closure)  
**Status:** READY (implementation NOT STARTED)  
**Module:** none (documentation / ADR / Spec / queue only)

### Result

Подготовлена задача и согласованный дизайн lifecycle для доверенного импорта из расчётной программы.

- Added **ADR-031** (ADR document **v1.7**).
- Order Management Specification → **v1.4** (§8/§9/§13/§27).
- Stage 5 Manifest updated (§4, §7, §17, §21–22).
- WORK-QUEUE: `STAGE5-058` = **READY**.
- STATUS / CONTEXT-MAP синхронизированы.
- Production code / Import Core / UI / Flyway **не изменялись**.
- Stage 6 = **NOT STARTED**.

### Next

Взять `STAGE5-058` в `IN_PROGRESS` только при явном продолжении реализации (не в этой documentary-подготовке).

---

## STAGE5-057 — Manual GUI Smoke Completion

**Date:** 2026-08-03  
**Stage:** 5  
**Status:** DONE  
**Module:** none (documentary closure only)

### Result

User Manual GUI Smoke PASS on packaged TMP (Windows / PostgreSQL `tmp-stage5-pg` / DB `tmp_gui_stage5` / admin).

- Import STXT `Альпы 25062026.stxt` → order `26062891` (5 positions, 24 products, 20 spec lines).
- Persistence after restart verified.
- Quantity rule verified: `productQuantity` and `lineQuantity` are independent (no `8 × 16`).
- `STAGE5-057-SMOKE-001` = **FIXED AND VERIFIED** (commercial draft / specification save after import).

### Status transitions

- `STAGE5-057` = DONE  
- Stage 5 = DONE  
- Stage 6 = NOT STARTED  
- STAGE5-058 not created / not implemented  

**Next planned improvement (not a work-queue task):** Imported Order Lifecycle Rules.

### Unchanged in this closure

No production code, lifecycle, Import Core, STXT, UI, Document Engine, Flyway. Git not executed.

---

## STAGE5-057 — FIX REQUIRED — STAGE5-057-SMOKE-001 (cycle 2)

**Date:** 2026-08-03  
**Stage:** 5  
**Status:** IN_PROGRESS (`STAGE5-057-SMOKE-001` fixed in source + rebuilt `TMP.exe`; awaiting user manual confirmation)  
**Modules:** `tmp-ui-shell`, `tmp-order-management` (application UI only)

### Real root cause

Russian message «Количество изделий должно быть целым числом больше нуля» exists only in:

1. `ProductQuantityUiValidation` (UI) — **actual source on commercial draft save**
2. `OrderImportValidator` (Import Core) — Integer `productQuantity <= 0`; **not on this path**

Flow for imported DRAFT commercial save:

`OrderItemEditorViewModel.saveCommercialDraft()` → `OrderItemDocumentUiService.saveItemUpdateDraft()` → commercial payload only (`OrderItemCommercialDraft` has **no** quantity) → **does not** call `OrderedQuantity`.

Bug: UI still validated TextField quantity on ITEM_UPDATE even though quantity is not in the update payload. Field text came from NUMERIC(19,6) as `8.000000` via `BigDecimal.toPlainString()`. Pattern `8` PASS / `8.0` FAIL matches old digit-only UI check in the **packaged binary** the user was running.

### Why previous fixes did not appear to work

Source was already corrected, but manual smoke used stale `dist/jpackage/TMP/TMP.exe` (jpackage only runs at `pre-integration-test`). Field still showing `8.000000` proves old UI binary. Cycle 2 also hardens application query projection so snapshot quantity is scale-0 (`"8"`) before UI binding.

### Fix (cycle 2)

1. **Application query:** `UiProductQuantityNormalization` — editor/spec snapshots expose wholes at scale 0 (`8.000000` → `8`).
2. **Application parse:** `DefaultOrderItemDocumentUiService.parseQuantity` uses the same normalization before `OrderedQuantity.of`.
3. **UI commercial UPDATE:** never blocks on quantity; best-effort display normalize to `"8"`.
4. **UI CREATE/revision/spec:** normalize `8`/`8.0`/`8.000000` → `"8"`.
5. **Rebuilt** `dist/jpackage/TMP/TMP.exe` (2026-08-03 14:46).

### Tests

- `Stage5Smoke001ProductQuantityDiagnosticTest` — pins message source; imported DRAFT save without quantity change; `8`/`8.0`/`8.000000` same commercial path
- `UiProductQuantityNormalizationTest`
- Editor/spec query tests for scaled import quantity
- Existing ViewModel / ProductQuantityUiValidation coverage

Focused automated: **98 PASS** (26 OM + 72 UI).

### Unchanged

Import Core, STXT Adapter, Domain semantics, Persistence, Flyway — not modified. Git not executed. Stage 6 not started.

### Next

User retests rebuilt TMP.exe. **Do not** set STAGE5-057 = DONE until user confirms.

---

## STAGE5-057 — FIX REQUIRED — STAGE5-057-SMOKE-001 (additional)

**Date:** 2026-08-03  
**Stage:** 5  
**Status:** IN_PROGRESS (automated fix applied; awaiting user manual confirmation)  
**Modules:** `tmp-ui-shell`, `tmp-order-management` (application UI only)

### Smoke defect (retest)

**ID:** `STAGE5-057-SMOKE-001`  
**Symptom (still after first fix):** Import STXT → order `26062891` → open item → change comment → «Сохранить коммерческий черновик» → «Количество изделий должно быть целым числом больше нуля». Field `8.000000` / `8.0` FAIL; `8` PASS.  
**Cause (full path):**

1. TextField bound to `orderedQuantity` loaded via `BigDecimal.toPlainString()` → keeps NUMERIC scale (`8.000000`).
2. `OrderItemEditorViewModel.saveCommercialDraft()` for existing DRAFT always called `ProductQuantityUiValidation` **even though** `saveItemUpdateDraft` does **not** send quantity (commercial update payload has no `orderedQuantity`).
3. First fix changed mathematical whole check, but commercial-draft path still blocked on display leftovers; outbound paths that do send quantity needed canonical normalization.

Validators with the same Russian message:

| Location | Used on commercial draft save? |
|---|---|
| `ProductQuantityUiValidation` (UI) | Yes (was incorrectly required for ITEM_UPDATE) |
| `OrderImportValidator` (Import Core) | No (Integer `productQuantity`; unchanged) |

### Fix

1. **Commercial draft UPDATE:** do not validate/send quantity (quantity belongs to REVISION_UPDATE).
2. **Display:** `ProductQuantityUiValidation.formatForDisplay` → show `8` for mathematically whole values.
3. **CREATE / revision / specification save:** `requireValidNormalizedProductQuantity` → canonical `"8"` for `8` / `8.0` / `8.000000`.
4. **Whole check:** fractional part via `remainder(ONE).signum() == 0` (not scale).
5. **Application:** `DefaultOrderItemDocumentUiService.parseQuantity` normalizes mathematical wholes to scale 0 before `OrderedQuantity.of`.

### Tests

- `ProductQuantityUiValidationTest` — accept/normalize `8`/`8.0`/`8.000000`; reject `8.5`/`0`/`-1`
- `OrderItemEditorViewModelTest` — imported DRAFT comment save; scaled leftovers ignored on commercial save; CREATE accepts scaled wholes
- `OrderItemSpecificationEditorViewModelTest` — imported spec save; normalize scaled wholes
- `DefaultOrderItemDocumentUiServiceTest.saveItemCreateDraftAcceptsScaledWholeProductQuantity`

Focused: `mvn -pl tmp-ui-shell,tmp-order-management -am test` (selected) — **80 tests PASS** (13 + 67).

### Unchanged

Import Core, STXT Adapter, Domain, Persistence, Flyway — not modified. Stage 6 not started. Git not executed.

### Next

STAGE5-057 stays **IN_PROGRESS** until user reconfirms manual smoke. Stage 5 not closed.

---

## STAGE5-057 — FIX REQUIRED — STAGE5-057-SMOKE-001

**Date:** 2026-08-03  
**Stage:** 5  
**Status:** SUPERSEDED by additional FIX (display/commercial-path) — see entry above  
**Module:** `tmp-ui-shell`

### Smoke defect

**ID:** `STAGE5-057-SMOKE-001`  
**Symptom:** After STXT import, `productQuantity` shown as `8.000000`; saving DRAFT item/spec failed with «Количество изделий должно быть целым числом больше нуля»; typing `8` worked.  
**Cause:** `ProductQuantityUiValidation` rejected any string with a decimal point via regex `-?\d+` / scale checks instead of mathematical whole-number integrity.

### Fix

`ProductQuantityUiValidation`: parse `BigDecimal`, accept when `signum() > 0` and `stripTrailingZeros().scale() <= 0` (fractional part is zero). Still reject null/blank, non-numeric, `<= 0`, and true fractions (`8.5`).

### Tests

- `ProductQuantityUiValidationTest` — `8` / `8.0` / `8.000000` PASS; `8.5` / `0` / `-1` FAIL  
- `OrderItemEditorViewModelTest.importedDraftWithScaledProductQuantityAllowsCommentSave`  
- `OrderItemSpecificationEditorViewModelTest.importedSpecificationWithScaledProductQuantityCanSaveUnchanged`

### Unchanged

Import Core, STXT Adapter, Domain, Persistence, Flyway — not modified. Stage 6 not started. Git not executed.

### Next

Stage 5 remains IN_PROGRESS (explicitly not closed by this FIX REQUIRED task).

---

## STAGE5-057 — Manual GUI Smoke (Order Intake) — preparation

**Date:** 2026-08-03  
**Stage:** 5  
**Status:** IN_PROGRESS — WAITING_USER_CONFIRMATION  
**Module:** none (manual)

### Result

Подготовлен ручной Order Intake smoke checklist. Код не изменялся. `TMP.exe` агентом не запускался.

Packaged app: `dist/jpackage/TMP/TMP.exe` (present).  
STXT fixture: `tmp-order-management/src/test/resources/stxt/sample-utf8.stxt` (existing; order `26062891`).

Stage 5 remains IN_PROGRESS until user PASS. Stage 6 not started. Git not executed.

### Next

User executes checklist and reports PASS or FAIL (with step/expected/actual if FAIL).

---

## STAGE5-056 — Automated Verification (FIX REQUIRED pass)

**Date:** 2026-08-03  
**Stage:** 5  
**Status:** DONE  
**Module:** multi-module verification (tests + control docs only)

### Result

FIX REQUIRED pass for STAGE5-056 — усиление automated verification без новой бизнес-функциональности.

Добавлено:

1. **Verification matrix** (Feature → Test Class → Scenario → Result) в VERIFICATION-LOG.
2. **End-to-end IT** `OrderIntakeStxtEndToEndIT`: STXT fixture → adapter → batch → preview → plan → confirm → DRAFT order/item/revision/spec → Query API + UI editor reads (`productCode`/`name` null; `lineQuantity` not multiplied).
3. **PackagingSmokeIT** расширен: TMP.exe start + login window title; admin login + Import GUI open via package-profile Spring/JavaFX; без импорта и без мутации orders.
4. **Flyway bootstrap IT** `OrderIntakeFlywayBootstrapIT`: clean V1→V12 + app start; upgrade V11→V12 с сохранением данных + app start.
5. **Regression matrix** в VERIFICATION-LOG.

Production (Import Core / STXT / Import GUI / Domain / Persistence) не изменялись. `STAGE5-057` not started. Git not executed.

### Verification

- `mvn test` — PASS
- `mvn verify` — PASS
- `mvn -Ppackage clean verify` — PASS; PackagingSmokeIT = 2/0/0/0

### Next

`STAGE5-057` — Manual GUI Smoke (Order Intake) — NOT STARTED.

---

## STAGE5-056 — Automated Verification

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE (superseded by FIX REQUIRED pass 2026-08-03)  
**Module:** multi-module verification (Order Intake gate)

### Result

Полный автоматический gate блока Order Intake + regression Stage 5 без новой функциональности.

Проверено существующими тестами и architecture rules:

- Order Contracts / incomplete DRAFT;
- Manual Entry UI (create/edit DRAFT, APPROVED readonly, validation, Russian messages);
- Import Core (preview / prepared plan / confirm / duplicate / conflict / rollback / metadata);
- STXT Adapter (UTF-8 / UTF-8 BOM / Windows-1251 / headers / delimiter / mm / decimal comma);
- Import GUI (FileChooser / preview / counters / errors / warnings / confirm / duplicate / conflict).

Architecture (добавлены явные правила STAGE5-056):

- Import Core: no JavaFX / STXT parser / Firebird / UI (уже было);
- STXT Adapter: no UI / persistence / JDBC / Document Engine internals (**новые**);
- UI shell: no Repository / no JDBC (**новые**).

Packaging: `mvn -Ppackage clean verify` — PASS; `PackagingSmokeIT` = 1/0/0/0; `dist/jpackage/TMP/TMP.exe` present.

Defects found: none. Production code unchanged. `STAGE5-057` not started. Git not executed.

### Verification

- `mvn test` — PASS (EXIT_CODE=0)
- `mvn verify` — PASS (EXIT_CODE=0)
- `mvn -Ppackage clean verify` — PASS (EXIT_CODE=0); PackagingSmokeIT executed, not skipped

### Counts (`-Ppackage clean verify` surefire/failsafe XML)

- Surefire (unit): 977 / 0 failures / 0 errors / 0 skipped
- Failsafe (integration): 117 / 0 / 0 / 0
- Architecture module surefire: 60 reported (+ Stage4 ArchUnit engine overlap in console)
- Stage5 ArchRules: 31 (incl. 4 Order Intake boundary rules added)
- FX tests (ui-shell name match): 37
- PackagingSmokeIT: 1

### Next

`STAGE5-057` — Manual GUI Smoke (Order Intake) — NOT STARTED (user only).

---

## STAGE5-055 — Import GUI (FIX REQUIRED pass)

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE  
**Module:** `tmp-ui-shell`


### Result

Fix pass for Import GUI review remarks:

1. Confirm errors: duplicate / order conflict / validation show exact Russian user messages (`OrderImportDuplicateException.USER_MESSAGE`, `OrderImportConflictException.USER_MESSAGE`, preview problem text). No generic «Ошибка импорта», no SQL/stack.
2. Prepared plan gate: Import enabled only when `preparedPlan` present, successful preview without ERROR, and errors empty.
3. FileChooser Cancel: no adapter call, preview unchanged, no error.
4. Preview counters: explicit «Ошибки: N» / «Предупреждения: N» (including 0).
5. Success result format: «Заказ {n} успешно импортирован… Создано позиций… Строк спецификации…».

Import Core and STXT Adapter unchanged. `STAGE5-056` not started. Git not executed.

### Verification

- `mvn -q -pl tmp-ui-shell -am test` — PASS
- `mvn -q -pl tmp-ui-shell -am verify` — PASS

### Next

`STAGE5-056` — Automated Verification — NOT STARTED.

---

## STAGE5-055 — Import GUI

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE  
**Module:** `tmp-ui-shell` (+ bootstrap wiring; minimal public STXT UI contract)

### Result

JavaFX Import GUI (Spec §27.6):

- Screen `order.view.order-import`: FileChooser → `StxtOrderFileParser` → `OrderImportService.preview` → display → confirm via `PreparedOrderImportPlan`;
- UI: file name, preview counts (order number / positions / products / spec lines), errors table, warnings table, buttons Проверить / Импортировать / Отмена;
- ERROR disables Import; WARNING-only allows Import; Cancel does not persist;
- Russian user messages; no SQL / stack trace / Java exception text to the user;
- read-only after import (no edit of imported data on this screen);
- Order list button «Импорт заказа» (permission `order.order.create`);
- Public UI contract: `StxtOrderFileParser` + `OrderImportFileParseResult` in `com.tmp.order.api.imports`; facade `DefaultStxtOrderFileParser` delegates to unchanged `StxtFileAdapter`.

Import Core service/model and STXT adapter internals unchanged. Firebird absent. `STAGE5-056` not started. Git not executed.

### Files changed (production)

- `OrderImportViewModel` / `OrderImportController` / `OrderImportScreen.fxml`
- `UiShellScreens` (import screen id/FXML)
- Order list: Import button + navigation callback
- `UiShellAutoConfiguration` screen registration / navigation bridge
- `StxtOrderFileParser`, `OrderImportFileParseResult`, `DefaultStxtOrderFileParser` (+ AutoConfiguration bean)
- FX/ViewModel tests + bootstrap wiring test
- control docs

### Verification

- `OrderImportViewModelTest` — PASS
- `OrderImportControllerFxTest` — PASS
- `tmp-ui-shell` SpotBugs — PASS
- `Stage5OrderManagementArchitectureTest` — PASS
- `DesktopBootstrapWiringTest` — PASS

### Next

`STAGE5-056` — Automated Verification — NOT STARTED.

---

## STAGE5-054 — STXT File Adapter

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE  
**Module:** `tmp-order-management`

### Result

STXT file adapter (Spec §27.5 / ADR-029):

- `StxtFileAdapter` + `StxtParseResult` + encoding detector + table parser under `com.tmp.order.application.imports.stxt`;
- encodings: Windows-1251, UTF-8, UTF-8 BOM; clear encoding failure message;
- structural delimiter `" / "` (slash inside material names preserved);
- header alias mapping; missing required header → ERROR; unknown header → WARNING;
- size normalization (`мм` / decimal comma) → `lengthMm`; blank color/size → null;
- one order number per file; `lineQuantity` not multiplied by `productQuantity`;
- SHA-256 `contentChecksum`; `sourceReference` = file name only;
- no DB writes, Document post, Confirm, JavaFX or Firebird;
- Spring bean wiring in `OrderManagementAutoConfiguration`;
- ArchUnit Import Core rule excludes `stxt` package from self-ban.

Import Core model/services unchanged. `STAGE5-055` not started. Git not executed.

### Files changed (production)

- `com.tmp.order.application.imports.stxt.StxtFileAdapter`
- `com.tmp.order.application.imports.stxt.StxtParseResult`
- `com.tmp.order.application.imports.stxt.StxtEncodingDetector`
- `com.tmp.order.application.imports.stxt.StxtTableParser`
- `OrderManagementAutoConfiguration` (bean)
- ArchUnit Stage 5 import rule (exclude stxt subject)
- unit/IT + fixture `stxt/sample-utf8.stxt`
- control docs

### Verification

- `mvn -q -pl tmp-order-management -am verify` — PASS
- `StxtFileAdapterTest` — PASS (15)
- `StxtImportPreviewIntegrationTest` — PASS (1)
- `Stage5OrderManagementArchitectureTest` — PASS

### Next

`STAGE5-055` — Import GUI — NOT STARTED.

---

## STAGE5-053 — Import Core (FIX REQUIRED pass)

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE  
**Module:** `tmp-order-management`

### Result

Fix pass after STAGE5-053 review:

1. **Opaque PreparedPlan:** `PreparedOrderImportPlan` — public interface only; `DefaultPreparedOrderImportPlan` with package-private constructor; confirm rejects non-core implementations. No public factory.
2. **Confirm races:** IT for duplicate checksum after preview, orderNumber conflict after preview, unique-constraint TOCTOU → controlled duplicate + full TX rollback.
3. **Rollback completeness:** failure path asserts zero payloads, processing records, documents delta, aggregates, metadata.
4. **Validation:** `sourceType` ≤ 64, `contentChecksum` ≤ 128, `sourceReference` ≤ 512; absolute paths rejected before DB.

`STAGE5-054` not started. Git not executed.

### Verification

- `mvn -q -pl tmp-order-management -am test` — PASS
- `mvn -q -pl tmp-order-management -am verify` — PASS
- Stage 5 architecture — PASS

### Next

`STAGE5-054` — STXT File Adapter — NOT STARTED.

---

## STAGE5-053 — Import Core

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE  
**Module:** `tmp-order-management`

### Result

Source-neutral Import Core (ADR-029 / Spec §27.4–27.7):

- immutable `OrderImportBatch` / position / specification line model without STXT/JavaFX/Firebird;
- preview with counts, errors/warnings, `canConfirm`, immutable `PreparedOrderImportPlan` (only when no errors);
- preview is read-only (duplicate/conflict checks only; no documents/metadata);
- atomic confirm via Document Engine flows `ORDER_CREATE` → `ORDER_ITEM_CREATE` → `ORDER_ITEM_REVISION_UPDATE` in one `TransactionTemplate`;
- incomplete commercial DRAFT without placeholders (ADR-030);
- controlled conflict / duplicate messages; unique `(source_type, content_checksum)` metadata;
- Flyway `V12__order_import_metadata.sql`; actor via `AuthenticationService.currentSession().userId()`.

`STAGE5-054` not started. Stage 6 not started. Git not executed.

### Files changed (production)

- `com.tmp.order.api.imports.*` — public import API;
- `com.tmp.order.application.imports.*` — validator, service, metadata port/model;
- `JdbcOrderImportMetadataRepository`;
- `V12__order_import_metadata.sql`;
- `OrderManagementAutoConfiguration` wiring;
- unit/IT/Flyway tests; Stage 5 ArchUnit import rules;
- control docs (STATUS, WORK-QUEUE, CONTEXT-MAP, STAGE-5, IMPLEMENTATION-LOG, VERIFICATION-LOG).

### Verification

- `mvn -q -pl tmp-order-management -am test` — PASS (308 unit);
- `mvn -q -pl tmp-order-management -am verify` — PASS (308 unit + 71 IT);
- `mvn -q -pl tmp-architecture-tests -am test -Dtest=Stage5OrderManagementArchitectureTest` — PASS.

### Next

`STAGE5-054` — STXT File Adapter — NOT STARTED.

---

## STAGE 5 — Documentation consistency fix (Order Intake planning)

**Date:** 2026-07-30  
**Stage:** 5  
**Status:** DONE (documentation only)

### Result

Documentation consistency correction only:

- corrected Stage 5 task sequencing (`STAGE5-050` sole `IN_PROGRESS`; `STAGE5-051` only after `STAGE5-050 = DONE`);
- `STAGE5-050` redefined as Core Order Management manual smoke;
- Stage 5 closure moved after `STAGE5-057`;
- approved incomplete DRAFT commercial-data rule (ADR-030); blocker resolved;
- corrected Stage 0–9 sequence (removed Stage 10/11 as approved development stages from STATUS/CONTEXT-MAP);
- replaced misleading Stage 5 percentage with split status;
- updated Context Map for Order Intake tasks;
- synchronized specification, ADR and migration references (latest OM migration = V8; next = V9).

Production functionality was **not** implemented.

### Files modified

- `docs/TMP/.../Order-Management-Specification.md`
- `docs/TMP/.../05-ADR/TMP-Architecture-Decisions.md`
- `docs/development-control/stages/STAGE-5-ORDER-MANAGEMENT.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/CONTEXT-MAP.md`
- `docs/development-control/BLOCKERS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next

User completes STAGE5-050 GUI retest-2. Do not start STAGE5-051 until STAGE5-050 = DONE.

---

## STAGE5-050 — Manual packaged GUI smoke (user verification complete)

**Date:** 2026-07-30  
**Stage:** 5  
**Status:** DONE — PASS (user-confirmed)

### Result

User completed manual packaged GUI smoke for Core Order Management. Documentation-only closure; no production code changes.

### Verification summary

| Check | Result |
|---|---|
| PostgreSQL Docker (`tmp-stage5-pg`, volume preserved) | PASS |
| DB port `55432` (54325 unavailable — Windows reserved range) | PASS |
| Database `tmp_gui_stage5` | PASS |
| `TMP.exe` launch + JDBC connect | PASS |
| Admin login | PASS |
| Russian permission display names + technical IDs in parentheses | PASS |
| Role selection preserved on checkbox ON (`sample.technical.view`) | PASS |
| Role selection preserved on checkbox OFF | PASS |
| Order persistence after restart (4 orders) | PASS |
| Clean application shutdown | PASS |

### Orders confirmed after restart

- TEST-ITEM-CANCEL — DRAFT
- TEST-CANCEL-001 — CANCELLED
- TEST-002 — APPROVED
- TEST-001 — APPROVED

### Files modified

- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next

`STAGE5-051` remains NOT STARTED. Await user authorization before Order Intake implementation.

---

# Entry Template

## `<TASK-ID>` ? `<title>`

**Date:** YYYY-MM-DD  
**Stage:** ...  
**Status:** DONE | BLOCKED | FAILED

### Result

...

### Files created

- ...

### Files modified

- ...

### Tests added or changed

- ...

### Verification

| Check | Result |
|---|---|
| ... | PASSED/FAILED |

### Architecture review

- ...

### Documentation updated

- ...

### Next task

`<TASK-ID>`

## `CONTROL-001` ? `Validate development sources`

**Date:** 2026-07-17  
**Stage:** Control  
**Status:** DONE

### Result

?????? ?????? ???????????? ??????????, ???????????? ??????? ????????? ?????????? ? ?????????? ????? ?????????? ?? ???????/??????????.

### Files created

- None.

### Files modified

- `docs/development-control/CONTEXT-MAP.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| Manual document path and status validation | PASSED |

### Architecture review

- ?????????? ????? Constitution, ADR, Architecture Overview, Database Specification, Development Guide, Code Quality Standards, Master Implementation Plan ? Cursor AI Guide ?? ?????????? ?? ?????? ???????????? ?????? ? ???????? Accepted.

### Documentation updated

- `docs/development-control/CONTEXT-MAP.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Next task

`CONTROL-002`

## `CONTROL-002` ? `Build Stage 0 task queue`

**Date:** 2026-07-17  
**Stage:** Control  
**Status:** DONE

### Result

???????????? ???????????? Stage 0 ? ?????????? ?????? ? ???????????? scope, ?????????????, ?????????? ??????? ? ????????? ????????; ?????? ?????? Stage 0 ??????????? ? READY.

### Files created

- None.

### Files modified

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| Task-size and Stage 0 manifest conformity review | PASSED |

### Architecture review

- ??????? ?? ???????? ??????? Stage 0 (??? ???????? ?????? ? ??????-???????), ?????? ????????????? ?? ??????????? ?????????.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`

### Next task

`STAGE0-001`

## `STAGE0-001` ? `Bootstrap repository reactor and parent pom`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

?????? ??????? Maven reactor (`tmp-parent`) ? ?????? ??????? Stage 0. ????? ?????????????? ???????????? ????? ??????????? `validate` ???????????? ????? ????????? portable Maven.

### Files created

- `pom.xml`
- `tmp-bootstrap-app/pom.xml`
- `tmp-ui-shell/pom.xml`
- `tmp-infra-db/pom.xml`
- `tmp-architecture-tests/pom.xml`

### Files modified

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests validate` | PASSED |

### Architecture review

- ????????? ?????????? ??????????? foundation ????? ? ?? ?????? ??????-??????.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-002`

## `STAGE0-002` ? `Configure dependency and plugin management baseline`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

? `tmp-parent` ????????? ???????????????? ?????? ? ?????????? build plugins ????? `dependencyManagement` ? `pluginManagement`, ??? ??????? ?????? baseline ?????? ??? ??????? Stage 0.

### Files created

- None.

### Files modified

- `pom.xml`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -DskipTests help:effective-pom` | PASSED |

### Architecture review

- ????????? ?????????? ??????????????? ??????, ??? ?????????? ?????????? ?????? ? ??? ?????? ?? ??????? Stage 0.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-003`

## `STAGE0-003` ? `Wire formatting and static analysis gates`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

?????????? quality gates ? `verify`: enforcer (?????? Java/Maven), checkstyle ? spotbugs. ??????? ???????? ??????????? ????????????? ? ????????? ????? Maven.

### Files created

- None.

### Files modified

- `pom.xml`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Tests added or changed

- None.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify -DskipTests` | PASSED |

### Architecture review

- ????????? ?????????? build quality pipeline, ??? ?????????? ??????-????????????????.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-004`

## `STAGE0-004` ? `Establish test baseline and test module conventions`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

??????????? baseline ??? ?????? (JUnit dependency ? smoke test). ????? ??????????? JDK 21 verification `mvn test` ?????? ???????.

### Files created

- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/BootstrapSmokeTest.java`

### Files modified

- `pom.xml`
- `tmp-bootstrap-app/pom.xml`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Tests added or changed

- `BootstrapSmokeTest` (unit smoke test baseline)

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q test` (JDK 21) | PASSED |

### Architecture review

- ?????????? ?? ??????? ?? ??????? Stage 0, ?? ??????????? ??????? ?????, ??????????????? ????????????? Java 21 baseline.

### Documentation updated

- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/STATUS.md`
- `docs/development-control/BLOCKERS.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task

`STAGE0-005`

## `STAGE0-005` ? `Create Spring composition root skeleton`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

???????? Spring Boot composition root ? `tmp-bootstrap-app`: `TmpBootstrapApplication`, `BootstrapConfiguration`, `application.yml` (non-web).

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-bootstrap-app test` | PASSED |

### Next task

`STAGE0-006`

## `STAGE0-006` ? `Implement JavaFX empty shell bootstrap`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

?????????? JavaFX empty shell (`EmptyMainShell`, `JavaFxShellLauncher`, `DesktopBootstrap`) ? ????????? bootstrap ?? Spring.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-ui-shell test` | PASSED |

### Next task

`STAGE0-007`

## `STAGE0-007` ? `Configure PostgreSQL connectivity profiles`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

?????? `tmp-infra-db` ???????? ? PostgreSQL datasource profiles (`dev`/`test`) ? auto-configuration.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db test` | PASSED |

### Next task

`STAGE0-008`

## `STAGE0-008` ? `Add Flyway baseline migration flow`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

????????? baseline Flyway-???????? `V1__platform_baseline.sql` ? ?????????????? smoke-????? schema history.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | PASSED |

### Next task

`STAGE0-009`

## `STAGE0-009` ? `Integrate Testcontainers for PostgreSQL tests`

**Date:** 2026-07-17  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

????????? Testcontainers dependencies ? `FlywayPostgresIntegrationIT`, ?? verification ????????????? ??????????? Docker.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | FAILED (Docker not available) |

### Next task

`STAGE0-009` (resume after BLK-003 resolution)

## `STAGE0-009` ? `Integrate Testcontainers for PostgreSQL tests` (resolved)

**Date:** 2026-07-20  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

Docker Desktop + WSL2 ??????????; Testcontainers ???????? ?? 1.21.4 ? `api.version=1.44`. PostgreSQL container IT ????????.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -pl :tmp-infra-db verify` | PASSED |

### Next task

`STAGE0-010`

## `STAGE0-010` ? `Create ArchUnit baseline architecture tests`

**Date:** 2026-07-20  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

???????? ?????? `tmp-architecture-tests` ? baseline ArchUnit-????????? ?????? UI/infra/Spring.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify -DskipITs` | PASSED |

### Next task

`STAGE0-011`

## `STAGE0-011` ? `Configure logging, runtime profiles, and packaging`

**Date:** 2026-07-20  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

????????? logging (`logback-spring.xml`), ??????? `dev`/`test`/`package`, jpackage app-image ? `dist/jpackage/TMP`.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q -Ppackage verify` | PASSED |
| Package artifact `dist/jpackage/TMP/TMP.exe` | PRESENT |

### Next task

`STAGE0-012`

## `STAGE0-012` ? `Run complete Stage 0 verification gate`

**Date:** 2026-07-20  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

?????? Stage 0 verification gate ???????. Exit criteria Stage 0 ????????????; Stage 0 ??????.

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd -q verify` | PASSED |
| Package artifact present | PASSED |
| No business functionality in Stage 0 | PASSED |

### Next task

Stage 0 complete ? awaiting Stage 1 Start Gate

## Stage 2 ? Document Engine (summary)

## `STAGE2-026` ? `Final Stage 2 re-verification gate (re-review)`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

Stage 2 ?????? ????? residual re-review fixes (BLK-011 transaction-final registry compensation, BLK-013 after-commit handler policy, PostgreSQL ITs, intra-module FK). Stage 3 ?? ?????.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `dist/jpackage/TMP/TMP.exe` | PASSED |
| BLK-011, BLK-013 | RESOLVED |
| Stage 3 start | NOT STARTED |

### Next task

Stage 2 complete ? awaiting Stage 3 Start Gate

## `STAGE2-025` ? `FK document_type_id decision and invariant`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

???????? intra-module FK `fk_documents_document_type` (`documents.document_type_id` ? `document_types.id`) ????? `V3__documents_document_type_fk.sql`. ??????? ??????????? ? Database Specification ?12 (cross-module FK ?????????; ??? ??????? ? schema `documents`). ????????????? `createDocument` ????????? `documentTypeExists`.

### Files created

- `V3__documents_document_type_fk.sql`

### Next task

`STAGE2-026`

## `STAGE2-024` ? `PostgreSQL Testcontainers Document Engine ITs`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

???????? `DocumentEnginePostgresIntegrationIT` ? ????????? registration/operation rollback, optimistic lock, concurrent post/update, after-commit/no-event-on-rollback, failing subscriber, snapshots/journal, file storage, FK presence. H2 component tests ?????????. ???????? `docker-java.properties` (`api.version=1.44`) ??? Docker Engine 29.

### Files created

- `DocumentEnginePostgresIntegrationIT.java`
- `tmp-document-engine/src/test/resources/docker-java.properties`

### Next task

`STAGE2-025` / `STAGE2-026`

## `STAGE2-023` ? `After-commit handler failure policy (BLK-013)`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

Best-effort after-commit delivery: handler failures ?????????? ? ?? ?????????????? ??????????? ????. ????????/journal ???????? committed. Platform Core EventBus contract ?? ???????. Callers must not retry document mutations on delivery failure.

### Files modified

- `TransactionAfterCommitEventPublisher.java`

### Tests added or changed

- `DefaultDocumentEngineTransactionEventTest.failingAfterCommitHandlerDoesNotFailDocumentOperation`

### Next task

`STAGE2-024`

## `STAGE2-022` ? `Registry rollback compensation (BLK-011 reopen)`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

In-memory processor registry ?????????????? ??? ????? non-COMMITTED outcome ????? `TransactionSynchronization.afterCompletion`. Outer rollback / commit-failure path ??????? registry; ????????? ??????????? ???????. Create ??????????? ??? ?????????? ???? ? ??.

### Files modified

- `DefaultDocumentEngine.java`
- `DefaultDocumentProcessorRegistry.java`

### Tests added or changed

- `DefaultDocumentEngineRegistrationTransactionTest`

### Next task

`STAGE2-023`

## `STAGE2-021` ? `Final Stage 2 re-verification gate`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

Stage 2 ?????? ????? ?????????? acceptance-review blockers BLK-010..012. ?????? verification ? ?????? ?????? TMP.exe ???????. Stage 3 ?? ?????. (??????? reopened ??? residual BLK-011/BLK-013; ???????????? ?????? ? STAGE2-026.)

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `dist/jpackage/TMP/TMP.exe` | PASSED |
| BLK-010..012 | RESOLVED (BLK-011 later reopened) |
| Stage 2 exit criteria | CONFIRMED |
| Stage 3 start | NOT STARTED |

### Documentation updated

- STATUS; WORK-QUEUE; BLOCKERS; IMPLEMENTATION-LOG; VERIFICATION-LOG

### Next task

Stage 2 complete ? awaiting Stage 3 Start Gate

## `STAGE2-020` ? `Expanded lifecycle/rollback/concurrency tests`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

????????? ????????: processor failure rollback (create/post/unpost), invalid transitions, immutable POSTED/CLOSED, delete restrictions, optimistic locking, concurrent post/update, version snapshots, journal consistency, close allow/reject, file storage adapter.

### Tests added or changed

- `DefaultDocumentEngineLifecycleTest`
- `JdbcDocumentFileStorageAdapterTest`
- `ConfigurableDocumentProcessor` (test support)

### Verification

| Check | Result |
|---|---|
| Document engine + bootstrap tests | PASSED |

### Next task

`STAGE2-021`

## `STAGE2-019` ? `Post-commit event publishing (BLK-012)`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

??????? DocumentCreated/Posted/Unposted/Closed/Deleted ??????????? ?????? ????? commit ????? Spring `TransactionSynchronization.afterCommit`. ??? rollback ??????? ?? ???????????. Message broker ?? ?????????.

### Files created

- `TransactionAfterCommitEventPublisher.java`

### Files modified

- `DefaultDocumentEngine.java`
- `DocumentEngineAutoConfiguration.java`

### Tests added or changed

- `DefaultDocumentEngineTransactionEventTest`

### Verification

| Check | Result |
|---|---|
| Transaction event integration tests | PASSED |
| BLK-012 | RESOLVED |

### Next task

`STAGE2-020`

## `STAGE2-018` ? `Atomic processor registration (BLK-011)`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

??????????? processor ??????????? ? document type: DB `registerDocumentType` ??????????? ?? in-memory `processorRegistry.register`. ??? DB failure partial state ? registry ?? ????????; ????????? ??????????? ????????.

### Files modified

- `DefaultDocumentEngine.java`

### Tests added or changed

- `DefaultDocumentEngineRegistrationTest` (duplicate, DB failure, retry)

### Verification

| Check | Result |
|---|---|
| Registration atomicity tests | PASSED |
| BLK-011 | RESOLVED |

### Next task

`STAGE2-019`

## `STAGE2-017` ? `Fix duplicate DocumentEngine beans (BLK-010)`

**Date:** 2026-07-22  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

???????? ????? ???? Spring bean ???? `DocumentEngine`. `DesktopBootstrap.getBean(DocumentEngine.class)` ?????? ?? ???????? ambiguous candidates.

### Files modified

- `DocumentEngineAutoConfiguration.java` (?????? `documentEngineFacade`)
- `DocumentEnginePlatformRegistrar.java`

### Tests added or changed

- `DocumentEngineBeanLookupTest`
- `DesktopBootstrapLookupSmokeTest`

### Verification

| Check | Result |
|---|---|
| Bean lookup + DesktopBootstrap smoke | PASSED |
| BLK-010 | RESOLVED |

### Next task

`STAGE2-018`

## `STAGE2-016` ? `Complete Stage 2 Document Engine`

**Date:** 2026-07-21  
**Stage:** Stage 2 ? Document Engine  
**Status:** DONE

### Result

?????????? ?????? `tmp-document-engine` ??? domain-independent ?????????? ?????????? ???????????: ???? ??????????, ?????? lifecycle, ??????????? ???????????, storage/journal/version ports, JDBC adapters, ???????? ????? `documents`, bootstrap integration ? ????????????? ???????????.

### Key deliverables

| Component | Location |
|---|---|
| Public API | `com.tmp.document.api` (`DocumentEngine`, metadata/commands/query/status) |
| Processor contract | `DocumentProcessor` (one type ? one processor) |
| Engine implementation | `DefaultDocumentEngine` |
| Persistence ports | `DocumentStoragePort`, `LifecycleJournalPort`, `DocumentVersionPort`, `DocumentFileStoragePort` |
| JDBC adapters | `JdbcDocumentStorageAdapter`, `JdbcLifecycleJournalAdapter`, `JdbcDocumentVersionAdapter`, `JdbcDocumentFileStorageAdapter` |
| Database migration | `V2__documents_schema.sql` (`documents` schema + tables/indexes/checks) |
| Spring wiring | `DocumentEngineAutoConfiguration`, platform component registrar |
| UI bridge | Bootstrap renders document panel text; UI shell remains independent |
| Architecture rules | `Stage2DocumentEngineArchitectureTest` |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Stage 2 exit criteria | CONFIRMED |
| Stage 3 start | NOT STARTED |

### Next task

Stage 2 complete ? awaiting Stage 3 Start Gate (later reopened for BLK-010..012; closed again in STAGE2-021)

## Stage 1 ? Platform Core (summary)

## `STAGE1-016` ? `Fix registration/lifecycle race condition (BLK-009)`

**Date:** 2026-07-21  
**Stage:** Stage 1 ? Platform Core  
**Status:** DONE

### Result

????????? race condition ????? registration ? lifecycle. ?????? monitor `DefaultLifecycleManager` ??? ???? state transitions ? `registerComponentWithRegistry()`. Deterministic concurrency test (200 iterations). ?????????? ????????? STOPPED restart.

### Key deliverables

| Fix | Location |
|---|---|
| Unified synchronization | `DefaultLifecycleManager.registerComponentWithRegistry()` |
| Removed split lock | `DefaultPlatformCore` delegates to lifecycle manager |
| Safe startup iteration | `List.copyOf(components.values())` in `startAll()` |
| Concurrency + restart tests | `DefaultPlatformCoreRegistrationTest` |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| BLK-009 | RESOLVED |

### Next task

Stage 1 complete ? awaiting Stage 2 Start Gate

---

## `STAGE1-015` ? `Fix Stage 1 re-review remaining defects (BLK-008)`

**Date:** 2026-07-21  
**Stage:** Stage 1 ? Platform Core  
**Status:** DONE

### Result

????????? ?????????? ??????? ????????? ???????? Stage 1. Registration guard ?? platform state; platform events ? `com.tmp.core.api.event.platform`; shutdown listener ? `try/finally`; ?????????? ArchUnit ??????? api-only.

### Key deliverables

| Fix | Location |
|---|---|
| Registration lifecycle guard | `DefaultPlatformCore.isRegistrationAllowed()` |
| Public platform events | `com.tmp.core.api.event.platform` |
| Shutdown resilience | `PlatformCoreAutoConfiguration.PlatformCoreLifecycleListener` |
| Generic API boundary rule | `Stage1PlatformCoreArchitectureTest.externalModulesUseOnlyCorePublicApi` |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| BLK-008 | RESOLVED |

### Next task

Stage 1 complete ? awaiting Stage 2 Start Gate

---

## `STAGE1-014` ? `Fix Stage 1 acceptance review blockers (BLK-005..007)`

**Date:** 2026-07-20  
**Stage:** Stage 1 ? Platform Core  
**Status:** DONE

### Result

????????? ??? ??????????? ??????? acceptance review. Event metadata ?????? immutable ????? `AbstractPlatformEvent` / `AbstractDomainEvent`. `DefaultLifecycleManager` ????????? ? ?????????? rollback ? platform state ?? failure. ?????? ????????? registration path ? `PlatformCore.registerComponent()`. ????????? Service Registry count; ????????? EventBus ? architecture tests.

### Key deliverables

| Fix | Location |
|---|---|
| Stable event contract | `AbstractPlatformEvent`, `AbstractDomainEvent`, updated platform events |
| Lifecycle failure policy | `DefaultLifecycleManager`, `DefaultLifecycleManagerTest` |
| Atomic registration | `DefaultPlatformCore.registerComponent()`, `DefaultPlatformCoreRegistrationTest` |
| Service registry accuracy | `registeredServiceCount()`, `DefaultServiceRegistryTest` |
| API boundary enforcement | `Stage1PlatformCoreArchitectureTest` (com.tmp.core root blocked) |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| BLK-005..007 | RESOLVED |
| Manual TMP.exe launch | PASSED (window opens, process stops cleanly) |

### Next task

Stage 1 complete ? awaiting Stage 2 Start Gate

---

**Date:** 2026-07-20  
**Status:** DONE (STAGE1-001..STAGE1-013)

### Result

?????????? ?????? `tmp-platform-core` ? ????????? API (`com.tmp.core.api`), registries (Platform/Service/Capability), ?????????? Event Bus, lifecycle management, platform configuration, Spring auto-configuration, ArchUnit-????????? ? ??????????? UI-?????????? ??????? ?????????.

### Key deliverables

| Component | Location |
|---|---|
| Public Core API | `tmp-platform-core/.../com/tmp/core/api/` |
| Registries | `DefaultPlatformRegistry`, `DefaultServiceRegistry`, `DefaultCapabilityRegistry` |
| Event Bus | `SynchronousEventBus` (sync, no broker) |
| Lifecycle | `DefaultLifecycleManager` |
| Configuration | `SpringPlatformConfiguration`, `PlatformCoreProperties` |
| Bootstrap wiring | `PlatformCoreAutoConfiguration`, `DesktopBootstrap` |
| Architecture tests | `Stage1PlatformCoreArchitectureTest` |
| UI status | `EmptyMainShell` bottom label via status string |

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Unit tests (platform-core) | PASSED |
| Integration tests (bootstrap) | PASSED |
| Architecture tests | PASSED |
| No domain/business logic in Core | CONFIRMED |

### Next task

Stage 1 complete ? awaiting Stage 2 Start Gate

## `STAGE0-012` ? `Run complete Stage 0 verification gate` (BLK-004 rework)

**Date:** 2026-07-20  
**Stage:** Stage 0 ? Development Foundation  
**Status:** DONE

### Result

????????? ??????????? ?????? acceptance review: `TmpBootstrapApplication` ?????? ?? ????????? DataSource/Flyway auto-configuration. ????????? ??????? `dev`/`test`/`package`; package-??????? ?????? `TMP_DB_URL`, `TMP_DB_USERNAME`, `TMP_DB_PASSWORD`. ???????? `TmpBootstrapPostgresIntegrationIT` ?? ???????? entry point; ??????? `SpringContextSmokeTest` ? `PackagingSmokeIT`. ????????? ??????? jpackage (pre-integration-test ????? repackage).

### Verification

| Check | Result |
|---|---|
| `.tools/apache-maven-3.9.9/bin/mvn.cmd clean verify` | PASSED |
| `.tools/apache-maven-3.9.9/bin/mvn.cmd clean verify -Ppackage` | PASSED |
| `TmpBootstrapPostgresIntegrationIT` (PostgreSQL Testcontainers) | PASSED |
| Package artifact `dist/jpackage/TMP/TMP.exe` | PRESENT |

### Next task

Stage 0 complete ? awaiting Stage 1 Start Gate

## `Stage 3 Start Gate` ? `Baseline verification before Capability Engine`

**Date:** 2026-07-22  
**Stage:** Stage 3 ? Capability Engine  
**Status:** DONE

### Result

???????? ???????????? Stage 3 Start Gate: ????????????, ??? `git status` ?? ???????? ??????????? ????????? production-???? (?????? ??????????????? `.gitignore` ? ??????????? `target/`, ?? ??????????? ? ????), `HEAD` ????????? ? `origin/master` (`af0d2a1b86c3e340398face46dbdf3e8c537e452`), Stage 2 ?????? ?? 100%, ??? blockers Stage 0?2 ? ??????? RESOLVED, STAGE2-026 ? ????????? ??????????? ??????. JDK 21 (Temurin 21.0.11+10, portable `.tools/jdk-21.0.11+10`) ? Maven 3.9.9 (portable `.tools/apache-maven-3.9.9`) ????????. `mvn clean verify` ???????? ??? baseline ? ?????? ????????? (105 ??????, 0 failures/errors ?? ???? ???????, ??????? PostgreSQL Testcontainers ITs ? `tmp-document-engine` ? `tmp-bootstrap-app`).

??????? ???????????? ?????????: Capability Engine Specification, Platform Core Specification (????????? API `com.tmp.core.api.*`), Document Engine ????????? API (`com.tmp.document.api.*`), ??????????? ADR (ADR-001, ADR-002, ADR-003, ADR-019, ADR-022), UI/UX Specification (?????? ?????????), governance rule, ??????? ????????? `tmp-platform-core`/`tmp-document-engine`/`tmp-bootstrap-app`/`tmp-architecture-tests`.

### Design decision ? external contribution atomicity (no blocker required)

??????????????? ????: `DocumentEngine.registerProcessor()` (public API) ? `ServiceRegistry.register()` (public API) ?? ????????????? ????????? compensating unregister. ???????? ?????????? ???????????:
- `DefaultDocumentProcessorRegistry`/`DefaultDocumentEngine.registerProcessor()` ??? ???????? ?? ????? ??????? (Stage 2 BLK-011/018): ??? duplicate typeId ??? DB-?????? ?????? ?? ??????????????; ????????? ????????? ??????????.
- `DefaultServiceRegistry.register()` ?? ???????? ???????? duplicate ? ?? ??????? ?????????? (?????????? ??????? ?? ???????? ?????????).

???????? ??????????? ???????: Capability Engine ????????? pre-validation (???????? ???????????? ????? read-only `documentEngine.registeredTypes()` ? `serviceRegistry.lookup()`) ??? ?????? registration lock ?? ?????? ??????? ?????????? ???????, ? ??????????? document-???????????, ? ????? service-??????????? ??? ????????? ???? atomic registration (????? ???? internal-catalog ?????, ??????? ????????? ???????????). ????????? service-??????????? ?????????? ??????? ?? ???????? ?????????, ? document-??????????? ?????????????? ???????? ?? ????? ???????, ?????????? ????????? ???????????? ???????? "??????? ??????????? ????? ??? ???????????". ?????????? ????????????? ???? (??????? ????????? ????????? `ServiceRegistry`) ??????????????? ??? known limitation ? Javadoc `DefaultCapabilityRegistrationService`, ? ?? ??????????. ????????? API Platform Core/Document Engine ?? ??????????; blocker ?? ?????????.

### Verification

| Check | Result |
|---|---|
| `git status --short` | clean production code (only unrelated `.gitignore` diff) |
| `git rev-parse HEAD` vs `origin/master` | MATCH (`af0d2a1b86c3e340398face46dbdf3e8c537e452`) |
| `mvn clean verify` | PASSED (BUILD SUCCESS, 105 tests, 0 failures) |

### Next task

STAGE3-001

## `STAGE3-001` ? `Bootstrap tmp-capability-engine module and public API package skeleton`

**Date:** 2026-07-22  
**Stage:** Stage 3 ? Capability Engine  
**Status:** DONE

### Result

?????? Maven-?????? `tmp-capability-engine`, ????????? ? root reactor (`<modules>` ? `<dependencyManagement>`), ??????? ?????? ?? `tmp-platform-core`, `tmp-document-engine` (????????? API) ? `spring-boot-starter`; test-scope ????????????? ??????? `tmp-infra-db`, `spring-boot-starter-jdbc`, `spring-boot-starter-test`, JUnit, Testcontainers (postgresql) ? ???????, ??? ??????? ????? STAGE3-015/019, ??? ????????????? ? production-???? ?? ???? ????. ?????? ?????? ????? `com.tmp.capability.api` (`package-info.java`), ??? ??????? ????????? ? ??? ???????? ??????? ? STAGE3-002.

### Files created

- `tmp-capability-engine/pom.xml`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/package-info.java`

### Files modified

- `pom.xml` (root reactor: added module + dependencyManagement entry)

### Tests added or changed

- none (pure structural task, matches STAGE1-001/STAGE2-001 precedent).

### Verification

| Check | Result |
|---|---|
| `mvn -q -DskipTests validate` (full reactor) | PASSED |
| `mvn -q -pl :tmp-capability-engine -am compile` | PASSED |
| `mvn -q -pl :tmp-capability-engine -am verify -DskipTests` (checkstyle + spotbugs gates) | PASSED |

### Architecture review

- Module depends only on `tmp-platform-core`, `tmp-document-engine` public APIs and Spring Boot starter; no business logic present.

### Next task

STAGE3-002

---

## `STAGE3-002` ? `CapabilityId and CapabilityVersion value objects with version compatibility rule`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

??????????? immutable value objects `CapabilityId` (???????? ??????, ??????????? ????????????? ??? ??????-??????) ? `CapabilityVersion` (`MAJOR.MINOR.PATCH`, ??? ??????? SemVer-??????????). `CapabilityVersion` ????????? `Comparable<CapabilityVersion>` ? ????????????????? ?????????? ?? ??????????? ? ????? `isCompatibleWith(CapabilityVersion required)`, ??????????? ??????????????? ? design decisions ???????: ??????????? major ?????????? (????? ????????????); ??? ?????? major ?????????? minor/patch ?????? ???? `>=` ????????? (????????????????? ?? (minor, patch)). ???????????? ?????? ?????? (??????????? ?????????, ??????????/????????????? ????????) ??????????? ? `IllegalArgumentException` ????? ??????? regex `(\d+)\.(\d+)\.(\d+)` ? `Matcher.matches()` (?????? ?????????? ??????).

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityId.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityVersion.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityIdTest.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityVersionTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityIdTest`: ???????? id, blank/empty/null ?????????, `equals`/`hashCode` ?? ????????.
- `CapabilityVersionTest`: ???????? ??????? ???????????; ?????????? ??? ??????????? ??????????, ?????????? ????????, ????????????? ????????, `null`; ????????????? ??? ?????? major ? minor/patch ???? ??? ????? ?????????? (??????? ?????????); ??????????????? ??? ?????? major ? ??? me????? minor/patch ? ??? ?? major; ??????????????????? ? ?????????????? `compareTo`; `equals`/`hashCode` ?? ???????? ??????????? (?? ?? ???????? ??????).

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-platform-core,:tmp-document-engine -am install -DskipTests` (upstream artifacts refreshed in local repo) | PASSED |
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityIdTest,CapabilityVersionTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine test` (full module test suite) | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (checkstyle + spotbugs gates) | PASSED |

### Architecture review

- ?????? ????? ????????? ???? ? `com.tmp.capability.api`; ??????? ????????? ???????????? ?????????? Platform Core / Document Engine.
- ??? registry/lifecycle/discovery ?????? ? ???????? ????????? value objects, ??? ? ???????????.
- ??? ??????????? ?? ??????? SemVer-??????????.

### Next task

STAGE3-003

---

## `STAGE3-003` ? `Dependency descriptor and dependency validation error contract`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? immutable `DependencyDescriptor` (???? `CapabilityId` ??????? ??????????? + ?????????? ????????? `CapabilityVersion`, null-????????, `equals`/`hashCode` ?? ????? ?????) ? unchecked `DependencyValidationException` ? ?????????????? ???????? ?????? ????????? ???????????? ? nested enum `DependencyValidationReason` (`MISSING_DEPENDENCY, SELF_DEPENDENCY, DUPLICATE_DEPENDENCY, INCOMPATIBLE_VERSION, CYCLIC_DEPENDENCY`). ?????????? ????????? ?????? ????? ?????????????? ??????????? ??????? (`missingDependency`, `selfDependency`, `duplicateDependency`, `incompatibleVersion`, `cyclicDependency`), ?????? ????????? ?????? ???????????????? ????????? ? ?????? ?????? ?????????? `CapabilityId` (`offendingIds()`) ??? ?????? ???????????. ?? descriptor, ?? exception ?? ???????? ?????? ????????? ????? ???????????? ? ??? ????????? ? STAGE3-010.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DependencyDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DependencyValidationException.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/DependencyDescriptorTest.java`

### Files modified

- none.

### Tests added or changed

- `DependencyDescriptorTest`: ???????? ??????????? ? ?????? ? ?????; `null` id ????????; `null` ?????? ?????????; `equals`/`hashCode` ?? ???? (id, minimumVersion), ??????? ???????? ?????? ?? id ? ?????? ?? ??????.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=DependencyDescriptorTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- ?????? ????? ????????? ???? ? `com.tmp.capability.api`; ??????? ????????? ???????????? ??????????.
- `DependencyDescriptor` ? `DependencyValidationException` ? ?????? data/error-????????? ??? ??????????? ?? registry/graph ??????, ??? ? ???????????.
- ??? ?????? ?? ?????????? ??????-capability ? ?????? ??????????? ??????????????.

### Next task

STAGE3-004

---

## `STAGE3-004` ? `Command, View, Navigation and Permission descriptor contracts`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

??????????? ?????? ???????????, ?????-??????????? immutable metadata-?????????: `PermissionDescriptor` (id/displayName/description ? ?????? ???????? ?????, ??? ?????????????/?????/???????? ???????), `CommandDescriptor` (id/displayName/`requiredPermissionIds` ? ???????????? ?????? ????? defensive `List.copyOf`), `ViewDescriptor` (id/displayName/`navigationTargetId` ? ?????? routing-??????????, ??? ?????? ?? FXML/Controller/ViewModel), `NavigationContribution` (id/displayName/viewId/order ? ?????????? ??? ?????????? ?????? ?????????). ??? ?????? ???? ?????????? ???????? (non-blank) ????????????? ? ????????? ????????? ?????????? ??? ??????????????? ????? ??????????? ??????? `of(...)`, `equals`/`hashCode` ?????????? ?? ???????????? ?????????????? ???? (permissionId/commandId/viewId/navigationId) ???????? acceptance criteria. ?? ???? ????? ?? ???????? ?????? ???????????, ?? ??????? ?? JavaFX/Spring/persistence.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/PermissionDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CommandDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/ViewDescriptor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/NavigationContribution.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/ContributionDescriptorsTest.java`

### Files modified

- `tmp-capability-engine/pom.xml` (added `spotbugs-annotations` provided-scope dependency, matching `tmp-document-engine` precedent, to support `@SuppressFBWarnings` on the intentionally-safe immutable-list getter).

### Tests added or changed

- `ContributionDescriptorsTest` (nested per type): valid construction and field access; blank/null identifier rejected; `CommandDescriptor.requiredPermissionIds()` returns an unmodifiable list (`UnsupportedOperationException` on mutation) and is defensively copied from the constructor argument; `equals`/`hashCode` by identifier for all four types; all four types are `final` pure data classes.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=ContributionDescriptorsTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED (after adding `@SuppressFBWarnings("EI_EXPOSE_REP")` with justification on `CommandDescriptor.requiredPermissionIds()`, since SpotBugs cannot statically infer that a `List.copyOf`-produced list is immutable) |

### Architecture review

- Only new public types in `com.tmp.capability.api`; nothing existing changes.
- No FXML/Controller/ViewModel classes, no permission-checking/authorization logic, no user/role types ? matches "Forbidden" scope exactly.
- No dependency on JavaFX, Spring, or persistence in any of the four new types.

### Next task

STAGE3-005

---

## `STAGE3-005` ? `Public service, event, settings and document contribution contracts`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

??????????? ?????? ???????????, ?????-??????????? immutable metadata-?????????, ??????????? ??? ??????? ?????????? ? Platform Core/Document Engine: `PublicServiceContribution<T>` (generic-holder ?????????? ???????; `serviceType`/`serviceInstance` non-null, instance ?????? ???? assignable ? declared type ? ??????????? ? ???????????? ????? `Class.isInstance`); `EventContribution` (id+description ???????????? ???? ???????, ??? ????????????? payload); `SettingsContribution` (key/displayName/description/defaultValue ? ?????? descriptor, ??? persistence); `DocumentContribution` (documentTypeId/displayName/description + ?????? ?? `com.tmp.document.api.DocumentProcessor`, ? fail-fast ?????????, ??? `processor.documentTypeId()` ????????? ? ?????????? `documentTypeId`). ??? ?????? ???? ? ?????? ?????? ??? ???????? ????????: ?? ???? ??????????? ?? ?????????? ? Platform Core ??? Document Engine registration API. `equals`/`hashCode` ??? ???? ????? ?????????? ?? ????????????????? ????? (`serviceType`, `eventTypeId`, `settingKey`, `documentTypeId` ??????????????), ????????? ??????? STAGE3-004.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/PublicServiceContribution.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/EventContribution.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/SettingsContribution.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/DocumentContribution.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/IntegrationContributionDescriptorsTest.java`

### Files modified

- none.

### Tests added or changed

- `IntegrationContributionDescriptorsTest` (nested per type): valid construction and field access for all four types; `PublicServiceContribution` rejects a service instance not assignable to the declared type (using a raw-cast test double to bypass compile-time generics), rejects `null` type/instance; `DocumentContribution` rejects a processor whose `documentTypeId()` does not match the declared id (using an inline `DocumentProcessor` test double), rejects blank/`null` document type id and `null` processor; `EventContribution`/`SettingsContribution` reject blank/`null` identifiers; `equals`/`hashCode` by identifying key for all four types.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=IntegrationContributionDescriptorsTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- Only new public types in `com.tmp.capability.api`; read-only usage of `com.tmp.document.api.DocumentProcessor` (no change to Document Engine).
- No constructor calls into Platform Core or Document Engine registration APIs ? pure data contracts, consistent with "Forbidden" scope.
- No persistence of settings values; `SettingsContribution` is a registration-only descriptor.

### Next task

STAGE3-006

---

## `STAGE3-006` ? `CapabilityDescriptor aggregate, CapabilityLifecycleState and Capability SPI contract`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? `CapabilityLifecycleState` enum (????????????? ????? ?? 8 ??????: `DISCOVERED, VALIDATED, REGISTERED, INITIALIZED, ACTIVE, STOPPED, DEACTIVATED, FAILED`); `Capability` SPI-????????? (`descriptor()`, `onInitialize()`, `onActivate()`, `onDeactivate()`, `onStop()`) ??? ?????? ?????? ?? `com.tmp.core`/`com.tmp.document` ? ?????? ???????? hooks, capability ?? ?????????? ? ????????????? ??????; ? immutable `CapabilityDescriptor`, ???????????? ??? ????????? STAGE3-002..005, ??????????? ????????????? ????? `Builder`. `Builder.build()` ?????????? ???????????? ???? (`id` ? `NullPointerException`, `name`/`version`/`description` ? non-null/non-blank) ? ????????? descriptor-level ???????????????????: ?????????? ????????????? id ? ?????? ?? 8 ????????? contributions ? permission id, command id, view id, navigation id, event type id, settings key, document type id ? dependency target id. ??? ????????????? ??????????? ??????????????? ??? ???????????? `DependencyValidationException.duplicateDependency(...)` (?????????????? ???????? ?? STAGE3-003); ??? ????????? 7 ????????? ???????? ????? nested `CapabilityDescriptor.DuplicateContributionException` (unchecked, ? ?????? ?????????? "Duplicate &lt;category&gt; '&lt;id&gt;' in capability descriptor"). ??? 9 ????????? (`dependencies`, `permissions`, `commands`, `views`, `navigationContributions`, `documents`, `publicServices`, `events`, `settings`) ? defensive-copied ????? `List.copyOf` ? ???????????.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityLifecycleState.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/Capability.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityDescriptor.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/api/CapabilityDescriptorTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityDescriptorTest`: ???????? descriptor ?? ????? 9 ?????? contributions ????????????; ????????????? id ???????? (`NullPointerException`); ????????? ???? ?? ???????? ??? ?????? ?? 8 ????????? (permission/command/view/navigation/event/settings/document/dependency), ?????? ????????????? ??????????? ??????????? ?? ?????????? `DependencyValidationReason.DUPLICATE_DEPENDENCY`; ????????? ???? ?? ?????????????? (`UnsupportedOperationException` ??? ??????? `add`) ??? ?????? ?? 9 ?????????; ????????? ????????, ??? `Capability` ? ????????? ??? ?????????? ??????? ?? `com.tmp.core`/`com.tmp.document`.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityDescriptorTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- ?????? ????? ????????? ???? (`CapabilityDescriptor`, `CapabilityLifecycleState`, `Capability`); ?????? ????????????? ?? ????????.
- ??????? registry/lifecycle-manager ?????????? ? ??? ?????? data-???????? (registry ? STAGE3-007+).
- `Capability` SPI ?? ??????? ?? `com.tmp.core`/`com.tmp.document` ????? ????, ??? ??? ???????????? `DocumentContribution` (???????????? ?????? ? ????? ??? `CapabilityDescriptor`, ??????? ??? ????????? ?? `DocumentContribution.processor(): com.tmp.document.api.DocumentProcessor`, ??? ???????????? ? ??????????? ???????).

### Next task

STAGE3-007

---

## `STAGE3-007` ? `Capability Registry (read-only catalog, uniqueness, immutable snapshots)`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? ?????????? (?? ? `.api`) thread-safe `CapabilityRegistry` ? ????? ?????? `com.tmp.capability.registry`, ????????? ?? `com.tmp.core.api.CapabilityRegistry` (Stage 1). ?????????? ???????? ???????????: `reserve(id)` ???????? claim'?? id (?????????? `ConcurrentHashMap.newKeySet().add()` ??? ?????????? `ReentrantLock` ??? ?????????? ????? ????? ????????? committed-????? ? claim'??), `release(id)` ??????????? ?????????? ??? rollback (retry-after-failure), `commit(CapabilityRegistration)` ???????????? ??????????? ? ??????? ??????????, `updateState(id, newState)` ???????? ???????? snapshot ????? (??? registry ?? ????????? ??????????? ???????? ? ??? STAGE3-008). `findById`/`findAll` ?????? ?? `ConcurrentHashMap` ??? ??????????; `findAll()` ?????????? ???????????????? ??????????????? ?? id `List.copyOf`-??????. ??????????????? (?? ?? committed) id ?? ????? ? `findById`/`findAll` ? ???????? ?????????? partial state. `CapabilityRegistration` ? immutable record-???????? ????? (descriptor + state + owning `Capability`), ? package-private `withState(...)` ??? ????????????? ?????? ????? registry.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/registry/CapabilityRegistry.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/registry/CapabilityRegistration.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/registry/CapabilityRegistryTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityRegistryTest`: reserve/commit/findById/findAll happy path (including that a bare reservation is invisible to reads); duplicate id rejected at `reserve`; duplicate id rejected at `commit`; release-after-reserve allows a subsequent reserve to succeed; `updateState` reflected in both `findById` and `findAll`; `updateState` on an unregistered id throws; `findAll()` snapshot is immutable (`UnsupportedOperationException` on mutation); `findAll()` is sorted deterministically by id; a two-thread concurrent test (200 capabilities, `CyclicBarrier`-gated, matching `DefaultPlatformCoreRegistrationTest` precedent) exercising concurrent `reserve`/`commit` against concurrent `findAll`/`findById` produces no `ConcurrentModificationException` and ends with a fully consistent registry.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityRegistryTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED (after switching `CapabilityRegistration` from a `record` to an explicit immutable class with justified `@SuppressFBWarnings` on `EI_EXPOSE_REP`/`EI_EXPOSE_REP2`, since SpotBugs cannot statically infer that `CapabilityDescriptor` is immutable) |

### Architecture review

- `CapabilityRegistry`/`CapabilityRegistration` are intentionally internal (package `com.tmp.capability.registry`, not `.api`); no public contract changes.
- No dependency validation, discovery, or contribution-catalog logic added ? pure id/state/snapshot bookkeeping, as required.
- In-memory only, no persistence added.

### Next task

STAGE3-008

---

## `STAGE3-008` ? `Capability lifecycle state machine (allowed transitions)`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? ??????, stateless ??????????? ????? `CapabilityStateTransition` ? ????? ?????? `com.tmp.capability.lifecycle`, ? ???????????? ??????? `isAllowed(CapabilityLifecycleState from, CapabilityLifecycleState to)`, ??????????? ????????????? ??????? ?????????: `DISCOVERED->VALIDATED`, `VALIDATED->REGISTERED`, `REGISTERED->INITIALIZED`, `INITIALIZED->ACTIVE`, `ACTIVE->STOPPED`, `STOPPED->INITIALIZED` (restart), `STOPPED->DEACTIVATED`, ? `FAILED` ???????? ?? `DISCOVERED|VALIDATED|REGISTERED|INITIALIZED|ACTIVE`. `DEACTIVATED` ? `FAILED` ? ???????????? ????????? ??? ????????? ????????? (??????????????????? ??????????? scope, ? ?? ????????). ??????? ??????????? ????? `EnumMap<CapabilityLifecycleState, Set<CapabilityLifecycleState>>`, ??????????? ???? ??? ?????????? ? ????????? ? ???????????? `Map.copyOf`. ??????? orchestration-?????? ??? I/O ? ?????? ???????.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/lifecycle/CapabilityStateTransition.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityStateTransitionTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityStateTransitionTest`: ????????????????? ????, ????????????? ??? 12 ??????????? ????????? (?????? ??????? `true`); ????????????????? ???? ? 32 ????????????????? ???????????? ??????????, ??????? skip-ahead (`DISCOVERED -> ACTIVE`), reverse (`ACTIVE -> DISCOVERED`), self-transitions ??? ??????? ?????????, ? ?????? ????? terminal-state escapes ?? `DEACTIVATED`/`FAILED`; ????????? exhaustive-????, ??? ?? `DEACTIVATED`/`FAILED` ?? ???????? ??????? ?? ? ???? ?? 8 ?????????; `null` `from`/`to` ????????? (`NullPointerException`).

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityStateTransitionTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module test suite + checkstyle + spotbugs) | PASSED |

### Architecture review

- ??????????, ?? ????????? ???????? (`com.tmp.capability.lifecycle`, ?? `.api`); ?????? ?????????? ?? ????????.
- ??????? lifecycle orchestration (initialize/activate/deactivate execution) ? ??? STAGE3-013; ???????? I/O.
- `ACTIVE -> DEACTIVATED` ???????? ?? ???????? ? ??????????? ??????? ????????? ????? `STOPPED`, ??? ? ???????????.

### Next task

STAGE3-009

---

## `STAGE3-009` ? `Discovery of Capability beans (Spring composition, deterministic ordering)`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? framework-agnostic `CapabilityDiscovery` ? ?????? `com.tmp.capability.discovery`: ??????????? ????????? `List<Capability>` (Spring/Java composition, ??? classloading/hot-deployment/plugin marketplace/network discovery), `discover()` ?????????? ??????, ??????????????? ???????????????? ?? `CapabilityId`, ? ????????? ????????????? id ????? `IllegalStateException` ? ??????? ????? ??????? Capability. ????????? ?????? `discover()` ???? ?????????? ?????????. ????? ?? ????? Spring-????????? ? ?? ????????? I/O.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/discovery/CapabilityDiscovery.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/discovery/CapabilityDiscoveryTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityDiscoveryTest`: zero ? empty; one ? singleton; multiple with two different input orders ? same sorted output; duplicate id ? `IllegalStateException` naming both classes; repeated `discover()` calls return equal deterministic results.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityDiscoveryTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No ClassLoader, ServiceLoader, package scanning, jar scanning, or networking.
- Internal component only; consumed later by the facade (STAGE3-014).

### Next task

STAGE3-010

---

## `STAGE3-010` ? `Dependency graph validation (missing/self/duplicate/version/cycles) and topological order`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? stateless `DependencyGraphValidator` ? ?????? `com.tmp.capability.validation`: `validate(List<Capability>)` ????????? missing/self/duplicate/incompatible-version ??????????? (????? `DependencyValidationException` ? ??????????????? `DependencyValidationReason`) ? ?????? ????????????????? topological order (dependencies before dependents; ties broken by `CapabilityId` natural order via `TreeSet`). ????? (direct ? indirect) ????????????? ?? ??????????? in-degree ????? Kahn's algorithm. `reverse(List<Capability>)` ?????????? ?????? reverse ??? shutdown order. ??????? ???????????? ?????? ?? ?????????? ?????????? ????????????? ?????????? ? Javadoc (`DependencyDescriptor` ????????? ?????? ?? `CapabilityId`, ?? ?? concrete class).

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/validation/DependencyGraphValidator.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/validation/DependencyGraphValidatorTest.java`

### Files modified

- none.

### Tests added or changed

- `DependencyGraphValidatorTest`: linear + diamond valid graphs; missing/self/duplicate/incompatible-version; direct + indirect cycle; deterministic order across repeated runs and input-order independence; `reverse` produces exact reverse of forward order. Duplicate-dependency case uses a package-private reflection bypass of `CapabilityDescriptor.Builder` (documented) because the public builder already rejects duplicates (STAGE3-006) ? validator re-checks defensively against the discovered graph.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=DependencyGraphValidatorTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` (full module + checkstyle + spotbugs) | PASSED |

### Architecture review

- No registry mutation, no contribution registration, pure graph algorithm.
- Internal validator only.

### Next task

STAGE3-011

---

## `STAGE3-011` ? `Internal contribution catalogs (permission, command, view, navigation, settings, event descriptor)`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? generic thread-safe `ContributionCatalog<T>` (ConcurrentHashMap + ReentrantLock; `add` ????????? duplicate id across all owners; `removeAllForOwner` ??? rollback/deactivation; `activeEntries` immutable snapshot; `ownerOf` ??? owner tracking) ? ??????? `CapabilityContributionCatalogs` ?? ????? ????????? (permissions, commands, views, navigation, settings, events). `registerInternalContributions(CapabilityDescriptor)` ???????????? ??? contributions ????????? ? ??? ????? ?????? ?????????? ??? ????? ????????? ????? `removeAllForOwner` ?? rethrow ? partial state ?? ????????. Document/public-service contributions ????????? ??????????? (??????? ??????????? ? STAGE3-012).

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/contribution/ContributionCatalog.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/contribution/CapabilityContributionCatalogs.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/contribution/ContributionCatalogTest.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/contribution/CapabilityContributionCatalogsTest.java`

### Files modified

- none.

### Tests added or changed

- `ContributionCatalogTest`: add/retrieve; duplicate id across owners rejected; owner tracking; removeAllForOwner removes only target owner; concurrent add/read without ConcurrentModificationException.
- `CapabilityContributionCatalogsTest`: aggregate registration across all six catalogs; bulk rollback; conflict mid-registration rolls back all catalogs for the failing owner.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=ContributionCatalogTest,CapabilityContributionCatalogsTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED (after justified `@SuppressFBWarnings("EI_EXPOSE_REP")` on catalog accessors) |

### Architecture review

- No Document Engine / Platform Core calls; no authorization logic.
- Internal only; read-only exposure via facade comes later (STAGE3-014).

### Next task

STAGE3-012

---

## `STAGE3-012` ? `Atomic registration orchestrator (Document Engine + Platform Core external contributions with rollback)`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? `CapabilityRegistrationService` ? ???????????? ????? ????????? ??????????? Capability ? ?????????? `ReentrantLock` ? ????????????? ???????? (design decisions ?6): reserve ? internal catalogs ? Platform Core `capabilityRegistry` ? Document Engine `registerProcessor` (? pre-check `registeredTypes`) ? Platform Core `ServiceRegistry` ? commit ??? `REGISTERED`. Event contributions ? ?????? cataloging (???????? ??????? ???? Capability). ??? ?????? unwind CE-owned state (catalogs + reservation) ? ??????????? original cause ? suppressed compensation failures ????? `CapabilityRegistrationException`. Known residual limitation (??? ??????????? ??? ???????? ServiceRegistry/Document Engine ???????) ???????????????? ? Javadoc.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/registration/CapabilityRegistrationService.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/registration/CapabilityRegistrationException.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/registration/CapabilityRegistrationServiceTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityRegistrationServiceTest`: happy path all contribution types; catalog conflict rollback + retry; document pre-check conflict rollback (Document Engine not mutated); duplicate processor rejection; service failure fixture rolls back CE-owned state while documenting residual external mutation; retry after corrected failure; concurrent same-id (exactly one winner); original exception preserved.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityRegistrationServiceTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No modifications to `com.tmp.core.api.*` or `com.tmp.document.api.*`; public APIs only.
- No reflection/internal access; original failure cause never swallowed.

### Next task

STAGE3-013

---

## `STAGE3-013` ? `Lifecycle manager: initialization order, activation/deactivation, dependents check, reverse shutdown`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? `CapabilityLifecycleManager`: `initializeAll()` ? topological order (hook ????? transition; failure isolation ? FAILED + transitive dependents FAILED ? chained cause; ??????????? ????????); `activateAll()` ?????? ??? INITIALIZED, ????????? ????????? ????????? ???? ???? ACTIVE; `deactivate(id)` ????????? active dependents (??? cascade), ACTIVE?STOPPED?DEACTIVATED, `removeAllForOwner` ?? catalogs; `stopAll()` ? reverse topological order. ??? transitions ????? `CapabilityStateTransition.isAllowed`.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/lifecycle/CapabilityLifecycleManager.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityLifecycleManagerTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityLifecycleManagerTest`: full init/activate chain order; invalid deactivate from REGISTERED; repeated activation rejected; stopAll reverse order (3-capability chain); normal deactivation removes catalogs without cascade; deactivation with active dependents rejected; failed init/activate isolate failed + dependents while independent remains ACTIVE; dependency-failed never reaches ACTIVE.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityLifecycleManagerTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No cascading deactivation; no business data deletion.
- Internal only; facade exposure in STAGE3-014.

### Next task

STAGE3-014

---

## `STAGE3-014` ? `CapabilityEngine public facade and status snapshot`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

?????????? ????????? API `CapabilityEngine` + `CapabilityEngineStatus` ? `DefaultCapabilityEngine`: `discoverAndRegisterAll()` = discover ? DependencyGraphValidator ? register each ? initializeAll; `activateAll`/`deactivate`/`stopAll` ?????????? lifecycle manager; workplace queries ????????? contributions ?????? ?????????? ? ????????? `ACTIVE`; `status()` ??????? discovered/registered/active/failed. ????????? ?? ??????????? internal types.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityEngine.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/api/CapabilityEngineStatus.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/DefaultCapabilityEngine.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/DefaultCapabilityEngineTest.java`

### Files modified

- none.

### Tests added or changed

- `DefaultCapabilityEngineTest`: e2e discover?register?init?activate ? dependency graph (2 capabilities); active-only queries ????? deactivate; status counts ?? ?????? ????? lifecycle.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=DefaultCapabilityEngineTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No access-decision logic; only `.api` types in facade signatures.
- New public types only; Stage 1/2 contracts unchanged.

### Next task

STAGE3-015

---

## `STAGE3-015` ? `Spring Boot auto-configuration and Platform Core component registration`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

??????????? `CapabilityEngineAutoConfiguration` (wiring ???? internal collaborators + ???? `CapabilityEngine` bean), `CapabilityEnginePlatformComponent` (??????? ? `PlatformComponent` ? `ComponentType.SERVICE`, id `"capability-engine"`; initialize?discoverAndRegisterAll, start?activateAll, stop?stopAll), inner `CapabilityEnginePlatformRegistrar` ? `@PostConstruct` (??????? Document Engine), ? `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. ?????????? registry bean ???????????? ? `capabilityEngineRegistry`, ????? ?? ????????????? ? Platform Core `capabilityRegistry`.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEngineAutoConfiguration.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEnginePlatformComponent.java`
- `tmp-capability-engine/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- `tmp-capability-engine/src/test/java/com/tmp/capability/CapabilityEngineAutoConfigurationTest.java`

### Files modified

- none.

### Tests added or changed

- `CapabilityEngineAutoConfigurationTest`: slice context (PlatformCore + CapabilityEngine auto-config + stub DocumentEngine + test Capability); exactly one CapabilityEngine bean; component registered in PlatformCore after startup; lifecycle delegation verified via ApplicationReadyEvent initialize/activate and explicit stop.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityEngineAutoConfigurationTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- Exactly one CapabilityEngine PlatformComponent registration; no dependency on tmp-ui-shell or tmp-bootstrap-app.
- `ComponentType.SERVICE` documented in Javadoc (not CAPABILITY).

### Next task

STAGE3-017

---

## `STAGE3-016` ? `Sample technical Capability (end-to-end fixture, no business logic)`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

??????????? sample technical capabilities (`SampleTechnicalCapability`, `SampleDependentTechnicalCapability`) ? ?????? ??????? contribution types (permission, command, view, navigation, setting, event, document, public service), trivial `SampleTechnicalDocumentProcessor` ? `SampleTechnicalService`, probe `SampleLifecycleProbe` ??? ??????? activation ? service resolution. ?????????????? ???? ????????? ?????? Spring-???????? (Database + Platform Core + Document Engine + Capability Engine) ?? H2 ? ????????? discover?register?init?activate, dependency order, ServiceRegistry lookup, document creation ? deactivation ? active dependents.

### Files created

- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalCapability.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleDependentTechnicalCapability.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalDocumentProcessor.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalService.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalServiceImpl.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleLifecycleProbe.java`
- `tmp-capability-engine/src/test/java/com/tmp/capability/sample/SampleTechnicalCapabilityIntegrationTest.java`

### Files modified

- `tmp-capability-engine/pom.xml` (test-scoped `h2` dependency for integration test datasource)

### Tests added or changed

- `SampleTechnicalCapabilityIntegrationTest`: full e2e for both sample capabilities; activation order; ServiceRegistry resolution; DocumentEngine.createDocument; deactivation rejected while dependent active, succeeds after dependent stopped first.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=SampleTechnicalCapabilityIntegrationTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- Sample capabilities are technical fixtures only; no business/domain naming.
- Dependent capability resolves public service exclusively via Platform Core ServiceRegistry (ADR-003).
- Sample beans registered via test `@Configuration` only; production bootstrap wiring deferred to STAGE3-017.

### Next task

STAGE3-017

---

## `STAGE3-017` ? `Bootstrap integration and minimal technical capability status UI`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

????????? `tmp-capability-engine` ? `tmp-bootstrap-app`: sample technical capabilities ???????????????? ??? Spring beans ? `BootstrapConfiguration`, `DesktopBootstrap` ???????? `CapabilityEngine` ? ????????? `formatCapabilityStatus()` ? ?????? status-?????? (discovered/active counts + id/state sample capabilities). UI shell ?? ????????? ? plain-string bridge ????????.

### Files created

- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/CapabilityEngineBeanLookupTest.java`

### Files modified

- `tmp-bootstrap-app/pom.xml` (dependency `tmp-capability-engine`)
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/BootstrapConfiguration.java` (sample capability beans)
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/DesktopBootstrap.java` (`formatCapabilityStatus`, launch wiring)
- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/DesktopBootstrapLookupSmokeTest.java` (capability lookup + formatting)
- `tmp-bootstrap-app/src/test/java/com/tmp/bootstrap/PlatformCoreIntegrationIT.java` (updated counts for capability-engine + sample capabilities)

### Tests added or changed

- `CapabilityEngineBeanLookupTest`: single bean lookup; `formatCapabilityStatus` includes counts and sample capability ACTIVE states.
- `PlatformCoreIntegrationIT`: capability/component/service counts adjusted for auto-registered sample capabilities and capability-engine component.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-bootstrap-app test -Dtest=CapabilityEngineBeanLookupTest,SpringContextSmokeTest,DocumentEngineBeanLookupTest` | PASSED |
| `mvn -q -pl :tmp-bootstrap-app verify` | PASSED |

### Architecture review

- `tmp-ui-shell` unchanged; no Capability Engine types in UI layer.
- Bootstrap owns all engine API calls and string formatting (same pattern as Platform Core / Document Engine).

### Next task

STAGE3-019

---

## `STAGE3-018` ? `Stage 3 architecture tests`

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

???????? `Stage3CapabilityEngineArchitectureTest` ? 8 ArchUnit-????????? ?????? Capability Engine (business-module isolation, public-API-only ??? core/document/capability, reverse dependencies, sample package). ??? ??????????? `externalModulesUseOnlyCapabilityPublicApi`: sample beans ?????????? ?? bootstrap ? `SampleTechnicalCapabilitiesAutoConfiguration` (conditional on `tmp.capability.sample.diagnostic`); bootstrap ?????????? ?????? `com.tmp.capability.api`; `CapabilityEngineAutoConfiguration` ????????? ?? name-based `@AutoConfigureAfter`.

### Files created

- `tmp-architecture-tests/src/test/java/com/tmp/architecture/Stage3CapabilityEngineArchitectureTest.java`
- `tmp-capability-engine/src/main/java/com/tmp/capability/sample/SampleTechnicalCapabilitiesAutoConfiguration.java`

### Files modified

- `tmp-architecture-tests/pom.xml` (dependency `tmp-capability-engine`)
- `tmp-capability-engine/src/main/java/com/tmp/capability/CapabilityEngineAutoConfiguration.java` (name-based AutoConfigureAfter)
- `tmp-capability-engine/src/main/resources/META-INF/spring/...AutoConfiguration.imports`
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/BootstrapConfiguration.java` (removed sample beans)
- `tmp-bootstrap-app/src/main/java/com/tmp/bootstrap/DesktopBootstrap.java` (dynamic registeredCapabilities listing)
- `tmp-bootstrap-app/src/main/resources/application.yml`, `application-test.yml` (`tmp.capability.sample.diagnostic: true`)

### Tests added or changed

- `Stage3CapabilityEngineArchitectureTest`: 8 module-boundary rules.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-architecture-tests test -Dtest=Stage3CapabilityEngineArchitectureTest,Stage0ArchitectureBaselineTest,Stage1PlatformCoreArchitectureTest,Stage2DocumentEngineArchitectureTest` | PASSED |
| `mvn -q -pl :tmp-architecture-tests verify` | PASSED |

### Architecture review

- No Stage 0/1/2 rules modified.
- Bootstrap no longer depends on `com.tmp.capability.sample..`; only public API.

### Next task

STAGE3-021

---

## `STAGE3-019` ? PostgreSQL Testcontainers integration test for document contribution

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

???????? `CapabilityEngineDocumentPostgresIntegrationIT` ? ????? ??????????: ??????????? document type ????? ?????? ???? Capability Engine ?? PostgreSQL, document lifecycle (create/post/query), rollback ??? duplicate document type. ?????????? ?????? Testcontainers: Spring Boot BOM (1.19.8) ?????????? parent `testcontainers.version` 1.21.4 ? ????????? ????? managed dependencies ? parent pom ??? ????????????? ? Docker Desktop API 1.55.

### Files created

- `tmp-capability-engine/src/test/java/com/tmp/capability/CapabilityEngineDocumentPostgresIntegrationIT.java`
- `tmp-capability-engine/src/test/resources/testcontainers.properties`

### Files modified

- `pom.xml` (parent: explicit Testcontainers 1.21.4 managed deps override Spring Boot BOM)
- `tmp-capability-engine/pom.xml` (surefire DOCKER_HOST + docker.host for Windows Docker Desktop)

### Tests added or changed

- `CapabilityEngineDocumentPostgresIntegrationIT`: 3 ordered PostgreSQL-backed scenarios.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityEngineDocumentPostgresIntegrationIT` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- Reuses `postgres:16-alpine` and Flyway migrations from existing document-engine IT pattern.
- No production API changes.

### Next task

STAGE3-020

---

## `STAGE3-020` ? Concurrency tests

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

???????? `CapabilityLifecycleConcurrencyTest` ? ????? ?????????????????? concurrency-??????????: 8-???????? ??????????? ?????? capability id (????? ???? ??????????), 200-???????????? ????? activateAll vs deactivate ??? ?????? partial state, reader/writer snapshot safety ??? `registeredCapabilities()` ? `activeCommands()`.

### Files created

- `tmp-capability-engine/src/test/java/com/tmp/capability/lifecycle/CapabilityLifecycleConcurrencyTest.java`

### Tests added or changed

- `CapabilityLifecycleConcurrencyTest`: concurrent registration, activation/deactivation race, snapshot consistency.

### Verification

| Check | Result |
|---|---|
| `mvn -q -pl :tmp-capability-engine test -Dtest=CapabilityLifecycleConcurrencyTest` | PASSED |
| `mvn -q -pl :tmp-capability-engine verify` | PASSED |

### Architecture review

- No production code changes; latch/barrier synchronization only (no sleep-based sync).

### Next task

? (stop before Stage 4)

---

## `STAGE3-021` ? Final Stage 3 verification gate

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

????????? ????????? ??????????? ??????????? Stage 3: ?????? reactor `mvn clean verify`, package profile `mvn clean verify -Ppackage`, ?????? ?????? `dist/jpackage/TMP/TMP.exe` ? PostgreSQL (Docker `postgres:16-alpine`, `TMP_DB_*` env vars). Spring context, Flyway migrations ? DesktopBootstrap ???????? ??? ??????.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` (full reactor) | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `dist/jpackage/TMP/TMP.exe` + PostgreSQL | PASSED |

### Architecture review

- Stage 3 exit criteria satisfied; no Stage 4 work started.

### Next task

? (stop before Stage 4)

---

## `STAGE3-022` ? Stage 3 acceptance rework (BLK-014)

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

????????? ??????????? ??????? acceptance review: reversible public API Stage 1?2, ????????? ??????????? ? compensation handles, ?????????? ??????????? ? lifecycle failure handling, ???????????? event subscriptions.

### Verification

| Check | Result |
|---|---|
| `mvn -q test -pl :tmp-capability-engine` | PASSED |
| SpotBugs lock-path fix | PASSED |

### Next task

STAGE3-023

---

## `STAGE3-023` ? Re-verification gate after acceptance rework

**Date:** 2026-07-22
**Stage:** Stage 3 ? Capability Engine
**Status:** DONE

### Result

`mvn clean verify`, `mvn clean verify -Ppackage`, manual `TMP.exe` ? PostgreSQL ? ??? PASSED.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `TMP.exe` | PASSED |

### Next task

? (stop before Stage 4)



---

## `STAGE3-024` - Lifecycle contribution cleanup (BLK-015)

**Date:** 2026-07-23
**Stage:** Stage 3 - Capability Engine
**Status:** DONE

### Result

Unified `cleanupContributions` / `cleanupFailedCapability` for initialize/activation/stop/deactivation failures. `DEACTIVATED` only after successful full cleanup. `unsubscribeAll` aggregates failures. Original lifecycle exceptions are preserved; cleanup failures are suppressed.

### Verification

| Check | Result |
|---|---|
| Lifecycle cleanup acceptance tests | PASSED |
| PostgreSQL IT (init failure cleanup) | PASSED |

### Next task

STAGE3-025

---

## `STAGE3-025` - Re-verification gate after BLK-015

**Date:** 2026-07-23
**Stage:** Stage 3 - Capability Engine
**Status:** DONE

### Result

`mvn clean verify`, `mvn clean verify -Ppackage`, manual `TMP.exe` with PostgreSQL - all PASSED.

### Verification

| Check | Result |
|---|---|
| `mvn clean verify` | PASSED |
| `mvn clean verify -Ppackage` | PASSED |
| Manual `TMP.exe` | PASSED |

### Next task

- (stop before Stage 4)

---

## `STAGE4-000` - Stage 4 Start Gate and decomposition (planning only)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE (planning); implementation of `STAGE4-001` not started, per explicit user instruction to stop after planning

### Result

Start Gate checks performed by reading only file contents (no Git commands used, per absolute Git prohibition): confirmed Stage 0-3 DONE 100%, last completed task `STAGE3-025`, all Stage 0-3 blockers RESOLVED, Stage 4 was `PLANNED 0%`, Java 21 (`.tools/jdk-21.0.11+10`) and portable Maven (`.tools/apache-maven-3.9.9`) available, Docker available (`DOCKER_HOST=npipe:////./pipe/docker_engine`). Ran the mandatory baseline `mvn clean verify` for the full reactor (log: `stage4-000-baseline-verify.log`, gitignored) ? BUILD SUCCESS, including PostgreSQL Testcontainers integration tests (`FlywayPostgresIntegrationIT`, `DocumentEnginePostgresIntegrationIT`, `CapabilityEngineDocumentPostgresIntegrationIT`). No regression found; Stage 4 was safe to start.

Read the mandatory source set for Stage 4 planning: `Security-Specification.md` (full), relevant `Database-Specification.md` sections (schema-per-module, identifiers, technical fields, optimistic locking, transactions, Flyway, naming, module boundaries, JPA/persistence, audit, appendices), relevant `TMP-Architecture-Decisions.md` ADRs (ADR-001..003, ADR-019..022 ? none are Security-specific; no Security/audit/transaction-specific ADR exists), relevant `UI-UX-Specification.md` sections (main window, navigation, screens, FXML, Controller, ViewModel, messages, mandatory technical screens), `Capability-Engine-Specification.md`'s public API in full (`com.tmp.capability.api.*` ? `Capability`, `CapabilityEngine`, `CapabilityDescriptor`, `PermissionDescriptor`, `CommandDescriptor`, `NavigationContribution`, `ViewDescriptor`, `PublicServiceContribution`, `DocumentContribution`, `EventContribution`, `SettingsContribution`, `CapabilityId`, `CapabilityVersion`, `CapabilityLifecycleState`, `CapabilityRuntimeAccess`), `Platform-Core` public API in full (`com.tmp.core.api.*` ? `PlatformCore`, `ServiceRegistry`, `PlatformComponent`, `PlatformComponentMetadata`, `LifecycleManager`), plus the current (pre-Stage-4) state of `tmp-ui-shell` (`JavaFxShellApplication`/`JavaFxShellLauncher`/`EmptyMainShell`), `tmp-bootstrap-app` (`DesktopBootstrap`/`BootstrapConfiguration`/`TmpBootstrapApplication`), `tmp-infra-db` (Flyway/datasource wiring), and the existing Flyway migration numbering across `tmp-infra-db`/`tmp-document-engine` (highest existing version `V3`; `tmp-capability-engine` has no persistence) to determine the next free migration version (`V4`) for `tmp-security`. Did not load full internal implementations of Platform Core, Document Engine, or Capability Engine beyond what was needed to confirm lifecycle-ordering and discovery mechanics (`DefaultLifecycleManager.startAll()` ordering, `CapabilityEngineAutoConfiguration`'s `List<Capability>` discovery wiring) cited in the Design decisions.

Produced the full Stage 4 decomposition directly in `WORK-QUEUE.md`: a "Design decisions fixed for this Stage" preamble (14 points covering module/package layout, persistence technology choice matching Stage 2 precedent, Flyway version numbering, `PermissionId` format resolution, Capability Engine integration pattern, startup ordering proof, UI-screen module placement decision and navigation-permission-gating convention, `JavaFxShellLauncher` static hand-off extension, bootstrap config convention, BCrypt technology choice, case-insensitive login uniqueness mechanism, audit-ownership justification, and explicit confirmation that all Stage 4 ?4.18 exclusions remain absent) followed by 40 fully-specified tasks (`STAGE4-001`..`STAGE4-040`) covering: Domain value objects and aggregates (`STAGE4-002`..`009`); Flyway migration and JDBC persistence (`STAGE4-010`..`014`); BCrypt hashing (`STAGE4-015`); Capability integration and permission synchronization (`STAGE4-016`..`017`); bootstrap administrator (`STAGE4-018`..`019`); session/authentication/authorization (`STAGE4-020`..`022`); user/role/permission/audit Application Services (`STAGE4-023`..`027`); the public `com.tmp.security.api` facade (`STAGE4-028`); Spring auto-configuration and startup wiring (`STAGE4-029`); an end-to-end PostgreSQL IT (`STAGE4-030`); `tmp-ui-shell` Navigation Service foundation and all five mandatory screens plus bootstrap integration (`STAGE4-031`..`038`); Stage 4 architecture tests (`STAGE4-039`); and the Final Stage 4 verification gate (`STAGE4-040`). No blocker was raised ? every design ambiguity encountered (persistence technology, `PermissionId` format vs. the spec's informal examples, UI-screen module placement, navigation-permission-gating mechanism, and audit ownership vs. the Database Specification's non-binding recommendation) was resolvable from existing approved documents/precedent and is recorded as a documented design decision rather than a blocker, per governance ?5 ("??????? ??????????? ???????, ?????????? ????????? ?? ????????????, ???????? ??????????????").

Updated `STATUS.md` (Stage 4 ? IN_PROGRESS 0%, Current Task ? `STAGE4-001` READY) per the user's explicit final instruction: stopped immediately after planning and filling in the work plan, before starting `STAGE4-001` implementation.

### Verification

| Check | Result |
|---|---|
| Start Gate file-content checks (STATUS/WORK-QUEUE/BLOCKERS/STAGE-4-SECURITY.md/root `pom.xml`/existing modules/Stage 0-3 sources) | PASSED |
| `mvn clean verify` (full reactor, Stage 4 baseline) | PASSED (BUILD SUCCESS, incl. PostgreSQL Testcontainers ITs) |
| Stage 4 decomposition completeness against governance required task fields | PASSED (all 40 tasks include Goal/Required documents/Code context/Allowed scope/Forbidden/Implementation requirements/Public contracts/Acceptance criteria/Required tests/Verification commands/Documentation updates/Expected result) |

### Next task

STAGE4-001 (not started ? awaiting next user instruction to resume autonomous execution)

---

## `STAGE4-001`..`STAGE4-018` - Domain, persistence, Capability, bootstrap (autonomous batch)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE through STAGE4-018 (implementation); STAGE4-019+ pending

### Result

Created Maven module `tmp-security` (reactor + dependencyManagement). Implemented public identity VOs; domain aggregates/ports (User, Role, PermissionDefinition, assignments, overrides, EffectivePermissionCalculator, SecurityAuditEvent); Flyway `V4__security_schema.sql`; JDBC adapters; BCrypt hasher; Security Administration Capability (12 permissions); PermissionSynchronizationApplicationService; BootstrapAdministratorApplicationService + `TMP_SECURITY_*` properties. JDBC repository tests use PostgreSQL Testcontainers (H2 cannot apply `lower(login)` unique index).

### Next task

STAGE4-019 / session / authentication / authorization / UI / final gate

---

## `STAGE4-024`..`STAGE4-032` - Password/roles/API/wiring/E2E/UI login (autonomous)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE through STAGE4-032

### Result

Completed password/role/permission/audit application services; public `com.tmp.security.api` facades + credential-leak reflection test; `SecurityAutoConfiguration` / `SecurityPlatformComponent` (non-final `@Transactional` services for CGLIB; `Clock` `@ConditionalOnMissingBean`); `SecurityEndToEndPostgresIntegrationIT` (bootstrap>permissions, mutation+audit rollback via controllable audit repo, inactive-capability denial); `tmp-ui-shell` Navigation Service + Login Screen/FXML/ViewModel; `UiShellEntryPoint` hand-off; bootstrap tests moved from H2 to PostgreSQL Testcontainers (V4 `lower(login)` index).

### Next task

STAGE4-033 (Main Window permission-filtered navigation)

---

## `STAGE4-033`..`STAGE4-039` - Main Window, admin screens, bootstrap finalization, architecture (autonomous)

**Date:** 2026-07-23
**Stage:** Stage 4 - Security
**Status:** DONE through STAGE4-039

### Result

Completed Main Window (permission-filtered nav), Access Denied + `SecuredOperationDemo` bypass-prevention proof, User/Role/Audit admin screens (ViewModels + FX controller tests), login-gated bootstrap (`UiShellEntryPoint` only; logout/stop clear session). Moved UI Spring wiring to `com.tmp.bootstrap.UiShellAutoConfiguration` and introduced `ShellNavigationCatalogue` so `com.tmp.ui..` stays free of Spring/Capability (Stage 0/3 rules). Promoted `SecurityPermissions` and `RoleInUseException` to `com.tmp.security.api`. Added `Stage4SecurityArchitectureTest` (ArchUnit + pom scan).

### Next task

STAGE4-040 (final Stage 4 gate)

---

## `STAGE4-041`?`STAGE4-048` ? BLK-016 corrective (autonomous)

**Date:** 2026-07-23  
**Stage:** Stage 4 ? Security  
**Status:** DONE

### Result

Closed Stage 4 acceptance defects (BLK-016):

- Auth audit/session separation + dummy BCrypt timing mitigation + PostgreSQL auth IT.
- Atomic bootstrap via `pg_advisory_xact_lock`, unique `lower(roles.name)`, concurrent bootstrap IT.
- Removed bootstrap secret defaults from `application-dev.yml`.
- Flyway `V5__permission_ownership_and_role_name_unique.sql`; permission ownership sync.
- Deleted-user authorization + session clear; restart-safe document contribution registration.
- VERIFICATION-LOG Latest result + STAGE4-019..023 honest batch note.

### Next task

STAGE4-040 formal Stage 4 close (Stage 5 not started).

---

## `STAGE4-049`?`STAGE4-052` ? BLK-017 residual corrective (implementation)

**Date:** 2026-07-23
**Stage:** Stage 4 ? Security
**Status:** DONE

### Result

Closed residual Stage 4 acceptance defects (BLK-017 implementation):

- `PermissionDefinition.claimLegacyOwnership` + sync legacy claim; V4?V5 PostgreSQL upgrade IT preserves role permissions/overrides; post-claim ownership conflicts still rejected.
- `logout` closes session in `finally` even when logout audit fails; unit + PostgreSQL IT with controllable audit.
- `login` closes prior session before credential attempt; failed login leaves no session; switch-user opens only new session.
- `UserStatus` re-checked before session open; deterministic login-vs-delete race IT denies usable session for DELETED user.

### Files created

- `tmp-security/src/test/java/com/tmp/security/PermissionOwnershipUpgradePostgresIntegrationIT.java`
- `tmp-security/src/test/java/com/tmp/security/LoginDeleteRacePostgresIntegrationIT.java`
- `tmp-security/src/test/java/com/tmp/security/support/ControllableUserRepository.java`

### Files modified

- `PermissionDefinition.java`, `PermissionSynchronizationApplicationService.java`, `AuthenticationApplicationService.java`
- matching unit/IT tests; control docs (STATUS, WORK-QUEUE, BLOCKERS, IMPLEMENTATION-LOG, VERIFICATION-LOG)

### Tests added or changed

- Domain/unit: claimLegacyOwnership, legacy sync claim, logout audit-failure, prior-session login failures, deleted-after-credential-check.
- PostgreSQL: upgrade ownership IT; auth logout/prior-session ITs; login/delete race IT.

### Verification

Focused module tests PASSED prior to STAGE4-053 full reactor gate.

### Next task

STAGE4-053 automated verification; then STAGE4-040 after user GUI confirmation.

---

## `STAGE4-053` ? Automated verification after BLK-017 fixes

**Date:** 2026-07-23
**Stage:** Stage 4 ? Security
**Status:** DONE

### Result

Automated gate after BLK-017 residual fixes:

- `mvn clean verify` ? PASSED
- `mvn clean verify -Ppackage` ? PASSED
- Detached `dist/jpackage/TMP/TMP.exe` against PostgreSQL prepared at Flyway V4 with seeded V4 permission definition ? app stayed alive; Flyway applied V5; `security.users.view.owner_capability_id = security-administration`

Stage 4 not closed; Stage 5 not started; STAGE4-040 awaits user packaged GUI confirmation.

### Next task

STAGE4-040 final Stage 4 gate (user GUI smoke).

---

## `STAGE4-054` ? Final Stage 4 close after manual packaged GUI confirmation

**Date:** 2026-07-24  
**Stage:** Stage 4 ? Security  
**Status:** DONE

### Result

User confirmed manual packaged GUI smoke for `dist/jpackage/TMP/TMP.exe` against Docker PostgreSQL (`tmp-stage4-pg` / DB `tmp_gui_stage4` / user `tmp`). Full checklist PASSED (clean DB start, login/wrong-password/neutral message, main window, Users/Roles/Audit screens, logout/relogin, restart without bootstrap env, single admin + Security Administrator, no secrets in logs, clean process exit).

Closed final Stage 4 verification gate and STAGE4-040. Registered non-blocking `BACKLOG-001` (Security Audit pagination text encoding) ? not fixed in this close. Stage 5 not started. No production-code changes. No Git operations.

### Files modified

- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/IMPLEMENTATION-LOG.md`
- `docs/development-control/VERIFICATION-LOG.md`
- `docs/development-control/BLOCKERS.md`

### Verification

User-confirmed manual packaged GUI checklist PASSED; prior STAGE4-053 automated gate retained as evidence.

### Next task

None (Stage 4 COMPLETE). Stop before Stage 5. Optional later: `BACKLOG-001`.

---

## `STAGE5-000` ? Stage 5 Start Gate and Specification Reconciliation

**Date:** 2026-07-24  
**Stage:** Stage 5 ? Order Management  
**Status:** DONE (documentation & planning only)

### Nature of task

???????????????? ? ????????????? ??????. **Java-??? ?? ?????????.** ?? ??????????? ?????? `tmp-order-management`, ????????, ?????, FXML/CSS; root `pom.xml` ?? ?????????. Git-??????? ?? ???????????.

### Documents updated

- `docs/TMP/TMP_Initial_Documents/architecture/10-Order-Management/Order-Management-Specification.md` ? **v1.1**.
- `docs/development-control/stages/STAGE-5-ORDER-MANAGEMENT.md` ? ?????? Stage Manifest.
- `docs/development-control/CONTEXT-MAP.md` ? ?????? ?Stage 5 ? Order Management Context? + ?????? Stage 5.
- `docs/development-control/WORK-QUEUE.md` ? `STAGE5-000` (DONE) + ??????? `STAGE5-001..038`.
- `docs/development-control/STATUS.md`, `BLOCKERS.md`, `VERIFICATION-LOG.md` ????????????????.

### Architectural contradictions resolved (Etap A)

1. ???????? `Production Status` ?????? Order Item ? ???????; Production ??????? ???????????????? ?????????? (ADR-019; Production Spec ?4?5).
2. ???????? ????????????? ? ????????????????? ?????????? ????? ? ????????? ?? 4 ????? (?11/?12.2/?12.3 + Production).
3. ??????????? ????????? ???????? ? ???????? ?????????? (v1.0 ?13.1) ? ???????; ???????? ? ????????? ????????? (?5.12, ADR-019).
4. ?Production ???????? ???? Order Management? ? ?????????????????: Production ?????? ??????????? ????????? ?? `Order Item ID + Revision`, ?? ?????? Order Management (Production Invariant 1).
5. ???????????????? ??????? ? Public API ? ??????; Query DTO ???????? ?????? ?????? Order Management.
6. ?????? mutating-???????? ??? ??????? Public API ? ????????? ?? Query API ? ?????????? Application API (ADR-003/004).
7. ?????????? ??????-?????????? ? ?????? ??????? ?16.1 (9 ?????????? ? 9 ??????).
8. ?????????? transition matrices ? ????????? ??? Order/Item/Revision.
9. ???????? ??????????? ????? ??????????? ? ????????????? immutability (ADR-018).
10. ???????? ?????? Order Item/Revision ? ????????????? (?8/?9): ?????????? `Order Item ID`, `current` Revision.
11. ???????? ????? ??????????????????????????????????? ? ??????? ? ?16.1.
12. ?????? Order Management ?? ?????? ?????? Capability ? persistence scope ?19 ? ????? ???????? ?19.1.
13. ???? capability `order.read`/`order.item.revision.create` ??? 3-??????????? ??????? Security `PermissionId` ? ????????? ? ??????? ?18.1.
14. ??????? ?????? `IN_PROGRESS`/`COMPLETED` ??? ????????????? ???????? ? ????????? ?? Stage 5, ?????????? ? ??????? ?????????? Order?Production (?11.1, ?5.7 ??????).

### Queue formation

??????? `STAGE5-001..038` ???????????? **?????? ?????** ??????????? documentation gate. ?????? `STAGE5-001` ? `READY`; ????????? ? `PLANNED` ? ??????????? ?????????????. ?????????? `STAGE5-001` ?? ??????????.

### Next task

`STAGE5-001` ? Bootstrap `tmp-order-management` module and architecture boundaries (?? ??????? ????????????). Stage 6 ?? ??????????.

---

## `STAGE5-000-FIX` ? Stage 5 Documentation Gate Corrections

**Date:** 2026-07-24  
**Stage:** Stage 5 ? Order Management  
**Status:** COMPLETED (documentation & planning only)

### Nature of task

???????????????? ? ????????????? ??????. **Java-??? ?? ?????????.** ?? ??????????? ?????? `tmp-order-management`, ????????, ?????, FXML/CSS; root `pom.xml` ?? ?????????. Git-??????? ?? ???????????. ?????????? `STAGE5-001` ?? ??????????. ?? ????? ??????????? `STAGE5-001` ??? ????????? ? `PLANNED`, ?????? `STAGE5-000-FIX` ???? ???????????? `READY`; ????? ????????? gate `STAGE5-001` ????????? ? `READY`.

### Decisions taken (????????????????, ??????? ??? ???????? ????????)

- **Capability-owned payload:** ??????? ???????, ??? Document Engine ??????? lifecycle/metadata, ? Order Management ? ?????? ?????????????? payload, ????????? ?? `DocumentId`, versioned, ? optimistic locking (`PayloadRevision`) ? immutability ????? ??????????. Generic JSON ? Platform Core ?? ????????????.
- **Revision model:** ????????? ? ????????? `activeRevision` (immutable, ?????? ????????) ? `draftRevision` (? 1, ????????????? ?????????). ???????? ???????? `ORDER_ITEM_REVISION_UPDATE`; `ORDER_ITEM_UPDATE` ????????? ????????????? ??????.
- **Query API:** ?????????/???????? ? `searchOrders`, ?????? items/revisions, ????????? (default 50, max 100, zero-based), ?????????? ?????????? (`createdAt DESC, orderId DESC`), sort whitelist; ?????? ???????? ?????? active Revision.
- **Cancellation:** ?????????? ?????? ???????????? ???????? ? ? Stage 5 ????????? `APPROVED?CANCELLED`, `ACTIVE?CANCELLED` ? ????????? ??????? approved order; ?????????? ? future integration.
- **Document lifecycle policy:** ?????????? ? `onPost` (idempotent, load payload by `DocumentId`), `onUnpost` = NOT SUPPORTED, `onClose` = no business change, `onDelete` = draft only (payload removed); idempotency ????? processing record (`DocumentId + Operation`).
- **Constitution/ADR:** ???????? ? Constitution v1.2 ??????? 28 ? ADR-003/ADR-004: ????????? ? ??????-?????????; ?????? ? Public Query API; ??????????? ? Domain Events; Query API/Events ?? ??????? document-driven ?????????.

### Transaction boundary verification (?? ???????????? ????????? Document Engine)

???????? ??? ? ????? `tmp-document-engine`. `DefaultDocumentEngine` `@Transactional`; `DocumentProcessor.onPost(context)` ?????????? ?????? ?????????? ??????????; `DocumentId` ???????? ????? `context.document().id()` (`DocumentOperationContext` ? `DocumentMetadata.id()`); Domain Events ??????????? ?????? ????? commit (`TransactionAfterCommitEventPublisher`); ????? ? processor ?????????? ???????? ? ?? ????????? ??????? (`DefaultDocumentEngineTransactionEventTest`). ????????? ??????????? ????????????? ???????????? ??????????. (????????? ? STAGE5-000-FIX2: ?????????????? ???????? ??????? ? ????????? Document Engine Specification v1.1 ? ????????? `TransactionalEventPublisher`; ? ??????? ???????? ?????? ?????????? ????????? `STAGE5-017` ?? ??????? Document Processor ? ????? ?????? `STAGE5-046`.)

### Documents updated

- `docs/TMP/TMP_Initial_Documents/architecture/00-Constitution/TMP-Constitution.md` ? **v1.2** (??????? 28; ??????? ?.16).
- `docs/TMP/TMP_Initial_Documents/architecture/05-ADR/TMP-Architecture-Decisions.md` ? **v1.3** (???????? **ADR-028**; ???????? ADR-003, ADR-004; ??????? ? ??????? ?????????).
- `docs/TMP/TMP_Initial_Documents/architecture/10-Order-Management/Order-Management-Specification.md` ? **v1.2**.
- `docs/development-control/stages/STAGE-5-ORDER-MANAGEMENT.md` (Stage Manifest) ???????? ??? v1.2.
- `docs/development-control/CONTEXT-MAP.md` ? ????????? ??????????? ?????? Stage 5 (payload model/persistence, processor lifecycle, processing idempotency, revision draft workflow, query search and pagination, transaction boundary verification).
- `docs/development-control/WORK-QUEUE.md` ? ??????? Stage 5 ????????? ??????????? (`STAGE5-001..050`).
- `STATUS.md`, `BLOCKERS.md`, `VERIFICATION-LOG.md` ????????????????.

### Queue rebuild

??????? Stage 5 ??????????? ?????? (??? Stage 5 ?? ?????????): `STAGE5-001..050` ? ???????? module bootstrap ? boundaries ? identifiers ? aggregates ? active/draft revision ? immutable specification ? repository ports ? query API ? paginated search ? typed payload models ? payload use cases ? payload persistence port ? payload schema ? processing record & idempotency ? document type registration ? lifecycle policy base ? transaction boundary verification ? Document Processors (???? ??? ?? ??????) ? aggregate persistence ? migrations ? security ? UI ? tests (unit/persistence/lifecycle/idempotency/rollback) ? architecture tests ? full reactor ? packaged ? manual GUI smoke. ?????? ?????? ???????? ID, Title, Status, Depends on, Goal, Scope, Out of scope, Required documents, Required code context, Files allowed to change, Acceptance criteria, Verification commands, Documentation updates, Stop conditions. ?????? `STAGE5-001` ? `READY`.

### Next task

`STAGE5-001` ? Bootstrap `tmp-order-management` module (?? ??????? ????????????). Stage 6 ?? ??????????. Git-???????? ????????? ????????????.

---

## STAGE5-000-FIX2 ? Final Documentation Corrections

**???:** ?????? ????????????. Java-???, Maven-??????, `pom.xml`, ????????, ?????, ????? `STAGE5-001` ? Git ? ?? ?????????????.

**????????? ????????? (?? ????? ?????):** `STAGE5-000-FIX2` ????????? ??? ???????????? `READY`; `STAGE5-001` ????????? ? `PLANNED`; ? `STATUS.md` ????????????? ????????? ???????? Documentation Gate.

### ????????? ???????????

1. **????????? after-commit ????????.** Document Engine Specification ? **v1.1**: ???????????? ?????????????? ???????? lifecycle-???????? (`DocumentId` ? operation context; ????? processor ?????? ??????????; ????????? ????? ????????? Capability, metadata ? lifecycle journal; ???????? capability-owned payload ?? `DocumentId`; ???????? metadata/lifecycle vs typed payload) ? ????????? ???????? `TransactionalEventPublisher { void publishAfterCommit(DomainEvent); }` ?? Spring transaction synchronization. Order Management ????????? ?? ????????? ???????? (OM Spec ?12/?17/?21, Manifest ?11, CONTEXT-MAP) ? **?? ???????????** ?????????? ?????? Document Engine. ????????? prerequisite-?????? `STAGE5-017` ?? ??????? Document Processor.
2. **?????????? ?????? typed payload.** OM Spec ?11.5/?19: `order_document_payload` (????? metadata, ???? `document_id`) + typed-??????? ?? ?????????? ????????? + `order_item_revision_payload_line`; ????? ????? `document_id` (FK), optimistic lock `payload_revision`, ????????? ???????? Draft, immutability ????? ??????????, ????????? ? ?????????? ????????; JSON/???????????? ?????????. ????????? ?????? persistence/migrations (`STAGE5-016`, `STAGE5-020`, `STAGE5-043`).
3. **Idempotency.** OM Spec ?14.1/?16, Manifest ?10: ????????? ????????? `DocumentEngine.postDocument` ???????????? ????????? ??????????? lifecycle validation; guard ?????? processor ??? ???????????? processing record ??????????? ??? already processed (??? ?????????? ?????????/???????/??????); `resultReference` ??????????; `DocumentProcessor.onPost()` ? `void`. ??????? ?????????? ???????? ???????????? ??????????. ????????? `STAGE5-018`, `STAGE5-045`.
4. **????????????? ??????/???????.** CONTEXT-MAP: ?Order Management Specification **v1.2**?; Manifest ?17/?19: `STAGE5-001..STAGE5-050`, GUI smoke `STAGE5-050`.
5. **?????????? ??????? Stage 5 (50 ?????).** ??????? ?8: ??????/??????? ? ???????? ?????? ? Query API ? typed payload model ? typed payload persistence ? ????????? `TransactionalEventPublisher` (`STAGE5-017`) ? processing record & idempotency ? Document Processors ? ??????? ? UI ? ????? ? ????????? ???????? ? GUI smoke. ?????? ?????? ???????? ???????????, scope, acceptance criteria, ??????????? ?????, verification commands, stop conditions.

### Documents updated

- `docs/TMP/TMP_Initial_Documents/architecture/07-Document-Engine/Document-Engine-Specification.md` ? **v1.1**.
- `docs/TMP/TMP_Initial_Documents/architecture/10-Order-Management/Order-Management-Specification.md` ? **v1.2** (rev. STAGE5-000-FIX2: ?11.5, ?12, ?14.1, ?16, ?17, ?19, ??????????, ???????).
- `docs/development-control/stages/STAGE-5-ORDER-MANAGEMENT.md` (?5/?10/?11/?12/?17/?18/?19/?20).
- `docs/development-control/CONTEXT-MAP.md` (v1.2; ????????? `TransactionalEventPublisher`; payload persistence ???. ??????; ?????? ?????????? ????? ?? OM-?????????).
- `docs/development-control/WORK-QUEUE.md` ? ??????? Stage 5 ??????????? (`STAGE5-001..050`) + `STAGE5-000-FIX2` (COMPLETED).
- `docs/development-control/STATUS.md`, `BLOCKERS.md` (DOC-BLK-7..10 CLOSED), `VERIFICATION-LOG.md`.

?????? `STAGE5-001` ? `READY`; ????????? ? `PLANNED`. ?????????? `STAGE5-001` ?? ??????????.

### Next task

`STAGE5-001` ? Bootstrap `tmp-order-management` module. Stage 6 ?? ??????????. Git-???????? ????????? ????????????.

---

## Stage 5 execution module 5.1 ? Order Management bootstrap and boundaries

????????? ?????? `STAGE5-001..003` (?????? ?????????????? ?????? Stage 5). ??????-????????, ?????????, payload, persistence adapters, ???????? ? UI ?? ???????????????. Git-???????? ?? ???????????.

### STAGE5-001 ? Bootstrap `tmp-order-management` module

- ?????? ????? Maven-?????? `tmp-order-management` (`jar`), ????????? ? reactor ? root `pom.xml` (????? `tmp-security`, ????? `tmp-bootstrap-app`); ????????? ?????? ? `dependencyManagement` (`com.tmp:tmp-order-management:${project.version}`).
- ??????????? ?????? ? ?????? ??????, ??????? ??????????? ????????? API: `tmp-platform-core` (`com.tmp.core.api`), `tmp-document-engine` (`com.tmp.document.api`), `tmp-capability-engine` (`com.tmp.capability.api`), `tmp-security` (`com.tmp.security.api`), ???? `spring-boot-starter`, `spotbugs-annotations` (provided), `junit-jupiter` (test). JavaFX ? JPA ?? ????????????.
- ?????? package skeleton (?????? `package-info.java`, ??? ???????): `com.tmp.order.api|domain|application|persistence|capability`. `api` ?????? ??? ???????????? ??????? ???????? ??? mutating-????????; ?????????? ?????? ???????? ??? ??????????? ?????.
- **Files changed:** `pom.xml` (reactor + dependencyManagement), `tmp-order-management/pom.xml`, ???? `package-info.java`.
- **Verification:** `mvn -q -pl tmp-order-management -am validate` ? PASSED.

### STAGE5-002 ? Architecture boundaries and dependency rules

- ???????? ????? ArchUnit-????? `Stage5OrderManagementArchitectureTest` (`tmp-architecture-tests`) ? 10 ????????? ?????? Stage 5:
  - ??????? ?????? ??????? ?? Order Management ?????? ????? `com.tmp.order.api..` (??? ???????? mutating API);
  - `com.tmp.order.api` ?? ??????? ?? `domain/application/persistence/capability`;
  - Order Management ??????? ?? Document Engine, Capability Engine, Platform Core, Security **??????** ????? ?? `*.api..` (? ?.?. ????? ??????? ?no internal Document Engine imports?);
  - ?????????? ?????? Order ?????????? ?????? ??????????? ????????? API + JDK/Spring/JDBC;
  - Order ?? ??????? ?? warehouse/production/cutting/analytics/ui/bootstrap (??? production-owned ?????? ????? ??????);
  - ????? Order ???????? ?? Spring/JPA/Hibernate/JavaFX;
  - ?????? Order ?? ??????? ?? JavaFX.
- `tmp-architecture-tests/pom.xml`: ????????? ??????????? ?? `tmp-order-management`.
- `Stage4SecurityArchitectureTest`: ??????? `stage5PlusBusinessPackagesDoNotExist` ????????????? ? `stage6PlusBusinessPackagesDoNotExist` ? ?????? (?????? `com.tmp.order..`, ??????? ?????? ????????? ???????? Stage 5; warehouse/production/cutting/analytics ??-???????? ?????????). ?????? ??????? Stage 0?4 ?? ??????????.
- ??????? ???????? ?? ?????? (skeleton) ??????: 10/10 rules, 0 failures.
- **Files changed:** `tmp-architecture-tests/pom.xml`, `Stage5OrderManagementArchitectureTest.java` (new), `Stage4SecurityArchitectureTest.java` (???? ???????).
- **Verification:** `mvn -q -pl tmp-architecture-tests -am test` ? PASSED (`Stage5OrderManagementArchitectureTest`: Tests run: 10, Failures: 0, Errors: 0).

### STAGE5-003 ? Identifiers and common value objects

- ????????? ???? (`com.tmp.order.api`): `OrderId`, `OrderItemId` (UUID identity, `of`/`generate`, immutable, equals/hashCode/toString); `RevisionNumber` (int `>= 1`, `first()`/`next()`/`isAfter`, `Comparable`, ????????????); ??????? `OrderStatus` (`DRAFT/APPROVED/CANCELLED`), `OrderItemStatus` (`DRAFT/ACTIVE/CANCELLED`), `RevisionStatus` (`DRAFT/APPROVED`) ? ?????? Stage 5 ????????; production-??????? ????????? ?????????.
- ?????????? payload VO (`com.tmp.order.domain`, ?? ??????????? ?????? ? ????????????? ?11): `PayloadSchemaVersion` (int `>= 1`, `initial()`), `PayloadRevision` (long `>= 0`, `initial()` = 0, `next()`, optimistic lock ?????????).
- ????????, ???????? ????????, persistence, ????????? ? payload-?????? **??** ??????????? (scope STAGE5-004+). ???????? ???????? ???????? ?? STAGE5-004.
- Unit-????? (JUnit 5, 6 ???????, 37 ??????): ????????? ??????????????? (null/????????/????????????/equality), ?????????? `RevisionNumber`, ??????? ??????? ????????, ????????? ? ????????? payload VO.
- **Files changed:** `tmp-order-management/.../api/{OrderId,OrderItemId,RevisionNumber,OrderStatus,OrderItemStatus,RevisionStatus}.java`, `.../domain/{PayloadSchemaVersion,PayloadRevision}.java`, ????? ? `src/test/java/com/tmp/order/{api,domain}`.
- **Verification:** `mvn -q -pl tmp-order-management -am test` ? PASSED (37 tests, 0 failures, 0 errors).

### Execution module 5.1 gate

- `mvn -q -pl tmp-order-management -am test` ? PASSED.
- `mvn -q -pl tmp-architecture-tests -am test` ? PASSED (Stage 0?5 architecture tests green).
- ???????? ??????: `tmp-order-management` ? reactor; ??????-???????? ???????????; SQL/FXML/persistence adapters ???????????; production-owned ?????? ???????????; ?????? ?????????? ??????? ?????? ??????? ???????? ? ??????????? ArchUnit; ?????? ??????????? ?????? read-only Query surface (`com.tmp.order.api`), mutating API ?????? ???????????; ?????????????? ? Value Objects ??????? unit-???????; Stage 0?4 ?????????? ??????????.
- `STAGE5-004` **??** ?????????; ???????? `PLANNED`. Git-???????? ?? ???????????.

---

## Stage 5 execution module 5.2 ? Domain model and repository ports

### STAGE5-004 ? Domain aggregate: Customer Order

- ?????????? ??????? `CustomerOrder` (immutable, ?????? ?????????? ????? ?????????): `create` ? `DRAFT`; `approve` (`DRAFT?APPROVED`); `cancel` (`DRAFT?CANCELLED`); `updateCommercialData` ?????? ? `DRAFT`.
- ????????? ? ??????? ???????: `APPROVED?CANCELLED`, ????????? `APPROVED`, ???????? ?? `CANCELLED`, ????????? ???????????? ????? ??? `DRAFT`.
- VO: `OrderNumber`, `CurrencyCode`, `OrderDirection` (`PRIVATE/DEALER/CORPORATE`), `OrderCommercialData` (customerRef/Name, contractRef, siteRef, responsibleManager, direction, currency); ?????????? `InvalidOrderStateException`.
- ??????????? ?? 1 ???????? ???????? ????????? ?? application/document-processor (??????? ? ????????? ???????).
- **Verification:** `mvn -q -pl tmp-order-management -am test` ? PASSED.

### STAGE5-005 ? Domain aggregate: Order Item

- ?????????? ??????? `OrderItem`: `create` ? `DRAFT` ? `draftRevisionNumber=1`, `activeRevisionNumber=null`; `activate` (`DRAFT?ACTIVE`, ??????? active revision); `cancel` (`DRAFT?CANCELLED`); `updateCommercialData` ?????? ? `DRAFT`.
- ?????????: `ACTIVE?CANCELLED`, ????????? `ACTIVE`, ???????? ?? `CANCELLED`, ?????? draft-??????.
- VO: `ProductCode`, `ItemCommercialData` (productCode, name, comments ? ?????? ???? Spec ?5.2). Production Status / ???????????????? ?????????? ???????????.
- ????????? revision ??????????? package-???????? (`withActiveRevisionNumber` / `withDraftRevisionNumber` / `withoutDraftRevision`) ??? STAGE5-006.
- **Verification:** `mvn -q -pl tmp-order-management "-Dtest=OrderItemTest,CustomerOrderTest" test` ? PASSED.

### STAGE5-006 ? Active/draft Revision model

- `OrderItemRevision` (entity ? ??????? Order Item): `RevisionNumber`, `RevisionStatus`, `OrderedQuantity`, previous revision, `ItemSpecification`.
- `OrderItem` ??????? ?????? revisions: create ??????? Draft Rev 1; `createNextDraftRevision` ? N+1 ??? ????? active; ?1 draft; `approveDraftRevision` ???????? APPROVED + ????? active + clear draft + `DRAFT?ACTIVE` ??? ?????? ???????????; ?????????? active ???????????.
- ???????????? Revision immutable (`withOrderedQuantity` / ????????? `approved` ???????????).
- **Verification:** `mvn -q -pl tmp-order-management -am test` ? PASSED.

### STAGE5-007 ? Immutable Item Specification

- `ItemSpecification` ????????? ? `OrderItemId` + `RevisionNumber`; `SpecificationLine` (materialCode/Name, quantity > 0, unit, consumptionNorm ? 0).
- Draft ????????????? (`addLine`/`withLines`/`clearLines`); ????? `approveDraftRevision` ? `frozen()` / `isImmutable()`, ????????? ?????????; ????????? ??????? ? ?????? ????? ????? Draft Revision.
- `lines()` ?????????? unmodifiable copy; ??????? ????????? ?? ???????????? ?? ??????.
- **Verification:** `mvn -q -pl tmp-order-management "-Dtest=ItemSpecificationTest,OrderItemTest,OrderItemRevisionTest" test` ? PASSED.

### STAGE5-008 ? Repository ports

- ????? ? `com.tmp.order.domain.repository` (?????? ??????????): `CustomerOrderRepository` (save/findById/existsByOrderNumber), `OrderItemRepository` (save ??????? ???????? ? revisions/specs, findById, findByOrderId), `OrderItemRevisionRepository` ? `ItemSpecificationRepository` (find-by-typed-id; ?????? ????? `OrderItemRepository` ? ?????? ??????? ????????).
- `OptimisticLockConflictException` ??? ????????? `version` (Database Spec ?7). ?????????? ? `Optional.empty()`. ??? JDBC/Spring Data/JPA/SQL ?? ??????????.
- Contract-????? `RepositoryPortsContractTest` (4).
- **Verification:** `mvn -q -pl tmp-order-management -am test` ? PASSED; module gate + `mvn -q -pl tmp-architecture-tests -am test` ? PASSED.

### Execution module 5.2 gate

- `STAGE5-004..008` = DONE; `STAGE5-009` ???????? `PLANNED` (?? READY, ?? ??????).
- ????? ???????? ?? Spring/JPA/Hibernate/JavaFX; SQL ? persistence adapters ???????????; ??????? mutating API ???????????; Production-owned ?????? ???????????.
- Git-???????? ?? ???????????.

---

## Stage 5 execution module 5.3 ? Public Query API

### STAGE5-009 ? Public Query API contracts and DTO

- `OrderQueryService` (read-only interface): `getOrder`, `getOrderItems`, `getOrderItem`, `getOrderItemRevisions`, `getOrderItemRevision`, `getActiveOrderItemRevision`, `getItemSpecification`. Absence via `Optional` / empty page. No mutating methods.
- Immutable DTOs: `OrderDto`, `OrderSummaryDto`, `OrderItemDto` (only `activeRevisionNumber`, no draft), `OrderItemRevisionDto` (constructor rejects non-`APPROVED`), `ItemSpecificationDto`, `SpecificationLineDto`.
- Supporting types introduced for list signatures: `PageRequest`, `PageResult`, `OrderSort` (defaults/validation completed in STAGE5-010 tests).
- DTO ?? ???????? domain types, Production/Stock/Cutting ?????; ????????? unmodifiable; draft ?????????? ?? ??????? API.
- **Verification:** `mvn -q -pl tmp-order-management -am test` ? PASSED.

### STAGE5-010 ? Paginated search contracts

- `OrderSearchCriteria` (orderNumber/status/customerRef/customerName/createdFrom/createdTo); blank strings ? absent; inverted created range rejected.
- `PageRequest`: zero-based; default size 50 (`firstPage()`); max 100; rejects negative index / size < 1 / size > 100; carries `OrderSort`.
- `OrderSort`: default `createdAt DESC, orderId DESC`; whitelist `createdAt|orderId|orderNumber|status`; ASC/DESC; unknown field/direction rejected.
- `OrderQueryService.searchOrders(criteria, pageRequest)` ? `PageResult<OrderSummaryDto>`.
- **Verification:** `mvn -q -pl tmp-order-management -am test` ? PASSED; architecture gate ? PASSED.

### Execution module 5.3 gate

- `STAGE5-009..010` = DONE; `STAGE5-011` ???????? `PLANNED` (?? READY, ?? ??????).
- Public API read-only; domain entities ?? ????????????; Draft Revision ?? ????????????; ????? ??????-?????? ???????????; ?????????/?????????? ????????????; persistence/UI ???????????.
- Git-???????? ?? ???????????.


## STAGE5-011 ? Typed payload models: Order documents

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Immutable typed Order document payloads with ?11.2 identity; sealed OrderDocumentPayload; document type binding enforced; no generic JSON.

### Files created

- 	mp-order-management/.../application/payload/{DocumentId,DocumentTypeCode,PayloadIdentity,OrderDocumentPayload,OrderCreatePayload,OrderUpdatePayload,OrderApprovePayload,OrderCancelPayload}.java
- 	mp-order-management/.../application/payload/OrderPayloadModelsTest.java

### Tests added or changed

- OrderPayloadModelsTest ? identity fields, type binding, immutability, no generic JSON, revision increment on commercial update.

### Verification

- mvn -q -pl tmp-order-management -am test > PASSED

## STAGE5-012 ? Typed payload models: Item documents

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Immutable typed Item payloads: create (order/item/commercial/qty), update (commercial only ? no Revision/Specification), cancel (item id only).

### Files created

- `OrderItemCreatePayload`, `OrderItemUpdatePayload`, `OrderItemCancelPayload`
- `OrderItemPayloadModelsTest`

### Verification

- `mvn -q -pl tmp-order-management -am test` > PASSED

## STAGE5-013 ? Typed payload models: Revision documents

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Revision payloads + `OrderItemRevisionPayloadLine`; update targets only Draft (rejects APPROVED); immutable line collection; quantity/norm validation.

### Verification

- `mvn -q -pl tmp-order-management -am test` > PASSED

## STAGE5-014 ? Payload application use cases (draft edit + optimistic lock)

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Internal `DraftPayloadApplicationService`: create/load/update/delete Draft payload; Draft check via public `DocumentEngine.findById` + `DocumentStatus.DRAFT`; optimistic lock via `PayloadRevision` / `PayloadOptimisticLockException`. Introduced single `OrderDocumentPayloadPort` (no competing port). Test double: `InMemoryOrderDocumentPayloadPort` + `FakeDocumentEngine`.

### Verification

- `mvn -q -pl tmp-order-management -am test` > PASSED

## STAGE5-015 ? Payload persistence port

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Frozen single `OrderDocumentPayloadPort` (no second port): load/create/update(expected revision)/deleteDraft/exists; typed `OrderDocumentPayload`; explicit absence and `PayloadOptimisticLockException`. Infra-free interface; JDBC adapter not implemented.

### Verification

- `mvn -q -pl tmp-order-management -am test` > PASSED

## STAGE5-016 ? Payload physical schema (Flyway typed tables)

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Flyway `V6__order_payload_schema.sql` in `tmp-order-management`: schema `order_management` with `order_document_payload` + typed payload tables + `order_item_revision_payload_line`; FK `document_id` ON DELETE CASCADE; `payload_revision` optimistic lock; no JSON/BLOB; no aggregate/processing tables; no JDBC adapter.

### Files created

- `tmp-order-management/src/main/resources/db/migration/V6__order_payload_schema.sql`
- `OrderPayloadSchemaFlywayTest`
- test-scoped Flyway/PostgreSQL/Testcontainers deps in `tmp-order-management/pom.xml`

### Verification

- `mvn -q -pl tmp-order-management -am test` > PASSED
- `mvn -q -pl tmp-infra-db -am test` > PASSED
- `mvn -q -pl tmp-architecture-tests -am test` > PASSED

### Execution module 5.4 gate

- `STAGE5-011..016` = DONE; `STAGE5-017` remains `PLANNED` (not READY, not started).
- Git operations not executed.

## STAGE5-017 ? Public TransactionalEventPublisher contract and adapter

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Public `com.tmp.document.api.TransactionalEventPublisher`; internal `TransactionAfterCommitEventPublisher` implements it; Spring bean exposed as public interface. Events after commit only; rollback does not deliver. Document Engine business logic unchanged.

### Verification

- `mvn -q -pl tmp-document-engine -am test` > PASSED

## STAGE5-018 ? Processing record and idempotency model

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

`ProcessingRecord`, `ProcessingOperation.POST`, `ProcessingStatus`, `ProcessingRecordPort`, `IdempotencyGuard` with void `runPostOnce`; duplicate path skips event; ResultReference internal.

### Verification

- `mvn -q -pl tmp-order-management -am test` > PASSED

## STAGE5-019 ? Processing record schema

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

`V7__order_processing_record.sql`: `order_management.order_document_processing` with PK `(document_id, operation)`, operation POST, nullable result_reference, no JSON.

### Verification

- `mvn -q -pl tmp-order-management -am test` > PASSED

## STAGE5-020 ? Payload and processing-record persistence adapters

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

`JdbcOrderDocumentPayloadAdapter` (all 10 typed payloads, optimistic lock, cascade delete, ordered revision lines) and `JdbcProcessingRecordAdapter` (unique document_id+POST, duplicate > DuplicateProcessingRecordException). PostgreSQL IT coverage.

## STAGE5-021 ? Business document type registration model

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

Immutable `OrderBusinessDocumentCatalog` with exactly 10 descriptors (type, display, description, payload class, schema v1, capability). Processors not registered.

## STAGE5-022 ? Document lifecycle policy base

**Date:** 2026-07-25  
**Stage:** 5  
**Status:** DONE

### Result

`AbstractOrderDocumentProcessor`: void onPost with idempotency + public TransactionalEventPublisher; onUnpost rejects; onClose no-op; onDelete Draft payload only. No concrete ORDER_* processors.

### Execution module 5.5 gate

- `STAGE5-017..022` = DONE; `STAGE5-023` remains `PLANNED`.
- Git operations not executed.

## STAGE5-023 ? Document processor: ORDER_CREATE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`CreateOrderCommand` / `CreateOrderUseCase` create Draft `CustomerOrder` with unique `OrderNumber`. `OrderCreateDocumentProcessor` extends `AbstractOrderDocumentProcessor`; publishes `OrderCreated` via public publisher; `ResultReference` stores created `OrderId`; registration via `DocumentEngine.registerProcessor`.

## STAGE5-024 ? Document processor: ORDER_UPDATE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`UpdateOrderCommand` / `UpdateOrderUseCase` update commercial fields of Draft orders only; missing order ? `OrderNotFoundException`. `OrderUpdateDocumentProcessor` publishes `OrderUpdated`; optimistic lock via repository save.

## STAGE5-025 ? Document processor: ORDER_APPROVE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`ApproveOrderCommand` / `ApproveOrderUseCase` require at least one ACTIVE item via `OrderItemRepository.findByOrderId` (no Production Status). `OrderApproveDocumentProcessor` publishes `OrderApproved`. Domain re-approval and missing active items reject without processing record/event.

## STAGE5-026 ? Document processor: ORDER_CANCEL

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`CancelOrderCommand` / `CancelOrderUseCase` cancel Draft only (`APPROVED -> CANCELLED` forbidden). `OrderCancelDocumentProcessor` publishes `OrderCancelled`. No compensating documents.

### Execution module 5.6 gate

- `STAGE5-023..026` = DONE; `STAGE5-027` remains `PLANNED`.
- Exactly four concrete `ORDER_*` processors; all extend `AbstractOrderDocumentProcessor`.
- No direct `EventBus`; no SQL/JDBC aggregate adapters; no item processors.
- Git operations not executed.

## STAGE5-027 ? Document processor: ORDER_ITEM_CREATE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

Extended `IdempotencyGuard` / `AbstractOrderDocumentProcessor` with immutable multi-event publish (backward compatible). `CreateOrderItemCommand` / `CreateOrderItemUseCase` / `OrderItemCreateDocumentProcessor` create Draft item + Revision 1; publish `OrderItemCreated` + `OrderItemRevisionCreated`.

## STAGE5-028 ? Document processor: ORDER_ITEM_UPDATE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`UpdateOrderItemCommand` / `UpdateOrderItemUseCase` / `OrderItemUpdateDocumentProcessor` update commercial fields of Draft items only; publish `OrderItemUpdated`.

## STAGE5-029 ? Document processor: ORDER_ITEM_REVISION_CREATE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`CreateOrderItemRevisionCommand` / `CreateOrderItemRevisionUseCase` / `OrderItemRevisionCreateDocumentProcessor` create Draft N+1 for ACTIVE items; optional copy-from APPROVED; publish `OrderItemRevisionCreated`.

## STAGE5-030 ? Document processor: ORDER_ITEM_REVISION_UPDATE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`UpdateOrderItemRevisionCommand` / `UpdateOrderItemRevisionUseCase` / `OrderItemRevisionUpdateDocumentProcessor` update Draft quantity+specification from typed lines; publish `OrderItemRevisionUpdated`.

## STAGE5-031 ? Document processor: ORDER_ITEM_REVISION_APPROVE

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`ApproveOrderItemRevisionCommand` / `ApproveOrderItemRevisionUseCase` / `OrderItemRevisionApproveDocumentProcessor` approve Draft revision atomically; publish `OrderItemRevisionApproved`.

## STAGE5-032 ? Document processor: ORDER_ITEM_CANCEL

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`CancelOrderItemCommand` / `CancelOrderItemUseCase` / `OrderItemCancelDocumentProcessor` cancel Draft items only; publish `OrderItemCancelled`.

### Execution module 5.7 gate

- `STAGE5-027..032` = DONE; `STAGE5-033` remains `PLANNED`.
- Six new item/revision processors; multi-event base support preserved existing order processors.
- No direct EventBus; no aggregate JDBC/SQL; Git not executed.

## STAGE5-033 ? Aggregate persistence adapters

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

JDBC adapters for `CustomerOrderRepository`, `OrderItemRepository`, `OrderItemRevisionRepository` (read), `ItemSpecificationRepository` (read). Order Item `save` atomically persists item + revisions + specifications + lines + active/draft pointers with optimistic locking (`version = version + 1` / `WHERE ... AND version = ?`). No own transaction; no JPA. `OrderItemRevision.rehydrate` / `ItemSpecification.rehydrate` made public for persistence adapters only.

## STAGE5-034 ? Aggregate Flyway migration

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

Created `V8__order_management_aggregates.sql`: `orders`, `order_items`, `order_item_revisions`, `item_specifications`, `item_specification_lines` in schema `order_management`. FK/unique/check constraints; no Production/Warehouse/Cutting columns; no JSON/BLOB. PostgreSQL IT covers migrate, aggregate round-trip, optimistic lock, unique order number, FK integrity.

## STAGE5-035 ? Security capabilities registration

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`OrderManagementPermissions` (13 `PermissionId`), `OrderManagementPermissionCatalog` (immutable `PermissionDescriptor` set), `OrderManagementCapability` (Capability SPI, permissions only, no navigation). Document mappings validated against `OrderBusinessDocumentCatalog`; view capabilities for Query API declared. No user/role assignment.

### Execution module 5.8 gate

- `STAGE5-033..035` = DONE; `STAGE5-036` remains `PLANNED`.
- No Public Query API service; no UI; Git not executed.

## STAGE5-035A ? Query API implementation

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`DefaultOrderQueryService` + `OrderQueryReadPort` + `JdbcOrderQueryReadAdapter`. All eight Public Query API methods; parameterized search filters; whitelist sort; Draft Revision/Specification hidden; APPROVED-only revision exposure; active revision via `active_revision_number`; DTO projections only; read-only SQL.

## STAGE5-035B ? Query API runtime wiring and security

**Date:** 2026-07-26  
**Stage:** 5  
**Status:** DONE

### Result

`OrderManagementAutoConfiguration` registers read port, `OrderQueryService`, `OrderManagementCapability`. Permission checks via public `AuthorizationService`: `order.order.view`, `order.item.view`, `order.specification.view`. Bootstrap depends on `tmp-order-management`. No user/role auto-assignment; no UI; `STAGE5-036` remains PLANNED.

### Query API prerequisite gate

- `STAGE5-035A..035B` = DONE; `STAGE5-036` = PLANNED.
- Git not executed.

### Follow-up before STAGE5-039 (item list sorting)

`getOrderItems` currently reuses order-listing `OrderSort` (fields `createdAt` / `orderId` / `orderNumber` / `status`) via `PageRequest`. Before STAGE5-039, replace that order-specific sort for item listings with a dedicated item-sort contract or a fixed item order. Do not change item-list sorting as part of Stage 5.9B UI (`STAGE5-036`/`STAGE5-037`).

### Stage 5.9B preflight

- Removed tracked generated file `target/checkstyle-cachefile` from the working tree (`.gitignore` unchanged; `target/` already ignored).
- Added positive runtime permission IT: grant `order.order.view` via public `RoleAdministrationService`, then `OrderQueryService.searchOrders` succeeds without `AccessDeniedException` (no production auto-assignment).

## STAGE5-036 ? Order Management navigation

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result

`OrderManagementCapability` contributes `order.nav.orders` > `order.view.orders` (display `??????`), gated by `order.order.view` via matching `CommandDescriptor`. UI Shell registers screen id `order.view.orders` with placeholder FXML/Controller/ViewModel; bootstrap wires registration. Navigation hidden without permission; opens list screen id. No duplicate navigation ids. UI imports only public Security/UI contracts (screen constants; no order internals).

### Notes

- Item-list `OrderSort` follow-up remains recorded for STAGE5-039 (unchanged in this task).

## STAGE5-037 ? Order list screen

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result

Read-only order list in `tmp-ui-shell`: FXML/Controller/ViewModel call only `OrderQueryService.searchOrders`. Filters (orderNumber, orderStatus, customerRef, customerName, createdFrom, createdTo) with apply/clear; invalid date range not sent. Pagination zero-based, default size 50, max 100, prev/next with boundary lock, reset to page 0 on filter. Default sort `createdAt DESC, orderId DESC` via `OrderSort`/`PageRequest` (no SQL in UI). States: loading, empty, success, access denied, data error. Bootstrap wires `OrderListViewModel(OrderQueryService)`. No mutating actions; no editor/items screens.

### Follow-up (unchanged)

Before STAGE5-039, replace order-specific `OrderSort` reuse for item listings with a dedicated contract or fixed order.

## STAGE5-038 ? UI: Order editor (document-driven)

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result

Stage 5.9C order editor completed end-to-end.

**9B list preflight:** `OrderListViewModel` no longer queries in the constructor; initial `refresh()` once on screen open; `canGoPrevious` / `canGoNext` disable Prev on first page, Next on last page, both while loading.

**UI application service:** `com.tmp.order.api.ui.OrderDocumentUiService` (+ `OrderHeaderDraft`) with `DefaultOrderDocumentUiService` ? begin CREATE/UPDATE/APPROVE/CANCEL, save typed drafts via existing payload use cases, post only through `DocumentEngine.postDocument`. CREATE `OrderId` resolved from processing record (`OrderCreateDocumentProcessor.orderIdFrom`). No direct aggregate mutation, no repository/JDBC from the UI fa?ade API surface.

**Spring wiring:** `OrderManagementAutoConfiguration` registers order-level processors and UI service; `@AutoConfigureAfter` uses string names only (no internal Document Engine class import). Item processors not registered.

**UI shell:** `OrderEditorScreen.fxml` / `OrderEditorController` / `OrderEditorViewModel` with CREATE and VIEW_EXISTING; DRAFT edit/approve/cancel; APPROVED/CANCELLED read-only; local error display without success flash; list ???????? ?????? / open selected via existing navigation (`OrderScreenNavigationBridge`). Permissions: `order.order.view|create|edit|approve|cancel` via public Security API.

**Tests:** list single initial request + pagination bounds; editor FXML/open modes/read-only/permissions/document flow/error retention; Spring wiring for `OrderDocumentUiService` + `OrderEditorViewModel`.

### Notes

- Approve still requires ?1 ACTIVE item (existing domain rule); item UI is STAGE5-039.
- STAGE5-039 left PLANNED (not READY).
- No Git operations executed.

## STAGE5-039 ? UI: Item and Revision editor

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result

Stage 5.9D item and revision editor completed end-to-end.

**Preflight:** `OrderDocumentUiService` Javadoc corrected (`ORDER_CREATE` OrderId from processing record; no `searchOrders`). Success messages after post/approve/cancel preserved across reload. Item list order fixed to `created_at ASC, order_item_id ASC`; removed erroneous `OrderSort.ORDER_NUMBER ? product_code` mapping.

**UI contracts:** `OrderItemDocumentUiService` (+ `OrderItemCommercialDraft`) for six document flows; `OrderItemEditorQueryService` / `OrderItemEditorSnapshot` expose optional Draft Revision for desktop UI only. Public Query API remains APPROVED-only.

**Spring wiring:** `OrderManagementAutoConfiguration` registers six item/revision use cases and processors via Document Engine public API (10 processors total with existing four order-level). `OrderItemDocumentUiService` and `OrderItemEditorQueryService` beans wired.

**UI shell:** Order editor ? item list (`getOrderItems` only); item create/open; item/revision editor with separate active/draft display; commercial UPDATE/CANCEL for Draft items; revision CREATE N+1 / UPDATE quantity (lines preserved) / APPROVE. Specification line editing not implemented (STAGE5-040). Permissions via public Security API (`order.item.*`, `order.revision.*`).

**Tests:** processor registration (10); FXML list/editor; document flows; draft/active separation; second-draft blocked; permissions; no repository/JDBC in UI ViewModels; success message after reload; Public Query API draft absence contract retained.

### Notes

- STAGE5-040 left PLANNED (not READY).
- No Git operations executed.

## STAGE5-040 ? UI: Specification editor (immutable after approve)

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result

Stage 5.9E Specification editor completed end-to-end.

**UI-facing read model:** `OrderItemSpecificationEditorQueryService` / `OrderItemSpecificationEditorSnapshot` / `OrderItemSpecificationLineView` load Draft or Approved Specification for desktop UI only (`order.specification.view`). Snapshot and line list are immutable. Public Query API unchanged (APPROVED-only).

**Document save:** `OrderItemSpecificationLineDraft` + full `saveRevisionUpdateDraft(..., specificationLines, ...)`. Quantity-only overload delegates and preserves lines. Typed `OrderItemRevisionUpdatePayload` with sequential line numbers `1..N`; Approved Revision rejected by application service; optimistic locking via `PayloadRevision`; post only through Document Engine.

**UI shell:** `OrderItemSpecificationEditorScreen` (FXML/Controller/ViewModel); Draft editable (add/update/delete/reorder/clear/save/post); Approved read-only. Item Editor actions ???????? ????????/???????? ?????????????; return reloads item snapshot. Permissions: `order.specification.view`, `order.revision.edit`.

**Bootstrap:** Specification editor ViewModel + screen registration + navigation bridge wired.

### Files created

- `tmp-order-management/.../api/ui/OrderItemSpecificationEditorQueryService.java`
- `tmp-order-management/.../api/ui/OrderItemSpecificationEditorSnapshot.java`
- `tmp-order-management/.../api/ui/OrderItemSpecificationLineView.java`
- `tmp-order-management/.../api/ui/OrderItemSpecificationLineDraft.java`
- `tmp-order-management/.../application/ui/DefaultOrderItemSpecificationEditorQueryService.java`
- `tmp-ui-shell/.../orderspecificationeditor/OrderItemSpecificationEditorViewModel.java`
- `tmp-ui-shell/.../orderspecificationeditor/OrderItemSpecificationEditorController.java`
- `tmp-ui-shell/.../orderspecificationeditor/SpecificationLineRow.java`
- `tmp-ui-shell/.../orderspecificationeditor/OrderItemSpecificationEditorScreen.fxml`
- related tests

### Files modified

- `OrderItemDocumentUiService` / `DefaultOrderItemDocumentUiService`
- `OrderManagementAutoConfiguration`
- `OrderItemEditorViewModel` / Controller / FXML
- `UiShellScreens`, `UiShellAutoConfiguration`
- control docs

### Tests added or changed

- `DefaultOrderItemSpecificationEditorQueryServiceTest`
- `DefaultOrderItemDocumentUiServiceTest` (full-line save / approved reject / stale lock / empty lines)
- `OrderItemSpecificationEditorViewModelTest` / `ControllerFxTest`
- Item Editor open-spec + FakeDocs overload; bootstrap/autoconfig bean assertions

### Verification

| Check | Result |
|---|---|
| Order Management verify | PASSED |
| UI Shell verify | PASSED |
| Bootstrap test | PASSED |
| Document Engine test | PASSED |
| Architecture tests | PASSED |

### Notes

- STAGE5-041 left PLANNED (not READY).
- No Git operations executed.

## STAGE5-041 — UI: Error handling and user messages

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result

Stage 5.9F unified UI error handling completed. Stage 5.9 (`STAGE5-036..041`) completed.

**Preflight:** Specification Editor `dirty` state; post allowed only when `editable && documentId != null && !dirty`; unsaved changes block post without calling Document Engine and without clearing user lines.

**Error mapping:** `OrderUiErrorMapper` / `OrderUiOperation` / `OrderUiErrorCategory` / `OrderUiUserMessage` in `tmp-ui-shell` (Spring-free). Classifies AccessDenied, optimistic lock, validation, not found, forbidden transition, already posted, unpost rejected, technical failure via cause chain without importing Order application/persistence or Document Engine internals. Safe Russian messages; no raw exception text leakage.

**Screens:** Order List, Order Editor, Item List, Item Editor, Specification Editor use mapper; success/error exclusivity; failed post preserves fields and documentId/payloadRevision; Spec reload failure after successful post keeps success + warning.

### Files created

- `tmp-ui-shell/.../order/error/OrderUiErrorMapper.java` (+ Operation/Category/UserMessage)
- mapper and ViewModel tests

### Files modified

- Order Management ViewModels (list/editor/item/spec)
- Spec Editor FXML (warning label)
- Stage5 architecture test (UI shell internals ban)
- control docs

### Verification

| Check | Result |
|---|---|
| UI Shell verify | PASSED |
| Order Management test | PASSED |
| Bootstrap test | PASSED |
| Architecture tests | PASSED |

### Notes

- STAGE5-042 left PLANNED (not READY).
- No Git operations executed.

## STAGE5-042 — Unit tests consolidation

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result
Unit tests consolidation for Order Management domain + application base processor.
Added missing unit scenarios for `CustomerOrder`, `OrderItem`/draft revision/spec editing and
`AbstractOrderDocumentProcessor` payload schema-version mismatch rejection.

### Tests added or changed
- `CustomerOrderTest`
- `OrderItemTest`
- `OrderItemRevisionTest`
- `ItemSpecificationTest`
- `AbstractOrderDocumentProcessorTest`

### Verification
| Check | Result |
|---|---|
| tmp-ui-shell unit tests | PASSED |
| tmp-order-management unit tests | PASSED |

### Documentation updated
- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`

### Next task
`STAGE5-043`

---

## STAGE5-043 — Persistence integration tests (PostgreSQL Testcontainers)

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result
Order Management persistence verified on real PostgreSQL (Testcontainers):
typed payload round-trips, optimistic locking/stale update rejection, processing-record
round-trip including nullable internal `result_reference`, and minimal `information_schema`
checks (constraints and forbidden column/type patterns).

### Tests added or changed
- `JdbcPayloadAndProcessingAdaptersIT`
  - stronger `typed payload` round-trip assertions: exact loaded Java class + `createdAt/updatedAt`
  - optimistic locking/stale rejection for all 10 typed payload types
  - `ProcessingRecord.resultReference` nullable round-trip test
- `JdbcAggregatePersistenceIT`
  - `information_schema` checks: UNIQUE order_number, FOREIGN KEY existence, absence of JSON/BYTEA, and no Postgres enum types for `*status*` columns
  - extended timestamp assertions for `CustomerOrder` insert/load

### Verification
| Check | Result |
|---|---|
| tmp-order-management verify | PASSED |
| tmp-infra-db test | PASSED |

### Documentation updated
- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task
`STAGE5-044`

---

## STAGE5-044 — Document lifecycle integration tests

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result
Document lifecycle verified through public `DocumentEngine` + concrete Order Management processors on PostgreSQL:
successful sequential post scenario, separate cancel/revision-create flows, unpost rejection, close without business mutation, draft delete cascade, and after-commit event timing.

### Tests added or changed
- `OrderDocumentLifecycleIT`
  - sequential posts: ORDER_CREATE → UPDATE → ITEM_CREATE → ITEM_UPDATE → REVISION_UPDATE → REVISION_APPROVE → ORDER_APPROVE
  - separate: ORDER_CANCEL, ORDER_ITEM_CANCEL, ORDER_ITEM_REVISION_CREATE
  - unpost posted document rejected; aggregate/processing/payload unchanged; no extra business event
  - close keeps aggregate/payload/processing; no new business event
  - delete draft removes metadata + typed payload; aggregate untouched; posted delete rejected
  - OrderCreated absent before commit, present after commit

### Verification
| Check | Result |
|---|---|
| tmp-order-management verify | PASSED |
| tmp-document-engine test | PASSED |

### Documentation updated
- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task
`STAGE5-045`

---

## STAGE5-045 — Idempotency tests

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result
Idempotency verified on PostgreSQL through public Document Engine and internal processor guard:
repeated public post rejected by lifecycle; internal `onPost` with existing processing record is a no-op; concurrent processing-record insert and concurrent public post keep a single business effect.

### Tests added or changed
- `OrderDocumentIdempotencyIT`
  - public repeated `postDocument` rejected; aggregate version / events / processing row unchanged
  - internal processor guard: repeated `onPost` does not mutate aggregate, events, or `result_reference`; `onPost` remains `void`
  - concurrent `ProcessingRecord` insert → one row
  - concurrent public post → one success, one order, one processing record

### Verification
| Check | Result |
|---|---|
| tmp-order-management verify | PASSED |

### Documentation updated
- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task
`STAGE5-046`

---

## STAGE5-046 — Transaction rollback tests

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result
Transaction rollback verified through public Document Engine API with a test-only fault-injecting processor registered via `DocumentEngine.registerProcessor()`. After controlled failure: no aggregate change, no processing record, document stays DRAFT, no lifecycle POSTED journal entry, no domain event; retry succeeds.

### Tests added or changed
- `OrderDocumentRollbackIT`
  - `FaultInjectingProcessor` performs real aggregate mutation, processing-record insert, and `TransactionalEventPublisher` registration before throwing
  - post-rollback assertions on aggregate, processing record, document metadata, lifecycle journal, typed payload, and events
  - successful retry after rollback

### Verification
| Check | Result |
|---|---|
| tmp-order-management verify | PASSED |
| tmp-document-engine test | PASSED |

### Documentation updated
- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task
`STAGE5-047`

---

## STAGE5-047 — Architecture tests (boundaries and ownership)

**Date:** 2026-07-27  
**Stage:** 5  
**Status:** DONE

### Result
Finalized Stage 5 ArchUnit rules for Order Management module boundaries, Document Engine public API usage, UI/persistence isolation, payload ownership, foreign business-state prohibition, and negative-rule verification with test-only violators.

### Tests added or changed
- `Stage5OrderManagementArchitectureTest` — extended rules for api.ui isolation, EventBus, void `onPost`, Jackson/JPA bans, JDBC placement, payload ownership, read-only Query API, draft revision exposure, foreign business state
- `Stage5OrderManagementArchitectureNegativeTest` + `Stage5ArchitectureViolators` — negative-rule detection

### Verification
| Check | Result |
|---|---|
| tmp-architecture-tests test | PASSED |
| tmp-order-management verify | PASSED |
| tmp-ui-shell verify | PASSED |

### Documentation updated
- `docs/development-control/STATUS.md`
- `docs/development-control/WORK-QUEUE.md`
- `docs/development-control/VERIFICATION-LOG.md`

### Next task
`STAGE5-048` (PLANNED — waiting for user authorization)

---

## Stage 5.11A — Architecture verification corrections

**Date:** 2026-07-28  
**Stage:** 5  
**Status:** COMPLETED

Stage 5.11A completed.

Added:
- negative verification for Order Management domain dependency on Spring;
- prohibition of Object and Map in typed payload contracts;
- negative generic-payload violator test;
- updated Stage 5.10 latest verification result.

STAGE5-047 remains DONE.
STAGE5-048 remains PLANNED.

### Tests added or changed
- Strengthened `orderDomainIsFrameworkFree` (`javax.persistence`, `java.sql`, `org.springframework.jdbc`)
- `orderTypedPayloadDoesNotUseObjectOrMap` ArchUnit rule (fields, constructors, public methods)
- Test-only `DomainDependsOnSpringViolator` (`com.tmp.order.domain`)
- Test-only `GenericMapPayloadViolator` (`com.tmp.order.application.payload`)
- Negative + positive checks in `Stage5OrderManagementArchitectureNegativeTest`

### Verification
| Check | Result |
|---|---|
| tmp-architecture-tests -am test | PASSED |
| tmp-order-management -am test | PASSED |

---

## STAGE5-048 — Full reactor verification

**Date:** 2026-07-28  
**Stage:** 5  
**Status:** DONE

### Command
`mvn -q clean verify` (project root; no packaging profile)

### Environment
- OS: Windows 10 (10.0.19045)
- Java: OpenJDK 21.0.8 (JetBrains JBR)
- Maven: 3.9.9
- Docker: Docker Desktop 4.82.0 / Engine 29.6.1 — available

### Defect found and fixed
- `tmp-bootstrap-app` Failsafe: `PlatformCoreIntegrationIT.registersServicesCapabilitiesAndDeliversEvents`
- Cause: expected capability count remained 4 after Order Management capability registration (actual 5)
- Fix: update expected count and assertion message in `PlatformCoreIntegrationIT.java` (test-only)
- Local gate: `mvn -q -pl tmp-bootstrap-app -am verify` PASSED
- Final full reactor re-run: PASSED

### Packaging
Not executed (`STAGE5-049` not started).

### Next task
`STAGE5-049` (PLANNED — waiting for user authorization)

---

## STAGE5-049 — Packaged application verification

**Date:** 2026-07-28  
**Stage:** 5  
**Status:** DONE

### Command
`mvn -q -Ppackage clean verify` (project root)

### Environment
- OS: Windows 10 (10.0.19045)
- Java: Eclipse Temurin OpenJDK 21.0.11 (full JDK with jpackage)
- Maven: 3.9.9
- Docker: Docker Desktop 4.82.0 / Engine 29.6.1 — available
- jpackage: `${JAVA_HOME}\bin\jpackage.exe` present (`jpackage --version` = 21.0.11)

### Infrastructure note
JetBrains JBR 21 used for earlier Stage 5 work does not ship `jpackage.exe`. Temurin JDK 21.0.11 was installed so `${java.home}/bin/jpackage` required by the `package` profile is available. No packaging configuration changes.

### Result
- Packaging build: BUILD SUCCESS (EXIT_CODE=0)
- PackagingSmokeIT: executed=1, failures=0, errors=0, skipped=0
- Artifact: `dist\jpackage\TMP\TMP.exe`
- Bundled runtime: present (`runtime\release`, `runtime\lib\jvm.cfg`)
- `TMP.cfg`: contains `-Dspring.profiles.active=package` and `org.springframework.boot.loader.launch.JarLauncher`
- Fat jar: `dist\jpackage\TMP\app\tmp-bootstrap-app-0.1.0-SNAPSHOT.jar`
- `application-package.yml`: present at `BOOT-INF/classes/application-package.yml`
- Env-based package config verified in source (`TMP_DB_*`, `TMP_SECURITY_BOOTSTRAP_ADMIN_*`)
- GUI / `TMP.exe` launch: NOT EXECUTED

### Packaging defects
- None in project packaging configuration

### Next task
`STAGE5-050` (PLANNED — waiting for user authorization)

---

## STAGE5-050 — Manual packaged GUI smoke (preparation)

**Date:** 2026-07-28  
**Stage:** 5  
**Status:** IN_PROGRESS — WAITING_USER_RETEST-2
  - `dist\jpackage\TMP\TMP.exe`
  - `dist\jpackage\TMP\runtime\`
  - `dist\jpackage\TMP\app\TMP.cfg`
- Prepared separate Testcontainers-style PostgreSQL smoke DB instructions (container `tmp-stage5-pg`, DB `tmp_gui_stage5`, port `54325`)
- Prepared PowerShell env template for package profile (`TMP_DB_*`, `TMP_SECURITY_BOOTSTRAP_ADMIN_*`)
- Prepared manual checklist sections A–H for user confirmation

### Not done by agent
- `TMP.exe` not launched
- GUI checklist items not marked PASS/FAIL by agent
- Stage 5 not closed
- Stage 6 not started
- Packaging not rebuilt

### Next
Await user PASS/FAIL after Stage 5.11D-FIX-2 retest.

---

## STAGE 5.11D-FIX-2 — Roles UI selection and permission localization

**Date:** 2026-07-30  
**Stage:** 5  
**Status:** DONE (code fixes); STAGE5-050 remains IN_PROGRESS — WAITING_USER_RETEST-2

### Defects fixed

| ID | Issue | Root cause | Fix |
|---|---|---|---|
| DEFECT-5 | Role selection cleared by permission checkbox | `togglePermission` called full `refresh()` → `roleList.setAll()` cleared TableView selection → listener called `select(null)` and wiped `selectedRoleId` before restore | In-place role update without `setAll`; `select(null)` ignored; `clearSelection()` only for explicit clear; restore TableView selection after toggle |
| UI-LOCALIZATION-1 | English permission display names on Roles screen | Capability catalogues used English metadata; existing DB retained English until sync | Russian `displayName` in Order/Security/Sample catalogues; existing `PermissionSynchronizationApplicationService` updates metadata on re-registration |

### Regression tests

- `RoleAdministrationSelectionFxTest` — checkbox ON/OFF keeps TableView selection + selectedRoleId + Update targets selected role
- `RoleAdministrationViewModelTest.togglePermissionKeepsSelectedRoleIdWithoutFullListReplacement`
- Order/Security/Sample Russian displayName tests
- `PermissionSynchronizationPostgresIntegrationIT.resyncUpdatesEnglishDisplayNameToRussianWithoutChangingAssignments`
- `PermissionSynchronizationApplicationServiceTest.synchronizeUpdatesExistingDisplayNameMetadata`

### Packaging

Rebuilt: `mvn -q -Ppackage clean verify` — BUILD SUCCESS; `dist\jpackage\TMP\TMP.exe` present.

### Next

User manual GUI retest for STAGE5-050 (WAITING_USER_RETEST-2).

---

## STAGE 5 — Order Intake MVP planning (documentation only)

**Date:** 2026-07-30  
**Stage:** 5  
**Status:** DONE (documentation + decomposition); implementation NOT STARTED

### Delivered

- Order-Management-Specification → v1.3 (§27 Order Intake MVP; updated Item/Specification attributes)
- ADR-029 Source-neutral Order Intake Boundary
- STAGE-5 Manifest updated (extension order STAGE5-051..057; open commercial DRAFT decision)
- WORK-QUEUE tasks STAGE5-051..057 (NOT STARTED)
- Control docs + blocker STAGE5-INTAKE-COMMERCIAL-DRAFT

### Explicitly not done

- No Java / FXML / CSS / migration / dependency changes
- No parser / import GUI / fixtures implementation
- No TMP.exe launch
- STAGE5-050 not closed; Stage 5 not closed; Stage 6 not started

### Next

1. User completes STAGE5-050 GUI retest-2  
2. Only after STAGE5-050 = DONE may STAGE5-051 start (user authorization still required for extension work)  
3. Commercial DRAFT rule accepted as ADR-030 (blocker RESOLVED)

---

## STAGE 5.11D-FIX-1 — Manual smoke blocker corrections

**Date:** 2026-07-28  
**Stage:** 5  
**Status:** DONE (code fixes); STAGE5-050 remains IN_PROGRESS — WAITING_USER_RETEST

### Defects fixed

| ID | Issue | Root cause | Fix |
|---|---|---|---|
| DEFECT-1 | Create Order button disabled despite `order.order.create` | `OrderListViewModel` is a Spring singleton; `refreshPermissions()` ran only in constructor before login | Call `refreshPermissions()` from `refresh()` on each screen load |
| DEFECT-2 | Role selection cleared on permission checkbox | Checkbox focus cleared `TableView` selection; listener called `select(null)` | Track `selectedRoleId` in ViewModel; restore table selection; non-focusable checkboxes |
| DEFECT-3 | Mojibake in Roles screen messages | Corrupted UTF-8 literals in `RoleAdministrationViewModel` | `RoleAdministrationMessages` with correct Russian UTF-8 strings + status label |
| DEFECT-4 | Duplicate role assignment shows SQL | No application-layer duplicate check before INSERT | `RoleAlreadyAssignedException` + pre-check in `RoleAssignmentApplicationService` |

### Regression tests added

- `OrderListCreatePermissionTest`, `OrderListControllerFxTest`, `OrderListPermissionBootstrapIT`
- `RoleAdministrationSelectionFxTest`, `RoleAdministrationViewModelTest` (UTF-8 message)
- `RoleAssignmentApplicationServiceTest.duplicateAssignmentIsRejectedWithoutSecondRow`
- `SecurityEndToEndPostgresIntegrationIT.duplicateRoleAssignmentDoesNotInsertSecondRowOrLeakSql`

### Packaging

Rebuilt: `mvn -q -Ppackage clean verify` — BUILD SUCCESS; `dist\jpackage\TMP\TMP.exe` present.

### Next

User manual GUI retest for STAGE5-050.

---

## STAGE5-051 — Order Item and Specification Contracts

**Date:** 2026-07-30  
**Stage:** 5  
**Status:** DONE

### Summary

Order Intake contract alignment in `tmp-order-management`: `externalPositionNumber` on Item; `OrderedQuantity` as sole `productQuantity` (whole number > 0); `SpecificationLine` with `color`, `lengthMm`, `lineQuantity` (removed `consumptionNorm`); incomplete commercial DRAFT per ADR-030; `ORDER_APPROVE` lists missing mandatory fields.

### Key deliverables

- Domain: `ItemCommercialData.externalPositionNumber`, `OrderCommercialData` nullable draft fields + `missingMandatoryFieldsForApproval()`, `CommercialPlaceholderValidator`.
- API/DTO/Query/UI payload contracts updated across layers.
- Flyway `V9__order_intake_contracts.sql` (nullable order commercial columns, spec line reshape, payload tables).
- Tests: `OrderIntakeContractsTest`, `OrderIntakeContractsFlywayTest`, updated processor/persistence suites.

### Verification

`mvn -q -pl tmp-order-management -am test` — PASS (255 tests, 0 skipped).  
`mvn -q -pl tmp-order-management -am verify` — PASS.

### Fix pass (2026-07-30)

**Status:** DONE

**Defects addressed:**

1. **Query API incomplete DRAFT (ADR-030):** `OrderDto` and `OrderSummaryDto` no longer require non-null `customerName` / `direction` / `currency`; incomplete DRAFT orders readable via `getOrder` and search. `ORDER_APPROVE` gate unchanged.
2. **V10 constraints:** `V10__order_intake_constraints.sql` — pre-flight fractional check; `NUMERIC(19,6)` alignment on payload quantity columns; whole-number CHECK on `ordered_quantity` in revisions and payload tables.

**Key files:** `OrderDto.java`, `OrderSummaryDto.java`, `V10__order_intake_constraints.sql`, `OrderQueryApiContractTest`, `JdbcOrderQueryReadAdapterIT`, `OrderIntakeConstraintsFlywayTest`.

**Removed:** `docs/Input/Test 25062026.stxt` (unused sample).

**Verification:** `mvn -q -pl tmp-order-management test` — PASS (264); `mvn -q -pl tmp-order-management verify` — PASS (264 + 57 IT); `Stage5OrderManagementArchitectureTest` — PASS (24).

### Next

`STAGE5-052` Manual Entry UI — NOT STARTED (await user authorization).

---

## STAGE5-052 — Manual Entry UI

**Date:** 2026-07-30  
**Stage:** 5  
**Status:** DONE

### Summary

Completed manual entry MVP fields in existing Order Item and Specification JavaFX editors, aligned to STAGE5-051 contracts: `externalPositionNumber`, `productQuantity` (OrderedQuantity), `materialCode`, `materialName`, `color`, `lengthMm`, `lineQuantity`, `unitOfMeasure`.

### Key deliverables

- Extended UI read contract: `OrderItemEditorSnapshot.externalPositionNumber` + mapping in `DefaultOrderItemEditorQueryService`.
- Order Item editor (`tmp-ui-shell`): new field "Внешний номер позиции", label "Количество изделий", local integer validation `> 0` with Russian field-specific messages, reload mapping of saved value.
- Specification editor (`tmp-ui-shell`): removed `consumptionNorm`/legacy quantity UI, introduced `color` (nullable), `lengthMm` (nullable + `>0` when present), `lineQuantity` (`>0`), table columns and edit form in Russian.
- Draft/Approved behavior preserved: Draft editable, Approved read-only via existing flags/permission pattern; no repository calls in controller/view model.
- Mapping preserves `lineQuantity` as-is; no multiplication by product quantity in save or display path.

### Tests updated

- `tmp-order-management`:
  - `OrderItemEditorSnapshotTest` (new): value/null behavior for `externalPositionNumber`.
  - `DefaultOrderItemDocumentUiServiceTest`: query mapping includes `externalPositionNumber`, nullable pass-through.
- `tmp-ui-shell`:
  - `OrderItemEditorViewModelTest`: external position mapping/save/null handling, quantity validation cases.
  - `OrderItemSpecificationEditorViewModelTest`: nullable color/length mapping, line/length validation, line quantity non-multiplication.
  - `OrderItemListViewModelTest`: adjusted `OrderItemDto.of(...)` invocation to STAGE5-051 signature.

### Boundaries

- No domain model changes.
- No persistence/Flyway changes.
- No import/STXT/Firebird implementation.
- No Stage 6 work.

---

## STAGE5-052 — Manual Entry UI (fix pass)

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE

### Summary

Closed review gaps for STAGE5-052 without starting STAGE5-053: local `orderedQuantity` validation in the specification editor, removal of legacy consumption-norm UI aliases, default unit «шт», and real Controller FX scenarios beyond FXML load.

### Changes

- Shared UI helper `ProductQuantityUiValidation` used by Order Item and Specification editors.
- `OrderItemSpecificationEditorViewModel.saveDraft()` validates product quantity before `beginRevisionUpdate` / `saveRevisionUpdateDraft`.
- Removed `editConsumptionNormProperty()` and `editQuantityProperty()`; public accessor is `editLineQuantityProperty()`.
- Default unit of measure for a new line form: `SpecificationLineRow.DEFAULT_UNIT_OF_MEASURE` = «шт» (reset after add/clear; existing row unit preserved).
- Expanded ViewModel and Controller FX tests for validation, DRAFT/APPROVED, null display, and document-service call/no-call paths.

### Boundaries

- No domain / persistence / Flyway changes.
- No import / STXT / Firebird.
- STAGE5-053 not started.
- Git not executed by agent.

---

## STAGE5-052A — Imported Draft Item Commercial Contract

**Date:** 2026-07-31  
**Stage:** 5  
**Status:** DONE

### Summary

Extended ADR-030 incomplete DRAFT to Order Item commercial fields (`productCode`, `name`) so Order Intake can create DRAFT positions without inventing placeholders. Completeness is enforced at `ORDER_ITEM_REVISION_APPROVE` with Russian missing-field labels.

### Key deliverables

- Domain: `ItemCommercialData` nullable `productCode`/`name`, blank→null, placeholder rejection, `missingMandatoryFieldsForApproval()`.
- `OrderItem.approveDraftRevision` rejects incomplete commercial data before DRAFT→ACTIVE.
- Contracts: `OrderItemCommercialDraft`, `OrderItemDto`, `OrderItemEditorSnapshot`, UI/document mapping null-safe.
- Persistence JDBC null mapping; Flyway `V11__order_item_incomplete_draft_contract.sql`.
- Minimal UI: empty fields for null productCode/name; fill via document flow.

### Fix pass (2026-07-31)

- Bug: `JdbcOrderQueryReadAdapter.findOrderItem` SELECT omitted `external_position_number` while shared `mapOrderItem` read it → SQLException on single-item read.
- Fix: added `external_position_number` to `findOrderItem` SELECT (same columns/mapping as `findOrderItems`); no separate mapper; no new Flyway migration.
- JDBC ITs: Query API incomplete DRAFT (`findOrderItem`/`findOrderItems`, null externalPositionNumber, complete regression); aggregate round-trip; create/update payload round-trip — all via real adapters, not in-memory DTO-only construction.
- Docs: ADR header Version 1.6; CANCELLED may retain incomplete DRAFT state (not editable); ACTIVE always complete; CONTEXT-MAP V11/V12.

### Boundaries

- No Import Core / STXT / import metadata / Firebird.
- STAGE5-053 not started.
- Stage 6 not started.
- Git not executed by agent.

---

## STAGE6-001 — Warehouse Module Foundation

**Date:** 2026-08-06  
**Stage:** 6  
**Status:** DONE

### Summary

Created the initial `tmp-warehouse` module foundation and connected it to the root Maven reactor without implementing Warehouse business behavior.

### Key deliverables

- Added `tmp-warehouse` to root `pom.xml` modules list and dependencyManagement.
- Created `tmp-warehouse/pom.xml` as a standalone Stage 6 module.
- Created package boundaries in `com.tmp.warehouse`:
  - `domain`
  - `application`
  - `api`
  - `infrastructure`
- Added `package-info.java` markers only; no domain entities, no API implementation, no persistence tables.

### Boundaries

- No Warehouse business logic implemented.
- No Warehouse domain entities created.
- No DB tables/migrations created.
- No API endpoints/handlers implemented.
- No Production/Cutting/Order Management internals changed.
- Git commands not executed by agent.

### Build verification unblock

- Resolved environment blocker by running verification with local Maven binary:
  `C:\Program Files\JetBrains\IntelliJIdea2025.2\plugins\maven\lib\maven3\bin\mvn.cmd`.

---

## STAGE6-002 — Warehouse Domain Model

**Date:** 2026-08-07  
**Stage:** 6  
**Status:** DONE

### Summary

Implemented Warehouse domain model in `com.tmp.warehouse.domain` only: warehouse/cell stock ownership, operation write-path for stock changes, and immutable movement history. No persistence, API, services or Material Master.

### Key deliverables

- Entities: `Warehouse`, `StorageCell`, `StockPosition`, `WarehouseOperation`, `WarehouseMovement`
- Supporting types: IDs, `MaterialReference`, `StockQuantity`, `StockState`, `WarehouseOperationType`, `InvalidWarehouseStateException`
- Domain unit tests (18) covering code/reference invariants, stock states, operation-only mutation path, movement immutability

### Invariants fixed in domain

- Warehouse/StorageCell code mandatory; warehouse may be inactive; cell belongs to warehouse
- Stock states only `AVAILABLE` / `IN_TRANSIT` / `BLOCKED` (no `RESERVED`)
- Non-negative quantity
- `StockPosition.applyChange` package-private; public write collaborator is `WarehouseOperation`
- `WarehouseMovement` immutable (no public mutators)
- `WarehouseOperation.describe` creates description without executing Receipt/Move/Transfer flows

### Boundaries

- Only domain layer
- No repositories/services/controllers/REST/migrations
- No Material Master / Batch / FIFO / FEFO / reservation stock state
- No Spring / DB / UI dependencies in domain
- Git commands not executed by agent.

---

## STAGE7-004B — SpecificationId Stability Fix

**Date:** 2026-08-19
**Status:** DONE

### Problem

V24 migration used `md5(...)::uuid` to populate `specification_id`, while Java uses `UUID.nameUUIDFromBytes()` (UUID v3). These are NOT equivalent: UUID v3 sets version nibble to `3` and variant bits to `10`, while raw `md5()::uuid` does not. This breaks the SpecificationId stability invariant — a migrated row would get a different SpecificationId upon next Java save.

### Solution

1. **V25 migration** (`V25__fix_specification_id_uuid_v3.sql`): recalculates all `specification_id` values using SQL that replicates UUID v3 bit manipulation (version=3 at byte 6, variant=IETF at byte 8), matching Java's `UUID.nameUUIDFromBytes()` exactly.
2. **`SpecificationIdMigrationConsistencyIT`**: two integration tests with Testcontainers PostgreSQL:
   - `migrationAndJavaProduceSameSpecificationId` — verifies the SQL UUID v3 formula matches Java for the same input.
   - `migrationThenJavaRederivationProducesSameId` — verifies stability: after migration, Java re-derivation produces the same UUID.
3. **Flyway version assertions updated** in `OrderImportMetadataFlywayTest` (24→25) and `OrderIntakeFlywayBootstrapIT` (14→25).

### Files changed

- `tmp-order-management/src/main/resources/db/migration/V25__fix_specification_id_uuid_v3.sql` (NEW)
- `tmp-order-management/src/test/java/com/tmp/order/persistence/SpecificationIdMigrationConsistencyIT.java` (NEW)
- `tmp-order-management/src/test/java/com/tmp/order/persistence/OrderImportMetadataFlywayTest.java` (version assertion fix)
- `tmp-order-management/src/test/java/com/tmp/order/persistence/OrderIntakeFlywayBootstrapIT.java` (version assertion fix)

### Invariant established

One canonical SpecificationId algorithm: Java `UUID.nameUUIDFromBytes()`. Migration V25 aligns DB data. No second independent implementation exists.

---

## STAGE7-005 — Production Foundation (Specification Reference Freeze)

**Date:** 2026-08-20
**Status:** DONE

### Architecture decision: reference-only, no content snapshot

Per Production Spec §127–134 and OM Spec v1.10: specifications addressed by `SpecificationId` are immutable in Order Management. Production **does not copy** specification content. `ProductionFoundation` stores only the stable `SpecificationId` reference + source order item + freeze timestamp. Material lines are resolved on demand via `getSpecificationById(frozenSpecificationId)`.

### Domain

- **`ProductionFoundation`**: immutable value object (`specificationId`, `sourceOrderId`, `sourceOrderItemId`, `frozenAt`); `freeze()` at Launch only; `restore()` for persistence rehydration.
- **`CuttingPlanLinks`**: empty extension point for STAGE7-008 (0..N cutting plan references per material).
- **`ProductionItemState`**: embeds `ProductionFoundation`; all transitions preserve foundation unchanged.

### Application

- **`OrderSpecificationQueryPort`** + **`DefaultOrderSpecificationQueryAdapter`**: wraps OM `OrderQueryService` public API.
- **`ProductionLaunchService`**: calls `resolveCurrentForLaunch()` exactly once; creates `ProductionFoundation`; Launch document is sole freeze point.
- **`ProductionFoundationQueryService`**: post-launch reads via `resolveById()` only; exposes `materialLines()` for future Material Check/Transfer/Release.
- **`LaunchProductionCommand`**: no longer accepts `specificationId` from caller — resolved from OM at Launch.

### Persistence

- No schema migration. `specification_id` + `created_at` map to foundation reference + `frozenAt`.
- Repository persists/restores only; no foundation computation.

### Tests

- Unit: `ProductionFoundationTest`, `ProductionFoundationQueryServiceTest`, `ProductionLaunchServiceTest`, updated `ProductionItemStateTest`.
- Contract: `ProductionFoundationFreezeContractTest` (new revision does not affect frozen foundation).
- Integration: `JdbcProductionItemStateRepositoryTest` (foundation round-trip).
- Architecture: 3 new ArchUnit rules in `Stage7ProductionArchitectureTest`.

### Verification

- `mvn verify` — BUILD SUCCESS (all modules).

