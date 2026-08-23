package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.ProductionReleaseImmutableException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
class JdbcProductionReleaseRepositoryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T14:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-21T14:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private JdbcProductionReleaseRepository repository;

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
        jdbc.update("DELETE FROM production.production_release_material_lines");
        jdbc.update("DELETE FROM production.production_release_item_lines");
        jdbc.update("DELETE FROM production.production_releases");
        repository = new JdbcProductionReleaseRepository(jdbc, CLOCK);
    }

    @Test
    void roundTripPreservesHeaderItemAndMaterialPlanFact() {
        UUID documentId = UUID.randomUUID();
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        MaterialReferenceId material = MaterialReferenceId.generate();
        CuttingPlanId cuttingPlanId = CuttingPlanId.generate();

        ProductionRelease draft =
                ProductionRelease.draft(
                        documentId,
                        orderId,
                        T0,
                        List.of(
                                new ProductionRelease.ItemLine(
                                        itemId, specId, ProductionQuantity.positive(3))),
                        List.of(
                                new ProductionRelease.MaterialLine(
                                        material,
                                        BigDecimal.TEN,
                                        BigDecimal.valueOf(8),
                                        MaterialPlanningSource.SPECIFICATION,
                                        Optional.of(cuttingPlanId),
                                        Optional.of(itemId),
                                        Optional.of("short"))));
        repository.saveDraft(draft);

        JdbcProductionReleaseRepository reloadedRepo =
                new JdbcProductionReleaseRepository(jdbc, CLOCK);
        ProductionRelease loaded = reloadedRepo.findByDocumentId(documentId).orElseThrow();

        assertEquals(orderId, loaded.sourceOrderId());
        assertEquals(T0, loaded.releasedAt());
        assertEquals(1, loaded.itemLines().size());
        assertEquals(itemId, loaded.itemLines().getFirst().sourceOrderItemId());
        assertEquals(specId, loaded.itemLines().getFirst().specificationId());
        assertEquals(ProductionQuantity.positive(3), loaded.itemLines().getFirst().releaseQuantity());
        assertEquals(1, loaded.materialLines().size());
        ProductionRelease.MaterialLine materialLine = loaded.materialLines().getFirst();
        assertEquals(material, materialLine.materialReferenceId());
        assertEquals(0, BigDecimal.TEN.compareTo(materialLine.plannedQuantity()));
        assertEquals(0, BigDecimal.valueOf(8).compareTo(materialLine.actualQuantity()));
        assertEquals(MaterialPlanningSource.SPECIFICATION, materialLine.planningSource());
        assertEquals(Optional.of(cuttingPlanId), materialLine.cuttingPlanId());
        assertEquals(Optional.of(itemId), materialLine.sourceOrderItemId());
        assertEquals(Optional.of("short"), materialLine.comment());
    }

    @Test
    void planFactVariantsPersistExactly() {
        UUID documentId = UUID.randomUUID();
        ProductionRelease draft =
                ProductionRelease.draft(
                        documentId,
                        SourceOrderId.generate(),
                        T0,
                        List.of(
                                new ProductionRelease.ItemLine(
                                        SourceOrderItemId.generate(),
                                        SpecificationId.generate(),
                                        ProductionQuantity.positive(1))),
                        List.of(
                                material(10, 8),
                                material(10, 10),
                                material(10, 12),
                                material(4, 0)));
        repository.saveDraft(draft);
        ProductionRelease loaded = repository.findByDocumentId(documentId).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(8).compareTo(loaded.materialLines().get(0).actualQuantity()));
        assertEquals(0, BigDecimal.TEN.compareTo(loaded.materialLines().get(1).actualQuantity()));
        assertEquals(0, BigDecimal.valueOf(12).compareTo(loaded.materialLines().get(2).actualQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(loaded.materialLines().get(3).actualQuantity()));
    }

    @Test
    void postedReleaseRejectsDraftUpdate() {
        UUID documentId = UUID.randomUUID();
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        repository.saveDraft(
                ProductionRelease.draft(
                        documentId,
                        orderId,
                        T0,
                        List.of(
                                new ProductionRelease.ItemLine(
                                        itemId, specId, ProductionQuantity.positive(2))),
                        List.of()));
        repository.markPosted(
                repository.findByDocumentId(documentId).orElseThrow().markPosted());

        assertThrows(
                ProductionReleaseImmutableException.class,
                () ->
                        repository.saveDraft(
                                ProductionRelease.draft(
                                        documentId,
                                        orderId,
                                        T0,
                                        List.of(
                                                new ProductionRelease.ItemLine(
                                                        itemId,
                                                        specId,
                                                        ProductionQuantity.positive(1))),
                                        List.of(
                                                material(1, 1)))));
        ProductionRelease loaded = repository.findByDocumentId(documentId).orElseThrow();
        assertTrue(loaded.posted());
        assertEquals(ProductionQuantity.positive(2), loaded.itemLines().getFirst().releaseQuantity());
        assertTrue(loaded.materialLines().isEmpty());
    }

    @Test
    void duplicateItemLineConstraintEnforced() {
        UUID documentId = UUID.randomUUID();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        repository.saveDraft(
                ProductionRelease.draft(
                        documentId,
                        SourceOrderId.generate(),
                        T0,
                        List.of(
                                new ProductionRelease.ItemLine(
                                        itemId,
                                        SpecificationId.generate(),
                                        ProductionQuantity.positive(1))),
                        List.of()));
        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO production.production_release_item_lines
                                    (id, document_id, source_order_item_id, specification_id,
                                     release_quantity, line_order)
                                VALUES (?, ?, ?, ?, ?, ?)
                                """,
                                UUID.randomUUID(),
                                documentId,
                                itemId.value(),
                                UUID.randomUUID(),
                                BigDecimal.ONE,
                                99));
    }

    @Test
    void noRevisionColumnsOnReleaseTables() {
        Integer revisionColumns =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'production'
                          AND table_name LIKE 'production_release%'
                          AND column_name ILIKE '%revision%'
                        """,
                        Integer.class);
        assertEquals(0, revisionColumns);
    }

    @Test
    void noCrossCapabilityForeignKeys() {
        Integer foreignFks =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_name = ccu.constraint_name
                         AND tc.constraint_schema = ccu.constraint_schema
                        WHERE tc.table_schema = 'production'
                          AND tc.table_name LIKE 'production_release%'
                          AND tc.constraint_type = 'FOREIGN KEY'
                          AND ccu.table_schema <> 'production'
                        """,
                        Integer.class);
        assertEquals(0, foreignFks);
    }

    private static ProductionRelease.MaterialLine material(long plan, long fact) {
        return new ProductionRelease.MaterialLine(
                MaterialReferenceId.generate(),
                BigDecimal.valueOf(plan),
                BigDecimal.valueOf(fact),
                MaterialPlanningSource.SPECIFICATION,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
