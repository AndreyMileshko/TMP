package com.tmp.security.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleId;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PermissionDefinition;
import com.tmp.security.domain.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = JdbcRoleRepositoryTest.TestApplication.class)
@ActiveProfiles("test")
class JdbcRoleRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId VIEW = PermissionId.of("security.users.view");
    private static final PermissionId CREATE = PermissionId.of("security.users.create");

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
    private JdbcTemplate jdbcTemplate;

    private JdbcRoleRepository roleRepository;
    private JdbcPermissionDefinitionRepository permissionRepository;

    @BeforeEach
    void setUp() {
        roleRepository = new JdbcRoleRepository(jdbcTemplate);
        permissionRepository = new JdbcPermissionDefinitionRepository(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM security.role_permissions");
        jdbcTemplate.update("DELETE FROM security.user_roles");
        jdbcTemplate.update("DELETE FROM security.roles");
        jdbcTemplate.update("DELETE FROM security.permission_definitions");
        permissionRepository.save(PermissionDefinition.register(VIEW, "View", "", CLOCK));
        permissionRepository.save(PermissionDefinition.register(CREATE, "Create", "", CLOCK));
    }

    @Test
    void roundTripWithPermissions() {
        Role role = Role.create(RoleId.generate(), "Admin", "desc", CLOCK)
                .grantPermission(VIEW, CLOCK)
                .grantPermission(CREATE, CLOCK);
        Role saved = roleRepository.save(role);
        Role loaded = roleRepository.findById(saved.id()).orElseThrow();
        assertEquals("Admin", loaded.name());
        assertEquals(Set.of(VIEW, CREATE), loaded.permissions());
    }

    @Test
    void permissionSetReplacedOnUpdate() {
        Role created = roleRepository.save(
                Role.create(RoleId.generate(), "R", "", CLOCK).grantPermission(VIEW, CLOCK));
        Role updated = roleRepository.save(
                Role.rehydrate(
                                created.id(),
                                created.name(),
                                created.description(),
                                Set.of(CREATE),
                                created.version(),
                                created.createdAt(),
                                created.updatedAt())
                        .withDescription("changed", CLOCK));
        assertEquals(Set.of(CREATE), roleRepository.findById(updated.id()).orElseThrow().permissions());
        assertEquals(1L, updated.version());
    }

    @Test
    void optimisticLockConflict() {
        Role created = roleRepository.save(Role.create(RoleId.generate(), "R", "", CLOCK));
        roleRepository.save(created.withName("R2", CLOCK));
        Role stale = Role.rehydrate(
                created.id(), "stale", "", Set.of(), 0L, created.createdAt(), created.updatedAt());
        assertThrows(OptimisticLockConflictException.class, () -> roleRepository.save(stale));
        assertTrue(roleRepository.findAll().stream().anyMatch(r -> r.id().equals(created.id())));
    }

    @SpringBootApplication(
            excludeName = {
                "com.tmp.security.SecurityAutoConfiguration",
                "com.tmp.capability.CapabilityEngineAutoConfiguration",
                "com.tmp.document.DocumentEngineAutoConfiguration",
                "com.tmp.core.PlatformCoreAutoConfiguration"
            })
    @Import(com.tmp.infra.db.DatabaseAutoConfiguration.class)
    static class TestApplication {
        @Bean
        JdbcTemplate jdbcTemplate(javax.sql.DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
