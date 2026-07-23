package com.tmp.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.security.application.PermissionSynchronizationApplicationService;
import com.tmp.security.capability.SecurityAdministrationCapability;
import com.tmp.security.domain.repository.PermissionDefinitionRepository;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SecurityPermissions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = PermissionSynchronizationPostgresIntegrationIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class PermissionSynchronizationPostgresIntegrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private PermissionSynchronizationApplicationService synchronization;

    @Autowired
    private PermissionDefinitionRepository permissionDefinitions;

    @Autowired
    private CapabilityEngine capabilityEngine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void syncPersistsOwnerAndDeactivatesInactiveCapabilityDefinitions() {
        synchronization.synchronize();
        var definition = permissionDefinitions.findById(SecurityPermissions.USERS_VIEW).orElseThrow();
        assertEquals(SecurityAdministrationCapability.ID.value(), definition.ownerCapabilityId());
        assertTrue(definition.active());

        Long rolePermsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.role_permissions", Long.class);
        Long overridesBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.user_permission_overrides", Long.class);

        capabilityEngine.deactivate(SecurityAdministrationCapability.ID);
        synchronization.synchronize();
        assertFalse(permissionDefinitions.findById(SecurityPermissions.USERS_VIEW).orElseThrow().active());

        assertEquals(rolePermsBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.role_permissions", Long.class));
        assertEquals(overridesBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM security.user_permission_overrides", Long.class));
    }

    @Test
    void orphanDefinitionIsDeactivatedWithoutDeletingAssignments() {
        synchronization.synchronize();
        jdbcTemplate.update(
                """
                INSERT INTO security.permission_definitions (
                    permission_id, owner_capability_id, display_name, description, active, registered_at, version)
                VALUES ('security.orphan.perm', 'gone.capability', 'Orphan', '', TRUE, NOW(), 0)
                """);
        synchronization.synchronize();
        assertFalse(permissionDefinitions.findById(PermissionId.of("security.orphan.perm")).orElseThrow().active());
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
}
