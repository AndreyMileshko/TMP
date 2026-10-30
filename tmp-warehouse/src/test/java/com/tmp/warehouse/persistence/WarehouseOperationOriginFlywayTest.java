package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies V55 adds nullable consumption origin columns without backfill. */
@Testcontainers
class WarehouseOperationOriginFlywayTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
    }

    @Test
    void v55AddsNullableOriginColumnsOnWarehouseOperations() {
        Integer applied =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '55' AND success",
                        Integer.class);
        assertEquals(1, applied);

        assertTrue(columnExists("origin_document_type"));
        assertTrue(columnExists("origin_document_id"));
        assertTrue(columnExists("origin_document_number"));
        assertTrue(columnExists("origin_order_numbers"));

        Integer notNullCount =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'warehouse'
                          AND table_name = 'warehouse_operations'
                          AND column_name IN (
                            'origin_document_type',
                            'origin_document_id',
                            'origin_document_number',
                            'origin_order_numbers')
                          AND is_nullable = 'NO'
                        """,
                        Integer.class);
        assertEquals(0, notNullCount);
    }

    private static boolean columnExists(String column) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'warehouse'
                          AND table_name = 'warehouse_operations'
                          AND column_name = ?
                        """,
                        Integer.class,
                        column);
        return count != null && count == 1;
    }
}
