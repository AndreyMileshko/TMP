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
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.PermissionOverrideDecision;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.Session;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
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

class AuthorizationApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId VIEW = PermissionId.of("security.users.view");
    private static final PermissionId CREATE = PermissionId.of("security.users.create");

    private SessionContext sessions;
    private FakeCapabilityEngine engine;
    private InMemoryRoles roles;
    private InMemoryAssignments assignments;
    private InMemoryOverrides overrides;
    private AuthorizationApplicationService authorization;
    private UserId userId;

    @BeforeEach
    void setUp() {
        sessions = new SessionContext();
        engine = new FakeCapabilityEngine();
        engine.setActive(Set.of(VIEW, CREATE));
        roles = new InMemoryRoles();
        assignments = new InMemoryAssignments();
        overrides = new InMemoryOverrides();
        authorization = new AuthorizationApplicationService(
                sessions, engine, assignments, roles, overrides);
        userId = UserId.generate();
    }

    @Test
    void noSessionDenies() {
        assertFalse(authorization.hasPermission(VIEW));
        assertThrows(AccessDeniedException.class, () -> authorization.requirePermission(VIEW));
        assertTrue(authorization.effectivePermissions().isEmpty());
    }

    @Test
    void roleGrantAllowsWhenActive() {
        openSession();
        Role role = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK).grantPermission(VIEW, CLOCK));
        assignments.assignRole(userId, role.id());
        assertTrue(authorization.hasPermission(VIEW));
        authorization.requirePermission(VIEW);
        assertEquals(Set.of(VIEW), authorization.effectivePermissions());
    }

    @Test
    void inactivePermissionDeniedEvenIfGranted() {
        openSession();
        Role role = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK).grantPermission(VIEW, CLOCK));
        assignments.assignRole(userId, role.id());
        engine.setActive(Set.of(CREATE));
        assertFalse(authorization.hasPermission(VIEW));
    }

    @Test
    void individualRevokeOverridesRole() {
        openSession();
        Role role = roles.save(Role.create(RoleId.generate(), "R", "", CLOCK).grantPermission(VIEW, CLOCK));
        assignments.assignRole(userId, role.id());
        overrides.save(IndividualPermissionOverride.of(
                userId, VIEW, PermissionOverrideDecision.REVOKE, CLOCK));
        assertFalse(authorization.hasPermission(VIEW));
    }

    @Test
    void individualGrantWithoutRoleAllows() {
        openSession();
        overrides.save(IndividualPermissionOverride.of(
                userId, CREATE, PermissionOverrideDecision.GRANT, CLOCK));
        assertTrue(authorization.hasPermission(CREATE));
    }

    private void openSession() {
        sessions.open(Session.of(SessionId.generate(), userId, Login.of("u"), CLOCK.instant()));
    }

    private static final class FakeCapabilityEngine implements CapabilityEngine {
        private Set<PermissionId> active = Set.of();

        void setActive(Set<PermissionId> ids) {
            this.active = Set.copyOf(ids);
        }

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
        private final Map<UserId, Set<RoleId>> byUser = new HashMap<>();

        void assignRole(UserId userId, RoleId roleId) {
            byUser.computeIfAbsent(userId, k -> new HashSet<>()).add(roleId);
        }

        @Override
        public void assign(com.tmp.security.domain.RoleAssignment assignment) {
            assignRole(assignment.userId(), assignment.roleId());
        }

        @Override
        public void revoke(UserId userId, RoleId roleId) {
        }

        @Override
        public Set<RoleId> findRoleIdsForUser(UserId userId) {
            return Set.copyOf(byUser.getOrDefault(userId, Set.of()));
        }

        @Override
        public List<UserId> findUserIdsForRole(RoleId roleId) {
            return List.of();
        }

        @Override
        public long countUsersForRole(RoleId roleId) {
            return 0;
        }
    }

    private static final class InMemoryOverrides implements PermissionOverrideRepository {
        private final List<IndividualPermissionOverride> store = new ArrayList<>();

        @Override
        public IndividualPermissionOverride save(IndividualPermissionOverride override) {
            store.removeIf(o -> o.equals(override));
            store.add(override);
            return override;
        }

        @Override
        public void remove(UserId userId, PermissionId permissionId) {
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
}
