package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import com.tmp.security.application.securedfixture.SecuredOperationFixture;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecuredOperationFixtureTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId VIEW = PermissionId.of("security.users.view");

    @Test
    void directCallEnforcesAuthorization() {
        SessionContext sessions = new SessionContext();
        UserId userId = UserId.generate();
        sessions.open(Session.of(SessionId.generate(), userId, Login.of("u"), CLOCK.instant()));

        AuthorizationApplicationService denied = new AuthorizationApplicationService(
                sessions,
                alwaysActiveUsers(),
                activeEngine(Set.of(VIEW)),
                emptyAssignments(),
                emptyRoles(),
                emptyOverrides());
        SecuredOperationFixture fixture = new SecuredOperationFixture(denied);
        assertThrows(AccessDeniedException.class, () -> fixture.performSecuredOperation(VIEW));

        PermissionOverrideRepository grants = new PermissionOverrideRepository() {
            @Override
            public IndividualPermissionOverride save(IndividualPermissionOverride override) {
                return override;
            }

            @Override
            public void remove(UserId userId, PermissionId permissionId) {
            }

            @Override
            public List<IndividualPermissionOverride> findByUser(UserId id) {
                return List.of(IndividualPermissionOverride.of(
                        userId, VIEW, PermissionOverrideDecision.GRANT, CLOCK));
            }

            @Override
            public Optional<IndividualPermissionOverride> findByUserAndPermission(
                    UserId userId, PermissionId permissionId) {
                return Optional.empty();
            }
        };
        AuthorizationApplicationService allowed = new AuthorizationApplicationService(
                sessions,
                alwaysActiveUsers(),
                activeEngine(Set.of(VIEW)), emptyAssignments(), emptyRoles(), grants);
        assertEquals("OK", new SecuredOperationFixture(allowed).performSecuredOperation(VIEW));
    }

    private static CapabilityEngine activeEngine(Set<PermissionId> active) {
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

    private static PermissionOverrideRepository emptyOverrides() {
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
                return List.of();
            }

            @Override
            public Optional<IndividualPermissionOverride> findByUserAndPermission(
                    UserId userId, PermissionId permissionId) {
                return Optional.empty();
            }
        };
    }
    private static AlwaysActiveUserRepository alwaysActiveUsers() {
        return new AlwaysActiveUserRepository();
    }
}
