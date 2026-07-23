package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.User;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class AuthenticationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String GENERIC = AuthenticationFailedException.GENERIC_MESSAGE;

    private InMemoryUsers users;
    private SessionContext sessions;
    private InMemoryAudit audit;
    private RecordingHasher hasher;
    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        sessions = new SessionContext();
        audit = new InMemoryAudit();
        hasher = new RecordingHasher();
        service = new AuthenticationApplicationService(
                users, hasher, sessions, audit, CLOCK, immediateTransactions());
    }

    @Test
    void successfulLogin() {
        users.save(active("admin", "secret"));
        service.login(Login.of("admin"), "secret".toCharArray());
        assertTrue(service.isAuthenticated());
        assertEquals(AuditOperation.LOGIN_SUCCESS, audit.events.getFirst().operation());
    }

    @Test
    void unknownWrongAndDeletedShareMessage() {
        users.save(active("admin", "secret"));
        User deleted = users.save(active("gone", "secret").deleted(CLOCK));

        AuthenticationFailedException unknown = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(Login.of("missing"), "x".toCharArray()));
        AuthenticationFailedException wrong = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(Login.of("admin"), "bad".toCharArray()));
        AuthenticationFailedException deletedLogin = assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(deleted.login(), "secret".toCharArray()));

        assertEquals(GENERIC, unknown.getMessage());
        assertEquals(GENERIC, wrong.getMessage());
        assertEquals(GENERIC, deletedLogin.getMessage());
        assertEquals(3, audit.events.stream()
                .filter(e -> e.operation() == AuditOperation.LOGIN_FAILURE)
                .count());
        assertFalse(service.isAuthenticated());
    }

    @Test
    void unknownLoginAlwaysInvokesPasswordHasherMatches() {
        hasher.matchesCalls.set(0);
        assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(Login.of("missing"), "whatever".toCharArray()));
        assertEquals(1, hasher.matchesCalls.get());
        assertEquals(
                AuthenticationApplicationService.UNKNOWN_USER_DUMMY_HASH,
                hasher.lastHash);
    }

    @Test
    void auditFailureOnSuccessPathLeavesNoSession() {
        users.save(active("admin", "secret"));
        audit.failOnAppend = true;
        assertThrows(IllegalStateException.class, () -> service.login(Login.of("admin"), "secret".toCharArray()));
        assertFalse(service.isAuthenticated());
    }

    @Test
    void logoutWithAndWithoutSession() {
        service.logout();
        assertTrue(audit.events.isEmpty());
        users.save(active("admin", "secret"));
        service.login(Login.of("admin"), "secret".toCharArray());
        service.logout();
        assertFalse(service.isAuthenticated());
        assertTrue(audit.events.stream().anyMatch(e -> e.operation() == AuditOperation.LOGOUT));
    }

    @Test
    void logoutClearsSessionEvenWhenAuditFails() {
        users.save(active("admin", "secret"));
        service.login(Login.of("admin"), "secret".toCharArray());
        assertTrue(service.isAuthenticated());
        audit.failOnAppend = true;
        assertThrows(IllegalStateException.class, service::logout);
        assertFalse(service.isAuthenticated());
    }

    @Test
    void failedLoginWithPreExistingSessionLeavesNoSession() {
        users.save(active("admin", "secret"));
        users.save(active("other", "other-secret"));
        service.login(Login.of("admin"), "secret".toCharArray());
        assertTrue(service.isAuthenticated());

        assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(Login.of("admin"), "bad".toCharArray()));
        assertFalse(service.isAuthenticated());

        service.login(Login.of("admin"), "secret".toCharArray());
        assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(Login.of("missing"), "x".toCharArray()));
        assertFalse(service.isAuthenticated());

        User deleted = users.save(active("gone", "secret").deleted(CLOCK));
        service.login(Login.of("admin"), "secret".toCharArray());
        assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(deleted.login(), "secret".toCharArray()));
        assertFalse(service.isAuthenticated());
    }

    @Test
    void successfulLoginReplacesPreExistingSession() {
        users.save(active("admin", "secret"));
        users.save(active("other", "other-secret"));
        service.login(Login.of("admin"), "secret".toCharArray());
        service.login(Login.of("other"), "other-secret".toCharArray());
        assertTrue(service.isAuthenticated());
        assertEquals("other", service.currentSession().orElseThrow().login().value());
        assertTrue(audit.events.stream().anyMatch(e -> e.operation() == AuditOperation.LOGIN_SUCCESS));
        assertTrue(audit.events.stream()
                .noneMatch(e -> e.safeDescription().toLowerCase().contains("secret")
                        || e.safeDescription().toLowerCase().contains("$2a$")));
    }

    @Test
    void deletedUserAfterCredentialCheckDoesNotOpenSession() {
        User admin = users.save(active("admin", "secret"));
        users.deleteOnFindById = admin.id();
        assertThrows(
                AuthenticationFailedException.class,
                () -> service.login(Login.of("admin"), "secret".toCharArray()));
        assertFalse(service.isAuthenticated());
    }

    private User active(String login, String password) {
        return User.createActive(
                UserId.generate(),
                Login.of(login),
                DisplayName.of(login),
                PasswordHash.of(password),
                CLOCK);
    }

    private static TransactionOperations immediateTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }

    private static final class RecordingHasher implements PasswordHasher {
        private final AtomicInteger matchesCalls = new AtomicInteger();
        private PasswordHash lastHash;

        @Override
        public PasswordHash hash(char[] plaintextPassword) {
            return PasswordHash.of(new String(plaintextPassword));
        }

        @Override
        public boolean matches(char[] plaintextPassword, PasswordHash hash) {
            matchesCalls.incrementAndGet();
            lastHash = hash;
            return hash.encodedValue().equals(new String(plaintextPassword));
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<UserId, User> store = new HashMap<>();
        private UserId deleteOnFindById;

        @Override
        public User save(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
            if (deleteOnFindById != null && deleteOnFindById.equals(id)) {
                User current = store.get(id);
                if (current != null && current.isActive()) {
                    User deleted = current.deleted(CLOCK);
                    store.put(id, deleted);
                    return Optional.of(deleted);
                }
            }
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByLoginIgnoreCase(Login login) {
            return store.values().stream()
                    .filter(u -> u.login().value().equalsIgnoreCase(login.value()))
                    .findFirst();
        }

        @Override
        public boolean existsByLoginIgnoreCase(Login login) {
            return findByLoginIgnoreCase(login).isPresent();
        }

        @Override
        public boolean existsAny() {
            return !store.isEmpty();
        }

        @Override
        public java.util.List<User> findPage(
                int pageIndex, int pageSize, com.tmp.security.domain.UserStatus statusFilter) {
            return store.values().stream()
                    .filter(u -> statusFilter == null || u.status() == statusFilter)
                    .skip((long) pageIndex * pageSize)
                    .limit(pageSize)
                    .toList();
        }
    }

    private static final class InMemoryAudit implements SecurityAuditRepository {
        private final List<SecurityAuditEvent> events = new ArrayList<>();
        private boolean failOnAppend;

        @Override
        public void append(SecurityAuditEvent event) {
            if (failOnAppend) {
                throw new IllegalStateException("audit write failed");
            }
            events.add(event);
        }

        @Override
        public List<SecurityAuditEvent> findPage(AuditQueryFilter filter, int pageIndex, int pageSize) {
            return List.of();
        }

        @Override
        public long count(AuditQueryFilter filter) {
            return events.size();
        }
    }
}
