package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.DuplicateLoginException;
import com.tmp.security.domain.MissingBootstrapConfigurationException;
import com.tmp.security.domain.PasswordHash;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.RoleAssignment;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.User;
import com.tmp.security.domain.AuditQueryFilter;
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
import org.junit.jupiter.api.Test;

class BootstrapAdministratorApplicationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);

    @Test
    void createsAdminWhenEmptyAndConfigured() {
        InMemoryUsers users = new InMemoryUsers();
        InMemoryRoles roles = new InMemoryRoles();
        InMemoryAssignments assignments = new InMemoryAssignments();
        InMemoryAudit audit = new InMemoryAudit();
        SecurityBootstrapProperties props = configuredProps();
        BootstrapAdministratorApplicationService service = new BootstrapAdministratorApplicationService(
                users, roles, assignments, audit, new FakeHasher(), props, CLOCK);

        service.ensureBootstrapAdministrator();

        assertTrue(users.existsAny());
        assertEquals(1, roles.findAll().size());
        assertEquals(12, roles.findAll().getFirst().permissions().size());
        assertEquals(1, assignments.countUsersForRole(roles.findAll().getFirst().id()));
        assertEquals(1, audit.events.size());
    }

    @Test
    void missingConfigFailsFast() {
        InMemoryUsers users = new InMemoryUsers();
        BootstrapAdministratorApplicationService service = new BootstrapAdministratorApplicationService(
                users,
                new InMemoryRoles(),
                new InMemoryAssignments(),
                new InMemoryAudit(),
                new FakeHasher(),
                new SecurityBootstrapProperties(),
                CLOCK);
        assertThrows(MissingBootstrapConfigurationException.class, service::ensureBootstrapAdministrator);
        assertFalse(users.existsAny());
    }

    @Test
    void existingUserIsNoOp() {
        InMemoryUsers users = new InMemoryUsers();
        users.save(User.createActive(
                UserId.generate(),
                Login.of("existing"),
                DisplayName.of("E"),
                PasswordHash.of("hash"),
                CLOCK));
        InMemoryAudit audit = new InMemoryAudit();
        BootstrapAdministratorApplicationService service = new BootstrapAdministratorApplicationService(
                users,
                new InMemoryRoles(),
                new InMemoryAssignments(),
                audit,
                new FakeHasher(),
                configuredProps(),
                CLOCK);
        service.ensureBootstrapAdministrator();
        assertEquals(1, users.store.size());
        assertTrue(audit.events.isEmpty());
    }

    @Test
    void concurrentDuplicateLoginIsBenign() {
        InMemoryUsers users = new InMemoryUsers();
        users.failNextSaveWithDuplicate = true;
        BootstrapAdministratorApplicationService service = new BootstrapAdministratorApplicationService(
                users,
                new InMemoryRoles(),
                new InMemoryAssignments(),
                new InMemoryAudit(),
                new FakeHasher(),
                configuredProps(),
                CLOCK);
        service.ensureBootstrapAdministrator();
        assertFalse(users.existsAny());
    }

    private static SecurityBootstrapProperties configuredProps() {
        SecurityBootstrapProperties props = new SecurityBootstrapProperties();
        props.setAdminLogin("admin");
        props.setAdminDisplayName("Administrator");
        props.setAdminPassword("bootstrap-secret");
        return props;
    }

    private static final class FakeHasher implements PasswordHasher {
        @Override
        public PasswordHash hash(char[] plaintextPassword) {
            return PasswordHash.of("$2a$10$fake");
        }

        @Override
        public boolean matches(char[] plaintextPassword, PasswordHash hash) {
            return true;
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<UserId, User> store = new ConcurrentHashMap<>();
        private boolean failNextSaveWithDuplicate;

        @Override
        public User save(User user) {
            if (failNextSaveWithDuplicate) {
                failNextSaveWithDuplicate = false;
                throw new DuplicateLoginException("dup");
            }
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
        public List<User> findPage(int pageIndex, int pageSize, com.tmp.security.domain.UserStatus statusFilter) {
            return store.values().stream()
                    .filter(u -> statusFilter == null || u.status() == statusFilter)
                    .skip((long) pageIndex * pageSize)
                    .limit(pageSize)
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
}
