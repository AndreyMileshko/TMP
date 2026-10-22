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
import com.tmp.security.api.RoleInUseException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleAdministrationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryUsers users;
    private InMemoryRoles roles;
    private InMemoryAssignments assignments;
    private InMemoryOverrides overrides;
    private InMemoryAudit audit;
    private SessionContext sessions;
    private RoleAdministrationApplicationService service;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        roles = new InMemoryRoles();
        assignments = new InMemoryAssignments();
        overrides = new InMemoryOverrides();
        audit = new InMemoryAudit();
        sessions = new SessionContext();
        UserId actor = UserId.generate();
        sessions.open(Session.of(SessionId.generate(), actor, Login.of("admin"), CLOCK.instant()));
        Set<PermissionId> perms = Set.of(
                SecurityPermissions.ROLES_VIEW,
                SecurityPermissions.ROLES_CREATE,
                SecurityPermissions.ROLES_UPDATE,
                SecurityPermissions.ROLES_DELETE,
                SecurityPermissions.PERMISSIONS_ASSIGN);
        AuthorizationApplicationService authorization = new AuthorizationApplicationService(
                sessions,
                alwaysActiveUsers(),
                engine(perms), emptyAssignments(), emptyRoles(), grantAll(actor, perms));
        service = new RoleAdministrationApplicationService(
                roles,
                assignments,
                authorization,
                audit,
                sessions,
                CLOCK,
                LastAdministratorGuardFixtures.guard(users, roles, assignments, overrides));
    }

    @Test
    void createGrantDeleteUnassigned() {
        Role role = service.createRole("R", "d");
        service.grantPermissionToRole(role.id(), SecurityPermissions.USERS_VIEW);
        service.grantPermissionToRole(role.id(), SecurityPermissions.USERS_VIEW);
        assertEquals(1, roles.findById(role.id()).orElseThrow().permissions().size());
        service.deleteRole(role.id());
        assertTrue(roles.findById(role.id()).isEmpty());
        assertTrue(audit.events.stream().anyMatch(e -> e.operation() == AuditOperation.ROLE_DELETED));
    }

    @Test
    void deleteAssignedRoleRejected() {
        Role role = service.createRole("R", "");
        assignments.assign(RoleAssignment.of(UserId.generate(), role.id(), CLOCK.instant()));
        assertThrows(RoleInUseException.class, () -> service.deleteRole(role.id()));
        assertTrue(roles.findById(role.id()).isPresent());
    }

    @Test
    void deniedWithoutSession() {
        sessions.close();
        assertThrows(AccessDeniedException.class, () -> service.createRole("X", ""));
        assertTrue(roles.findAll().isEmpty());
    }

    @Test
    void setRolePermissionsReplacesExactSetAndAuditsDeltas() {
        Role role = service.createRole("R", "d");
        service.grantPermissionToRole(role.id(), SecurityPermissions.USERS_VIEW);
        service.grantPermissionToRole(role.id(), SecurityPermissions.ROLES_VIEW);
        service.grantPermissionToRole(role.id(), SecurityPermissions.AUDIT_VIEW);
        audit.events.clear();
        Role updated = service.setRolePermissions(
                role.id(),
                Set.of(SecurityPermissions.USERS_VIEW, SecurityPermissions.USERS_CREATE));
        assertEquals(
                Set.of(SecurityPermissions.USERS_VIEW, SecurityPermissions.USERS_CREATE),
                updated.permissions());
        long permissionAudits = audit.events.stream()
                .filter(e -> e.operation() == AuditOperation.ROLE_PERMISSIONS_CHANGED)
                .count();
        assertEquals(3, permissionAudits);
    }

    @Test
    void setRolePermissionsRequiresPermissionsAssignNotRolesAssign() {
        sessions.close();
        UserId actor = UserId.generate();
        sessions.open(Session.of(SessionId.generate(), actor, Login.of("assigner"), CLOCK.instant()));
        Set<PermissionId> perms = Set.of(SecurityPermissions.ROLES_ASSIGN);
        AuthorizationApplicationService authorization = new AuthorizationApplicationService(
                sessions,
                alwaysActiveUsers(),
                engine(perms), emptyAssignments(), emptyRoles(), grantAll(actor, perms));
        RoleAdministrationApplicationService limited = new RoleAdministrationApplicationService(
                roles,
                assignments,
                authorization,
                audit,
                sessions,
                CLOCK,
                LastAdministratorGuardFixtures.guard(users, roles, assignments, overrides));
        Role role = Role.create(RoleId.generate(), "R", "", CLOCK);
        roles.save(role);
        assertThrows(
                AccessDeniedException.class,
                () -> limited.setRolePermissions(role.id(), Set.of(SecurityPermissions.USERS_VIEW)));
        assertTrue(roles.findById(role.id()).orElseThrow().permissions().isEmpty());
    }

    @Test
    void rejectsPermissionUpdateRemovingLastEffectiveRolesAssign() {
        Role adminRole = roles.save(Role.create(RoleId.generate(), "Admin", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK)
                .grantPermission(SecurityPermissions.USERS_VIEW, CLOCK));
        UserId admin = users.save(User.createActive(
                        UserId.generate(), Login.of("admin-user"), DisplayName.of("Admin"), PasswordHash.of("h"), CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(admin, adminRole.id(), CLOCK.instant()));
        Set<PermissionId> before = adminRole.permissions();
        int auditBefore = audit.events.size();

        LastAdministratorException ex = assertThrows(
                LastAdministratorException.class,
                () -> service.setRolePermissions(adminRole.id(), Set.of(SecurityPermissions.USERS_VIEW)));
        assertEquals(LastAdministratorGuard.PERMISSION_REMOVE_MESSAGE, ex.getMessage());
        assertEquals(before, roles.findById(adminRole.id()).orElseThrow().permissions());
        assertEquals(auditBefore, audit.events.size());
    }

    @Test
    void allowsPermissionUpdateWhenAnotherEligibleAdministratorRemains() {
        Role adminRole = roles.save(Role.create(RoleId.generate(), "Admin", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK));
        Role otherAdminRole = roles.save(Role.create(RoleId.generate(), "OtherAdmin", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK));
        UserId first = users.save(User.createActive(
                        UserId.generate(), Login.of("a1"), DisplayName.of("A1"), PasswordHash.of("h"), CLOCK))
                .id();
        UserId second = users.save(User.createActive(
                        UserId.generate(), Login.of("a2"), DisplayName.of("A2"), PasswordHash.of("h"), CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(first, adminRole.id(), CLOCK.instant()));
        assignments.assign(RoleAssignment.of(second, otherAdminRole.id(), CLOCK.instant()));

        Role updated = service.setRolePermissions(adminRole.id(), Set.of(SecurityPermissions.USERS_VIEW));
        assertEquals(Set.of(SecurityPermissions.USERS_VIEW), updated.permissions());
        assertTrue(roles.findById(otherAdminRole.id())
                .orElseThrow()
                .permissions()
                .contains(SecurityPermissions.ROLES_ASSIGN));
    }

    @Test
    void retainsEligibilityThroughIndividualGrantWhenRoleLosesAssign() {
        Role adminRole = roles.save(Role.create(RoleId.generate(), "Admin", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK));
        UserId admin = users.save(User.createActive(
                        UserId.generate(), Login.of("grant-admin"), DisplayName.of("G"), PasswordHash.of("h"), CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(admin, adminRole.id(), CLOCK.instant()));
        overrides.save(IndividualPermissionOverride.of(
                admin, SecurityPermissions.ROLES_ASSIGN, PermissionOverrideDecision.GRANT, CLOCK));

        Role updated = service.setRolePermissions(adminRole.id(), Set.of());
        assertTrue(updated.permissions().isEmpty());
    }

    @Test
    void retainsEligibilityThroughSecondAssignedRoleWhenFirstLosesAssign() {
        Role firstRole = roles.save(Role.create(RoleId.generate(), "AdminA", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK)
                .grantPermission(SecurityPermissions.USERS_VIEW, CLOCK));
        Role secondRole = roles.save(Role.create(RoleId.generate(), "AdminB", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK));
        UserId admin = users.save(User.createActive(
                        UserId.generate(), Login.of("multi-admin"), DisplayName.of("M"), PasswordHash.of("h"), CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(admin, firstRole.id(), CLOCK.instant()));
        assignments.assign(RoleAssignment.of(admin, secondRole.id(), CLOCK.instant()));

        Role updated = service.setRolePermissions(firstRole.id(), Set.of(SecurityPermissions.USERS_VIEW));
        assertEquals(Set.of(SecurityPermissions.USERS_VIEW), updated.permissions());
        assertTrue(roles.findById(secondRole.id())
                .orElseThrow()
                .permissions()
                .contains(SecurityPermissions.ROLES_ASSIGN));
    }

    @Test
    void rejectsSinglePermissionRevokeRemovingLastEffectiveRolesAssign() {
        Role adminRole = roles.save(Role.create(RoleId.generate(), "Admin", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK)
                .grantPermission(SecurityPermissions.USERS_VIEW, CLOCK));
        UserId admin = users.save(User.createActive(
                        UserId.generate(), Login.of("single-revoke"), DisplayName.of("S"), PasswordHash.of("h"), CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(admin, adminRole.id(), CLOCK.instant()));
        Set<PermissionId> before = adminRole.permissions();

        LastAdministratorException ex = assertThrows(
                LastAdministratorException.class,
                () -> service.revokePermissionFromRole(adminRole.id(), SecurityPermissions.ROLES_ASSIGN));
        assertEquals(LastAdministratorGuard.PERMISSION_REMOVE_MESSAGE, ex.getMessage());
        assertEquals(before, roles.findById(adminRole.id()).orElseThrow().permissions());
    }

    @Test
    void allowsUnrelatedPermissionChangesOnAdminRole() {
        Role adminRole = roles.save(Role.create(RoleId.generate(), "Admin", "", CLOCK)
                .grantPermission(SecurityPermissions.ROLES_ASSIGN, CLOCK)
                .grantPermission(SecurityPermissions.USERS_VIEW, CLOCK));
        UserId admin = users.save(User.createActive(
                        UserId.generate(), Login.of("keep-admin"), DisplayName.of("K"), PasswordHash.of("h"), CLOCK))
                .id();
        assignments.assign(RoleAssignment.of(admin, adminRole.id(), CLOCK.instant()));

        Role updated = service.setRolePermissions(
                adminRole.id(),
                Set.of(SecurityPermissions.ROLES_ASSIGN, SecurityPermissions.AUDIT_VIEW));
        assertEquals(
                Set.of(SecurityPermissions.ROLES_ASSIGN, SecurityPermissions.AUDIT_VIEW),
                updated.permissions());
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

    private static PermissionOverrideRepository grantAll(UserId userId, Set<PermissionId> perms) {
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
            for (RoleAssignment a : store) {
                if (a.userId().equals(userId)) {
                    ids.add(a.roleId());
                }
            }
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
                    .skip((long) pageIndex * pageSize)
                    .limit(pageSize)
                    .toList();
        }

        @Override
        public List<User> searchByLoginOrDisplayName(String query, int limit) {
            return List.of();
        }
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
