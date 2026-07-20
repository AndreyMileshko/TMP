package com.tmp.infra.db;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = InfraDbTestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_flyway_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class FlywayBaselineIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void baselineMigrationCreatesSchemaHistory() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM \"flyway_schema_history\"",
                Integer.class);
        assertNotNull(migrationCount);
        assertTrue(migrationCount >= 1, "Flyway must apply at least one baseline migration");
    }

    @Test
    void baselineMigrationCreatesPlatformSchema() {
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'PLATFORM'",
                Integer.class);
        assertNotNull(schemaCount);
        assertEquals(1, schemaCount, "Platform schema must exist after baseline migration");
    }
}
