package com.tmp.ui.shell.screen.useradmin;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.PermissionSummary;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.ui.shell.screen.roleadmin.PermissionNamespaceGroup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Pure presentation helpers for user ↔ roles ↔ effective permissions (read model only).
 */
final class UserSecurityPresentation {

    static final String NO_ROLES = "Роли не назначены";
    static final String NO_PERMISSIONS = "Нет доступных прав";

    private UserSecurityPresentation() {
    }

    static String formatRolesLabel(List<String> roleNames) {
        Objects.requireNonNull(roleNames, "roleNames");
        if (roleNames.isEmpty()) {
            return NO_ROLES;
        }
        return String.join(", ", roleNames);
    }

    static List<String> roleNamesForUser(Set<RoleId> assignedRoleIds, List<RoleSummary> allRoles) {
        Objects.requireNonNull(assignedRoleIds, "assignedRoleIds");
        Objects.requireNonNull(allRoles, "allRoles");
        List<String> names = new ArrayList<>();
        for (RoleSummary role : allRoles) {
            if (assignedRoleIds.contains(role.id())) {
                names.add(role.name());
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return List.copyOf(names);
    }

    static Set<PermissionId> effectivePermissionsFromRoles(
            Set<RoleId> assignedRoleIds, List<RoleSummary> allRoles) {
        Objects.requireNonNull(assignedRoleIds, "assignedRoleIds");
        Objects.requireNonNull(allRoles, "allRoles");
        Set<PermissionId> effective = new HashSet<>();
        for (RoleSummary role : allRoles) {
            if (assignedRoleIds.contains(role.id())) {
                effective.addAll(role.permissionIds());
            }
        }
        return Set.copyOf(effective);
    }

    static List<RoleAssignmentItem> roleAssignmentItems(
            List<RoleSummary> allRoles, Set<RoleId> assignedRoleIds) {
        Objects.requireNonNull(allRoles, "allRoles");
        Objects.requireNonNull(assignedRoleIds, "assignedRoleIds");
        List<RoleSummary> sorted = new ArrayList<>(allRoles);
        sorted.sort(Comparator.comparing(RoleSummary::name, String.CASE_INSENSITIVE_ORDER));
        List<RoleAssignmentItem> items = new ArrayList<>(sorted.size());
        for (RoleSummary role : sorted) {
            items.add(new RoleAssignmentItem(role.id(), role.name(), assignedRoleIds.contains(role.id())));
        }
        return List.copyOf(items);
    }

    static List<EffectivePermissionGroup> effectivePermissionGroups(
            List<PermissionSummary> catalogue, Set<PermissionId> effectivePermissions) {
        Objects.requireNonNull(catalogue, "catalogue");
        Objects.requireNonNull(effectivePermissions, "effectivePermissions");
        if (catalogue.isEmpty()) {
            return List.of();
        }
        List<EffectivePermissionGroup> groups = new ArrayList<>();
        for (PermissionNamespaceGroup namespaceGroup : PermissionNamespaceGroup.group(catalogue)) {
            List<EffectivePermissionItem> items = new ArrayList<>();
            for (PermissionSummary permission : namespaceGroup.permissions()) {
                items.add(new EffectivePermissionItem(
                        permission.permissionId(),
                        permission.displayName(),
                        effectivePermissions.contains(permission.permissionId())));
            }
            groups.add(new EffectivePermissionGroup(namespaceGroup.displayName(), List.copyOf(items)));
        }
        return List.copyOf(groups);
    }

    record RoleAssignmentItem(RoleId roleId, String name, boolean assigned) {
        RoleAssignmentItem {
            Objects.requireNonNull(roleId, "roleId");
            Objects.requireNonNull(name, "name");
        }
    }

    record EffectivePermissionItem(PermissionId permissionId, String displayName, boolean granted) {
        EffectivePermissionItem {
            Objects.requireNonNull(permissionId, "permissionId");
            Objects.requireNonNull(displayName, "displayName");
        }
    }

    record EffectivePermissionGroup(String displayName, List<EffectivePermissionItem> permissions) {
        EffectivePermissionGroup {
            Objects.requireNonNull(displayName, "displayName");
            permissions = List.copyOf(Objects.requireNonNull(permissions, "permissions"));
        }
    }
}
