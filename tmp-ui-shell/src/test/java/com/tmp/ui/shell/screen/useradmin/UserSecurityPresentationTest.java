package com.tmp.ui.shell.screen.useradmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.ui.shell.screen.useradmin.UserSecurityPresentation.EffectivePermissionGroup;
import com.tmp.ui.shell.screen.useradmin.UserSecurityPresentation.RoleAssignmentItem;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserSecurityPresentationTest {

    private static final Instant TS = Instant.parse("2026-09-28T00:00:00Z");

    @Test
    void formatRolesLabelJoinsNamesOrShowsEmptyState() {
        assertEquals(UserSecurityPresentation.NO_ROLES, UserSecurityPresentation.formatRolesLabel(List.of()));
        assertEquals("Кладовщик, Менеджер", UserSecurityPresentation.formatRolesLabel(List.of("Кладовщик", "Менеджер")));
    }

    @Test
    void effectivePermissionsUnionAcrossRoles() {
        RoleId roleA = RoleId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        RoleId roleB = RoleId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        PermissionId p1 = PermissionId.of("warehouse.stock.view");
        PermissionId p2 = PermissionId.of("order.order.view");
        List<RoleSummary> roles = List.of(
                role("A", roleA, Set.of(p1)),
                role("B", roleB, Set.of(p2)));

        Set<PermissionId> effective =
                UserSecurityPresentation.effectivePermissionsFromRoles(Set.of(roleA, roleB), roles);

        assertEquals(Set.of(p1, p2), effective);
    }

    @Test
    void roleAssignmentItemsMarkAssignedRoles() {
        RoleId roleA = RoleId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        RoleId roleB = RoleId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        List<RoleSummary> roles = List.of(
                role("Кладовщик", roleA, Set.of()),
                role("Менеджер", roleB, Set.of()));

        List<RoleAssignmentItem> items =
                UserSecurityPresentation.roleAssignmentItems(roles, Set.of(roleA));

        assertEquals(2, items.size());
        assertTrue(items.get(0).assigned());
        assertEquals("Кладовщик", items.get(0).name());
        assertFalse(items.get(1).assigned());
    }

    @Test
    void effectivePermissionGroupsUseExistingNamespaceHierarchy() {
        PermissionId view = PermissionId.of("warehouse.stock.view");
        PermissionId move = PermissionId.of("warehouse.stock.move");
        List<PermissionSummary> catalogue = List.of(
                new PermissionSummary(view, "Просмотр склада", "", true),
                new PermissionSummary(move, "Перемещение материалов", "", true));

        List<EffectivePermissionGroup> groups =
                UserSecurityPresentation.effectivePermissionGroups(catalogue, Set.of(view));

        assertEquals(1, groups.size());
        assertEquals("Склад", groups.get(0).displayName());
        assertEquals(2, groups.get(0).permissions().size());
        assertTrue(groups.get(0).permissions().stream()
                .anyMatch(p -> p.granted() && "Просмотр склада".equals(p.displayName())));
        assertTrue(groups.get(0).permissions().stream()
                .anyMatch(p -> !p.granted() && "Перемещение материалов".equals(p.displayName())));
    }

    private static RoleSummary role(String name, RoleId id, Set<PermissionId> permissions) {
        return new RoleSummary(id, name, "", permissions, 0L, TS, TS);
    }
}
