# Stage 3.5 Warehouse Checkpoint

**Date:** 2026-09-28  
**Base HEAD:** `703e3721800f32d0a7949163413a0e036c6cc381`  
**Working tree:** dirty (empty-state placeholder fix only; no commit / no push)  
**Flyway:** V47 (no new migration)  
**Stage 3.5 status:** IN PROGRESS until manual confirmation

## Completed

- Stocks workspace
- Warehouse operations
- Transfer workflow
- Tasks workflow
- History
- Adjustment
- Receipt
- RBAC integration

## Deferred

- Inventory
- Reservation
- Write-off redesign
- Information Links
- Production
- Role templates
- Analytics
- Delete permissions / revision.create (RBAC backlog)

## Known minor UX backlog

- Manual interactive Warehouse acceptance still pending (Склад → Задачи / Остатки / История / Поступление)
- Manual Security acceptance still pending (Безопасность → Пользователи / Роли)
- JavaFX unnamed-module WARN on packaged startup (known, non-blocking)
- Full reactor not re-run after Stage 3.5.11–3.5.15 / RBAC corrective cycles

## Stabilization note (2026-09-28)

- Fixed: table empty placeholder was bound to `statusMessage`, so success text such as «Принято: 10» could replace empty-state hints after Receive with an empty task list.
- Fix: dedicated `tableEmptyMessage` for Tasks / Stocks / History placeholders; status bar keeps success feedback.

## Verification snapshot

| Gate | Result |
|------|--------|
| Targeted Warehouse UI + domain | PASS |
| Targeted Security / RBAC | PASS |
| Stage6WarehouseArchitectureTest | PASS |
| Stage4SecurityArchitectureTest | PASS |
| `mvn -pl :tmp-bootstrap-app -am clean install -DskipTests` | PASS |
| Package `dist/jpackage/TMP/TMP.exe` | PASS (2026-09-28 17:07:37) |
| Startup `localhost:55432/tmp_gui_stage5` | PASS — Flyway 47; DesktopBootstrap ~4.7s; exceptions NONE |
| DB business delta after startup | 0 |
| Full reactor | NOT RUN |

## Status

READY FOR NEXT MODULE

(Stage 3.5 remains formally IN PROGRESS until manual confirmation.)
