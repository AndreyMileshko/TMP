package com.tmp.security.domain;

import com.tmp.security.api.PermissionId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Pure domain service: calculates effective permissions from role grants and individual overrides.
 * Never persists the result.
 */
public final class EffectivePermissionCalculator {

    private EffectivePermissionCalculator() {
    }

    public static boolean isGranted(
            PermissionId permissionId,
            Set<IndividualPermissionOverride> overrides,
            Set<Role> assignedRoles) {
        Objects.requireNonNull(permissionId, "permissionId");
        Objects.requireNonNull(overrides, "overrides");
        Objects.requireNonNull(assignedRoles, "assignedRoles");

        for (IndividualPermissionOverride override : overrides) {
            if (permissionId.equals(override.permissionId())
                    && override.decision() == PermissionOverrideDecision.REVOKE) {
                return false;
            }
        }
        for (IndividualPermissionOverride override : overrides) {
            if (permissionId.equals(override.permissionId())
                    && override.decision() == PermissionOverrideDecision.GRANT) {
                return true;
            }
        }
        for (Role role : assignedRoles) {
            if (role.permissions().contains(permissionId)) {
                return true;
            }
        }
        return false;
    }

    public static Set<PermissionId> effectivePermissions(
            Set<PermissionId> declaredActivePermissionIds,
            Set<IndividualPermissionOverride> overrides,
            Set<Role> assignedRoles) {
        Objects.requireNonNull(declaredActivePermissionIds, "declaredActivePermissionIds");
        Set<PermissionId> result = new HashSet<>();
        for (PermissionId permissionId : declaredActivePermissionIds) {
            if (isGranted(permissionId, overrides, assignedRoles)) {
                result.add(permissionId);
            }
        }
        return Set.copyOf(result);
    }
}
