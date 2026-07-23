package com.tmp.security.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.PermissionId;
import com.tmp.security.domain.OptimisticLockConflictException;
import com.tmp.security.domain.PermissionDefinition;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
@SpringBootTest(classes = JdbcPermissionDefinitionRepositoryTest.TestApplication.class)
@ActiveProfiles("test")
class JdbcPermissionDefinitionRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-23T03:00:00Z"), ZoneOffset.UTC);
    private static final PermissionId VIEW = PermissionId.of("security.users.view");

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

    private JdbcPermissionDefinitionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcPermissionDefinitionRepository(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM security.role_permissions");
        jdbcTemplate.update("DELETE FROM security.user_permission_overrides");
        jdbcTemplate.update("DELETE FROM security.permission_definitions");
    }

    @Test
    void roundTripAndActiveToggle() {
        PermissionDefinition saved = repository.save(PermissionDefinition.register(VIEW, "View", "d", CLOCK));
        assertTrue(repository.findById(VIEW).orElseThrow().active());
        PermissionDefinition deactivated = repository.save(saved.deactivated());
        assertFalse(deactivated.active());
        assertEquals(1L, deactivated.version());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void optimisticLockConflict() {
        PermissionDefinition created = repository.save(PermissionDefinition.register(VIEW, "View", "", CLOCK));
        repository.save(created.withDisplayName("View2"));
        PermissionDefinition stale = PermissionDefinition.rehydrate(
                VIEW, "stale", "", true, created.registeredAt(), 0L);
        assertThrows(OptimisticLockConflictException.class, () -> repository.save(stale));
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
