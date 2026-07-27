package com.tmp.ui.shell.screen.ordereditor;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.api.ui.OrderHeaderDraft;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
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
 * Document-driven order editor. Mutating actions go only through {@link OrderDocumentUiService}.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderEditorViewModel {

    public enum Mode {
        CREATE,
        VIEW_EXISTING
    }

    private static final String PERM_VIEW = "order.order.view";
    private static final String PERM_CREATE = "order.order.create";
    private static final String PERM_EDIT = "order.order.edit";
    private static final String PERM_APPROVE = "order.order.approve";
    private static final String PERM_CANCEL = "order.order.cancel";

    private final OrderQueryService orderQueryService;
    private final OrderDocumentUiService orderDocuments;
    private final AuthorizationService authorization;

    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.CREATE);
    private final StringProperty title = new SimpleStringProperty("Заказ");
    private final StringProperty statusText = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty successMessage = new SimpleStringProperty("");
    private final StringProperty orderNumber = new SimpleStringProperty("");
    private final StringProperty customerRef = new SimpleStringProperty("");
    private final StringProperty customerName = new SimpleStringProperty("");
    private final StringProperty contractRef = new SimpleStringProperty("");
    private final StringProperty siteRef = new SimpleStringProperty("");
    private final StringProperty responsibleManager = new SimpleStringProperty("");
    private final StringProperty direction = new SimpleStringProperty("PRIVATE");
    private final StringProperty currency = new SimpleStringProperty("RUB");
    private final BooleanProperty fieldsEditable = new SimpleBooleanProperty(true);
    private final BooleanProperty canSaveDraft = new SimpleBooleanProperty(false);
    private final BooleanProperty canPost = new SimpleBooleanProperty(false);
    private final BooleanProperty canApprove = new SimpleBooleanProperty(false);
    private final BooleanProperty canCancel = new SimpleBooleanProperty(false);
    private final BooleanProperty orderNumberEditable = new SimpleBooleanProperty(true);
    private final BooleanProperty canOpenItems = new SimpleBooleanProperty(false);

    private OrderId orderId;
    private OrderStatus orderStatus;
    private UUID documentId;
    private long payloadRevision;
    private Runnable onBackToList = () -> {
    };
    private Consumer<OrderId> onOrderOpened = id -> {
    };
    private Runnable onOpenItems = () -> {
    };

    public OrderEditorViewModel(
            OrderQueryService orderQueryService,
            OrderDocumentUiService orderDocuments,
            AuthorizationService authorization) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.orderDocuments = Objects.requireNonNull(orderDocuments, "orderDocuments");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public void setOnBackToList(Runnable onBackToList) {
        this.onBackToList = Objects.requireNonNull(onBackToList, "onBackToList");
    }

    public void setOnOrderOpened(Consumer<OrderId> onOrderOpened) {
        this.onOrderOpened = Objects.requireNonNull(onOrderOpened, "onOrderOpened");
    }

    public void setOnOpenItems(Runnable onOpenItems) {
        this.onOpenItems = Objects.requireNonNull(onOpenItems, "onOpenItems");
    }

    public void openCreate() {
        clearMessages();
        mode.set(Mode.CREATE);
        title.set("Новый заказ");
        orderId = null;
        orderStatus = null;
        documentId = null;
        payloadRevision = 0L;
        orderNumber.set("");
        customerRef.set("");
        customerName.set("");
        contractRef.set("");
        siteRef.set("");
        responsibleManager.set("");
        direction.set("PRIVATE");
        currency.set("RUB");
        statusText.set("CREATE");
        refreshActionFlags();
    }

    public void openExisting(OrderId id) {
        Objects.requireNonNull(id, "id");
        clearMessages();
        reloadExisting(id);
    }

    /**
     * Reloads order state without clearing a previously set success message (used after post /
     * approve / cancel).
     */
    private void reloadExisting(OrderId id) {
        mode.set(Mode.VIEW_EXISTING);
        documentId = null;
        payloadRevision = 0L;
        errorMessage.set("");
        try {
            Optional<OrderDto> loaded = orderQueryService.getOrder(id);
            if (loaded.isEmpty()) {
                showError(OrderUiErrorMapper.NOT_FOUND);
                return;
            }
            applyOrder(loaded.get());
            onOrderOpened.accept(id);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        }
    }

    public void saveDraft() {
        clearMessages();
        try {
            OrderHeaderDraft draft = currentDraft();
            if (mode.get() == Mode.CREATE) {
                if (documentId == null) {
                    documentId = orderDocuments.beginOrderCreate("ORDER_CREATE " + draft.orderNumber());
                    payloadRevision = 0L;
                }
                payloadRevision = orderDocuments.saveCreateDraft(documentId, draft, payloadRevision);
                showSuccess("Черновик документа сохранён");
            } else {
                if (orderId == null || orderStatus != OrderStatus.DRAFT) {
                    showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
                    return;
                }
                if (documentId == null) {
                    documentId = orderDocuments.beginOrderUpdate("ORDER_UPDATE " + orderId.value(), orderId);
                    payloadRevision = 0L;
                }
                payloadRevision =
                        orderDocuments.saveUpdateDraft(documentId, orderId, draft, payloadRevision);
                showSuccess("Черновик изменения сохранён");
            }
            refreshActionFlags();
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.SAVE_DRAFT);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.SAVE_DRAFT);
        }
    }

    public void postCurrentDocument() {
        clearMessages();
        try {
            if (documentId == null) {
                errorMessage.set("Сначала сохраните черновик документа");
                return;
            }
            OrderId result = orderDocuments.postDocument(documentId);
            documentId = null;
            payloadRevision = 0L;
            showSuccess("Документ проведён");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.POST_DOCUMENT);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.POST_DOCUMENT);
        }
    }

    public void approveOrder() {
        clearMessages();
        try {
            if (orderId == null || orderStatus != OrderStatus.DRAFT) {
                showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
                return;
            }
            UUID approveDoc =
                    orderDocuments.beginOrderApprove("ORDER_APPROVE " + orderId.value(), orderId);
            OrderId result = orderDocuments.postDocument(approveDoc);
            showSuccess("Заказ утверждён");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.APPROVE);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.APPROVE);
        }
    }

    public void cancelOrder() {
        clearMessages();
        try {
            if (orderId == null || orderStatus != OrderStatus.DRAFT) {
                showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
                return;
            }
            UUID cancelDoc =
                    orderDocuments.beginOrderCancel("ORDER_CANCEL " + orderId.value(), orderId);
            OrderId result = orderDocuments.postDocument(cancelDoc);
            showSuccess("Заказ отменён");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.CANCEL);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.CANCEL);
        }
    }

    public void backToList() {
        onBackToList.run();
    }

    public void openItems() {
        if (orderId == null || !canOpenItems.get()) {
            return;
        }
        onOpenItems.run();
    }

    public OrderId currentOrderId() {
        return orderId;
    }

    public OrderStatus currentOrderStatus() {
        return orderStatus;
    }

    public ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty statusTextProperty() {
        return statusText;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public StringProperty orderNumberProperty() {
        return orderNumber;
    }

    public StringProperty customerRefProperty() {
        return customerRef;
    }

    public StringProperty customerNameProperty() {
        return customerName;
    }

    public StringProperty contractRefProperty() {
        return contractRef;
    }

    public StringProperty siteRefProperty() {
        return siteRef;
    }

    public StringProperty responsibleManagerProperty() {
        return responsibleManager;
    }

    public StringProperty directionProperty() {
        return direction;
    }

    public StringProperty currencyProperty() {
        return currency;
    }

    public BooleanProperty fieldsEditableProperty() {
        return fieldsEditable;
    }

    public BooleanProperty orderNumberEditableProperty() {
        return orderNumberEditable;
    }

    public BooleanProperty canSaveDraftProperty() {
        return canSaveDraft;
    }

    public BooleanProperty canPostProperty() {
        return canPost;
    }

    public BooleanProperty canApproveProperty() {
        return canApprove;
    }

    public BooleanProperty canCancelProperty() {
        return canCancel;
    }

    public BooleanProperty canOpenItemsProperty() {
        return canOpenItems;
    }

    OrderId orderIdForTest() {
        return orderId;
    }

    UUID documentIdForTest() {
        return documentId;
    }

    private void applyOrder(OrderDto dto) {
        orderId = dto.orderId();
        orderStatus = dto.status();
        title.set("Заказ " + dto.orderNumber());
        statusText.set(dto.status().name());
        orderNumber.set(dto.orderNumber());
        customerRef.set(nullToEmpty(dto.customerRef()));
        customerName.set(dto.customerName());
        contractRef.set(nullToEmpty(dto.contractRef()));
        siteRef.set(nullToEmpty(dto.siteRef()));
        responsibleManager.set(nullToEmpty(dto.responsibleManager()));
        direction.set(dto.direction());
        currency.set(dto.currency());
        refreshActionFlags();
    }

    private void refreshActionFlags() {
        boolean hasView = authorization.hasPermission(PermissionId.of(PERM_VIEW));
        boolean hasCreate = authorization.hasPermission(PermissionId.of(PERM_CREATE));
        boolean hasEdit = authorization.hasPermission(PermissionId.of(PERM_EDIT));
        boolean hasApprove = authorization.hasPermission(PermissionId.of(PERM_APPROVE));
        boolean hasCancel = authorization.hasPermission(PermissionId.of(PERM_CANCEL));

        if (mode.get() == Mode.CREATE) {
            fieldsEditable.set(hasCreate);
            orderNumberEditable.set(hasCreate);
            canSaveDraft.set(hasCreate);
            canPost.set(hasCreate && documentId != null);
            canApprove.set(false);
            canCancel.set(false);
            canOpenItems.set(false);
            return;
        }

        boolean draft = orderStatus == OrderStatus.DRAFT;
        boolean readOnlyStatus =
                orderStatus == OrderStatus.APPROVED || orderStatus == OrderStatus.CANCELLED;
        boolean hasItemView =
                authorization.hasPermission(PermissionId.of("order.item.view"));
        fieldsEditable.set(hasView && draft && hasEdit);
        orderNumberEditable.set(false);
        canSaveDraft.set(draft && hasEdit);
        canPost.set(draft && hasEdit && documentId != null);
        canApprove.set(draft && hasApprove);
        canCancel.set(draft && hasCancel);
        canOpenItems.set(orderId != null && hasItemView);
        if (readOnlyStatus) {
            fieldsEditable.set(false);
            canSaveDraft.set(false);
            canPost.set(false);
            canApprove.set(false);
            canCancel.set(false);
        }
    }

    private OrderHeaderDraft currentDraft() {
        return OrderHeaderDraft.of(
                orderNumber.get(),
                blankToNull(customerRef.get()),
                customerName.get(),
                blankToNull(contractRef.get()),
                blankToNull(siteRef.get()),
                blankToNull(responsibleManager.get()),
                direction.get(),
                currency.get());
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
        showError(OrderUiErrorMapper.text(error, operation));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
