package com.tmp.security.application;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SystemRolePermissionEnsureService;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.repository.RoleRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default system ensure for role permission assignments (no interactive authorization check).
 */
public class DefaultSystemRolePermissionEnsureService implements SystemRolePermissionEnsureService {

    private final RoleRepository roleRepository;
    private final Clock clock;

    public DefaultSystemRolePermissionEnsureService(RoleRepository roleRepository, Clock clock) {
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    @Transactional
    public void ensurePermissions(String roleName, Set<PermissionId> permissionIds) {
        Objects.requireNonNull(roleName, "roleName");
        Objects.requireNonNull(permissionIds, "permissionIds");
        if (roleName.isBlank() || permissionIds.isEmpty()) {
            return;
        }
        Optional<Role> role =
                roleRepository.findAll().stream().filter(r -> roleName.equals(r.name())).findFirst();
        if (role.isEmpty()) {
            return;
        }
        Role current = role.get();
        Role updated = current;
        for (PermissionId permissionId : permissionIds) {
            Objects.requireNonNull(permissionId, "permissionId");
            updated = updated.grantPermission(permissionId, clock);
        }
        if (updated != current) {
            roleRepository.save(updated);
        }
    }
}
