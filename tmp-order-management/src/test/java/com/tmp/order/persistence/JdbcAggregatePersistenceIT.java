package com.tmp.order.persistence;

import com.tmp.order.testsupport.IntakeContractFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.CustomerOrder;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.ItemSpecification;
import com.tmp.order.domain.OptimisticLockConflictException;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.SpecificationLine;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
class JdbcAggregatePersistenceIT {

    private static final Instant NOW = Instant.parse("2026-07-26T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private JdbcCustomerOrderRepository orders;
    private JdbcOrderItemRepository items;
    private JdbcOrderItemRevisionRepository revisions;
    private JdbcItemSpecificationRepository specifications;

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
        orders = new JdbcCustomerOrderRepository(jdbc);
        items = new JdbcOrderItemRepository(jdbc);
        revisions = new JdbcOrderItemRevisionRepository(jdbc);
        specifications = new JdbcItemSpecificationRepository(jdbc);
    }

    @Test
    void migrationCreatesAggregateTablesWithoutForeignCapabilityColumns() {
        assertEquals(1, countTables("orders"));
        assertEquals(1, countTables("order_items"));
        assertEquals(1, countTables("order_item_revisions"));
        assertEquals(1, countTables("item_specifications"));
        assertEquals(1, countTables("item_specification_lines"));
        Integer foreign =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND (
                            lower(column_name) LIKE '%production%'
                            OR lower(column_name) LIKE '%warehouse%'
                            OR lower(column_name) LIKE '%cutting%'
                          )
                        """,
                        Integer.class);
        assertEquals(0, foreign);
    }

    @Test
    void insertAndLoadCustomerOrder() {
        CustomerOrder created =
                CustomerOrder.create(OrderId.generate(), OrderNumber.of("ORD-100"), commercial(), CLOCK);
        CustomerOrder saved = orders.save(created);
        Optional<CustomerOrder> loaded = orders.findById(saved.id());
        assertTrue(loaded.isPresent());
        assertEquals(saved.orderNumber(), loaded.get().orderNumber());
        assertEquals(OrderStatus.DRAFT, loaded.get().status());
        assertEquals(0L, loaded.get().version());
        assertEquals(CLOCK.instant(), loaded.get().createdAt());
        assertEquals(CLOCK.instant(), loaded.get().updatedAt());
        assertEquals("Acme", loaded.get().commercialData().customerName());
        assertTrue(orders.existsByOrderNumber(OrderNumber.of("ORD-100")));
        assertFalse(orders.existsByOrderNumber(OrderNumber.of("MISSING")));
    }

    @Test
    void customerOrderOptimisticLockingAndStaleConflict() {
        CustomerOrder created =
                CustomerOrder.create(OrderId.generate(), OrderNumber.of("ORD-OL"), commercial(), CLOCK);
        CustomerOrder v0 = orders.save(created);
        CustomerOrder edited = v0.updateCommercialData(commercial("Renamed"), CLOCK);
        CustomerOrder v1 = orders.save(edited);
        assertEquals(1L, v1.version());
        assertEquals(CLOCK.instant(), v1.updatedAt());

        CustomerOrder stale =
                CustomerOrder.rehydrate(
                        v0.id(),
                        v0.orderNumber(),
                        commercial("Stale"),
                        OrderStatus.DRAFT,
                        0L,
                        v0.createdAt(),
                        NOW);
        assertThrows(OptimisticLockConflictException.class, () -> orders.save(stale));
        assertEquals("Renamed", orders.findById(v0.id()).orElseThrow().commercialData().customerName());
    }

    @Test
    void informationSchemaConstraintsExistAndNoJsonOrEnumStatusColumns() {
        Integer uniqueOrderNumber =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON tc.constraint_name = kcu.constraint_name
                         AND tc.table_schema = kcu.table_schema
                        WHERE tc.table_schema = 'order_management'
                          AND tc.table_name = 'orders'
                          AND tc.constraint_type = 'UNIQUE'
                          AND kcu.column_name = 'order_number'
                        """,
                        Integer.class);
        assertTrue(uniqueOrderNumber > 0);

        Integer foreignKeys =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints tc
                        WHERE tc.table_schema = 'order_management'
                          AND tc.constraint_type = 'FOREIGN KEY'
                        """,
                        Integer.class);
        assertTrue(foreignKeys > 0);

        Integer jsonColumns =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND (
                            data_type IN ('json', 'jsonb')
                            OR udt_name IN ('json', 'jsonb')
                            OR data_type = 'bytea'
                          )
                        """,
                        Integer.class);
        assertEquals(0, jsonColumns);

        // status columns must be string codes (no Postgres enum types)
        Integer userDefinedStatus =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND column_name ILIKE '%status%'
                          AND data_type = 'USER-DEFINED'
                        """,
                        Integer.class);
        assertEquals(0, userDefinedStatus);
    }

    @Test
    void uniqueOrderNumberIsEnforced() {
        orders.save(CustomerOrder.create(OrderId.generate(), OrderNumber.of("DUP"), commercial(), CLOCK));
        CustomerOrder duplicate =
                CustomerOrder.create(OrderId.generate(), OrderNumber.of("DUP"), commercial(), CLOCK);
        assertThrows(DataIntegrityViolationException.class, () -> orders.save(duplicate));
    }

    @Test
    void insertAndLoadFullOrderItemAggregateWithMultipleRevisions() {
        CustomerOrder order =
                orders.save(
                        CustomerOrder.create(
                                OrderId.generate(), OrderNumber.of("ORD-ITEM"), commercial(), CLOCK));
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber rev1 = RevisionNumber.first();
        RevisionNumber rev2 = RevisionNumber.of(2);
        ItemSpecification spec1 =
                ItemSpecification.rehydrate(
                        itemId,
                        rev1,
                        List.of(
                                IntakeContractFixtures.specLine("M1", "Steel", new BigDecimal("1.5"), "kg"),
                                IntakeContractFixtures.specLine("M2", "Paint", new BigDecimal("0.2"), "l")),
                        true);
        ItemSpecification spec2 =
                ItemSpecification.rehydrate(
                        itemId,
                        rev2,
                        List.of(
                                IntakeContractFixtures.specLine("M3", "Glass", new BigDecimal("3"), "m2")),
                        false);
        OrderItemRevision revision1 =
                OrderItemRevision.rehydrate(
                        itemId,
                        rev1,
                        RevisionStatus.APPROVED,
                        OrderedQuantity.of(10),
                        null,
                        spec1);
        OrderItemRevision revision2 =
                OrderItemRevision.rehydrate(
                        itemId,
                        rev2,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(12),
                        rev1,
                        spec2);
        Map<RevisionNumber, OrderItemRevision> revisionMap = new LinkedHashMap<>();
        revisionMap.put(rev1, revision1);
        revisionMap.put(rev2, revision2);
        OrderItem aggregate =
                OrderItem.rehydrate(
                        itemId,
                        order.id(),
                        ItemCommercialData.of(ProductCode.of("P-100"), "Door", "comment"),
                        OrderItemStatus.ACTIVE,
                        rev1,
                        rev2,
                        revisionMap,
                        0L,
                        NOW,
                        NOW);

        OrderItem saved = items.save(aggregate);
        OrderItem loaded = items.findById(itemId).orElseThrow();
        assertEquals(saved.version(), loaded.version());
        assertEquals(OrderItemStatus.ACTIVE, loaded.status());
        assertEquals(Optional.of(rev1), loaded.activeRevisionNumber());
        assertEquals(Optional.of(rev2), loaded.draftRevisionNumber());
        assertEquals(2, loaded.revisions().size());

        List<SpecificationLine> loadedLines =
                loaded.revision(rev1).orElseThrow().specification().orElseThrow().lines();
        assertEquals(2, loadedLines.size());
        assertEquals("M1", loadedLines.get(0).materialCode());
        assertEquals("M2", loadedLines.get(1).materialCode());

        OrderItemRevision readRevision =
                revisions.findByOrderItemIdAndRevisionNumber(itemId, rev2).orElseThrow();
        assertEquals(RevisionStatus.DRAFT, readRevision.status());
        assertEquals(rev1, readRevision.previousRevisionNumber().orElseThrow());

        ItemSpecification readSpec =
                specifications.findByOrderItemIdAndRevisionNumber(itemId, rev1).orElseThrow();
        assertTrue(readSpec.isImmutable());
        assertEquals(2, readSpec.lines().size());

        List<OrderItem> byOrder = items.findByOrderId(order.id());
        assertEquals(1, byOrder.size());
        assertEquals(itemId, byOrder.getFirst().id());
    }

    @Test
    void orderItemOptimisticLockConflict() {
        CustomerOrder order =
                orders.save(
                        CustomerOrder.create(
                                OrderId.generate(), OrderNumber.of("ORD-ITEM-OL"), commercial(), CLOCK));
        OrderItem created =
                OrderItem.create(
                        OrderItemId.generate(),
                        order.id(),
                        ItemCommercialData.of(ProductCode.of("P-1"), "Item", null),
                        OrderedQuantity.of(1),
                        CLOCK);
        OrderItem v0 = items.save(created);
        OrderItem edited = v0.updateCommercialData(
                ItemCommercialData.of(ProductCode.of("P-1"), "Renamed", null), CLOCK);
        OrderItem v1 = items.save(edited);
        assertEquals(1L, v1.version());

        OrderItem stale =
                OrderItem.rehydrate(
                        v0.id(),
                        v0.orderId(),
                        ItemCommercialData.of(ProductCode.of("P-1"), "Stale", null),
                        v0.status(),
                        v0.activeRevisionNumber().orElse(null),
                        v0.draftRevisionNumber().orElse(null),
                        v0.revisions(),
                        0L,
                        v0.createdAt(),
                        NOW);
        assertThrows(OptimisticLockConflictException.class, () -> items.save(stale));
    }

    @Test
    void foreignKeyIntegrityRejectsItemWithoutOrder() {
        OrderItem orphan =
                OrderItem.create(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        ItemCommercialData.of(ProductCode.of("P-X"), "Orphan", null),
                        OrderedQuantity.of(1),
                        CLOCK);
        assertThrows(DataIntegrityViolationException.class, () -> items.save(orphan));
    }

    private static OrderCommercialData commercial() {
        return commercial("Acme");
    }

    private static OrderCommercialData commercial(String customerName) {
        return OrderCommercialData.of(
                "CUST-1",
                customerName,
                "CTR-1",
                "SITE-1",
                "Manager",
                OrderDirection.CORPORATE,
                CurrencyCode.of("EUR"));
    }

    private static int countTables(String tableName) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'order_management' AND table_name = ?
                        """,
                        Integer.class,
                        tableName);
        return count == null ? 0 : count;
    }
}
