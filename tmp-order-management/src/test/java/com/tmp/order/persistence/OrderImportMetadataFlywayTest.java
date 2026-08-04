package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies {@code V13__order_lifecycle_active.sql} drops import-metadata table. */
@Testcontainers
class OrderImportMetadataFlywayTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateClean() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        jdbc = new JdbcTemplate(dataSource(POSTGRES));
    }

    @Test
    void cleanDatabaseMigratesToV13AndDropsImportMetadataTable() throws Exception {
        assertEquals(
                "13",
                jdbc.queryForObject(
                        "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                        String.class));
        assertFalse(tableExists(POSTGRES, "order_import_metadata"));
    }

    @Test
    void v12DatabaseUpgradesToV13DroppingImportMetadata() throws Exception {
        try (PostgreSQLContainer<?> upgradeContainer = new PostgreSQLContainer<>("postgres:16-alpine")) {
            upgradeContainer.start();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource(upgradeContainer));

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .target("12")
                    .load()
                    .migrate();

            assertTrue(tableExists(upgradeContainer, "order_import_metadata"));

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertEquals(
                    "13",
                    upgradeJdbc.queryForObject(
                            "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                            String.class));
            assertFalse(tableExists(upgradeContainer, "order_import_metadata"));
        }
    }

    private static boolean tableExists(PostgreSQLContainer<?> container, String table)
            throws Exception {
        try (Connection connection = dataSource(container).getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(null, "order_management", table, null)) {
                return rs.next();
            }
        }
    }

    private static DataSource dataSource(PostgreSQLContainer<?> container) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }
}
