package com.tmp.ui.shell.screen.orderlist;

import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.PageRequest;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserUiPreferenceService;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import com.tmp.ui.shell.order.worklist.OrderListFilterPreference;
import com.tmp.ui.shell.order.worklist.OrderListFilterPreferenceCodec;
import com.tmp.ui.shell.order.worklist.OrderListMemento;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.ui.shell.order.worklist.OrderOperationalListRequest;
import com.tmp.ui.shell.order.worklist.OrderOperationalListResult;
import com.tmp.ui.shell.order.worklist.OrderOperationalListResult.ProductionFactsState;
import com.tmp.ui.shell.order.worklist.OrderOperationalListService;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import com.tmp.ui.shell.order.worklist.OrderOperationalSummary;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

/**
 * Operational Orders list ViewModel. Composes Order Management + Production through
 * {@link OrderOperationalListService}. Persistent filters are per-user; quick search is session-only.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderListViewModel {

    private final OrderOperationalListService listService;
    private final OrderWorklistQuery worklistQuery;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final UserUiPreferenceService preferenceService;
    private final Clock clock;
    private final ObservableList<OrderOperationalSummary> orders = FXCollections.observableArrayList();
    private final ObjectProperty<OrderOperationalSummary> selectedOrder = new SimpleObjectProperty<>();
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canImport = new SimpleBooleanProperty(false);
    private final StringProperty title = new SimpleStringProperty("Заказы");
    private final StringProperty subtitle =
            new SimpleStringProperty("Управление заказами и их производственным состоянием");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty emptyResult = new SimpleBooleanProperty(false);
    private final IntegerProperty pageIndex = new SimpleIntegerProperty(0);
    private final IntegerProperty pageSize = new SimpleIntegerProperty(PageRequest.DEFAULT_PAGE_SIZE);
    private final LongProperty totalElements = new SimpleLongProperty(0);
    private final StringProperty quickSearch = new SimpleStringProperty("");
    private final ObjectProperty<OrderListPeriod.Preset> periodPreset =
            new SimpleObjectProperty<>(OrderListPeriod.Preset.LAST_30_DAYS);
    private final ObjectProperty<LocalDate> customFrom = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> customTo = new SimpleObjectProperty<>();
    private final BooleanProperty canGoPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canGoNext = new SimpleBooleanProperty(false);
    private final ObservableSet<OrderOperationalStatus> selectedStatuses =
            FXCollections.observableSet(EnumSet.noneOf(OrderOperationalStatus.class));
    private final BooleanProperty selectAllCustomers = new SimpleBooleanProperty(true);
    private final ObservableSet<String> selectedCustomerRefs = FXCollections.observableSet();
    private final BooleanProperty includeUnassignedCustomer = new SimpleBooleanProperty(false);
    private final StringProperty customerFilterLabel = new SimpleStringProperty("Заказчики: Все");
    private boolean restoring;
    private boolean preferencesLoaded;
    private Runnable onCreateOrder = () -> {};
    private Runnable onImportOrder = () -> {};
    private Consumer<OrderId> onOpenOrder = id -> {};

    public OrderListViewModel(
            OrderOperationalListService listService,
            OrderWorklistQuery worklistQuery,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            UserUiPreferenceService preferenceService,
            Clock clock) {
        this.listService = Objects.requireNonNull(listService, "listService");
        this.worklistQuery = Objects.requireNonNull(worklistQuery, "worklistQuery");
        this.authorizationService = Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService =
                Objects.requireNonNull(authenticationService, "authenticationService");
        this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService");
        this.clock = Objects.requireNonNull(clock, "clock");
        applyPreference(OrderListFilterPreference.defaults(), false);
        refreshPermissions();
        updatePaginationFlags();
    }

    public void setOnCreateOrder(Runnable onCreateOrder) {
        this.onCreateOrder = Objects.requireNonNull(onCreateOrder, "onCreateOrder");
    }

    public void setOnImportOrder(Runnable onImportOrder) {
        this.onImportOrder = Objects.requireNonNull(onImportOrder, "onImportOrder");
    }

    public void setOnOpenOrder(Consumer<OrderId> onOpenOrder) {
        this.onOpenOrder = Objects.requireNonNull(onOpenOrder, "onOpenOrder");
    }

    public void refreshPermissions() {
        boolean createAllowed =
                authorizationService.hasPermission(PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
        canCreate.set(createAllowed);
        canImport.set(createAllowed);
    }

    public ObservableList<OrderOperationalSummary> orders() {
        return orders;
    }

    public ObjectProperty<OrderOperationalSummary> selectedOrderProperty() {
        return selectedOrder;
    }

    public BooleanProperty canCreateProperty() {
        return canCreate;
    }

    public BooleanProperty canImportProperty() {
        return canImport;
    }

    public void createOrder() {
        onCreateOrder.run();
    }

    public void importOrder() {
        onImportOrder.run();
    }

    public void openSelectedOrder() {
        OrderOperationalSummary selected = selectedOrder.get();
        if (selected != null) {
            onOpenOrder.accept(selected.orderId());
        }
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty emptyResultProperty() {
        return emptyResult;
    }

    public IntegerProperty pageIndexProperty() {
        return pageIndex;
    }

    public IntegerProperty pageSizeProperty() {
        return pageSize;
    }

    public LongProperty totalElementsProperty() {
        return totalElements;
    }

    public StringProperty quickSearchProperty() {
        return quickSearch;
    }

    public ObjectProperty<OrderListPeriod.Preset> periodPresetProperty() {
        return periodPreset;
    }

    public ObjectProperty<LocalDate> customFromProperty() {
        return customFrom;
    }

    public ObjectProperty<LocalDate> customToProperty() {
        return customTo;
    }

    public BooleanProperty canGoPreviousProperty() {
        return canGoPrevious;
    }

    public BooleanProperty canGoNextProperty() {
        return canGoNext;
    }

    public ObservableSet<OrderOperationalStatus> selectedStatuses() {
        return selectedStatuses;
    }

    public BooleanProperty selectAllCustomersProperty() {
        return selectAllCustomers;
    }

    public ObservableSet<String> selectedCustomerRefs() {
        return selectedCustomerRefs;
    }

    public BooleanProperty includeUnassignedCustomerProperty() {
        return includeUnassignedCustomer;
    }

    public StringProperty customerFilterLabelProperty() {
        return customerFilterLabel;
    }

    public void ensurePreferencesLoaded() {
        if (preferencesLoaded) {
            return;
        }
        preferencesLoaded = true;
        currentUserId()
                .flatMap(
                        userId ->
                                preferenceService.load(
                                        userId,
                                        OrderListFilterPreference.NAMESPACE,
                                        OrderListFilterPreference.KEY))
                .ifPresent(raw -> applyPreference(OrderListFilterPreferenceCodec.decode(raw), false));
    }

    public void refresh() {
        ensurePreferencesLoaded();
        reconcileCustomerSelectionAgainstOptions();
        refreshPermissions();
        loading.set(true);
        errorMessage.set("");
        statusMessage.set("");
        try {
            int size = clampPageSize(pageSize.get());
            if (pageSize.get() != size) {
                pageSize.set(size);
            }
            OrderListPeriod.Range range = resolvePeriod();
            OrderOperationalListResult page =
                    listService.search(
                            new OrderOperationalListRequest(
                                    range.fromInclusive(),
                                    range.toExclusive(),
                                    blankToNull(quickSearch.get()),
                                    Set.copyOf(selectedStatuses),
                                    Set.copyOf(selectedCustomerRefs),
                                    includeUnassignedCustomer.get(),
                                    !selectAllCustomers.get(),
                                    pageIndex.get(),
                                    size));
            orders.setAll(page.content());
            totalElements.set(page.totalElements());
            pageIndex.set(page.pageIndex());
            emptyResult.set(page.content().isEmpty());
            if (page.content().isEmpty()) {
                statusMessage.set(emptyMessage());
            }
            applyProductionFactsState(page);
            updateCustomerFilterLabel();
        } catch (IllegalArgumentException ex) {
            orders.clear();
            totalElements.set(0);
            emptyResult.set(false);
            errorMessage.set(ex.getMessage());
        } catch (AccessDeniedException ex) {
            emptyResult.set(false);
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.LOAD));
            statusMessage.set(OrderUiErrorMapper.LIST_REFRESH_FAILED);
        } catch (RuntimeException ex) {
            emptyResult.set(false);
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.LOAD));
            statusMessage.set(OrderUiErrorMapper.LIST_REFRESH_FAILED);
        } finally {
            loading.set(false);
            updatePaginationFlags();
        }
    }

    public void onFiltersChanged() {
        if (restoring) {
            return;
        }
        pageIndex.set(0);
        refresh();
        persistFilters();
    }

    public void onSearchChanged() {
        if (restoring) {
            return;
        }
        pageIndex.set(0);
        refresh();
    }

    public void applyCustomerSelection(
            boolean allCustomers, Set<String> customerRefs, boolean includeUnassigned) {
        restoring = true;
        try {
            selectAllCustomers.set(allCustomers);
            selectedCustomerRefs.clear();
            if (!allCustomers) {
                selectedCustomerRefs.addAll(customerRefs);
            }
            includeUnassignedCustomer.set(!allCustomers && includeUnassigned);
            updateCustomerFilterLabel();
        } finally {
            restoring = false;
        }
        onFiltersChanged();
    }

    /**
     * At least one status checkbox must remain selected. Deselecting the last selected status is a
     * no-op.
     */
    public void toggleStatus(OrderOperationalStatus status, boolean selected) {
        Objects.requireNonNull(status, "status");
        if (status == OrderOperationalStatus.STATUS_UNAVAILABLE) {
            return;
        }
        if (selected) {
            selectedStatuses.add(status);
        } else if (!canDeselectStatus(status)) {
            return;
        } else {
            selectedStatuses.remove(status);
        }
        onFiltersChanged();
    }

    public boolean canDeselectStatus(OrderOperationalStatus status) {
        return !(selectedStatuses.size() == 1 && selectedStatuses.contains(status));
    }

    public void setPeriodPreset(OrderListPeriod.Preset preset) {
        periodPreset.set(preset);
        onFiltersChanged();
    }

    public List<OrderCustomerOptionDto> loadCustomerOptions() {
        try {
            OrderListPeriod.Range range = resolvePeriod();
            return worklistQuery.listWorklistCustomers(range.fromInclusive(), range.toExclusive());
        } catch (RuntimeException ex) {
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.LOAD));
            return List.of();
        }
    }

    public void nextPage() {
        long total = totalElements.get();
        int size = clampPageSize(pageSize.get());
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / size);
        if (pageIndex.get() < maxPage) {
            pageIndex.set(pageIndex.get() + 1);
            refresh();
        }
    }

    public void previousPage() {
        if (pageIndex.get() > 0) {
            pageIndex.set(pageIndex.get() - 1);
            refresh();
        }
    }

    public OrderListMemento captureMemento() {
        OrderOperationalSummary selected = selectedOrder.get();
        return new OrderListMemento(
                quickSearch.get() == null ? "" : quickSearch.get(),
                Set.copyOf(selectedStatuses),
                selectAllCustomers.get(),
                Set.copyOf(selectedCustomerRefs),
                includeUnassignedCustomer.get(),
                periodPreset.get(),
                customFrom.get(),
                customTo.get(),
                pageIndex.get(),
                selected == null ? null : selected.orderId());
    }

    public void restoreMemento(OrderListMemento memento) {
        Objects.requireNonNull(memento, "memento");
        restoring = true;
        try {
            preferencesLoaded = true;
            quickSearch.set(memento.quickSearch());
            selectedStatuses.clear();
            selectedStatuses.addAll(memento.statuses());
            if (selectedStatuses.isEmpty()) {
                selectedStatuses.addAll(OrderListFilterPreference.defaults().statuses());
            }
            selectAllCustomers.set(memento.selectAllCustomers());
            selectedCustomerRefs.clear();
            selectedCustomerRefs.addAll(memento.customerRefs());
            includeUnassignedCustomer.set(memento.includeUnassignedCustomer());
            periodPreset.set(memento.periodPreset());
            customFrom.set(memento.customFrom());
            customTo.set(memento.customTo());
            pageIndex.set(memento.pageIndex());
        } finally {
            restoring = false;
        }
        refresh();
        if (memento.selectedOrderId() != null) {
            for (OrderOperationalSummary row : orders) {
                if (row.orderId().equals(memento.selectedOrderId())) {
                    selectedOrder.set(row);
                    return;
                }
            }
        }
        selectedOrder.set(null);
    }

    public void resetForSidebarOpen() {
        ensurePreferencesLoaded();
        restoring = true;
        try {
            quickSearch.set("");
            pageIndex.set(0);
            selectedOrder.set(null);
        } finally {
            restoring = false;
        }
        refresh();
    }

    private void persistFilters() {
        Optional<UserId> userId = currentUserId();
        if (userId.isEmpty()) {
            return;
        }
        Set<OrderOperationalStatus> statuses = new LinkedHashSet<>(selectedStatuses);
        statuses.remove(OrderOperationalStatus.STATUS_UNAVAILABLE);
        if (statuses.isEmpty()) {
            statuses.addAll(OrderListFilterPreference.defaults().statuses());
        }
        OrderListFilterPreference preference =
                new OrderListFilterPreference(
                        statuses,
                        selectAllCustomers.get(),
                        Set.copyOf(selectedCustomerRefs),
                        includeUnassignedCustomer.get(),
                        periodPreset.get(),
                        customFrom.get(),
                        customTo.get(),
                        clampPageSize(pageSize.get()));
        preferenceService.save(
                userId.get(),
                OrderListFilterPreference.NAMESPACE,
                OrderListFilterPreference.KEY,
                OrderListFilterPreference.VERSION,
                OrderListFilterPreferenceCodec.encode(preference));
    }

    private void applyPreference(OrderListFilterPreference preference, boolean persist) {
        restoring = true;
        try {
            selectedStatuses.clear();
            selectedStatuses.addAll(preference.statuses());
            if (selectedStatuses.isEmpty()) {
                selectedStatuses.addAll(OrderListFilterPreference.defaults().statuses());
            }
            selectAllCustomers.set(preference.selectAllCustomers());
            selectedCustomerRefs.clear();
            selectedCustomerRefs.addAll(preference.customerRefs());
            includeUnassignedCustomer.set(preference.includeUnassignedCustomer());
            periodPreset.set(preference.periodPreset());
            customFrom.set(preference.customFrom());
            customTo.set(preference.customTo());
            pageSize.set(clampPageSize(preference.pageSize()));
            pageIndex.set(0);
            updateCustomerFilterLabel();
        } finally {
            restoring = false;
        }
        if (persist) {
            persistFilters();
        }
    }

    private void reconcileCustomerSelectionAgainstOptions() {
        if (selectAllCustomers.get()) {
            if (!selectedCustomerRefs.isEmpty() || includeUnassignedCustomer.get()) {
                restoring = true;
                try {
                    selectedCustomerRefs.clear();
                    includeUnassignedCustomer.set(false);
                } finally {
                    restoring = false;
                }
                persistFilters();
            }
            updateCustomerFilterLabel();
            return;
        }
        List<OrderCustomerOptionDto> options = loadCustomerOptions();
        Set<String> validRefs = new LinkedHashSet<>();
        boolean hasUnassignedOption = false;
        for (OrderCustomerOptionDto option : options) {
            if (option.isUnassigned()) {
                hasUnassignedOption = true;
            } else if (option.customerRef() != null && !option.customerRef().isBlank()) {
                validRefs.add(option.customerRef());
            }
        }
        Set<String> cleaned = new LinkedHashSet<>();
        for (String ref : selectedCustomerRefs) {
            if (validRefs.contains(ref)) {
                cleaned.add(ref);
            }
        }
        boolean includeUnassigned = includeUnassignedCustomer.get() && hasUnassignedOption;
        boolean becameEmpty = cleaned.isEmpty() && !includeUnassigned;
        boolean changed =
                becameEmpty
                        || !cleaned.equals(Set.copyOf(selectedCustomerRefs))
                        || includeUnassigned != includeUnassignedCustomer.get();
        restoring = true;
        try {
            if (becameEmpty) {
                selectAllCustomers.set(true);
                selectedCustomerRefs.clear();
                includeUnassignedCustomer.set(false);
            } else {
                selectedCustomerRefs.clear();
                selectedCustomerRefs.addAll(cleaned);
                includeUnassignedCustomer.set(includeUnassigned);
            }
            updateCustomerFilterLabel();
        } finally {
            restoring = false;
        }
        if (changed) {
            persistFilters();
        }
    }

    private void applyProductionFactsState(OrderOperationalListResult page) {
        if (page.productionFactsState() == ProductionFactsState.TECHNICAL_FAILURE) {
            RuntimeException failure =
                    page.technicalFailure()
                            .orElseGet(() -> new IllegalStateException("Production facts unavailable"));
            errorMessage.set(OrderUiErrorMapper.text(failure, OrderUiOperation.LOAD));
            if (statusMessage.get() == null || statusMessage.get().isBlank()) {
                statusMessage.set("Статус производства недоступен для части заказов.");
            }
        }
    }

    private OrderListPeriod.Range resolvePeriod() {
        OrderListPeriod.Preset preset = periodPreset.get();
        if (preset == OrderListPeriod.Preset.CUSTOM
                && (customFrom.get() == null || customTo.get() == null)) {
            throw new IllegalArgumentException("Укажите дату начала и дату окончания периода.");
        }
        return OrderListPeriod.resolve(
                periodPreset.get(), ZoneId.systemDefault(), clock, customFrom.get(), customTo.get());
    }

    private String emptyMessage() {
        if (blankToNull(quickSearch.get()) != null) {
            return "По вашему запросу заказы не найдены.";
        }
        return "Заказы не найдены. Измените период или параметры фильтра.";
    }

    private void updateCustomerFilterLabel() {
        if (selectAllCustomers.get()) {
            customerFilterLabel.set("Заказчики: Все");
            return;
        }
        int count = selectedCustomerRefs.size() + (includeUnassignedCustomer.get() ? 1 : 0);
        customerFilterLabel.set("Заказчики: выбрано " + count);
    }

    private Optional<UserId> currentUserId() {
        return authenticationService.currentSession().map(SessionSummary::userId);
    }

    private static int clampPageSize(int requested) {
        if (requested < 1) {
            return PageRequest.DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, PageRequest.MAX_PAGE_SIZE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void updatePaginationFlags() {
        boolean busy = loading.get();
        long total = totalElements.get();
        int size = clampPageSize(pageSize.get());
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / size);
        int page = pageIndex.get();
        canGoPrevious.set(!busy && page > 0);
        canGoNext.set(!busy && page < maxPage);
    }

    Set<OrderOperationalStatus> defaultStatusesForTest() {
        return new LinkedHashSet<>(selectedStatuses);
    }
}
