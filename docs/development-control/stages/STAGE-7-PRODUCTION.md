# Stage 7 Manifest — Production

## Source

- Production Specification **v2.1** (TMP-SPEC-012)
- ADR-033, ADR-034, ADR-035 (и действующие ADR-017…022, 024, 028, 031, 032)
- Order Management Public Query API (ACTIVE Order → Item → Specification + `SpecificationId`)
- Warehouse Public Query API + Warehouse Application/Document Commands (Stage 6 ownership unchanged)
- Document Engine transactional contract
- Database Specification
- Security Specification (permission contribution model)
- Cutting Optimization Specification — только для задач Cutting Plan integration (Stage 8 NOT STARTED)

## Status

**NOT STARTED / 0%** — documentation updated; Start Gate not passed.

**Active blocker:** `BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY` (OPEN)

Implementation must not begin until Start Gate closes open blockers.

## Start Gate (required before READY tasks)

| Item | Requirement |
|------|-------------|
| Docs alignment | Production Spec v2.1 + ADR-033…035 accepted |
| OM contract | Production-facing read without Order Item Revision; stable `SpecificationId` |
| Spec freeze | Launch фиксирует Production Specification Reference |
| Warehouse contract | Query API read-only; Transfer/Consumption = Warehouse-owned document commands |
| Cutting | 0..N MaterialReference→CuttingPlanId; no Revision Cutting Plan; Stage 8 NOT STARTED |
| Atomicity | `BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY` resolved |
| No MES | Manifest domains contain no routes/stages/work centers |

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
13. Warehouse Consumption integration (document-owned; atomicity Start Gate);
14. whole-order Production Cancellation;
15. documents + processors + domain events;
16. persistence and migrations;
17. Security permissions for business actions;
18. simple Production workbench UI (order-centric);
19. integration tests with Order Management + Warehouse;
20. optional future Cutting contract without Stage 8 runtime dependency;
21. final closure audit.

## Explicitly out of scope (v1.0)

- MES: technological routes, operations, stages, work centers, equipment, shifts, scrap/rework, OEE, APS, calendar planning;
- Production Order entity;
- Material Master;
- Cutting Plan Revision / Production-managed Cutting lifecycle;
- Batch/FIFO/FEFO (Warehouse Stage 6 exclusions remain);
- automatic material return on cancellation;
- Analytics.

## Exit criteria

- Production owns only its state and documents;
- user workflow is order-level; stored state is item-owned;
- Specification Reference frozen at Launch;
- Cutting Plan links support 0..N by material;
- Order Management and Warehouse boundaries preserved (Query vs Document);
- Release/Consumption atomicity proven;
- no MES; no Production Order; Cutting optional;
- Constitution and Accepted ADR satisfied;
- Stage 7 closure audit PASS.
