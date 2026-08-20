package com.tmp.order.persistence;

import com.tmp.order.testsupport.IntakeContractFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSort;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.SpecificationId;
import com.tmp.order.application.query.DefaultOrderQueryService;
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
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
class JdbcOrderQueryReadAdapterIT {

    private static final Instant T1 = Instant.parse("2026-07-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant T3 = Instant.parse("2026-07-22T10:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private JdbcCustomerOrderRepository orderRepository;
    private JdbcOrderItemRepository itemRepository;
    private JdbcOrderQueryReadAdapter readAdapter;
    private DefaultOrderQueryService queries;

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
        orderRepository = new JdbcCustomerOrderRepository(jdbc);
        itemRepository = new JdbcOrderItemRepository(jdbc);
        readAdapter = new JdbcOrderQueryReadAdapter(jdbc);
        queries = new DefaultOrderQueryService(readAdapter, AllowingAuthorization.INSTANCE);
    }

    @Test
    void incompleteDraftItemIsReadableViaFindOrderItemAndFindOrderItems() {
        Clock clock = Clock.fixed(T1, ZoneOffset.UTC);
        OrderId orderId =
                orderRepository
                        .save(
                                CustomerOrder.create(
                                        OrderId.generate(),
                                        OrderNumber.of("ORD-ITEM-INC"),
                                        OrderCommercialData.of(
                                                null, null, null, null, null, null, null),
                                        clock))
                        .id();
        OrderItemId itemId =
                itemRepository
                        .save(
                                OrderItem.create(
                                        OrderItemId.generate(),
                                        orderId,
                                        ItemCommercialData.of(null, null, null, "EXT-IMP-42"),
                                        OrderedQuantity.of(3),
                                        clock))
                        .id();

        Optional<OrderItemDto> byId = readAdapter.findOrderItem(itemId);
        assertTrue(byId.isPresent());
        OrderItemDto dto = byId.orElseThrow();
        assertNull(dto.productCode());
        assertNull(dto.name());
        assertEquals("EXT-IMP-42", dto.externalPositionNumber());
        assertEquals(OrderItemStatus.DRAFT, dto.status());
        assertFalse("UNKNOWN".equalsIgnoreCase(String.valueOf(dto.productCode())));
        assertFalse("N/A".equalsIgnoreCase(String.valueOf(dto.name())));

        PageResult<OrderItemDto> page =
                readAdapter.findOrderItems(orderId, PageRequest.firstPage());
        assertEquals(1, page.totalElements());
        OrderItemDto listed = page.content().getFirst();
        assertNull(listed.productCode());
        assertNull(listed.name());
        assertEquals("EXT-IMP-42", listed.externalPositionNumber());

        Optional<OrderItemDto> viaQueryService = queries.getOrderItem(itemId);
        assertTrue(viaQueryService.isPresent());
        assertEquals("EXT-IMP-42", viaQueryService.orElseThrow().externalPositionNumber());
    }

    @Test
    void incompleteDraftItemAllowsNullExternalPositionNumber() {
        Clock clock = Clock.fixed(T2, ZoneOffset.UTC);
        OrderId orderId =
                orderRepository
                        .save(
                                CustomerOrder.create(
                                        OrderId.generate(),
                                        OrderNumber.of("ORD-ITEM-NULL-EXT"),
                                        OrderCommercialData.of(
                                                null, null, null, null, null, null, null),
                                        clock))
                        .id();
        OrderItemId itemId =
                itemRepository
                        .save(
                                OrderItem.create(
                                        OrderItemId.generate(),
                                        orderId,
                                        ItemCommercialData.of(null, null, null, null),
                                        OrderedQuantity.of(1),
                                        clock))
                        .id();

        OrderItemDto dto = readAdapter.findOrderItem(itemId).orElseThrow();
        assertNull(dto.productCode());
        assertNull(dto.name());
        assertNull(dto.externalPositionNumber());

        OrderItemDto listed =
                readAdapter
                        .findOrderItems(orderId, PageRequest.firstPage())
                        .content()
                        .getFirst();
        assertNull(listed.externalPositionNumber());
    }

    @Test
    void completeItemRemainsReadableWithoutBehaviorChange() {
        Clock clock = Clock.fixed(T3, ZoneOffset.UTC);
        OrderId orderId =
                orderRepository
                        .save(
                                CustomerOrder.create(
                                        OrderId.generate(),
                                        OrderNumber.of("ORD-ITEM-FULL"),
                                        OrderCommercialData.of(
                                                "C",
                                                "Customer",
                                                "CTR",
                                                "SITE",
                                                null,
                                                OrderDirection.PRIVATE,
                                                CurrencyCode.of("USD")),
                                        clock))
                        .id();
        OrderItemId itemId =
                itemRepository
                        .save(
                                OrderItem.create(
                                        OrderItemId.generate(),
                                        orderId,
                                        ItemCommercialData.of(
                                                ProductCode.of("P-FULL"),
                                                "Full Name",
                                                "note",
                                                "EXT-FULL"),
                                        OrderedQuantity.of(2),
                                        clock))
                        .id();

        OrderItemDto dto = readAdapter.findOrderItem(itemId).orElseThrow();
        assertEquals("P-FULL", dto.productCode());
        assertEquals("Full Name", dto.name());
        assertEquals("note", dto.comments());
        assertEquals("EXT-FULL", dto.externalPositionNumber());
        assertEquals(OrderItemStatus.DRAFT, dto.status());
    }

    @Test
    void incompleteDraftOrderIsReadableViaGetOrderAndSearch() {
        Clock clock = Clock.fixed(T1, ZoneOffset.UTC);
        CustomerOrder incomplete =
                orderRepository.save(
                        CustomerOrder.create(
                                OrderId.generate(),
                                OrderNumber.of("ORD-INCOMPLETE-Q"),
                                OrderCommercialData.of(
                                        null, null, null, null, null, null, null),
                                clock));

        Optional<OrderDto> loaded = queries.getOrder(incomplete.id());
        assertTrue(loaded.isPresent());
        OrderDto dto = loaded.orElseThrow();
        assertEquals("ORD-INCOMPLETE-Q", dto.orderNumber());
        assertEquals(OrderStatus.DRAFT, dto.status());
        assertNull(dto.customerName());
        assertNull(dto.direction());
        assertNull(dto.currency());

        PageResult<OrderSummaryDto> search =
                queries.searchOrders(
                        OrderSearchCriteria.builder().orderNumber("ORD-INCOMPLETE-Q").build(),
                        PageRequest.firstPage());
        assertEquals(1, search.totalElements());
        assertNull(search.content().getFirst().customerName());
    }

    @Test
    void incompleteDraftOrderPersistsCommercialNullsRoundTrip() {
        Clock clock = Clock.fixed(T2, ZoneOffset.UTC);
        OrderId orderId =
                orderRepository
                        .save(
                                CustomerOrder.create(
                                        OrderId.generate(),
                                        OrderNumber.of("ORD-RT-NULL"),
                                        OrderCommercialData.of(
                                                "REF-ONLY",
                                                null,
                                                null,
                                                null,
                                                null,
                                                null,
                                                null),
                                        clock))
                        .id();

        CustomerOrder reloaded = orderRepository.findById(orderId).orElseThrow();
        assertEquals("REF-ONLY", reloaded.commercialData().customerRef());
        assertNull(reloaded.commercialData().customerName());
        assertNull(reloaded.commercialData().direction());
        assertNull(reloaded.commercialData().currency());

        OrderDto queryView = queries.getOrder(orderId).orElseThrow();
        assertNull(queryView.customerName());
        assertNull(queryView.direction());
        assertNull(queryView.currency());
    }

    @Test
    void searchWithoutFiltersReturnsAllOrdersWithDefaultSort() {
        OrderId older = seedOrder("A-1", "CustA", "NameA", OrderStatus.DRAFT, T1);
        OrderId newer = seedOrder("B-1", "CustB", "NameB", OrderStatus.APPROVED, T3);
        seedOrder("C-1", "CustC", "NameC", OrderStatus.CANCELLED, T2);

        PageResult<OrderSummaryDto> page =
                queries.searchOrders(OrderSearchCriteria.empty(), PageRequest.firstPage());
        assertEquals(3, page.totalElements());
        assertEquals(3, page.content().size());
        assertEquals(newer, page.content().get(0).orderId());
        assertEquals(older, page.content().get(2).orderId());
    }

    @Test
    void eachFilterWorksIndependently() {
        seedOrder("FN-1", "REF-X", "Alpha", OrderStatus.DRAFT, T1);
        seedOrder("FN-2", "REF-Y", "Beta", OrderStatus.APPROVED, T2);

        assertEquals(
                1,
                queries.searchOrders(
                                OrderSearchCriteria.builder().orderNumber("FN-1").build(),
                                PageRequest.firstPage())
                        .totalElements());
        assertEquals(
                1,
                queries.searchOrders(
                                OrderSearchCriteria.builder()
                                        .orderStatus(OrderStatus.APPROVED)
                                        .build(),
                                PageRequest.firstPage())
                        .totalElements());
        assertEquals(
                1,
                queries.searchOrders(
                                OrderSearchCriteria.builder().customerRef("REF-X").build(),
                                PageRequest.firstPage())
                        .totalElements());
        assertEquals(
                1,
                queries.searchOrders(
                                OrderSearchCriteria.builder().customerName("Beta").build(),
                                PageRequest.firstPage())
                        .totalElements());
        assertEquals(
                1,
                queries.searchOrders(
                                OrderSearchCriteria.builder().createdFrom(T2).build(),
                                PageRequest.firstPage())
                        .totalElements());
        assertEquals(
                1,
                queries.searchOrders(
                                OrderSearchCriteria.builder().createdTo(T1).build(),
                                PageRequest.firstPage())
                        .totalElements());
    }

    @Test
    void combinedFiltersAndPaginationAndTotalElements() {
        seedOrder("P-1", "R", "N", OrderStatus.DRAFT, T1);
        seedOrder("P-2", "R", "N", OrderStatus.DRAFT, T2);
        seedOrder("P-3", "R", "N", OrderStatus.DRAFT, T3);
        seedOrder("P-4", "OTHER", "N", OrderStatus.DRAFT, T3);

        OrderSearchCriteria criteria =
                OrderSearchCriteria.builder().customerRef("R").orderStatus(OrderStatus.DRAFT).build();
        PageResult<OrderSummaryDto> page0 =
                queries.searchOrders(criteria, PageRequest.of(0, 2));
        PageResult<OrderSummaryDto> page1 =
                queries.searchOrders(criteria, PageRequest.of(1, 2));
        assertEquals(3, page0.totalElements());
        assertEquals(2, page0.content().size());
        assertEquals(3, page1.totalElements());
        assertEquals(1, page1.content().size());
        assertEquals("P-3", page0.content().get(0).orderNumber());
    }

    @Test
    void stableCustomSortByOrderNumberAsc() {
        seedOrder("Z-9", "R", "N", OrderStatus.DRAFT, T2);
        seedOrder("A-1", "R", "N", OrderStatus.DRAFT, T1);
        PageResult<OrderSummaryDto> page =
                queries.searchOrders(
                        OrderSearchCriteria.empty(),
                        PageRequest.of(
                                0,
                                10,
                                OrderSort.of(
                                        new OrderSort.Order(
                                                OrderSort.Field.ORDER_NUMBER, OrderSort.Direction.ASC),
                                        new OrderSort.Order(
                                                OrderSort.Field.ORDER_ID, OrderSort.Direction.ASC))));
        assertEquals("A-1", page.content().get(0).orderNumber());
        assertEquals("Z-9", page.content().get(1).orderNumber());
    }

    @Test
    void emptySearchReturnsEmptyPage() {
        PageResult<OrderSummaryDto> page =
                queries.searchOrders(OrderSearchCriteria.empty(), PageRequest.firstPage());
        assertEquals(0, page.totalElements());
        assertTrue(page.content().isEmpty());
        assertTrue(queries.getOrder(OrderId.generate()).isEmpty());
    }

    @Test
    void getOrderAndItemsAndHideDraftRevision() {
        OrderId orderId = seedOrder("ORD-Q", "C", "Customer", OrderStatus.ACTIVE, T1);
        OrderItemId itemId = seedItemWithDraftAndApproved(orderId);

        assertEquals("ORD-Q", queries.getOrder(orderId).orElseThrow().orderNumber());
        PageResult<OrderItemDto> items = queries.getOrderItems(orderId, PageRequest.firstPage());
        assertEquals(1, items.totalElements());
        assertEquals(itemId, items.content().getFirst().orderItemId());
        assertEquals(Optional.of(RevisionNumber.first()), items.content().getFirst().activeRevisionNumber());

        PageResult<OrderItemRevisionDto> revisions =
                queries.getOrderItemRevisions(itemId, PageRequest.firstPage());
        assertEquals(1, revisions.totalElements());
        assertEquals(RevisionStatus.ACTIVE, revisions.content().getFirst().status());
        assertEquals(RevisionNumber.first(), revisions.content().getFirst().revisionNumber());

        assertTrue(
                queries.getOrderItemRevision(itemId, RevisionNumber.of(2)).isEmpty(),
                "Draft revision must not be exposed");
        assertTrue(queries.getOrderItemRevision(itemId, RevisionNumber.first()).isPresent());

        OrderItemRevisionDto active =
                queries.getActiveOrderItemRevision(itemId).orElseThrow();
        assertEquals(RevisionNumber.first(), active.revisionNumber());
        assertEquals(RevisionStatus.ACTIVE, active.status());
    }

    @Test
    void draftSpecificationHiddenAndApprovedLinesKeepOrder() {
        OrderId orderId = seedOrder("ORD-SPEC", "C", "Customer", OrderStatus.ACTIVE, T1);
        OrderItemId itemId = seedItemWithDraftAndApproved(orderId);

        assertTrue(
                queries.getItemSpecification(itemId, RevisionNumber.of(2)).isEmpty(),
                "Draft specification must not be exposed");

        ItemSpecificationDto spec =
                queries.getItemSpecification(itemId, RevisionNumber.first()).orElseThrow();
        assertEquals(2, spec.lines().size());
        assertEquals("M1", spec.lines().get(0).materialCode());
        assertEquals("M2", spec.lines().get(1).materialCode());
    }

    private OrderId seedOrder(
            String number,
            String customerRef,
            String customerName,
            OrderStatus status,
            Instant createdAt) {
        Clock clock = Clock.fixed(createdAt, ZoneOffset.UTC);
        CustomerOrder created =
                CustomerOrder.create(
                        OrderId.generate(),
                        OrderNumber.of(number),
                        OrderCommercialData.of(
                                customerRef,
                                customerName,
                                "CTR",
                                "SITE",
                                "Mgr",
                                OrderDirection.PRIVATE,
                                CurrencyCode.of("USD")),
                        clock);
        CustomerOrder withStatus =
                switch (status) {
                    case DRAFT -> created;
                    case APPROVED -> created.approve(clock);
                    case ACTIVE -> created.approve(clock).activate(clock);
                    case CANCELLED -> created.cancel(clock);
                };
        return orderRepository.save(withStatus).id();
    }

    private OrderItemId seedItemWithDraftAndApproved(OrderId orderId) {
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber rev1 = RevisionNumber.first();
        RevisionNumber rev2 = RevisionNumber.of(2);
        ItemSpecification approvedSpec =
                ItemSpecification.rehydrate(
                        itemId,
                        rev1,
                        List.of(
                                IntakeContractFixtures.specLine("M1", "Steel", new BigDecimal("1"), "kg"),
                                IntakeContractFixtures.specLine("M2", "Paint", new BigDecimal("2"), "l")),
                        true);
        ItemSpecification draftSpec =
                ItemSpecification.rehydrate(
                        itemId,
                        rev2,
                        List.of(
                                IntakeContractFixtures.specLine("M9", "Hidden", new BigDecimal("9"), "pcs")),
                        false);
        Map<RevisionNumber, OrderItemRevision> revisions = new LinkedHashMap<>();
        revisions.put(
                rev1,
                OrderItemRevision.rehydrate(
                        itemId,
                        rev1,
                        RevisionStatus.ACTIVE,
                        OrderedQuantity.of(5),
                        null,
                        approvedSpec));
        revisions.put(
                rev2,
                OrderItemRevision.rehydrate(
                        itemId,
                        rev2,
                        RevisionStatus.DRAFT,
                        OrderedQuantity.of(7),
                        rev1,
                        draftSpec));
        OrderItem item =
                OrderItem.rehydrate(
                        itemId,
                        orderId,
                        ItemCommercialData.of(ProductCode.of("P-1"), "Panel", "c"),
                        OrderItemStatus.ACTIVE,
                        rev1,
                        rev2,
                        revisions,
                        0L,
                        T1,
                        T1);
        return itemRepository.save(item).id();
    }

    @Test
    void getCurrentItemSpecificationReturnsProductionDto() {
        OrderId orderId = seedOrder("ORD-PROD-SPEC", "CR", "Cust", OrderStatus.ACTIVE, T1);
        OrderItemId itemId = seedItemWithDraftAndApproved(orderId);

        Optional<ProductionSpecificationDto> result =
                queries.getCurrentItemSpecification(itemId);

        assertTrue(result.isPresent());
        ProductionSpecificationDto dto = result.orElseThrow();
        assertNotNull(dto.specificationId());
        assertEquals(itemId, dto.orderItemId());
        assertEquals(new BigDecimal("5.000000"), dto.orderedQuantity());
        assertEquals(2, dto.lines().size());
    }

    @Test
    void getCurrentItemSpecificationReturnsEmptyForNoActiveRevision() {
        OrderId orderId = seedOrder("ORD-NO-ACTIVE", "CR", "Cust", OrderStatus.DRAFT, T1);
        OrderItemId itemId = OrderItemId.generate();
        OrderItem item =
                OrderItem.create(
                        itemId,
                        orderId,
                        ItemCommercialData.of(ProductCode.of("P-X"), "NoRev", ""),
                        OrderedQuantity.of(1),
                        Clock.fixed(T1, ZoneOffset.UTC));
        itemRepository.save(item);

        assertTrue(queries.getCurrentItemSpecification(itemId).isEmpty());
    }

    @Test
    void getSpecificationByIdReturnsCorrectSpec() {
        OrderId orderId = seedOrder("ORD-BY-SPECID", "CR", "Cust", OrderStatus.ACTIVE, T1);
        OrderItemId itemId = seedItemWithDraftAndApproved(orderId);

        ProductionSpecificationDto current =
                queries.getCurrentItemSpecification(itemId).orElseThrow();
        SpecificationId specId = current.specificationId();

        Optional<ProductionSpecificationDto> byId = queries.getSpecificationById(specId);
        assertTrue(byId.isPresent());
        assertEquals(specId, byId.orElseThrow().specificationId());
        assertEquals(2, byId.orElseThrow().lines().size());
    }

    @Test
    void getSpecificationByIdReturnsEmptyForUnknown() {
        assertTrue(
                queries.getSpecificationById(
                                SpecificationId.of(java.util.UUID.randomUUID()))
                        .isEmpty());
    }

    @Test
    void specificationIdIsStableAcrossReloads() {
        OrderId orderId = seedOrder("ORD-STABLE-ID", "CR", "Cust", OrderStatus.ACTIVE, T1);
        OrderItemId itemId = seedItemWithDraftAndApproved(orderId);

        SpecificationId first =
                queries.getCurrentItemSpecification(itemId).orElseThrow().specificationId();
        SpecificationId second =
                queries.getCurrentItemSpecification(itemId).orElseThrow().specificationId();

        assertEquals(first, second);
    }

    @Test
    void legacySpecificationApiStillWorks() {
        OrderId orderId = seedOrder("ORD-LEGACY", "CR", "Cust", OrderStatus.ACTIVE, T1);
        OrderItemId itemId = seedItemWithDraftAndApproved(orderId);

        Optional<ItemSpecificationDto> legacy =
                queries.getItemSpecification(itemId, RevisionNumber.first());
        assertTrue(legacy.isPresent());
        assertEquals(RevisionNumber.first(), legacy.orElseThrow().revisionNumber());
        assertEquals(2, legacy.orElseThrow().lines().size());
    }

    private enum AllowingAuthorization implements AuthorizationService {
        INSTANCE;

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return true;
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            // allow all for Query API persistence integration tests
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }
}
