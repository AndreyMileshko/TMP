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
    void productionAccessDeniedStillReturnsEditingAndAwaitingRows() {
        InMemoryWorklistQuery worklist = new InMemoryWorklistQuery();
        worklist.rows.add(row(OrderId.generate(), "D-1", OrderStatus.DRAFT, 1L));
        MapProductionQuery production = new MapProductionQuery() {
            @Override
            public java.util.Map<UUID, OrderProductionListFacts> getOrderProductionListFacts(
                    java.util.Collection<UUID> orderIds) {
                throw new AccessDeniedException("production.order.view");
            }
        };
        OrderOperationalListService service = new OrderOperationalListService(worklist, production);
        OrderOperationalListResult result =
                service.search(request(Set.of(OrderOperationalStatus.EDITING), 0, 50));
        assertEquals(1, result.totalElements());
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
