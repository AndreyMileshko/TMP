package com.tmp.ui.shell.screen.orderlist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.order.api.OrderStatus;
import com.tmp.ui.shell.order.worklist.OrderListFilterPreference;
import com.tmp.ui.shell.order.worklist.OrderListFilterPreferenceCodec;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderListCustomerOptionsViewModelTest {

    @Test
    void customerOptionsIncludeCustomersOutsideSelectedPeriod() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "OLD-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-03-01T10:00:00Z")));
        worklist.rows.add(
                OrderListTestSupport.row(
                        "NEW-1",
                        OrderStatus.DRAFT,
                        "c-b",
                        "Beta",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();

        List<OrderCustomerOptionDto> options = viewModel.loadCustomerOptions().orElseThrow();
        assertEquals(
                Set.of("c-a", "c-b"),
                options.stream()
                        .filter(option -> !option.isUnassigned())
                        .map(OrderCustomerOptionDto::customerRef)
                        .collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, viewModel.orders().size());
        assertEquals("NEW-1", viewModel.orders().getFirst().orderNumber());
    }

    @Test
    void savedCustomerOutsidePeriodRemainsSelected() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "OLD-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-03-01T10:00:00Z")));
        worklist.rows.add(
                OrderListTestSupport.row(
                        "NEW-1",
                        OrderStatus.DRAFT,
                        "c-b",
                        "Beta",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        saveCustomerPreference(prefs, Set.of("c-a"));
        int savesBefore = prefs.saveCalls;
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
        assertTrue(viewModel.orders().isEmpty());
        assertEquals(savesBefore, prefs.saveCalls);
        assertPersistedCustomers(prefs, Set.of("c-a"));
    }

    @Test
    void periodChangeDoesNotResetCustomerSelection() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "OLD-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-03-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        saveCustomerPreference(prefs, Set.of("c-a"));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        viewModel.refresh();
        viewModel.setPeriodPreset(OrderListPeriod.Preset.TODAY);
        assertEquals(Set.of("c-a"), Set.copyOf(viewModel.selectedCustomerRefs()));
        viewModel.setPeriodPreset(OrderListPeriod.Preset.CURRENT_MONTH);
        assertEquals(Set.of("c-a"), Set.copyOf(viewModel.selectedCustomerRefs()));
        assertFalse(viewModel.selectAllCustomersProperty().get());
    }

    @Test
    void trueStaleCustomerIsRemovedAfterSuccessfulKnownListLoad() {
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
        saveCustomerPreference(prefs, Set.of("c-a", "c-x"));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        viewModel.refresh();

        assertEquals(Set.of("c-a"), Set.copyOf(viewModel.selectedCustomerRefs()));
        assertPersistedCustomers(prefs, Set.of("c-a"));
    }

    @Test
    void customerOptionsLoadFailurePreservesSelectionAndPreference() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        saveCustomerPreference(prefs, Set.of("c-a"));
        int savesBefore = prefs.saveCalls;
        worklist.denyCustomers = new RuntimeException("customer catalogue down");
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
        assertEquals(savesBefore, prefs.saveCalls);
        assertPersistedCustomers(prefs, Set.of("c-a"));
        assertFalse(viewModel.errorMessageProperty().get().isBlank());
        assertFalse(viewModel.errorMessageProperty().get().contains("customer catalogue down"));
        assertEquals(1, viewModel.orders().size());
    }

    @Test
    void loadFailureThenSuccessKeepsValidSelection() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        saveCustomerPreference(prefs, Set.of("c-a"));
        worklist.denyCustomers = new RuntimeException("first failure");
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        viewModel.refresh();
        assertEquals(Set.of("c-a"), Set.copyOf(viewModel.selectedCustomerRefs()));

        worklist.denyCustomers = null;
        viewModel.refresh();
        assertEquals(Set.of("c-a"), Set.copyOf(viewModel.selectedCustomerRefs()));
        assertFalse(viewModel.selectAllCustomersProperty().get());
    }

    @Test
    void loadFailureThenSuccessfulLoadNormalizesRealStale() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        saveCustomerPreference(prefs, Set.of("c-x"));
        worklist.denyCustomers = new RuntimeException("first failure");
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        viewModel.refresh();
        assertEquals(Set.of("c-x"), Set.copyOf(viewModel.selectedCustomerRefs()));
        assertPersistedCustomers(prefs, Set.of("c-x"));

        worklist.denyCustomers = null;
        viewModel.refresh();
        assertTrue(viewModel.selectAllCustomersProperty().get());
        assertTrue(viewModel.selectedCustomerRefs().isEmpty());
        assertEquals("Заказчики: Все", viewModel.customerFilterLabelProperty().get());
    }

    @Test
    void duplicateDisplayNamesPreserveDistinctCustomerRefs() {
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
                        "Alpha",
                        Instant.parse("2026-09-01T11:00:00Z")));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();
        List<String> refs =
                viewModel.loadCustomerOptions().orElseThrow().stream()
                        .filter(option -> "Alpha".equals(option.customerName()))
                        .map(OrderCustomerOptionDto::customerRef)
                        .sorted()
                        .toList();
        assertEquals(List.of("c-a", "c-b"), refs);
    }

    @Test
    void loadCustomerOptionsFailureReturnsEmptyOptionalWithoutClearingSelection() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "A-1",
                        OrderStatus.DRAFT,
                        "c-a",
                        "Alpha",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListTestSupport.InMemoryPreferences prefs = new OrderListTestSupport.InMemoryPreferences();
        saveCustomerPreference(prefs, Set.of("c-a"));
        int savesBefore = prefs.saveCalls;
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        prefs);
        viewModel.refresh();
        worklist.denyCustomers = new RuntimeException("popup catalogue down");

        assertTrue(viewModel.loadCustomerOptions().isEmpty());
        assertEquals(Set.of("c-a"), Set.copyOf(viewModel.selectedCustomerRefs()));
        assertFalse(viewModel.selectAllCustomersProperty().get());
        assertEquals(savesBefore, prefs.saveCalls);
        assertPersistedCustomers(prefs, Set.of("c-a"));
        assertFalse(viewModel.errorMessageProperty().get().isBlank());
    }

    @Test
    void loadCustomerOptionsSuccessEmptyIsDistinctFromFailure() {
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        new OrderListTestSupport.InMemoryWorklistQuery(),
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();

        var loaded = viewModel.loadCustomerOptions();
        assertTrue(loaded.isPresent());
        assertTrue(loaded.get().isEmpty());
        assertTrue(
                viewModel.errorMessageProperty().get() == null
                        || viewModel.errorMessageProperty().get().isBlank());
    }

    @Test
    void sameCustomerRefUsesLatestKnownDisplayName() {
        OrderListTestSupport.InMemoryWorklistQuery worklist = new OrderListTestSupport.InMemoryWorklistQuery();
        worklist.rows.add(
                OrderListTestSupport.row(
                        "OLD-1",
                        OrderStatus.DRAFT,
                        "CUST-1",
                        "ООО Альфа",
                        Instant.parse("2026-01-01T10:00:00Z")));
        worklist.rows.add(
                OrderListTestSupport.row(
                        "NEW-1",
                        OrderStatus.DRAFT,
                        "CUST-1",
                        "Альфа",
                        Instant.parse("2026-09-01T10:00:00Z")));
        OrderListViewModel viewModel =
                OrderListTestSupport.viewModel(
                        worklist,
                        new OrderListTestSupport.MapProductionQuery(),
                        new FakeAuthorization(),
                        new OrderListTestSupport.SessionAuthn(OrderListTestSupport.userId()),
                        new OrderListTestSupport.InMemoryPreferences());
        viewModel.refresh();

        List<OrderCustomerOptionDto> named =
                viewModel.loadCustomerOptions().orElseThrow().stream()
                        .filter(option -> !option.isUnassigned())
                        .toList();
        assertEquals(1, named.size());
        assertEquals("CUST-1", named.getFirst().customerRef());
        assertEquals("Альфа", named.getFirst().customerName());
    }

    private static void saveCustomerPreference(
            OrderListTestSupport.InMemoryPreferences prefs, Set<String> customerRefs) {
        OrderListFilterPreference preference =
                new OrderListFilterPreference(
                        EnumSet.of(OrderOperationalStatus.EDITING),
                        false,
                        customerRefs,
                        false,
                        OrderListPeriod.Preset.LAST_30_DAYS,
                        null,
                        null,
                        50);
        prefs.save(
                OrderListTestSupport.userId(),
                OrderListFilterPreference.NAMESPACE,
                OrderListFilterPreference.KEY,
                OrderListFilterPreference.VERSION,
                OrderListFilterPreferenceCodec.encode(preference));
    }

    private static void assertPersistedCustomers(
            OrderListTestSupport.InMemoryPreferences prefs, Set<String> expected) {
        OrderListFilterPreference loaded =
                OrderListFilterPreferenceCodec.decode(
                        prefs.load(
                                        OrderListTestSupport.userId(),
                                        OrderListFilterPreference.NAMESPACE,
                                        OrderListFilterPreference.KEY)
                                .orElseThrow());
        assertEquals(expected, loaded.customerRefs());
        assertEquals(expected.isEmpty(), loaded.selectAllCustomers());
    }
}
