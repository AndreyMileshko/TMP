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
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PermissionOverrideDecision;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.RoleAssignment;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserNotActiveException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleAssignmentApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryUsers users;
    private InMemoryRoles roles;
    private InMemoryAssignments assignments;
    private InMemoryAudit audit;
    private SessionContext sessions;
    private RoleAssignmentApplicationService service;
    private UserId actor;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        roles = new InMemoryRoles();
        assignments = new InMemoryAssignments();
        audit = new InMemoryAudit();
        sessions = new SessionContext();
        actor = UserId.generate();
        sessions.open(Session.of(SessionId.generate(), actor, Login.of("admin"), CLOCK.instant()));
        Set<PermissionId> perms = Set.of(SecurityPermissions.ROLES_ASSIGN);
        service = new RoleAssignmentApplicationService(
                users,
                roles,
                assignments,
                new AuthorizationApplicationService(
                sessions,
                alwaysActiveUsers(),
                engine(perms), emptyAssignments(), emptyRoles(), grant(actor, perms)),
                audit,
                sessions,
                CLOCK);
    }

    @Test
    void assignAndRevoke() {
        UserId target = users.save(User.createActive(
                        UserId.generate(), Login.of("u"), DisplayName.of("U"), PasswordHash.of("h"), CLOCK))
                .id();
        RoleId roleId = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK)).id();
        service.assignRole(target, roleId);
        assertEquals(1, assignments.countUsersForRole(roleId));
        assertEquals(AuditOperation.ROLE_ASSIGNED, audit.events.getFirst().operation());
        service.revokeRole(target, roleId);
        assertEquals(0, assignments.countUsersForRole(roleId));
    }

    @Test
    void assignToDeletedUserRejected() {
        User deleted = users.save(User.createActive(
                        UserId.generate(), Login.of("d"), DisplayName.of("D"), PasswordHash.of("h"), CLOCK)
                .deleted(CLOCK));
        RoleId roleId = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK)).id();
        assertThrows(UserNotActiveException.class, () -> service.assignRole(deleted.id(), roleId));
        assertEquals(0, assignments.countUsersForRole(roleId));
    }

    @Test
    void deniedWithoutPermission() {
        sessions.close();
        assertThrows(
                AccessDeniedException.class,
                () -> service.assignRole(UserId.generate(), RoleId.generate()));
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

    private static PermissionOverrideRepository grant(UserId userId, Set<PermissionId> perms) {
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
                return perms.stream()
                        .map(p -> IndividualPermissionOverride.of(
                                userId, p, PermissionOverrideDecision.GRANT, CLOCK))
                        .toList();
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
            public void assign(RoleAssignment assignment) {
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

    private static final class InMemoryRoles implements RoleRepository {
        private final Map<RoleId, Role> store = new HashMap<>();

        @Override
        public Role save(Role role) {
            store.put(role.id(), role);
            return role;
        }

        @Override
        public Optional<Role> findById(RoleId id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Role> findAll() {
            return List.copyOf(store.values());
        }

        @Override
        public void deleteById(RoleId id) {
            store.remove(id);
        }
    }

    private static final class InMemoryAssignments implements RoleAssignmentRepository {
        private final List<RoleAssignment> store = new ArrayList<>();

        @Override
        public void assign(RoleAssignment assignment) {
            store.add(assignment);
        }

        @Override
        public void revoke(UserId userId, RoleId roleId) {
            store.removeIf(a -> a.userId().equals(userId) && a.roleId().equals(roleId));
        }

        @Override
        public Set<RoleId> findRoleIdsForUser(UserId userId) {
            Set<RoleId> ids = new HashSet<>();
            store.stream().filter(a -> a.userId().equals(userId)).forEach(a -> ids.add(a.roleId()));
            return ids;
        }

        @Override
        public List<UserId> findUserIdsForRole(RoleId roleId) {
            return store.stream().filter(a -> a.roleId().equals(roleId)).map(RoleAssignment::userId).toList();
        }

        @Override
        public long countUsersForRole(RoleId roleId) {
            return store.stream().filter(a -> a.roleId().equals(roleId)).count();
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
