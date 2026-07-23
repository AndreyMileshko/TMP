package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultAuthenticationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void loginMapsToSessionSummary() {
        InMemoryUsers users = new InMemoryUsers();
        UserId id = UserId.generate();
        users.save(User.createActive(
                id, Login.of("admin"), DisplayName.of("A"), PasswordHash.of("secret"), CLOCK));
        SessionContext sessions = new SessionContext();
        AuthenticationApplicationService app = new AuthenticationApplicationService(
                users,
                new ExactHasher(),
                sessions,
                new InMemoryAudit(),
                CLOCK,
                new org.springframework.transaction.support.TransactionOperations() {
                    @Override
                    public <T> T execute(
                            org.springframework.transaction.support.TransactionCallback<T> action) {
                        return action.doInTransaction(
                                new org.springframework.transaction.support.SimpleTransactionStatus());
                    }
                });

        DefaultAuthenticationService facade = new DefaultAuthenticationService(app);
        SessionSummary summary = facade.login(Login.of("admin"), "secret".toCharArray());
        assertEquals(id, summary.userId());
        assertEquals("admin", summary.login().value());
        assertTrue(facade.isAuthenticated());
        facade.logout();
        assertTrue(facade.currentSession().isEmpty());
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
        private final Map<UserId, User> store = new ConcurrentHashMap<>();

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
        public List<User> findPage(int pageIndex, int pageSize, UserStatus statusFilter) {
            return List.of();
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
