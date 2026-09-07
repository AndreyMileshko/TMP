package com.tmp.ui.shell.order.worklist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.ui.shell.screen.orderlist.OrderListTestSupport.InMemoryWorklistQuery;
import com.tmp.ui.shell.screen.orderlist.OrderListTestSupport.MapProductionQuery;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderOperationalListServiceTest {

    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

    @Test
    void paginatesAfterOperationalStatusFilter() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        MapProductionQuery production = new MapProductionQuery();
        for (int i = 0; i < 5; i++) {
            OrderId id = OrderId.generate();
            worklist.rows.add(row(id, "D-" + i, OrderStatus.DRAFT, 1L));
        }
        for (int i = 0; i < 5; i++) {
            OrderId id = OrderId.generate();
            worklist.rows.add(row(id, "A-" + i, OrderStatus.ACTIVE, 10L));
            production.put(
                    id,
                    facts(id.value(), OrderProductionViewStatus.MANUFACTURED, 10L, 10L, 0L, false));
        }
        OrderOperationalListService service = new OrderOperationalListService(worklist, production);
        OrderOperationalListResult page0 =
                service.search(request(Set.of(OrderOperationalStatus.COMPLETED), 0, 3));
        assertEquals(5, page0.totalElements());
        assertEquals(3, page0.content().size());
        assertTrue(page0.content().stream().allMatch(row -> row.operationalStatus() == OrderOperationalStatus.COMPLETED));
        OrderOperationalListResult page1 =
                service.search(request(Set.of(OrderOperationalStatus.COMPLETED), 1, 3));
        assertEquals(5, page1.totalElements());
        assertEquals(2, page1.content().size());
        assertEquals(2, production.batchCalls);
        assertEquals(0, production.viewCalls);
    }

    @Test
    void combinedFiltersApplyBeforePagination() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        OrderId alpha = OrderId.generate();
        OrderId beta = OrderId.generate();
        worklist.rows.add(
                OrderWorklistRowDto.of(
                        alpha,
                        "TMP-100",
                        OrderStatus.DRAFT,
                        "c-alpha",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z"),
                        2L));
        worklist.rows.add(
                OrderWorklistRowDto.of(
                        beta,
                        "TMP-200",
                        OrderStatus.DRAFT,
                        "c-beta",
                        "Beta",
                        Instant.parse("2026-09-01T11:00:00Z"),
                        3L));
        worklist.rows.add(
                OrderWorklistRowDto.of(
                        OrderId.generate(),
                        "OTHER",
                        OrderStatus.DRAFT,
                        "c-alpha",
                        "Alpha",
                        Instant.parse("2026-09-01T12:00:00Z"),
                        1L));
        OrderOperationalListService service =
                new OrderOperationalListService(worklist, new MapProductionQuery());
        OrderOperationalListResult result =
                service.search(
                        new OrderOperationalListRequest(
                                FROM,
                                TO,
                                "tmp",
                                EnumSet.of(OrderOperationalStatus.EDITING),
                                Set.of("c-alpha"),
                                false,
                                true,
                                0,
                                50));
        assertEquals(1, result.totalElements());
        assertEquals("TMP-100", result.content().getFirst().orderNumber());
    }

    @Test
    void quickSearchIsOrOfNumberAndCustomerName() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        worklist.rows.add(row(OrderId.generate(), "ABC-1", OrderStatus.DRAFT, 1L, "c1", "Monolith"));
        worklist.rows.add(row(OrderId.generate(), "ZZZ", OrderStatus.DRAFT, 1L, "c2", "Alpha"));
        worklist.rows.add(row(OrderId.generate(), "NOPE", OrderStatus.DRAFT, 1L, "c3", "Other"));
        OrderOperationalListService service =
                new OrderOperationalListService(worklist, new MapProductionQuery());
        OrderOperationalListResult byNumber =
                service.search(request("abc", EnumSet.allOf(OrderOperationalStatus.class)));
        assertEquals(1, byNumber.totalElements());
        OrderOperationalListResult byName =
                service.search(request("alpha", EnumSet.allOf(OrderOperationalStatus.class)));
        assertEquals(1, byName.totalElements());
        OrderOperationalListResult none =
                service.search(request("missing", EnumSet.allOf(OrderOperationalStatus.class)));
        assertEquals(0, none.totalElements());
    }

    @Test
    void productionAccessDeniedStillReturnsEditingAndUnavailableActiveRows() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        worklist.rows.add(row(OrderId.generate(), "D-1", OrderStatus.DRAFT, 1L));
        OrderId activeId = OrderId.generate();
        worklist.rows.add(row(activeId, "A-1", OrderStatus.ACTIVE, 10L));
        MapProductionQuery production = new MapProductionQuery() {
            @Override
            public java.util.Map<UUID, OrderProductionListFacts> getOrderProductionListFacts(
                    java.util.Collection<UUID> orderIds) {
                throw new AccessDeniedException("production.order.view");
            }
        };
        OrderOperationalListService service = new OrderOperationalListService(worklist, production);
        OrderOperationalListResult editingFilter =
                service.search(request(Set.of(OrderOperationalStatus.EDITING), 0, 50));
        // STATUS_UNAVAILABLE is always included even when not in the checkbox filter.
        assertEquals(2, editingFilter.totalElements());
        assertEquals(
                OrderOperationalListResult.ProductionFactsState.ACCESS_DENIED,
                editingFilter.productionFactsState());
        assertTrue(
                editingFilter.content().stream()
                        .anyMatch(row -> row.operationalStatus() == OrderOperationalStatus.EDITING));
        assertTrue(
                editingFilter.content().stream()
                        .anyMatch(
                                row ->
                                        row.operationalStatus()
                                                == OrderOperationalStatus.STATUS_UNAVAILABLE));

        OrderOperationalListResult defaults =
                service.search(
                        request(
                                Set.of(
                                        OrderOperationalStatus.EDITING,
                                        OrderOperationalStatus.AWAITING_PRODUCTION),
                                0,
                                50));
        assertEquals(2, defaults.totalElements());
        assertTrue(
                defaults.content().stream()
                        .anyMatch(
                                row ->
                                        row.orderNumber().equals("A-1")
                                                && row.operationalStatus()
                                                        == OrderOperationalStatus.STATUS_UNAVAILABLE));
        assertTrue(
                defaults.content().stream()
                        .noneMatch(
                                row ->
                                        row.operationalStatus()
                                                == OrderOperationalStatus.AWAITING_PRODUCTION));
    }

    @Test
    void productionTechnicalFailureDoesNotFabricateAwaitingProduction() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        worklist.rows.add(row(OrderId.generate(), "A-1", OrderStatus.ACTIVE, 10L));
        MapProductionQuery production = new MapProductionQuery() {
            @Override
            public java.util.Map<UUID, OrderProductionListFacts> getOrderProductionListFacts(
                    java.util.Collection<UUID> orderIds) {
                throw new IllegalStateException("production down");
            }
        };
        OrderOperationalListService service = new OrderOperationalListService(worklist, production);
        OrderOperationalListResult result =
                service.search(request(Set.of(OrderOperationalStatus.AWAITING_PRODUCTION), 0, 50));
        assertEquals(1, result.totalElements());
        assertEquals(OrderOperationalStatus.STATUS_UNAVAILABLE, result.content().getFirst().operationalStatus());
        assertEquals(
                OrderOperationalListResult.ProductionFactsState.TECHNICAL_FAILURE,
                result.productionFactsState());
        assertTrue(result.technicalFailure().isPresent());
    }

    @Test
    void successfulProductionReadStillDerivesAwaitingWhenNotAccepted() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        OrderId id = OrderId.generate();
        worklist.rows.add(row(id, "A-1", OrderStatus.ACTIVE, 10L));
        MapProductionQuery production = new MapProductionQuery();
        production.put(
                id,
                facts(id.value(), OrderProductionViewStatus.NOT_ACCEPTED, 10L, 0L, 0L, false));
        OrderOperationalListService service = new OrderOperationalListService(worklist, production);
        OrderOperationalListResult result =
                service.search(request(Set.of(OrderOperationalStatus.AWAITING_PRODUCTION), 0, 50));
        assertEquals(1, result.totalElements());
        assertEquals(OrderOperationalStatus.AWAITING_PRODUCTION, result.content().getFirst().operationalStatus());
        assertEquals(
                OrderOperationalListResult.ProductionFactsState.AVAILABLE, result.productionFactsState());
    }

    @Test
    void sortsFullMatchedSetBeforePagination() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        for (int i = 1; i <= 120; i++) {
            worklist.rows.add(
                    OrderWorklistRowDto.of(
                            OrderId.generate(),
                            "N-" + i,
                            OrderStatus.DRAFT,
                            "c",
                            "C",
                            Instant.parse("2026-09-01T10:00:00Z").plusSeconds(i),
                            i));
        }
        OrderOperationalListService service =
                new OrderOperationalListService(worklist, new MapProductionQuery());
        OrderOperationalListResult page0 =
                service.search(
                        new OrderOperationalListRequest(
                                FROM,
                                TO,
                                null,
                                EnumSet.of(OrderOperationalStatus.EDITING),
                                Set.of(),
                                Set.of(),
                                false,
                                false,
                                0,
                                50,
                                OrderListSortField.ITEM_COUNT,
                                OrderListSortDirection.ASC));
        OrderOperationalListResult page1 =
                service.search(
                        new OrderOperationalListRequest(
                                FROM,
                                TO,
                                null,
                                EnumSet.of(OrderOperationalStatus.EDITING),
                                Set.of(),
                                Set.of(),
                                false,
                                false,
                                1,
                                50,
                                OrderListSortField.ITEM_COUNT,
                                OrderListSortDirection.ASC));
        OrderOperationalListResult page2 =
                service.search(
                        new OrderOperationalListRequest(
                                FROM,
                                TO,
                                null,
                                EnumSet.of(OrderOperationalStatus.EDITING),
                                Set.of(),
                                Set.of(),
                                false,
                                false,
                                2,
                                50,
                                OrderListSortField.ITEM_COUNT,
                                OrderListSortDirection.ASC));
        assertEquals(120, page0.totalElements());
        assertEquals(1L, page0.content().getFirst().itemQuantity());
        assertEquals(50L, page0.content().getLast().itemQuantity());
        assertEquals(51L, page1.content().getFirst().itemQuantity());
        assertEquals(100L, page1.content().getLast().itemQuantity());
        assertEquals(101L, page2.content().getFirst().itemQuantity());
        assertEquals(120L, page2.content().getLast().itemQuantity());
    }

    @Test
    void statusSortUsesBusinessOrderAcrossPages() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        MapProductionQuery production = new MapProductionQuery();
        addStatusFixture(worklist, production);
        OrderOperationalListService service = new OrderOperationalListService(worklist, production);
        OrderOperationalListResult page0 =
                service.search(
                        new OrderOperationalListRequest(
                                FROM,
                                TO,
                                null,
                                EnumSet.allOf(OrderOperationalStatus.class),
                                Set.of(),
                                Set.of(),
                                false,
                                false,
                                0,
                                3,
                                OrderListSortField.STATUS,
                                OrderListSortDirection.ASC));
        OrderOperationalListResult page1 =
                service.search(
                        new OrderOperationalListRequest(
                                FROM,
                                TO,
                                null,
                                EnumSet.allOf(OrderOperationalStatus.class),
                                Set.of(),
                                Set.of(),
                                false,
                                false,
                                1,
                                3,
                                OrderListSortField.STATUS,
                                OrderListSortDirection.ASC));
        assertEquals(OrderOperationalStatus.EDITING, page0.content().get(0).operationalStatus());
        assertEquals(OrderOperationalStatus.AWAITING_PRODUCTION, page0.content().get(1).operationalStatus());
        assertEquals(OrderOperationalStatus.IN_PRODUCTION, page0.content().get(2).operationalStatus());
        assertEquals(OrderOperationalStatus.PARTIALLY_COMPLETED, page1.content().get(0).operationalStatus());
        assertEquals(OrderOperationalStatus.COMPLETED, page1.content().get(1).operationalStatus());
        assertEquals(OrderOperationalStatus.CANCELLED, page1.content().get(2).operationalStatus());
    }

    private static void addStatusFixture(InMemoryWorklistQuery worklist, MapProductionQuery production) {
        OrderId doneId = OrderId.generate();
        worklist.rows.add(row(doneId, "done", OrderStatus.ACTIVE, 10L));
        production.put(doneId, facts(doneId.value(), OrderProductionViewStatus.MANUFACTURED, 10L, 10L, 0L, false));

        worklist.rows.add(row(OrderId.generate(), "edit", OrderStatus.DRAFT, 1L));
        worklist.rows.add(row(OrderId.generate(), "cancel", OrderStatus.CANCELLED, 1L));

        OrderId prodId = OrderId.generate();
        worklist.rows.add(row(prodId, "prod", OrderStatus.ACTIVE, 10L));
        production.put(
                prodId, facts(prodId.value(), OrderProductionViewStatus.IN_PRODUCTION, 10L, 3L, 7L, false));

        OrderId awaitId = OrderId.generate();
        worklist.rows.add(row(awaitId, "await", OrderStatus.ACTIVE, 10L));
        production.put(
                awaitId, facts(awaitId.value(), OrderProductionViewStatus.NOT_ACCEPTED, 10L, 0L, 0L, false));

        OrderId partialId = OrderId.generate();
        worklist.rows.add(row(partialId, "partial", OrderStatus.ACTIVE, 10L));
        production.put(
                partialId,
                facts(partialId.value(), OrderProductionViewStatus.CANCELLED, 10L, 4L, 0L, true));
    }

    private static OrderOperationalListRequest request(Set<OrderOperationalStatus> statuses, int page, int size) {
        return new OrderOperationalListRequest(
                FROM, TO, null, statuses, Set.of(), false, false, page, size);
    }

    private static OrderOperationalListRequest request(String search, Set<OrderOperationalStatus> statuses) {
        return new OrderOperationalListRequest(
                FROM, TO, search, statuses, Set.of(), false, false, 0, 50);
    }

    private static OrderWorklistRowDto row(OrderId id, String number, OrderStatus status, long qty) {
        return row(id, number, status, qty, "c-1", "Customer");
    }

    private static OrderWorklistRowDto row(
            OrderId id, String number, OrderStatus status, long qty, String ref, String name) {
        return OrderWorklistRowDto.of(
                id, number, status, ref, name, Instant.parse("2026-09-02T10:00:00Z"), qty);
    }

    private static OrderProductionListFacts facts(
            UUID id,
            OrderProductionViewStatus status,
            long ordered,
            long released,
            long active,
            boolean cancelled) {
        return new OrderProductionListFacts(id, status, ordered, released, active, cancelled);
    }
}
