# Stage 7 Production Audit

**Audit type:** Deep read-only audit before Production UX redesign  
**Date:** 2026-09-29  
**Scope:** Actual Production implementation vs Accepted architecture  
**Constraint:** No production code, UI, schema, API, ADR, or business-logic changes. This file is the only deliverable.

---

## 1. Baseline

### HEAD

```text
31ed5da0aaba4d81998e836b9d44851222d37471
```

Matches expected baseline `31ed5da0aaba4d81998e836b9d44851222d37471`.

### Working tree

```text
On branch master
Your branch is up to date with 'origin/master'.
nothing to commit, working tree clean
```

### Recent commits (git log -10 --oneline)

```text
31ed5da Remove Windows console launcher and cleanup tracked logs
d01475b TMP reactor verification check
ac907f6 Stage 3.5 warehouse checkpoint
703e372 Complete RBAC administration UX
d3fdb9c Align RBAC UI gates with authorization model
d4ff077 3.5.15 Refine task editing and Warehouse adjustment history
38d21a1 3.5.15 Add task creation time to Warehouse task list
4534957 3.5.15 Simplify Warehouse Tasks and add task dialog
c43883a Warehouse receipt UX fixes
528a7fe Warehouse receipt UX fixes
```

### Targeted tests in this audit session

`mvn` is **not available** on PATH and no Maven wrapper (`mvnw`) exists in the repository. Java 21 is present (`JAVA_HOME`). Targeted `tmp-production` test suites were **NOT RUN** in this session. Evidence below is from static code/docs/test-source review.

### DB used during audit

None (no integration tests executed). No production/stage database touched. Business data delta: **0**.

---

## 2. Executive Summary

Stage 7 Production is **feature-complete at backend/document/domain level** and marked DONE in control docs. Runtime model is:

- **Order-centric user workflow** (accept / cancel / aggregated status)
- **Item-owned persisted state** (`SourceOrderId` + `SourceOrderItemId` + `SpecificationId`)
- **No Order Item Revision** in Production domain/API/persistence
- Documents: `production.launch`, `production.release`, `production.cancellation`
- Material path after Stage 3.5.9/3.5.10: **Material Requirement DRAFT → Submit → Warehouse Transfer Documents**
- Legacy Stage 7 **Material Transfer Template** persistence and receipt API remain

**Critical architectural finding for UX redesign input:** the live UI material-request path (Material Requirement) is **disconnected** from the live receipt path (logical transfers from the template model). Release does not require materials received.

**Revision:** absent from Production-facing user model (SpecificationId only). Revision appears only in OM test doubles / negative assertions / OM fixture permissions — not as a Production UX object.

---

## 3. Production Functional Map

| Stage | Status | Evidence |
|-------|--------|----------|
| Order Item as production object | **IMPLEMENTED** | `ProductionItemState`, table `production.production_item_states` |
| Production state storage | **IMPLEMENTED** | JDBC `JdbcProductionItemStateRepository` |
| Order Production View (computed) | **IMPLEMENTED** | `OrderProductionViewCalculator`, `ProductionOrderViewService` |
| Material availability check | **IMPLEMENTED** (informational) | `CheckMaterialAvailabilityService`; does **not** gate Launch/Release |
| Material requirement (DRAFT/edit/submit) | **IMPLEMENTED** + **UI** | `MaterialRequirementService`, `SubmitMaterialRequirementService`, Workbench panel |
| Warehouse interaction (demand → Transfer Documents) | **IMPLEMENTED** (backend) | `WarehouseDemandCommandApi.acceptProductionDemand` (+ later `prepareProductionDemandTransfers` for WAITING supply) |
| Warehouse physical send/receive of MR documents | **WAREHOUSE-OWNED / OUTSIDE Production UI** | Production does not call `sendTransferDocument` |
| Legacy material transfer template | **SCAFFOLDING / HISTORICAL** | Tables V27/V28; `ConfirmMaterialTransferService` **not** Spring-wired; not on `ProductionApplicationApi` |
| Launch (accept into production) | **IMPLEMENTED** + **UI** | Whole-order `ProductionLaunchService` |
| In Production | **IMPLEMENTED** | Item status `IN_PRODUCTION` / order view `IN_PRODUCTION` |
| Partial Release | **IMPLEMENTED** + **UI** | `ReleaseProductsService` + item «К выпуску» |
| Full Release / Manufactured | **IMPLEMENTED** | Item `RELEASED` → order view `MANUFACTURED` |
| Cancellation | **IMPLEMENTED** + **UI** | Whole-order `CancelOrderProductionService` |
| Production receipt confirmation | **BACKEND PRESENT** + **UI**, but **chain break vs MR** | `ConfirmMaterialReceiptService` expects `ProductionMaterialTransfer` (template path) |
| Cutting Plan association post-launch | **NOT IMPLEMENTED** (STAGE7-008A PLANNED) | Links empty at Launch; opaque types exist |
| Per-item / partial Launch | **NOT IMPLEMENTED** | Launch always full `orderedQuantity` for all ACTIVE items |
| “Launchable Quantity” concept | **NOT IMPLEMENTED** | No symbol/formula in `tmp-production` |
| MES / routes / work centers | **NOT IMPLEMENTED** (explicitly out of scope) | Stage 7 Manifest |

### End-to-end chain (factual)

```text
ACTIVE Order (OM)
  → acceptOrderIntoProduction / production.launch
  → production_item_states (IN_PRODUCTION, qty = ordered)
  → checkMaterialAvailability (history only; optional)
  → prepareMaterialRequirement / submitMaterialRequirement
        → Warehouse Transfer Documents (DRAFT) + Warehouse Tasks
  → [Warehouse send/receive of Transfer Documents — Warehouse UI]
  → confirmMaterialReceipt(logicalTransferId)   ← expects template logical transfers
        → typically EMPTY for MR-only flow
  → prepareRelease / releaseProducts + Warehouse consume
  → optional cancelOrderProduction
```

**FINDING-F1 (chain break):** Material Requirement Submit does not create `production.material_transfers`. Receipt UI lists logical transfers from the template repository. For orders that only use MR, receipt has nothing actionable; Release still works without receipt.

---

## 4. Production State Machine

### Item statuses (`ProductionStatus`)

| Status | Persisted? | Meaning |
|--------|------------|---------|
| `NOT_STARTED` | No | Absence of row / query semantics |
| `IN_PRODUCTION` | Yes | Created at Launch |
| `PARTIALLY_RELEASED` | Yes | After partial Release |
| `RELEASED` | Yes | `releasedQuantity == orderedQuantity` |
| `CANCELLED` | Yes | Unfinished cancelled; released qty preserved |

**Evidence:** `ProductionStatus.java`, `ProductionItemState.java`, `V23__production_schema.sql` CHECK constraint.

### Order view statuses (`OrderProductionViewStatus`) — computed, not stored

| Status | Rule (summary) |
|--------|----------------|
| `NOT_ACCEPTED` | No item states |
| `IN_PRODUCTION` | Any `IN_PRODUCTION` or `PARTIALLY_RELEASED` |
| `MANUFACTURED` | All items `RELEASED` |
| `CANCELLED` | Posted cancellation context and/or all cancelled |

**Evidence:** `OrderProductionViewCalculator`, `ProductionOrderViewService` + `ProductionCancellationQuery.hasPostedCancellation`.

### Transitions

| From | To | Trigger | Actor | Document / command | Result |
|------|-----|---------|-------|--------------------|--------|
| (absent) | `IN_PRODUCTION` | Accept | User with `production.order.accept` | `production.launch` via `ProductionLaunchService` | Whole order ACTIVE items launched; foundation frozen |
| `IN_PRODUCTION` / `PARTIALLY_RELEASED` | `PARTIALLY_RELEASED` / `RELEASED` | Release | User with `production.release.create` | `production.release` via `ReleaseProductsService` | Qty released; Warehouse consume actuals |
| unfinished | `CANCELLED` | Cancel | User with `production.cancellation.create` | `production.cancellation` | Active→0; released preserved; `RELEASED` items `PRESERVED_RELEASED` |
| `RELEASED` (item) | — | Cancel | — | Rejected for fully manufactured order view | Cannot cancel manufactured order |

### Behavior checks (no code changes)

| Question | Actual behavior | Evidence |
|----------|-----------------|----------|
| Re-launch same order? | **No** — conflict / unique identity | `ProductionLaunchConflictException`; UK on item identity |
| Launch more than ordered? | **No** — launch qty = ordered only | `ProductionItemState.launch` |
| Partial item launch? | **No** | Whole-order Launch |
| Release more than active? | **No** | Domain + service guards; concurrent over-release IT |
| Cancel already released qty? | Released **preserved**; unfinished cancelled | `ProductionItemState.cancel`; cancel ITs |
| Change Spec after Launch? | Production keeps frozen `SpecificationId` | `ProductionFoundationQueryService` uses `getSpecificationById` only |

---

## 5. Production Documents

| Document type | Payload | Processor | Create | Post | Close | Cancel / Unpost | Idempotency | TX boundary | Persistence | UI entry |
|---------------|---------|-----------|--------|------|-------|-----------------|-------------|-------------|-------------|----------|
| `production.launch` | `ProductionLaunchPayload` (order + lines with foundation + qty) | `ProductionLaunchProcessor` | DE `createDocument` | DE `postDocument` | N/A | `onUnpost` → Unsupported | Not idempotent (2nd launch conflicts) | DE ambient TX on post | Item states + history | «Принять в производство» |
| `production.release` | `ProductionRelease` plan/fact lines | `ProductionReleaseProcessor` | Internal gateway in Release TX | Same outer TX | N/A | `onUnpost` unsupported; `ProductionReleaseImmutableException` after POST | New document per release event | Outer `TransactionTemplate` REQUIRED: lock → consume → post | `production_releases*` | «Выпустить изделия» → confirm |
| `production.cancellation` | Cancellation + item actions | `ProductionCancellationProcessor` | Internal gateway | Same outer TX | N/A | Unpost unsupported; immutable after POST; UK one cancel per order | Not idempotent (2nd cancel blocked) | Outer REQUIRED + FOR UPDATE | `production_cancellations*` | «Отменить производство заказа» |

**Material check / Material Requirement / Transfer are not Production Business Documents** (history events + Production-owned aggregates / Warehouse documents).

### Posted document immutability

| Check | Result |
|-------|--------|
| Processors refuse UNPOST | Yes — all three |
| Domain payload markPosted guards | Yes — Release / Cancellation |
| DB unique cancel per order | Yes — `production_cancellations` |

**No FINDING** on posted Production document mutability for the three document types.

---

## 6. Launch

### Input (actual)

| Field | Source |
|-------|--------|
| `orderId` | UI / `acceptOrderIntoProduction` |
| ACTIVE items | OM `OrderQueryService.getOrderForProduction` |
| `SpecificationId` per item | Same OM query |
| `orderedQuantity` per item | Same OM query |
| `createdBy` | UI actor string |

**Not used as Launch inputs:** RevisionNumber, per-item launch qty, material readiness.

### Validations (`ProductionLaunchService.launch`)

1. Order exists  
2. `OrderStatus.ACTIVE`  
3. `activeItemCount > 0` and non-empty lines  
4. No `missingSpecificationItemIds`  
5. Per line: freeze foundation; qty = positive ordered quantity  

### Quantity formula (actual — not invented)

```text
For each ACTIVE item with Specification:
  orderedQuantity  = OM orderedQuantity
  launchedQuantity = orderedQuantity
  activeProductionQuantity = orderedQuantity
  releasedQuantity = 0
  status = IN_PRODUCTION
```

**Launchable Quantity:** **does not exist** as a named concept or remaining-to-launch formula in code. Launch is all-or-nothing per ACTIVE item at whole-order accept.

### Guards

| Case | Behavior |
|------|----------|
| Duplicate launch | Conflict / unique constraint; whole document fails |
| CANCELLED order production | New launch not applicable once states exist (conflict) |
| Missing specification | `SpecificationNotAvailableForLaunchException` |
| Material readiness | **Not checked** |

---

## 7. Release

### Inputs

- Order id  
- Selected item releases: `sourceOrderItemId` + `releaseQuantity`  
- Material actual usages + production-cell allocations  

### Rules

| Concern | Behavior |
|---------|----------|
| Remaining / active | Release ≤ `activeProductionQuantity` |
| Partial | Allowed; status → `PARTIALLY_RELEASED` |
| Full | When cumulative released == ordered → `RELEASED` |
| Repeated release | Allowed while active remains |
| Over-release | Rejected (domain + service); concurrent IT: only one of two over-releases succeeds |
| Plan formula | Spec §15.1.1 cumulative proportional: `Plan = C_after − C_before` with scale 6 HALF_UP; final release closes exact `Q` |
| Consumption | Warehouse `consume` on **actual** quantities |
| Preview trust | `prepareRelease` unlocked; confirm **recomputes plan under lock** |

### Concurrency mechanism (Release)

| Mechanism | Present? | Where |
|-----------|----------|-------|
| Outer REQUIRED TX | Yes | `ReleaseProductsService` |
| `SELECT … FOR UPDATE` whole order item states | Yes | `JdbcProductionItemStateRepository.findBySourceOrderIdForUpdate` via `ProductionOrderStateLockService` |
| Optimistic version alone for release | Not the primary release guard | Item state has `version` column; release locking is pessimistic |
| Concurrent Release tests | Yes | `ReleaseProductsPostgresIT` |
| Concurrent Release vs Cancel | Yes | `ReleaseCancelOverlapPostgresIT` |

**No FINDING** on missing Release concurrency protection for the documented scenarios covered by ITs.

---

## 8. Cancellation

| Aspect | Actual |
|--------|--------|
| Scope | Whole order only |
| Service | `CancelOrderProductionService` |
| Document | `production.cancellation` |
| Lock | Same order FOR UPDATE lock service |
| Warehouse | **No** Warehouse mutation / return |
| Released qty | Preserved |
| Allowed when view | `IN_PRODUCTION` only (rejects `MANUFACTURED`) |
| Second cancel | Blocked (posted evidence + unique order) |

---

## 9. Concurrency

| Scenario | Lock | Where | Transaction | Expected result | Existing test |
|----------|------|-------|-------------|-----------------|---------------|
| **A. Two users launch same Order Item** | Unique identity + launch conflict | Launch processor / UK | DE post path | Second fails | Unit: duplicate conflict rollback; **no multi-thread Launch IT found** → **FINDING-C1** (gap in concurrent Launch proof) |
| **B. Two users release same Order Item** | `FOR UPDATE` item states | `ReleaseProductsService` | Outer REQUIRED | Serialized; no lost update; over-release one wins | `ReleaseProductsPostgresIT` |
| **C. Release + cancellation** | Same FOR UPDATE | Both services | Outer REQUIRED each | Loser waits then rejects or preserves released | `ReleaseCancelOverlapPostgresIT`, `CancelOrderProductionPostgresIT` |
| **D. Release + launch** | Launch blocked by existing states | Launch conflict | — | Launch cannot re-accept launched items | Conflict tests (not multi-thread) |
| **E. Production + Warehouse material change** | Release stock precheck + consume in TX; MR submit shortage rolls back | Release / Submit MR | Outer TX | Consume failure rolls back release; MR shortage keeps DRAFT | Release rollback ITs; `SubmitMaterialRequirementPostgresIT` |
| MR quantity edit concurrent | Optimistic `version` | `MaterialRequirementRepository` | Ambient | Exactly one wins | `MaterialRequirementPostgresIT` |
| MR submit concurrent | `FOR UPDATE` + idempotent SUBMITTED | `SubmitMaterialRequirementService` | ACID | Documents exactly once | `SubmitMaterialRequirementPostgresIT` |

**FINDING-C1:** Concurrent dual Launch of the same order is protected by uniqueness/conflict logic, but there is no PostgreSQL multi-thread Launch IT analogous to Release concurrency proofs.

---

## 10. Order Integration

### Production → Order Management (runtime)

| Port / adapter | OM Public API | Purpose |
|----------------|---------------|---------|
| `DefaultOrderForProductionQueryAdapter` | `getOrderForProduction` | Launch eligibility + lines + SpecificationId + qty |
| `DefaultOrderSpecificationQueryAdapter` | `getSpecificationById` | Post-launch frozen spec content |
| `getCurrentItemSpecification` on adapter | Present | **No `src/main` Production caller found** (Launch uses `getOrderForProduction`) |

### Direct OM private DB from Production main?

**No.** Grep of `tmp-production/src/main`: no `order_management.` SQL.

### UI

Workbench additionally uses OM `OrderQueryService` for display (order number, customer, items) — outside Production module, still public API.

**FINDING:** none on private OM table access from Production main.

---

## 11. Warehouse Integration

### Production → Warehouse (runtime)

| Production action | Warehouse API | Operation | Stock effect |
|-------------------|---------------|-----------|--------------|
| Material availability | `WarehouseQueryApi` (+ reference APIs) | Read AVAILABLE | None |
| MR Submit | `WarehouseDemandCommandApi.acceptProductionDemand` | Persist Warehouse Demand; best-effort Transfer DRAFTs for routable lines | None at submit (stock changes on later send/receive) |
| Legacy template confirm (unwired) | `WarehouseCommandApi.createTransferDraft` | DRAFT transfer operations | None until send |
| Receipt (logical transfer) | `getTransferStatus` + `receiveTransfer` | Receive SENT ops | Stock moves on Warehouse receive |
| Release | `getStockByMaterialReferenceId` / `listStorageCells` + `consume` | Consumption | Decrements production warehouse stock |

### Forbidden direct ownership checks

| Check | Result |
|-------|--------|
| Production writes `StockPosition` | **No** in main |
| Production writes `WarehouseMovement` / private ops | **No** in main |
| ArchUnit guards | Present in `Stage7ProductionArchitectureTest` |

**FINDING:** none on direct Warehouse private persistence from Production main.

---

## 12. Material Requirements

### Current active model (Stage 3.5.9 / 3.5.10)

| Question | Answer | Evidence |
|----------|--------|----------|
| Who creates | `MaterialRequirementService.prepareMaterialRequirement` | Application + API |
| Material source | Frozen Specification lines via `ProductionFoundationQueryService` | Spec aggregation |
| Quantity | Sum of `lineQuantity` by (article, color, UoM); **not** × product qty | `SpecificationMaterialRequirementCalculator` |
| Link to Order Item | `material_requirement_line_source_items` | V43 |
| Edit | DRAFT only; optimistic version | `changeMaterialRequirementQuantity` |
| Submit to Warehouse | `SubmitMaterialRequirementService` → Demand API | V44 generated docs + routing snapshot |
| Re-create | New prepare creates new DRAFT requirement (UI flow) | Application API |
| Partial availability | Submit shortage → exception / rollback keeps DRAFT | Domain + IT |
| Destination warehouse | Production warehouse assignment (`is_production` / V45 Warehouse) | `ProductionDestinationWarehouse` |

### Legacy Material Transfer Template

| Aspect | Status |
|--------|--------|
| Persistence | Present (V27/V28) |
| Confirm service | Class exists; **not** registered in `ProductionAutoConfiguration` |
| Application API | No prepare/confirm template methods |
| Spec note | Spec §13.1a HISTORICAL; §13.1 CURRENT = MR |

### Material readiness vs Launch

Availability check and MR readiness **do not** gate Launch or Release. MR prepare requires resolvable unique Warehouse material references.

---

## 13. Persistence

| Table | Purpose | Owner | Important columns | FKs | Indexes / concurrency |
|-------|---------|-------|-------------------|-----|------------------------|
| `production_item_states` | Item production state | Production | order/item/spec ids, status, qtys, timestamps | Opaque UUIDs only | UK identity; `version`; order/item/spec indexes |
| `production_item_cutting_plan_links` | Cutting links | Production | material_reference_id, cutting_plan_id | → item_states | — |
| `material_transfer_templates(+lines…)` | Legacy template | Production | status DRAFT/CONFIRMED | Internal | `version` |
| `material_transfers` + `operation_refs` | Logical transfer + WH op refs | Production | draft operation ids | → templates | — |
| `production_releases(+item/material lines)` | Release payload | Production | document_id, plan/fact | Internal | — |
| `production_cancellations(+item lines)` | Cancel payload | Production | document_id, reason | Internal | UK source_order_id |
| `production_history` | Append-only history | Production | history_type, details | — | Triggers reject UPDATE/DELETE |
| `material_requirements(+lines, source_items)` | MR DRAFT/SUBMITTED | Production | destination warehouse, version, status | Internal | `version`; submit FOR UPDATE |
| `material_requirement_generated_documents` | WH doc links | Production | warehouse_document_id | → requirements | — |
| `material_requirement_routing_snapshot` | Routing audit | Production | routed/uncovered | → requirements/lines | — |

No Production Order table. No Revision columns (negative tests assert).

---

## 14. Flyway

| Version | Module | Purpose | Current relevance |
|---------|--------|---------|-------------------|
| V23 | production | Schema + item states | **Active SoT** for production state |
| V26 | production | Cutting plan links | Active structure; association UX not done |
| V27 | production | Material transfer templates | **Historical / readable**; not active planning algorithm |
| V28 | production | Template confirm + logical transfers | Supports legacy receipt path |
| V29 | production | Release tables | **Active** |
| V30 | production | Cancellation tables | **Active** |
| V31 | production | History | **Active** |
| V43 | production | Material requirements DRAFT | **Active** planning |
| V44 | production | MR submission + docs/snapshot | **Active** submit path |
| V45 | warehouse | `is_production` warehouse assignment | Used by Production destination resolution |
| V46–V47 | warehouse | Actor / comments | Not Production-owned |

**Production is not global Flyway latest.** Global continuum continues through Warehouse (and others) beyond V44. Production tests were previously corrected to stop assuming Production owns the latest version — **do not reverse that**.

---

## 15. Security

Canonical permissions (`ProductionPermissions` — exactly 7):

| Action | Permission | UI gate | Backend gate |
|--------|------------|---------|--------------|
| View Production | `production.order.view` | Nav | Query API + list transfers / destination |
| Accept / Launch | `production.order.accept` | Accept button | `acceptOrderIntoProduction` |
| Check materials | `production.materials.check` | Check button | `checkMaterialAvailability` |
| Material requirement / transfer | `production.transfer.create` | Request / Submit | prepare / change / submit |
| Confirm receipt | `production.receipt.confirm` | Receipt ∧ receivable | `confirmMaterialReceipt` |
| Release | `production.release.create` | Release | prepare / releaseProducts |
| Cancel | `production.cancellation.create` | Cancel | `cancelOrderProduction` |

### UI/backend mismatches (factual)

1. **Применить количество** — not bound to `canTransfer` disable flag (backend still enforces permission).  
2. **Add/Remove allocation** — no `canRelease` disable binding.  
3. **Confirm release** — no loading disable (toolbar has it).  
4. Receipt has extra Warehouse lifecycle gate beyond permission (intentional).

No separate “Material Requirement” permission distinct from `production.transfer.create`.

---

## 16. UI Audit

### Screen inventory

**Single Production screen:** Production Workbench  
Files: `ProductionWorkbenchScreen.fxml`, `ProductionWorkbenchController`, `ProductionWorkbenchViewModel`, `ProductionActionPolicy`, `ProductionPresentationLabels`.

Orders list integrates Production **only** as operational status (no Spec/Revision columns).

### Workbench sections

| Section | Purpose | User | Data shown | Actions | States |
|---------|---------|------|------------|---------|--------|
| Order selector | Open order | Production user | UUID or number prompt; order #, customer, status | Open, Refresh | Loading |
| Toolbar | Lifecycle commands | Same | — | Accept, Check, Request materials, Confirm receipt, Release, Cancel | Policy by order status + permissions |
| Позиции | Item production facts | Same | Position (± UUID), status, qtys, **Specification ID UUID**, cutting UUID/count, release qty | Select rows; edit «К выпуску» | Item statuses |
| Наличие материалов | Availability snapshot | Same | Material, required, warehouses, deficit (also unresolved text), planning source | After Check | Informational |
| История | Business history | Same | Time, event RU, actor, summary | Read-only | — |
| Логическое перемещение | Pick transfer for receipt | Same | createdAt + English WH lifecycle | Combo selection | Empty if no template transfers |
| Потребность в материалах | MR DRAFT panel | Same | Article, name, color, qty, UoM | Apply qty, Submit | Conditional |
| Выпуск — план/факт | Release confirm | Same | Plan/fact, cell allocations | Add/remove allocation, Confirm | Conditional |

### What the user sees now (observable)

- One dense scrollable page for the entire production lifecycle  
- Technical UUIDs in selector prompt, position column, Specification ID column, cutting labels, release success (`documentId`)  
- English Warehouse statuses in transfer combo amid Russian UI  
- Duplicate status detail for several order statuses  
- Item checkboxes used for materials/release selection while Cancel is whole-order (dialog explains; easy to misread)  
- Receipt control coexists with MR workflow even when logical transfers are empty  

**No new UX design proposed in this audit** (per task restriction).

---

## 17. Current User Workflow

### Existing role/capability assumption

Control docs / Spec refer to production master workflow. Runtime enforces **permission codes**, not a hard-coded “Мастер производства” role entity in Production module. Effective ability = Security roles ∪ grants that include the seven Production permissions (plus OM/Warehouse permissions for related screens).

| User intent | Current UI | Current backend |
|-------------|------------|-----------------|
| See if order is in production | Orders list status / Workbench | `ProductionQueryApi` facts/view |
| Accept order into production | Accept button | Launch document |
| Know if materials exist | Check materials | Availability query + history |
| Ask warehouse for materials | Request materials → edit → Submit | MR + Demand API |
| Confirm materials arrived (Production) | Confirm receipt + logical transfer | Receipt via template logical transfers (**often dead for MR flow**) |
| Perform warehouse physical receive | Warehouse Tasks UI (Stage 3.5) | Warehouse Transfer Document lifecycle |
| Release products | Release → plan/fact → confirm | Release doc + consume |
| Cancel unfinished production | Cancel button | Cancellation document |

---

## 18. UX Findings

### Critical UX

1. **Broken materials receipt story on Workbench for MR-only orders:** user can Submit requirement, but «Подтвердить получение» depends on logical transfers that MR does not create.  
2. **Release does not require materials received / MR submitted:** user can release without completing material supply — may be intentional architecturally, but UI presents materials steps as if sequential.  
3. **Technical identifiers dominate item grid** (`Specification ID` UUID column) — blocks readable production work.

### Major UX

4. Entire lifecycle on one page (six commands + panels) without guided states.  
5. Mixed Russian labels and English Warehouse lifecycle enums.  
6. Checkbox selection semantics vs whole-order Cancel.  
7. Deficit column overloaded with unresolved/ambiguous status text.  
8. Secondary buttons (apply qty, allocations) inconsistently gated vs primary toolbar.  
9. Success feedback shows raw `documentId` UUID after release.

### Minor UX

10. Duplicate order status / status detail strings.  
11. Cutting plan column shows UUID noise while Stage 8 association is not implemented.  
12. Prompt text emphasizes UUID alongside order number.

---

## 19. Revision Findings

Production **does not** use Order Item Revision as a user/production object. Identity is `SourceOrderId` + `SourceOrderItemId` + `SpecificationId`.

### Places where “Revision” still appears (do not remove in this audit)

| File | Class / context | Reason |
|------|-----------------|--------|
| `ProductionFoundation.java` | Domain comment | States no revision semantics |
| `SourceOrderItemId.java` | Domain comment | Stable across OM revisions |
| `SpecificationId.java` | Domain comment | Independent of Revision |
| `CuttingPlanId.java` / `ProductionCuttingPlanLink.java` | Domain comments | No Cutting revision |
| `domain/package-info.java` | Package doc | Explicit exclusion |
| `V23__production_schema.sql` | Migration comment | No Revision tables |
| `ProductionItemStateTest` | `productionDomainHasNoRevisionFields` | Negative assertion |
| `CuttingPlanLinksTest` | `assertNoRevision*` | Negative assertion |
| `ProductionSchemaFlywayTest` | No revision tables/columns | Negative schema proof |
| `JdbcProductionReleaseRepositoryTest` | `noRevisionColumnsOnReleaseTables` | Negative schema proof |
| `Stage7ProductionArchitectureTest` | `noCuttingPlanRevisionTypeInProduction` | ArchUnit |
| `PublicBoundaryPermissions` | Grants `order.revision.*` | OM fixture permissions for IT setup |
| `ProductionPublicBoundaryPostgresIT` | SQL cleanup `order_item_revisions` | Test teardown of OM tables |
| `ProductionFoundationQueryServiceTest` | Fixture text `"New revision"` | Test data string only |
| `DefaultOrderForProductionQueryAdapterTest` | Fake implements OM revision query methods | Interface completeness stub |
| `ProductionWorkbenchUiTestSupport` | Fake `OrderQueryService` revision methods + `RevisionNumber.first()` in fixture | UI test double for full OM interface |

### Production UI / public DTOs

**No** `RevisionNumber` fields on `ProductionQueryApi` / `ProductionApplicationApi` views. Workbench FXML has **no** Revision column.

**Decision required later (not now):** how to further reduce Revision visibility in test doubles / docs wording — out of scope for this audit.

---

## 20. Architecture Findings

| Rule area | Result | Evidence |
|-----------|--------|----------|
| Production → Order public API only | **PASS** (main) | Adapters + ArchUnit |
| Production → Warehouse public API only | **PASS** (main) | Query/Command/Demand/Reference APIs + ArchUnit |
| Production → Document Engine | **PASS** | Launch/Release/Cancellation processors |
| UI → Production public/application API | **PASS** | Workbench uses `ProductionApplicationApi` / `ProductionQueryApi` |
| No UI → Repository | **PASS** (ArchUnit intent) | Stage7 rules |
| No cross-capability private DB | **PASS** (main) | production schema only |
| Dual material models coexistence | **FINDING-A1** | MR active + template/receipt legacy |
| Spec §14.2 CURRENT vs §13.1 CURRENT | **FINDING-D1** | Doc internal tension (see §22) |
| Stage Manifest Spec version | **FINDING-D2** | Manifest v2.4 vs Spec/CONTEXT-MAP v2.6 |

ArchUnit suite: `Stage7ProductionArchitectureTest` (~83 rules) covering API boundaries, UI isolation, foundation freeze, release/cancel internals, history append-only, security package limits.

---

## 21. Test Coverage

### By layer (inventory summary)

| Layer | Representative coverage |
|-------|-------------------------|
| Domain | Item state transitions, quantities, foundation, view calculator, MR, template, release, cutting links, no-revision assertions |
| Application | Launch (whole-order), foundation freeze, availability, MR prepare/submit, receipt, release plan/confirm, cancel, API auth facade |
| Persistence | JDBC item/release/MR/template/history; Flyway V23–V44 contracts; V43→V44 migration IT |
| Integration / public boundary | `ProductionPublicBoundaryPostgresIT` matrix with OM+WH public APIs + DE |
| Concurrency | Release, Release↔Cancel, MR submit/edit |
| Security | 7 permissions, capability registration, query VIEW deny |
| UI | Action policy, ViewModel commands, controller FX, receipt eligibility, error mapper |
| Architecture | Stage 7 ArchUnit |

### Gaps / thin / outdated-model risks

| Gap | Severity | Notes |
|-----|----------|-------|
| No multi-thread Launch IT | Major (proof gap) | Uniqueness exists; concurrent proof thin |
| Public-boundary / UI path for MR → Warehouse receive → Production receipt continuity | Critical scenario missing as one IT | Matches FINDING-F1 |
| Template confirm tests vs unwired runtime | Tests cover dead path | Risk of false confidence |
| Cutting association STAGE7-008A | Not implemented | Expected |
| Targeted tests this session | **NOT RUN** | Maven unavailable |

---

## 22. Documentation Mismatches

| DOC says | CODE / other DOC does | Finding |
|----------|------------------------|---------|
| Stage 7 Manifest: Production Spec **v2.4** | Spec file + CONTEXT-MAP + STATUS: **v2.6** | **FINDING-D2** |
| Spec §13.1 CURRENT = Material Requirement after 3.5.10 | Code/UI: MR prepare/submit wired | Aligned |
| Spec §14.2 CURRENT still describes Stage 7 template send/receive Production receipt story | Code: MR submit is active; receipt still template-oriented | **FINDING-D1** (spec sections disagree on CURRENT) |
| Spec §2: CURRENT IMPLEMENTATION note for editable transfer template (header) vs §13.1 CURRENT MR | Same tension | **FINDING-D1** |
| STATUS: Stage 7 DONE / Closure PASS | Code present and substantial | Aligned (closure historical) |
| WORK-QUEUE STAGE7-008A PLANNED | No post-launch cutting association command in Application API | Aligned |
| ADR-033: no Revision in Production-facing contract | Domain/API/persistence comply | Aligned |
| ADR-037 target Warehouse-centric receive | Production UI still has Confirm receipt | Expected transitional; UX redesign input |

**Documents were not corrected** (audit rule).

---

## 23. KEEP

| Item | Why | Code evidence |
|------|-----|---------------|
| Item-owned state + opaque SpecificationId | Matches ADR-033 / Spec §5 | `ProductionItemState`, V23 |
| Whole-order Launch / Cancel | Spec §10 / §16 | Launch/Cancel services |
| Computed Order Production View | ADR-020 / Spec §5.3 | `OrderProductionViewCalculator` |
| Three Production documents + POSTED immutability | Spec §9 / §18 principle | Processors refuse unpost |
| Release + Consumption atomicity + FOR UPDATE | ADR-036 / STAGE7-013A | `ReleaseProductsService`, lock service, ITs |
| Partial release plan §15.1.1 | Spec calculator | `PartialReleaseMaterialPlanCalculator` |
| OM/Warehouse public API adapters | Constitution boundary | Port adapters |
| Append-only `production_history` | Spec §22 | V31 + triggers |
| Seven Production permissions | Spec §20 | `ProductionPermissions` |
| Material Requirement DRAFT/Submit model | Spec §13.1 after 3.5.10 | MR services + V43/V44 |
| ArchUnit Stage 7 guards | Closure evidence | `Stage7ProductionArchitectureTest` |

---

## 24. REWORK

| Item | Why | Code evidence |
|------|-----|---------------|
| Production Workbench UX information architecture | Dense, UUID-heavy, mixed language, unclear step sequence | FXML + ViewModel |
| Receipt UX vs Material Requirement path | Chain break FINDING-F1 | MR submit vs `listLogicalTransfers` / `confirmMaterialReceipt` |
| Spec CURRENT notes (§13 vs §14) consistency | Docs conflict FINDING-D1 | Spec file |
| Stage Manifest Spec version pin | FINDING-D2 | Manifest vs Spec v2.6 |
| Secondary UI permission/disable alignment | Minor security UX mismatch | Apply qty / allocations |
| Launch concurrency IT | FINDING-C1 | Missing multi-thread Launch IT |

---

## 25. REPLACE

| Item | Why | Code evidence |
|------|-----|---------------|
| Treating Material Transfer Template as active user planning model | Spec marks historical; UI already on MR; confirm unwired | Spec §13.1a; AutoConfiguration omission |
| User-facing “Specification ID” as primary column | Technical OM reference, not worker language | FXML `Specification ID` |
| Production-centric receipt as primary material completion UX (possibly) | ADR-037 target Warehouse-centric; Stage 3.5 Tasks exist | Spec §14.1 TARGET vs Workbench receipt |

**Decision required:** whether Production receipt button remains at all after UX redesign — **do not decide silently here**.

---

## 26. UNKNOWN

| Item | Why unknown | Decision required |
|------|-------------|-------------------|
| Exact future Production UX information model (order vs item emphasis) | Audit forbids redesign | Separate UX design stage |
| Whether Release must require materials received | Spec allows check as non-prerequisite for Launch; Release prerequisites emphasize stock for consumption, not receipt history | Product decision |
| Fate of historical template tables | Readable for completed records per Spec; cleanup policy unset | ADR / cleanup task |
| How Cutting Plan links enter UX (STAGE7-008A) | PLANNED only | Stage 8 / 008A |
| Whether concurrent Launch needs dedicated IT before UX work | Proof gap only | QA decision |

---

## 27. Deferred Questions

Questions for the user / architecture owners before Production UX redesign implementation:

1. **Should Production UI still own “Подтвердить получение”**, or is Warehouse Tasks the sole receive UX (ADR-037 TARGET)?  
2. **Must materials be received (or MR submitted) before Release is allowed**, or remain optional?  
3. **Is whole-order Launch still the only accept model**, or should UX expose item-level acceptance later?  
4. **What human-readable identity** replaces Specification ID / item UUID in daily work (position number, product name, article)?  
5. **Keep or hide Cutting Plan column** until STAGE7-008A / Stage 8?  
6. **Retention policy** for V27/V28 template tables and receipt APIs?  
7. **Should Spec §14.2 CURRENT be rewritten** to match §13.1 MR CURRENT before UX design, or wait for redesign ADR?  
8. **Align Stage 7 Manifest** to Production Spec v2.6?

---

## Appendix A — Findings register

| ID | Type | Summary |
|----|------|---------|
| FINDING-F1 | Functional / integration | MR Submit ↔ Production Receipt chain broken |
| FINDING-C1 | Concurrency proof | No multi-thread Launch IT |
| FINDING-A1 | Architecture coexistence | Dual material models (MR active + template residual) |
| FINDING-D1 | Documentation | Spec CURRENT notes conflict (§13.1 vs §14.2) |
| FINDING-D2 | Documentation | Manifest Spec v2.4 vs Spec/CONTEXT-MAP v2.6 |
| FINDING-UX-* | UX | See §18 Critical/Major/Minor |

---

## Appendix B — Explicit non-actions of this audit

- No production code changes  
- No UI / FXML / CSS changes  
- No Flyway / schema changes  
- No API / ADR / Spec edits  
- No commits / pushes  
- No `mvn clean verify` / package / jpackage  
- No Production UX redesign implementation  

---

*End of Stage 7 Production Audit report.*
