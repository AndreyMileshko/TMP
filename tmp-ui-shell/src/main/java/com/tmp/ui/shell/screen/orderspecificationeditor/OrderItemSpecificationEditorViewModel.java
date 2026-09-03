package com.tmp.ui.shell.screen.orderspecificationeditor;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationEditorQueryService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.order.api.ui.OrderItemSpecificationLineView;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.order.ProductQuantityUiValidation;
import com.tmp.ui.shell.order.error.OrderUiErrorMapper;
import com.tmp.ui.shell.order.error.OrderUiOperation;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Document-driven Item Specification editor. Line mutations persist immediately through {@code
 * ORDER_ITEM_REVISION_UPDATE} (save + post); the user does not see technical draft/post buttons.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderItemSpecificationEditorViewModel {

    private final OrderItemDocumentUiService itemDocuments;
    private final OrderItemSpecificationEditorQueryService specificationQuery;
    private final AuthorizationService authorization;
    private final OrderItemEditorQueryService itemEditorQuery;
    private final OrderQueryService orderQueryService;

    private final StringProperty title = new SimpleStringProperty("Спецификация");
    private final StringProperty orderedQuantityText = new SimpleStringProperty("");
    private final StringProperty orderedQuantity = new SimpleStringProperty("1");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty successMessage = new SimpleStringProperty("");
    private final StringProperty warningMessage = new SimpleStringProperty("");
    private final BooleanProperty editable = new SimpleBooleanProperty(false);
    private final BooleanProperty canAddLine = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdateLine = new SimpleBooleanProperty(false);
    private final BooleanProperty canDeleteLine = new SimpleBooleanProperty(false);
    private final BooleanProperty canMoveUp = new SimpleBooleanProperty(false);
    private final BooleanProperty canMoveDown = new SimpleBooleanProperty(false);
    private final IntegerProperty selectedIndex = new SimpleIntegerProperty(-1);
    private final ObjectProperty<SpecificationLineRow> selectedLine = new SimpleObjectProperty<>();
    private final ObservableList<SpecificationLineRow> lines = FXCollections.observableArrayList();

    private OrderItemId orderItemId;
    private RevisionNumber revisionNumber;
    private RevisionStatus revisionStatus;
    private boolean immutable;
    private UUID documentId;
    private long payloadRevision;
    private Consumer<OrderItemId> onBackToItem = id -> {
    };

    public OrderItemSpecificationEditorViewModel(
            OrderItemDocumentUiService itemDocuments,
            OrderItemSpecificationEditorQueryService specificationQuery,
            AuthorizationService authorization) {
        this(itemDocuments, specificationQuery, authorization, null, null);
    }

    public OrderItemSpecificationEditorViewModel(
            OrderItemDocumentUiService itemDocuments,
            OrderItemSpecificationEditorQueryService specificationQuery,
            AuthorizationService authorization,
            OrderItemEditorQueryService itemEditorQuery,
            OrderQueryService orderQueryService) {
        this.itemDocuments = Objects.requireNonNull(itemDocuments, "itemDocuments");
        this.specificationQuery =
                Objects.requireNonNull(specificationQuery, "specificationQuery");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.itemEditorQuery = itemEditorQuery;
        this.orderQueryService = orderQueryService;
        selectedIndex.addListener((obs, oldValue, newValue) -> onSelectionChanged());
    }

    public void setOnBackToItem(Consumer<OrderItemId> onBackToItem) {
        this.onBackToItem = Objects.requireNonNull(onBackToItem, "onBackToItem");
    }

    public void open(OrderItemId orderItemId, RevisionNumber revisionNumber) {
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(revisionNumber, "revisionNumber");
        clearMessages();
        this.orderItemId = orderItemId;
        this.revisionNumber = revisionNumber;
        documentId = null;
        payloadRevision = 0L;
        reloadSnapshot(false);
    }

    public void selectLine(int index) {
        if (index < -1 || index >= lines.size()) {
            selectedIndex.set(-1);
            return;
        }
        selectedIndex.set(index);
    }

    public void addLine(SpecificationLineRow row) {
        beginOperation();
        if (!ensureEditable()) {
            return;
        }
        try {
            Objects.requireNonNull(row, "row");
            row.requireValid();
            lines.add(row);
            selectedIndex.set(lines.size() - 1);
            persistWorkingCopy("Материал добавлен");
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.VALIDATE);
            reloadSnapshot(false);
        }
    }

    public void updateSelectedLine(SpecificationLineRow row) {
        beginOperation();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size()) {
            showError("Выберите строку для изменения");
            return;
        }
        try {
            Objects.requireNonNull(row, "row");
            row.requireValid();
            lines.set(index, row);
            selectedLine.set(row);
            persistWorkingCopy("Материал изменён");
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.VALIDATE);
            reloadSnapshot(false);
        }
    }

    public void deleteSelectedLine() {
        beginOperation();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size()) {
            showError("Выберите строку для удаления");
            return;
        }
        try {
            lines.remove(index);
            if (lines.isEmpty()) {
                selectedIndex.set(-1);
            } else {
                selectedIndex.set(Math.min(index, lines.size() - 1));
            }
            persistWorkingCopy("Строка удалена");
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.SAVE_DRAFT);
            reloadSnapshot(false);
        }
    }

    public void moveSelectedUp() {
        beginOperation();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index <= 0 || index >= lines.size()) {
            return;
        }
        try {
            SpecificationLineRow current = lines.remove(index);
            lines.add(index - 1, current);
            selectedIndex.set(index - 1);
            persistWorkingCopy("Строка перемещена");
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.SAVE_DRAFT);
            reloadSnapshot(false);
        }
    }

    public void moveSelectedDown() {
        beginOperation();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size() - 1) {
            return;
        }
        try {
            SpecificationLineRow current = lines.remove(index);
            lines.add(index + 1, current);
            selectedIndex.set(index + 1);
            persistWorkingCopy("Строка перемещена");
        } catch (RuntimeException ex) {
            showMappedError(ex, OrderUiOperation.SAVE_DRAFT);
            reloadSnapshot(false);
        }
    }

    public void backToItem() {
        if (orderItemId != null) {
            onBackToItem.accept(orderItemId);
        }
    }

    public ObservableList<SpecificationLineRow> lines() {
        return lines;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty orderedQuantityTextProperty() {
        return orderedQuantityText;
    }

    public StringProperty orderedQuantityProperty() {
        return orderedQuantity;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty successMessageProperty() {
        return successMessage;
    }

    public StringProperty warningMessageProperty() {
        return warningMessage;
    }

    public BooleanProperty editableProperty() {
        return editable;
    }

    public BooleanProperty canAddLineProperty() {
        return canAddLine;
    }

    public BooleanProperty canUpdateLineProperty() {
        return canUpdateLine;
    }

    public BooleanProperty canDeleteLineProperty() {
        return canDeleteLine;
    }

    public BooleanProperty canMoveUpProperty() {
        return canMoveUp;
    }

    public BooleanProperty canMoveDownProperty() {
        return canMoveDown;
    }

    public IntegerProperty selectedIndexProperty() {
        return selectedIndex;
    }

    public ObjectProperty<SpecificationLineRow> selectedLineProperty() {
        return selectedLine;
    }

    OrderItemId orderItemIdForTest() {
        return orderItemId;
    }

    RevisionNumber revisionNumberForTest() {
        return revisionNumber;
    }

    UUID documentIdForTest() {
        return documentId;
    }

    long payloadRevisionForTest() {
        return payloadRevision;
    }

    boolean immutableForTest() {
        return immutable;
    }

    private void persistWorkingCopy(String successText) {
        String quantity =
                ProductQuantityUiValidation.requireValidNormalizedProductQuantity(
                        orderedQuantity.get());
        orderedQuantity.set(quantity);
        List<OrderItemSpecificationLineDraft> drafts = toDrafts();
        if (documentId == null) {
            documentId =
                    itemDocuments.beginRevisionUpdate(
                            "ORDER_ITEM_REVISION_UPDATE " + orderItemId.value(), orderItemId);
            payloadRevision = 0L;
        }
        payloadRevision =
                itemDocuments.saveRevisionUpdateDraft(
                        documentId,
                        orderItemId,
                        revisionNumber,
                        quantity,
                        drafts,
                        payloadRevision);
        itemDocuments.postDocument(documentId);
        documentId = null;
        payloadRevision = 0L;
        showSuccess(successText);
        reloadSnapshot(true);
    }

    private void reloadSnapshot(boolean afterSuccessfulPost) {
        try {
            Optional<OrderItemSpecificationEditorSnapshot> loaded =
                    specificationQuery.getSpecificationSnapshot(orderItemId, revisionNumber);
            if (loaded.isEmpty()) {
                if (afterSuccessfulPost) {
                    showWarning(OrderUiErrorMapper.RELOAD_FAILED_AFTER_POST);
                } else {
                    showMappedError(
                            new IllegalArgumentException("not found"), OrderUiOperation.LOAD);
                }
                refreshActionFlags();
                return;
            }
            applySnapshot(loaded.get());
        } catch (RuntimeException ex) {
            if (afterSuccessfulPost) {
                showWarning(OrderUiErrorMapper.RELOAD_FAILED_AFTER_POST);
            } else {
                showMappedError(ex, OrderUiOperation.LOAD);
                editable.set(false);
            }
            refreshActionFlags();
        }
    }

    private void applySnapshot(OrderItemSpecificationEditorSnapshot snapshot) {
        orderItemId = snapshot.orderItemId();
        revisionNumber = snapshot.revisionNumber();
        revisionStatus = snapshot.revisionStatus();
        immutable = snapshot.immutable();
        String productCode = resolveProductCode(orderItemId);
        title.set(
                productCode == null || productCode.isBlank()
                        ? "Спецификация позиции"
                        : "Спецификация позиции " + productCode);
        String quantityDisplay =
                ProductQuantityUiValidation.formatForDisplay(snapshot.orderedQuantity());
        orderedQuantity.set(quantityDisplay);
        orderedQuantityText.set("Количество изделий: " + quantityDisplay);
        lines.setAll(toRows(snapshot.lines()));
        if (lines.isEmpty()) {
            selectedIndex.set(-1);
        } else if (selectedIndex.get() < 0 || selectedIndex.get() >= lines.size()) {
            selectedIndex.set(0);
        }
        refreshActionFlags();
    }

    private String resolveProductCode(OrderItemId id) {
        if (itemEditorQuery != null) {
            try {
                Optional<OrderItemEditorSnapshot> snapshot = itemEditorQuery.getEditorSnapshot(id);
                if (snapshot.isPresent()) {
                    return snapshot.get().productCode();
                }
            } catch (RuntimeException ignored) {
                // fall through
            }
        }
        if (orderQueryService != null) {
            try {
                return orderQueryService
                        .getOrderItem(id)
                        .map(item -> item.productCode())
                        .orElse("");
            } catch (RuntimeException ignored) {
                return "";
            }
        }
        return "";
    }

    private List<SpecificationLineRow> toRows(List<OrderItemSpecificationLineView> views) {
        List<SpecificationLineRow> rows = new ArrayList<>(views.size());
        for (OrderItemSpecificationLineView view : views) {
            rows.add(
                    new SpecificationLineRow(
                            view.materialCode(),
                            view.materialName(),
                            nullToEmpty(view.color()),
                            DecimalUiFormat.formatOptional(view.lengthMm()),
                            DecimalUiFormat.format(view.lineQuantity()),
                            view.unitOfMeasure()));
        }
        return rows;
    }

    private List<OrderItemSpecificationLineDraft> toDrafts() {
        List<OrderItemSpecificationLineDraft> drafts = new ArrayList<>(lines.size());
        for (SpecificationLineRow row : lines) {
            row.requireValid();
            drafts.add(
                    OrderItemSpecificationLineDraft.of(
                            row.materialCode(),
                            row.materialName(),
                            row.normalizedColor(),
                            row.parseLengthMm(),
                            row.parseLineQuantity(),
                            row.unitOfMeasure()));
        }
        return drafts;
    }

    private void onSelectionChanged() {
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size()) {
            selectedLine.set(null);
            refreshActionFlags();
            return;
        }
        selectedLine.set(lines.get(index));
        refreshActionFlags();
    }

    private boolean ensureEditable() {
        boolean hasView =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION));
        boolean hasEdit =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_REVISION_EDIT_PERMISSION));
        if (!hasView || !hasEdit) {
            showError(OrderUiErrorMapper.ACCESS_DENIED);
            return false;
        }
        if (immutable || revisionStatus == RevisionStatus.ACTIVE) {
            showError(OrderUiErrorMapper.APPROVED_SPEC_READ_ONLY);
            return false;
        }
        if (!editable.get()) {
            showError(OrderUiErrorMapper.FORBIDDEN_TRANSITION);
            return false;
        }
        return true;
    }

    private void refreshActionFlags() {
        boolean hasView =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_SPECIFICATION_VIEW_PERMISSION));
        boolean hasEdit =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_REVISION_EDIT_PERMISSION));
        boolean draftEditable =
                hasView
                        && hasEdit
                        && !immutable
                        && revisionStatus == RevisionStatus.DRAFT
                        && orderItemId != null
                        && revisionNumber != null;
        editable.set(draftEditable);
        int index = selectedIndex.get();
        boolean hasSelection = draftEditable && index >= 0 && index < lines.size();
        canAddLine.set(draftEditable);
        canUpdateLine.set(hasSelection);
        canDeleteLine.set(hasSelection);
        canMoveUp.set(hasSelection && index > 0);
        canMoveDown.set(hasSelection && index < lines.size() - 1);
        if (!hasView) {
            canAddLine.set(false);
            canUpdateLine.set(false);
            canDeleteLine.set(false);
            canMoveUp.set(false);
            canMoveDown.set(false);
            editable.set(false);
        }
    }

    private void beginOperation() {
        errorMessage.set("");
        warningMessage.set("");
        successMessage.set("");
    }

    private void showSuccess(String message) {
        errorMessage.set("");
        warningMessage.set("");
        successMessage.set(message);
        refreshActionFlags();
    }

    private void showWarning(String message) {
        errorMessage.set("");
        warningMessage.set(message);
    }

    private void showError(String message) {
        successMessage.set("");
        warningMessage.set("");
        errorMessage.set(message);
        refreshActionFlags();
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

    private void clearMessages() {
        errorMessage.set("");
        successMessage.set("");
        warningMessage.set("");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
