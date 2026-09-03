package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.ui.shell.order.worklist.OrderListFilterPreference;
import com.tmp.ui.shell.order.worklist.OrderListFilterPreferenceCodec;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderListCorrectiveViewModelTest {

    @Test
    void cannotDeselectLastStatusCheckbox() {
        OrderListViewModel viewModel = OrderListTestSupport.viewModel();
        viewModel.selectedStatuses().clear();
        viewModel.selectedStatuses().add(OrderOperationalStatus.EDITING);
        assertFalse(viewModel.canDeselectStatus(OrderOperationalStatus.EDITING));
        viewModel.toggleStatus(OrderOperationalStatus.EDITING, false);
        assertTrue(viewModel.selectedStatuses().contains(OrderOperationalStatus.EDITING));
        assertEquals(1, viewModel.selectedStatuses().size());

        viewModel.toggleStatus(OrderOperationalStatus.IN_PRODUCTION, true);
        assertTrue(viewModel.canDeselectStatus(OrderOperationalStatus.EDITING));
        viewModel.toggleStatus(OrderOperationalStatus.EDITING, false);
        assertFalse(viewModel.selectedStatuses().contains(OrderOperationalStatus.EDITING));
        assertTrue(viewModel.selectedStatuses().contains(OrderOperationalStatus.IN_PRODUCTION));
    }

    @Test
    void staleCustomerRefsAreRemovedAndCaptionUsesValidCount() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        worklist.rows.add(
                OrderListTestSupport.row(
                        "B-1",
                        OrderStatus.DRAFT,
                        "c-b",
                        "Beta",
                        Instant.parse("2026-09-01T11:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        OrderListFilterPreference stale =
                new OrderListFilterPreference(
                        EnumSet.of(OrderOperationalStatus.EDITING),
                        false,
                        Set.of("c-a", "c-stale"),
                        false,
                        com.tmp.ui.shell.order.worklist.OrderListPeriod.Preset.LAST_30_DAYS,
                        null,
                        null,
                        50);
        prefs.save(
                OrderListTestSupport.userId(),
                OrderListFilterPreference.NAMESPACE,
                OrderListFilterPreference.KEY,
                OrderListFilterPreference.VERSION,
                OrderListFilterPreferenceCodec.encode(stale));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        viewModel.refresh();
        assertEquals(Set.of("c-a"), Set.copyOf(viewModel.selectedCustomerRefs()));
        assertFalse(viewModel.selectAllCustomersProperty().get());
        assertEquals("Заказчики: выбрано 1", viewModel.customerFilterLabelProperty().get());
        assertEquals(1, viewModel.orders().size());
        assertEquals("A-1", viewModel.orders().getFirst().orderNumber());
    }

    @Test
    void onlyStaleCustomerRefsNormalizeToSelectAll() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        OrderListFilterPreference staleOnly =
                new OrderListFilterPreference(
                        EnumSet.of(OrderOperationalStatus.EDITING),
                        false,
                        Set.of("c-gone"),
                        false,
                        com.tmp.ui.shell.order.worklist.OrderListPeriod.Preset.LAST_30_DAYS,
                        null,
                        null,
                        50);
        prefs.save(
                OrderListTestSupport.userId(),
                OrderListFilterPreference.NAMESPACE,
                OrderListFilterPreference.KEY,
                OrderListFilterPreference.VERSION,
                OrderListFilterPreferenceCodec.encode(staleOnly));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        viewModel.refresh();
        assertTrue(viewModel.selectAllCustomersProperty().get());
        assertTrue(viewModel.selectedCustomerRefs().isEmpty());
        assertEquals("Заказчики: Все", viewModel.customerFilterLabelProperty().get());
        assertEquals(1, viewModel.orders().size());
    }

    @Test
    void productionAccessDeniedShowsUnavailableNotAwaiting() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.ACTIVE,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.MapProductionQuery production = new OrderListTestSupport.MapProductionQuery() {
            @Override
            public java.util.Map<
                            java.util.UUID,
                            com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts>
                    getOrderProductionListFacts(java.util.Collection<java.util.UUID> orderIds) {
                throw new AccessDeniedException("production.order.view");
            }
        };
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        production,
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();
        assertEquals(1, viewModel.orders().size());
        assertEquals(
                OrderOperationalStatus.STATUS_UNAVAILABLE,
                viewModel.orders().getFirst().operationalStatus());
        assertFalse(viewModel.errorMessageProperty().get().contains("AccessDenied"));
    }
}
