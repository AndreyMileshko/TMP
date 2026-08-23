package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.document.ProductionReleaseProcessor;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.ProductionReleaseValidationException;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.production.persistence.JdbcProductionReleaseRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PostgreSQL durability and transaction proofs for Production Release (STAGE7-012).
 */
@Testcontainers
class ProductionReleasePostgresIntegrationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T18:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-21T18:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-21T19:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager txManager;

    private JdbcProductionItemStateRepository itemRepository;
    private JdbcProductionReleaseRepository releaseRepository;
    private ProductionReleaseProcessor processor;
    private TransactionTemplate tx;

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
        txManager = new DataSourceTransactionManager(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM production.production_release_material_lines");
        jdbc.update("DELETE FROM production.production_release_item_lines");
        jdbc.update("DELETE FROM production.production_releases");
        jdbc.update("DELETE FROM production.production_item_cutting_plan_links");
        jdbc.update("DELETE FROM production.production_item_states");
        itemRepository = new JdbcProductionItemStateRepository(jdbc, CLOCK);
        releaseRepository = new JdbcProductionReleaseRepository(jdbc, CLOCK);
        processor = new ProductionReleaseProcessor(releaseRepository, itemRepository);
        tx = new TransactionTemplate(txManager);
    }

    @Test
    void postedReleaseSurvivesNewRepositoryInstance() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        itemRepository.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(10),
                        T0));

        UUID documentId = UUID.randomUUID();
        releaseRepository.saveDraft(
                ProductionRelease.draft(
                        documentId,
                        orderId,
                        T1,
                        List.of(
                                new ProductionRelease.ItemLine(
                                        itemId, specId, ProductionQuantity.positive(10))),
                        List.of()));

        tx.executeWithoutResult(
                status ->
                        processor.onPost(
                                () ->
                                        new com.tmp.document.api.DocumentMetadata(
                                                documentId,
                                                ProductionReleaseProcessor.DOCUMENT_TYPE_ID,
                                                "PR-IT",
                                                "release",
                                                com.tmp.document.api.DocumentStatus.DRAFT,
                                                0L,
                                                T1,
                                                T1,
                                                null,
                                                null)));

        JdbcProductionReleaseRepository freshRelease =
                new JdbcProductionReleaseRepository(jdbc, CLOCK);
        JdbcProductionItemStateRepository freshItems =
                new JdbcProductionItemStateRepository(jdbc, CLOCK);

        ProductionRelease loaded = freshRelease.findByDocumentId(documentId).orElseThrow();
        assertTrue(loaded.posted());
        assertEquals(ProductionQuantity.positive(10), loaded.itemLines().getFirst().releaseQuantity());

        ProductionItemState state =
                freshItems.findByIdentity(orderId, itemId, specId).orElseThrow();
        assertEquals(ProductionStatus.RELEASED, state.status());
        assertEquals(ProductionQuantity.zero(), state.activeProductionQuantity());
    }

    @Test
    void failedPostRollsBackItemStateAndPostedFlag() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();
        itemRepository.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemA, specA, T0),
                        ProductionQuantity.positive(10),
                        T0));
        itemRepository.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemB, specB, T0),
                        ProductionQuantity.positive(3),
                        T0));

        UUID documentId = UUID.randomUUID();
        releaseRepository.saveDraft(
                ProductionRelease.draft(
                        documentId,
                        orderId,
                        T1,
                        List.of(
                                new ProductionRelease.ItemLine(
                                        itemA, specA, ProductionQuantity.positive(2)),
                                new ProductionRelease.ItemLine(
                                        itemB, specB, ProductionQuantity.positive(4))),
                        List.of()));

        assertThrows(
                ProductionReleaseValidationException.class,
                () ->
                        tx.executeWithoutResult(
                                status ->
                                        processor.onPost(
                                                () ->
                                                        new com.tmp.document.api.DocumentMetadata(
                                                                documentId,
                                                                ProductionReleaseProcessor
                                                                        .DOCUMENT_TYPE_ID,
                                                                "PR-IT",
                                                                "release",
                                                                com.tmp.document.api.DocumentStatus
                                                                        .DRAFT,
                                                                0L,
                                                                T1,
                                                                T1,
                                                                null,
                                                                null))));

        assertEquals(
                ProductionStatus.IN_PRODUCTION,
                itemRepository.findByIdentity(orderId, itemA, specA).orElseThrow().status());
        assertEquals(
                ProductionStatus.IN_PRODUCTION,
                itemRepository.findByIdentity(orderId, itemB, specB).orElseThrow().status());
        assertTrue(releaseRepository.findByDocumentId(documentId).orElseThrow().posted() == false);
    }
}
