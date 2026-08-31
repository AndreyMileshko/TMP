# POST-CLOSURE PRODUCTION/Warehouse CORRECTION — 2026-08-31

**Base corrective commit:** `2c7113b6d150f9f87218417b6828ac937be73f3e`

**Context:** Stage 7 was formally closed on 2026-08-25 (STAGE7-020 DONE). Manual acceptance testing discovered defects after closure. This pass corrects them without reopening STAGE7-019/STAGE7-020 or creating new Stage 7 task IDs.

## Manual defects

| ID | Summary | Status |
|---|---|---|
| PROD-MANUAL-004 | Transfer allocation rows disappeared from lower JavaFX table after template line re-selection | Reproduced; fixed; JavaFX controller regression test added |
| PROD-MANUAL-005 | Warehouse Transfer DRAFT list visibility / permission mismatch | Reproduced; fixed (VIEW reads drafts; TRANSFER sends selected draft) |
| PROD-MANUAL-006 | Production Receive enabled/disabled inconsistent with Warehouse transfer lifecycle | Already fixed in 2c7113b; strengthened with public-boundary lifecycle test |
| PROD-MANUAL-007 | Existing Production-created DRAFT must be sent via same operation ID | Already fixed in 2c7113b; proven DRAFT → SAME ID SENT → Receive → RECEIVED in public-boundary IT |

## Corrections

- Restored Stage 6 manual Warehouse Transfer Send form alongside DRAFT table (Section A + Section B in «Межскладское перемещение»).
- Normalized draft list permissions: `warehouse.view` for read/refresh; `warehouse.transfer` for send selected draft and manual send.
- Extended `ProductionPublicBoundaryPostgresIT` with listTransferDrafts identity proof, 3+4+3 partial release closure, plan/fact deviation, zero actual, receipt lifecycle fail-closed cases.
- Added `ProductionWorkbenchControllerFxTest.transferLineReselectionKeepsAllocationRowsVisibleInTable`.

## Verification (2026-08-31)

Full reactor regression rerun on corrected tree. Previous 2026-08-25 green log is superseded for this code.

Stage 7: **REMAINS DONE**
Stage 8: **NOT STARTED**
