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
import com.tmp.security.api.LastAdministratorException;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleAlreadyAssignedException;
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
import java.util.Comparator;
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
    private InMemoryOverrides overrides;
    private InMemoryAudit audit;
    private SessionContext sessions;
    private RoleAssignmentApplicationService service;
    private UserId actor;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        roles = new InMemoryRoles();
        assignments = new InMemoryAssignments();
        overrides = new InMemoryOverrides();
        audit = new InMemoryAudit();
        sessions = new SessionContext();
        actor = UserId.generate();
        users.save(User.createActive(
                actor, Login.of("admin"), DisplayName.of("Admin"), PasswordHash.of("h"), CLOCK));
        sessions.open(Session.of(SessionId.generate(), actor, Login.of("admin"), CLOCK.instant()));
        Set<PermissionId> perms = Set.of(SecurityPermissions.ROLES_ASSIGN);
        PermissionOverrideRepository authOverrides = grant(actor, perms);
        service = new RoleAssignmentApplicationService(
                users,
                roles,
                assignments,
                overrides,
                engine(perms),
                new AuthorizationApplicationService(
                        sessions,
                        alwaysActiveUsers(),
                        engine(perms),
                        emptyAssignments(),
                        emptyRoles(),
                        authOverrides),
                audit,
                sessions,
                CLOCK,
                LastAdministratorGuardFixtures.guard(users, roles, assignments, overrides));
    }

    @Test
    void assignAndRevokeOrdinaryRole() {
        UserId target = users.save(User.createActive(
                        UserId.generate(), Login.of("u"), DisplayName.of("U"), PasswordHash.of("h"), CLOCK))
                .id();
        RoleId roleId = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK)).id();
        service.assignRole(target, roleId);
        assertEquals(1, assignments.countUsersForRole(roleId));
        assertEquals(AuditOperation.ROLE_ASSIGNED, audit.events.getFirst().operation());
        service.revokeRole(target, roleId);
        assertEquals(0, assignments.countUsersForRole(roleId));
        assertEquals(AuditOperation.ROLE_REVOKED, audit.events.get(1).operation());
    }

    @Test
    void lastActiveAdministratorCannotRevokeOwnAdministratorAssignment() {
        RoleId adminRole = saveAdminRole();
        assignments.assign(RoleAssignment.of(actor, adminRole, CLOCK.instant()));
        int auditBefore = audit.events.size();

        LastAdministratorException ex =
                assertThrows(LastAdministratorException.class, () -> service.revokeRole(actor, adminRole));
        assertEquals(LastAdministratorGuard.ROLE_REVOKE_MESSAGE, ex.getMessage());
        assertEquals(Set.of(adminRole), assignments.findRoleIdsForUser(actor));
        assertEquals(auditBefore, audit.events.size());
    }

    @Test
    void anotherUserCannotRevokeLastAdministratorCriticalRole() {
        RoleId adminRole = saveAdminRole();
        assignments.assign(RoleAssignment.of(actor, adminRole, CLOCK.instant()));
        UserId operator = users.save(User.createActive(
                        UserId.generate(), Login.of("op"), DisplayName.of("Op"), PasswordHash.of("h"), CLOCK))
                .id();
        int auditBefore = audit.events.size();

        LastAdministratorException ex =
                assertThrows(LastAdministratorException.class, () -> service.revokeRole(actor, adminRole));
        assertEquals(LastAdministratorGuard.ROLE_REVOKE_MESSAGE, ex.getMessage());
        assertEquals(Set.of(adminRole), assignments.findRoleIdsForUser(actor));
        assertTrue(assignments.findRoleIdsForUser(operator).isEmpty());
        assertEquals(auditBefore, audit.events.size());
    }

    @Test
    void revokeAllowedWhenAnotherEligibleActiveAdministratorExists() {
        RoleId adminRole = saveAdminRole();
        UserId secondAdmin = users.save(User.createActive(
                        UserId.generate(),
                        Login.of("admin2"),
                        DisplayName.of("Admin2"),
                        PasswordHash.of("h"),
                        CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(actor, adminRole, CLOCK.instant()));
        assignments.assign(RoleAssignment.of(secondAdmin, adminRole, CLOCK.instant()));

        service.revokeRole(actor, adminRole);

        assertTrue(assignments.findRoleIdsForUser(actor).isEmpty());
        assertEquals(Set.of(adminRole), assignments.findRoleIdsForUser(secondAdmin));
        assertEquals(AuditOperation.ROLE_REVOKED, audit.events.getLast().operation());
    }

    @Test
    void inactiveAdministratorsDoNotSatisfySafeguard() {
        RoleId adminRole = saveAdminRole();
        UserId inactiveAdmin = users.save(User.createActive(
                        UserId.generate(),
                        Login.of("gone"),
                        DisplayName.of("Gone"),
                        PasswordHash.of("h"),
                        CLOCK)
                .deleted(CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(actor, adminRole, CLOCK.instant()));
        assignments.assign(RoleAssignment.of(inactiveAdmin, adminRole, CLOCK.instant()));

        LastAdministratorException ex =
                assertThrows(LastAdministratorException.class, () -> service.revokeRole(actor, adminRole));
        assertEquals(LastAdministratorGuard.ROLE_REVOKE_MESSAGE, ex.getMessage());
        assertEquals(Set.of(adminRole), assignments.findRoleIdsForUser(actor));
        assertEquals(Set.of(adminRole), assignments.findRoleIdsForUser(inactiveAdmin));
    }

    @Test
    void rejectedChangeLeavesAllAssignmentsIntact() {
        RoleId adminRole = saveAdminRole();
        RoleId otherRole = roles.save(Role.create(RoleId.generate(), "Other", "", CLOCK)).id();
        UserId operator = users.save(User.createActive(
                        UserId.generate(), Login.of("op2"), DisplayName.of("Op2"), PasswordHash.of("h"), CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(actor, adminRole, CLOCK.instant()));
        assignments.assign(RoleAssignment.of(operator, otherRole, CLOCK.instant()));

        assertThrows(LastAdministratorException.class, () -> service.revokeRole(actor, adminRole));

        assertEquals(Set.of(adminRole), assignments.findRoleIdsForUser(actor));
        assertEquals(Set.of(otherRole), assignments.findRoleIdsForUser(operator));
        assertEquals(1, assignments.countUsersForRole(adminRole));
        assertEquals(1, assignments.countUsersForRole(otherRole));
    }

    @Test
    void listEffectivePermissionsIncludesRoleUnionAndHonorsRevokeOverride() {
        UserId target = users.save(User.createActive(
                        UserId.generate(), Login.of("eff"), DisplayName.of("Eff"), PasswordHash.of("h"), CLOCK))
                .id();
        PermissionId view = PermissionId.of("warehouse.stock.view");
        PermissionId move = PermissionId.of("warehouse.move.create");
        Role role = Role.create(RoleId.generate(), "Store", "", CLOCK)
                .grantPermission(view, CLOCK)
                .grantPermission(move, CLOCK);
        roles.save(role);
        service.assignRole(target, role.id());

        Set<PermissionId> active = Set.of(SecurityPermissions.ROLES_ASSIGN, view, move);
        InMemoryOverrides overrideStore = new InMemoryOverrides();
        overrideStore.save(IndividualPermissionOverride.of(
                target, move, PermissionOverrideDecision.REVOKE, CLOCK));
        RoleAssignmentApplicationService queryService = new RoleAssignmentApplicationService(
                users,
                roles,
                assignments,
                overrideStore,
                engine(active),
                new AuthorizationApplicationService(
                        sessions,
                        alwaysActiveUsers(),
                        engine(active),
                        emptyAssignments(),
                        emptyRoles(),
                        grant(actor, Set.of(SecurityPermissions.ROLES_ASSIGN))),
                audit,
                sessions,
                CLOCK,
                LastAdministratorGuardFixtures.guard(users, roles, assignments, overrideStore));

        assertEquals(Set.of(view), queryService.listEffectivePermissionsForUser(target));
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
    void duplicateAssignmentIsRejectedWithoutSecondRow() {
        UserId target = users.save(User.createActive(
                        UserId.generate(), Login.of("dup"), DisplayName.of("Dup"), PasswordHash.of("h"), CLOCK))
                .id();
        RoleId roleId = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK)).id();
        service.assignRole(target, roleId);
        assertEquals(1, assignments.countUsersForRole(roleId));

        RoleAlreadyAssignedException ex = assertThrows(
                RoleAlreadyAssignedException.class, () -> service.assignRole(target, roleId));
        assertEquals("Роль уже назначена пользователю.", ex.getMessage());
        assertEquals(1, assignments.countUsersForRole(roleId));
        assertEquals(1, audit.events.size());
    }

    @Test
    void deniedWithoutPermission() {
        sessions.close();
        assertThrows(
                AccessDeniedException.class,
                () -> service.assignRole(UserId.generate(), RoleId.generate()));
        assertThrows(
                AccessDeniedException.class,
                () -> service.revokeRole(UserId.generate(), RoleId.generate()));
    }

    private RoleId saveAdminRole() {
        return roles.save(Role.create(RoleId.generate(), "Security Administrator", "", CLOCK)
                        .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK))
                .id();
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

    private static final class InMemoryOverrides implements PermissionOverrideRepository {
        private final List<IndividualPermissionOverride> store = new ArrayList<>();

        @Override
        public IndividualPermissionOverride save(IndividualPermissionOverride override) {
            store.removeIf(existing -> existing.userId().equals(override.userId())
                    && existing.permissionId().equals(override.permissionId()));
            store.add(override);
            return override;
        }

        @Override
        public void remove(UserId userId, PermissionId permissionId) {
            store.removeIf(existing -> existing.userId().equals(userId)
                    && existing.permissionId().equals(permissionId));
        }

        @Override
        public List<IndividualPermissionOverride> findByUser(UserId userId) {
            return store.stream().filter(o -> o.userId().equals(userId)).toList();
        }

        @Override
        public Optional<IndividualPermissionOverride> findByUserAndPermission(
                UserId userId, PermissionId permissionId) {
            return store.stream()
                    .filter(o -> o.userId().equals(userId) && o.permissionId().equals(permissionId))
                    .findFirst();
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
            return store.values().stream()
                    .filter(u -> statusFilter == null || u.status() == statusFilter)
                    .sorted(Comparator.comparing(u -> u.login().value(), String.CASE_INSENSITIVE_ORDER))
                    .skip((long) pageIndex * pageSize)
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public List<User> searchByLoginOrDisplayName(String query, int limit) {
            String normalized = query == null ? "" : query.trim().toLowerCase();
            if (normalized.isEmpty() || limit < 1) {
                return List.of();
            }
            return store.values().stream()
                    .filter(u -> u.status() == UserStatus.ACTIVE)
                    .filter(u -> u.login().value().toLowerCase().contains(normalized)
                            || u.displayName().value().toLowerCase().contains(normalized))
                    .sorted(Comparator.comparing(u -> u.login().value(), String.CASE_INSENSITIVE_ORDER))
                    .limit(limit)
                    .toList();
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
