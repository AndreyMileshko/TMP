package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Integration tests for Production JDBC persistence against PostgreSQL. */
@Testcontainers
class JdbcProductionItemStateRepositoryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-19T06:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-19T06:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-19T07:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private ProductionItemStateRepository repository;

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
        jdbc.update("DELETE FROM production.production_item_states");
        repository = new JdbcProductionItemStateRepository(jdbc, CLOCK);
    }

    @Test
    void saveAndLoadRoundTripPreservesDomainState() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specificationId = SpecificationId.generate();

        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, specificationId, T0);
        ProductionItemState launched =
                ProductionItemState.launch(foundation, ProductionQuantity.positive(10), T0);
        ProductionItemState partial =
                launched.release(ProductionQuantity.positive(4), T1).recordMaterialCheck(T1);

        repository.save(partial);

        ProductionItemState loaded =
                repository
                        .findByIdentity(orderId, itemId, specificationId)
                        .orElseThrow();

        assertFieldEquals(partial, loaded);
        assertEquals(foundation, loaded.foundation());
        assertEquals(specificationId, loaded.specificationId());
        assertEquals(ProductionStatus.PARTIALLY_RELEASED, loaded.status());
        assertEquals(ProductionQuantity.positive(10), loaded.orderedQuantity());
        assertEquals(ProductionQuantity.positive(10), loaded.launchedQuantity());
        assertEquals(ProductionQuantity.nonNegative(6), loaded.activeProductionQuantity());
        assertEquals(ProductionQuantity.nonNegative(4), loaded.releasedQuantity());
        assertEquals(T1, loaded.lastMaterialCheckAt());
        assertEquals(T1, loaded.lastStatusChangedAt());
    }

    @Test
    void updateReplacesExistingRowByBusinessIdentity() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specificationId = SpecificationId.generate();

        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, specificationId, T0);
        ProductionItemState launched =
                ProductionItemState.launch(foundation, ProductionQuantity.positive(5), T0);
        repository.save(launched);

        ProductionItemState released = launched.release(ProductionQuantity.positive(5), T1);
        repository.save(released);

        ProductionItemState loaded =
                repository
                        .findByIdentity(orderId, itemId, specificationId)
                        .orElseThrow();

        assertEquals(ProductionStatus.RELEASED, loaded.status());
        assertEquals(ProductionQuantity.zero(), loaded.activeProductionQuantity());
        assertEquals(ProductionQuantity.positive(5), loaded.releasedQuantity());

        Integer rowCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM production.production_item_states", Integer.class);
        assertEquals(1, rowCount);
    }

    @Test
    void specificationIdIsPersistedAsNotNull() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specificationId = SpecificationId.generate();

        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, specificationId, T0);
        repository.save(
                ProductionItemState.launch(foundation, ProductionQuantity.positive(1), T0));

        UUID persistedSpecId =
                jdbc.queryForObject(
                        """
                        SELECT specification_id FROM production.production_item_states
                        WHERE source_order_id = ? AND source_order_item_id = ?
                        """,
                        UUID.class,
                        orderId.value(),
                        itemId.value());

        assertEquals(specificationId.value(), persistedSpecId);
        assertTrue(
                repository.findByIdentity(orderId, itemId, specificationId).isPresent());
    }

    @Test
    void findBySourceOrderIdReturnsAllStatesForOrderOnly() {
        SourceOrderId orderA = SourceOrderId.generate();
        SourceOrderId orderB = SourceOrderId.generate();
        SourceOrderItemId itemA1 = SourceOrderItemId.generate();
        SourceOrderItemId itemA2 = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SpecificationId specA1 = SpecificationId.generate();
        SpecificationId specA2 = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();

        ProductionItemState stateA1 =
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderA, itemA1, specA1, T0),
                        ProductionQuantity.positive(2),
                        T0);
        ProductionItemState stateA2 =
                ProductionItemState.launch(
                                ProductionFoundation.freeze(orderA, itemA2, specA2, T0),
                                ProductionQuantity.positive(5),
                                T0)
                        .release(ProductionQuantity.positive(5), T1);
        ProductionItemState stateB =
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderB, itemB, specB, T0),
                        ProductionQuantity.positive(1),
                        T0);

        repository.save(stateA1);
        repository.save(stateA2);
        repository.save(stateB);

        var loadedA = repository.findBySourceOrderId(orderA);
        assertEquals(2, loadedA.size());
        assertTrue(
                loadedA.stream().allMatch(state -> state.sourceOrderId().equals(orderA)));
        assertEquals(
                1,
                loadedA.stream()
                        .filter(state -> state.status() == ProductionStatus.IN_PRODUCTION)
                        .count());
        assertEquals(
                1,
                loadedA.stream()
                        .filter(state -> state.status() == ProductionStatus.RELEASED)
                        .count());
        assertEquals(
                ProductionQuantity.positive(5),
                loadedA.stream()
                        .filter(state -> state.sourceOrderItemId().equals(itemA2))
                        .findFirst()
                        .orElseThrow()
                        .releasedQuantity());

        assertEquals(1, repository.findBySourceOrderId(orderB).size());
        assertTrue(repository.findBySourceOrderId(SourceOrderId.generate()).isEmpty());
    }

    private static void assertFieldEquals(ProductionItemState expected, ProductionItemState actual) {
        assertEquals(expected.foundation(), actual.foundation());
        assertEquals(expected.sourceOrderId(), actual.sourceOrderId());
        assertEquals(expected.sourceOrderItemId(), actual.sourceOrderItemId());
        assertEquals(expected.specificationId(), actual.specificationId());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.orderedQuantity(), actual.orderedQuantity());
        assertEquals(expected.launchedQuantity(), actual.launchedQuantity());
        assertEquals(expected.activeProductionQuantity(), actual.activeProductionQuantity());
        assertEquals(expected.releasedQuantity(), actual.releasedQuantity());
        assertEquals(expected.lastMaterialCheckAt(), actual.lastMaterialCheckAt());
        assertEquals(expected.lastStatusChangedAt(), actual.lastStatusChangedAt());
    }
}
