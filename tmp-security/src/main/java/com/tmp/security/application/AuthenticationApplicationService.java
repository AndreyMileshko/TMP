package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.PasswordHash;
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
import org.springframework.transaction.support.TransactionOperations;

/**
 * Login / logout / session queries. Never logs or audits plaintext passwords.
 *
 * <p>DB work and in-memory session open are deliberately separated: session is opened only
 * after a successful audit transaction commits. Failed-login audits run in their own
 * completed transaction so they are not rolled back by {@link AuthenticationFailedException}.
 *
 * <p>Desktop session policy:
 * <ul>
 *   <li>every {@link #login} attempt closes any prior session first;</li>
 *   <li>failed login leaves no session (neither prior nor new);</li>
 *   <li>{@link #logout} always clears the session, even when logout audit persistence fails;</li>
 *   <li>{@link com.tmp.security.domain.UserStatus} is re-checked immediately before session open
 *       so a concurrently deleted user never receives a usable session.</li>
 * </ul>
 */
public class AuthenticationApplicationService {

    /**
     * Constant technical BCrypt hash used when the login is unknown/deleted so verification
     * cost matches the known-user path (timing side-channel mitigation). Not a real user password.
     */
    static final PasswordHash UNKNOWN_USER_DUMMY_HASH = PasswordHash.of(
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy");

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final SessionContext sessionContext;
    private final SecurityAuditRepository auditRepository;
    private final Clock clock;
    private final TransactionOperations authenticationTransactions;

    public AuthenticationApplicationService(
            UserRepository userRepository,
            PasswordHasher passwordHasher,
            SessionContext sessionContext,
            SecurityAuditRepository auditRepository,
            Clock clock,
            TransactionOperations authenticationTransactions) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.sessionContext = Objects.requireNonNull(sessionContext, "sessionContext");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.authenticationTransactions =
                Objects.requireNonNull(authenticationTransactions, "authenticationTransactions");
    }

    public Session login(Login login, char[] password) {
        Objects.requireNonNull(login, "login");
        Objects.requireNonNull(password, "password");
        try {
            // Desktop contract: any new login attempt closes the previous session first so a
            // failed attempt never leaves the prior user authenticated.
            sessionContext.close();
            Optional<User> found = userRepository.findByLoginIgnoreCase(login);
            boolean credentialsAccepted = verifyCredentials(found, password);
            if (!credentialsAccepted) {
                recordLoginFailure(login, found);
                throw new AuthenticationFailedException();
            }
            User user = found.orElseThrow();
            User activeUser = requireStillActive(login, user);
            recordLoginSuccess(activeUser);
            // Re-check immediately before open so a concurrent logical delete cannot leave a
            // usable session for a DELETED user (LOGIN_SUCCESS may already be committed).
            User beforeOpen = requireStillActive(login, activeUser);
            Session session = Session.of(
                    SessionId.generate(), beforeOpen.id(), beforeOpen.login(), clock.instant());
            sessionContext.open(session);
            return session;
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public void logout() {
        Optional<Session> current = sessionContext.current();
        if (current.isEmpty()) {
            return;
        }
        Session session = current.get();
        try {
            authenticationTransactions.executeWithoutResult(status -> auditRepository.append(SecurityAuditEvent.record(
                    AuditEventId.generate(),
                    clock.instant(),
                    session.userId(),
                    session.login().value(),
                    AuditOperation.LOGOUT,
                    "USER",
                    session.userId().value().toString(),
                    "Logout",
                    AuditResult.SUCCESS)));
        } finally {
            // Session must clear even when logout audit persistence fails; audit failure still
            // propagates to the caller.
            sessionContext.close();
        }
    }

    public Optional<Session> currentSession() {
        return sessionContext.current();
    }

    public boolean isAuthenticated() {
        return sessionContext.isAuthenticated();
    }

    private User requireStillActive(Login login, User previouslyAccepted) {
        Optional<User> current = userRepository.findById(previouslyAccepted.id());
        if (current.isPresent() && current.get().isActive()) {
            return current.get();
        }
        recordLoginFailure(login, current.isPresent() ? current : Optional.of(previouslyAccepted));
        throw new AuthenticationFailedException();
    }

    private boolean verifyCredentials(Optional<User> found, char[] password) {
        if (found.isPresent() && found.get().isActive()) {
            return passwordHasher.matches(password, found.get().passwordHash());
        }
        passwordHasher.matches(password, UNKNOWN_USER_DUMMY_HASH);
        return false;
    }

    private void recordLoginFailure(Login login, Optional<User> found) {
        authenticationTransactions.executeWithoutResult(status -> auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                found.map(User::id).orElse(null),
                login.value(),
                AuditOperation.LOGIN_FAILURE,
                "USER",
                found.map(u -> u.id().value().toString()).orElse(null),
                "Login failed",
                AuditResult.FAILURE)));
    }

    private void recordLoginSuccess(User user) {
        authenticationTransactions.executeWithoutResult(status -> auditRepository.append(SecurityAuditEvent.record(
                AuditEventId.generate(),
                clock.instant(),
                user.id(),
                user.login().value(),
                AuditOperation.LOGIN_SUCCESS,
                "USER",
                user.id().value().toString(),
                "Login succeeded",
                AuditResult.SUCCESS)));
    }
}
