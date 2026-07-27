package com.tmp.ui.shell.screen.orderitemlist;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
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
 * Order item list ViewModel. Loads items only through {@link OrderQueryService#getOrderItems}.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderItemListViewModel {

    private final OrderQueryService orderQueryService;
    private final AuthorizationService authorization;

    private final ObservableList<OrderItemDto> items = FXCollections.observableArrayList();
    private final ObjectProperty<OrderItemDto> selectedItem = new SimpleObjectProperty<>();
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
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
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
            items.setAll(page.content());
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
        OrderItemDto selected = selectedItem.get();
        if (selected == null) {
            return;
        }
        onOpenItem.accept(selected.orderItemId());
    }

    public void backToOrder() {
        onBackToOrder.run();
    }

    public ObservableList<OrderItemDto> items() {
        return items;
    }

    public ObjectProperty<OrderItemDto> selectedItemProperty() {
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

    private void refreshActionFlags() {
        boolean hasCreate =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_CREATE_PERMISSION));
        canCreate.set(orderStatus == OrderStatus.DRAFT && hasCreate);
    }
}
