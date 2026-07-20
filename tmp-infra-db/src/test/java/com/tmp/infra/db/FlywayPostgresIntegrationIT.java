package com.tmp.infra.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = InfraDbTestApplication.class)
@ActiveProfiles("test")
class FlywayPostgresIntegrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = PostgresTestcontainersSupport.createPostgreSqlContainer();

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayAppliesBaselineOnPostgreSqlContainer() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class);
        assertNotNull(migrationCount);
        assertTrue(migrationCount >= 1);
    }

    @Test
    void postgresContainerProvidesPlatformSchema() {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'platform'",
                Integer.class);
        assertNotNull(schemaCount);
        assertEquals(1, schemaCount);
    }
}
