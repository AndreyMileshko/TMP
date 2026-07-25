package com.tmp.order.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageRequestAndSearchContractTest {

    @Test
    void firstPageUsesDefaultSizeFiftyAndZeroBasedIndex() {
        PageRequest request = PageRequest.firstPage();
        assertEquals(0, request.pageIndex());
        assertEquals(PageRequest.DEFAULT_PAGE_SIZE, request.pageSize());
        assertEquals(50, request.pageSize());
    }

    @Test
    void maxPageSizeIsOneHundred() {
        assertEquals(100, PageRequest.MAX_PAGE_SIZE);
        PageRequest request = PageRequest.of(0, 100);
        assertEquals(100, request.pageSize());
    }

    @Test
    void rejectsNegativePageIndex() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(-1, 50));
    }

    @Test
    void rejectsPageSizeBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 0));
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, -5));
    }

    @Test
    void rejectsPageSizeAboveMaximum() {
        assertThrows(IllegalArgumentException.class, () -> PageRequest.of(0, 101));
    }

    @Test
    void defaultSortIsCreatedAtDescThenOrderIdDesc() {
        List<OrderSort.Order> orders = OrderSort.defaultSort().orders();
        assertEquals(2, orders.size());
        assertEquals(OrderSort.Field.CREATED_AT, orders.get(0).field());
        assertEquals(OrderSort.Direction.DESC, orders.get(0).direction());
        assertEquals(OrderSort.Field.ORDER_ID, orders.get(1).field());
        assertEquals(OrderSort.Direction.DESC, orders.get(1).direction());
        assertEquals(OrderSort.defaultSort().orders(), PageRequest.firstPage().orderSort().orders());
    }

    @Test
    void sortWhitelistAcceptsKnownFieldsAndDirections() {
        OrderSort sort = OrderSort.of("orderNumber", "ASC");
        assertEquals(OrderSort.Field.ORDER_NUMBER, sort.orders().get(0).field());
        assertEquals(OrderSort.Direction.ASC, sort.orders().get(0).direction());
        assertEquals(OrderSort.Field.STATUS, OrderSort.Field.fromApiName("status"));
    }

    @Test
    void unknownSortFieldIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OrderSort.of("productionStatus", "ASC"));
        assertThrows(IllegalArgumentException.class, () -> OrderSort.Field.fromApiName("stock"));
    }

    @Test
    void unknownSortDirectionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> OrderSort.of("createdAt", "UP"));
        assertThrows(IllegalArgumentException.class, () -> OrderSort.Direction.fromApiName("SIDEWAYS"));
    }

    @Test
    void searchCriteriaNormalizesBlankOptionalStrings() {
        OrderSearchCriteria criteria = OrderSearchCriteria.builder()
                .orderNumber("  ")
                .customerRef("")
                .customerName("  Acme  ")
                .orderStatus(OrderStatus.DRAFT)
                .build();
        assertTrue(criteria.orderNumber().isEmpty());
        assertTrue(criteria.customerRef().isEmpty());
        assertEquals("Acme", criteria.customerName().orElseThrow());
        assertEquals(OrderStatus.DRAFT, criteria.orderStatus().orElseThrow());
    }

    @Test
    void searchCriteriaEmptyHasNoFilters() {
        OrderSearchCriteria empty = OrderSearchCriteria.empty();
        assertTrue(empty.orderNumber().isEmpty());
        assertTrue(empty.orderStatus().isEmpty());
        assertTrue(empty.customerRef().isEmpty());
        assertTrue(empty.customerName().isEmpty());
        assertTrue(empty.createdFrom().isEmpty());
        assertTrue(empty.createdTo().isEmpty());
    }

    @Test
    void searchCriteriaRejectsInvertedCreatedRange() {
        Instant from = Instant.parse("2026-07-25T12:00:00Z");
        Instant to = Instant.parse("2026-07-25T10:00:00Z");
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderSearchCriteria.builder().createdFrom(from).createdTo(to).build());
    }

    @Test
    void searchOrdersIsDeclaredOnQueryService() throws Exception {
        var method = OrderQueryService.class.getMethod(
                "searchOrders", OrderSearchCriteria.class, PageRequest.class);
        assertEquals(PageResult.class, method.getReturnType());
    }

    @Test
    void pageResultRejectsNegativeIndex() {
        assertThrows(IllegalArgumentException.class, () -> PageResult.of(List.of(), -1, 50, 0L));
    }
}
