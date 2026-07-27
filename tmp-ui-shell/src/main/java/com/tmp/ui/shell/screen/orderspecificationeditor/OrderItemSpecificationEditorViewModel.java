package com.tmp.ui.shell.screen.orderspecificationeditor;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorQueryService;
import com.tmp.order.api.ui.OrderItemSpecificationEditorSnapshot;
import com.tmp.order.api.ui.OrderItemSpecificationLineDraft;
import com.tmp.order.api.ui.OrderItemSpecificationLineView;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
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
 * Document-driven Item Specification editor. Draft lines are edited as a local working copy and
 * persisted only through {@code ORDER_ITEM_REVISION_UPDATE}.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class OrderItemSpecificationEditorViewModel {

    private final OrderItemDocumentUiService itemDocuments;
    private final OrderItemSpecificationEditorQueryService specificationQuery;
    private final AuthorizationService authorization;

    private final StringProperty title = new SimpleStringProperty("Спецификация");
    private final StringProperty revisionNumberText = new SimpleStringProperty("");
    private final StringProperty revisionStatusText = new SimpleStringProperty("");
    private final StringProperty orderedQuantity = new SimpleStringProperty("1");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty successMessage = new SimpleStringProperty("");
    private final StringProperty editMaterialCode = new SimpleStringProperty("");
    private final StringProperty editMaterialName = new SimpleStringProperty("");
    private final StringProperty editQuantity = new SimpleStringProperty("");
    private final StringProperty editUnitOfMeasure = new SimpleStringProperty("");
    private final StringProperty editConsumptionNorm = new SimpleStringProperty("0");
    private final BooleanProperty editable = new SimpleBooleanProperty(false);
    private final BooleanProperty canAddLine = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdateLine = new SimpleBooleanProperty(false);
    private final BooleanProperty canDeleteLine = new SimpleBooleanProperty(false);
    private final BooleanProperty canMoveUp = new SimpleBooleanProperty(false);
    private final BooleanProperty canMoveDown = new SimpleBooleanProperty(false);
    private final BooleanProperty canClearLines = new SimpleBooleanProperty(false);
    private final BooleanProperty canSaveDraft = new SimpleBooleanProperty(false);
    private final BooleanProperty canPost = new SimpleBooleanProperty(false);
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
        this.itemDocuments = Objects.requireNonNull(itemDocuments, "itemDocuments");
        this.specificationQuery =
                Objects.requireNonNull(specificationQuery, "specificationQuery");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
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
        reloadSnapshot();
    }

    public void selectLine(int index) {
        if (index < -1 || index >= lines.size()) {
            selectedIndex.set(-1);
            return;
        }
        selectedIndex.set(index);
    }

    public void addLine() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        try {
            SpecificationLineRow row = rowFromEditFields();
            row.requireValid();
            lines.add(row);
            selectedIndex.set(lines.size() - 1);
            clearEditFields();
            refreshActionFlags();
            successMessage.set("Строка добавлена");
        } catch (RuntimeException ex) {
            errorMessage.set(ex.getMessage() == null ? "Ошибка валидации строки" : ex.getMessage());
        }
    }

    public void updateSelectedLine() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size()) {
            errorMessage.set("Выберите строку для изменения");
            return;
        }
        try {
            SpecificationLineRow row = rowFromEditFields();
            row.requireValid();
            lines.set(index, row);
            selectedLine.set(row);
            refreshActionFlags();
            successMessage.set("Строка изменена");
        } catch (RuntimeException ex) {
            errorMessage.set(ex.getMessage() == null ? "Ошибка валидации строки" : ex.getMessage());
        }
    }

    public void deleteSelectedLine() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size()) {
            errorMessage.set("Выберите строку для удаления");
            return;
        }
        lines.remove(index);
        if (lines.isEmpty()) {
            selectedIndex.set(-1);
            clearEditFields();
        } else {
            selectedIndex.set(Math.min(index, lines.size() - 1));
        }
        refreshActionFlags();
        successMessage.set("Строка удалена");
    }

    public void moveSelectedUp() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index <= 0 || index >= lines.size()) {
            return;
        }
        SpecificationLineRow current = lines.remove(index);
        lines.add(index - 1, current);
        selectedIndex.set(index - 1);
        refreshActionFlags();
    }

    public void moveSelectedDown() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size() - 1) {
            return;
        }
        SpecificationLineRow current = lines.remove(index);
        lines.add(index + 1, current);
        selectedIndex.set(index + 1);
        refreshActionFlags();
    }

    public void clearLines() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        lines.clear();
        selectedIndex.set(-1);
        clearEditFields();
        refreshActionFlags();
        successMessage.set("Список строк очищен");
    }

    public void saveDraft() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        try {
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
                            orderedQuantity.get(),
                            drafts,
                            payloadRevision);
            successMessage.set("Черновик спецификации сохранён");
            refreshActionFlags();
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Ошибка сохранения спецификации" : ex.getMessage());
        }
    }

    public void postDocument() {
        clearMessages();
        if (!ensureEditable()) {
            return;
        }
        try {
            if (documentId == null) {
                errorMessage.set("Сначала сохраните черновик спецификации");
                return;
            }
            itemDocuments.postDocument(documentId);
            documentId = null;
            payloadRevision = 0L;
            successMessage.set("Спецификация обновлена");
            reloadSnapshot();
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Отказ проведения документа" : ex.getMessage());
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

    public StringProperty revisionNumberTextProperty() {
        return revisionNumberText;
    }

    public StringProperty revisionStatusTextProperty() {
        return revisionStatusText;
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

    public StringProperty editMaterialCodeProperty() {
        return editMaterialCode;
    }

    public StringProperty editMaterialNameProperty() {
        return editMaterialName;
    }

    public StringProperty editQuantityProperty() {
        return editQuantity;
    }

    public StringProperty editUnitOfMeasureProperty() {
        return editUnitOfMeasure;
    }

    public StringProperty editConsumptionNormProperty() {
        return editConsumptionNorm;
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

    public BooleanProperty canClearLinesProperty() {
        return canClearLines;
    }

    public BooleanProperty canSaveDraftProperty() {
        return canSaveDraft;
    }

    public BooleanProperty canPostProperty() {
        return canPost;
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

    private void reloadSnapshot() {
        try {
            Optional<OrderItemSpecificationEditorSnapshot> loaded =
                    specificationQuery.getSpecificationSnapshot(orderItemId, revisionNumber);
            if (loaded.isEmpty()) {
                errorMessage.set("Спецификация не найдена");
                lines.clear();
                revisionNumberText.set("");
                revisionStatusText.set("");
                editable.set(false);
                refreshActionFlags();
                return;
            }
            applySnapshot(loaded.get());
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
            editable.set(false);
            refreshActionFlags();
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Ошибка загрузки спецификации" : ex.getMessage());
            editable.set(false);
            refreshActionFlags();
        }
    }

    private void applySnapshot(OrderItemSpecificationEditorSnapshot snapshot) {
        orderItemId = snapshot.orderItemId();
        revisionNumber = snapshot.revisionNumber();
        revisionStatus = snapshot.revisionStatus();
        immutable = snapshot.immutable();
        title.set("Спецификация редакции " + revisionNumber.value());
        revisionNumberText.set(Integer.toString(revisionNumber.value()));
        revisionStatusText.set(revisionStatus.name());
        orderedQuantity.set(snapshot.orderedQuantity().toPlainString());
        lines.setAll(toRows(snapshot.lines()));
        selectedIndex.set(lines.isEmpty() ? -1 : 0);
        refreshActionFlags();
    }

    private List<SpecificationLineRow> toRows(List<OrderItemSpecificationLineView> views) {
        List<SpecificationLineRow> rows = new ArrayList<>(views.size());
        for (OrderItemSpecificationLineView view : views) {
            rows.add(
                    new SpecificationLineRow(
                            view.materialCode(),
                            view.materialName(),
                            view.quantity().toPlainString(),
                            view.unitOfMeasure(),
                            view.consumptionNorm().toPlainString()));
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
                            row.parseQuantity(),
                            row.unitOfMeasure(),
                            row.parseConsumptionNorm()));
        }
        return drafts;
    }

    private SpecificationLineRow rowFromEditFields() {
        return new SpecificationLineRow(
                editMaterialCode.get(),
                editMaterialName.get(),
                editQuantity.get(),
                editUnitOfMeasure.get(),
                editConsumptionNorm.get());
    }

    private void onSelectionChanged() {
        int index = selectedIndex.get();
        if (index < 0 || index >= lines.size()) {
            selectedLine.set(null);
            refreshActionFlags();
            return;
        }
        SpecificationLineRow row = lines.get(index);
        selectedLine.set(row);
        editMaterialCode.set(row.materialCode());
        editMaterialName.set(row.materialName());
        editQuantity.set(row.quantity());
        editUnitOfMeasure.set(row.unitOfMeasure());
        editConsumptionNorm.set(row.consumptionNorm());
        refreshActionFlags();
    }

    private void clearEditFields() {
        editMaterialCode.set("");
        editMaterialName.set("");
        editQuantity.set("");
        editUnitOfMeasure.set("");
        editConsumptionNorm.set("0");
    }

    private boolean ensureEditable() {
        if (immutable || !editable.get()) {
            errorMessage.set("Утверждённая спецификация доступна только для просмотра");
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
        canClearLines.set(draftEditable && !lines.isEmpty());
        canSaveDraft.set(draftEditable);
        canPost.set(draftEditable && documentId != null);
        if (!hasView) {
            canAddLine.set(false);
            canUpdateLine.set(false);
            canDeleteLine.set(false);
            canMoveUp.set(false);
            canMoveDown.set(false);
            canClearLines.set(false);
            canSaveDraft.set(false);
            canPost.set(false);
            editable.set(false);
        }
    }

    private void clearMessages() {
        errorMessage.set("");
        successMessage.set("");
    }
}
