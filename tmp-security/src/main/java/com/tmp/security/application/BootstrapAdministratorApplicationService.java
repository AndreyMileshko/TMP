package com.tmp.security.application;

import com.tmp.security.api.AuditEventId;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.api.UserId;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.domain.AuditOperation;
import com.tmp.security.domain.AuditResult;
import com.tmp.security.domain.DuplicateLoginException;
import com.tmp.security.domain.MissingBootstrapConfigurationException;
import com.tmp.security.domain.PasswordHasher;
import com.tmp.security.domain.Role;
import com.tmp.security.domain.RoleAssignment;
import com.tmp.security.domain.SecurityAuditEvent;
import com.tmp.security.domain.User;
import com.tmp.security.domain.repository.RoleAssignmentRepository;
import com.tmp.security.domain.repository.RoleRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import com.tmp.security.domain.repository.UserRepository;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first Security Administrator exactly once when no users exist.
 *
 * <p>Concurrency: the existence check has a race window; the unique index on
 * {@code lower(login)} is the final arbiter. A racing second bootstrap that hits
 * {@link DuplicateLoginException} is treated as a benign no-op.
 */
public class BootstrapAdministratorApplicationService {

    public static final String SECURITY_ADMINISTRATOR_ROLE_NAME = "Security Administrator";

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapAdministratorApplicationService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final SecurityAuditRepository auditRepository;
    private final PasswordHasher passwordHasher;
    private final SecurityBootstrapProperties properties;
    private final java.time.Clock clock;

    public BootstrapAdministratorApplicationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            SecurityAuditRepository auditRepository,
            PasswordHasher passwordHasher,
            SecurityBootstrapProperties properties,
            java.time.Clock clock) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository");
        this.roleAssignmentRepository =
                Objects.requireNonNull(roleAssignmentRepository, "roleAssignmentRepository");
        this.auditRepository = Objects.requireNonNull(auditRepository, "auditRepository");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public void ensureBootstrapAdministrator() {
        if (userRepository.existsAny()) {
            return;
        }
        if (!properties.isComplete()) {
            throw new MissingBootstrapConfigurationException(
                    "Bootstrap administrator configuration is incomplete. "
                            + "Set TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN, "
                            + "TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME, and "
                            + "TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD.");
        }

        try {
            Role role = Role.create(
                    RoleId.generate(),
                    SECURITY_ADMINISTRATOR_ROLE_NAME,
                    "Full access to Security Administration",
                    clock);
            for (PermissionId permissionId : securityAdministrationPermissions()) {
                role = role.grantPermission(permissionId, clock);
            }
            role = roleRepository.save(role);

            char[] passwordChars = properties.getAdminPassword().toCharArray();
            User admin;
            try {
                admin = userRepository.save(User.createActive(
                        UserId.generate(),
                        Login.of(properties.getAdminLogin()),
                        DisplayName.of(properties.getAdminDisplayName()),
                        passwordHasher.hash(passwordChars),
                        clock));
            } finally {
                Arrays.fill(passwordChars, '\0');
            }

            roleAssignmentRepository.assign(
                    RoleAssignment.of(admin.id(), role.id(), clock.instant()));
            auditRepository.append(SecurityAuditEvent.record(
                    AuditEventId.generate(),
                    clock.instant(),
                    null,
                    "system-bootstrap",
                    AuditOperation.USER_CREATED,
                    "USER",
                    admin.id().value().toString(),
                    "Bootstrap administrator created",
                    AuditResult.SUCCESS));
        } catch (DuplicateLoginException ex) {
            LOG.info("Bootstrap administrator already created by a concurrent startup; continuing");
        }
    }

    private static Set<PermissionId> securityAdministrationPermissions() {
        return Set.of(
                SecurityPermissions.USERS_VIEW,
                SecurityPermissions.USERS_CREATE,
                SecurityPermissions.USERS_UPDATE,
                SecurityPermissions.USERS_DELETE,
                SecurityPermissions.USERS_RESET_PASSWORD,
                SecurityPermissions.ROLES_VIEW,
                SecurityPermissions.ROLES_CREATE,
                SecurityPermissions.ROLES_UPDATE,
                SecurityPermissions.ROLES_DELETE,
                SecurityPermissions.ROLES_ASSIGN,
                SecurityPermissions.PERMISSIONS_ASSIGN,
                SecurityPermissions.AUDIT_VIEW);
    }
}
