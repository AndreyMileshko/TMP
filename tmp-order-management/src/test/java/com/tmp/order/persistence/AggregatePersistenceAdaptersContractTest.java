package com.tmp.order.persistence;

import com.tmp.order.testsupport.IntakeContractFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderItem;
import com.tmp.order.domain.OrderItemRevision;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.ProductCode;
import com.tmp.order.domain.SpecificationLine;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.ItemSpecificationRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.order.domain.repository.OrderItemRevisionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit / SQL-contract tests for aggregate persistence adapters (STAGE5-033). Full PostgreSQL
 * integration coverage follows STAGE5-034.
 */
class AggregatePersistenceAdaptersContractTest {

    @Test
    void adaptersImplementRepositoryPorts() {
        assertTrue(CustomerOrderRepository.class.isAssignableFrom(JdbcCustomerOrderRepository.class));
        assertTrue(OrderItemRepository.class.isAssignableFrom(JdbcOrderItemRepository.class));
        assertTrue(
                OrderItemRevisionRepository.class.isAssignableFrom(
                        JdbcOrderItemRevisionRepository.class));
        assertTrue(
                ItemSpecificationRepository.class.isAssignableFrom(
                        JdbcItemSpecificationRepository.class));
    }

    @Test
    void customerOrderUpdateSqlUsesOptimisticLockAndVersionIncrement() {
        String sql = OrderAggregateSql.UPDATE_ORDER;
        assertTrue(sql.contains("WHERE order_id = ? AND version = ?"));
        assertTrue(sql.contains("version = version + 1"));
        assertFalse(sql.toLowerCase().contains("json"));
        assertFalse(sql.toLowerCase().contains("bytea"));
    }

    @Test
    void orderItemUpdateSqlUsesOptimisticLockAndVersionIncrement() {
        String sql = OrderAggregateSql.UPDATE_ORDER_ITEM;
        assertTrue(sql.contains("WHERE order_item_id = ? AND version = ?"));
        assertTrue(sql.contains("version = version + 1"));
        assertTrue(sql.contains("active_revision_number"));
        assertTrue(sql.contains("draft_revision_number"));
    }

    @Test
    void aggregateSqlTargetsOrderManagementSchemaWithoutForeignCapabilityColumns() {
        String all =
                OrderAggregateSql.INSERT_ORDER
                        + OrderAggregateSql.INSERT_ORDER_ITEM
                        + OrderAggregateSql.INSERT_REVISION
                        + OrderAggregateSql.INSERT_SPECIFICATION
                        + OrderAggregateSql.INSERT_SPEC_LINE;
        assertTrue(all.contains("order_management.orders"));
        assertTrue(all.contains("order_management.order_items"));
        assertTrue(all.contains("order_management.order_item_revisions"));
        assertTrue(all.contains("order_management.item_specifications"));
        assertTrue(all.contains("order_management.item_specification_lines"));
        String lower = all.toLowerCase();
        assertFalse(lower.contains("production"));
        assertFalse(lower.contains("warehouse"));
        assertFalse(lower.contains("cutting"));
        assertFalse(lower.contains("json"));
        assertFalse(lower.contains("bytea"));
    }

    @Test
    void rehydrateFactoriesRestoreCommercialFieldsStatusVersionAndTimestamps() {
        Instant created = Instant.parse("2026-07-26T10:00:00Z");
        Instant updated = Instant.parse("2026-07-26T11:00:00Z");
        OrderId orderId = OrderId.generate();
        CustomerOrder order =
                CustomerOrder.rehydrate(
                        orderId,
                        OrderNumber.of("ORD-1"),
                        OrderCommercialData.of(
                                "C-1",
                                "Acme",
                                "CT-1",
                                "SITE-1",
                                "Manager",
                                OrderDirection.DEALER,
                                CurrencyCode.of("USD")),
                        OrderStatus.APPROVED,
                        3L,
                        created,
                        updated);
        assertEquals(orderId, order.id());
        assertEquals("ORD-1", order.orderNumber().value());
        assertEquals(OrderStatus.APPROVED, order.status());
        assertEquals(3L, order.version());
        assertEquals(created, order.createdAt());
        assertEquals(updated, order.updatedAt());
        assertEquals("Acme", order.commercialData().customerName());
        assertEquals(OrderDirection.DEALER, order.commercialData().direction());
    }

    @Test
    void orderItemRehydrateRestoresRevisionsSpecificationAndPointers() {
        OrderItemId itemId = OrderItemId.generate();
        OrderId orderId = OrderId.generate();
        RevisionNumber rev1 = RevisionNumber.first();
        List<SpecificationLine> lines =
                List.of(
                        IntakeContractFixtures.specLine("MAT-1", "Steel", new BigDecimal("2.5"), "kg"));
        ItemSpecification specification =
                ItemSpecification.rehydrate(itemId, rev1, lines, true);
        OrderItemRevision revision =
                OrderItemRevision.rehydrate(
                        itemId,
                        rev1,
                        RevisionStatus.ACTIVE,
                        OrderedQuantity.of(5),
                        null,
                        specification);
        Map<RevisionNumber, OrderItemRevision> revisions = new LinkedHashMap<>();
        revisions.put(rev1, revision);
        Instant created = Instant.parse("2026-07-26T12:00:00Z");
        OrderItem item =
                OrderItem.rehydrate(
                        itemId,
                        orderId,
                        ItemCommercialData.of(ProductCode.of("P-1"), "Panel", "note"),
                        OrderItemStatus.ACTIVE,
                        rev1,
                        null,
                        revisions,
                        2L,
                        created,
                        created);
        assertEquals(OrderItemStatus.ACTIVE, item.status());
        assertEquals(Optional.of(rev1), item.activeRevisionNumber());
        assertTrue(item.draftRevisionNumber().isEmpty());
        assertEquals(1, item.revisions().size());
        assertEquals(RevisionStatus.ACTIVE, item.revision(rev1).orElseThrow().status());
        assertEquals(
                "MAT-1",
                item.revision(rev1).orElseThrow().specification().orElseThrow().lines().getFirst()
                        .materialCode());
    }
}
