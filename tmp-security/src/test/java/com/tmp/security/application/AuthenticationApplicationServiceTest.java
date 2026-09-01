package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PasswordSetupRequiredException;
import com.tmp.security.api.AuthenticationFailedException;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.api.InvalidActivationCodeException;
import com.tmp.security.domain.ActivationCodeGenerator;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.User;
import com.tmp.security.domain.Role;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.PermissionId;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.util.Set;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import com.tmp.security.support.ActivationTestSupport;
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
    private PasswordApplicationService passwordService;
    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        sessions = new SessionContext();
        audit = new InMemoryAudit();
        hasher = new RecordingHasher();
        passwordService = new PasswordApplicationService(
                users,
                hasher,
                allowAllAuthorization(),
                audit,
                sessions,
                new ActivationCodeGenerator(),
                ActivationTestSupport.defaultActivationProperties(),
                CLOCK);
        service = new AuthenticationApplicationService(
                users, hasher, passwordService, sessions, audit, CLOCK, immediateTransactions());
    }

    @Test
    void successfulLogin() {
        users.save(active("admin", "secret"));
        service.login(Login.of("admin"), "secret".toCharArray());
        assertTrue(service.isAuthenticated());
        assertEquals(AuditOperation.LOGIN_SUCCESS, audit.events.getFirst().operation());
    }

    @Test
    void passwordSetupRequiredRedirectsWithoutSession() {
        users.save(User.createActivePendingPasswordSetup(
                UserId.generate(), Login.of("newbie"), DisplayName.of("Newbie"), CLOCK));
        PasswordSetupRequiredException ex = assertThrows(
                PasswordSetupRequiredException.class,
                () -> service.login(Login.of("newbie"), "anything".toCharArray()));
        assertEquals("newbie", ex.login().value());
        assertFalse(service.isAuthenticated());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void completePasswordSetupOpensSessionWithActivationCode() {
        User pending = users.save(User.createActivePendingPasswordSetup(
                UserId.generate(), Login.of("newbie"), DisplayName.of("Newbie"), CLOCK));
        String code = passwordService.issueActivationCode(pending);
        service.completePasswordSetup(
                Login.of("newbie"), code, "long-enough".toCharArray(), "long-enough".toCharArray());
        assertTrue(service.isAuthenticated());
        assertFalse(users.findByLoginIgnoreCase(Login.of("newbie")).orElseThrow().passwordSetupRequired());
        assertTrue(audit.events.stream().anyMatch(e -> e.operation() == AuditOperation.PASSWORD_INITIALIZED));
        assertTrue(audit.events.stream().anyMatch(e -> e.operation() == AuditOperation.LOGIN_SUCCESS));
    }

    @Test
    void completePasswordSetupRejectsWithoutActivationCode() {
        users.save(User.createActivePendingPasswordSetup(
                UserId.generate(), Login.of("newbie"), DisplayName.of("Newbie"), CLOCK));
        assertThrows(
                InvalidActivationCodeException.class,
                () -> service.completePasswordSetup(
                        Login.of("newbie"),
                        "AAAA-BBBB-CCCC",
                        "long-enough".toCharArray(),
                        "long-enough".toCharArray()));
        assertFalse(service.isAuthenticated());
    }

    @Test
    void completePasswordSetupRejectsMismatch() {
        User pending = users.save(User.createActivePendingPasswordSetup(
                UserId.generate(), Login.of("newbie"), DisplayName.of("Newbie"), CLOCK));
        String code = passwordService.issueActivationCode(pending);
        assertThrows(
                com.tmp.security.api.PasswordConfirmationMismatchException.class,
                () -> service.completePasswordSetup(
                        Login.of("newbie"), code, "long-enough".toCharArray(), "different".toCharArray()));
        assertFalse(service.isAuthenticated());
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

    private static AuthorizationApplicationService allowAllAuthorization() {
        PermissionOverrideRepository overrides = new PermissionOverrideRepository() {
            @Override
            public com.tmp.security.domain.IndividualPermissionOverride save(
                    com.tmp.security.domain.IndividualPermissionOverride override) {
                return override;
            }

            @Override
            public void remove(UserId userId, PermissionId permissionId) {
            }

            @Override
            public java.util.List<com.tmp.security.domain.IndividualPermissionOverride> findByUser(UserId userId) {
                return java.util.List.of();
            }

            @Override
            public java.util.Optional<com.tmp.security.domain.IndividualPermissionOverride> findByUserAndPermission(
                    UserId userId, PermissionId permissionId) {
                return java.util.Optional.empty();
            }
        };
        CapabilityEngine engine = new CapabilityEngine() {
            @Override
            public void discoverAndRegisterAll() {
            }

            @Override
            public void activateAll() {
            }

            @Override
            public void deactivate(CapabilityId id) {
            }

            @Override
            public void stopAll() {
            }

            @Override
            public java.util.Optional<CapabilityDescriptor> findById(CapabilityId id) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<CapabilityDescriptor> registeredCapabilities() {
                return java.util.List.of();
            }

            @Override
            public CapabilityLifecycleState stateOf(CapabilityId id) {
                return CapabilityLifecycleState.ACTIVE;
            }

            @Override
            public java.util.List<PermissionDescriptor> activePermissions() {
                return java.util.List.of();
            }

            @Override
            public java.util.List<CommandDescriptor> activeCommands() {
                return java.util.List.of();
            }

            @Override
            public java.util.List<ViewDescriptor> activeViews() {
                return java.util.List.of();
            }

            @Override
            public java.util.List<NavigationContribution> activeNavigation() {
                return java.util.List.of();
            }

            @Override
            public CapabilityEngineStatus status() {
                return new CapabilityEngineStatus(0, 0, 0, 0);
            }
        };
        RoleAssignmentRepository assignments = new RoleAssignmentRepository() {
            @Override
            public void assign(com.tmp.security.domain.RoleAssignment assignment) {
            }

            @Override
            public void revoke(UserId userId, RoleId roleId) {
            }

            @Override
            public Set<RoleId> findRoleIdsForUser(UserId userId) {
                return Set.of();
            }

            @Override
            public java.util.List<UserId> findUserIdsForRole(RoleId roleId) {
                return java.util.List.of();
            }

            @Override
            public long countUsersForRole(RoleId roleId) {
                return 0;
            }
        };
        RoleRepository roles = new RoleRepository() {
            @Override
            public Role save(Role role) {
                return role;
            }

            @Override
            public java.util.Optional<Role> findById(RoleId id) {
                return java.util.Optional.empty();
            }

            @Override
            public java.util.List<Role> findAll() {
                return java.util.List.of();
            }

            @Override
            public void deleteById(RoleId id) {
            }
        };
        return new AuthorizationApplicationService(
                new SessionContext(), new AlwaysActiveUserRepository(), engine, assignments, roles, overrides);
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
