package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.ProductionHistoryService;
import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType;
import com.tmp.production.domain.SourceOrderId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
 * PostgreSQL proofs for Production business history store (STAGE7-015A): durability, immutability,
 * order filter, deterministic ordering, and history-failure rollback of ambient business TX.
 */
@Testcontainers
class JdbcProductionHistoryRepositoryPostgresIT {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static PlatformTransactionManager txManager;

    private JdbcProductionHistoryRepository repository;
    private ProductionHistoryService historyService;

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
        DataSource dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
        txManager = new DataSourceTransactionManager(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("TRUNCATE TABLE production.production_history");
        repository = new JdbcProductionHistoryRepository(jdbc);
        historyService = new ProductionHistoryService(repository, CLOCK);
    }

    @Test
    void appendIsDurableAcrossNewRepositoryInstance() {
        SourceOrderId orderId = SourceOrderId.generate();
        UUID documentId = UUID.randomUUID();
        historyService.append(
                historyService.orderAccepted(
                        orderId,
                        documentId,
                        Instant.parse("2026-08-24T11:00:00Z"),
                        List.of(UUID.randomUUID()),
                        Optional.of("operator")));

        JdbcProductionHistoryRepository reloaded = new JdbcProductionHistoryRepository(jdbc);
        List<ProductionHistoryEntry> entries = reloaded.listByOrder(orderId);
        assertEquals(1, entries.size());
        assertEquals(ProductionHistoryType.ORDER_ACCEPTED, entries.getFirst().historyType());
        assertEquals("operator", entries.getFirst().actorRef().orElseThrow());
        assertEquals(documentId, entries.getFirst().businessReferenceId().orElseThrow());
    }

    @Test
    void listByOrderFiltersOtherOrdersAndIsChronological() {
        SourceOrderId orderA = SourceOrderId.generate();
        SourceOrderId orderB = SourceOrderId.generate();
        Instant t1 = Instant.parse("2026-08-24T01:00:00Z");
        Instant t2 = Instant.parse("2026-08-24T02:00:00Z");
        Instant t3 = Instant.parse("2026-08-24T03:00:00Z");
        Instant t4 = Instant.parse("2026-08-24T04:00:00Z");
        Instant t5 = Instant.parse("2026-08-24T05:00:00Z");
        appendCheck(orderA, t3);
        appendCheck(orderA, t1);
        appendCheck(orderA, t5);
        appendCheck(orderA, t2);
        appendCheck(orderA, t4);
        appendCheck(orderB, t1);
        appendCheck(orderB, t2);
        appendCheck(orderB, t3);

        List<ProductionHistoryEntry> forA = historyService.listByOrder(orderA);
        assertEquals(5, forA.size());
        assertEquals(List.of(t1, t2, t3, t4, t5), forA.stream().map(ProductionHistoryEntry::occurredAt).toList());
        assertTrue(forA.stream().noneMatch(e -> e.sourceOrderId().equals(orderB)));
        assertEquals(3, historyService.listByOrder(orderB).size());
        assertTrue(historyService.listByOrder(SourceOrderId.generate()).isEmpty());
    }

    @Test
    void updateAndDeleteAreRejectedByDatabaseTriggers() {
        SourceOrderId orderId = SourceOrderId.generate();
        ProductionHistoryEntry entry =
                historyService.append(
                        historyService.orderAccepted(
                                orderId,
                                UUID.randomUUID(),
                                Instant.parse("2026-08-24T11:00:00Z"),
                                List.of(UUID.randomUUID()),
                                Optional.empty()));
        UUID entryId = entry.entryId().value();

        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                "UPDATE production.production_history SET summary = 'mutated' WHERE entry_id = ?",
                                entryId));
        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                "DELETE FROM production.production_history WHERE entry_id = ?",
                                entryId));

        List<ProductionHistoryEntry> remaining = repository.listByOrder(orderId);
        assertEquals(1, remaining.size());
        assertEquals("Order accepted into production", remaining.getFirst().summary().orElseThrow());
    }

    @Test
    void duplicateBusinessReferenceForSameTypeIsRejected() {
        SourceOrderId orderId = SourceOrderId.generate();
        UUID documentId = UUID.randomUUID();
        historyService.append(
                historyService.orderAccepted(
                        orderId,
                        documentId,
                        Instant.parse("2026-08-24T11:00:00Z"),
                        List.of(UUID.randomUUID()),
                        Optional.empty()));
        assertThrows(
                ProductionPersistenceException.class,
                () ->
                        historyService.append(
                                historyService.orderAccepted(
                                        orderId,
                                        documentId,
                                        Instant.parse("2026-08-24T11:05:00Z"),
                                        List.of(UUID.randomUUID()),
                                        Optional.empty())));
        assertEquals(1, historyService.listByOrder(orderId).size());
    }

    @Test
    void historyAppendFailureRollsBackAmbientBusinessMutation() {
        SourceOrderId orderId = SourceOrderId.generate();
        UUID itemId = UUID.randomUUID();
        TransactionTemplate tx = new TransactionTemplate(txManager);
        assertThrows(
                RuntimeException.class,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            """
                                            INSERT INTO production.production_item_states (
                                                id, source_order_id, source_order_item_id, specification_id,
                                                status, ordered_quantity, launched_quantity,
                                                active_production_quantity, released_quantity,
                                                last_status_changed_at, version, created_at, updated_at)
                                            VALUES (?, ?, ?, ?, 'IN_PRODUCTION', 10, 10, 10, 0, ?, 0, ?, ?)
                                            """,
                                            UUID.randomUUID(),
                                            orderId.value(),
                                            itemId,
                                            UUID.randomUUID(),
                                            java.sql.Timestamp.from(CLOCK.instant()),
                                            java.sql.Timestamp.from(CLOCK.instant()),
                                            java.sql.Timestamp.from(CLOCK.instant()));
                                    throw new RuntimeException("controlled history failure");
                                }));

        Integer itemCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM production.production_item_states WHERE source_order_id = ?",
                        Integer.class,
                        orderId.value());
        assertEquals(0, itemCount);
        assertTrue(historyService.listByOrder(orderId).isEmpty());
    }

    @Test
    void historyFailureAfterBusinessInsertRollsBackBoth() {
        SourceOrderId orderId = SourceOrderId.generate();
        UUID itemId = UUID.randomUUID();
        FailingHistoryRepository failing = new FailingHistoryRepository(repository);
        ProductionHistoryService failingHistory = new ProductionHistoryService(failing, CLOCK);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        assertThrows(
                RuntimeException.class,
                () ->
                        tx.executeWithoutResult(
                                status -> {
                                    jdbc.update(
                                            """
                                            INSERT INTO production.production_item_states (
                                                id, source_order_id, source_order_item_id, specification_id,
                                                status, ordered_quantity, launched_quantity,
                                                active_production_quantity, released_quantity,
                                                last_status_changed_at, version, created_at, updated_at)
                                            VALUES (?, ?, ?, ?, 'IN_PRODUCTION', 10, 10, 10, 0, ?, 0, ?, ?)
                                            """,
                                            UUID.randomUUID(),
                                            orderId.value(),
                                            itemId,
                                            UUID.randomUUID(),
                                            java.sql.Timestamp.from(CLOCK.instant()),
                                            java.sql.Timestamp.from(CLOCK.instant()),
                                            java.sql.Timestamp.from(CLOCK.instant()));
                                    failingHistory.append(
                                            failingHistory.orderAccepted(
                                                    orderId,
                                                    UUID.randomUUID(),
                                                    CLOCK.instant(),
                                                    List.of(itemId),
                                                    Optional.empty()));
                                }));

        Integer itemCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM production.production_item_states WHERE source_order_id = ?",
                        Integer.class,
                        orderId.value());
        assertEquals(0, itemCount);
        assertTrue(repository.listByOrder(orderId).isEmpty());
    }

    @Test
    void truncateClearsFixtureWithoutTriggerInterference() {
        SourceOrderId orderId = SourceOrderId.generate();
        historyService.append(
                historyService.orderAccepted(
                        orderId,
                        UUID.randomUUID(),
                        CLOCK.instant(),
                        List.of(UUID.randomUUID()),
                        Optional.empty()));
        jdbc.update("TRUNCATE TABLE production.production_history");
        assertTrue(historyService.listByOrder(orderId).isEmpty());
    }

    private void appendCheck(SourceOrderId orderId, Instant occurredAt) {
        repository.append(
                ProductionHistoryEntry.create(
                        ProductionHistoryEntry.ProductionHistoryEntryId.generate(),
                        orderId,
                        ProductionHistoryType.MATERIALS_CHECKED,
                        occurredAt,
                        occurredAt.plusSeconds(1),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("check"),
                        Optional.of("{\"overallStatus\":\"ALL_AVAILABLE\"}")));
    }

    private static final class FailingHistoryRepository
            implements com.tmp.production.domain.repository.ProductionHistoryRepository {

        private final com.tmp.production.domain.repository.ProductionHistoryRepository delegate;

        private FailingHistoryRepository(
                com.tmp.production.domain.repository.ProductionHistoryRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public ProductionHistoryEntry append(ProductionHistoryEntry entry) {
            throw new RuntimeException("controlled history repository failure");
        }

        @Override
        public List<ProductionHistoryEntry> listByOrder(SourceOrderId sourceOrderId) {
            return delegate.listByOrder(sourceOrderId);
        }
    }
}
