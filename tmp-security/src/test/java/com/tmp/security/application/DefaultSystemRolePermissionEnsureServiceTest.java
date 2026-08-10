package com.tmp.security.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.repository.RoleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

class DefaultSystemRolePermissionEnsureServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-10T04:30:00Z"), ZoneOffset.UTC);
    private static final PermissionId WAREHOUSE_VIEW = PermissionId.of("warehouse.stock.view");

    @Test
    void grantsMissingPermissions() {
        InMemoryRoles roles = new InMemoryRoles();
        roles.save(Role.create(RoleId.generate(), "Security Administrator", "admin", CLOCK));
        DefaultSystemRolePermissionEnsureService service =
                new DefaultSystemRolePermissionEnsureService(roles, CLOCK);

        service.ensurePermissions("Security Administrator", Set.of(WAREHOUSE_VIEW));

        assertTrue(roles.findAll().get(0).permissions().contains(WAREHOUSE_VIEW));
    }

    @Test
    void isIdempotent() {
        InMemoryRoles roles = new InMemoryRoles();
        Role admin =
                Role.create(RoleId.generate(), "Security Administrator", "admin", CLOCK)
                        .grantPermission(WAREHOUSE_VIEW, CLOCK);
        roles.save(admin);
        DefaultSystemRolePermissionEnsureService service =
                new DefaultSystemRolePermissionEnsureService(roles, CLOCK);

        service.ensurePermissions("Security Administrator", Set.of(WAREHOUSE_VIEW));

        assertEquals(1, roles.saveCount);
    }

    private static final class InMemoryRoles implements RoleRepository {
        private final ConcurrentHashMap<RoleId, Role> byId = new ConcurrentHashMap<>();
        private int saveCount;

        @Override
        public Role save(Role role) {
            saveCount++;
            byId.put(role.id(), role);
            return role;
        }

        @Override
        public Optional<Role> findById(RoleId id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<Role> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public void deleteById(RoleId id) {
            byId.remove(id);
        }
    }
}
