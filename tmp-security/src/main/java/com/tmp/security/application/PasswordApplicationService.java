package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.api.InvalidCurrentPasswordException;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.PasswordPolicy;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * Self-service password change, initial password setup, and administrative password reset requests.
 * Never logs, audits, or puts password/hash material into exception messages.
 */
public class PasswordApplicationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final AuthorizationApplicationService authorization;
    private final SecurityAuditRepository auditRepository;
    private final SessionContext sessionContext;
    private final Clock clock;

    public PasswordApplicationService(
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
    public void changeOwnPassword(char[] currentPassword, char[] newPassword) {
        Objects.requireNonNull(currentPassword, "currentPassword");
        Objects.requireNonNull(newPassword, "newPassword");
        Session session = sessionContext.current()
                .orElseThrow(() -> new IllegalStateException("Not authenticated"));
        try {
            User user = userRepository.findById(session.userId())
                    .orElseThrow(() -> new IllegalStateException("Session user missing"));
            if (!passwordHasher.matches(currentPassword, user.passwordHash())) {
                throw new InvalidCurrentPasswordException();
            }
            PasswordPolicy.validateNewPassword(newPassword);
            User updated = userRepository.save(
                    user.withPasswordHash(passwordHasher.hash(newPassword), clock));
            appendAudit(
                    AuditOperation.PASSWORD_CHANGED,
                    session.userId(),
                    session.login().value(),
                    updated.id(),
                    "Password changed by user");
        } finally {
            Arrays.fill(currentPassword, '\0');
            Arrays.fill(newPassword, '\0');
        }
    }

    @Transactional
    public User initializePassword(
            com.tmp.security.api.Login login, char[] newPassword, char[] confirmPassword) {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(newPassword, "newPassword");
        Objects.requireNonNull(confirmPassword, "confirmPassword");
        try {
            PasswordPolicy.validate(newPassword, confirmPassword);
            User user = userRepository.findByLoginIgnoreCase(login)
                    .filter(User::isActive)
                    .filter(User::passwordSetupRequired)
                    .orElseThrow(() -> new IllegalArgumentException("Password setup is not required for login"));
            User updated = userRepository.save(
                    user.withPasswordInitialized(passwordHasher.hash(newPassword), clock));
            appendAudit(
                    AuditOperation.PASSWORD_INITIALIZED,
                    updated.id(),
                    updated.login().value(),
                    updated.id(),
                    "Password initialized by user");
            return updated;
        } finally {
            Arrays.fill(newPassword, '\0');
            Arrays.fill(confirmPassword, '\0');
        }
    }

    @Transactional
    public void requestPasswordReset(UserId targetUserId) {
        authorization.requirePermission(SecurityPermissions.USERS_RESET_PASSWORD);
        Objects.requireNonNull(targetUserId, "targetUserId");
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + targetUserId));
        User updated = userRepository.save(user.requiringPasswordSetup(clock));
        var actor = sessionContext.current();
        appendAudit(
                AuditOperation.PASSWORD_RESET,
                actor.map(Session::userId).orElse(null),
                actor.map(s -> s.login().value()).orElse("system"),
                updated.id(),
                "Password reset requested; user must set a new password on next login");
    }

    private void appendAudit(
            AuditOperation operation,
            UserId actorUserId,
            String actorLogin,
            UserId targetId,
            String description) {
        auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                actorUserId,
                actorLogin,
                operation,
                "USER",
                targetId.value().toString(),
                description,
                AuditResult.SUCCESS));
    }
}
