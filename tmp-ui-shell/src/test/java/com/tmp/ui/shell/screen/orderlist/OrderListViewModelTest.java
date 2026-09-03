package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.PageRequest;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.worklist.OrderListMemento;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderListViewModelTest {

    @Test
    void constructorDoesNotQueryUntilRefresh() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(row("O-1"));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        assertEquals(0, worklist.listCalls);
        viewModel.refresh();
        assertEquals(1, worklist.listCalls);
        assertEquals(1, viewModel.orders().size());
    }

    @Test
    void emptyResultUsesHumanReadableMessage() {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel();
        viewModel.refresh();
        assertTrue(viewModel.emptyResultProperty().get());
        assertEquals(
                "Заказы не найдены. Измените период или параметры фильтра.",
                viewModel.statusMessageProperty().get());
        viewModel.quickSearchProperty().set("ABC");
        viewModel.onSearchChanged();
        assertEquals("По вашему запросу заказы не найдены.", viewModel.statusMessageProperty().get());
    }

    @Test
    void accessDeniedIsSurfacedWithoutThrowing() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.deny = new AccessDeniedException("Access denied for permission: order.order.view");
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();
        assertTrue(viewModel.orders().isEmpty());
        assertEquals(OrderUiErrorMapper.ACCESS_DENIED, viewModel.errorMessageProperty().get());
        assertEquals(OrderUiErrorMapper.LIST_REFRESH_FAILED, viewModel.statusMessageProperty().get());
        assertFalse(viewModel.emptyResultProperty().get());
    }

    @Test
    void paginationStopsAtBoundariesAndFilterResetsPage() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        for (int i = 0; i < 55; i++) {
            worklist.rows.add(row("O-" + i));
        }
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();
        assertEquals(0, viewModel.pageIndexProperty().get());
        assertFalse(viewModel.canGoPreviousProperty().get());
        assertTrue(viewModel.canGoNextProperty().get());
        viewModel.nextPage();
        assertEquals(1, viewModel.pageIndexProperty().get());
        viewModel.onSearchChanged();
        assertEquals(0, viewModel.pageIndexProperty().get());
    }

    @Test
    void pageSizeIsClampedToMax() {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel();
        viewModel.pageSizeProperty().set(500);
        viewModel.refresh();
        assertEquals(PageRequest.MAX_PAGE_SIZE, viewModel.pageSizeProperty().get());
    }

    @Test
    void mementoRestoresSearchPageAndSelection() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        for (int i = 0; i < 160; i++) {
            worklist.rows.add(row("O-" + i));
        }
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();
        viewModel.quickSearchProperty().set("O-");
        viewModel.onSearchChanged();
        viewModel.nextPage();
        viewModel.nextPage();
        OrderId selected = viewModel.orders().getFirst().orderId();
        viewModel.selectedOrderProperty().set(viewModel.orders().getFirst());
        OrderListMemento memento = viewModel.captureMemento();
        viewModel.quickSearchProperty().set("");
        viewModel.onSearchChanged();
        viewModel.restoreMemento(memento);
        assertEquals("O-", viewModel.quickSearchProperty().get());
        assertEquals(2, viewModel.pageIndexProperty().get());
        assertEquals(selected, viewModel.selectedOrderProperty().get().orderId());
        assertEquals(OrderListPeriod.Preset.LAST_30_DAYS, viewModel.periodPresetProperty().get());
    }

    @Test
    void defaultStatusesExcludeCancelled() {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel();
        Set<OrderOperationalStatus> statuses = viewModel.defaultStatusesForTest();
        assertTrue(statuses.contains(OrderOperationalStatus.EDITING));
        assertFalse(statuses.contains(OrderOperationalStatus.CANCELLED));
    }

    @Test
    void viewModelUsesOnlyPublicQueryAndSecurityDependencies() throws Exception {
        for (Field field : OrderListViewModel.class.getDeclaredFields()) {
            String name = field.getType().getName();
            assertFalse(name.contains("JdbcTemplate"));
            assertFalse(name.contains("Repository"));
            assertFalse(name.contains("persistence"));
            assertFalse(name.startsWith("com.tmp.order.domain"));
        }
        boolean foundAuthz = false;
        for (Field field : OrderListViewModel.class.getDeclaredFields()) {
            if (field.getType() == AuthorizationService.class) {
                foundAuthz = true;
            }
        }
        assertTrue(foundAuthz);
    }

    private static com.tmp.order.api.OrderWorklistRowDto row(String number) {
        return OrderListTestSupport.row(
                number,
                OrderStatus.DRAFT,
                "ref-" + number,
                "Customer " + number,
                Instant.parse("2026-09-01T10:00:00Z"));
    }
}
