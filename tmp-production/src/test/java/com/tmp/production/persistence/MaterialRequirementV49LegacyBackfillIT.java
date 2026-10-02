package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V49 legacy backfill must preserve historical MR provenance without inventing authoritative
 * product coverage for pre-Phase-2 rows.
 */
@Testcontainers
class MaterialRequirementV49LegacyBackfillIT {

    private static final Instant T0 = Instant.parse("2026-09-10T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static MaterialRequirementRepository repository;

    @BeforeAll
    static void connect() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
        repository =
                new JdbcMaterialRequirementRepository(
                        jdbc, CLOCK, new DataSourceTransactionManager(dataSource));
    }

    @Test
    void legacySubmittedBackfillDoesNotCountTowardProductCoverage() {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("48")
                .load()
                .migrate();

        UUID requirementId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();

        jdbc.update(
                """
                INSERT INTO production.production_item_states (
                    id, source_order_id, source_order_item_id, specification_id, status,
                    ordered_quantity, launched_quantity, active_production_quantity,
                    released_quantity, last_status_changed_at, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'IN_PRODUCTION', 10, 10, 10, 0, ?, 0, ?, ?)
                """,
                UUID.randomUUID(),
                orderId,
                itemId,
                UUID.randomUUID(),
                Timestamp.from(T0),
                Timestamp.from(T0),
                Timestamp.from(T0));

        jdbc.update(
                """
                INSERT INTO production.material_requirements (
                    id, source_order_id, destination_warehouse_id,
                    created_at, updated_at, version, status, submitted_at, submitted_by)
                VALUES (?, ?, ?, ?, ?, 1, 'SUBMITTED', ?, 'legacy-user')
                """,
                requirementId,
                orderId,
                warehouseId,
                Timestamp.from(T0),
                Timestamp.from(T0),
                Timestamp.from(T0));

        jdbc.update(
                """
                INSERT INTO production.material_requirement_lines (
                    id, requirement_id, material_reference_id, material_code, material_name,
                    color, unit_of_measure, quantity, line_order)
                VALUES (?, ?, ?, 'MAT-L', 'Legacy', '', 'PCS', ?, 0)
                """,
                lineId,
                requirementId,
                materialId,
                new BigDecimal("4.000000"));

        jdbc.update(
                """
                INSERT INTO production.material_requirement_line_source_items (
                    line_id, source_order_item_id)
                VALUES (?, ?)
                """,
                lineId,
                itemId);

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("49")
                .load()
                .migrate();

        Boolean counts =
                jdbc.queryForObject(
                        """
                        SELECT counts_toward_product_coverage
                        FROM production.material_requirement_source_items
                        WHERE requirement_id = ?
                          AND source_order_id = ?
                          AND source_order_item_id = ?
                        """,
                        Boolean.class,
                        requirementId,
                        orderId,
                        itemId);
        assertFalse(Boolean.TRUE.equals(counts));

        Long placeholderQty =
                jdbc.queryForObject(
                        """
                        SELECT requested_product_quantity
                        FROM production.material_requirement_source_items
                        WHERE requirement_id = ?
                        """,
                        Long.class,
                        requirementId);
        assertEquals(10L, placeholderQty);

        MaterialRequirementSourceItemKey key =
                MaterialRequirementSourceItemKey.of(
                        SourceOrderId.of(orderId), SourceOrderItemId.of(itemId));
        Map<MaterialRequirementSourceItemKey, Long> submitted =
                repository.sumSubmittedProductQuantities(List.of(key));
        assertEquals(0L, submitted.get(key));
    }
}
