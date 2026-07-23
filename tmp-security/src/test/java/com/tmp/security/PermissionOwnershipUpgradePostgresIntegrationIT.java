package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.security.api.SecurityPermissions;
import com.tmp.security.application.PermissionSynchronizationApplicationService;
import com.tmp.security.capability.SecurityAdministrationCapability;
import com.tmp.security.domain.PermissionOwnershipConflictException;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import com.tmp.security.domain.repository.SecurityAuditRepository;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Mandatory V4 → V5 upgrade path: existing permission definitions receive
 * {@code legacy.unassigned}, then synchronization claims ownership without dropping
 * role permissions or individual overrides.
 */
@Testcontainers
@SpringBootTest(classes = PermissionOwnershipUpgradePostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class PermissionOwnershipUpgradePostgresIntegrationIT {

    private static final AtomicBoolean PREPARED = new AtomicBoolean();
    private static final UUID LEGACY_ROLE_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final UUID LEGACY_USER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String LEGACY_PERMISSION = SecurityPermissions.USERS_VIEW.value();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        prepareV4ToV5Upgrade(POSTGRES);
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private PermissionSynchronizationApplicationService synchronization;

    @Autowired
    private PermissionDefinitionRepository permissionDefinitions;

    @Autowired
    private SecurityAuditRepository auditRepository;

    @Autowired
    private Clock clock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void v4ToV5UpgradeClaimsLegacyOwnershipAndPreservesAssignments() {
        synchronization.synchronize();

        var definition = permissionDefinitions.findById(SecurityPermissions.USERS_VIEW).orElseThrow();
        assertEquals(SecurityAdministrationCapability.ID.value(), definition.ownerCapabilityId());

        assertEquals(1, countRolePermissions());
        assertEquals(1, countOverrides());

        synchronization.synchronize();
        assertEquals(
                SecurityAdministrationCapability.ID.value(),
                permissionDefinitions.findById(SecurityPermissions.USERS_VIEW).orElseThrow().ownerCapabilityId());
        assertEquals(1, countRolePermissions());
        assertEquals(1, countOverrides());

        FakeCapabilityEngine conflicting = new FakeCapabilityEngine();
        conflicting.put(
                CapabilityDescriptor.builder()
                        .id(CapabilityId.of("other.capability"))
                        .name("Other")
                        .version(CapabilityVersion.of("1.0.0"))
                        .description("other")
                        .permissions(List.of(PermissionDescriptor.of(LEGACY_PERMISSION, "View users", "desc")))
                        .build(),
                CapabilityLifecycleState.ACTIVE);
        PermissionSynchronizationApplicationService conflictingSync =
                new PermissionSynchronizationApplicationService(
                        conflicting, permissionDefinitions, auditRepository, clock);
        assertThrows(PermissionOwnershipConflictException.class, conflictingSync::synchronize);
        assertEquals(
                SecurityAdministrationCapability.ID.value(),
                permissionDefinitions.findById(SecurityPermissions.USERS_VIEW).orElseThrow().ownerCapabilityId());
        assertEquals(1, countRolePermissions());
        assertEquals(1, countOverrides());
    }

    private long countRolePermissions() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.role_permissions WHERE permission_id = ?",
                Long.class,
                LEGACY_PERMISSION);
        return count == null ? 0L : count;
    }

    private long countOverrides() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.user_permission_overrides WHERE permission_id = ?",
                Long.class,
                LEGACY_PERMISSION);
        return count == null ? 0L : count;
    }

    private static void prepareV4ToV5Upgrade(PostgreSQLContainer<?> postgres) {
        if (!PREPARED.compareAndSet(false, true)) {
            return;
        }
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("4")
                .load()
                .migrate();

        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    INSERT INTO security.permission_definitions (
                        permission_id, display_name, description, active, registered_at, version)
                    VALUES (
                        '%s', 'View users', 'legacy V4 definition', TRUE, NOW(), 0)
                    """
                            .formatted(LEGACY_PERMISSION));
            statement.execute(
                    """
                    INSERT INTO security.roles (id, name, description, version, created_at, updated_at)
                    VALUES ('%s', 'Legacy Role', 'V4 role', 0, NOW(), NOW())
                    """
                            .formatted(LEGACY_ROLE_ID));
            statement.execute(
                    """
                    INSERT INTO security.role_permissions (role_id, permission_id, granted_at)
                    VALUES ('%s', '%s', NOW())
                    """
                            .formatted(LEGACY_ROLE_ID, LEGACY_PERMISSION));
            statement.execute(
                    """
                    INSERT INTO security.users (
                        id, login, display_name, password_hash, status, version, created_at, updated_at)
                    VALUES (
                        '%s', 'legacy-user', 'Legacy User', 'not-a-real-hash', 'ACTIVE', 0, NOW(), NOW())
                    """
                            .formatted(LEGACY_USER_ID));
            statement.execute(
                    """
                    INSERT INTO security.user_permission_overrides (
                        user_id, permission_id, decision, updated_at, version)
                    VALUES ('%s', '%s', 'GRANT', NOW(), 0)
                    """
                            .formatted(LEGACY_USER_ID, LEGACY_PERMISSION));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to seed V4 security data for upgrade test", ex);
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        Integer legacyOwners = null;
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                Statement statement = connection.createStatement();
                var rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM security.permission_definitions "
                                + "WHERE owner_capability_id = 'legacy.unassigned'")) {
            if (rs.next()) {
                legacyOwners = rs.getInt(1);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify V5 legacy backfill", ex);
        }
        assertTrue(legacyOwners != null && legacyOwners >= 1);
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        SecurityAutoConfiguration.class
    })
    static class TestApplication {
    }

    private static final class FakeCapabilityEngine implements CapabilityEngine {
        private final Map<CapabilityId, CapabilityDescriptor> descriptors = new HashMap<>();
        private final Map<CapabilityId, CapabilityLifecycleState> states = new HashMap<>();

        void put(CapabilityDescriptor descriptor, CapabilityLifecycleState state) {
            descriptors.put(descriptor.id(), descriptor);
            states.put(descriptor.id(), state);
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
            return Optional.ofNullable(descriptors.get(id));
        }

        @Override
        public List<CapabilityDescriptor> registeredCapabilities() {
            return List.copyOf(descriptors.values());
        }

        @Override
        public CapabilityLifecycleState stateOf(CapabilityId id) {
            return states.getOrDefault(id, CapabilityLifecycleState.REGISTERED);
        }

        @Override
        public List<PermissionDescriptor> activePermissions() {
            return List.of();
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
            return new CapabilityEngineStatus(0, descriptors.size(), 0, 0);
        }
    }
}
