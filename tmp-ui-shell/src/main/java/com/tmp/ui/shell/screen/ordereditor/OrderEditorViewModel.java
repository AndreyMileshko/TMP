package com.tmp.ui.shell.screen.ordereditor;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.api.ui.OrderHeaderDraft;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import com.tmp.ui.shell.order.worklist.DateTimePresentation;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatus;
import com.tmp.ui.shell.order.worklist.OrderOperationalStatusDeriver;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * User-facing order editor. Document Engine stages are orchestrated by {@link OrderDocumentUiService}.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderEditorViewModel {

    public enum Mode {
        CREATE,
        VIEW_EXISTING
    }

    static final String TRANSFER_IMMUTABILITY_HINT =
            "После передачи заказ и его позиции нельзя будет редактировать.";

    private static final String PERM_VIEW = "order.order.view";
    private static final String PERM_CREATE = "order.order.create";
    private static final String PERM_EDIT = "order.order.edit";
    private static final String PERM_APPROVE = "order.order.approve";
    private static final String PERM_CANCEL = "order.order.cancel";

    private final OrderQueryService orderQueryService;
    private final OrderDocumentUiService orderDocuments;
    private final AuthorizationService authorization;
    private final ProductionQueryApi productionQueryApi;

    private final javafx.beans.property.ObjectProperty<Mode> mode =
            new javafx.beans.property.SimpleObjectProperty<>(Mode.CREATE);
    private final StringProperty title = new SimpleStringProperty("Новый заказ");
    private final StringProperty subtitle = new SimpleStringProperty("");
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
    private final BooleanProperty canSave = new SimpleBooleanProperty(false);
    private final BooleanProperty canTransferToWork = new SimpleBooleanProperty(false);
    private final BooleanProperty canCancel = new SimpleBooleanProperty(false);
    private final BooleanProperty orderNumberEditable = new SimpleBooleanProperty(true);
    private final BooleanProperty canOpenItems = new SimpleBooleanProperty(false);

    private OrderId orderId;
    private OrderStatus orderStatus;
    private OrderOperationalStatus operationalStatus = OrderOperationalStatus.EDITING;
    private Runnable onOpenItems = () -> {};
    private Consumer<OrderId> onOrderCreated = id -> {};

    public OrderEditorViewModel(
            OrderQueryService orderQueryService,
            OrderDocumentUiService orderDocuments,
            AuthorizationService authorization,
            ProductionQueryApi productionQueryApi) {
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.orderDocuments = Objects.requireNonNull(orderDocuments, "orderDocuments");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.productionQueryApi = Objects.requireNonNull(productionQueryApi, "productionQueryApi");
    }

    public void setOnOpenItems(Runnable onOpenItems) {
        this.onOpenItems = Objects.requireNonNull(onOpenItems, "onOpenItems");
    }

    /**
     * Invoked after the first successful save from CREATE mode so Shell history can replace the
     * abstract create route with a stable existing-order entry.
     */
    public void setOnOrderCreated(Consumer<OrderId> onOrderCreated) {
        this.onOrderCreated = Objects.requireNonNull(onOrderCreated, "onOrderCreated");
    }

    public void openCreate() {
        clearMessages();
        mode.set(Mode.CREATE);
        title.set("Новый заказ");
        orderId = null;
        orderStatus = null;
        operationalStatus = OrderOperationalStatus.EDITING;
        orderNumber.set("");
        customerRef.set("");
        customerName.set("");
        contractRef.set("");
        siteRef.set("");
        responsibleManager.set("");
        direction.set("PRIVATE");
        currency.set("RUB");
        statusText.set(OrderOperationalStatus.EDITING.caption());
        updateSubtitle();
        refreshActionFlags();
    }

    public void openExisting(OrderId id) {
        Objects.requireNonNull(id, "id");
        clearMessages();
        reloadExisting(id);
    }

    private void reloadExisting(OrderId id) {
        mode.set(Mode.VIEW_EXISTING);
        errorMessage.set("");
        try {
            Optional<OrderDto> loaded = orderQueryService.getOrder(id);
            if (loaded.isEmpty()) {
                showError(OrderUiErrorMapper.NOT_FOUND);
                return;
            }
            applyOrder(loaded.get());
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
        }
    }

    public void save() {
        clearMessages();
        try {
            OrderHeaderDraft draft = currentDraft();
            if (mode.get() == Mode.CREATE) {
                OrderId created = orderDocuments.saveNewOrder(draft);
                showSuccess("Заказ сохранён");
                reloadExisting(created);
                if (orderId != null) {
                    onOrderCreated.accept(orderId);
                }
                return;
            }
            if (orderId == null || orderStatus != OrderStatus.DRAFT) {
                showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
                return;
            }
            OrderId saved = orderDocuments.saveExistingDraft(orderId, draft);
            showSuccess("Заказ сохранён");
            reloadExisting(saved);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.SAVE);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.SAVE);
        }
    }

    public void transferToWork() {
        clearMessages();
        try {
            if (orderId == null
                    || (orderStatus != OrderStatus.DRAFT && orderStatus != OrderStatus.APPROVED)) {
                showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
                return;
            }
            OrderId result = orderDocuments.transferToWork(orderId);
            showSuccess("Заказ передан в работу");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            showMappedError(ex, OrderUiOperation.TRANSFER_TO_WORK);
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.TRANSFER_TO_WORK);
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

    public void openItems() {
        if (orderId == null || !canOpenItems.get()) {
            return;
        }
        onOpenItems.run();
    }

    public String transferConfirmationTitle() {
        String number = orderNumber.get();
        if (number == null || number.isBlank()) {
            return "Передать заказ в работу?";
        }
        return "Передать заказ №" + number + " в работу?";
    }

    public OrderId currentOrderId() {
        return orderId;
    }

    public OrderStatus currentOrderStatus() {
        return orderStatus;
    }

    public OrderOperationalStatus operationalStatus() {
        return operationalStatus;
    }

    public javafx.beans.property.ObjectProperty<Mode> modeProperty() {
        return mode;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty subtitleProperty() {
        return subtitle;
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

    public BooleanProperty canSaveProperty() {
        return canSave;
    }

    public BooleanProperty canTransferToWorkProperty() {
        return canTransferToWork;
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

    private void applyOrder(OrderDto dto) {
        orderId = dto.orderId();
        orderStatus = dto.status();
        title.set("Заказ №" + dto.orderNumber());
        orderNumber.set(dto.orderNumber());
        customerRef.set(nullToEmpty(dto.customerRef()));
        customerName.set(dto.customerName());
        contractRef.set(nullToEmpty(dto.contractRef()));
        siteRef.set(nullToEmpty(dto.siteRef()));
        responsibleManager.set(nullToEmpty(dto.responsibleManager()));
        direction.set(dto.direction() == null || dto.direction().isBlank() ? "PRIVATE" : dto.direction());
        currency.set(dto.currency() == null || dto.currency().isBlank() ? "RUB" : dto.currency());
        operationalStatus = deriveOperationalStatus(dto);
        statusText.set(operationalStatus.caption());
        updateSubtitle();
        refreshActionFlags();
    }

    private OrderOperationalStatus deriveOperationalStatus(OrderDto dto) {
        if (dto.status() == OrderStatus.DRAFT || dto.status() == OrderStatus.APPROVED) {
            return OrderOperationalStatus.EDITING;
        }
        if (dto.status() == OrderStatus.CANCELLED) {
            return OrderOperationalStatus.CANCELLED;
        }
        Optional<OrderProductionListFacts> facts = loadProductionFacts(dto.orderId());
        long orderedQuantity = facts.map(OrderProductionListFacts::orderedQuantity).orElse(0L);
        return OrderOperationalStatusDeriver.derive(dto.status(), orderedQuantity, facts);
    }

    /**
     * Returns Production facts when the Public Query succeeds. AccessDenied and technical failures
     * yield empty — never fake zero manufactured facts.
     */
    private Optional<OrderProductionListFacts> loadProductionFacts(OrderId id) {
        try {
            OrderProductionListFacts facts =
                    productionQueryApi.getOrderProductionListFacts(List.of(id.value())).get(id.value());
            return Optional.ofNullable(facts);
        } catch (AccessDeniedException ex) {
            return Optional.empty();
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.LOAD);
            return Optional.empty();
        }
    }

    private void updateSubtitle() {
        String customer = DateTimePresentation.customerDisplay(customerName.get());
        String status = operationalStatus == null ? "" : operationalStatus.caption();
        if (status.isBlank()) {
            subtitle.set(customer);
            return;
        }
        subtitle.set(customer + " · " + status);
    }

    private void refreshActionFlags() {
        boolean hasView = authorization.hasPermission(PermissionId.of(PERM_VIEW));
        boolean hasCreate = authorization.hasPermission(PermissionId.of(PERM_CREATE));
        boolean hasEdit = authorization.hasPermission(PermissionId.of(PERM_EDIT));
        boolean hasApprove = authorization.hasPermission(PermissionId.of(PERM_APPROVE));
        boolean hasCancel = authorization.hasPermission(PermissionId.of(PERM_CANCEL));
        boolean hasItemView = authorization.hasPermission(PermissionId.of("order.item.view"));

        if (mode.get() == Mode.CREATE) {
            fieldsEditable.set(hasCreate);
            orderNumberEditable.set(hasCreate);
            canSave.set(hasCreate);
            canTransferToWork.set(false);
            canCancel.set(false);
            canOpenItems.set(false);
            return;
        }

        boolean draft = orderStatus == OrderStatus.DRAFT;
        boolean approved = orderStatus == OrderStatus.APPROVED;
        boolean transferred =
                orderStatus == OrderStatus.ACTIVE || orderStatus == OrderStatus.CANCELLED;
        fieldsEditable.set(hasView && draft && hasEdit);
        orderNumberEditable.set(false);
        canSave.set(draft && hasEdit);
        canTransferToWork.set((draft || approved) && hasApprove);
        canCancel.set(draft && hasCancel);
        canOpenItems.set(orderId != null && hasItemView);
        if (transferred) {
            fieldsEditable.set(false);
            canSave.set(false);
            canTransferToWork.set(false);
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
