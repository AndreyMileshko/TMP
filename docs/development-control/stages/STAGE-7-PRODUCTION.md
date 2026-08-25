# Stage 7 Manifest — Production

## Source

- Production Specification **v2.4** (TMP-SPEC-012)
- ADR-033, ADR-034, ADR-035, **ADR-036** (и действующие ADR-017…022, 024, 028, 031, 032)
- Document Engine Specification **v1.2** (multi-document / ambient transaction contract)
- Order Management Public Query API (ACTIVE Order → Item → Specification + `SpecificationId`)
- Warehouse Public Query API + Warehouse Application/Document Commands (Stage 6 ownership unchanged)
- Database Specification
- Security Specification (permission contribution model)
- Cutting Optimization Specification — только для задач Cutting Plan integration (Stage 8 NOT STARTED)

## Status

**START GATE PASSED**  
**Stage 7 DONE / 100%**
**Closure Audit (`STAGE7-020`): PASS**

All 27 mandatory Stage 7 tasks are DONE (`STAGE7-001` … `STAGE7-020`, including 004A/004B/005A/012A/013A/015A/016A). `STAGE7-008A` (post-launch Cutting Plan link association) remains PLANNED, is excluded from the mandatory 27 and does not block closure.

**Active blockers:** NONE
**Production Specification:** v2.4 Accepted

## Start Gate (required before READY tasks)

| Item | Requirement | Result |
|------|-------------|--------|
| Docs alignment | Production Spec v2.4 + ADR-033…036 accepted | PASS |
| OM contract | Production-facing read without Order Item Revision; stable `SpecificationId` | PASS |
| Spec freeze | Launch фиксирует Production Specification Reference | PASS (docs) |
| Warehouse contract | Query API read-only; Transfer/Consumption = Warehouse-owned document commands | PASS |
| Cutting | 0..N MaterialReference→CuttingPlanId; no Revision Cutting Plan; Stage 8 NOT STARTED | PASS |
| Atomicity | `BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY` resolved | **PASS** |
| No MES | Manifest domains contain no routes/stages/work centers | PASS |

Atomicity evidence: outer shared ACID transaction; Document Engine REQUIRED joins ambient TX; ADR-036; Document Engine Specification v1.2; integration test `DocumentEngineMultiDocumentTransactionIT`.

## Planning domains

1. Production capability foundation (module bootstrap, contribution, security registration);
2. item-owned production state;
3. computed order-level Production View;
4. whole-order Production Launch / acceptance;
5. immutable Production Specification Reference (`Specification ID`);
6. material availability via Warehouse Public Query API;
7. 0..N Cutting Plan references by MaterialReference;
8. editable material transfer template;
9. Warehouse-owned Transfer (Application/Document commands);
10. receipt confirmation (Production UI → Warehouse receive);
11. Production Release;
12. plan/fact material consumption;
13. Warehouse Consumption integration (document-owned; atomicity ADR-036);
14. whole-order Production Cancellation;
15. documents + processors + domain events;
16. Production audit/history (Production-owned, Spec §22);
17. Production Public Query API (read-only, Spec §18);
18. persistence and migrations;
19. Security permissions for business actions;
20. simple Production workbench UI (order-centric);
21. integration tests with Order Management + Warehouse;
22. optional future Cutting contract without Stage 8 runtime dependency;
23. final closure audit.

## Explicitly out of scope (v1.0)

- MES: technological routes, operations, stages, work centers, equipment, shifts, scrap/rework, OEE, APS, calendar planning;
- Production Order entity;
- Material Master;
- Cutting Plan Revision / Production-managed Cutting lifecycle;
- Batch/FIFO/FEFO (Warehouse Stage 6 exclusions remain);
- automatic material return on cancellation;
- Analytics.

## Exit criteria

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | Production owns only its state and documents | PASS | `production` schema: item states, cutting links, transfer templates/transfers, release, cancellation, history; no Order/Warehouse/Cutting tables |
| 2 | User workflow is order-level; stored state is item-owned | PASS | whole-order Launch/Release/Cancellation; `production.production_item_states` keyed by order+item+specification |
| 3 | Specification Reference frozen at Launch | PASS | `ProductionFoundation.freeze` at Launch; post-launch reads only `getSpecificationById`; ArchUnit `foundationQueryMustNot*` |
| 4 | Cutting Plan links support 0..N by material | PASS | `CuttingPlanLinks`, `production.production_item_cutting_plan_links`; opaque ids, no lifecycle |
| 5 | Order Management and Warehouse boundaries preserved (Query vs Document) | PASS | `com.tmp.order.api` / `com.tmp.warehouse.api` only; Transfer/Consumption stay Warehouse-owned commands |
| 6 | Release/Consumption atomicity proven (ADR-036) and implemented | PASS | outer shared TX in `ReleaseProductsService`; `releaseFailureRollsBackPublicWarehouseConsumption` |
| 7 | Production business history and Public Query API implemented (Spec §18, §22) | PASS | append-only `production.production_history` with UPDATE/DELETE triggers; four read-only `ProductionQueryApi` operations |
| 8 | No MES; no Production Order; Cutting optional | PASS | no work centers/shifts/routing/OEE; no `ProductionOrder` aggregate/entity/table; Production runs with Stage 8 NOT STARTED |
| 9 | Constitution and Accepted ADR satisfied | PASS | ADR-017/018/020/021/024/032/033/034/035/036; Document Engine Spec v1.2 |
| 10 | Stage 7 closure audit PASS | PASS | `STAGE7-020`; full reactor `mvn test` / `mvn verify` PASS; 72/72 Stage 7 ArchUnit rules; `git diff --check` clean |

## Closure

**Closure Audit:** PASS (`STAGE7-020`, 2026-08-25)
**Mandatory tasks:** 27 / 27 DONE
**Progress:** 100%
**Active blockers:** NONE
**Non-blocking backlog:** `STAGE7-008A` (post-launch Cutting Plan link association) remains PLANNED
**Next stage:** Stage 8 Cutting Optimization — NOT STARTED (Start Gate not run)
