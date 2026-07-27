package com.tmp.ui.shell.screen.orderitemeditor;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.ui.OrderItemCommercialDraft;
import com.tmp.order.api.ui.OrderItemDocumentUiService;
import com.tmp.order.api.ui.OrderItemEditorQueryService;
import com.tmp.order.api.ui.OrderItemEditorSnapshot;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
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
 * Document-driven order item / revision editor. Mutating actions go only through {@link
 * OrderItemDocumentUiService}; draft revision is loaded only through {@link
 * OrderItemEditorQueryService}.
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

    private final ObjectProperty<Mode> mode = new SimpleObjectProperty<>(Mode.CREATE);
    private final StringProperty title = new SimpleStringProperty("Позиция");
    private final StringProperty statusText = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty successMessage = new SimpleStringProperty("");
    private final StringProperty productCode = new SimpleStringProperty("");
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty comments = new SimpleStringProperty("");
    private final StringProperty orderedQuantity = new SimpleStringProperty("1");
    private final StringProperty activeRevisionText = new SimpleStringProperty("");
    private final StringProperty draftRevisionText = new SimpleStringProperty("");
    private final StringProperty draftSpecLineCountText = new SimpleStringProperty("0");
    private final StringProperty copyFromRevision = new SimpleStringProperty("");
    private final BooleanProperty commercialEditable = new SimpleBooleanProperty(false);
    private final BooleanProperty quantityEditable = new SimpleBooleanProperty(false);
    private final BooleanProperty canSaveCommercialDraft = new SimpleBooleanProperty(false);
    private final BooleanProperty canPostCommercial = new SimpleBooleanProperty(false);
    private final BooleanProperty canCancelItem = new SimpleBooleanProperty(false);
    private final BooleanProperty canCreateRevision = new SimpleBooleanProperty(false);
    private final BooleanProperty canSaveRevisionDraft = new SimpleBooleanProperty(false);
    private final BooleanProperty canPostRevisionUpdate = new SimpleBooleanProperty(false);
    private final BooleanProperty canApproveRevision = new SimpleBooleanProperty(false);

    private OrderId orderId;
    private OrderItemId orderItemId;
    private OrderItemStatus itemStatus;
    private RevisionNumber draftRevisionNumber;
    private RevisionNumber activeRevisionNumber;
    private UUID documentId;
    private long payloadRevision;
    private DocumentKind pendingKind = DocumentKind.NONE;
    private Runnable onBackToItemList = () -> {
    };
    private Consumer<OrderItemId> onItemOpened = id -> {
    };

    private enum DocumentKind {
        NONE,
        ITEM_CREATE,
        ITEM_UPDATE,
        REVISION_CREATE,
        REVISION_UPDATE
    }

    public OrderItemEditorViewModel(
            OrderItemDocumentUiService itemDocuments,
            OrderItemEditorQueryService editorQuery,
            AuthorizationService authorization) {
        this.itemDocuments = Objects.requireNonNull(itemDocuments, "itemDocuments");
        this.editorQuery = Objects.requireNonNull(editorQuery, "editorQuery");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
    }

    public void setOnBackToItemList(Runnable onBackToItemList) {
        this.onBackToItemList = Objects.requireNonNull(onBackToItemList, "onBackToItemList");
    }

    public void setOnItemOpened(Consumer<OrderItemId> onItemOpened) {
        this.onItemOpened = Objects.requireNonNull(onItemOpened, "onItemOpened");
    }

    public void openCreate(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        clearMessages();
        mode.set(Mode.CREATE);
        title.set("Новая позиция");
        this.orderId = orderId;
        orderItemId = null;
        itemStatus = null;
        draftRevisionNumber = null;
        activeRevisionNumber = null;
        documentId = null;
        payloadRevision = 0L;
        pendingKind = DocumentKind.NONE;
        productCode.set("");
        name.set("");
        comments.set("");
        orderedQuantity.set("1");
        activeRevisionText.set("");
        draftRevisionText.set("1 (черновик после создания)");
        draftSpecLineCountText.set("0");
        copyFromRevision.set("");
        statusText.set("CREATE");
        refreshActionFlags();
    }

    public void openExisting(OrderItemId id) {
        Objects.requireNonNull(id, "id");
        clearMessages();
        reloadExisting(id);
    }

    private void reloadExisting(OrderItemId id) {
        mode.set(Mode.VIEW_EXISTING);
        documentId = null;
        payloadRevision = 0L;
        pendingKind = DocumentKind.NONE;
        errorMessage.set("");
        try {
            Optional<OrderItemEditorSnapshot> loaded = editorQuery.getEditorSnapshot(id);
            if (loaded.isEmpty()) {
                errorMessage.set("Позиция не найдена");
                return;
            }
            applySnapshot(loaded.get());
            onItemOpened.accept(id);
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Ошибка загрузки позиции" : ex.getMessage());
        }
    }

    public void saveCommercialDraft() {
        clearMessages();
        try {
            OrderItemCommercialDraft draft = currentCommercialDraft();
            if (mode.get() == Mode.CREATE) {
                if (documentId == null) {
                    documentId =
                            itemDocuments.beginItemCreate(
                                    "ORDER_ITEM_CREATE " + draft.productCode(), orderId);
                    payloadRevision = 0L;
                    pendingKind = DocumentKind.ITEM_CREATE;
                }
                payloadRevision =
                        itemDocuments.saveItemCreateDraft(
                                documentId,
                                orderId,
                                Optional.ofNullable(orderItemId),
                                draft,
                                orderedQuantity.get(),
                                payloadRevision);
                successMessage.set("Черновик документа позиции сохранён");
            } else {
                if (orderItemId == null || itemStatus != OrderItemStatus.DRAFT) {
                    errorMessage.set("Коммерческое изменение доступно только для черновика позиции");
                    return;
                }
                if (documentId == null || pendingKind != DocumentKind.ITEM_UPDATE) {
                    documentId =
                            itemDocuments.beginItemUpdate(
                                    "ORDER_ITEM_UPDATE " + orderItemId.value(), orderItemId);
                    payloadRevision = 0L;
                    pendingKind = DocumentKind.ITEM_UPDATE;
                }
                payloadRevision =
                        itemDocuments.saveItemUpdateDraft(
                                documentId, orderItemId, draft, payloadRevision);
                successMessage.set("Черновик изменения позиции сохранён");
            }
            refreshActionFlags();
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Ошибка сохранения черновика" : ex.getMessage());
        }
    }

    public void postCommercialDocument() {
        clearMessages();
        try {
            if (documentId == null
                    || (pendingKind != DocumentKind.ITEM_CREATE
                            && pendingKind != DocumentKind.ITEM_UPDATE)) {
                errorMessage.set("Сначала сохраните коммерческий черновик документа");
                return;
            }
            OrderItemId result = itemDocuments.postDocument(documentId);
            documentId = null;
            payloadRevision = 0L;
            pendingKind = DocumentKind.NONE;
            successMessage.set("Документ позиции проведён");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Отказ проведения документа" : ex.getMessage());
        }
    }

    public void cancelItem() {
        clearMessages();
        try {
            if (orderItemId == null || itemStatus != OrderItemStatus.DRAFT) {
                errorMessage.set("Отмена доступна только для черновика позиции");
                return;
            }
            UUID cancelDoc =
                    itemDocuments.beginItemCancel(
                            "ORDER_ITEM_CANCEL " + orderItemId.value(), orderItemId);
            OrderItemId result = itemDocuments.postDocument(cancelDoc);
            successMessage.set("Позиция отменена");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(ex.getMessage() == null ? "Ошибка отмены позиции" : ex.getMessage());
        }
    }

    public void createNextRevision() {
        clearMessages();
        try {
            if (orderItemId == null || itemStatus != OrderItemStatus.ACTIVE) {
                errorMessage.set("Новая редакция доступна только для активной позиции");
                return;
            }
            if (draftRevisionNumber != null) {
                errorMessage.set("У позиции уже есть черновая редакция");
                return;
            }
            if (activeRevisionNumber == null) {
                errorMessage.set("Нет активной редакции для создания следующей");
                return;
            }
            RevisionNumber next = activeRevisionNumber.next();
            Optional<RevisionNumber> copyFrom = parseCopyFrom();
            documentId =
                    itemDocuments.beginRevisionCreate(
                            "ORDER_ITEM_REVISION_CREATE " + orderItemId.value(), orderItemId);
            payloadRevision = 0L;
            pendingKind = DocumentKind.REVISION_CREATE;
            payloadRevision =
                    itemDocuments.saveRevisionCreateDraft(
                            documentId, orderItemId, next, copyFrom, payloadRevision);
            OrderItemId result = itemDocuments.postDocument(documentId);
            documentId = null;
            payloadRevision = 0L;
            pendingKind = DocumentKind.NONE;
            successMessage.set("Черновая редакция " + next.value() + " создана");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Ошибка создания редакции" : ex.getMessage());
        }
    }

    public void saveRevisionQuantityDraft() {
        clearMessages();
        try {
            if (orderItemId == null || draftRevisionNumber == null) {
                errorMessage.set("Нет черновой редакции для изменения количества");
                return;
            }
            if (documentId == null || pendingKind != DocumentKind.REVISION_UPDATE) {
                documentId =
                        itemDocuments.beginRevisionUpdate(
                                "ORDER_ITEM_REVISION_UPDATE " + orderItemId.value(), orderItemId);
                payloadRevision = 0L;
                pendingKind = DocumentKind.REVISION_UPDATE;
            }
            payloadRevision =
                    itemDocuments.saveRevisionUpdateDraft(
                            documentId,
                            orderItemId,
                            draftRevisionNumber,
                            orderedQuantity.get(),
                            payloadRevision);
            successMessage.set("Черновик изменения количества сохранён");
            refreshActionFlags();
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Ошибка сохранения количества" : ex.getMessage());
        }
    }

    public void postRevisionUpdate() {
        clearMessages();
        try {
            if (documentId == null || pendingKind != DocumentKind.REVISION_UPDATE) {
                errorMessage.set("Сначала сохраните черновик изменения количества");
                return;
            }
            OrderItemId result = itemDocuments.postDocument(documentId);
            documentId = null;
            payloadRevision = 0L;
            pendingKind = DocumentKind.NONE;
            successMessage.set("Количество черновой редакции обновлено");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Отказ проведения изменения редакции" : ex.getMessage());
        }
    }

    public void approveDraftRevision() {
        clearMessages();
        try {
            if (orderItemId == null || draftRevisionNumber == null) {
                errorMessage.set("Нет черновой редакции для утверждения");
                return;
            }
            UUID approveDoc =
                    itemDocuments.beginRevisionApprove(
                            "ORDER_ITEM_REVISION_APPROVE " + orderItemId.value(), orderItemId);
            OrderItemId result = itemDocuments.postDocument(approveDoc);
            successMessage.set("Редакция утверждена");
            reloadExisting(result);
        } catch (AccessDeniedException ex) {
            errorMessage.set(ex.getMessage() == null ? "Доступ запрещён" : ex.getMessage());
        } catch (RuntimeException ex) {
            errorMessage.set(
                    ex.getMessage() == null ? "Ошибка утверждения редакции" : ex.getMessage());
        }
    }

    public void backToItemList() {
        onBackToItemList.run();
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

    public StringProperty productCodeProperty() {
        return productCode;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty commentsProperty() {
        return comments;
    }

    public StringProperty orderedQuantityProperty() {
        return orderedQuantity;
    }

    public StringProperty activeRevisionTextProperty() {
        return activeRevisionText;
    }

    public StringProperty draftRevisionTextProperty() {
        return draftRevisionText;
    }

    public StringProperty draftSpecLineCountTextProperty() {
        return draftSpecLineCountText;
    }

    public StringProperty copyFromRevisionProperty() {
        return copyFromRevision;
    }

    public BooleanProperty commercialEditableProperty() {
        return commercialEditable;
    }

    public BooleanProperty quantityEditableProperty() {
        return quantityEditable;
    }

    public BooleanProperty canSaveCommercialDraftProperty() {
        return canSaveCommercialDraft;
    }

    public BooleanProperty canPostCommercialProperty() {
        return canPostCommercial;
    }

    public BooleanProperty canCancelItemProperty() {
        return canCancelItem;
    }

    public BooleanProperty canCreateRevisionProperty() {
        return canCreateRevision;
    }

    public BooleanProperty canSaveRevisionDraftProperty() {
        return canSaveRevisionDraft;
    }

    public BooleanProperty canPostRevisionUpdateProperty() {
        return canPostRevisionUpdate;
    }

    public BooleanProperty canApproveRevisionProperty() {
        return canApproveRevision;
    }

    OrderItemId orderItemIdForTest() {
        return orderItemId;
    }

    UUID documentIdForTest() {
        return documentId;
    }

    private void applySnapshot(OrderItemEditorSnapshot snapshot) {
        orderItemId = snapshot.orderItemId();
        orderId = snapshot.orderId();
        itemStatus = snapshot.status();
        activeRevisionNumber = snapshot.activeRevisionNumber().orElse(null);
        draftRevisionNumber = snapshot.draftRevisionNumber().orElse(null);
        title.set("Позиция " + snapshot.productCode());
        statusText.set(snapshot.status().name());
        productCode.set(snapshot.productCode());
        name.set(snapshot.name());
        comments.set(nullToEmpty(snapshot.comments()));
        orderedQuantity.set(snapshot.orderedQuantity().toPlainString());
        activeRevisionText.set(
                snapshot.activeRevision()
                        .map(
                                view ->
                                        view.revisionNumber().value()
                                                + " / "
                                                + view.orderedQuantity().toPlainString())
                        .orElse("—"));
        draftRevisionText.set(
                snapshot.draftRevision()
                        .map(
                                view ->
                                        view.revisionNumber().value()
                                                + " / "
                                                + view.orderedQuantity().toPlainString()
                                                + " ("
                                                + view.status().name()
                                                + ")")
                        .orElse("—"));
        draftSpecLineCountText.set(Integer.toString(snapshot.draftSpecificationLineCount()));
        refreshActionFlags();
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
        boolean hasApprove =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_ITEM_APPROVE_PERMISSION));
        boolean hasRevisionCreate =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_REVISION_CREATE_PERMISSION));
        boolean hasRevisionEdit =
                authorization.hasPermission(
                        PermissionId.of(UiShellScreens.ORDER_REVISION_EDIT_PERMISSION));

        if (mode.get() == Mode.CREATE) {
            commercialEditable.set(hasCreate);
            quantityEditable.set(hasCreate);
            canSaveCommercialDraft.set(hasCreate);
            canPostCommercial.set(hasCreate && documentId != null && pendingKind == DocumentKind.ITEM_CREATE);
            canCancelItem.set(false);
            canCreateRevision.set(false);
            canSaveRevisionDraft.set(false);
            canPostRevisionUpdate.set(false);
            canApproveRevision.set(false);
            return;
        }

        boolean draftItem = itemStatus == OrderItemStatus.DRAFT;
        boolean activeItem = itemStatus == OrderItemStatus.ACTIVE;
        boolean cancelled = itemStatus == OrderItemStatus.CANCELLED;
        boolean hasDraftRevision = draftRevisionNumber != null;

        commercialEditable.set(draftItem && hasEdit);
        quantityEditable.set(
                (mode.get() == Mode.CREATE && hasCreate)
                        || (hasDraftRevision && hasRevisionEdit)
                        || (draftItem && hasCreate));
        if (draftItem) {
            // Create flow quantity is part of ITEM_CREATE; for existing draft item quantity belongs
            // to draft revision 1 via REVISION_UPDATE.
            quantityEditable.set(hasDraftRevision && hasRevisionEdit);
        }
        canSaveCommercialDraft.set(draftItem && hasEdit);
        canPostCommercial.set(
                draftItem && hasEdit && documentId != null && pendingKind == DocumentKind.ITEM_UPDATE);
        canCancelItem.set(draftItem && hasCancel);
        canCreateRevision.set(activeItem && !hasDraftRevision && hasRevisionCreate);
        canSaveRevisionDraft.set(hasDraftRevision && hasRevisionEdit);
        canPostRevisionUpdate.set(
                hasDraftRevision
                        && hasRevisionEdit
                        && documentId != null
                        && pendingKind == DocumentKind.REVISION_UPDATE);
        canApproveRevision.set(hasDraftRevision && hasApprove);
        if (cancelled) {
            commercialEditable.set(false);
            quantityEditable.set(false);
            canSaveCommercialDraft.set(false);
            canPostCommercial.set(false);
            canCancelItem.set(false);
            canCreateRevision.set(false);
            canSaveRevisionDraft.set(false);
            canPostRevisionUpdate.set(false);
            canApproveRevision.set(false);
        }
    }

    private OrderItemCommercialDraft currentCommercialDraft() {
        return OrderItemCommercialDraft.of(
                productCode.get(), name.get(), blankToNull(comments.get()));
    }

    private Optional<RevisionNumber> parseCopyFrom() {
        String raw = copyFromRevision.get();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(RevisionNumber.of(Integer.parseInt(raw.trim())));
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Некорректный номер редакции для копирования", ex);
        }
    }

    private void clearMessages() {
        errorMessage.set("");
        successMessage.set("");
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
