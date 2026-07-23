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

class PermissionOverrideApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    private InMemoryUsers users;
    private InMemoryOverrides overrides;
    private InMemoryAudit audit;
    private SessionContext sessions;
    private PermissionOverrideApplicationService service;
    private UserId target;

    @BeforeEach
    void setUp() {
        users = new InMemoryUsers();
        overrides = new InMemoryOverrides();
        audit = new InMemoryAudit();
        sessions = new SessionContext();
        UserId actor = UserId.generate();
        sessions.open(Session.of(SessionId.generate(), actor, Login.of("admin"), CLOCK.instant()));
        target = users.save(User.createActive(
                        UserId.generate(), Login.of("u"), DisplayName.of("U"), PasswordHash.of("h"), CLOCK))
                .id();
        Set<PermissionId> perms = Set.of(SecurityPermissions.PERMISSIONS_ASSIGN);
        service = new PermissionOverrideApplicationService(
                users,
                overrides,
                new AuthorizationApplicationService(
                sessions,
                alwaysActiveUsers(),
                engine(perms), emptyAssignments(), emptyRoles(), grant(actor, perms)),
                audit,
                sessions,
                CLOCK);
    }

    @Test
    void grantRevokeFlipAndRemove() {
        service.grantIndividualPermission(target, SecurityPermissions.USERS_VIEW);
        assertEquals(1, overrides.findByUser(target).size());
        assertEquals(PermissionOverrideDecision.GRANT, overrides.findByUser(target).getFirst().decision());
        service.revokeIndividualPermission(target, SecurityPermissions.USERS_VIEW);
        assertEquals(1, overrides.findByUser(target).size());
        assertEquals(PermissionOverrideDecision.REVOKE, overrides.findByUser(target).getFirst().decision());
        service.removeOverride(target, SecurityPermissions.USERS_VIEW);
        assertTrue(overrides.findByUser(target).isEmpty());
        assertTrue(audit.events.stream().anyMatch(e -> e.operation() == AuditOperation.PERMISSION_OVERRIDE_REMOVED));
    }

    @Test
    void removeMissingIsNoOpWithoutAudit() {
        int before = audit.events.size();
        service.removeOverride(target, SecurityPermissions.USERS_CREATE);
        assertEquals(before, audit.events.size());
    }

    @Test
    void deniedWithoutPermission() {
        sessions.close();
        assertThrows(
                AccessDeniedException.class,
                () -> service.grantIndividualPermission(target, SecurityPermissions.USERS_VIEW));
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

    private static final class InMemoryOverrides implements PermissionOverrideRepository {
        private final Map<String, IndividualPermissionOverride> store = new HashMap<>();

        private static String key(UserId userId, PermissionId permissionId) {
            return userId.value() + ":" + permissionId.value();
        }

        @Override
        public IndividualPermissionOverride save(IndividualPermissionOverride override) {
            store.put(key(override.userId(), override.permissionId()), override);
            return override;
        }

        @Override
        public void remove(UserId userId, PermissionId permissionId) {
            store.remove(key(userId, permissionId));
        }

        @Override
        public List<IndividualPermissionOverride> findByUser(UserId userId) {
            return store.values().stream().filter(o -> o.userId().equals(userId)).toList();
        }

        @Override
        public Optional<IndividualPermissionOverride> findByUserAndPermission(
                UserId userId, PermissionId permissionId) {
            return Optional.ofNullable(store.get(key(userId, permissionId)));
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
