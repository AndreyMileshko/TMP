# Stage 7 Production — Completion

**Date:** 2026-10-02  
**Baseline (Phase 8 start):** `9d0a7f7bc7deb8d0f0298096d5fe70b244b39451`  
**Phase:** IMPLEMENTATION PHASE 8 — Final Consolidation / Cleanup / Acceptance

## STATUS: COMPLETE

Stage 7 Production UX Phases 1–8 are implemented. Stage 8 (Cutting) is **NOT STARTED**.

## Implemented capabilities

| Capability | Status |
|---|---|
| Launch (whole-order Accept) | DONE |
| Quantity Mode STANDARD / FLEXIBLE | DONE |
| Cross-order Material Request + DRAFT reopen + Submit | DONE |
| Material Readiness (AVAILABLE snapshot, no reservation) | DONE |
| Release + Partial Release + Plan/Fact | DONE |
| Multi-order Release orchestration (per-order TX) | DONE |
| Cancellation (Order Card; unfinished cancelled; released preserved) | DONE |
| History (Order Card + details; human-readable types) | DONE |
| RBAC (7 Production permissions retained) | DONE |
| Concurrency (mode / DRAFT / coverage / Release / Cancel locks) | DONE |

## Explicitly out of Stage 7

| Item | Status |
|---|---|
| Cutting Plan association UI / `STAGE7-008A` | DEFERRED |
| New Warehouse reservation / order pinning | NOT INTRODUCED |
| Production receipt UI | REMOVED (Warehouse owns physical receipt) |
| Order Item Revision in Production UI | NOT USED |
| New Production Order entity | NOT INTRODUCED |
| MR Submit → history event | KNOWN NON-BLOCKING GAP (DEP-9) |
| Actor display name (FIO) via Security | DEFERRED (login/`—` fallback) |

## Legacy retention (Phase 8)

| Component | Decision | Reason |
|---|---|---|
| Material Transfer Template domain/persistence | RETAINED | Historical logical transfers; `listLogicalTransfers`; ArchUnit rules; Flyway tables |
| `ConfirmMaterialTransferService` | RETAINED | Architecture / historical tests; not Spring-wired into new MR flow |
| `confirmMaterialReceipt` / receipt services | RETAINED | Public Application API + ArchUnit; may confirm historical logical transfers |
| Legacy Flyway tables / migrations | RETAINED | No destructive DROP; no historical migration edits |
| `production.receipt.confirm` permission | RETAINED | Security/RBAC contract; UI no longer exposes receipt |
| `production.materials.check` permission | RETAINED | Backend `checkMaterialAvailability` still exists |

## Action model (final)

| User operation | Entry |
|---|---|
| Launch | Order Card primary Accept |
| Quantity Mode | Order Card only |
| Material Request | LEVEL 1 tree selection |
| Release | LEVEL 1 tree selection |
| Cancellation | Order Card Actions menu |
| Receipt | Warehouse only |
| Cutting | Hidden |

## Verification (Phase 8)

Recorded in VERIFICATION-LOG / Phase 8 final report:

- Targeted Production UI + architecture tests
- Full reactor `mvn clean verify`
- Package `mvn -pl :tmp-bootstrap-app pre-integration-test -Ppackage -DskipTests`
- Packaged app startup smoke

## No commit / no push

Phase 8 leaves working tree dirty for human review; agent does not commit.
