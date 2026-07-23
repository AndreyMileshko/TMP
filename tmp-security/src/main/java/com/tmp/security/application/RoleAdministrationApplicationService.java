package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.Role;
import com.tmp.security.api.RoleInUseException;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role create/update/delete and role-permission mutations.
 */
public class RoleAdministrationApplicationService {

    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AuthorizationApplicationService authorization;
    private final SecurityAuditRepository auditRepository;
    private final SessionContext sessionContext;
    private final Clock clock;

    public RoleAdministrationApplicationService(
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AuthorizationApplicationService authorization,
            SecurityAuditRepository auditRepository,
            SessionContext sessionContext,
            Clock clock) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.roleAssignmentRepository =
                Objects.requireNonNull(roleAssignmentRepository, "roleAssignmentRepository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Role createRole(String name, String description) {
        authorization.requirePermission(SecurityPermissions.ROLES_CREATE);
        Role created = roleRepository.save(Role.create(RoleId.generate(), name, description, clock));
        appendAudit(AuditOperation.ROLE_CREATED, created.id(), "Role created");
        return created;
    }

    @Transactional
    public Role updateRole(RoleId roleId, String name, String description) {
        authorization.requirePermission(SecurityPermissions.ROLES_UPDATE);
        Role role = requireRole(roleId);
        Role updated = roleRepository.save(
                role.withName(name, clock).withDescription(description, clock));
        appendAudit(AuditOperation.ROLE_UPDATED, updated.id(), "Role updated");
        return updated;
    }

    @Transactional
    public Role grantPermissionToRole(RoleId roleId, PermissionId permissionId) {
        authorization.requirePermission(SecurityPermissions.PERMISSIONS_ASSIGN);
        Role role = requireRole(roleId);
        Role updated = roleRepository.save(role.grantPermission(permissionId, clock));
        appendAudit(AuditOperation.ROLE_PERMISSIONS_CHANGED, updated.id(), "Role permission granted");
        return updated;
    }

    @Transactional
    public Role revokePermissionFromRole(RoleId roleId, PermissionId permissionId) {
        authorization.requirePermission(SecurityPermissions.PERMISSIONS_ASSIGN);
        Role role = requireRole(roleId);
        Role updated = roleRepository.save(role.revokePermission(permissionId, clock));
        appendAudit(AuditOperation.ROLE_PERMISSIONS_CHANGED, updated.id(), "Role permission revoked");
        return updated;
    }

    @Transactional
    public void deleteRole(RoleId roleId) {
        authorization.requirePermission(SecurityPermissions.ROLES_DELETE);
        requireRole(roleId);
        if (roleAssignmentRepository.countUsersForRole(roleId) > 0) {
            throw new RoleInUseException("Role still assigned to users: " + roleId);
        }
        roleRepository.deleteById(roleId);
        appendAudit(AuditOperation.ROLE_DELETED, roleId, "Role deleted");
    }

    public List<Role> listRoles() {
        authorization.requirePermission(SecurityPermissions.ROLES_VIEW);
        return roleRepository.findAll();
    }

    private Role requireRole(RoleId roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
    }

    private void appendAudit(AuditOperation operation, RoleId targetId, String description) {
        var actor = sessionContext.current();
        auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                actor.map(Session::userId).orElse(null),
                actor.map(s -> s.login().value()).orElse("system"),
                operation,
                "ROLE",
                targetId.value().toString(),
                description,
                AuditResult.SUCCESS));
    }
}
