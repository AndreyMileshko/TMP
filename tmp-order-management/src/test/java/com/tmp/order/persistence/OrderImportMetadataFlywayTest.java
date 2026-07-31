package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies {@code V12__order_import_metadata.sql}. */
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
    void cleanDatabaseMigratesToV12WithoutJsonOrBytea() throws Exception {
        assertEquals(
                "12",
                jdbc.queryForObject(
                        "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                        String.class));
        assertTrue(tableExists(POSTGRES, "order_import_metadata"));
        assertFalse(hasJsonOrByteaColumn(POSTGRES, "order_import_metadata"));
    }

    @Test
    void v11DatabaseUpgradesToV12PreservingOrdersAndEnforcingUniqueChecksum() throws Exception {
        try (PostgreSQLContainer<?> upgradeContainer = new PostgreSQLContainer<>("postgres:16-alpine")) {
            upgradeContainer.start();
            JdbcTemplate upgradeJdbc = new JdbcTemplate(dataSource(upgradeContainer));

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .target("11")
                    .load()
                    .migrate();

            UUID orderId = UUID.randomUUID();
            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.orders (
                        order_id, order_number, customer_ref, customer_name, contract_ref, site_ref,
                        responsible_manager, direction, currency, status, version, created_at, updated_at
                    ) VALUES (?, 'V11-KEEP', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', 0,
                              NOW(), NOW())
                    """,
                    orderId);

            Flyway.configure()
                    .dataSource(
                            upgradeContainer.getJdbcUrl(),
                            upgradeContainer.getUsername(),
                            upgradeContainer.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            assertEquals(
                    1,
                    upgradeJdbc.queryForObject(
                            "SELECT COUNT(*) FROM order_management.orders WHERE order_number = 'V11-KEEP'",
                            Integer.class));
            assertTrue(tableExists(upgradeContainer, "order_import_metadata"));
            assertFalse(hasJsonOrByteaColumn(upgradeContainer, "order_import_metadata"));

            UUID userId = UUID.randomUUID();
            upgradeJdbc.update(
                    """
                    INSERT INTO order_management.order_import_metadata (
                        import_id, source_type, source_reference, content_checksum,
                        imported_at, imported_by, order_id
                    ) VALUES (?, 'STXT', 'a.stxt', 'same-checksum', NOW(), ?, ?)
                    """,
                    UUID.randomUUID(),
                    userId,
                    orderId);

            assertThrows(
                    DuplicateKeyException.class,
                    () ->
                            upgradeJdbc.update(
                                    """
                                    INSERT INTO order_management.order_import_metadata (
                                        import_id, source_type, source_reference, content_checksum,
                                        imported_at, imported_by, order_id
                                    ) VALUES (?, 'STXT', 'b.stxt', 'same-checksum', NOW(), ?, ?)
                                    """,
                                    UUID.randomUUID(),
                                    userId,
                                    orderId));
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

    private static boolean hasJsonOrByteaColumn(PostgreSQLContainer<?> container, String table)
            throws Exception {
        Set<String> forbidden = new HashSet<>();
        try (Connection connection = dataSource(container).getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(null, "order_management", table, null)) {
                while (rs.next()) {
                    String type = rs.getString("TYPE_NAME");
                    if (type != null) {
                        String normalized = type.toLowerCase();
                        if (normalized.contains("json") || normalized.contains("bytea")) {
                            forbidden.add(rs.getString("COLUMN_NAME") + ":" + type);
                        }
                    }
                }
            }
        }
        return !forbidden.isEmpty();
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
