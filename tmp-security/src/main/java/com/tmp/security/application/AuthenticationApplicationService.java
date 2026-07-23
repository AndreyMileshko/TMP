package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login / logout / session queries. Never logs or audits plaintext passwords.
 */
public class AuthenticationApplicationService {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionContext sessionContext;
    private final SecurityAuditRepository auditRepository;
    private final Clock clock;

    public AuthenticationApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            SessionContext sessionContext,
            SecurityAuditRepository auditRepository,
            Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public Session login(Login login, char[] password) {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(password, "password");
        try {
            Optional<User> found = userRepository.findByLoginIgnoreCase(login);
            if (found.isEmpty()
                    || !found.get().isActive()
                    || !passwordHasher.matches(password, found.get().passwordHash())) {
                auditRepository.append(SecurityAuditEvent.record(
                        AuditEventId.generate(),
                        clock.instant(),
                        found.map(User::id).orElse(null),
                        login.value(),
                        AuditOperation.LOGIN_FAILURE,
                        "USER",
                        found.map(u -> u.id().value().toString()).orElse(null),
                        "Login failed",
                        AuditResult.FAILURE));
                throw new AuthenticationFailedException();
            }
            User user = found.get();
            Session session = Session.of(
                    SessionId.generate(), user.id(), user.login(), clock.instant());
            sessionContext.open(session);
            auditRepository.append(SecurityAuditEvent.record(
                    AuditEventId.generate(),
                    clock.instant(),
                    user.id(),
                    user.login().value(),
                    AuditOperation.LOGIN_SUCCESS,
                    "USER",
                    user.id().value().toString(),
                    "Login succeeded",
                    AuditResult.SUCCESS));
            return session;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Transactional
    public void logout() {
        Optional<Session> current = sessionContext.current();
        if (current.isEmpty()) {
            return;
        }
        Session session = current.get();
        auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                session.userId(),
                session.login().value(),
                AuditOperation.LOGOUT,
                "USER",
                session.userId().value().toString(),
                "Logout",
                AuditResult.SUCCESS));
        sessionContext.close();
    }

    public Optional<Session> currentSession() {
        return sessionContext.current();
    }

    public boolean isAuthenticated() {
        return sessionContext.isAuthenticated();
    }
}
