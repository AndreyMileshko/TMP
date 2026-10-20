package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies V54 adds nullable ready_date without backfill or defaults. */
@Testcontainers
class OrderReadyDateFlywayTest {

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
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void v54AddsNullableReadyDateOnOrdersAndCreatePayload() {
        assertEquals(
                1L,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '54' AND success",
                        Long.class));
        assertEquals(
                "YES",
                jdbc.queryForObject(
                        """
                        SELECT is_nullable FROM information_schema.columns
                         WHERE table_schema = 'order_management'
                           AND table_name = 'orders'
                           AND column_name = 'ready_date'
                        """,
                        String.class));
        assertEquals(
                "date",
                jdbc.queryForObject(
                        """
                        SELECT data_type FROM information_schema.columns
                         WHERE table_schema = 'order_management'
                           AND table_name = 'orders'
                           AND column_name = 'ready_date'
                        """,
                        String.class));
        assertEquals(
                "YES",
                jdbc.queryForObject(
                        """
                        SELECT is_nullable FROM information_schema.columns
                         WHERE table_schema = 'order_management'
                           AND table_name = 'order_create_payload'
                           AND column_name = 'ready_date'
                        """,
                        String.class));
    }

    @Test
    void existingOrderInsertWithoutReadyDateRemainsNullAndCreatedAtUnchanged() {
        UUID orderId = UUID.randomUUID();
        Instant created = Instant.parse("2026-10-19T09:00:00Z");
        Timestamp ts = Timestamp.from(created);
        jdbc.update(
                """
                INSERT INTO order_management.orders
                  (order_id, order_number, customer_ref, customer_name, contract_ref, site_ref,
                   responsible_manager, direction, currency, status, version, ready_date,
                   created_at, updated_at)
                VALUES (?, 'RD-NULL-1', NULL, 'Customer', NULL, NULL, NULL, 'PRIVATE', 'RUB',
                        'DRAFT', 0, NULL, ?, ?)
                """,
                orderId,
                ts,
                ts);
        LocalDate ready =
                jdbc.queryForObject(
                        "SELECT ready_date FROM order_management.orders WHERE order_id = ?",
                        LocalDate.class,
                        orderId);
        Instant loadedCreated =
                jdbc.queryForObject(
                                "SELECT created_at FROM order_management.orders WHERE order_id = ?",
                                Timestamp.class,
                                orderId)
                        .toInstant();
        assertNull(ready);
        assertEquals(created, loadedCreated);
        assertTrue(
                jdbc.queryForObject(
                                """
                                SELECT COUNT(*) FROM information_schema.columns
                                 WHERE table_schema = 'order_management'
                                   AND table_name = 'orders'
                                   AND column_name = 'ready_date'
                                   AND column_default IS NULL
                                """,
                                Long.class)
                        == 1L);
    }
}