package com.tmp.security.application;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.domain.EffectivePermissionCalculator;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Centralized authorization checks. Effective permissions are always recomputed live.
 */
public final class AuthorizationApplicationService {

    private final SessionContext sessionContext;
    private final CapabilityEngine capabilityEngine;
    private final RoleAssignmentRepository roleAssignments;
    private final RoleRepository roles;
    private final PermissionOverrideRepository overrides;

    public AuthorizationApplicationService(
            SessionContext sessionContext,
            CapabilityEngine capabilityEngine,
            RoleAssignmentRepository roleAssignments,
            RoleRepository roles,
            PermissionOverrideRepository overrides) {
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.capabilityEngine = Objects.requireNonNull(capabilityEngine, "capabilityEngine");
        this.roleAssignments = Objects.requireNonNull(roleAssignments, "roleAssignments");
        this.roles = Objects.requireNonNull(roles, "roles");
        this.overrides = Objects.requireNonNull(overrides, "overrides");
    }

    public boolean hasPermission(PermissionId permissionId) {
        Objects.requireNonNull(permissionId, "permissionId");
        Optional<Session> session = sessionContext.current();
        if (session.isEmpty()) {
            return false;
        }
        if (!isActivePermission(permissionId)) {
            return false;
        }
        UserId userId = session.get().userId();
        Set<IndividualPermissionOverride> userOverrides = new HashSet<>(overrides.findByUser(userId));
        Set<Role> assignedRoles = loadRoles(userId);
        return EffectivePermissionCalculator.isGranted(permissionId, userOverrides, assignedRoles);
    }

    public void requirePermission(PermissionId permissionId) {
        if (!hasPermission(permissionId)) {
            throw new AccessDeniedException("Access denied for permission: " + permissionId.value());
        }
    }

    public Set<PermissionId> effectivePermissions() {
        Optional<Session> session = sessionContext.current();
        if (session.isEmpty()) {
            return Set.of();
        }
        Set<PermissionId> active = activePermissionIds();
        UserId userId = session.get().userId();
        return EffectivePermissionCalculator.effectivePermissions(
                active,
                new HashSet<>(overrides.findByUser(userId)),
                loadRoles(userId));
    }

    private boolean isActivePermission(PermissionId permissionId) {
        return activePermissionIds().contains(permissionId);
    }

    private Set<PermissionId> activePermissionIds() {
        return capabilityEngine.activePermissions().stream()
                .map(PermissionDescriptor::permissionId)
                .map(PermissionId::of)
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<Role> loadRoles(UserId userId) {
        Set<Role> result = new HashSet<>();
        for (RoleId roleId : roleAssignments.findRoleIdsForUser(userId)) {
            roles.findById(roleId).ifPresent(result::add);
        }
        return result;
    }
}
