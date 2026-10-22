package com.tmp.security.application;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.api.UserId;
import com.tmp.security.domain.EffectivePermissionCalculator;
import com.tmp.security.domain.IndividualPermissionOverride;
import com.tmp.security.domain.PermissionOverrideDecision;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.User;
import com.tmp.security.domain.UserStatus;
import com.tmp.security.domain.repository.PermissionOverrideRepository;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.UserRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared last-administrator invariant: at least one ACTIVE user must retain effective
 * {@link SecurityPermissions#ROLES_ASSIGN}. Uses a transaction-scoped PostgreSQL advisory lock.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Constructor stores Spring-managed collaborators for transactional checks.")
public final class LastAdministratorGuard {

    public static final String LOCK_KEY = "tmp.security.last-administrator";

    public static final String ROLE_REVOKE_MESSAGE =
            "Нельзя снять роль с последнего действующего администратора системы.";

    public static final String USER_DELETE_MESSAGE =
            "Нельзя отключить последнего действующего администратора системы.";

    public static final String PERMISSION_REMOVE_MESSAGE =
            "Нельзя удалить право назначения ролей у последнего действующего администратора.";

    private static final int ACTIVE_USER_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PermissionOverrideRepository permissionOverrideRepository;
    private final JdbcTemplate jdbcTemplate;

    public LastAdministratorGuard(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            PermissionOverrideRepository permissionOverrideRepository,
            JdbcTemplate jdbcTemplate) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.roleAssignmentRepository =
                Objects.requireNonNull(roleAssignmentRepository, "roleAssignmentRepository");
        this.permissionOverrideRepository =
                Objects.requireNonNull(permissionOverrideRepository, "permissionOverrideRepository");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    public void acquireLock() {
        jdbcTemplate.execute(
                (org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
                    try (var statement = connection.prepareStatement(
                            "SELECT pg_advisory_xact_lock(hashtext(?))")) {
                        statement.setString(1, LOCK_KEY);
                        statement.execute();
                    }
                    return null;
                });
    }

    public boolean wouldRetainEligibleAfterRoleRevoke(UserId userId, RoleId roleId) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleId, "roleId");
        Set<IndividualPermissionOverride> overrides = overridesFor(userId);
        Set<RoleId> beforeIds = new HashSet<>(roleAssignmentRepository.findRoleIdsForUser(userId));
        boolean hadBefore = isGranted(overrides, loadRolesByIds(beforeIds));
        Set<RoleId> afterIds = new HashSet<>(beforeIds);
        afterIds.remove(roleId);
        boolean hasAfter = isGranted(overrides, loadRolesByIds(afterIds));
        if (!hadBefore || hasAfter) {
            return true;
        }
        return hasOtherEligibleAdministrator(userId);
    }

    public boolean wouldRetainEligibleAfterUserDeactivation(UserId userId) {
        Objects.requireNonNull(userId, "userId");
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !user.get().isActive() || !isEligible(userId)) {
            return true;
        }
        return hasOtherEligibleAdministrator(userId);
    }

    public boolean wouldRetainEligibleAfterRolePermissions(
            RoleId roleId, Set<PermissionId> targetPermissions) {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(targetPermissions, "targetPermissions");
        Role current = roleRepository
                .findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
        Set<PermissionId> target = Set.copyOf(targetPermissions);
        boolean losesAssign = current.permissions().contains(SecurityPermissions.ROLES_ASSIGN)
                && !target.contains(SecurityPermissions.ROLES_ASSIGN);
        if (!losesAssign) {
            return true;
        }
        Role substituted = Role.rehydrate(
                current.id(),
                current.name(),
                current.description(),
                target,
                current.version(),
                current.createdAt(),
                current.updatedAt());
        for (User candidate : loadAllActiveUsers()) {
            if (isEligibleWithRoleSubstitution(candidate.id(), roleId, substituted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param afterDecision empty means the override row is removed
     */
    public boolean wouldRetainEligibleAfterOverrideChange(
            UserId userId, PermissionId permissionId, Optional<PermissionOverrideDecision> afterDecision) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(permissionId, "permissionId");
        Objects.requireNonNull(afterDecision, "afterDecision");
        if (!permissionId.equals(SecurityPermissions.ROLES_ASSIGN)) {
            return true;
        }
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !user.get().isActive()) {
            return true;
        }
        Set<Role> roles = loadRolesByIds(roleAssignmentRepository.findRoleIdsForUser(userId));
        Set<IndividualPermissionOverride> before = overridesFor(userId);
        boolean hadBefore = isGranted(before, roles);
        Set<IndividualPermissionOverride> after = simulateOverrideChange(userId, permissionId, afterDecision);
        boolean hasAfter = isGranted(after, roles);
        if (!hadBefore || hasAfter) {
            return true;
        }
        return hasOtherEligibleAdministrator(userId);
    }

    private boolean hasOtherEligibleAdministrator(UserId excludedUserId) {
        for (User candidate : loadAllActiveUsers()) {
            if (candidate.id().equals(excludedUserId)) {
                continue;
            }
            if (isEligible(candidate.id())) {
                return true;
            }
        }
        return false;
    }

    private boolean isEligible(UserId userId) {
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || !user.get().isActive()) {
            return false;
        }
        return isGranted(
                overridesFor(userId),
                loadRolesByIds(roleAssignmentRepository.findRoleIdsForUser(userId)));
    }

    private boolean isEligibleWithRoleSubstitution(UserId userId, RoleId modifiedRoleId, Role substituted) {
        Set<Role> roles = new HashSet<>();
        for (RoleId id : roleAssignmentRepository.findRoleIdsForUser(userId)) {
            if (id.equals(modifiedRoleId)) {
                roles.add(substituted);
            } else {
                roleRepository.findById(id).ifPresent(roles::add);
            }
        }
        return isGranted(overridesFor(userId), roles);
    }

    private static boolean isGranted(Set<IndividualPermissionOverride> overrides, Set<Role> roles) {
        return EffectivePermissionCalculator.isGranted(SecurityPermissions.ROLES_ASSIGN, overrides, roles);
    }

    private Set<IndividualPermissionOverride> overridesFor(UserId userId) {
        return new HashSet<>(permissionOverrideRepository.findByUser(userId));
    }

    private Set<IndividualPermissionOverride> simulateOverrideChange(
            UserId userId, PermissionId permissionId, Optional<PermissionOverrideDecision> afterDecision) {
        Set<IndividualPermissionOverride> result = new HashSet<>();
        for (IndividualPermissionOverride override : permissionOverrideRepository.findByUser(userId)) {
            if (!override.permissionId().equals(permissionId)) {
                result.add(override);
            }
        }
        afterDecision.ifPresent(decision -> result.add(IndividualPermissionOverride.of(
                userId, permissionId, decision, java.time.Clock.systemUTC())));
        return result;
    }

    private List<User> loadAllActiveUsers() {
        List<User> active = new ArrayList<>();
        int pageIndex = 0;
        while (true) {
            List<User> page =
                    userRepository.findPage(pageIndex, ACTIVE_USER_PAGE_SIZE, UserStatus.ACTIVE);
            active.addAll(page);
            if (page.size() < ACTIVE_USER_PAGE_SIZE) {
                break;
            }
            pageIndex++;
        }
        return active;
    }

    private Set<Role> loadRolesByIds(Set<RoleId> roleIds) {
        Set<Role> result = new HashSet<>();
        for (RoleId id : roleIds) {
            roleRepository.findById(id).ifPresent(result::add);
        }
        return result;
    }
}