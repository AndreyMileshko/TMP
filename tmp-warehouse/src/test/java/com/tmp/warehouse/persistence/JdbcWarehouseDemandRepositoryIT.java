package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.application.WarehouseDemandAcceptanceService;
import com.tmp.warehouse.application.WarehouseDemandAcceptanceService.AcceptWarehouseDemandCommand;
import com.tmp.warehouse.application.WarehouseDemandAcceptanceService.AcceptWarehouseDemandLineDraft;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.UnitOfMeasure;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandLine;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseDemandWaitingReason;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcWarehouseDemandRepositoryIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-05T10:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private WarehouseDemandRepository demands;
    private WarehouseDemandAcceptanceService acceptance;
    private WarehouseId destinationWarehouseId;
    private WarehouseId sourceWarehouseId;

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
        jdbc.update("DELETE FROM warehouse.warehouse_demand_transfer_links");
        jdbc.update("DELETE FROM warehouse.warehouse_demand_lines");
        jdbc.update("DELETE FROM warehouse.warehouse_demands");
        jdbc.update("DELETE FROM warehouse.transfer_document_lines");
        jdbc.update("DELETE FROM warehouse.transfer_document_payload");
        jdbc.update("DELETE FROM warehouse.warehouse_user_responsibility");
        jdbc.update("DELETE FROM warehouse.storage_cells");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.warehouses");
        jdbc.update("DELETE FROM warehouse.material_references");

        demands = new JdbcWarehouseDemandRepository(jdbc);
        acceptance = new WarehouseDemandAcceptanceService(demands, CLOCK);

        JdbcWarehouseCatalogRepository catalog = new JdbcWarehouseCatalogRepository(jdbc, CLOCK);
        destinationWarehouseId = WarehouseId.generate();
        sourceWarehouseId = WarehouseId.generate();
        catalog.insert(Warehouse.create(destinationWarehouseId, "DST", "Destination"));
        catalog.insert(Warehouse.create(sourceWarehouseId, "SRC", "Source"));
    }

    @Test
    void persistHeaderAndLinesAtomicallyAndReloadSnapshot() {
        UUID sourceMrId = UUID.randomUUID();
        UUID sourceLine1 = UUID.randomUUID();
        UUID sourceLine2 = UUID.randomUUID();
        BigDecimal length = new BigDecimal("1800.000000");

        WarehouseDemand accepted =
                acceptance.accept(
                        new AcceptWarehouseDemandCommand(
                                sourceMrId,
                                destinationWarehouseId,
                                "operator",
                                List.of(
                                        new AcceptWarehouseDemandLineDraft(
                                                sourceLine1,
                                                "ART-1",
                                                "Profile A",
                                                "White",
                                                "m",
                                                length,
                                                new BigDecimal("3.500000"),
                                                null,
                                                WarehouseDemandWaitingReason.MATERIAL_UNMATCHED),
                                        new AcceptWarehouseDemandLineDraft(
                                                sourceLine2,
                                                "ART-2",
                                                null,
                                                "",
                                                "pcs",
                                                null,
                                                BigDecimal.TEN,
                                                null,
                                                null))));

        WarehouseDemand loaded = demands.findById(accepted.id()).orElseThrow();
        assertEquals(sourceMrId, loaded.sourceMaterialRequirementId());
        assertEquals(destinationWarehouseId, loaded.destinationWarehouseId());
        assertEquals(CLOCK.instant(), loaded.acceptedAt());
        assertEquals("operator", loaded.acceptedBy().orElseThrow());
        assertTrue(loaded.cancelledAt().isEmpty());
        assertEquals(0L, loaded.version());
        assertEquals(2, loaded.lines().size());

        WarehouseDemandLine first =
                loaded.lines().stream()
                        .filter(l -> l.sourceMaterialRequirementLineId().equals(sourceLine1))
                        .findFirst()
                        .orElseThrow();
        assertEquals("ART-1", first.materialCode());
        assertEquals("Profile A", first.materialName());
        assertEquals("White", first.color());
        assertEquals("m", first.unitOfMeasure());
        assertEquals(0, length.compareTo(first.lengthMm().orElseThrow()));
        assertEquals(0, new BigDecimal("3.500000").compareTo(first.requiredQuantity().value()));
        assertTrue(first.materialReferenceId().isEmpty());
        assertEquals(
                WarehouseDemandWaitingReason.MATERIAL_UNMATCHED,
                first.waitingReason().orElseThrow());

        WarehouseDemandLine second =
                loaded.lines().stream()
                        .filter(l -> l.sourceMaterialRequirementLineId().equals(sourceLine2))
                        .findFirst()
                        .orElseThrow();
        assertEquals(null, second.materialName());
        assertTrue(second.lengthMm().isEmpty());
        assertTrue(second.waitingReason().isEmpty());
    }

    @Test
    void materialReferenceIdNullPersistsAndNullableLengthWaitingReason() {
        MaterialReference material =
                new JdbcMaterialReferenceRepository(jdbc, CLOCK)
                        .create(
                                MaterialReference.create(
                                        "ART-X", "Name", "Red", "", UnitOfMeasure.METERS.code()));

        WarehouseDemand accepted =
                acceptance.accept(
                        new AcceptWarehouseDemandCommand(
                                UUID.randomUUID(),
                                destinationWarehouseId,
                                null,
                                List.of(
                                        new AcceptWarehouseDemandLineDraft(
                                                UUID.randomUUID(),
                                                "ART-X",
                                                "Name",
                                                "Red",
                                                "m",
                                                null,
                                                BigDecimal.ONE,
                                                material.id(),
                                                WarehouseDemandWaitingReason.ROUTING_DEFERRED))));

        WarehouseDemandLine line = demands.findById(accepted.id()).orElseThrow().lines().get(0);
        assertEquals(material.id(), line.materialReferenceId().orElseThrow());
        assertTrue(line.lengthMm().isEmpty());
        assertEquals(
                WarehouseDemandWaitingReason.ROUTING_DEFERRED, line.waitingReason().orElseThrow());
    }

    @Test
    void sourceMaterialRequirementIdUnique() {
        UUID sourceMrId = UUID.randomUUID();
        AcceptWarehouseDemandCommand command =
                singleLineCommand(sourceMrId, UUID.randomUUID(), "ART-1", BigDecimal.ONE);
        demands.insert(
                WarehouseDemand.accept(
                        sourceMrId,
                        destinationWarehouseId,
                        CLOCK.instant(),
                        null,
                        List.of(
                                WarehouseDemandLine.create(
                                        command.lines().get(0).sourceMaterialRequirementLineId(),
                                        "ART-1",
                                        "A",
                                        "",
                                        "m",
                                        null,
                                        StockQuantity.of(BigDecimal.ONE),
                                        null,
                                        null))));

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        demands.insert(
                                WarehouseDemand.accept(
                                        sourceMrId,
                                        destinationWarehouseId,
                                        CLOCK.instant(),
                                        null,
                                        List.of(
                                                WarehouseDemandLine.create(
                                                        UUID.randomUUID(),
                                                        "ART-2",
                                                        "B",
                                                        "",
                                                        "pcs",
                                                        null,
                                                        StockQuantity.of(BigDecimal.TEN),
                                                        null,
                                                        null)))));
    }

    @Test
    void duplicateSourceLineWithinDemandRejectedByDb() {
        UUID demandId = UUID.randomUUID();
        UUID sourceLineId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_demands (
                    id, source_material_requirement_id, destination_warehouse_id,
                    accepted_at, accepted_by, cancelled_at, cancelled_by, version)
                VALUES (?, ?, ?, ?, NULL, NULL, NULL, 0)
                """,
                demandId,
                UUID.randomUUID(),
                destinationWarehouseId.value(),
                java.sql.Timestamp.from(CLOCK.instant()));
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_demand_lines (
                    id, demand_id, source_material_requirement_line_id,
                    material_code, material_name, color, unit_of_measure, length_mm,
                    required_quantity, material_reference_id, waiting_reason)
                VALUES (?, ?, ?, 'ART-1', 'A', '', 'm', NULL, 1, NULL, NULL)
                """,
                UUID.randomUUID(),
                demandId,
                sourceLineId);
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO warehouse.warehouse_demand_lines (
                                    id, demand_id, source_material_requirement_line_id,
                                    material_code, material_name, color, unit_of_measure, length_mm,
                                    required_quantity, material_reference_id, waiting_reason)
                                VALUES (?, ?, ?, 'ART-2', 'B', '', 'pcs', NULL, 2, NULL, NULL)
                                """,
                                UUID.randomUUID(),
                                demandId,
                                sourceLineId));
    }

    @Test
    void acceptSameSourceRequirementTwiceIsIdempotent() {
        UUID sourceMrId = UUID.randomUUID();
        UUID sourceLineId = UUID.randomUUID();
        AcceptWarehouseDemandCommand command =
                singleLineCommand(sourceMrId, sourceLineId, "ART-1", BigDecimal.ONE);

        WarehouseDemand first = acceptance.accept(command);
        WarehouseDemand second = acceptance.accept(command);

        assertEquals(first.id(), second.id());
        Integer demandCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_demands WHERE source_material_requirement_id = ?",
                        Integer.class,
                        sourceMrId);
        Integer lineCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_demand_lines WHERE demand_id = ?",
                        Integer.class,
                        first.id().value());
        assertEquals(1, demandCount);
        assertEquals(1, lineCount);
    }

    @Test
    void demandLineMayHaveMultipleTransferLinksAndDuplicateTransferLineRejected() {
        WarehouseDemand demand =
                acceptance.accept(
                        singleLineCommand(
                                UUID.randomUUID(), UUID.randomUUID(), "ART-1", BigDecimal.TEN));
        WarehouseDemandLine demandLine = demand.lines().get(0);

        MaterialReferenceId materialId =
                new JdbcMaterialReferenceRepository(jdbc, CLOCK)
                        .create(
                                MaterialReference.create(
                                        "ART-T",
                                        "Transfer Mat",
                                        "",
                                        "",
                                        UnitOfMeasure.METERS.code()))
                        .id();

        UUID document1 = UUID.randomUUID();
        UUID document2 = UUID.randomUUID();
        WarehouseTransferLineId transferLine1 = WarehouseTransferLineId.generate();
        WarehouseTransferLineId transferLine2 = WarehouseTransferLineId.generate();
        insertTransferFixture(document1, transferLine1, materialId, new BigDecimal("4"));
        insertTransferFixture(document2, transferLine2, materialId, new BigDecimal("6"));

        demands.insertTransferLink(
                WarehouseDemandTransferLink.create(
                        demandLine.id(),
                        document1,
                        transferLine1,
                        StockQuantity.of(new BigDecimal("4"))));
        demands.insertTransferLink(
                WarehouseDemandTransferLink.create(
                        demandLine.id(),
                        document2,
                        transferLine2,
                        StockQuantity.of(new BigDecimal("6"))));

        List<WarehouseDemandTransferLink> links =
                demands.findTransferLinksByDemandLineId(demandLine.id());
        assertEquals(2, links.size());

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        demands.insertTransferLink(
                                WarehouseDemandTransferLink.create(
                                        demandLine.id(),
                                        document1,
                                        transferLine1,
                                        StockQuantity.of(new BigDecimal("1")))));

        Integer linkCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM warehouse.warehouse_demand_transfer_links",
                        Integer.class);
        assertEquals(2, linkCount);
    }

    @Test
    void flywayRecordsV50DemandMigration() {
        Integer applied =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '50' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied);

        Integer tables =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'warehouse'
                          AND table_name IN (
                              'warehouse_demands',
                              'warehouse_demand_lines',
                              'warehouse_demand_transfer_links')
                        """,
                        Integer.class);
        assertEquals(3, tables);

        Integer productionFk =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                          FROM information_schema.table_constraints tc
                          JOIN information_schema.constraint_column_usage ccu
                            ON tc.constraint_name = ccu.constraint_name
                           AND tc.constraint_schema = ccu.constraint_schema
                         WHERE tc.constraint_schema = 'warehouse'
                           AND tc.table_name LIKE 'warehouse_demand%'
                           AND tc.constraint_type = 'FOREIGN KEY'
                           AND ccu.table_schema = 'production'
                        """,
                        Integer.class);
        assertEquals(0, productionFk);
    }

    private AcceptWarehouseDemandCommand singleLineCommand(
            UUID sourceMrId, UUID sourceLineId, String article, BigDecimal quantity) {
        return new AcceptWarehouseDemandCommand(
                sourceMrId,
                destinationWarehouseId,
                null,
                List.of(
                        new AcceptWarehouseDemandLineDraft(
                                sourceLineId,
                                article,
                                "Name",
                                "",
                                "m",
                                null,
                                quantity,
                                null,
                                null)));
    }

    private void insertTransferFixture(
            UUID documentId,
            WarehouseTransferLineId transferLineId,
            MaterialReferenceId materialId,
            BigDecimal quantity) {
        Instant now = CLOCK.instant();
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_payload (
                    document_id, source_warehouse_id, destination_warehouse_id,
                    payload_schema_version, payload_revision, created_at, updated_at)
                VALUES (?, ?, ?, 1, 0, ?, ?)
                """,
                documentId,
                sourceWarehouseId.value(),
                destinationWarehouseId.value(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now));
        jdbc.update(
                """
                INSERT INTO warehouse.transfer_document_lines (
                    id, document_id, material_reference_id, quantity, line_order)
                VALUES (?, ?, ?, ?, 1)
                """,
                transferLineId.value(),
                documentId,
                materialId.value(),
                quantity);
    }
}
