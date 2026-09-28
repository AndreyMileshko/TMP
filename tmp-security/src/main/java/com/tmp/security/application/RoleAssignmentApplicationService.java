package com.tmp.security.application;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleAlreadyAssignedException;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.EffectivePermissionCalculator;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.RoleAssignment;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserNotActiveException;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assign / revoke roles for users; read effective permissions for administration UX.
 */
public class RoleAssignmentApplicationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PermissionOverrideRepository permissionOverrideRepository;
    private final CapabilityEngine capabilityEngine;
    private final AuthorizationApplicationService authorization;
    private final SecurityAuditRepository auditRepository;
    private final SessionContext sessionContext;
    private final Clock clock;

    public RoleAssignmentApplicationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            PermissionOverrideRepository permissionOverrideRepository,
            CapabilityEngine capabilityEngine,
            AuthorizationApplicationService authorization,
            SecurityAuditRepository auditRepository,
            SessionContext sessionContext,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.roleAssignmentRepository =
                Objects.requireNonNull(roleAssignmentRepository, "roleAssignmentRepository");
        this.permissionOverrideRepository =
                Objects.requireNonNull(permissionOverrideRepository, "permissionOverrideRepository");
        this.capabilityEngine = Objects.requireNonNull(capabilityEngine, "capabilityEngine");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public void assignRole(UserId userId, RoleId roleId) {
        authorization.requirePermission(SecurityPermissions.ROLES_ASSIGN);
        requireActiveUser(userId);
        if (roleRepository.findById(roleId).isEmpty()) {
            throw new IllegalArgumentException("Role not found: " + roleId);
        }
        if (roleAssignmentRepository.findRoleIdsForUser(userId).contains(roleId)) {
            throw new RoleAlreadyAssignedException("Роль уже назначена пользователю.");
        }
        roleAssignmentRepository.assign(RoleAssignment.of(userId, roleId, clock.instant()));
        appendAudit(AuditOperation.ROLE_ASSIGNED, userId, roleId, "Role assigned");
    }

    @Transactional
    public void revokeRole(UserId userId, RoleId roleId) {
        authorization.requirePermission(SecurityPermissions.ROLES_ASSIGN);
        roleAssignmentRepository.revoke(userId, roleId);
        appendAudit(AuditOperation.ROLE_REVOKED, userId, roleId, "Role revoked");
    }

    public Set<RoleId> listRolesForUser(UserId userId) {
        authorization.requirePermission(SecurityPermissions.ROLES_ASSIGN);
        Objects.requireNonNull(userId, "userId");
        return Set.copyOf(roleAssignmentRepository.findRoleIdsForUser(userId));
    }

    /**
     * Live effective permissions for administration display (roles ∪ GRANT − REVOKE).
     * Does not change {@link AuthorizationApplicationService} or the calculator.
     */
    public Set<PermissionId> listEffectivePermissionsForUser(UserId userId) {
        authorization.requirePermission(SecurityPermissions.ROLES_ASSIGN);
        Objects.requireNonNull(userId, "userId");
        Set<PermissionId> active =
                capabilityEngine.activePermissions().stream()
                        .map(PermissionDescriptor::permissionId)
                        .map(PermissionId::of)
                        .collect(Collectors.toUnmodifiableSet());
        Set<IndividualPermissionOverride> overrides =
                new HashSet<>(permissionOverrideRepository.findByUser(userId));
        return EffectivePermissionCalculator.effectivePermissions(active, overrides, loadRoles(userId));
    }

    private Set<Role> loadRoles(UserId userId) {
        Set<Role> result = new HashSet<>();
        for (RoleId roleId : roleAssignmentRepository.findRoleIdsForUser(userId)) {
            roleRepository.findById(roleId).ifPresent(result::add);
        }
        return result;
    }

    private User requireActiveUser(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!user.isActive()) {
            throw new UserNotActiveException("Cannot assign role to inactive user: " + userId);
        }
        return user;
    }

    private void appendAudit(AuditOperation operation, UserId userId, RoleId roleId, String description) {
        var actor = sessionContext.current();
        auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                actor.map(Session::userId).orElse(null),
                actor.map(s -> s.login().value()).orElse("system"),
                operation,
                "USER_ROLE",
                userId.value() + ":" + roleId.value(),
                description,
                AuditResult.SUCCESS));
    }
}
