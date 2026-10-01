package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.OrderQuantityModeOptimisticLockException;
import com.tmp.production.domain.OrderQuantityModeSetting;
import com.tmp.production.domain.ProductionQuantityMode;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.repository.OrderQuantityModeRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class JdbcOrderQuantityModeRepositoryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-01T09:00:00Z"), ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private OrderQuantityModeRepository repository;

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
        jdbc.update("DELETE FROM production.order_quantity_modes");
        repository = new JdbcOrderQuantityModeRepository(jdbc, CLOCK);
    }

    @Test
    void unknownOrderHasNoStoredSetting() {
        assertTrue(repository.findBySourceOrderId(SourceOrderId.generate()).isEmpty());
    }

    @Test
    void firstChangeFromDefaultInsertsVersionOne() {
        SourceOrderId orderId = SourceOrderId.generate();

        OrderQuantityModeSetting saved =
                repository.save(orderId, ProductionQuantityMode.FLEXIBLE, 0L);

        assertEquals(new OrderQuantityModeSetting(orderId, ProductionQuantityMode.FLEXIBLE, 1L), saved);
        assertEquals(saved, repository.findBySourceOrderId(orderId).orElseThrow());
    }

    @Test
    void standardToFlexibleToStandardIncrementsVersionAndReadsAuthoritativeValue() {
        SourceOrderId orderId = SourceOrderId.generate();
        repository.save(orderId, ProductionQuantityMode.STANDARD, 0L);

        OrderQuantityModeSetting flexible =
                repository.save(orderId, ProductionQuantityMode.FLEXIBLE, 1L);
        assertEquals(ProductionQuantityMode.FLEXIBLE, flexible.quantityMode());
        assertEquals(2L, flexible.version());
        assertEquals(flexible, repository.findBySourceOrderId(orderId).orElseThrow());

        OrderQuantityModeSetting standard =
                repository.save(orderId, ProductionQuantityMode.STANDARD, 2L);
        assertEquals(ProductionQuantityMode.STANDARD, standard.quantityMode());
        assertEquals(3L, standard.version());
        assertEquals(standard, repository.findBySourceOrderId(orderId).orElseThrow());
    }

    @Test
    void staleVersionDoesNotOverwriteNewerValue() {
        SourceOrderId orderId = SourceOrderId.generate();
        // User A and User B both read the default (STANDARD, version 0); B saves FLEXIBLE first.
        repository.save(orderId, ProductionQuantityMode.FLEXIBLE, 0L);

        OrderQuantityModeOptimisticLockException ex =
                assertThrows(
                        OrderQuantityModeOptimisticLockException.class,
                        () -> repository.save(orderId, ProductionQuantityMode.STANDARD, 0L));
        assertEquals(orderId, ex.sourceOrderId());
        assertEquals(0L, ex.expectedVersion());
        assertEquals(
                new OrderQuantityModeSetting(orderId, ProductionQuantityMode.FLEXIBLE, 1L),
                repository.findBySourceOrderId(orderId).orElseThrow());

        repository.save(orderId, ProductionQuantityMode.STANDARD, 1L);
        assertThrows(
                OrderQuantityModeOptimisticLockException.class,
                () -> repository.save(orderId, ProductionQuantityMode.FLEXIBLE, 1L));
        assertEquals(
                ProductionQuantityMode.STANDARD,
                repository.findBySourceOrderId(orderId).orElseThrow().quantityMode());
    }

    @Test
    void concurrentWritersWithSameExpectedVersionHaveExactlyOneWinner()
            throws InterruptedException, ExecutionException {
        assertExactlyOneWinner(SourceOrderId.generate(), 0L);

        SourceOrderId existing = SourceOrderId.generate();
        repository.save(existing, ProductionQuantityMode.STANDARD, 0L);
        assertExactlyOneWinner(existing, 1L);
    }

    @Test
    void negativeExpectedVersionIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.save(SourceOrderId.generate(), ProductionQuantityMode.FLEXIBLE, -1L));
    }

    @Test
    void schemaHasSingleModeColumnNoForeignKeysAndV48Applied() {
        List<String> columns =
                jdbc.queryForList(
                        """
                        SELECT column_name FROM information_schema.columns
                        WHERE table_schema = 'production' AND table_name = 'order_quantity_modes'
                        ORDER BY ordinal_position
                        """,
                        String.class);
        assertEquals(
                List.of("source_order_id", "quantity_mode", "version", "created_at", "updated_at"),
                columns);

        Integer foreignKeys =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = 'production'
                          AND table_name = 'order_quantity_modes'
                          AND constraint_type = 'FOREIGN KEY'
                        """,
                        Integer.class);
        assertEquals(0, foreignKeys);

        Integer applied48 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '48' AND success = TRUE
                          AND script = 'V48__production_order_quantity_modes.sql'
                        """,
                        Integer.class);
        assertEquals(1, applied48);
    }

    @Test
    void schemaRejectsUnknownModeValues() {
        assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () ->
                        jdbc.update(
                                """
                                INSERT INTO production.order_quantity_modes (
                                    source_order_id, quantity_mode, version, created_at, updated_at)
                                VALUES (gen_random_uuid(), 'PARTIAL', 1, now(), now())
                                """));
    }

    private void assertExactlyOneWinner(SourceOrderId orderId, long expectedVersion)
            throws InterruptedException, ExecutionException {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> writers = new ArrayList<>();
            for (ProductionQuantityMode mode :
                    List.of(ProductionQuantityMode.FLEXIBLE, ProductionQuantityMode.STANDARD)) {
                writers.add(
                        () -> {
                            barrier.await(10, TimeUnit.SECONDS);
                            try {
                                repository.save(orderId, mode, expectedVersion);
                                return true;
                            } catch (OrderQuantityModeOptimisticLockException ex) {
                                return false;
                            }
                        });
            }
            int winners = 0;
            for (Future<Boolean> result : executor.invokeAll(writers)) {
                if (result.get()) {
                    winners++;
                }
            }
            assertEquals(1, winners);
            assertEquals(
                    expectedVersion + 1,
                    repository.findBySourceOrderId(orderId).orElseThrow().version());
        } finally {
            executor.shutdownNow();
        }
    }
}
