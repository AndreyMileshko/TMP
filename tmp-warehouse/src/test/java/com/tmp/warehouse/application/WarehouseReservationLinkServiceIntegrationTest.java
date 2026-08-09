package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.persistence.JdbcMaterialReservationLinkRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

/**
 * Integration: reservation links persist and read without touching stock or movements.
 */
@Testcontainers
class WarehouseReservationLinkServiceIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-09T13:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private WarehouseReservationLinkService service;

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
        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM warehouse.material_reservation_links");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");

        service =
                new WarehouseReservationLinkService(
                        new JdbcMaterialReservationLinkRepository(jdbc), CLOCK);
    }

    @Test
    void createAndReadPersistsInformationalLinkWithoutStockOrMovementRows() {
        MaterialReference material = MaterialReference.of("VEKA 103.211 WHITE");
        ReservationTargetReference order = ReservationTargetReference.order("26096190");

        MaterialReservationLink created =
                service.createLink(material, order, StockQuantity.of(200L));

        MaterialReservationLink loaded = service.findById(created.id()).orElseThrow();
        assertEquals(created.id(), loaded.id());
        assertEquals(material, loaded.material());
        assertEquals(order, loaded.target());
        assertEquals(StockQuantity.of(200L), loaded.quantity());

        List<MaterialReservationLink> byOrder = service.findByTarget(order);
        assertEquals(1, byOrder.size());
        assertEquals(created.id(), byOrder.get(0).id());

        Integer stockRows =
                jdbc.queryForObject("SELECT COUNT(*) FROM warehouse.stock_positions", Integer.class);
        Integer movementRows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_movements", Integer.class);
        Integer linkRows =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.material_reservation_links", Integer.class);
        assertEquals(0, stockRows);
        assertEquals(0, movementRows);
        assertEquals(1, linkRows);
        assertTrue(linkRows > 0);
    }
}
