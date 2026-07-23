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
import com.tmp.security.domain.PermissionOverrideDecision;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.RoleAssignment;
import com.tmp.security.api.RoleInUseException;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleAdministrationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryRoles roles;
    private InMemoryAssignments assignments;
    private InMemoryAudit audit;
    private SessionContext sessions;
    private RoleAdministrationApplicationService service;

    @BeforeEach
    void setUp() {
        roles = new InMemoryRoles();
        assignments = new InMemoryAssignments();
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
                roles, assignments, authorization, audit, sessions, CLOCK);
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
