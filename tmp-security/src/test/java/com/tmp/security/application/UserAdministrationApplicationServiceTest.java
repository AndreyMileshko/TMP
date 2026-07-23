package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.tmp.security.domain.DuplicateLoginException;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PasswordHasher;
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

class UserAdministrationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryUsers users;
    private InMemoryAudit audit;
    private SessionContext sessions;
    private UserAdministrationApplicationService service;
    private AuthorizationApplicationService authorization;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        audit = new InMemoryAudit();
        sessions = new SessionContext();
        UserId actor = UserId.generate();
        sessions.open(Session.of(SessionId.generate(), actor, Login.of("actor"), CLOCK.instant()));
        authorization = new AuthorizationApplicationService(
                sessions,
                alwaysActiveUsers(),
                allPermissionsEngine(),
                emptyAssignments(),
                emptyRoles(),
                grantAllOverrides(actor));
        service = new UserAdministrationApplicationService(
                users, new FakeHasher(), authorization, audit, sessions, CLOCK);
    }

    @Test
    void createUpdateDeleteAndList() {
        User created = service.createUser(Login.of("new"), DisplayName.of("New"), "pwd".toCharArray());
        assertEquals(1, audit.events.stream().filter(e -> e.operation() == AuditOperation.USER_CREATED).count());
        service.updateUser(created.id(), DisplayName.of("Renamed"));
        User deleted = service.deleteUser(created.id());
        assertEquals(UserStatus.DELETED, deleted.status());
        assertTrue(users.findById(deleted.id()).isPresent());
        assertEquals(1, service.listUsers(0, 10, UserStatus.DELETED).size());
    }

    @Test
    void deniedWithoutPermission() {
        sessions.close();
        assertThrows(AccessDeniedException.class,
                () -> service.createUser(Login.of("x"), DisplayName.of("X"), "p".toCharArray()));
        assertTrue(users.store.isEmpty());
    }

    @Test
    void duplicateLoginSurfaces() {
        service.createUser(Login.of("dup"), DisplayName.of("A"), "p".toCharArray());
        assertThrows(DuplicateLoginException.class,
                () -> service.createUser(Login.of("DUP"), DisplayName.of("B"), "p".toCharArray()));
    }

    private static final class FakeHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] plaintextPassword) {
            return PasswordHash.of("$2a$10$x");
        }

        @Override
        public boolean matches(char[] plaintextPassword, PasswordHash hash) {
            return true;
        }
    }

    private static CapabilityEngine allPermissionsEngine() {
        Set<PermissionId> all = Set.of(
                SecurityPermissions.USERS_VIEW,
                SecurityPermissions.USERS_CREATE,
                SecurityPermissions.USERS_UPDATE,
                SecurityPermissions.USERS_DELETE);
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
                return all.stream()
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

    private static PermissionOverrideRepository grantAllOverrides(UserId actor) {
        return new PermissionOverrideRepository() {
            @Override
            public IndividualPermissionOverride save(IndividualPermissionOverride override) {
                return override;
            }

            @Override
            public void remove(UserId userId, PermissionId permissionId) {
            }

            @Override
            public List<IndividualPermissionOverride> findByUser(UserId userId) {
                if (!userId.equals(actor)) {
                    return List.of();
                }
                return List.of(
                        IndividualPermissionOverride.of(
                                actor, SecurityPermissions.USERS_VIEW,
                                com.tmp.security.domain.PermissionOverrideDecision.GRANT, CLOCK),
                        IndividualPermissionOverride.of(
                                actor, SecurityPermissions.USERS_CREATE,
                                com.tmp.security.domain.PermissionOverrideDecision.GRANT, CLOCK),
                        IndividualPermissionOverride.of(
                                actor, SecurityPermissions.USERS_UPDATE,
                                com.tmp.security.domain.PermissionOverrideDecision.GRANT, CLOCK),
                        IndividualPermissionOverride.of(
                                actor, SecurityPermissions.USERS_DELETE,
                                com.tmp.security.domain.PermissionOverrideDecision.GRANT, CLOCK));
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
            for (User existing : store.values()) {
                if (existing.login().value().equalsIgnoreCase(user.login().value())
                        && !existing.id().equals(user.id())) {
                    throw new DuplicateLoginException("dup");
                }
            }
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
    private static AlwaysActiveUserRepository alwaysActiveUsers() {
        return new AlwaysActiveUserRepository();
    }
}
