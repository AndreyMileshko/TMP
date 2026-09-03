package com.tmp.ui.shell.screen.orderitemlist;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.ProductQuantityUiValidation;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import com.tmp.ui.shell.order.worklist.ItemProductionReadResult;
import com.tmp.ui.shell.order.worklist.ItemProductionStateReader;
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
 * Order item list ViewModel. Loads items through {@link OrderQueryService#getOrderItems} with
 * normal pagination and derives operational status / quantity for presentation.
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
    private final OrderItemDocumentUiService itemDocuments;

    private final ObservableList<OrderItemListRow> items = FXCollections.observableArrayList();
    private final ObjectProperty<OrderItemListRow> selectedItem = new SimpleObjectProperty<>();
    private final StringProperty title = new SimpleStringProperty("Позиции заказа");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty canCreate = new SimpleBooleanProperty(false);
    private final BooleanProperty canOpenSelected = new SimpleBooleanProperty(false);
    private final IntegerProperty pageIndex = new SimpleIntegerProperty(0);
    private final IntegerProperty pageSize = new SimpleIntegerProperty(PageRequest.DEFAULT_PAGE_SIZE);
    private final LongProperty totalElements = new SimpleLongProperty(0);
    private final BooleanProperty canGoPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canGoNext = new SimpleBooleanProperty(false);

    private OrderId orderId;
    private OrderStatus orderStatus;
    private OrderItemId pendingSelection;
    private Runnable onBackToOrder = () -> {
    };
    private Runnable onCreateItem = () -> {
    };
    private Consumer<OrderItemId> onOpenSpecification = id -> {
    };
    private Consumer<OrderItemId> onEditItem = id -> {
    };

    public OrderItemListViewModel(
            OrderQueryService orderQueryService, AuthorizationService authorization) {
        this(orderQueryService, authorization, null, null, null);
    }

    public OrderItemListViewModel(
            OrderQueryService orderQueryService,
            AuthorizationService authorization,
            OrderItemEditorQueryService editorQuery,
            ProductionQueryApi productionQuery) {
        this(orderQueryService, authorization, editorQuery, productionQuery, null);
    }

    public OrderItemListViewModel(
            OrderQueryService orderQueryService,
            AuthorizationService authorization,
            OrderItemEditorQueryService editorQuery,
            ProductionQueryApi productionQuery,
            OrderItemDocumentUiService itemDocuments) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.editorQuery = editorQuery;
        this.productionQuery = productionQuery;
        this.itemDocuments = itemDocuments;
        selectedItem.addListener((obs, oldValue, newValue) -> canOpenSelected.set(newValue != null));
    }

    public void setOnBackToOrder(Runnable onBackToOrder) {
        this.onBackToOrder = Objects.requireNonNull(onBackToOrder, "onBackToOrder");
    }

    public void setOnCreateItem(Runnable onCreateItem) {
        this.onCreateItem = Objects.requireNonNull(onCreateItem, "onCreateItem");
    }

    public void setOnOpenSpecification(Consumer<OrderItemId> onOpenSpecification) {
        this.onOpenSpecification =
                Objects.requireNonNull(onOpenSpecification, "onOpenSpecification");
    }

    public void setOnEditItem(Consumer<OrderItemId> onEditItem) {
        this.onEditItem = Objects.requireNonNull(onEditItem, "onEditItem");
    }

    public void openForOrder(OrderId orderId, OrderStatus orderStatus) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.orderStatus = Objects.requireNonNull(orderStatus, "orderStatus");
        title.set("Позиции заказа");
        pageIndex.set(0);
        pendingSelection = null;
        refresh();
    }

    public void restoreMemento(OrderItemListMemento memento, OrderStatus orderStatus) {
        Objects.requireNonNull(memento, "memento");
        this.orderId = memento.orderId();
        this.orderStatus = Objects.requireNonNull(orderStatus, "orderStatus");
        title.set("Позиции заказа");
        pageIndex.set(Math.max(0, memento.pageIndex()));
        pendingSelection = memento.selectedItemId();
        refresh();
    }

    public OrderItemListMemento captureMemento() {
        if (orderId == null) {
            return null;
        }
        OrderItemId selected =
                selectedItem.get() == null ? null : selectedItem.get().orderItemId();
        return new OrderItemListMemento(orderId, pageIndex.get(), selected);
    }

    public void refresh() {
        errorMessage.set("");
        if (orderId == null) {
            items.clear();
            totalElements.set(0);
            updatePaginationFlags();
            refreshActionFlags();
            return;
        }
        try {
            PageRequest request = PageRequest.of(pageIndex.get(), pageSize.get());
            PageResult<OrderItemDto> page = orderQueryService.getOrderItems(orderId, request);
            totalElements.set(page.totalElements());
            pageIndex.set(page.pageIndex());
            ItemProductionStateReader.BatchLoad productionBatch = loadProductionBatch();
            List<OrderItemListRow> rows = new ArrayList<>(page.content().size());
            for (OrderItemDto item : page.content()) {
                rows.add(toRow(item, productionBatch));
            }
            items.setAll(rows);
            restorePendingSelection();
            updatePaginationFlags();
            refreshActionFlags();
        } catch (AccessDeniedException ex) {
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.LOAD));
            items.clear();
            totalElements.set(0);
            updatePaginationFlags();
            refreshActionFlags();
        } catch (RuntimeException ex) {
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.LOAD));
            items.clear();
            totalElements.set(0);
            updatePaginationFlags();
            refreshActionFlags();
        }
    }

    public void nextPage() {
        long total = totalElements.get();
        int size = Math.max(1, pageSize.get());
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / size);
        if (pageIndex.get() < maxPage) {
            pageIndex.set(pageIndex.get() + 1);
            pendingSelection = null;
            refresh();
        }
    }

    public void previousPage() {
        if (pageIndex.get() > 0) {
            pageIndex.set(pageIndex.get() - 1);
            pendingSelection = null;
            refresh();
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
        openSpecification(selected);
    }

    public void openSpecification(OrderItemListRow row) {
        if (row == null) {
            return;
        }
        if (!hasSpecificationViewPermission()) {
            errorMessage.set(OrderUiErrorMapper.ACCESS_DENIED);
            return;
        }
        onOpenSpecification.accept(row.orderItemId());
    }

    public void editSelected() {
        OrderItemListRow selected = selectedItem.get();
        if (selected == null || !canEditItem(selected)) {
            return;
        }
        onEditItem.accept(selected.orderItemId());
    }

    public void editItem(OrderItemListRow row) {
        if (row == null || !canEditItem(row)) {
            return;
        }
        selectedItem.set(row);
        onEditItem.accept(row.orderItemId());
    }

    public void cancelItem(OrderItemListRow row) {
        if (row == null || !canCancelItem(row) || itemDocuments == null) {
            return;
        }
        errorMessage.set("");
        try {
            java.util.UUID cancelDoc =
                    itemDocuments.beginItemCancel(
                            "ORDER_ITEM_CANCEL " + row.orderItemId().value(), row.orderItemId());
            itemDocuments.postDocument(cancelDoc);
            refresh();
        } catch (AccessDeniedException ex) {
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.CANCEL));
        } catch (RuntimeException ex) {
            errorMessage.set(OrderUiErrorMapper.text(ex, OrderUiOperation.CANCEL));
        }
    }

    public boolean canEditItem(OrderItemListRow row) {
        if (row == null || orderStatus == null) {
            return false;
        }
        boolean hasEdit =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_EDIT_PERMISSION));
        return hasEdit
                && OrderItemOperationalStatusDeriver.isItemDataEditable(orderStatus, row.item().status());
    }

    public boolean canCancelItem(OrderItemListRow row) {
        if (row == null || orderStatus == null) {
            return false;
        }
        boolean hasCancel =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_CANCEL_PERMISSION));
        return hasCancel
                && OrderItemOperationalStatusDeriver.isItemCancellable(orderStatus, row.item().status());
    }

    public boolean hasSpecificationViewPermission() {
        return authorization.hasPermission(
                PermissionId.of(UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION));
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

    public IntegerProperty pageIndexProperty() {
        return pageIndex;
    }

    public IntegerProperty pageSizeProperty() {
        return pageSize;
    }

    public LongProperty totalElementsProperty() {
        return totalElements;
    }

    public BooleanProperty canGoPreviousProperty() {
        return canGoPrevious;
    }

    public BooleanProperty canGoNextProperty() {
        return canGoNext;
    }

    public OrderId currentOrderId() {
        return orderId;
    }

    OrderId orderIdForTest() {
        return orderId;
    }

    private OrderItemListRow toRow(
            OrderItemDto item, ItemProductionStateReader.BatchLoad productionBatch) {
        String quantity = resolveQuantityDisplay(item.orderItemId());
        OrderItemOperationalStatus status = resolveOperationalStatus(item, productionBatch);
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

    private OrderItemOperationalStatus resolveOperationalStatus(
            OrderItemDto item, ItemProductionStateReader.BatchLoad productionBatch) {
        OrderStatus parent = orderStatus == null ? OrderStatus.DRAFT : orderStatus;
        ItemProductionReadResult productionRead = ItemProductionReadResult.successNotAccepted();
        if (parent == OrderStatus.ACTIVE) {
            productionRead =
                    ItemProductionStateReader.fromBatch(productionBatch, item.orderItemId().value());
        }
        return OrderItemOperationalStatusDeriver.derive(parent, item.status(), productionRead);
    }

    private ItemProductionStateReader.BatchLoad loadProductionBatch() {
        if (orderStatus != OrderStatus.ACTIVE || orderId == null) {
            return ItemProductionStateReader.BatchLoad.ok(java.util.Map.of());
        }
        return ItemProductionStateReader.loadBatch(productionQuery, orderId.value());
    }

    private void restorePendingSelection() {
        if (pendingSelection == null) {
            return;
        }
        OrderItemId wanted = pendingSelection;
        pendingSelection = null;
        for (OrderItemListRow row : items) {
            if (row.orderItemId().equals(wanted)) {
                selectedItem.set(row);
                return;
            }
        }
    }

    private void updatePaginationFlags() {
        long total = totalElements.get();
        int size = Math.max(1, pageSize.get());
        int page = pageIndex.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / size);
        canGoPrevious.set(page > 0);
        canGoNext.set(page < maxPage);
    }

    private void refreshActionFlags() {
        boolean hasCreate =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_CREATE_PERMISSION));
        canCreate.set(orderStatus == OrderStatus.DRAFT && hasCreate);
    }
}
