# TMP RBAC Audit

**Audit date:** 2026-09-28  
**HEAD:** `d4ff07728bbfead4e88da517ff4d0d54c50a27d8` (`d4ff077 3.5.15 Refine task editing and Warehouse adjustment history`)  
**Scope:** analysis only — no code, permission, role, Flyway, or database changes.  
**Result:** **FINDINGS**

---

## Baseline

| Field | Value |
|---|---|
| HEAD | `d4ff07728bbfead4e88da517ff4d0d54c50a27d8` |
| Working tree | Dirty (uncommitted changes present; not modified by this audit) |
| Existing uncommitted changes | Modified: `IMPLEMENTATION-LOG.md`, `VERIFICATION-LOG.md`, `UiShellAutoConfiguration.java`, User Administration UI/tests; Untracked: `UserSecurityPresentation.java`, `UserSecurityPresentationTest.java` |
| Destructive git ops | None |
| Commit / push | None |

---

## Effective permission model

Effective authorization is computed live in `AuthorizationApplicationService` via `EffectivePermissionCalculator`:

1. Session required; deleted users cannot authorize.
2. Permission must be **active** (declared by an active Capability catalogue).
3. **Individual overrides:**
   - `REVOKE` for a permission → denied (highest priority);
   - else `GRANT` → allowed;
4. Else **union of all assigned roles’ permissions** (multiple roles merge by union).
5. **No role inheritance hierarchy** (roles are flat templates).
6. **Direct user permissions** exist as individual GRANT/REVOKE overrides (`PermissionOverrideApplicationService`, requires `security.permissions.assign`).
7. UI visibility uses `AuthorizationService.hasPermission`; backend uses `requirePermission` (UI is not a substitute).

Bootstrap system role:

- Only **`Security Administrator`** is created by bootstrap (`BootstrapAdministratorApplicationService`).
- Ensure services auto-grant that role: all Security permissions + all 16 Warehouse permissions.
- **Order** and **Production** permissions are **not** auto-ensured for Security Administrator.
- No seeded operational roles such as «Кладовщик» — such roles would be admin-created custom roles only.

---

## Permission Inventory

Total declared production Capability permissions: **48**  
(Security 12 + Warehouse 16 + Order 13 + Production 7).  
Analytics: **0** (Stage 9 not started).  
Sample fixture `sample.technical.view`: not registered in bootstrap app — excluded from production inventory.

| Permission | Exists | Used In | Status |
|---|---|---|---|
| security.users.view | Yes | UserAdmin BE+nav+UI | GREEN |
| security.users.create | Yes | UserAdmin BE+UI | GREEN |
| security.users.update | Yes | UserAdmin BE+UI | GREEN |
| security.users.delete | Yes | UserAdmin BE+UI (logical delete) | GREEN |
| security.users.reset-password | Yes | PasswordApplicationService + UI | GREEN |
| security.roles.view | Yes | RoleAdmin BE+nav+UI | GREEN |
| security.roles.create | Yes | RoleAdmin BE+UI | GREEN |
| security.roles.update | Yes | RoleAdmin BE+UI | GREEN |
| security.roles.delete | Yes | RoleAdmin BE+UI | GREEN |
| security.roles.assign | Yes | RoleAssignment BE+User/Role UI | GREEN |
| security.permissions.assign | Yes | Role permissions + overrides BE+UI | GREEN |
| security.audit.view | Yes | AuditQuery BE+nav | GREEN |
| warehouse.stock.view | Yes | Warehouse queries, Workspace/Workbench nav, tasks/history/stock | GREEN |
| warehouse.receipt.create | Yes | Receipt BE + Workspace/Workbench UI | GREEN |
| warehouse.move.create | Yes | Move BE + Workspace/Workbench UI | GREEN |
| warehouse.transfer.create | Yes | Transfer/send/receive/reject/return/take-in-work BE + Workspace tasks UI | GREEN |
| warehouse.reservation.create | Yes | Reservation BE + legacy Workbench UI only | YELLOW |
| warehouse.consumption.create | Yes | Consumption BE + Workspace/Workbench UI | GREEN |
| warehouse.adjustment.create | Yes | Adjustment BE + Workspace/Workbench UI | GREEN |
| warehouse.inventory.create | Yes | `WarehouseInventoryService.reconcile` only; no public API/UI | YELLOW |
| warehouse.warehouse.view | Yes | Settings nav + structure list BE | GREEN |
| warehouse.warehouse.create | Yes | Create warehouse BE + Settings UI | GREEN |
| warehouse.warehouse.update | Yes | Update warehouse / responsibility BE + Settings UI | GREEN |
| warehouse.warehouse.delete | Yes | Catalog + UI flag on Workbench; **no delete API/action** | GRAY |
| warehouse.storage-cell.view | Yes | Catalogue list access + Settings cell load | GREEN |
| warehouse.storage-cell.create | Yes | Create cell BE + Settings UI | GREEN |
| warehouse.storage-cell.update | Yes | Update cell BE + Settings UI | GREEN |
| warehouse.storage-cell.delete | Yes | Catalog + UI flag on Workbench; **no delete API/action** | GRAY |
| order.order.view | Yes | Order query/list/editor nav | GREEN |
| order.order.create | Yes | Create/import BE + UI | GREEN |
| order.order.edit | Yes | Order edit BE + UI | GREEN |
| order.order.approve | Yes | Approve / transfer-to-work BE + UI | GREEN |
| order.order.cancel | Yes | Cancel BE + UI | GREEN |
| order.item.view | Yes | Item queries/list/editor nav | GREEN |
| order.item.create | Yes | Item create BE + UI | GREEN |
| order.item.edit | Yes | Item update BE + UI | GREEN |
| order.item.approve | Yes | Backend approve path + import; **UI Save does not gate on it** | YELLOW |
| order.item.cancel | Yes | Item cancel BE + UI | GREEN |
| order.revision.create | Yes | Backend `beginRevisionCreate` API; **no UI caller** | GRAY |
| order.revision.edit | Yes | Revision/spec update BE + Spec editor UI | GREEN |
| order.specification.view | Yes | Spec query + editor nav/UI | GREEN |
| production.order.view | Yes | Production query + workbench nav | GREEN |
| production.order.accept | Yes | Accept BE + UI | GREEN |
| production.materials.check | Yes | Check materials BE + UI | GREEN |
| production.transfer.create | Yes | Create transfer demand BE + UI | GREEN |
| production.receipt.confirm | Yes | Confirm receipt BE + UI | GREEN |
| production.release.create | Yes | Release BE + UI | GREEN |
| production.cancellation.create | Yes | Cancel production BE + UI | GREEN |
| analytics.material-movements.view | No | Spec Stage 9 only | YELLOW (planned) |
| analytics.material-movements.export | No | Spec Stage 9 only | YELLOW (planned) |

**Counts (production catalogue):**

| Metric | Count |
|---|---|
| Total declared | 48 |
| GREEN | 41 |
| YELLOW | 3 (`warehouse.reservation.create` primary-UI gap; `warehouse.inventory.create`; `order.item.approve` UI gate gap) |
| GRAY | 3 (`warehouse.warehouse.delete`, `warehouse.storage-cell.delete`, `order.revision.create`) |
| Used (GREEN+YELLOW with backend use) | 45 |
| Unused / no callable surface (GRAY) | 3 |

---

## Module audit matrices

### Security

| Function | Required Permission | Existing | Backend Check | UI Check | Result |
|---|---|---|---|---|---|
| Open Users | security.users.view | Yes | listUsers require | Nav + screen gate | GREEN |
| Create user | security.users.create | Yes | Yes | Button `canCreate` | GREEN |
| Update user | security.users.update | Yes | Yes | Button `canUpdate` | GREEN |
| Block user | (none designed) | N/A | N/A | N/A | N/A — only Active/Deleted; no separate lock |
| Delete user (logical) | security.users.delete | Yes | Yes | Button `canDelete` | GREEN |
| Reset password | security.users.reset-password | Yes | Yes | Button `canResetPassword` | GREEN |
| Open Roles | security.roles.view | Yes | listRoles require | Nav + screen gate | GREEN |
| Create role | security.roles.create | Yes | Yes | `canCreate` | GREEN |
| Update role | security.roles.update | Yes | Yes | `canUpdate` | GREEN |
| Delete role | security.roles.delete | Yes | Yes | `canDelete` | GREEN |
| Assign roles to user | security.roles.assign | Yes | Yes | User/Role UI | GREEN |
| Assign role permissions / overrides | security.permissions.assign | Yes | Yes | Role permission tree | GREEN |
| View security audit | security.audit.view | Yes | AuditQuery require | Nav + screen gate | GREEN |

### Warehouse

| Function | Required Permission | Existing | Backend Check | UI Check | Result |
|---|---|---|---|---|---|
| Open Warehouse section | warehouse.stock.view | Yes | Yes | Nav `warehouse.nav.workbench` | GREEN |
| View warehouses / stock / history / tasks | warehouse.stock.view | Yes | Yes | Workspace tabs | GREEN |
| Receipt | warehouse.receipt.create | Yes | Yes | Workspace | GREEN |
| Internal move | warehouse.move.create | Yes | Yes | Workspace | GREEN |
| Inter-warehouse transfer (docs) | warehouse.transfer.create | Yes | Yes | Workspace | GREEN |
| Adjustment | warehouse.adjustment.create | Yes | Yes | Workspace | GREEN |
| Consumption | warehouse.consumption.create | Yes | Yes | Workspace | GREEN |
| Reservation link | warehouse.reservation.create | Yes | Yes | Workbench only (not modern Workspace) | YELLOW |
| Inventory reconcile | warehouse.inventory.create | Yes | `WarehouseInventoryService` | No public API / no UI | YELLOW |
| See task | warehouse.stock.view (+ transfer for actions) | Yes | Yes | Tasks tab | GREEN |
| Take in work | warehouse.transfer.create | Yes | Yes | `canTransfer` + state | GREEN |
| Send / transfer | warehouse.transfer.create | Yes | Yes | Same | GREEN |
| Accept (receive) | warehouse.transfer.create | Yes | Yes | Same | GREEN |
| Reject | warehouse.transfer.create | Yes | Yes | Same | GREEN |
| Return materials | warehouse.transfer.create | Yes | Yes | Same | GREEN |
| Open Settings | warehouse.warehouse.view | Yes | Yes | Nav settings | GREEN |
| Create warehouse | warehouse.warehouse.create | Yes | Yes | Settings | GREEN |
| Update warehouse / responsibilities | warehouse.warehouse.update | Yes | Yes | Settings | GREEN |
| Delete warehouse | warehouse.warehouse.delete | Yes | **No API** | Flag only (Workbench) | GRAY |
| Create cell | warehouse.storage-cell.create | Yes | Yes | Settings | GREEN |
| Update cell | warehouse.storage-cell.update | Yes | Yes | Settings | GREEN |
| View cells | warehouse.storage-cell.view (or stock/ops) | Yes | Catalogue list access | Settings | GREEN |
| Delete cell | warehouse.storage-cell.delete | Yes | **No API** | Flag only (Workbench) | GRAY |

Note: Warehouse task actions intentionally share `warehouse.transfer.create` (no separate task.* permissions). Responsibility (ADR-037) is an additional gate beyond RBAC.

### Orders

| Function | Required Permission | Existing | Backend Check | UI Check | Result |
|---|---|---|---|---|---|
| View orders / open list | order.order.view | Yes | Yes | Nav + list | GREEN |
| Create order | order.order.create | Yes | Yes | Create/Import | GREEN |
| Edit order | order.order.edit | Yes | Yes | Editor | GREEN |
| Cancel order | order.order.cancel | Yes | Yes | Editor | GREEN |
| Transfer to work / approve | order.order.approve | Yes | Yes | Editor `canTransferToWork` | GREEN |
| View items | order.item.view | Yes | Yes | Item list/editor | GREEN |
| Create item | order.item.create | Yes | Yes | Item UI | GREEN |
| Edit item | order.item.edit | Yes | Yes | Item UI | GREEN |
| Cancel item | order.item.cancel | Yes | Yes | Item UI | GREEN |
| Approve item revision | order.item.approve | Yes | Yes (save/import paths) | **Not gated on Save button** | YELLOW |
| Create N+1 revision | order.revision.create | Yes | API only | **No UI call** | GRAY |
| Edit revision / specification lines | order.revision.edit | Yes | Yes | Spec editor | GREEN |
| View specification | order.specification.view | Yes | Yes | Spec editor | GREEN |
| Import order | order.order.create (+ item.create, revision.edit, item.approve, order.approve) | Yes | Yes | Import UI (create only for button) | YELLOW (UI checks create; BE requires fuller set) |

### Production

| Function | Required Permission | Existing | Backend Check | UI Check | Result |
|---|---|---|---|---|---|
| Open production | production.order.view | Yes | Yes | Nav | GREEN |
| Open order / positions | production.order.view | Yes | QueryApi | Workbench | GREEN |
| Accept | production.order.accept | Yes | Yes | Action flags | GREEN |
| Check materials | production.materials.check | Yes | Yes | Action flags | GREEN |
| Request materials / create transfer | production.transfer.create | Yes | Yes (+ Warehouse transfer for docs) | Action flags | GREEN |
| Confirm receipt | production.receipt.confirm | Yes | Yes (+ Warehouse transfer receive) | Action flags | GREEN |
| Release | production.release.create | Yes | Yes (+ Warehouse consumption) | Action flags | GREEN |
| Cancel production | production.cancellation.create | Yes | Yes | Action flags | GREEN |

### Analytics

| Function | Required Permission | Existing | Backend Check | UI Check | Result |
|---|---|---|---|---|---|
| Open Analytics | analytics.*.view (spec) | No | No module | No | YELLOW — Stage 9 NOT STARTED |
| Export | analytics.*.export (spec) | No | No | No | YELLOW |

---

## Missing Permissions

Functions that exist without a dedicated / sufficient permission **definition**, or where protection is incomplete relative to the implemented action.

| Function | Module | Risk | Notes |
|---|---|---|---|
| Order item Save → auto `ITEM_APPROVE` path | Orders | Medium | Permission exists, but UI enables Save with `order.item.edit` only; backend may deny when approve path runs |
| Order Import confirm | Orders | Medium | UI gates primarily on `order.order.create`; backend requires create+item.create+revision.edit+item.approve+order.approve |
| Dedicated Warehouse task accept/reject/take | Warehouse | Low | Not missing by catalogue design — all use `warehouse.transfer.create`; document if finer split is desired later |
| User block/lock | Security | N/A | Feature not implemented (Active/Deleted only) |

No case found where a mutating public API in Security / Warehouse / Order / Production lacks **any** `requirePermission` for its primary operation (except structure delete / inventory UI surfaces that simply do not exist).

---

## Unused Permissions

| Permission | Reason |
|---|---|
| warehouse.warehouse.delete | Declared in catalogue; Workbench sets `canDeleteWarehouse`; no backend delete method; Settings UI has no delete action |
| warehouse.storage-cell.delete | Same pattern for storage cells |
| order.revision.create | Backend `beginRevisionCreate` exists and is permission-checked; no UI-shell caller; import path does not use it |

Related (not fully unused, but incomplete surface):

| Permission | Reason |
|---|---|
| warehouse.inventory.create | Checked in `WarehouseInventoryService`; not exposed on `WarehouseApi`; no UI |
| warehouse.reservation.create | Used on legacy Workbench; modern Workspace (primary nav) has no reservation UI |

---

## UI Backend Mismatch

| Feature | UI | Backend | Result |
|---|---|---|---|
| Order item Save (ACTIVE + draft revision with spec) | Enabled with `order.item.edit` | May call approve → needs `order.item.approve` | UI shows / Backend may deny |
| Order Import | Button/screen mainly `order.order.create` | Multi-permission set on confirm | UI shows / Backend may deny |
| Warehouse structure delete | Workbench boolean flags for delete perms | No delete API | UI flag dead / Backend N/A |
| Warehouse inventory | No inventory reconcile UI | Service enforces `warehouse.inventory.create` | No UI / Backend ready |
| Reservation | Workbench only | API enforces reservation permission | Primary Workspace hides / Workbench allows |
| Spec editor edit | Requires view + `order.revision.edit` | `REVISION_EDIT` on update | Aligned |
| Warehouse task Accept/Reject/Take/Return | Gated by `warehouse.transfer.create` + task state | Same transfer permission | Aligned |
| Production actions | Per-action permission flags | Matching ProductionPermissions | Aligned |
| Security admin buttons | Fine-grained SecurityPermissions | Matching application services | Aligned |

---

## Role coverage

### Security Administrator (system)

| Area | Coverage |
|---|---|
| Assigned (ensure) | All 12 Security + all 16 Warehouse |
| Security module | OK |
| Warehouse module | OK (full catalogue granted) |
| Orders | **Gap** — no bootstrap ensure of Order permissions |
| Production | **Gap** — no bootstrap ensure of Production permissions |
| Analytics | N/A |

Potential gaps: first administrator cannot open Заказы / Производство until permissions are assigned manually via Roles UI.

### Кладовщик / other operational roles

| Area | Coverage |
|---|---|
| Seeded role | **None** — not created by bootstrap |
| Assigned permissions | Admin-defined only |
| Actual module coverage | Depends on custom role setup |
| Potential gaps | No standard storekeeper / production / order-manager templates |

### Custom roles

- Created via Role Administration.
- Permissions assigned via `security.permissions.assign`.
- Multiple roles → union; overrides can GRANT/REVOKE individually.

---

## Existing security / authorization tests

| Test | Coverage (high level) |
|---|---|
| `WarehouseSecurityAuthorizationTest` | Warehouse view/ops/structure/reservation/inventory/transfer permission denials & allows |
| `ProductionQueryApiAuthorizationTest` | Production query requires `production.order.view` |
| `AuthorizationApplicationServiceTest` | Effective permission calculation / require |
| `DefaultOrderQueryServiceSecurityTest` | Order query view permissions |
| `EffectivePermissionCalculatorTest` | Override + role merge rules |
| `SecurityAdministrationCapabilityTest` | Security permission catalogue contribution |
| `OrderListCreatePermissionTest` / `OrderListPermissionBootstrapIT` | Order create UI/bootstrap wiring |
| `SecurityAdminNavigationAccessEnsureTest` | Admin Security permission ensure |
| `WarehouseAdminNavigationAccessEnsureTest` | Admin Warehouse permission ensure |
| `Stage4SecurityArchitectureTest` / `Stage6WarehouseArchitectureTest` | Architecture boundaries for security usage |
| UI FX/VM tests (User/Role/Order/Warehouse/Production) | Visibility flags for selected permissions |

No new tests were written by this audit.

---

## Recommendations

### Immediate

1. Decide whether Security Administrator should also receive Order + Production permission ensures (parity with Warehouse).
2. Fix UI gates for Order item Save / Import to require the same permission set the backend will enforce (`order.item.approve`, import multi-set).
3. Document that Warehouse task Accept/Reject/Take/Return are intentionally under `warehouse.transfer.create`.

### Future

1. Implement or remove catalogue entries for `warehouse.warehouse.delete` / `warehouse.storage-cell.delete`.
2. Expose inventory reconcile on public API + UI under `warehouse.inventory.create`, or demote/retire the permission if Inventory remains out of scope.
3. Either add N+1 revision UI under `order.revision.create`, or keep permission only for future use and mark in Security Spec.
4. Move reservation UX into modern Workspace or clarify Workbench as the only reservation surface.
5. Seed optional operational role templates (e.g. Кладовщик, Производство, Менеджер заказов) after product agreement — do not invent codes now.
6. Analytics permissions only when Stage 9 starts (`analytics.material-movements.view|export` per Stage Manifest).

### Not needed

1. Separate permissions for each Warehouse task button (unless product requires finer split).
2. Separate «user block» permission while Spec defines only Active/Deleted.
3. Analytics permissions before Analytics capability exists.
4. Role inheritance hierarchy (current flat roles + overrides match Spec).

---

## Database

No changes.

## Code changes

None (audit document only).

## COMMIT

No commit.  
No push.
