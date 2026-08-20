package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.MaterialReferenceDisplayDto;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcMaterialReferenceDisplayReadAdapterIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private JdbcMaterialReferenceDisplayReadAdapter adapter;

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

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM order_management.item_specification_lines");
        jdbc.update("DELETE FROM order_management.item_specifications");
        jdbc.update("DELETE FROM order_management.order_item_revisions");
        jdbc.update("DELETE FROM order_management.order_items");
        jdbc.update("DELETE FROM order_management.orders");
        adapter = new JdbcMaterialReferenceDisplayReadAdapter(jdbc);
    }

    @Test
    void findsDisplayFieldsFromActiveSpecificationLine() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        insertActiveSpecificationLine(
                orderId, orderItemId, "VEKA-103.211", "Профиль VEKA Softline", "Белый", 6000, "шт");

        Optional<MaterialReferenceDisplayDto> found = adapter.findByMaterialCode("VEKA-103.211");

        assertTrue(found.isPresent());
        assertEquals("VEKA-103.211", found.get().article());
        assertEquals("Профиль VEKA Softline", found.get().materialName());
        assertEquals("Белый", found.get().color());
        assertEquals("6000 мм", found.get().size());
        assertEquals("шт", found.get().unitOfMeasure());
    }

    private void insertActiveSpecificationLine(
            UUID orderId,
            UUID orderItemId,
            String materialCode,
            String materialName,
            String color,
            double lengthMm,
            String unit) {
        jdbc.update(
                """
                INSERT INTO order_management.orders
                    (order_id, order_number, customer_name, direction, currency, status, version,
                     created_at, updated_at)
                VALUES (?, 'ORD-1', 'Customer', 'PRIVATE', 'RUB', 'ACTIVE', 0, NOW(), NOW())
                """,
                orderId);
        jdbc.update(
                """
                INSERT INTO order_management.order_items
                    (order_item_id, order_id, product_code, item_name, status, active_revision_number,
                     version, created_at, updated_at)
                VALUES (?, ?, 'P-1', 'Item', 'ACTIVE', 1, 0, NOW(), NOW())
                """,
                orderItemId,
                orderId);
        jdbc.update(
                """
                INSERT INTO order_management.order_item_revisions
                    (order_item_id, revision_number, revision_status, ordered_quantity)
                VALUES (?, 1, 'ACTIVE', 1)
                """,
                orderItemId);
        jdbc.update(
                """
                INSERT INTO order_management.item_specifications
                    (order_item_id, revision_number, immutable, specification_id)
                VALUES (?, 1, TRUE, md5(?::text || ':1')::uuid)
                """,
                orderItemId,
                orderItemId);
        jdbc.update(
                """
                INSERT INTO order_management.item_specification_lines
                    (order_item_id, revision_number, line_number, material_code, material_name, color,
                     length_mm, line_quantity, unit_of_measure)
                VALUES (?, 1, 1, ?, ?, ?, ?, 1, ?)
                """,
                orderItemId,
                materialCode,
                materialName,
                color,
                lengthMm,
                unit);
    }
}
