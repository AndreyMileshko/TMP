package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSort;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.RevisionNumber;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderListViewModelTest {

    @Test
    void loadsOrdersThroughOrderQueryServiceWithDefaultPageSizeAndSort() {
        FakeOrderQuery query = new FakeOrderQuery();
        for (int i = 0; i < 3; i++) {
            query.orders.add(summary("O-" + i));
        }
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        assertEquals(3, viewModel.orders().size());
        assertEquals(PageRequest.DEFAULT_PAGE_SIZE, query.lastPageRequest.pageSize());
        assertEquals(0, query.lastPageRequest.pageIndex());
        assertEquals(OrderSort.defaultSort().orders(), query.lastPageRequest.orderSort().orders());
        assertTrue(query.lastCriteria.orderNumber().isEmpty());
    }

    @Test
    void mapsFiltersToOrderSearchCriteriaAndResetsPage() {
        FakeOrderQuery query = new FakeOrderQuery();
        for (int i = 0; i < 60; i++) {
            query.orders.add(summary("O-" + i));
        }
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        viewModel.nextPage();
        assertEquals(1, viewModel.pageIndexProperty().get());

        viewModel.orderNumberFilterProperty().set("  A-1  ");
        viewModel.orderStatusFilterProperty().set("draft");
        viewModel.customerRefFilterProperty().set("C-1");
        viewModel.customerNameFilterProperty().set("Acme");
        viewModel.createdFromFilterProperty().set("2026-01-01T00:00:00Z");
        viewModel.createdToFilterProperty().set("2026-12-31T00:00:00Z");
        viewModel.applyFilters();

        assertEquals(0, viewModel.pageIndexProperty().get());
        assertEquals(Optional.of("A-1"), query.lastCriteria.orderNumber());
        assertEquals(Optional.of(OrderStatus.DRAFT), query.lastCriteria.orderStatus());
        assertEquals(Optional.of("C-1"), query.lastCriteria.customerRef());
        assertEquals(Optional.of("Acme"), query.lastCriteria.customerName());
        assertTrue(query.lastCriteria.createdFrom().isPresent());
        assertTrue(query.lastCriteria.createdTo().isPresent());
    }

    @Test
    void clearFiltersResetsCriteriaAndPage() {
        FakeOrderQuery query = new FakeOrderQuery();
        query.orders.add(summary("O-1"));
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        viewModel.orderNumberFilterProperty().set("X");
        viewModel.customerNameFilterProperty().set("Y");
        viewModel.pageIndexProperty().set(2);
        viewModel.clearFilters();
        assertEquals("", viewModel.orderNumberFilterProperty().get());
        assertEquals("", viewModel.customerNameFilterProperty().get());
        assertEquals(0, viewModel.pageIndexProperty().get());
        assertTrue(query.lastCriteria.orderNumber().isEmpty());
        assertTrue(query.lastCriteria.customerName().isEmpty());
    }

    @Test
    void blankFiltersBecomeAbsent() {
        FakeOrderQuery query = new FakeOrderQuery();
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        viewModel.orderNumberFilterProperty().set("   ");
        viewModel.customerRefFilterProperty().set("");
        OrderSearchCriteria criteria = viewModel.toSearchCriteriaForTest();
        assertTrue(criteria.orderNumber().isEmpty());
        assertTrue(criteria.customerRef().isEmpty());
        assertTrue(criteria.orderStatus().isEmpty());
    }

    @Test
    void invalidDateRangeIsNotSentToService() {
        FakeOrderQuery query = new FakeOrderQuery();
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        int callsAfterConstruct = query.searchCalls;
        viewModel.createdFromFilterProperty().set("2026-12-31T00:00:00Z");
        viewModel.createdToFilterProperty().set("2026-01-01T00:00:00Z");
        viewModel.applyFilters();
        assertEquals(callsAfterConstruct, query.searchCalls);
        assertTrue(viewModel.errorMessageProperty().get().contains("диапазон"));
    }

    @Test
    void paginationStopsAtBoundaries() {
        FakeOrderQuery query = new FakeOrderQuery();
        for (int i = 0; i < 55; i++) {
            query.orders.add(summary("O-" + i));
        }
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        assertEquals(0, viewModel.pageIndexProperty().get());
        assertFalse(viewModel.canGoPreviousProperty().get());
        assertTrue(viewModel.canGoNextProperty().get());
        viewModel.previousPage();
        assertEquals(0, viewModel.pageIndexProperty().get());
        viewModel.nextPage();
        assertEquals(1, viewModel.pageIndexProperty().get());
        assertTrue(viewModel.canGoPreviousProperty().get());
        assertFalse(viewModel.canGoNextProperty().get());
        viewModel.nextPage();
        assertEquals(1, viewModel.pageIndexProperty().get());
    }

    @Test
    void constructorDoesNotQueryAndOpenTriggersExactlyOneSearch() {
        FakeOrderQuery query = new FakeOrderQuery();
        query.orders.add(summary("O-1"));
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        assertEquals(0, query.searchCalls);
        viewModel.refresh();
        assertEquals(1, query.searchCalls);
        assertEquals(1, viewModel.orders().size());
    }

    @Test
    void emptyResultSetsEmptyState() {
        FakeOrderQuery query = new FakeOrderQuery();
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        assertTrue(viewModel.emptyResultProperty().get());
        assertEquals("Заказы не найдены", viewModel.statusMessageProperty().get());
    }

    @Test
    void accessDeniedIsSurfacedWithoutThrowing() {
        FakeOrderQuery query = new FakeOrderQuery();
        query.deny = true;
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.refresh();
        assertTrue(viewModel.orders().isEmpty());
        assertEquals(
                OrderUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
        assertEquals(
                OrderUiErrorMapper.LIST_REFRESH_FAILED, viewModel.statusMessageProperty().get());
        assertFalse(viewModel.emptyResultProperty().get());
    }

    @Test
    void pageSizeIsClampedToMax() {
        FakeOrderQuery query = new FakeOrderQuery();
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.pageSizeProperty().set(500);
        viewModel.refresh();
        assertEquals(PageRequest.MAX_PAGE_SIZE, query.lastPageRequest.pageSize());
        assertEquals(PageRequest.MAX_PAGE_SIZE, viewModel.pageSizeProperty().get());
    }

    @Test
    void rejectsUnknownSortFieldWithoutBuildingSql() {
        FakeOrderQuery query = new FakeOrderQuery();
        OrderListViewModel viewModel = new OrderListViewModel(query, new FakeAuthorization());
        viewModel.sortFieldProperty().set("productionStatus");
        viewModel.refresh();
        assertFalse(viewModel.errorMessageProperty().get().isBlank());
        assertThrows(IllegalArgumentException.class, () -> OrderSort.of("productionStatus", "ASC"));
    }

    @Test
    void viewModelUsesOnlyPublicQueryAndSecurityDependencies() throws Exception {
        Field[] fields = OrderListViewModel.class.getDeclaredFields();
        boolean foundQuery = false;
        boolean foundAuthz = false;
        for (Field field : fields) {
            Class<?> type = field.getType();
            String name = type.getName();
            assertFalse(name.contains("JdbcTemplate"), "UI must not use JdbcTemplate");
            assertFalse(name.contains("Repository"), "UI must not use repositories");
            assertFalse(name.contains("persistence"), "UI must not use persistence");
            assertFalse(name.startsWith("com.tmp.order.domain"), "UI must not use domain aggregates");
            if (type == OrderQueryService.class) {
                foundQuery = true;
            }
            if (type == AuthorizationService.class) {
                foundAuthz = true;
            }
        }
        assertTrue(foundQuery);
        assertTrue(foundAuthz);
    }

    private static OrderSummaryDto summary(String number) {
        return OrderSummaryDto.of(
                OrderId.generate(),
                number,
                OrderStatus.DRAFT,
                "ref-" + number,
                "Customer " + number,
                Instant.parse("2026-07-01T10:00:00Z"));
    }

    static final class FakeOrderQuery implements OrderQueryService {
        private final List<OrderSummaryDto> orders = new ArrayList<>();
        private OrderSearchCriteria lastCriteria;
        private PageRequest lastPageRequest;
        private int searchCalls;
        private boolean deny;

        @Override
        public PageResult<OrderSummaryDto> searchOrders(
                OrderSearchCriteria criteria, PageRequest pageRequest) {
            if (deny) {
                throw new AccessDeniedException("Access denied for permission: order.order.view");
            }
            searchCalls++;
            lastCriteria = criteria;
            lastPageRequest = pageRequest;
            int from = pageRequest.pageIndex() * pageRequest.pageSize();
            int to = Math.min(orders.size(), from + pageRequest.pageSize());
            List<OrderSummaryDto> page =
                    from >= orders.size() ? List.of() : orders.subList(from, to);
            return PageResult.of(page, pageRequest.pageIndex(), pageRequest.pageSize(), orders.size());
        }

        @Override
        public Optional<OrderDto> getOrder(OrderId orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OrderItemDto> getOrderItem(OrderItemId orderItemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageResult<OrderItemRevisionDto> getOrderItemRevisions(
                OrderItemId orderItemId, PageRequest pageRequest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OrderItemRevisionDto> getOrderItemRevision(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<OrderItemRevisionDto> getActiveOrderItemRevision(OrderItemId orderItemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ItemSpecificationDto> getItemSpecification(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> getCurrentItemSpecification(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.ProductionSpecificationDto> getSpecificationById(
                com.tmp.order.api.SpecificationId specificationId) {
            return Optional.empty();
        }

        @Override
        public Optional<com.tmp.order.api.OrderForProductionDto> getOrderForProduction(OrderId orderId) {
            return Optional.empty();
        }
    }
}
