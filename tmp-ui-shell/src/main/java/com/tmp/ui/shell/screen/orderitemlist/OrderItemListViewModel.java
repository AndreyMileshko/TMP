package com.tmp.ui.shell.screen.orderitemlist;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.ProductQuantityUiValidation;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import com.tmp.ui.shell.order.worklist.OrderItemOperationalStatus;
import com.tmp.ui.shell.order.worklist.OrderItemOperationalStatusDeriver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Order item list ViewModel. Loads items through {@link OrderQueryService#getOrderItems} and
 * derives operational status / quantity for presentation.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderItemListViewModel {

    private static final Logger LOGGER = System.getLogger(OrderItemListViewModel.class.getName());

    private final OrderQueryService orderQueryService;
    private final AuthorizationService authorization;
    private final OrderItemEditorQueryService editorQuery;
    private final ProductionQueryApi productionQuery;

    private final ObservableList<OrderItemListRow> items = FXCollections.observableArrayList();
    private final ObjectProperty<OrderItemListRow> selectedItem = new SimpleObjectProperty<>();
    private final StringProperty title = new SimpleStringProperty("Позиции заказа");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canOpenSelected = new SimpleBooleanProperty(false);

    private OrderId orderId;
    private OrderStatus orderStatus;
    private Runnable onBackToOrder = () -> {
    };
    private Runnable onCreateItem = () -> {
    };
    private Consumer<OrderItemId> onOpenItem = id -> {
    };

    public OrderItemListViewModel(
            OrderQueryService orderQueryService, AuthorizationService authorization) {
        this(orderQueryService, authorization, null, null);
    }

    public OrderItemListViewModel(
            OrderQueryService orderQueryService,
            AuthorizationService authorization,
            OrderItemEditorQueryService editorQuery,
            ProductionQueryApi productionQuery) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.editorQuery = editorQuery;
        this.productionQuery = productionQuery;
        selectedItem.addListener((obs, oldValue, newValue) -> canOpenSelected.set(newValue != null));
    }

    public void setOnBackToOrder(Runnable onBackToOrder) {
        this.onBackToOrder = Objects.requireNonNull(onBackToOrder, "onBackToOrder");
    }

    public void setOnCreateItem(Runnable onCreateItem) {
        this.onCreateItem = Objects.requireNonNull(onCreateItem, "onCreateItem");
    }

    public void setOnOpenItem(Consumer<OrderItemId> onOpenItem) {
        this.onOpenItem = Objects.requireNonNull(onOpenItem, "onOpenItem");
    }

    public void openForOrder(OrderId orderId, OrderStatus orderStatus) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.orderStatus = Objects.requireNonNull(orderStatus, "orderStatus");
        title.set("Позиции заказа");
        refresh();
    }

    public void refresh() {
        errorMessage.set("");
        if (orderId == null) {
            items.clear();
            refreshActionFlags();
            return;
        }
        try {
            PageResult<OrderItemDto> page =
                    orderQueryService.getOrderItems(orderId, PageRequest.firstPage());
            List<OrderItemListRow> rows = new ArrayList<>(page.content().size());
            for (OrderItemDto item : page.content()) {
                rows.add(toRow(item));
            }
            items.setAll(rows);
            refreshActionFlags();
        } catch (AccessDeniedException ex) {
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.LOAD));
            refreshActionFlags();
        } catch (RuntimeException ex) {
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.LOAD));
            refreshActionFlags();
        }
    }

    public void createItem() {
        if (!canCreate.get()) {
            return;
        }
        onCreateItem.run();
    }

    public void openSelected() {
        OrderItemListRow selected = selectedItem.get();
        if (selected == null) {
            return;
        }
        onOpenItem.accept(selected.orderItemId());
    }

    public void backToOrder() {
        onBackToOrder.run();
    }

    public ObservableList<OrderItemListRow> items() {
        return items;
    }

    public ObjectProperty<OrderItemListRow> selectedItemProperty() {
        return selectedItem;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public BooleanProperty canCreateProperty() {
        return canCreate;
    }

    public BooleanProperty canOpenSelectedProperty() {
        return canOpenSelected;
    }

    public OrderId currentOrderId() {
        return orderId;
    }

    OrderId orderIdForTest() {
        return orderId;
    }

    private OrderItemListRow toRow(OrderItemDto item) {
        String quantity = resolveQuantityDisplay(item.orderItemId());
        OrderItemOperationalStatus status = resolveOperationalStatus(item);
        return new OrderItemListRow(item, quantity, status);
    }

    private String resolveQuantityDisplay(OrderItemId orderItemId) {
        if (editorQuery == null) {
            return "";
        }
        try {
            Optional<OrderItemEditorSnapshot> snapshot = editorQuery.getEditorSnapshot(orderItemId);
            if (snapshot.isEmpty()) {
                return "";
            }
            return ProductQuantityUiValidation.formatForDisplay(snapshot.get().orderedQuantity());
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Failed to load item quantity for list", ex);
            return "";
        }
    }

    private OrderItemOperationalStatus resolveOperationalStatus(OrderItemDto item) {
        OrderStatus parent = orderStatus == null ? OrderStatus.DRAFT : orderStatus;
        Optional<ItemProductionStateView> productionFacts = Optional.empty();
        if (parent == OrderStatus.ACTIVE) {
            productionFacts = loadProductionFacts(item.orderItemId());
        }
        return OrderItemOperationalStatusDeriver.derive(parent, item.status(), productionFacts);
    }

    private Optional<ItemProductionStateView> loadProductionFacts(OrderItemId orderItemId) {
        if (productionQuery == null) {
            return Optional.empty();
        }
        try {
            return productionQuery.getItemProductionState(orderItemId.value());
        } catch (AccessDeniedException ex) {
            LOGGER.log(
                    Level.WARNING,
                    "Production facts unavailable (access denied) for item " + orderItemId.value(),
                    ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            LOGGER.log(
                    Level.WARNING,
                    "Production facts unavailable for item " + orderItemId.value(),
                    ex);
            return Optional.empty();
        }
    }

    private void refreshActionFlags() {
        boolean hasCreate =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_CREATE_PERMISSION));
        canCreate.set(orderStatus == OrderStatus.DRAFT && hasCreate);
    }
}
