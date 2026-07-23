package com.tmp.bootstrap;

import com.tmp.core.api.PlatformCore;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class SpringContextSmokeTest extends AbstractBootstrapPostgresSpringTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private PlatformCore platformCore;

    @Test
    void contextLoadsPlatformCore() {
        assertNotNull(platformCore, "PlatformCore must be configured in bootstrap context");
        assertNotNull(platformCore.eventBus(), "EventBus must be available through PlatformCore");
        assertNotNull(platformCore.platformRegistry(), "PlatformRegistry must be available through PlatformCore");
        assertEquals("TOP Manufacturing Platform", platformCore.status().platformName());
    }

    @Test
    void contextLoadsDatabaseInfrastructure() throws Exception {
        assertNotNull(dataSource, "DataSource must be configured in bootstrap context");
        assertNotNull(jdbcTemplate, "JdbcTemplate must be available in bootstrap context");
        assertNotNull(flyway, "Flyway must be configured in bootstrap context");

        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2), "DataSource must provide a valid connection");
        }
    }

    @Test
    void flywayAppliesBaselineMigration() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history",
                Integer.class);
        assertNotNull(migrationCount);
        assertTrue(migrationCount >= 4, "Flyway must apply V1..V4 migrations");
    }

    @Test
    void baselineMigrationCreatesPlatformSchema() {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'platform'",
                Integer.class);
        assertNotNull(schemaCount);
        assertEquals(1, schemaCount, "Platform schema must exist after baseline migration");
    }
}
