package com.tmp.ui.shell.screen.orderlist;

import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSort;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import com.tmp.ui.shell.UiShellScreens;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
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

/**
 * Read-only Order list ViewModel. Loads data only through {@link OrderQueryService#searchOrders}.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderListViewModel {

    private final OrderQueryService orderQueryService;
    private final AuthorizationService authorizationService;
    private final ObservableList<OrderSummaryDto> orders = FXCollections.observableArrayList();
    private final ObjectProperty<OrderSummaryDto> selectedOrder = new SimpleObjectProperty<>();
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canImport = new SimpleBooleanProperty(false);
    private final BooleanProperty canOpenSelected = new SimpleBooleanProperty(false);
    private final StringProperty title = new SimpleStringProperty("Заказы");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty emptyResult = new SimpleBooleanProperty(false);
    private final IntegerProperty pageIndex = new SimpleIntegerProperty(0);
    private final IntegerProperty pageSize = new SimpleIntegerProperty(PageRequest.DEFAULT_PAGE_SIZE);
    private final LongProperty totalElements = new SimpleLongProperty(0);
    private final StringProperty orderNumberFilter = new SimpleStringProperty("");
    private final StringProperty orderStatusFilter = new SimpleStringProperty("");
    private final StringProperty customerRefFilter = new SimpleStringProperty("");
    private final StringProperty customerNameFilter = new SimpleStringProperty("");
    private final StringProperty createdFromFilter = new SimpleStringProperty("");
    private final StringProperty createdToFilter = new SimpleStringProperty("");
    private final StringProperty sortField = new SimpleStringProperty(OrderSort.Field.CREATED_AT.apiName());
    private final StringProperty sortDirection = new SimpleStringProperty(OrderSort.Direction.DESC.name());
    private final BooleanProperty canGoPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canGoNext = new SimpleBooleanProperty(false);
    private Runnable onCreateOrder = () -> {
    };
    private Runnable onImportOrder = () -> {
    };
    private Consumer<OrderId> onOpenOrder = id -> {
    };

    public OrderListViewModel(
            OrderQueryService orderQueryService, AuthorizationService authorizationService) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        selectedOrder.addListener((obs, oldValue, newValue) -> canOpenSelected.set(newValue != null));
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
        boolean createAllowed = authorizationService.hasPermission(
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION));
        canCreate.set(createAllowed);
        canImport.set(createAllowed);
    }

    public ObservableList<OrderSummaryDto> orders() {
        return orders;
    }

    public ObjectProperty<OrderSummaryDto> selectedOrderProperty() {
        return selectedOrder;
    }

    public BooleanProperty canCreateProperty() {
        return canCreate;
    }

    public BooleanProperty canImportProperty() {
        return canImport;
    }

    public BooleanProperty canOpenSelectedProperty() {
        return canOpenSelected;
    }

    public void createOrder() {
        onCreateOrder.run();
    }

    public void importOrder() {
        onImportOrder.run();
    }

    public void openSelectedOrder() {
        OrderSummaryDto selected = selectedOrder.get();
        if (selected != null) {
            onOpenOrder.accept(selected.orderId());
        }
    }

    public StringProperty titleProperty() {
        return title;
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

    public StringProperty orderNumberFilterProperty() {
        return orderNumberFilter;
    }

    public StringProperty orderStatusFilterProperty() {
        return orderStatusFilter;
    }

    public StringProperty customerRefFilterProperty() {
        return customerRefFilter;
    }

    public StringProperty customerNameFilterProperty() {
        return customerNameFilter;
    }

    public StringProperty createdFromFilterProperty() {
        return createdFromFilter;
    }

    public StringProperty createdToFilterProperty() {
        return createdToFilter;
    }

    public StringProperty sortFieldProperty() {
        return sortField;
    }

    public StringProperty sortDirectionProperty() {
        return sortDirection;
    }

    public BooleanProperty canGoPreviousProperty() {
        return canGoPrevious;
    }

    public BooleanProperty canGoNextProperty() {
        return canGoNext;
    }

    public void refresh() {
        refreshPermissions();
        loading.set(true);
        updatePaginationFlags();
        errorMessage.set("");
        statusMessage.set("");
        try {
            Optional<OrderSearchCriteria> criteria = tryBuildCriteria();
            if (criteria.isEmpty()) {
                orders.clear();
                totalElements.set(0);
                emptyResult.set(false);
                return;
            }
            OrderSort sort = resolveSort();
            int size = clampPageSize(pageSize.get());
            pageSize.set(size);
            PageRequest request = PageRequest.of(pageIndex.get(), size, sort);
            PageResult<OrderSummaryDto> page = orderQueryService.searchOrders(criteria.get(), request);
            orders.setAll(page.content());
            totalElements.set(page.totalElements());
            pageIndex.set(page.pageIndex());
            emptyResult.set(page.content().isEmpty());
            if (page.content().isEmpty()) {
                statusMessage.set("Заказы не найдены");
            }
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

    public void applyFilters() {
        pageIndex.set(0);
        refresh();
    }

    public void clearFilters() {
        orderNumberFilter.set("");
        orderStatusFilter.set("");
        customerRefFilter.set("");
        customerNameFilter.set("");
        createdFromFilter.set("");
        createdToFilter.set("");
        sortField.set(OrderSort.Field.CREATED_AT.apiName());
        sortDirection.set(OrderSort.Direction.DESC.name());
        pageIndex.set(0);
        refresh();
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

    OrderSearchCriteria toSearchCriteriaForTest() {
        return tryBuildCriteria()
                .orElseThrow(() -> new IllegalStateException(errorMessage.get()));
    }

    private Optional<OrderSearchCriteria> tryBuildCriteria() {
        ParsedInstant from = parseInstant(createdFromFilter.get(), "createdFrom");
        if (from.invalid()) {
            errorMessage.set(from.errorMessage());
            return Optional.empty();
        }
        ParsedInstant to = parseInstant(createdToFilter.get(), "createdTo");
        if (to.invalid()) {
            errorMessage.set(to.errorMessage());
            return Optional.empty();
        }
        if (from.value() != null && to.value() != null && from.value().isAfter(to.value())) {
            errorMessage.set("Некорректный диапазон дат: дата \"с\" позже даты \"по\"");
            return Optional.empty();
        }
        ParsedStatus status = parseStatus(orderStatusFilter.get());
        if (status.invalid()) {
            errorMessage.set(status.errorMessage());
            return Optional.empty();
        }
        return Optional.of(OrderSearchCriteria.builder()
                .orderNumber(blankToNull(orderNumberFilter.get()))
                .orderStatus(status.value())
                .customerRef(blankToNull(customerRefFilter.get()))
                .customerName(blankToNull(customerNameFilter.get()))
                .createdFrom(from.value())
                .createdTo(to.value())
                .build());
    }

    private OrderSort resolveSort() {
        String field = blankToNull(sortField.get());
        String direction = blankToNull(sortDirection.get());
        if (field == null) {
            field = OrderSort.Field.CREATED_AT.apiName();
        }
        if (direction == null) {
            direction = OrderSort.Direction.DESC.name();
        }
        OrderSort primary = OrderSort.of(field, direction);
        OrderSort.Order first = primary.orders().get(0);
        if (first.field() == OrderSort.Field.CREATED_AT && first.direction() == OrderSort.Direction.DESC) {
            return OrderSort.defaultSort();
        }
        if (first.field() != OrderSort.Field.ORDER_ID) {
            return OrderSort.of(first, new OrderSort.Order(OrderSort.Field.ORDER_ID, OrderSort.Direction.DESC));
        }
        return primary;
    }

    private static ParsedInstant parseInstant(String raw, String fieldName) {
        String value = blankToNull(raw);
        if (value == null) {
            return ParsedInstant.absent();
        }
        try {
            return ParsedInstant.of(Instant.parse(value));
        } catch (DateTimeParseException ignored) {
            // try LocalDate
        }
        try {
            return ParsedInstant.of(LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException ex) {
            String fieldLabel = "createdFrom".equals(fieldName) ? "дата \"с\"" : "дата \"по\"";
            return ParsedInstant.invalid("Некорректная " + fieldLabel + ": " + value);
        }
    }

    private static ParsedStatus parseStatus(String raw) {
        String value = blankToNull(raw);
        if (value == null) {
            return ParsedStatus.absent();
        }
        try {
            return ParsedStatus.of(OrderStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return ParsedStatus.invalid("Неизвестный статус заказа: " + value);
        }
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

    private record ParsedInstant(Instant value, String errorMessage) {
        static ParsedInstant absent() {
            return new ParsedInstant(null, null);
        }

        static ParsedInstant of(Instant value) {
            return new ParsedInstant(value, null);
        }

        static ParsedInstant invalid(String message) {
            return new ParsedInstant(null, message);
        }

        boolean invalid() {
            return errorMessage != null;
        }
    }

    private record ParsedStatus(OrderStatus value, String errorMessage) {
        static ParsedStatus absent() {
            return new ParsedStatus(null, null);
        }

        static ParsedStatus of(OrderStatus value) {
            return new ParsedStatus(value, null);
        }

        static ParsedStatus invalid(String message) {
            return new ParsedStatus(null, message);
        }

        boolean invalid() {
            return errorMessage != null;
        }
    }
}
