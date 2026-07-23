package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration: create / update display name / logical delete / list.
 */
public class UserAdministrationApplicationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthorizationApplicationService authorization;
    private final SecurityAuditRepository auditRepository;
    private final SessionContext sessionContext;
    private final Clock clock;

    public UserAdministrationApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            AuthorizationApplicationService authorization,
            SecurityAuditRepository auditRepository,
            SessionContext sessionContext,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public User createUser(Login login, DisplayName displayName, char[] initialPassword) {
        authorization.requirePermission(SecurityPermissions.USERS_CREATE);
        Objects.requireNonNull(initialPassword, "initialPassword");
        try {
            User created = userRepository.save(User.createActive(
                    UserId.generate(),
                    login,
                    displayName,
                    passwordHasher.hash(initialPassword),
                    clock));
            appendAudit(AuditOperation.USER_CREATED, created.id(), "User created");
            return created;
        } finally {
            Arrays.fill(initialPassword, '\0');
        }
    }

    @Transactional
    public User updateUser(UserId userId, DisplayName newDisplayName) {
        authorization.requirePermission(SecurityPermissions.USERS_UPDATE);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        User updated = userRepository.save(user.withDisplayName(newDisplayName, clock));
        appendAudit(AuditOperation.USER_UPDATED, updated.id(), "User updated");
        return updated;
    }

    @Transactional
    public User deleteUser(UserId userId) {
        authorization.requirePermission(SecurityPermissions.USERS_DELETE);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        User deleted = userRepository.save(user.deleted(clock));
        appendAudit(AuditOperation.USER_DELETED, deleted.id(), "User logically deleted");
        sessionContext.current().ifPresent(session -> {
            if (session.userId().equals(userId)) {
                sessionContext.close();
            }
        });
        return deleted;
    }

    public List<User> listUsers(int pageIndex, int pageSize, UserStatus statusFilter) {
        authorization.requirePermission(SecurityPermissions.USERS_VIEW);
        return userRepository.findPage(pageIndex, pageSize, statusFilter);
    }

    private void appendAudit(AuditOperation operation, UserId targetId, String description) {
        var actor = sessionContext.current();
        auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                actor.map(s -> s.userId()).orElse(null),
                actor.map(s -> s.login().value()).orElse("system"),
                operation,
                "USER",
                targetId.value().toString(),
                description,
                AuditResult.SUCCESS));
    }
}
