package com.tmp.bootstrap;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
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
@SpringBootTest(classes = TmpBootstrapApplication.class)
@ActiveProfiles("test")
class TmpBootstrapPostgresIntegrationIT {

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
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void bootstrapApplicationWiresDatabaseInfrastructure() throws Exception {
        assertNotNull(dataSource, "TmpBootstrapApplication must expose DataSource");
        assertNotNull(jdbcTemplate, "TmpBootstrapApplication must expose JdbcTemplate");
        assertNotNull(flyway, "TmpBootstrapApplication must expose Flyway");

        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2), "PostgreSQL DataSource must be reachable");
        }
    }

    @Test
    void flywayAppliesBaselineOnPostgreSql() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class);
        assertNotNull(migrationCount);
        assertTrue(migrationCount >= 1, "Flyway must record baseline migration in schema history");
    }

    @Test
    void baselineMigrationCreatesPlatformSchemaOnPostgreSql() {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'platform'",
                Integer.class);
        assertNotNull(schemaCount);
        assertEquals(1, schemaCount, "Platform schema must exist after Flyway baseline");
    }
}
