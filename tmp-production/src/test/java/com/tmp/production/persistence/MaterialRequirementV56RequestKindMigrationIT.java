package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductRequestKind;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import com.tmp.production.domain.repository.MaterialRequirementRepository.SubmittedRepeatedProductRequestStats;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * V56 backfill: TRUE→COUNTED, FALSE→LEGACY_EXCLUDED (never REPEATED). Fresh migrate and upgrade
 * paths; repeated stats ignore LEGACY and DRAFT.
 */
class MaterialRequirementV56RequestKindMigrationIT {

    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);

    @Nested
    @Testcontainers
    class UpgradeFromV55 {

        @Container
        private static final PostgreSQLContainer<?> POSTGRES =
                new PostgreSQLContainer<>("postgres:16-alpine");

        private static JdbcTemplate jdbc;
        private static MaterialRequirementRepository repository;

        @BeforeAll
        static void connect() {
            DriverManagerDataSource ds = dataSource(POSTGRES);
            jdbc = new JdbcTemplate(ds);
            repository =
                    new JdbcMaterialRequirementRepository(
                            jdbc, CLOCK, new DataSourceTransactionManager(ds));
        }

        @Test
        void backfillsCountedAndLegacyExcludedNeverRepeated() {
            Flyway.configure()
                    .dataSource(dataSource(POSTGRES))
                    .locations("classpath:db/migration")
                    .target("55")
                    .load()
                    .migrate();

            UUID countedReq = UUID.randomUUID();
            UUID legacyReq = UUID.randomUUID();
            UUID orderId = UUID.randomUUID();
            UUID countedItem = UUID.randomUUID();
            UUID legacyItem = UUID.randomUUID();
            UUID warehouseId = UUID.randomUUID();

            insertSubmittedRequirement(jdbc, countedReq, orderId, countedItem, warehouseId, true, 3L);
            insertSubmittedRequirement(
                    jdbc, legacyReq, orderId, legacyItem, warehouseId, false, 7L);

            Flyway.configure()
                    .dataSource(dataSource(POSTGRES))
                    .locations("classpath:db/migration")
                    .target("56")
                    .load()
                    .migrate();

            assertEquals("COUNTED", kindOf(jdbc, countedReq, orderId, countedItem));
            assertEquals("LEGACY_EXCLUDED", kindOf(jdbc, legacyReq, orderId, legacyItem));
            assertTrue(countsOf(jdbc, countedReq, orderId, countedItem));
            assertFalse(countsOf(jdbc, legacyReq, orderId, legacyItem));

            Integer repeatedBackfill =
                    jdbc.queryForObject(
                            """
                            SELECT COUNT(*) FROM production.material_requirement_source_items
                            WHERE product_request_kind = 'REPEATED'
                            """,
                            Integer.class);
            assertEquals(0, repeatedBackfill);

            MaterialRequirementSourceItemKey countedKey =
                    MaterialRequirementSourceItemKey.of(
                            SourceOrderId.of(orderId), SourceOrderItemId.of(countedItem));
            MaterialRequirementSourceItemKey legacyKey =
                    MaterialRequirementSourceItemKey.of(
                            SourceOrderId.of(orderId), SourceOrderItemId.of(legacyItem));
            assertEquals(
                    3L,
                    repository.sumSubmittedProductQuantities(List.of(countedKey)).get(countedKey));
            assertEquals(
                    0L, repository.sumSubmittedProductQuantities(List.of(legacyKey)).get(legacyKey));

            Map<MaterialRequirementSourceItemKey, SubmittedRepeatedProductRequestStats> repeated =
                    repository.sumSubmittedRepeatedProductRequests(List.of(countedKey, legacyKey));
            assertEquals(0L, repeated.get(countedKey).submittedProductQuantity());
            assertEquals(0L, repeated.get(legacyKey).submittedRequestCount());
        }
    }

    @Nested
    @Testcontainers
    class FreshMigrate {

        @Container
        private static final PostgreSQLContainer<?> POSTGRES =
                new PostgreSQLContainer<>("postgres:16-alpine");

        private static MaterialRequirementRepository repository;

        @BeforeAll
        static void migrate() {
            DataSource ds = dataSource(POSTGRES);
            Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            repository =
                    new JdbcMaterialRequirementRepository(
                            new JdbcTemplate(ds), CLOCK, new DataSourceTransactionManager(ds));
        }

        @Test
        void persistsRepeatedAndExcludesFromCoverage() {
            SourceOrderId orderId = SourceOrderId.generate();
            SourceOrderItemId countedItem = SourceOrderItemId.generate();
            SourceOrderItemId repeatedItem = SourceOrderItemId.generate();
            UUID warehouseId = UUID.randomUUID();

            MaterialRequirement counted =
                    repository.save(
                            MaterialRequirement.create(
                                    warehouseId,
                                    T0,
                                    List.of(
                                            MaterialRequirementSourceItem.of(
                                                    orderId, countedItem, 4L)),
                                    List.of(line(orderId, countedItem, "4"))));
            MaterialRequirement repeated =
                    repository.save(
                            MaterialRequirement.create(
                                    warehouseId,
                                    T0,
                                    List.of(
                                            MaterialRequirementSourceItem.repeated(
                                                    orderId, repeatedItem, 5L)),
                                    List.of(line(orderId, repeatedItem, "5"))));

            MaterialRequirement countedSubmitted =
                    repository.markSubmitted(counted.submit("user-1", T0));
            MaterialRequirement repeatedSubmitted =
                    repository.markSubmitted(repeated.submit("user-1", T0));

            assertEquals(
                    ProductRequestKind.COUNTED,
                    countedSubmitted.sourceItems().getFirst().productRequestKind());
            assertEquals(
                    ProductRequestKind.REPEATED,
                    repeatedSubmitted.sourceItems().getFirst().productRequestKind());

            MaterialRequirementSourceItemKey countedKey =
                    MaterialRequirementSourceItemKey.of(orderId, countedItem);
            MaterialRequirementSourceItemKey repeatedKey =
                    MaterialRequirementSourceItemKey.of(orderId, repeatedItem);

            assertEquals(
                    4L,
                    repository.sumSubmittedProductQuantities(List.of(countedKey)).get(countedKey));
            assertEquals(
                    0L,
                    repository
                            .sumSubmittedProductQuantities(List.of(repeatedKey))
                            .get(repeatedKey));

            SubmittedRepeatedProductRequestStats stats =
                    repository
                            .sumSubmittedRepeatedProductRequests(List.of(repeatedKey))
                            .get(repeatedKey);
            assertEquals(5L, stats.submittedProductQuantity());
            assertEquals(1L, stats.submittedRequestCount());

            MaterialRequirement reopened =
                    repository.findById(repeatedSubmitted.requirementId()).orElseThrow();
            assertEquals(
                    ProductRequestKind.REPEATED,
                    reopened.sourceItems().getFirst().productRequestKind());
        }
    }

    private static DriverManagerDataSource dataSource(PostgreSQLContainer<?> postgres) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    private static MaterialRequirementLine line(
            SourceOrderId orderId, SourceOrderItemId itemId, String qty) {
        return MaterialRequirementLine.create(
                MaterialReferenceId.generate(),
                "MAT",
                "Material",
                "WHITE",
                "PCS",
                null,
                new BigDecimal(qty),
                List.of(
                        MaterialRequirementLineContribution.of(
                                orderId, itemId, new BigDecimal(qty))));
    }

    private static void insertSubmittedRequirement(
            JdbcTemplate jdbc,
            UUID requirementId,
            UUID orderId,
            UUID itemId,
            UUID warehouseId,
            boolean countsToward,
            long productQty) {
        UUID lineId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO production.material_requirements (
                    id, source_order_id, destination_warehouse_id,
                    created_at, updated_at, version, status, submitted_at, submitted_by)
                VALUES (?, ?, ?, ?, ?, 1, 'SUBMITTED', ?, 'seed')
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
                VALUES (?, ?, ?, 'MAT', 'M', '', 'PCS', ?, 0)
                """,
                lineId,
                requirementId,
                UUID.randomUUID(),
                new BigDecimal("1.000000"));
        jdbc.update(
                """
                INSERT INTO production.material_requirement_source_items (
                    requirement_id, source_order_id, source_order_item_id,
                    requested_product_quantity, counts_toward_product_coverage)
                VALUES (?, ?, ?, ?, ?)
                """,
                requirementId,
                orderId,
                itemId,
                productQty,
                countsToward);
    }

    private static String kindOf(JdbcTemplate jdbc, UUID requirementId, UUID orderId, UUID itemId) {
        return jdbc.queryForObject(
                """
                SELECT product_request_kind
                FROM production.material_requirement_source_items
                WHERE requirement_id = ? AND source_order_id = ? AND source_order_item_id = ?
                """,
                String.class,
                requirementId,
                orderId,
                itemId);
    }

    private static boolean countsOf(
            JdbcTemplate jdbc, UUID requirementId, UUID orderId, UUID itemId) {
        Boolean value =
                jdbc.queryForObject(
                        """
                        SELECT counts_toward_product_coverage
                        FROM production.material_requirement_source_items
                        WHERE requirement_id = ? AND source_order_id = ? AND source_order_item_id = ?
                        """,
                        Boolean.class,
                        requirementId,
                        orderId,
                        itemId);
        return Boolean.TRUE.equals(value);
    }
}
