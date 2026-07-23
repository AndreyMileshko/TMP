package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditQueryFilter;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.api.InvalidCurrentPasswordException;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.PermissionOverrideDecision;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PasswordApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryUsers users;
    private InMemoryAudit audit;
    private SessionContext sessions;
    private PasswordApplicationService service;
    private UserId selfId;
    private String storedHash = "old-hash";

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        audit = new InMemoryAudit();
        sessions = new SessionContext();
        selfId = UserId.generate();
        users.save(User.createActive(
                selfId, Login.of("self"), DisplayName.of("Self"), PasswordHash.of(storedHash), CLOCK));
        sessions.open(Session.of(SessionId.generate(), selfId, Login.of("self"), CLOCK.instant()));
        AuthorizationApplicationService authorization = new AuthorizationApplicationService(
                sessions,
                engine(Set.of(SecurityPermissions.USERS_RESET_PASSWORD)),
                emptyAssignments(),
                emptyRoles(),
                grant(selfId, SecurityPermissions.USERS_RESET_PASSWORD));
        service = new PasswordApplicationService(
                users, new ExactHasher(), authorization, audit, sessions, CLOCK);
    }

    @Test
    void changeOwnPasswordWrongCurrentRejected() {
        assertThrows(
                InvalidCurrentPasswordException.class,
                () -> service.changeOwnPassword("wrong".toCharArray(), "new1".toCharArray()));
        assertEquals(storedHash, users.findById(selfId).orElseThrow().passwordHash().encodedValue());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void changeOwnPasswordSuccess() {
        String newPwd = "brand-new-secret";
        service.changeOwnPassword("old-hash".toCharArray(), newPwd.toCharArray());
        assertEquals(newPwd, users.findById(selfId).orElseThrow().passwordHash().encodedValue());
        assertEquals(AuditOperation.PASSWORD_CHANGED, audit.events.getFirst().operation());
        assertFalse(audit.events.getFirst().safeDescription().contains(newPwd));
        assertFalse(audit.events.getFirst().safeDescription().contains("old-hash"));
    }

    @Test
    void resetWithoutPermissionDenied() {
        sessions.close();
        UserId other = users.save(User.createActive(
                        UserId.generate(),
                        Login.of("other"),
                        DisplayName.of("O"),
                        PasswordHash.of("x"),
                        CLOCK))
                .id();
        assertThrows(
                AccessDeniedException.class,
                () -> service.resetPassword(other, "new".toCharArray()));
    }

    @Test
    void resetWithPermissionWithoutKnowingOldPassword() {
        UserId other = users.save(User.createActive(
                        UserId.generate(),
                        Login.of("other"),
                        DisplayName.of("O"),
                        PasswordHash.of("unknown-old"),
                        CLOCK))
                .id();
        String replacement = "admin-set-password";
        service.resetPassword(other, replacement.toCharArray());
        assertEquals(replacement, users.findById(other).orElseThrow().passwordHash().encodedValue());
        assertEquals(AuditOperation.PASSWORD_RESET, audit.events.getFirst().operation());
        assertFalse(audit.events.getFirst().safeDescription().contains(replacement));
        assertFalse(audit.events.getFirst().safeDescription().contains("unknown-old"));
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

    private static CapabilityEngine engine(Set<PermissionId> active) {
        return new CapabilityEngine() {
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
            public Optional<CapabilityDescriptor> findById(CapabilityId id) {
                return Optional.empty();
            }

            @Override
            public List<CapabilityDescriptor> registeredCapabilities() {
                return List.of();
            }

            @Override
            public CapabilityLifecycleState stateOf(CapabilityId id) {
                return CapabilityLifecycleState.ACTIVE;
            }

            @Override
            public List<PermissionDescriptor> activePermissions() {
                return active.stream()
                        .map(id -> PermissionDescriptor.of(id.value(), id.value(), ""))
                        .toList();
            }

            @Override
            public List<CommandDescriptor> activeCommands() {
                return List.of();
            }

            @Override
            public List<ViewDescriptor> activeViews() {
                return List.of();
            }

            @Override
            public List<NavigationContribution> activeNavigation() {
                return List.of();
            }

            @Override
            public CapabilityEngineStatus status() {
                return new CapabilityEngineStatus(0, 0, 0, 0);
            }
        };
    }

    private static PermissionOverrideRepository grant(UserId userId, PermissionId permissionId) {
        return new PermissionOverrideRepository() {
            @Override
            public IndividualPermissionOverride save(IndividualPermissionOverride override) {
                return override;
            }

            @Override
            public void remove(UserId userId, PermissionId permissionId) {
            }

            @Override
            public List<IndividualPermissionOverride> findByUser(UserId id) {
                if (!id.equals(userId)) {
                    return List.of();
                }
                return List.of(IndividualPermissionOverride.of(
                        userId, permissionId, PermissionOverrideDecision.GRANT, CLOCK));
            }

            @Override
            public Optional<IndividualPermissionOverride> findByUserAndPermission(
                    UserId userId, PermissionId permissionId) {
                return Optional.empty();
            }
        };
    }

    private static RoleAssignmentRepository emptyAssignments() {
        return new RoleAssignmentRepository() {
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
            public List<UserId> findUserIdsForRole(RoleId roleId) {
                return List.of();
            }

            @Override
            public long countUsersForRole(RoleId roleId) {
                return 0;
            }
        };
    }

    private static RoleRepository emptyRoles() {
        return new RoleRepository() {
            @Override
            public Role save(Role role) {
                return role;
            }

            @Override
            public Optional<Role> findById(RoleId id) {
                return Optional.empty();
            }

            @Override
            public List<Role> findAll() {
                return List.of();
            }

            @Override
            public void deleteById(RoleId id) {
            }
        };
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
            return Optional.empty();
        }

        @Override
        public boolean existsByLoginIgnoreCase(Login login) {
            return false;
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
