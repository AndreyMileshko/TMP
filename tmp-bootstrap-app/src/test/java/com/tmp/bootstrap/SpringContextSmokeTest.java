package com.tmp.bootstrap;

import com.tmp.core.api.PlatformCore;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_bootstrap_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class SpringContextSmokeTest {

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
                "SELECT COUNT(*) FROM \"flyway_schema_history\"",
                Integer.class);
        assertNotNull(migrationCount);
        assertTrue(migrationCount >= 1, "Flyway must apply at least one baseline migration");
    }

    @Test
    void baselineMigrationCreatesPlatformSchema() {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = 'PLATFORM'",
                Integer.class);
        assertNotNull(schemaCount);
        assertEquals(1, schemaCount, "Platform schema must exist after baseline migration");
    }
}
