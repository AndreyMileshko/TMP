package com.tmp.ui.shell.screen.orderitemeditor;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationRef;
import com.tmp.order.api.ui.CurrentOrderItemSpecificationUiService;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * User-facing order item card. Mutations go through {@link OrderItemDocumentUiService} facades
 * ({@code saveNewItem} / {@code saveExistingItem}); specification open uses {@link
 * CurrentOrderItemSpecificationUiService}.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderItemEditorViewModel {

    public enum Mode {
        CREATE,
        VIEW_EXISTING
    }

    private final OrderItemDocumentUiService itemDocuments;
    private final OrderItemEditorQueryService editorQuery;
    private final AuthorizationService authorization;
    private final OrderQueryService orderQueryService;
    private final CurrentOrderItemSpecificationUiService currentSpecification;
    private final ProductionQueryApi productionQuery;

    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.CREATE);
    private final StringProperty title = new SimpleStringProperty("Новая позиция");
    private final ObjectProperty<OrderItemOperationalStatus> operationalStatus =
            new SimpleObjectProperty<>();
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty successMessage = new SimpleStringProperty("");
    private final StringProperty productCode = new SimpleStringProperty("");
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty comments = new SimpleStringProperty("");
    private final StringProperty externalPositionNumber = new SimpleStringProperty("");
    private final StringProperty orderedQuantity = new SimpleStringProperty("1");
    private final BooleanProperty fieldsEditable = new SimpleBooleanProperty(false);
    private final BooleanProperty canSave = new SimpleBooleanProperty(false);
    private final BooleanProperty canCancelItem = new SimpleBooleanProperty(false);
    private final BooleanProperty canOpenSpecification = new SimpleBooleanProperty(false);

    private OrderId orderId;
    private OrderItemId orderItemId;
    private OrderItemStatus itemStatus;
    private OrderStatus parentOrderStatus = OrderStatus.DRAFT;
    private RevisionNumber draftRevisionNumber;
    private RevisionNumber activeRevisionNumber;
    private Runnable onBackToItemList = () -> {
    };
    private Consumer<OrderItemId> onItemOpened = id -> {
    };
    private Consumer<RevisionTarget> onOpenSpecification = target -> {
    };

    public OrderItemEditorViewModel(
            OrderItemDocumentUiService itemDocuments,
            OrderItemEditorQueryService editorQuery,
            AuthorizationService authorization) {
        this(itemDocuments, editorQuery, authorization, null, null, null);
    }

    public OrderItemEditorViewModel(
            OrderItemDocumentUiService itemDocuments,
            OrderItemEditorQueryService editorQuery,
            AuthorizationService authorization,
            OrderQueryService orderQueryService) {
        this(itemDocuments, editorQuery, authorization, orderQueryService, null, null);
    }

    public OrderItemEditorViewModel(
            OrderItemDocumentUiService itemDocuments,
            OrderItemEditorQueryService editorQuery,
            AuthorizationService authorization,
            OrderQueryService orderQueryService,
            CurrentOrderItemSpecificationUiService currentSpecification,
            ProductionQueryApi productionQuery) {
        this.itemDocuments = Objects.requireNonNull(itemDocuments, "itemDocuments");
        this.editorQuery = Objects.requireNonNull(editorQuery, "editorQuery");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.orderQueryService = orderQueryService;
        this.currentSpecification = currentSpecification;
        this.productionQuery = productionQuery;
    }

    public void setOnBackToItemList(Runnable onBackToItemList) {
        this.onBackToItemList = Objects.requireNonNull(onBackToItemList, "onBackToItemList");
    }

    public void setOnItemOpened(Consumer<OrderItemId> onItemOpened) {
        this.onItemOpened = Objects.requireNonNull(onItemOpened, "onItemOpened");
    }

    public void setOnOpenSpecification(Consumer<RevisionTarget> onOpenSpecification) {
        this.onOpenSpecification =
                Objects.requireNonNull(onOpenSpecification, "onOpenSpecification");
    }

    public void openCreate(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        clearMessages();
        mode.set(Mode.CREATE);
        title.set("Новая позиция");
        this.orderId = orderId;
        refreshParentOrderStatus();
        orderItemId = null;
        itemStatus = null;
        draftRevisionNumber = null;
        activeRevisionNumber = null;
        productCode.set("");
        name.set("");
        comments.set("");
        externalPositionNumber.set("");
        orderedQuantity.set("1");
        operationalStatus.set(OrderItemOperationalStatus.EDITING);
        refreshActionFlags();
    }

    public void openExisting(OrderItemId id) {
        Objects.requireNonNull(id, "id");
        clearMessages();
        reloadExisting(id);
    }

    private void reloadExisting(OrderItemId id) {
        mode.set(Mode.VIEW_EXISTING);
        errorMessage.set("");
        try {
            Optional<OrderItemEditorSnapshot> loaded = editorQuery.getEditorSnapshot(id);
            if (loaded.isEmpty()) {
                showError(OrderUiErrorMapper.NOT_FOUND);
                return;
            }
            applySnapshot(loaded.get());
            onItemOpened.accept(id);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        }
    }

    /** User-facing Save: create or update via application facade (no technical document chain). */
    public void save() {
        clearMessages();
        if (!parentAllowsMutation()) {
            return;
        }
        try {
            OrderItemCommercialDraft draft = currentCommercialDraft();
            String quantity =
                    ProductQuantityUiValidation.requireValidNormalizedProductQuantity(
                            orderedQuantity.get());
            orderedQuantity.set(quantity);
            if (mode.get() == Mode.CREATE) {
                OrderItemId created = itemDocuments.saveNewItem(orderId, draft, quantity);
                showSuccess("Позиция сохранена");
                reloadExisting(created);
            } else {
                if (orderItemId == null) {
                    showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
                    return;
                }
                OrderItemId saved = itemDocuments.saveExistingItem(orderItemId, draft, quantity);
                showSuccess("Позиция сохранена");
                reloadExisting(saved);
            }
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.SAVE_DRAFT);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.SAVE_DRAFT);
        }
    }

    public void cancelItem() {
        clearMessages();
        if (!parentAllowsMutation()) {
            return;
        }
        try {
            if (orderItemId == null || itemStatus != OrderItemStatus.DRAFT) {
                showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
                return;
            }
            UUID cancelDoc =
                    itemDocuments.beginItemCancel(
                            "ORDER_ITEM_CANCEL " + orderItemId.value(), orderItemId);
            OrderItemId result = itemDocuments.postDocument(cancelDoc);
            showSuccess("Позиция отменена");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.CANCEL);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.CANCEL);
        }
    }

    public void openSpecification() {
        clearMessages();
        if (orderItemId == null) {
            errorMessage.set("Сначала сохраните позицию");
            return;
        }
        if (!authorization.hasPermission(
                PermissionId.of(UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION))) {
            showError(OrderUiErrorMapper.ACCESS_DENIED);
            return;
        }
        if (currentSpecification == null) {
            showError(OrderUiErrorMapper.NOT_FOUND);
            return;
        }
        try {
            Optional<CurrentOrderItemSpecificationRef> ref =
                    currentSpecification.resolveCurrent(orderItemId);
            if (ref.isEmpty()) {
                errorMessage.set("Нет спецификации для открытия");
                return;
            }
            onOpenSpecification.accept(
                    new RevisionTarget(ref.get().orderItemId(), ref.get().revisionNumber()));
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        }
    }

    public void backToItemList() {
        onBackToItemList.run();
    }

    /** Navigation target for the Specification editor. */
    public static final class RevisionTarget {
        private final OrderItemId orderItemId;
        private final RevisionNumber revisionNumber;

        public RevisionTarget(OrderItemId orderItemId, RevisionNumber revisionNumber) {
            this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
            this.revisionNumber = Objects.requireNonNull(revisionNumber, "revisionNumber");
        }

        public OrderItemId orderItemId() {
            return orderItemId;
        }

        public RevisionNumber revisionNumber() {
            return revisionNumber;
        }
    }

    public ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public ObjectProperty<OrderItemOperationalStatus> operationalStatusProperty() {
        return operationalStatus;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public StringProperty productCodeProperty() {
        return productCode;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty commentsProperty() {
        return comments;
    }

    public StringProperty externalPositionNumberProperty() {
        return externalPositionNumber;
    }

    public StringProperty orderedQuantityProperty() {
        return orderedQuantity;
    }

    public BooleanProperty fieldsEditableProperty() {
        return fieldsEditable;
    }

    public BooleanProperty canSaveProperty() {
        return canSave;
    }

    public BooleanProperty canCancelItemProperty() {
        return canCancelItem;
    }

    public BooleanProperty canOpenSpecificationProperty() {
        return canOpenSpecification;
    }

    OrderItemId orderItemIdForTest() {
        return orderItemId;
    }

    private void applySnapshot(OrderItemEditorSnapshot snapshot) {
        orderItemId = snapshot.orderItemId();
        orderId = snapshot.orderId();
        refreshParentOrderStatus();
        itemStatus = snapshot.status();
        activeRevisionNumber = snapshot.activeRevisionNumber().orElse(null);
        draftRevisionNumber = snapshot.draftRevisionNumber().orElse(null);
        title.set(
                snapshot.productCode() == null || snapshot.productCode().isBlank()
                        ? "Позиция"
                        : "Позиция " + snapshot.productCode());
        productCode.set(nullToEmpty(snapshot.productCode()));
        name.set(nullToEmpty(snapshot.name()));
        comments.set(nullToEmpty(snapshot.comments()));
        externalPositionNumber.set(nullToEmpty(snapshot.externalPositionNumber()));
        orderedQuantity.set(ProductQuantityUiValidation.formatForDisplay(snapshot.orderedQuantity()));
        operationalStatus.set(deriveOperationalStatus(snapshot.status()));
        refreshActionFlags();
    }

    private OrderItemOperationalStatus deriveOperationalStatus(OrderItemStatus status) {
        OrderStatus parent = parentOrderStatus == null ? OrderStatus.DRAFT : parentOrderStatus;
        ItemProductionReadResult productionRead = ItemProductionReadResult.successNotAccepted();
        if (parent == OrderStatus.ACTIVE && orderItemId != null) {
            productionRead = ItemProductionStateReader.readOne(productionQuery, orderItemId.value());
        } else if (parent == OrderStatus.ACTIVE) {
            productionRead = ItemProductionReadResult.unavailable();
        }
        return OrderItemOperationalStatusDeriver.derive(parent, status, productionRead);
    }

    private void refreshActionFlags() {
        boolean hasCreate =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_CREATE_PERMISSION));
        boolean hasEdit =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_EDIT_PERMISSION));
        boolean hasCancel =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_CANCEL_PERMISSION));
        boolean hasSpecificationView =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION));
        boolean parentDraft = parentOrderStatus == OrderStatus.DRAFT;

        if (mode.get() == Mode.CREATE) {
            fieldsEditable.set(hasCreate && parentDraft);
            canSave.set(hasCreate && parentDraft);
            canCancelItem.set(false);
            canOpenSpecification.set(false);
            return;
        }

        boolean draftItem = itemStatus == OrderItemStatus.DRAFT;
        boolean cancelled = itemStatus == OrderItemStatus.CANCELLED;
        boolean hasRevision = draftRevisionNumber != null || activeRevisionNumber != null;

        fieldsEditable.set(draftItem && hasEdit && parentDraft && !cancelled);
        canSave.set(draftItem && hasEdit && parentDraft && !cancelled);
        canCancelItem.set(draftItem && hasCancel && parentDraft && !cancelled);
        canOpenSpecification.set(hasRevision && hasSpecificationView && !cancelled);

        if (!parentDraft) {
            fieldsEditable.set(false);
            canSave.set(false);
            canCancelItem.set(false);
        }
    }

    private void refreshParentOrderStatus() {
        if (orderQueryService == null || orderId == null) {
            parentOrderStatus = OrderStatus.DRAFT;
            return;
        }
        try {
            parentOrderStatus =
                    orderQueryService
                            .getOrder(orderId)
                            .map(dto -> dto.status())
                            .orElse(OrderStatus.DRAFT);
        } catch (RuntimeException ex) {
            parentOrderStatus = OrderStatus.ACTIVE;
        }
    }

    private boolean parentAllowsMutation() {
        if (parentOrderStatus == OrderStatus.DRAFT) {
            return true;
        }
        showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
        return false;
    }

    private OrderItemCommercialDraft currentCommercialDraft() {
        return OrderItemCommercialDraft.of(
                productCode.get(),
                name.get(),
                blankToNull(comments.get()),
                blankToNull(externalPositionNumber.get()));
    }

    private void clearMessages() {
        errorMessage.set("");
        successMessage.set("");
    }

    private void showSuccess(String message) {
        errorMessage.set("");
        successMessage.set(message);
    }

    private void showError(String message) {
        successMessage.set("");
        errorMessage.set(message);
    }

    private void showMappedError(Throwable error, OrderUiOperation operation) {
        if (error instanceof IllegalArgumentException validationError
                && validationError.getMessage() != null
                && !validationError.getMessage().isBlank()) {
            showError(validationError.getMessage());
            return;
        }
        showError(OrderUiErrorMapper.text(error, operation));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
