package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.PermissionOverrideDecision;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserNotActiveException;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Individual permission GRANT / REVOKE / remove override for a user.
 */
public class PermissionOverrideApplicationService {

    private final UserRepository userRepository;
    private final PermissionOverrideRepository overrideRepository;
    private final AuthorizationApplicationService authorization;
    private final SecurityAuditRepository auditRepository;
    private final SessionContext sessionContext;
    private final Clock clock;

    public PermissionOverrideApplicationService(
            UserRepository userRepository,
            PermissionOverrideRepository overrideRepository,
            AuthorizationApplicationService authorization,
            SecurityAuditRepository auditRepository,
            SessionContext sessionContext,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.overrideRepository = Objects.requireNonNull(overrideRepository, "overrideRepository");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public void grantIndividualPermission(UserId userId, PermissionId permissionId) {
        saveOverride(userId, permissionId, PermissionOverrideDecision.GRANT, AuditOperation.PERMISSION_GRANTED);
    }

    @Transactional
    public void revokeIndividualPermission(UserId userId, PermissionId permissionId) {
        saveOverride(userId, permissionId, PermissionOverrideDecision.REVOKE, AuditOperation.PERMISSION_REVOKED);
    }

    @Transactional
    public void removeOverride(UserId userId, PermissionId permissionId) {
        authorization.requirePermission(SecurityPermissions.PERMISSIONS_ASSIGN);
        Optional<IndividualPermissionOverride> existing =
                overrideRepository.findByUserAndPermission(userId, permissionId);
        if (existing.isEmpty()) {
            return;
        }
        overrideRepository.remove(userId, permissionId);
        appendAudit(AuditOperation.PERMISSION_OVERRIDE_REMOVED, userId, permissionId, "Override removed");
    }

    private void saveOverride(
            UserId userId,
            PermissionId permissionId,
            PermissionOverrideDecision decision,
            AuditOperation operation) {
        authorization.requirePermission(SecurityPermissions.PERMISSIONS_ASSIGN);
        requireActiveUser(userId);
        Optional<IndividualPermissionOverride> existing =
                overrideRepository.findByUserAndPermission(userId, permissionId);
        IndividualPermissionOverride next = existing
                .map(o -> o.withDecision(decision, clock))
                .orElseGet(() -> IndividualPermissionOverride.of(userId, permissionId, decision, clock));
        overrideRepository.save(next);
        appendAudit(operation, userId, permissionId, "Individual permission " + decision.name());
    }

    private void requireActiveUser(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!user.isActive()) {
            throw new UserNotActiveException("Cannot change overrides for inactive user: " + userId);
        }
    }

    private void appendAudit(
            AuditOperation operation, UserId userId, PermissionId permissionId, String description) {
        var actor = sessionContext.current();
        auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                actor.map(Session::userId).orElse(null),
                actor.map(s -> s.login().value()).orElse("system"),
                operation,
                "PERMISSION_OVERRIDE",
                userId.value() + ":" + permissionId.value(),
                description,
                AuditResult.SUCCESS));
    }
}
