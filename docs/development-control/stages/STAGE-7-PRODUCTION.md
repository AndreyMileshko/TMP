# Stage 7 Manifest — Production

## Source

- Production Specification **v2.0** (TMP-SPEC-012)
- ADR-033, ADR-034, ADR-035 (и действующие ADR-017…022, 024, 028, 031, 032)
- Order Management Public Query API (ACTIVE Order → Item → current immutable Specification)
- Warehouse Public API / Transfer / Consumption (Stage 6 as implemented)
- Document Engine transactional contract
- Database Specification
- Security Specification (permission contribution model)

## Status

**NOT STARTED** — documentation updated; Start Gate not passed; implementation must not begin until Start Gate closes open blockers.

## Start Gate (required before READY tasks)

| Item | Requirement |
|------|-------------|
| Docs alignment | Production Spec v2.0 + ADR-033…035 accepted |
| OM contract | Production-facing read without mandatory Order Item Revision in Production state |
| Warehouse contract | Transfer send/receive + Consumption from production warehouse unchanged in ownership |
| Cutting | Optional reference only; Stage 8 NOT STARTED; no Revision Cutting Plan |
| Atomicity | `BLK-STAGE7-RELEASE-CONSUMPTION-ATOMICITY` resolved or accepted with explicit ADR/DE proof |
| No MES | Manifest domains contain no routes/stages/work centers |

## Planning domains

1. Production capability foundation (module bootstrap, contribution, security registration);
2. item production state + computed Order Production View;
3. whole-order Production Launch;
4. material availability integration with Warehouse Query API;
5. optional Cutting Plan ID reference (no Stage 8 runtime dependency);
6. editable material transfer template;
7. Warehouse Transfer send/receive integration (Production UI may confirm receive);
8. Production Release;
9. plan/fact material consumption with Warehouse Consumption;
10. whole-order Production Cancellation;
11. documents + processors + domain events;
12. persistence and migrations;
13. Security permissions for business actions;
14. simple Production workbench UI (order-centric);
15. integration tests with Order Management + Warehouse;
16. optional future Cutting contract without Stage 8 dependency;
17. final closure audit.

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
- Order Management and Warehouse boundaries preserved;
- Release/Consumption atomicity proven;
- no MES; no Production Order; Cutting optional;
- Constitution and Accepted ADR satisfied;
- Stage 7 closure audit PASS.
