package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderStatus;
import com.tmp.ui.shell.order.worklist.OrderListFilterPreference;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import java.time.Instant;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class OrderListPreferenceViewModelTest {

    @Test
    void preferencesArePerUserAndSearchIsNotPersisted() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.DRAFT,
                        "c-1",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        OrderListTestSupport.SessionAuthn authnA =
                new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId());
        OrderListViewModel userA =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        authnA,
                        prefs);
        userA.refresh();
        userA.toggleStatus(OrderOperationalStatus.EDITING, false);
        userA.toggleStatus(OrderOperationalStatus.IN_PRODUCTION, true);
        userA.setPeriodPreset(OrderListPeriod.Preset.CURRENT_MONTH);
        userA.quickSearchProperty().set("should-not-persist");
        userA.onSearchChanged();

        OrderListTestSupport.SessionAuthn authnB =
                new OrderListTestSupport.SessionAuthn(
                        OrderListTestSupport.userId("22222222-2222-4222-8222-222222222222"));
        OrderListViewModel userB =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        authnB,
                        prefs);
        userB.refresh();
        userB.setPeriodPreset(OrderListPeriod.Preset.TODAY);

        OrderListViewModel reloadA =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        reloadA.refresh();
        assertEquals(OrderListPeriod.Preset.CURRENT_MONTH, reloadA.periodPresetProperty().get());
        assertEquals("", reloadA.quickSearchProperty().get());
        assertFalse(reloadA.selectedStatuses().contains(OrderOperationalStatus.EDITING));
        assertTrue(reloadA.selectedStatuses().contains(OrderOperationalStatus.IN_PRODUCTION));

        OrderListViewModel reloadB =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        authnB,
                        prefs);
        reloadB.refresh();
        assertEquals(OrderListPeriod.Preset.TODAY, reloadB.periodPresetProperty().get());
    }

    @Test
    void invalidStoredPreferenceFallsBackWithoutCrash() {
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        OrderListTestSupport.SessionAuthn authn =
                new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId());
        prefs.save(
                OrderListTestSupport.userId(),
                OrderListFilterPreference.NAMESPACE,
                OrderListFilterPreference.KEY,
                1,
                "this is not valid");
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        new OrderListTestSupport.InMemoryWorklistQuery(),
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        authn,
                        prefs);
        viewModel.refresh();
        assertEquals(OrderListPeriod.Preset.LAST_30_DAYS, viewModel.periodPresetProperty().get());
        assertEquals(EnumSet.complementOf(EnumSet.of(OrderOperationalStatus.CANCELLED)).size(), viewModel.selectedStatuses().size());
        assertFalse(viewModel.selectedStatuses().contains(OrderOperationalStatus.CANCELLED));
        assertTrue(viewModel.selectedStatuses().contains(OrderOperationalStatus.EDITING));
    }
}
