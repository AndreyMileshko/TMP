package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.api.AuthenticationFailedException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final String GENERIC = AuthenticationFailedException.GENERIC_MESSAGE;

    private InMemoryUsers users;
    private SessionContext sessions;
    private InMemoryAudit audit;
    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        sessions = new SessionContext();
        audit = new InMemoryAudit();
        service = new AuthenticationApplicationService(
                users, new ExactHasher(), sessions, audit, CLOCK);
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
    void logoutWithAndWithoutSession() {
        service.logout();
        assertTrue(audit.events.isEmpty());
        users.save(active("admin", "secret"));
        service.login(Login.of("admin"), "secret".toCharArray());
        service.logout();
        assertFalse(service.isAuthenticated());
        assertTrue(audit.events.stream().anyMatch(e -> e.operation() == AuditOperation.LOGOUT));
    }

    private User active(String login, String password) {
        return User.createActive(
                UserId.generate(),
                Login.of(login),
                DisplayName.of(login),
                PasswordHash.of(password),
                CLOCK);
    }

    private static final class ExactHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] plaintextPassword) {
            return PasswordHash.of(new String(plaintextPassword));
        }

        @Override
        public boolean matches(char[] plaintextPassword, PasswordHash hash) {
            return hash.encodedValue().equals(new String(plaintextPassword));
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<UserId, User> store = new HashMap<>();

        @Override
        public User save(User user) {
            store.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<User> findById(UserId id) {
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
        public java.util.List<User> findPage(int pageIndex, int pageSize, com.tmp.security.domain.UserStatus statusFilter) {
            return store.values().stream()
                    .filter(u -> statusFilter == null || u.status() == statusFilter)
                    .skip((long) pageIndex * pageSize)
                    .limit(pageSize)
                    .toList();
        }
    }

    private static final class InMemoryAudit implements SecurityAuditRepository {
        private final List<SecurityAuditEvent> events = new ArrayList<>();

        @Override
        public void append(SecurityAuditEvent event) {
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
