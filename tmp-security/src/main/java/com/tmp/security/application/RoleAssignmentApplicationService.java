package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.RoleAssignment;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserNotActiveException;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assign / revoke roles for users.
 */
public class RoleAssignmentApplicationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AuthorizationApplicationService authorization;
    private final SecurityAuditRepository auditRepository;
    private final SessionContext sessionContext;
    private final Clock clock;

    public RoleAssignmentApplicationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AuthorizationApplicationService authorization,
            SecurityAuditRepository auditRepository,
            SessionContext sessionContext,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.roleAssignmentRepository =
                Objects.requireNonNull(roleAssignmentRepository, "roleAssignmentRepository");
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
        roleAssignmentRepository.assign(RoleAssignment.of(userId, roleId, clock.instant()));
        appendAudit(AuditOperation.ROLE_ASSIGNED, userId, roleId, "Role assigned");
    }

    @Transactional
    public void revokeRole(UserId userId, RoleId roleId) {
        authorization.requirePermission(SecurityPermissions.ROLES_ASSIGN);
        roleAssignmentRepository.revoke(userId, roleId);
        appendAudit(AuditOperation.ROLE_REVOKED, userId, roleId, "Role revoked");
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
