package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.AuthenticationService;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.order.worklist.DateTimePresentation;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.SourceCellSuggestion;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentLineInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentRejectResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnPlanItem;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceSuggestionLine;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseHistoryEntryView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseHistoryFilter;
import com.tmp.warehouse.api.WarehouseApi.WarehouseHistoryPage;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellFilterOptionView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellLineView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellPage;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javafx.application.Platform;
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
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * Modern warehouse workspace (Задачи / Остатки / История). Reads and commands via {@link
 * WarehouseApi} only; UI collects selections and reloads — no business calculation of
 * shortfall/continuation/settlement. History is read-only.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class WarehouseWorkspaceViewModel {

    static final int PAGE_SIZE = WarehouseApi.STOCK_SUMMARY_DEFAULT_PAGE_SIZE;
    static final int HISTORY_PAGE_SIZE = WarehouseApi.HISTORY_DEFAULT_PAGE_SIZE;

    private static final String EMPTY_STOCK_MESSAGE = "На выбранном складе нет доступных остатков";
    private static final String EMPTY_CELL_STOCK_MESSAGE =
            "В выбранной ячейке нет доступных остатков";
    private static final String EMPTY_SEARCH_MESSAGE = "По выбранным условиям ничего не найдено";
    private static final String EMPTY_TASKS_MESSAGE = "Нет задач, требующих вашего действия";
    private static final String EMPTY_HISTORY_PERIOD_MESSAGE = "За выбранный период операций нет";
    private static final String EMPTY_HISTORY_FILTER_MESSAGE =
            "По выбранным условиям ничего не найдено";
    private static final String TASK_DETAILS_PLACEHOLDER = "Выберите задачу в списке";
    private static final String HINT_PREPARATION =
            "Укажите ячейки и количества, затем нажмите Передать.";
    private static final String HINT_RECEIPT =
            "Укажите ячейки назначения и количества, затем нажмите Принять или Отклонить.";
    private static final String HINT_RETURN =
            "При необходимости выберите другую ячейку возврата, затем нажмите Вернуть.";

    public enum WorkspaceTab {
        TASKS("Задачи"),
        STOCK("Остатки"),
        HISTORY("История");

        private final String title;

        WorkspaceTab(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    /** Combo item: {@code warehouseId} null = all responsible warehouses. */
    public record WarehouseFilterOption(UUID warehouseId, String label) {

        public static final String ALL_LABEL = "Все мои склады";

        public WarehouseFilterOption {
            Objects.requireNonNull(label, "label");
        }

        public static WarehouseFilterOption all() {
            return new WarehouseFilterOption(null, ALL_LABEL);
        }

        public static WarehouseFilterOption from(WarehouseChoice choice) {
            return new WarehouseFilterOption(choice.id(), choice.label());
        }

        public boolean isAll() {
            return warehouseId == null;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Combo item: {@code operationType} null = all physical operation types. */
    public record HistoryOperationOption(String operationType, String label) {

        public HistoryOperationOption {
            Objects.requireNonNull(label, "label");
        }

        public static HistoryOperationOption all() {
            return new HistoryOperationOption(null, "Все операции");
        }

        public static HistoryOperationOption of(String operationType, String label) {
            return new HistoryOperationOption(operationType, label);
        }

        public boolean isAll() {
            return operationType == null;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Read-only History table row. */
    public static final class HistoryRow {

        private final UUID entryId;
        private final String occurredAtText;
        private final String operationLabel;
        private final String articleText;
        private final String nameText;
        private final String colorText;
        private final String sizeText;
        private final String unitText;
        private final String quantityText;
        private final String sourceText;
        private final String destinationText;
        private final String documentText;
        private final String actorText;

        HistoryRow(
                UUID entryId,
                String occurredAtText,
                String operationLabel,
                String articleText,
                String nameText,
                String colorText,
                String sizeText,
                String unitText,
                String quantityText,
                String sourceText,
                String destinationText,
                String documentText,
                String actorText) {
            this.entryId = Objects.requireNonNull(entryId, "entryId");
            this.occurredAtText = occurredAtText;
            this.operationLabel = operationLabel;
            this.articleText = articleText;
            this.nameText = nameText;
            this.colorText = colorText;
            this.sizeText = sizeText;
            this.unitText = unitText;
            this.quantityText = quantityText;
            this.sourceText = sourceText;
            this.destinationText = destinationText;
            this.documentText = documentText;
            this.actorText = actorText;
        }

        static HistoryRow from(WarehouseHistoryEntryView view, boolean allWarehousesMode) {
            return new HistoryRow(
                    view.entryId(),
                    DateTimePresentation.format(view.occurredAt()),
                    view.operationDisplayName(),
                    blankDash(view.materialArticle()),
                    blankDash(view.materialName()),
                    blankDash(view.materialColor()),
                    blankDash(view.materialSize()),
                    blankDash(view.unitOfMeasure()),
                    formatHistoryQuantity(view.operationType(), view.quantity()),
                    formatHistoryLocation(
                            view.sourceWarehouseName(),
                            view.sourceCellCode(),
                            allWarehousesMode),
                    formatHistoryLocation(
                            view.destinationWarehouseName(),
                            view.destinationCellCode(),
                            allWarehousesMode),
                    blankDash(view.documentNumber()),
                    blankDash(view.actorDisplayName()));
        }

        public UUID entryId() {
            return entryId;
        }

        public String occurredAtText() {
            return occurredAtText;
        }

        public String operationLabel() {
            return operationLabel;
        }

        public String articleText() {
            return articleText;
        }

        public String nameText() {
            return nameText;
        }

        public String colorText() {
            return colorText;
        }

        public String sizeText() {
            return sizeText;
        }

        public String unitText() {
            return unitText;
        }

        public String quantityText() {
            return quantityText;
        }

        public String sourceText() {
            return sourceText;
        }

        public String destinationText() {
            return destinationText;
        }

        public String documentText() {
            return documentText;
        }

        public String actorText() {
            return actorText;
        }
    }

    /** Combo item: {@code storageCellId} null = all cells in current warehouse scope. */
    public record CellFilterOption(
            UUID storageCellId, UUID warehouseId, String label, boolean allModeLabel) {

        public static final String ALL_LABEL = "Все ячейки";

        public CellFilterOption {
            Objects.requireNonNull(label, "label");
        }

        public static CellFilterOption all() {
            return new CellFilterOption(null, null, ALL_LABEL, false);
        }

        public static CellFilterOption from(
                WarehouseStockCellFilterOptionView view, boolean includeWarehousePrefix) {
            Objects.requireNonNull(view, "view");
            String label =
                    includeWarehousePrefix
                            ? view.warehouseCode() + " — " + view.storageCellCode()
                            : view.storageCellCode();
            return new CellFilterOption(
                    view.storageCellId(), view.warehouseId(), label, includeWarehousePrefix);
        }

        public boolean isAll() {
            return storageCellId == null;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Flat cell-centric Stocks table row (one material in one cell). */
    public static final class StockRow {

        private final UUID warehouseId;
        private final UUID storageCellId;
        private final UUID materialReferenceId;
        private final String warehouseLabel;
        private final String cellCode;
        private final String article;
        private final String name;
        private final String color;
        private final String size;
        private final String unitOfMeasure;
        private final BigDecimal availableQuantity;
        private final String quantityText;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        private StockRow(
                UUID warehouseId,
                UUID storageCellId,
                UUID materialReferenceId,
                String warehouseLabel,
                String cellCode,
                String article,
                String name,
                String color,
                String size,
                String unitOfMeasure,
                BigDecimal quantity) {
            this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
            this.storageCellId = Objects.requireNonNull(storageCellId, "storageCellId");
            this.materialReferenceId =
                    Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            this.warehouseLabel = warehouseLabel == null ? "" : warehouseLabel;
            this.cellCode = cellCode == null ? "" : cellCode;
            this.article = article;
            this.name = name;
            this.color = color;
            this.size = size;
            this.unitOfMeasure = unitOfMeasure;
            this.availableQuantity = Objects.requireNonNull(quantity, "availableQuantity");
            this.quantityText = DecimalUiFormat.formatRu(quantity);
        }

        static StockRow from(WarehouseStockCellLineView view) {
            return new StockRow(
                    view.warehouseId(),
                    view.storageCellId(),
                    view.materialReferenceId(),
                    view.warehouseCode() == null ? "" : view.warehouseCode(),
                    view.storageCellCode(),
                    view.article(),
                    view.name(),
                    view.color(),
                    view.size(),
                    view.unitOfMeasure(),
                    view.availableQuantity());
        }

        public UUID warehouseId() {
            return warehouseId;
        }

        public UUID storageCellId() {
            return storageCellId;
        }

        public UUID materialReferenceId() {
            return materialReferenceId;
        }

        public String warehouseLabel() {
            return warehouseLabel;
        }

        public String cellCode() {
            return cellCode;
        }

        public String article() {
            return article;
        }

        public String name() {
            return name;
        }

        public String color() {
            return color;
        }

        public String size() {
            return size;
        }

        public String unitOfMeasure() {
            return unitOfMeasure;
        }

        public BigDecimal availableQuantity() {
            return availableQuantity;
        }

        public String quantityText() {
            return quantityText;
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }

        public boolean isSelected() {
            return selected.get();
        }

        public void setSelected(boolean value) {
            selected.set(value);
        }

        public String materialLabel() {
            StringBuilder builder = new StringBuilder();
            if (article != null && !article.isBlank()) {
                builder.append(article.trim());
            }
            if (name != null && !name.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(" — ");
                }
                builder.append(name.trim());
            }
            return builder.toString();
        }
    }

    public static final class TaskRow {

        private final UUID documentId;
        private final WarehouseTaskKind taskKind;
        private final WarehouseTaskState taskState;
        private final UUID workingUserId;
        private final String documentNumber;
        private final String orderNumberText;
        private final String kindLabel;
        private final String stateLabel;
        private final String sourceWarehouseLabel;
        private final String destinationWarehouseLabel;
        private final String routeLabel;
        private final int lineCount;
        private final String workerDisplay;

        TaskRow(WarehouseTaskView view, String workerLogin) {
            this.documentId = view.documentId();
            this.taskKind = view.taskKind();
            this.taskState = view.taskState();
            this.workingUserId = view.workingUserId();
            this.documentNumber = view.documentNumber();
            this.orderNumberText =
                    view.sourceOrderNumber() == null || view.sourceOrderNumber().isBlank()
                            ? "—"
                            : view.sourceOrderNumber().trim();
            this.kindLabel = kindLabel(view.taskKind());
            this.stateLabel = stateLabel(view.taskState());
            this.sourceWarehouseLabel =
                    formatWarehouse(view.sourceWarehouseCode(), view.sourceWarehouseName());
            this.destinationWarehouseLabel =
                    formatWarehouse(
                            view.destinationWarehouseCode(), view.destinationWarehouseName());
            this.routeLabel = sourceWarehouseLabel + " → " + destinationWarehouseLabel;
            this.lineCount = view.lineCount();
            this.workerDisplay =
                    formatWorkerDisplay(view.taskState(), view.workingUserId(), workerLogin);
        }

        public UUID documentId() {
            return documentId;
        }

        public WarehouseTaskKind taskKind() {
            return taskKind;
        }

        public WarehouseTaskState taskState() {
            return taskState;
        }

        public UUID workingUserId() {
            return workingUserId;
        }

        public String documentNumber() {
            return documentNumber;
        }

        public String orderNumberText() {
            return orderNumberText;
        }

        public String kindLabel() {
            return kindLabel;
        }

        public String stateLabel() {
            return stateLabel;
        }

        public String sourceWarehouseLabel() {
            return sourceWarehouseLabel;
        }

        public String destinationWarehouseLabel() {
            return destinationWarehouseLabel;
        }

        public String routeLabel() {
            return routeLabel;
        }

        public int lineCount() {
            return lineCount;
        }

        public String lineCountText() {
            return Integer.toString(lineCount);
        }

        public String workerDisplay() {
            return workerDisplay;
        }

        static String kindLabel(WarehouseTaskKind kind) {
            return switch (kind) {
                case TRANSFER_PREPARATION -> "Подготовка";
                case TRANSFER_RECEIPT -> "Приёмка";
                case RETURN_MATERIALS -> "Возврат";
            };
        }

        static String stateLabel(WarehouseTaskState state) {
            return switch (state) {
                case NEW -> "Новая";
                case IN_WORK -> "В работе";
            };
        }

        private static String formatWarehouse(String code, String name) {
            if (code == null || code.isBlank()) {
                return name == null ? "" : name;
            }
            if (name == null || name.isBlank()) {
                return code;
            }
            return code + " — " + name;
        }

        private static String formatWorkerDisplay(
                WarehouseTaskState state, UUID workingUserId, String workerLogin) {
            if (state != WarehouseTaskState.IN_WORK) {
                return "—";
            }
            if (workerLogin != null && !workerLogin.isBlank()) {
                return workerLogin;
            }
            if (workingUserId == null) {
                return "В работе";
            }
            return "В работе";
        }
    }

    public abstract static class ActionEditRow {

        private final UUID lineId;
        private final String article;
        private final String name;
        private final String color;
        private final String size;
        private final String unitOfMeasure;
        private final String materialLabel;
        private final ObjectProperty<StorageCellChoice> storageCell = new SimpleObjectProperty<>();

        ActionEditRow(
                UUID lineId,
                MaterialParts material,
                StorageCellChoice initialCell) {
            this.lineId = Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(material, "material");
            this.article = material.article();
            this.name = material.name();
            this.color = material.color();
            this.size = material.size();
            this.unitOfMeasure = material.unitOfMeasure();
            this.materialLabel = material.combinedLabel();
            this.storageCell.set(initialCell);
        }

        public UUID lineId() {
            return lineId;
        }

        public String article() {
            return article;
        }

        public String name() {
            return name;
        }

        public String color() {
            return color;
        }

        public String size() {
            return size;
        }

        public String unitOfMeasure() {
            return unitOfMeasure;
        }

        public String materialLabel() {
            return materialLabel;
        }

        public abstract String referenceQuantityText();

        public ObjectProperty<StorageCellChoice> storageCellProperty() {
            return storageCell;
        }

        public abstract StringProperty quantityTextProperty();

        public abstract boolean quantityEditable();
    }

    /** Split material fields for Tasks dialog columns. */
    public record MaterialParts(
            String article, String name, String color, String size, String unitOfMeasure) {

        public MaterialParts {
            article = article == null ? "" : article;
            name = name == null ? "" : name;
            color = color == null ? "" : color;
            size = size == null ? "" : size;
            unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
        }

        static MaterialParts from(MaterialReferenceView view) {
            if (view == null) {
                return new MaterialParts("", "", "", "", "");
            }
            return new MaterialParts(
                    view.article(), view.name(), view.color(), view.size(), view.unitOfMeasure());
        }

        static MaterialParts unknown(UUID materialReferenceId) {
            String id = materialReferenceId == null ? "" : materialReferenceId.toString();
            return new MaterialParts(id, "", "", "", "");
        }

        String combinedLabel() {
            return formatMaterialDisplay(
                    article, name, color, size, unitOfMeasure);
        }
    }

    public static final class SourceAllocationEditRow extends ActionEditRow {

        private final BigDecimal requiredQuantity;
        private final StringProperty quantityText = new SimpleStringProperty("");

        SourceAllocationEditRow(
                UUID lineId,
                MaterialParts material,
                BigDecimal requiredQuantity,
                StorageCellChoice cell,
                BigDecimal quantity) {
            super(lineId, material, cell);
            this.requiredQuantity = Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            if (quantity != null) {
                this.quantityText.set(DecimalUiFormat.formatRu(quantity));
            }
        }

        public BigDecimal requiredQuantity() {
            return requiredQuantity;
        }

        @Override
        public String referenceQuantityText() {
            return DecimalUiFormat.formatRu(requiredQuantity);
        }

        @Override
        public StringProperty quantityTextProperty() {
            return quantityText;
        }

        @Override
        public boolean quantityEditable() {
            return true;
        }
    }

    public static final class ReceiveAllocationEditRow extends ActionEditRow {

        private final BigDecimal sentQuantity;
        private final StringProperty quantityText = new SimpleStringProperty("");

        ReceiveAllocationEditRow(
                UUID lineId,
                MaterialParts material,
                BigDecimal sentQuantity,
                StorageCellChoice cell,
                BigDecimal acceptQuantity) {
            super(lineId, material, cell);
            this.sentQuantity = Objects.requireNonNull(sentQuantity, "sentQuantity");
            if (acceptQuantity != null) {
                this.quantityText.set(DecimalUiFormat.formatRu(acceptQuantity));
            }
        }

        public BigDecimal sentQuantity() {
            return sentQuantity;
        }

        @Override
        public String referenceQuantityText() {
            return DecimalUiFormat.formatRu(sentQuantity);
        }

        @Override
        public StringProperty quantityTextProperty() {
            return quantityText;
        }

        @Override
        public boolean quantityEditable() {
            return true;
        }
    }

    public static final class ReturnAllocationEditRow extends ActionEditRow {

        private final BigDecimal outstandingQuantity;
        private final UUID defaultReturnStorageCellId;
        private final StringProperty quantityText;

        ReturnAllocationEditRow(
                UUID lineId,
                MaterialParts material,
                BigDecimal outstandingQuantity,
                UUID defaultReturnStorageCellId,
                StorageCellChoice cell) {
            super(lineId, material, cell);
            this.outstandingQuantity =
                    Objects.requireNonNull(outstandingQuantity, "outstandingQuantity");
            this.defaultReturnStorageCellId =
                    Objects.requireNonNull(defaultReturnStorageCellId, "defaultReturnStorageCellId");
            this.quantityText =
                    new FixedQuantityProperty(DecimalUiFormat.formatRu(outstandingQuantity));
        }

        public BigDecimal outstandingQuantity() {
            return outstandingQuantity;
        }

        public UUID defaultReturnStorageCellId() {
            return defaultReturnStorageCellId;
        }

        @Override
        public String referenceQuantityText() {
            return DecimalUiFormat.formatRu(outstandingQuantity);
        }

        @Override
        public StringProperty quantityTextProperty() {
            return quantityText;
        }

        @Override
        public boolean quantityEditable() {
            return false;
        }
    }

    private static final class FixedQuantityProperty extends SimpleStringProperty {
        private final String fixed;

        FixedQuantityProperty(String fixed) {
            super(fixed);
            this.fixed = fixed;
        }

        @Override
        public void set(String newValue) {
            super.set(fixed);
        }

        @Override
        public void setValue(String v) {
            super.set(fixed);
        }
    }

    private final WarehouseApi warehouseApi;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> uiExecutor;

    private final ObjectProperty<WorkspaceTab> selectedTab =
            new SimpleObjectProperty<>(WorkspaceTab.TASKS);
    private final StringProperty title = new SimpleStringProperty("Склад");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty taskDetailsText =
            new SimpleStringProperty(TASK_DETAILS_PLACEHOLDER);
    private final StringProperty transferActionsHint = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty taskDetailLoading = new SimpleBooleanProperty(false);
    private final BooleanProperty commandInFlight = new SimpleBooleanProperty(false);
    private final BooleanProperty canView = new SimpleBooleanProperty(false);
    private final BooleanProperty canTransfer = new SimpleBooleanProperty(false);
    private final BooleanProperty canMove = new SimpleBooleanProperty(false);
    private final BooleanProperty canReceipt = new SimpleBooleanProperty(false);
    private final BooleanProperty canConsumption = new SimpleBooleanProperty(false);
    private final BooleanProperty canAdjustment = new SimpleBooleanProperty(false);
    private final BooleanProperty canTakeSelectedTaskInWork = new SimpleBooleanProperty(false);
    private final BooleanProperty canSendSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty canReceiveSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty canRejectSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty canReturnSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty showWarehouseColumn = new SimpleBooleanProperty(false);
    private final BooleanProperty canMoveSelectedStock = new SimpleBooleanProperty(false);
    private final BooleanProperty canConsumeSelectedStock = new SimpleBooleanProperty(false);
    private final BooleanProperty canAdjustSelectedStock = new SimpleBooleanProperty(false);

    private final ObservableList<WarehouseFilterOption> warehouseFilterOptions =
            FXCollections.observableArrayList();
    private final ObjectProperty<WarehouseFilterOption> selectedWarehouseFilter =
            new SimpleObjectProperty<>();
    private final ObservableList<CellFilterOption> cellFilterOptions =
            FXCollections.observableArrayList();
    private final ObjectProperty<CellFilterOption> selectedCellFilter =
            new SimpleObjectProperty<>();
    private final StringProperty searchInput = new SimpleStringProperty("");
    private final ObservableList<StockRow> tableRows = FXCollections.observableArrayList();
    private final ObservableList<TaskRow> taskRows = FXCollections.observableArrayList();
    private final ObjectProperty<TaskRow> selectedTask = new SimpleObjectProperty<>();
    private final ObservableList<ActionEditRow> actionLines = FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> actionCellChoices =
            FXCollections.observableArrayList();

    private final IntegerProperty pageIndex = new SimpleIntegerProperty(0);
    private final LongProperty totalElements = new SimpleLongProperty(0);
    private final BooleanProperty canGoPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canGoNext = new SimpleBooleanProperty(false);

    private final ObservableList<HistoryRow> historyRows = FXCollections.observableArrayList();
    private final ObjectProperty<LocalDate> historyFromDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> historyToDate = new SimpleObjectProperty<>();
    private final StringProperty historySearchInput = new SimpleStringProperty("");
    private final ObservableList<HistoryOperationOption> historyOperationOptions =
            FXCollections.observableArrayList();
    private final ObjectProperty<HistoryOperationOption> selectedHistoryOperation =
            new SimpleObjectProperty<>();
    private final IntegerProperty historyPageIndex = new SimpleIntegerProperty(0);
    private final LongProperty historyTotalElements = new SimpleLongProperty(0);
    private final BooleanProperty historyCanGoPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty historyCanGoNext = new SimpleBooleanProperty(false);

    private final Set<UUID> accessibleWarehouseIds = new HashSet<>();
    private final Map<UUID, String> warehouseLabels = new HashMap<>();
    private final Map<UUID, MaterialReferenceView> materialById = new HashMap<>();
    private String committedSearch = "";
    private String committedHistorySearch = "";
    private long stockLoadGeneration;
    private long cellFilterLoadGeneration;
    private long taskLoadGeneration;
    private long historyLoadGeneration;
    private long taskDetailLoadGeneration;
    private boolean stockLoadedForCurrentFilter;
    private String pendingStockReloadReason = "UNSPECIFIED";
    private IntSupplier stockScrollAnchor;
    private IntConsumer stockScrollRestorer;

    private TransferDocumentView loadedDocument;
    private List<TransferDocumentReturnPlanItem> loadedReturnPlan = List.of();
    private String pendingTaskStatusMessage;
    private String pendingTaskErrorMessage;
    private boolean taskDialogOpen;
    private boolean closeTaskDialogAfterReload;
    private Runnable afterTerminalTaskAction;
    private Runnable afterTaskDetailsLoaded;
    private final ListChangeListener<ActionEditRow> actionLinesListener =
            change -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (ActionEditRow row : change.getAddedSubList()) {
                            attachRowValidationListeners(row);
                        }
                    }
                }
                updateActionAvailability();
            };

    public WarehouseWorkspaceViewModel(
            WarehouseApi warehouseApi, AuthorizationService authorizationService) {
        this(warehouseApi, authorizationService, null);
    }

    public WarehouseWorkspaceViewModel(
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService) {
        this(
                warehouseApi,
                authorizationService,
                authenticationService,
                Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread = new Thread(runnable, "warehouse-workspace");
                            thread.setDaemon(true);
                            return thread;
                        }),
                Platform::runLater);
    }

    WarehouseWorkspaceViewModel(
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            Executor backgroundExecutor,
            Consumer<Runnable> uiExecutor) {
        this(warehouseApi, authorizationService, null, backgroundExecutor, uiExecutor);
    }

    WarehouseWorkspaceViewModel(
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            Executor backgroundExecutor,
            Consumer<Runnable> uiExecutor) {
        this.warehouseApi = Objects.requireNonNull(warehouseApi, "warehouseApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService = authenticationService;
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        actionLines.addListener(actionLinesListener);
        commandInFlight.addListener(
                (obs, o, n) -> {
                    updateActionAvailability();
                    updateStockActionAvailability();
                });
        loading.addListener(
                (obs, o, n) -> {
                    updateActionAvailability();
                    updateStockActionAvailability();
                });
        taskDetailLoading.addListener((obs, o, n) -> updateActionAvailability());
        canTransfer.addListener((obs, o, n) -> updateActionAvailability());
        canMove.addListener((obs, o, n) -> updateStockActionAvailability());
        canConsumption.addListener((obs, o, n) -> updateStockActionAvailability());
        canAdjustment.addListener((obs, o, n) -> updateStockActionAvailability());
        CellFilterOption initialAllCells = CellFilterOption.all();
        cellFilterOptions.setAll(initialAllCells);
        selectedCellFilter.set(initialAllCells);
        initializeHistoryFilters();
        refreshPermissions();
        updatePaginationFlags();
        updateHistoryPaginationFlags();
    }

    private void initializeHistoryFilters() {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        historyFromDate.set(today.minusDays(29));
        historyToDate.set(today);
        historyOperationOptions.setAll(
                HistoryOperationOption.all(),
                HistoryOperationOption.of("RECEIPT", "Приход"),
                HistoryOperationOption.of("MOVE", "Перемещение"),
                HistoryOperationOption.of("TRANSFER_SEND", "Передача"),
                HistoryOperationOption.of("TRANSFER_RECEIVE", "Приёмка"),
                HistoryOperationOption.of("TRANSFER_RETURN", "Возврат"),
                HistoryOperationOption.of("CONSUMPTION", "Списание"),
                HistoryOperationOption.of("ADJUSTMENT", "Корректировка"));
        selectedHistoryOperation.set(HistoryOperationOption.all());
    }

    public void refreshPermissions() {
        canView.set(has(UiShellScreens.WAREHOUSE_VIEW_PERMISSION));
        canTransfer.set(has(UiShellScreens.WAREHOUSE_TRANSFER_PERMISSION));
        canMove.set(has(UiShellScreens.WAREHOUSE_MOVE_PERMISSION));
        canReceipt.set(has(UiShellScreens.WAREHOUSE_RECEIPT_PERMISSION));
        canConsumption.set(has(UiShellScreens.WAREHOUSE_CONSUMPTION_PERMISSION));
        canAdjustment.set(has(UiShellScreens.WAREHOUSE_ADJUSTMENT_PERMISSION));
        updateActionAvailability();
        updateStockActionAvailability();
    }

    public void onScreenOpened() {
        refreshPermissions();
        if (!canView.get()) {
            deny();
            return;
        }
        loadWarehouseFiltersAndInitialContent();
    }

    public void selectTab(WorkspaceTab tab) {
        Objects.requireNonNull(tab, "tab");
        WorkspaceTab previous = selectedTab.get();
        if (previous == tab) {
            return;
        }
        selectedTab.set(tab);
        errorMessage.set("");
        if (tab == WorkspaceTab.TASKS) {
            reloadTasks();
        } else if (tab == WorkspaceTab.STOCK) {
            ensureStockLoaded();
        } else if (tab == WorkspaceTab.HISTORY) {
            long actionId = HistoryRefreshTrace.beginAction("TAB_HISTORY");
            try {
                reloadHistory("TAB_HISTORY");
            } finally {
                HistoryRefreshTrace.endAction(actionId);
            }
        } else {
            statusMessage.set("");
        }
    }

    public void selectWarehouseFilter(WarehouseFilterOption option) {
        if (option != null && !option.isAll() && !accessibleWarehouseIds.contains(option.warehouseId())) {
            return;
        }
        WarehouseFilterOption current = selectedWarehouseFilter.get();
        if (Objects.equals(current, option)) {
            return;
        }
        pageIndex.set(0);
        historyPageIndex.set(0);
        setSelectedWarehouseFilterIdentity(option);
        showWarehouseColumn.set(option != null && option.isAll());
        setSelectedCellFilterToAllInListOrFresh();
        stockLoadedForCurrentFilter = false;
        WorkspaceTab tab = selectedTab.get();
        if (tab == WorkspaceTab.TASKS) {
            long actionId = TasksRefreshTrace.beginAction("WAREHOUSE_FILTER");
            try {
                reloadTasks("WAREHOUSE_FILTER");
            } finally {
                TasksRefreshTrace.endAction(actionId);
            }
        } else if (tab == WorkspaceTab.STOCK) {
            pendingStockReloadReason = "WAREHOUSE_FILTER";
            reloadCellFilterOptionsThenStock();
        } else if (tab == WorkspaceTab.HISTORY) {
            long actionId = HistoryRefreshTrace.beginAction("WAREHOUSE_FILTER");
            try {
                reloadHistory("WAREHOUSE_FILTER");
            } finally {
                HistoryRefreshTrace.endAction(actionId);
            }
        }
    }

    public void selectCellFilter(CellFilterOption option) {
        Objects.requireNonNull(option, "option");
        CellFilterOption current = selectedCellFilter.get();
        if (Objects.equals(current, option)
                || (current != null
                        && option.isAll()
                        && current.isAll()
                        && Objects.equals(current.label(), option.label()))) {
            return;
        }
        if (!option.isAll()) {
            boolean known =
                    cellFilterOptions.stream()
                            .anyMatch(o -> Objects.equals(o.storageCellId(), option.storageCellId()));
            if (!known) {
                return;
            }
        }
        selectedCellFilter.set(option);
        pageIndex.set(0);
        stockLoadedForCurrentFilter = false;
        if (selectedTab.get() == WorkspaceTab.STOCK) {
            reloadStockByCells("CELL_FILTER");
        }
    }

    public void selectTask(TaskRow row) {
        selectedTask.set(row);
        if (!taskDialogOpen) {
            clearActionEditingState();
            updateActionAvailability();
            updateTransferActionsHint();
        }
    }

    /** Load task details into the dialog surface (double-click). Does not run on single select. */
    public void openTaskDialogDetails(TaskRow row) {
        Objects.requireNonNull(row, "row");
        selectedTask.set(row);
        taskDialogOpen = true;
        closeTaskDialogAfterReload = false;
        clearActionEditingState();
        updateTransferActionsHint();
        loadSelectedTaskDetails(row);
    }

    public void setTaskDialogOpen(boolean open) {
        this.taskDialogOpen = open;
        if (!open) {
            closeTaskDialogAfterReload = false;
            clearActionEditingState();
            updateActionAvailability();
            updateTransferActionsHint();
        }
    }

    public boolean isTaskDialogOpen() {
        return taskDialogOpen;
    }

    public void setAfterTerminalTaskAction(Runnable handler) {
        this.afterTerminalTaskAction = handler;
    }

    public void setAfterTaskDetailsLoaded(Runnable handler) {
        this.afterTaskDetailsLoaded = handler;
    }

    public void takeSelectedTaskInWork() {
        TaskRow row = selectedTask.get();
        if (row == null || !canTakeSelectedTaskInWork.get() || commandInFlight.get()) {
            return;
        }
        UUID documentId = row.documentId();
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        warehouseApi.takeTransferTaskInWork(documentId);
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    reloadTasks("TAKE");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                    if (WarehouseUiErrorMapper.isStaleConflict(ex)) {
                                        reloadTasks("TAKE_STALE");
                                    }
                                });
                    }
                });
    }

    public void sendSelectedTask() {
        TaskRow row = selectedTask.get();
        TransferDocumentView document = loadedDocument;
        if (row == null
                || document == null
                || commandInFlight.get()
                || !canSendSelectedTask.get()) {
            return;
        }
        List<TransferDocumentSourceAllocationInput> allocations = buildSendAllocations();
        if (allocations.isEmpty()) {
            return;
        }
        UUID documentId = document.documentId();
        long documentVersion = document.documentVersion();
        long payloadRevision = document.payloadRevision();
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        TransferDocumentSendResult result =
                                warehouseApi.sendTransferDocument(
                                        new SendTransferDocumentCommand(
                                                documentId,
                                                documentVersion,
                                                payloadRevision,
                                                allocations));
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    pendingTaskStatusMessage =
                                            formatSendSuccess(result, allocations);
                                    invalidateStockAfterTaskMutation();
                                    closeTaskDialogAfterReload = true;
                                    reloadTasks("SEND");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> handleCommandFailure(ex));
                    }
                });
    }

    public void receiveSelectedTask() {
        TaskRow row = selectedTask.get();
        TransferDocumentView document = loadedDocument;
        if (row == null
                || document == null
                || document.operationalRevision() == null
                || commandInFlight.get()
                || !canReceiveSelectedTask.get()) {
            return;
        }
        List<TransferDocumentDestinationAllocationInput> allocations = buildReceiveAllocations();
        if (allocations.isEmpty()) {
            return;
        }
        UUID documentId = document.documentId();
        long operationalRevision = document.operationalRevision();
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        TransferDocumentReceiveResult result =
                                warehouseApi.receiveTransferDocument(
                                        new ReceiveTransferDocumentCommand(
                                                documentId, operationalRevision, allocations));
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    pendingTaskStatusMessage =
                                            formatReceiveSuccess(result, allocations);
                                    invalidateStockAfterTaskMutation();
                                    closeTaskDialogAfterReload = true;
                                    reloadTasks("RECEIVE");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> handleCommandFailure(ex));
                    }
                });
    }

    public void rejectSelectedTask(String reason) {
        TaskRow row = selectedTask.get();
        TransferDocumentView document = loadedDocument;
        if (row == null
                || document == null
                || document.operationalRevision() == null
                || commandInFlight.get()
                || !canRejectSelectedTask.get()) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            errorMessage.set(WarehouseUiErrorMapper.VALIDATION);
            return;
        }
        String trimmed = reason.trim();
        UUID documentId = document.documentId();
        long operationalRevision = document.operationalRevision();
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        TransferDocumentRejectResult result =
                                warehouseApi.rejectTransferDocument(
                                        new RejectTransferDocumentCommand(
                                                documentId, operationalRevision, trimmed));
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    pendingTaskStatusMessage =
                                            "Отклонено"
                                                    + (result.rejectionReason() == null
                                                                    || result.rejectionReason()
                                                                            .isBlank()
                                                            ? ""
                                                            : ": " + result.rejectionReason());
                                    invalidateStockAfterTaskMutation();
                                    closeTaskDialogAfterReload = true;
                                    reloadTasks("REJECT");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> handleCommandFailure(ex));
                    }
                });
    }

    public void returnSelectedTask() {
        TaskRow row = selectedTask.get();
        TransferDocumentView document = loadedDocument;
        if (row == null
                || document == null
                || document.operationalRevision() == null
                || commandInFlight.get()
                || !canReturnSelectedTask.get()) {
            return;
        }
        List<TransferDocumentReturnAllocationInput> allocations = buildReturnAllocations();
        UUID documentId = document.documentId();
        long operationalRevision = document.operationalRevision();
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        warehouseApi.returnTransferMaterials(
                                        new ReturnTransferMaterialsCommand(
                                                documentId, operationalRevision, allocations));
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    pendingTaskStatusMessage = "Материалы возвращены";
                                    invalidateStockAfterTaskMutation();
                                    closeTaskDialogAfterReload = true;
                                    reloadTasks("RETURN");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> handleCommandFailure(ex));
                    }
                });
    }

    public void addReceiveAllocationForLine(UUID lineId) {
        Objects.requireNonNull(lineId, "lineId");
        ReceiveAllocationEditRow template = null;
        for (ActionEditRow row : actionLines) {
            if (row instanceof ReceiveAllocationEditRow receive && lineId.equals(receive.lineId())) {
                template = receive;
                break;
            }
        }
        if (template == null) {
            return;
        }
        ReceiveAllocationEditRow added =
                new ReceiveAllocationEditRow(
                        template.lineId(),
                        new MaterialParts(
                                template.article(),
                                template.name(),
                                template.color(),
                                template.size(),
                                template.unitOfMeasure()),
                        template.sentQuantity(),
                        null,
                        null);
        actionLines.add(added);
    }

    public void removeReceiveAllocation(ReceiveAllocationEditRow row) {
        Objects.requireNonNull(row, "row");
        long count =
                actionLines.stream()
                        .filter(
                                r ->
                                        r instanceof ReceiveAllocationEditRow
                                                && row.lineId().equals(r.lineId()))
                        .count();
        if (count <= 1) {
            return;
        }
        actionLines.remove(row);
        updateActionAvailability();
    }

    public void addSourceAllocationForLine(UUID lineId) {
        Objects.requireNonNull(lineId, "lineId");
        SourceAllocationEditRow template = null;
        for (ActionEditRow row : actionLines) {
            if (row instanceof SourceAllocationEditRow source && lineId.equals(source.lineId())) {
                template = source;
                break;
            }
        }
        if (template == null) {
            return;
        }
        actionLines.add(
                new SourceAllocationEditRow(
                        template.lineId(),
                        new MaterialParts(
                                template.article(),
                                template.name(),
                                template.color(),
                                template.size(),
                                template.unitOfMeasure()),
                        template.requiredQuantity(),
                        null,
                        null));
    }

    public void removeSourceAllocation(SourceAllocationEditRow row) {
        Objects.requireNonNull(row, "row");
        long count =
                actionLines.stream()
                        .filter(
                                r ->
                                        r instanceof SourceAllocationEditRow
                                                && row.lineId().equals(r.lineId()))
                        .count();
        if (count <= 1) {
            return;
        }
        actionLines.remove(row);
        updateActionAvailability();
    }

    public void commitSearch() {
        if (selectedTab.get() != WorkspaceTab.STOCK) {
            return;
        }
        committedSearch = blankToNull(searchInput.get()) == null ? "" : searchInput.get().trim();
        pageIndex.set(0);
        reloadStockByCells("SEARCH");
    }

    public void commitHistorySearch() {
        if (selectedTab.get() != WorkspaceTab.HISTORY) {
            return;
        }
        committedHistorySearch =
                blankToNull(historySearchInput.get()) == null
                        ? ""
                        : historySearchInput.get().trim();
        historyPageIndex.set(0);
        long actionId = HistoryRefreshTrace.beginAction("SEARCH");
        try {
            reloadHistory("SEARCH");
        } finally {
            HistoryRefreshTrace.endAction(actionId);
        }
    }

    public void setHistoryFromDate(LocalDate date) {
        if (Objects.equals(historyFromDate.get(), date)) {
            return;
        }
        historyFromDate.set(date);
        historyPageIndex.set(0);
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            long actionId = HistoryRefreshTrace.beginAction("PERIOD_FROM");
            try {
                reloadHistory("PERIOD_FROM");
            } finally {
                HistoryRefreshTrace.endAction(actionId);
            }
        }
    }

    public void setHistoryToDate(LocalDate date) {
        if (Objects.equals(historyToDate.get(), date)) {
            return;
        }
        historyToDate.set(date);
        historyPageIndex.set(0);
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            long actionId = HistoryRefreshTrace.beginAction("PERIOD_TO");
            try {
                reloadHistory("PERIOD_TO");
            } finally {
                HistoryRefreshTrace.endAction(actionId);
            }
        }
    }

    public void selectHistoryOperation(HistoryOperationOption option) {
        Objects.requireNonNull(option, "option");
        HistoryOperationOption current = selectedHistoryOperation.get();
        if (Objects.equals(current, option)) {
            return;
        }
        selectedHistoryOperation.set(option);
        historyPageIndex.set(0);
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            long actionId = HistoryRefreshTrace.beginAction("OPERATION_FILTER");
            try {
                reloadHistory("OPERATION_FILTER");
            } finally {
                HistoryRefreshTrace.endAction(actionId);
            }
        }
    }

    public void refreshHistory() {
        if (selectedTab.get() != WorkspaceTab.HISTORY) {
            return;
        }
        long actionId = HistoryRefreshTrace.beginAction("REFRESH");
        try {
            reloadHistory("REFRESH");
        } finally {
            HistoryRefreshTrace.endAction(actionId);
        }
    }

    public void nextPage() {
        if (selectedTab.get() != WorkspaceTab.STOCK) {
            return;
        }
        long total = totalElements.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / PAGE_SIZE);
        if (pageIndex.get() < maxPage) {
            pageIndex.set(pageIndex.get() + 1);
            reloadStockByCells("PAGE_NEXT");
        }
    }

    public void previousPage() {
        if (selectedTab.get() != WorkspaceTab.STOCK) {
            return;
        }
        if (pageIndex.get() > 0) {
            pageIndex.set(pageIndex.get() - 1);
            reloadStockByCells("PAGE_PREV");
        }
    }

    public void nextHistoryPage() {
        if (selectedTab.get() != WorkspaceTab.HISTORY) {
            return;
        }
        long total = historyTotalElements.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / HISTORY_PAGE_SIZE);
        if (historyPageIndex.get() < maxPage) {
            historyPageIndex.set(historyPageIndex.get() + 1);
            long actionId = HistoryRefreshTrace.beginAction("PAGE_NEXT");
            try {
                reloadHistory("PAGE_NEXT");
            } finally {
                HistoryRefreshTrace.endAction(actionId);
            }
        }
    }

    public void previousHistoryPage() {
        if (selectedTab.get() != WorkspaceTab.HISTORY) {
            return;
        }
        if (historyPageIndex.get() > 0) {
            historyPageIndex.set(historyPageIndex.get() - 1);
            long actionId = HistoryRefreshTrace.beginAction("PAGE_PREV");
            try {
                reloadHistory("PAGE_PREV");
            } finally {
                HistoryRefreshTrace.endAction(actionId);
            }
        }
    }

    public ObjectProperty<WorkspaceTab> selectedTabProperty() {
        return selectedTab;
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

    public BooleanProperty taskDetailLoadingProperty() {
        return taskDetailLoading;
    }

    public BooleanProperty commandInFlightProperty() {
        return commandInFlight;
    }

    public BooleanProperty canViewProperty() {
        return canView;
    }

    public BooleanProperty canMoveSelectedStockProperty() {
        return canMoveSelectedStock;
    }

    public BooleanProperty canCreateReceiptProperty() {
        return canReceipt;
    }

    public BooleanProperty canConsumeSelectedStockProperty() {
        return canConsumeSelectedStock;
    }

    public BooleanProperty canAdjustSelectedStockProperty() {
        return canAdjustSelectedStock;
    }

    public BooleanProperty showWarehouseColumnProperty() {
        return showWarehouseColumn;
    }

    public ObservableList<WarehouseFilterOption> warehouseFilterOptions() {
        return warehouseFilterOptions;
    }

    public ObjectProperty<WarehouseFilterOption> selectedWarehouseFilterProperty() {
        return selectedWarehouseFilter;
    }

    public ObservableList<CellFilterOption> cellFilterOptions() {
        return cellFilterOptions;
    }

    public ObjectProperty<CellFilterOption> selectedCellFilterProperty() {
        return selectedCellFilter;
    }

    public StringProperty searchInputProperty() {
        return searchInput;
    }

    public ObservableList<StockRow> tableRows() {
        return tableRows;
    }

    public IntegerProperty pageIndexProperty() {
        return pageIndex;
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

    public ObservableList<TaskRow> taskRows() {
        return taskRows;
    }

    public ObjectProperty<TaskRow> selectedTaskProperty() {
        return selectedTask;
    }

    public ObservableList<ActionEditRow> actionLines() {
        return actionLines;
    }

    public ObservableList<StorageCellChoice> actionCellChoices() {
        return actionCellChoices;
    }

    public StringProperty taskDetailsTextProperty() {
        return taskDetailsText;
    }

    public StringProperty transferActionsHintProperty() {
        return transferActionsHint;
    }

    public BooleanProperty canTakeSelectedTaskInWorkProperty() {
        return canTakeSelectedTaskInWork;
    }

    public BooleanProperty canSendSelectedTaskProperty() {
        return canSendSelectedTask;
    }

    public BooleanProperty canReceiveSelectedTaskProperty() {
        return canReceiveSelectedTask;
    }

    public BooleanProperty canRejectSelectedTaskProperty() {
        return canRejectSelectedTask;
    }

    public BooleanProperty canReturnSelectedTaskProperty() {
        return canReturnSelectedTask;
    }

    public ObservableList<HistoryRow> historyRows() {
        return historyRows;
    }

    public ObjectProperty<LocalDate> historyFromDateProperty() {
        return historyFromDate;
    }

    public ObjectProperty<LocalDate> historyToDateProperty() {
        return historyToDate;
    }

    public StringProperty historySearchInputProperty() {
        return historySearchInput;
    }

    public ObservableList<HistoryOperationOption> historyOperationOptions() {
        return historyOperationOptions;
    }

    public ObjectProperty<HistoryOperationOption> selectedHistoryOperationProperty() {
        return selectedHistoryOperation;
    }

    public IntegerProperty historyPageIndexProperty() {
        return historyPageIndex;
    }

    public LongProperty historyTotalElementsProperty() {
        return historyTotalElements;
    }

    public BooleanProperty historyCanGoPreviousProperty() {
        return historyCanGoPrevious;
    }

    public BooleanProperty historyCanGoNextProperty() {
        return historyCanGoNext;
    }

    private void loadWarehouseFiltersAndInitialContent() {
        errorMessage.set("");
        statusMessage.set("");
        try {
            List<WarehouseView> mine = warehouseApi.listMyWarehouses();
            accessibleWarehouseIds.clear();
            warehouseLabels.clear();
            List<WarehouseFilterOption> options = new ArrayList<>();
            for (WarehouseView view : mine) {
                accessibleWarehouseIds.add(view.warehouseId());
                warehouseLabels.put(view.warehouseId(), view.code() + " — " + view.name());
            }
            if (mine.size() > 1) {
                options.add(WarehouseFilterOption.all());
            }
            for (WarehouseView view : mine) {
                options.add(WarehouseFilterOption.from(WarehouseChoice.from(view)));
            }
            warehouseFilterOptions.setAll(options);

            WarehouseFilterOption current = selectedWarehouseFilter.get();
            WarehouseFilterOption resolved = resolveWarehouseFilter(options, current, mine);
            setSelectedWarehouseFilterIdentity(resolved);
            showWarehouseColumn.set(resolved != null && resolved.isAll());
            if (selectedCellFilter.get() == null || selectedCellFilter.get().isAll()) {
                setSelectedCellFilterToAllInListOrFresh();
            }
            stockLoadedForCurrentFilter = false;
            if (selectedTab.get() == WorkspaceTab.STOCK) {
                pendingStockReloadReason = "INITIAL";
                reloadCellFilterOptionsThenStock();
            } else if (selectedTab.get() == WorkspaceTab.HISTORY) {
                reloadHistory();
            } else {
                reloadTasks();
            }
        } catch (RuntimeException ex) {
            errorMessage.set(WarehouseUiErrorMapper.text(ex));
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        }
    }

    private void ensureStockLoaded() {
        if (!stockLoadedForCurrentFilter) {
            pendingStockReloadReason = "TAB_ENSURE";
            reloadCellFilterOptionsThenStock();
        }
    }

    private void reloadTasks() {
        reloadTasks("UNSPECIFIED");
    }

    private void reloadTasks(String reason) {
        if (!canView.get()) {
            deny();
            return;
        }
        WarehouseFilterOption filter = selectedWarehouseFilter.get();
        UUID warehouseId = filter == null ? null : filter.warehouseId();
        String filterLabel = filter == null ? "null" : filter.label();
        long requestId = ++taskLoadGeneration;
        Object itemsBefore = taskRows;
        int rowsBefore = taskRows.size();
        TasksRefreshTrace.reloadRequested(reason, rowsBefore, itemsBefore, filterLabel);
        loading.set(true);
        errorMessage.set("");
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            statusMessage.set("");
        }
        backgroundExecutor.execute(
                () -> {
                    try {
                        List<WarehouseTaskView> tasks =
                                warehouseApi.listMyWarehouseTasks(warehouseId);
                        uiExecutor.accept(() -> applyTaskList(tasks, requestId, reason));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyTaskLoadError(ex, requestId, reason));
                    }
                });
    }

    private void applyTaskList(List<WarehouseTaskView> tasks, long requestId, String reason) {
        if (requestId != taskLoadGeneration) {
            return;
        }
        int rowsBefore = taskRows.size();
        Object itemsBefore = taskRows;
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            loading.set(false);
        }
        UUID previousSelection = selectedTask.get() == null ? null : selectedTask.get().documentId();
        List<TaskRow> rows = new ArrayList<>();
        for (WarehouseTaskView task : tasks) {
            String workerLogin = sessionLoginMatching(task.workingUserId());
            rows.add(new TaskRow(task, workerLogin));
        }
        taskRows.setAll(rows);
        TasksRefreshTrace.applyPage(
                reason, rowsBefore, rows.size(), itemsBefore, taskRows, true);
        TaskRow restored =
                previousSelection == null
                        ? null
                        : rows.stream()
                                .filter(r -> previousSelection.equals(r.documentId()))
                                .findFirst()
                                .orElse(null);
        selectedTask.set(restored);
        if (restored != null && taskDialogOpen && !closeTaskDialogAfterReload) {
            clearActionEditingState();
            updateTransferActionsHint();
            loadSelectedTaskDetails(restored);
        } else {
            clearActionEditingState();
            taskDetailsText.set(TASK_DETAILS_PLACEHOLDER);
            updateTransferActionsHint();
        }
        updateActionAvailability();
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            if (pendingTaskStatusMessage != null) {
                statusMessage.set(pendingTaskStatusMessage);
                pendingTaskStatusMessage = null;
            } else if (rows.isEmpty()) {
                statusMessage.set(EMPTY_TASKS_MESSAGE);
            }
            if (pendingTaskErrorMessage != null) {
                errorMessage.set(pendingTaskErrorMessage);
                pendingTaskErrorMessage = null;
            }
        }
        if (closeTaskDialogAfterReload) {
            closeTaskDialogAfterReload = false;
            taskDialogOpen = false;
            Runnable closeHandler = afterTerminalTaskAction;
            if (closeHandler != null) {
                closeHandler.run();
            }
        }
    }

    private void applyTaskLoadError(RuntimeException ex, long requestId, String reason) {
        if (requestId != taskLoadGeneration) {
            return;
        }
        int rowsBefore = taskRows.size();
        Object itemsBefore = taskRows;
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            loading.set(false);
        }
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        }
        taskRows.clear();
        TasksRefreshTrace.applyPage(reason, rowsBefore, 0, itemsBefore, taskRows, true);
        selectedTask.set(null);
        clearActionEditingState();
        taskDetailsText.set(TASK_DETAILS_PLACEHOLDER);
        updateTransferActionsHint();
        updateActionAvailability();
        closeTaskDialogAfterReload = false;
    }

    private void loadSelectedTaskDetails(TaskRow row) {
        if (row == null) {
            taskDetailsText.set(TASK_DETAILS_PLACEHOLDER);
            return;
        }
        UUID documentId = row.documentId();
        WarehouseTaskKind kind = row.taskKind();
        long requestId = ++taskDetailLoadGeneration;
        taskDetailsText.set(formatTaskHeader(row, null));
        taskDetailLoading.set(true);
        backgroundExecutor.execute(
                () -> {
                    try {
                        TransferDocumentView document = warehouseApi.getTransferDocument(documentId);
                        ensureMaterialCache();
                        DetailPackage detail = loadDetailPackage(kind, document);
                        uiExecutor.accept(
                                () -> applyTaskDetailPackage(document, detail, row, requestId));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyTaskDetailError(row, ex, requestId));
                    }
                });
    }

    private void ensureMaterialCache() {
        if (!materialById.isEmpty()) {
            return;
        }
        for (MaterialReferenceView material : warehouseApi.listMaterialReferences()) {
            materialById.put(material.materialReferenceId(), material);
        }
    }

    private DetailPackage loadDetailPackage(WarehouseTaskKind kind, TransferDocumentView document) {
        return switch (kind) {
            case TRANSFER_PREPARATION -> {
                List<TransferDocumentSourceSuggestionLine> suggestions =
                        warehouseApi.suggestTransferDocumentSourceAllocations(document.documentId());
                List<StorageCellChoice> cells =
                        loadActiveCellChoices(document.sourceWarehouseId());
                yield new DetailPackage(suggestions, List.of(), cells);
            }
            case TRANSFER_RECEIPT -> {
                List<StorageCellChoice> cells =
                        loadActiveCellChoices(document.destinationWarehouseId());
                yield new DetailPackage(List.of(), List.of(), cells);
            }
            case RETURN_MATERIALS -> {
                List<TransferDocumentReturnPlanItem> plan =
                        warehouseApi.listTransferDocumentReturnPlan(document.documentId());
                List<StorageCellChoice> cells =
                        loadActiveCellChoices(document.sourceWarehouseId());
                yield new DetailPackage(List.of(), plan, cells);
            }
        };
    }

    private List<StorageCellChoice> loadActiveCellChoices(UUID warehouseId) {
        List<StorageCellChoice> choices = new ArrayList<>();
        for (StorageCellView cell : warehouseApi.listStorageCells(warehouseId)) {
            if (cell.active()) {
                choices.add(StorageCellChoice.from(cell));
            }
        }
        return choices;
    }

    private void applyTaskDetailPackage(
            TransferDocumentView document, DetailPackage detail, TaskRow row, long requestId) {
        if (requestId != taskDetailLoadGeneration || !Objects.equals(selectedTask.get(), row)) {
            return;
        }
        taskDetailLoading.set(false);
        loadedDocument = document;
        loadedReturnPlan = List.copyOf(detail.returnPlan());
        actionCellChoices.setAll(detail.cells());
        taskDetailsText.set(formatTaskHeader(row, document));
        List<ActionEditRow> rows =
                switch (row.taskKind()) {
                    case TRANSFER_PREPARATION -> buildSourceRows(detail.suggestions(), detail.cells());
                    case TRANSFER_RECEIPT -> buildReceiveRows(document);
                    case RETURN_MATERIALS -> buildReturnRows(detail.returnPlan(), detail.cells());
                };
        actionLines.setAll(rows);
        updateTransferActionsHint();
        updateActionAvailability();
        Runnable detailsLoaded = afterTaskDetailsLoaded;
        if (detailsLoaded != null) {
            detailsLoaded.run();
        }
    }

    private List<ActionEditRow> buildSourceRows(
            List<TransferDocumentSourceSuggestionLine> suggestions, List<StorageCellChoice> cells) {
        Map<UUID, StorageCellChoice> byId = indexCells(cells);
        List<ActionEditRow> rows = new ArrayList<>();
        for (TransferDocumentSourceSuggestionLine line : suggestions) {
            MaterialParts material = materialParts(line.materialReferenceId());
            if (line.suggestions().isEmpty()) {
                rows.add(
                        new SourceAllocationEditRow(
                                line.lineId(), material, line.requiredQuantity(), null, null));
                continue;
            }
            for (SourceCellSuggestion suggestion : line.suggestions()) {
                rows.add(
                        new SourceAllocationEditRow(
                                line.lineId(),
                                material,
                                line.requiredQuantity(),
                                byId.get(suggestion.storageCellId()),
                                suggestion.suggestedQuantity()));
            }
        }
        return rows;
    }

    private List<ActionEditRow> buildReceiveRows(TransferDocumentView document) {
        List<ActionEditRow> rows = new ArrayList<>();
        for (var line : document.lines()) {
            rows.add(
                    new ReceiveAllocationEditRow(
                            line.lineId(),
                            materialParts(line.materialReferenceId()),
                            line.quantity(),
                            null,
                            line.quantity()));
        }
        return rows;
    }

    private List<ActionEditRow> buildReturnRows(
            List<TransferDocumentReturnPlanItem> plan, List<StorageCellChoice> cells) {
        Map<UUID, StorageCellChoice> byId = indexCells(cells);
        List<ActionEditRow> rows = new ArrayList<>();
        for (TransferDocumentReturnPlanItem item : plan) {
            StorageCellChoice cell = byId.get(item.defaultReturnStorageCellId());
            if (cell == null) {
                cell =
                        new StorageCellChoice(
                                item.defaultReturnStorageCellId(),
                                loadedDocument == null
                                        ? UUID.randomUUID()
                                        : loadedDocument.sourceWarehouseId(),
                                item.defaultReturnStorageCellCode(),
                                true);
            }
            rows.add(
                    new ReturnAllocationEditRow(
                            item.lineId(),
                            materialParts(item.materialReferenceId()),
                            item.outstandingQuantity(),
                            item.defaultReturnStorageCellId(),
                            cell));
        }
        return rows;
    }

    private static Map<UUID, StorageCellChoice> indexCells(List<StorageCellChoice> cells) {
        Map<UUID, StorageCellChoice> byId = new LinkedHashMap<>();
        for (StorageCellChoice cell : cells) {
            byId.put(cell.id(), cell);
        }
        return byId;
    }

    private MaterialParts materialParts(UUID materialReferenceId) {
        MaterialReferenceView view = materialById.get(materialReferenceId);
        if (view == null) {
            return MaterialParts.unknown(materialReferenceId);
        }
        return MaterialParts.from(view);
    }

    static String formatMaterialDisplay(MaterialReferenceView view) {
        return formatMaterialDisplay(
                view.article(), view.name(), view.color(), view.size(), view.unitOfMeasure());
    }

    static String formatMaterialDisplay(
            String article, String name, String color, String size, String unitOfMeasure) {
        String description = joinParts(name, color, size, unitOfMeasure);
        String articleText = article == null ? "" : article;
        if (description.isBlank()) {
            return articleText;
        }
        return articleText + " — " + description;
    }

    private static String joinParts(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(" | ");
            }
            builder.append(part.trim());
        }
        return builder.toString();
    }

    private void applyTaskDetailError(TaskRow row, RuntimeException ex, long requestId) {
        if (requestId != taskDetailLoadGeneration || !Objects.equals(selectedTask.get(), row)) {
            return;
        }
        taskDetailLoading.set(false);
        loadedDocument = null;
        loadedReturnPlan = List.of();
        actionLines.clear();
        actionCellChoices.clear();
        taskDetailsText.set(
                formatTaskHeader(row, null)
                        + "\n(Не удалось загрузить документ: "
                        + WarehouseUiErrorMapper.text(ex)
                        + ")");
        updateActionAvailability();
    }

    private static String formatTaskHeader(TaskRow row, TransferDocumentView document) {
        StringBuilder builder = new StringBuilder();
        builder.append("Документ: ")
                .append(row.documentNumber())
                .append(" · ")
                .append(row.kindLabel())
                .append(" · ")
                .append(row.stateLabel())
                .append("\nОткуда → Куда: ")
                .append(row.routeLabel())
                .append("\nИсполнитель: ")
                .append(row.workerDisplay());
        if (document != null) {
            builder.append("\nСтрок: ")
                    .append(document.lines().size())
                    .append(" · ")
                    .append(documentStatusLabel(document.documentStatus()));
            if (document.title() != null && !document.title().isBlank()) {
                builder.append("\n").append(document.title().trim());
            }
        } else {
            builder.append("\nСтрок: ").append(row.lineCount());
        }
        return builder.toString();
    }

    static String documentStatusLabel(String documentStatus) {
        if (documentStatus == null || documentStatus.isBlank()) {
            return "";
        }
        return switch (documentStatus.trim()) {
            case "DRAFT" -> "Черновик";
            case "POSTED" -> "Проведён";
            case "CLOSED" -> "Закрыт";
            default -> documentStatus.trim();
        };
    }

    private void clearActionEditingState() {
        loadedDocument = null;
        loadedReturnPlan = List.of();
        actionLines.clear();
        actionCellChoices.clear();
    }

    private void attachRowValidationListeners(ActionEditRow row) {
        row.storageCellProperty().addListener((obs, o, n) -> updateActionAvailability());
        row.quantityTextProperty().addListener((obs, o, n) -> updateActionAvailability());
    }

    private void updateTransferActionsHint() {
        TaskRow row = selectedTask.get();
        if (row == null) {
            transferActionsHint.set("");
            return;
        }
        transferActionsHint.set(
                switch (row.taskKind()) {
                    case TRANSFER_PREPARATION -> HINT_PREPARATION;
                    case TRANSFER_RECEIPT -> HINT_RECEIPT;
                    case RETURN_MATERIALS -> HINT_RETURN;
                });
    }

    private void updateActionAvailability() {
        TaskRow row = selectedTask.get();
        boolean busy = commandInFlight.get() || loading.get() || taskDetailLoading.get();
        boolean inWork = row != null && row.taskState() == WarehouseTaskState.IN_WORK;
        boolean isNew = row != null && row.taskState() == WarehouseTaskState.NEW;
        boolean base =
                canTransfer.get()
                        && row != null
                        && inWork
                        && !busy
                        && loadedDocument != null;
        canTakeSelectedTaskInWork.set(
                canTransfer.get() && row != null && isNew && !busy);
        canSendSelectedTask.set(
                base
                        && row.taskKind() == WarehouseTaskKind.TRANSFER_PREPARATION
                        && localValidSend());
        canReceiveSelectedTask.set(
                base
                        && row.taskKind() == WarehouseTaskKind.TRANSFER_RECEIPT
                        && loadedDocument.operationalRevision() != null
                        && localValidReceive());
        canRejectSelectedTask.set(
                base
                        && row.taskKind() == WarehouseTaskKind.TRANSFER_RECEIPT
                        && loadedDocument.operationalRevision() != null);
        canReturnSelectedTask.set(
                base
                        && row.taskKind() == WarehouseTaskKind.RETURN_MATERIALS
                        && loadedDocument.operationalRevision() != null
                        && localValidReturn());
    }

    private boolean localValidSend() {
        if (actionLines.isEmpty()) {
            return false;
        }
        Map<UUID, BigDecimal> sums = new HashMap<>();
        Map<UUID, BigDecimal> required = new HashMap<>();
        for (ActionEditRow row : actionLines) {
            if (!(row instanceof SourceAllocationEditRow source)) {
                return false;
            }
            BigDecimal qty = parsePositiveQuantity(source.quantityTextProperty().get());
            if (qty == null || source.storageCellProperty().get() == null) {
                return false;
            }
            required.put(source.lineId(), source.requiredQuantity());
            sums.merge(source.lineId(), qty, BigDecimal::add);
        }
        for (Map.Entry<UUID, BigDecimal> entry : sums.entrySet()) {
            BigDecimal lineRequired = required.get(entry.getKey());
            if (lineRequired == null || entry.getValue().compareTo(lineRequired) > 0) {
                return false;
            }
        }
        return true;
    }

    private boolean localValidReceive() {
        if (actionLines.isEmpty()) {
            return false;
        }
        Map<UUID, BigDecimal> sums = new HashMap<>();
        Map<UUID, BigDecimal> sent = new HashMap<>();
        for (ActionEditRow row : actionLines) {
            if (!(row instanceof ReceiveAllocationEditRow receive)) {
                return false;
            }
            BigDecimal qty = parsePositiveQuantity(receive.quantityTextProperty().get());
            if (qty == null || receive.storageCellProperty().get() == null) {
                return false;
            }
            sent.put(receive.lineId(), receive.sentQuantity());
            sums.merge(receive.lineId(), qty, BigDecimal::add);
        }
        boolean anyPositive = false;
        for (Map.Entry<UUID, BigDecimal> entry : sums.entrySet()) {
            BigDecimal lineSent = sent.get(entry.getKey());
            if (lineSent == null || entry.getValue().compareTo(lineSent) > 0) {
                return false;
            }
            if (entry.getValue().compareTo(BigDecimal.ZERO) > 0) {
                anyPositive = true;
            }
        }
        return anyPositive;
    }

    private boolean localValidReturn() {
        if (actionLines.isEmpty()) {
            return false;
        }
        for (ActionEditRow row : actionLines) {
            if (!(row instanceof ReturnAllocationEditRow)) {
                return false;
            }
            if (row.storageCellProperty().get() == null) {
                return false;
            }
        }
        return true;
    }

    private List<TransferDocumentSourceAllocationInput> buildSendAllocations() {
        List<TransferDocumentSourceAllocationInput> allocations = new ArrayList<>();
        for (ActionEditRow row : actionLines) {
            if (!(row instanceof SourceAllocationEditRow source)) {
                continue;
            }
            StorageCellChoice cell = source.storageCellProperty().get();
            BigDecimal qty = parsePositiveQuantity(source.quantityTextProperty().get());
            if (cell == null || qty == null) {
                continue;
            }
            allocations.add(
                    new TransferDocumentSourceAllocationInput(
                            source.lineId(), cell.id(), qty));
        }
        return allocations;
    }

    private List<TransferDocumentDestinationAllocationInput> buildReceiveAllocations() {
        List<TransferDocumentDestinationAllocationInput> allocations = new ArrayList<>();
        for (ActionEditRow row : actionLines) {
            if (!(row instanceof ReceiveAllocationEditRow receive)) {
                continue;
            }
            StorageCellChoice cell = receive.storageCellProperty().get();
            BigDecimal qty = parsePositiveQuantity(receive.quantityTextProperty().get());
            if (cell == null || qty == null) {
                continue;
            }
            allocations.add(
                    new TransferDocumentDestinationAllocationInput(
                            receive.lineId(), cell.id(), qty));
        }
        return allocations;
    }

    private List<TransferDocumentReturnAllocationInput> buildReturnAllocations() {
        boolean allDefaults = true;
        for (ActionEditRow row : actionLines) {
            if (!(row instanceof ReturnAllocationEditRow ret)) {
                continue;
            }
            StorageCellChoice cell = ret.storageCellProperty().get();
            if (cell == null || !cell.id().equals(ret.defaultReturnStorageCellId())) {
                allDefaults = false;
                break;
            }
        }
        if (allDefaults) {
            return List.of();
        }
        List<TransferDocumentReturnAllocationInput> allocations = new ArrayList<>();
        int index = 0;
        for (TransferDocumentReturnPlanItem item : loadedReturnPlan) {
            StorageCellChoice chosen = null;
            if (index < actionLines.size()
                    && actionLines.get(index) instanceof ReturnAllocationEditRow ret) {
                chosen = ret.storageCellProperty().get();
            }
            index++;
            if (chosen == null) {
                continue;
            }
            allocations.add(
                    new TransferDocumentReturnAllocationInput(
                            item.lineId(), chosen.id(), item.outstandingQuantity()));
        }
        return allocations;
    }

    private void handleCommandFailure(RuntimeException ex) {
        commandInFlight.set(false);
        if (WarehouseUiErrorMapper.isStaleConflict(ex)) {
            pendingTaskErrorMessage = WarehouseUiErrorMapper.STALE_STATE;
            reloadTasks();
            return;
        }
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
    }

    static String formatSendSuccess(
            TransferDocumentSendResult result,
            List<TransferDocumentSourceAllocationInput> allocations) {
        BigDecimal sum =
                allocations.stream()
                        .map(TransferDocumentSourceAllocationInput::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        String message = "Передано: " + DecimalUiFormat.formatRu(sum);
        if (result.continuationDocumentId() != null) {
            message += " Создано дополнительное перемещение.";
        }
        return message;
    }

    static String formatReceiveSuccess(
            TransferDocumentReceiveResult result,
            List<TransferDocumentDestinationAllocationInput> allocations) {
        BigDecimal sum =
                allocations.stream()
                        .map(TransferDocumentDestinationAllocationInput::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        String message = "Принято: " + DecimalUiFormat.formatRu(sum);
        String settlement = result.settlementState() == null ? "" : result.settlementState();
        if (settlement.contains("RETURN") || result.continuationDocumentId() != null) {
            message += " Остаток ожидает возврата.";
        }
        return message;
    }

    private static BigDecimal parsePositiveQuantity(String text) {
        return DecimalQuantityParser.tryParsePositive(text);
    }

    public List<StockRow> selectedStockRows() {
        List<StockRow> selected = new ArrayList<>();
        for (StockRow row : tableRows) {
            if (row.isSelected()) {
                selected.add(row);
            }
        }
        return List.copyOf(selected);
    }

    public void clearStockSelection() {
        for (StockRow row : tableRows) {
            row.setSelected(false);
        }
        updateStockActionAvailability();
    }

    public void selectSingleStockRow(StockRow row) {
        Objects.requireNonNull(row, "row");
        for (StockRow candidate : tableRows) {
            candidate.setSelected(candidate == row);
        }
        updateStockActionAvailability();
    }

    public void toggleStockRowSelection(StockRow row, boolean selected) {
        Objects.requireNonNull(row, "row");
        row.setSelected(selected);
        updateStockActionAvailability();
    }

    public void onStockSelectionChanged() {
        updateStockActionAvailability();
    }

    public List<StockRow> requireSameWarehouseSelectionForMove() {
        List<StockRow> selected = selectedStockRows();
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Выберите строки остатков.");
        }
        UUID warehouseId = selected.get(0).warehouseId();
        for (StockRow row : selected) {
            if (!warehouseId.equals(row.warehouseId())) {
                throw new IllegalArgumentException(
                        "Для одного перемещения выберите материалы одного склада.");
            }
        }
        return selected;
    }

    public List<StorageCellChoice> listDestinationCells(UUID warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        return warehouseApi.listStorageCells(warehouseId).stream()
                .filter(StorageCellView::active)
                .map(StorageCellChoice::from)
                .toList();
    }

    public List<WarehouseChoice> listActiveWarehouseChoices() {
        List<WarehouseChoice> choices = new ArrayList<>();
        for (WarehouseView view : warehouseApi.listWarehouses()) {
            if (view.active()) {
                choices.add(WarehouseChoice.from(view));
            }
        }
        return choices;
    }

    /** Active warehouses where the current user is responsible — for receipt destination. */
    public List<WarehouseChoice> listResponsibleWarehouseChoices() {
        List<WarehouseChoice> choices = new ArrayList<>();
        for (WarehouseView view : warehouseApi.listMyWarehouses()) {
            choices.add(WarehouseChoice.from(view));
        }
        return choices;
    }

    /** @deprecated use {@link #listActiveWarehouseChoices()} — destination is not responsibility-filtered. */
    @Deprecated
    public List<WarehouseChoice> listAccessibleWarehouseChoices() {
        return listActiveWarehouseChoices();
    }

    public List<String> listUnitOfMeasures() {
        return warehouseApi.listUnitOfMeasures();
    }

    public void executeReceipt(List<WarehouseReceiptDialogSupport.ReceiptLineSubmission> lines) {
        Objects.requireNonNull(lines, "lines");
        if (!canReceipt.get() || commandInFlight.get() || lines.isEmpty()) {
            return;
        }
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        for (WarehouseReceiptDialogSupport.ReceiptLineSubmission line : lines) {
                            warehouseApi.executeWarehouseOperation(
                                    ExecuteOperationCommand.receipt(
                                            line.article(),
                                            line.name(),
                                            line.color(),
                                            line.size(),
                                            line.unitOfMeasure(),
                                            line.quantity(),
                                            line.warehouseId(),
                                            line.storageCellId()));
                        }
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    statusMessage.set("Поступление выполнено");
                                    reloadStockByCells("OP_RECEIPT");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public void executeSameWarehouseMove(
            List<StockMoveLine> lines, UUID destinationStorageCellId) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
        if (!canMove.get() || commandInFlight.get() || lines.isEmpty()) {
            return;
        }
        validateMoveLines(lines);
        UUID warehouseId = lines.get(0).warehouseId();
        for (StockMoveLine line : lines) {
            if (!warehouseId.equals(line.warehouseId())) {
                throw new IllegalArgumentException(
                        "Для одного перемещения выберите материалы одного склада.");
            }
            if (destinationStorageCellId.equals(line.sourceStorageCellId())) {
                throw new IllegalArgumentException(
                        "Ячейка назначения должна отличаться от ячейки источника.");
            }
        }
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        for (StockMoveLine line : lines) {
                            warehouseApi.executeWarehouseOperation(
                                    ExecuteOperationCommand.move(
                                            line.materialReferenceId(),
                                            line.quantity(),
                                            line.warehouseId(),
                                            line.sourceStorageCellId(),
                                            line.warehouseId(),
                                            destinationStorageCellId));
                        }
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    statusMessage.set("Перемещение выполнено");
                                    reloadStockByCells("OP_MOVE");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public void executeInterWarehouseMove(List<StockMoveLine> lines, UUID destinationWarehouseId) {
        Objects.requireNonNull(lines, "lines");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        if ((!canTransfer.get() && !canMove.get()) || commandInFlight.get() || lines.isEmpty()) {
            return;
        }
        if (!canTransfer.get()) {
            deny();
            return;
        }
        validateMoveLines(lines);
        UUID sourceWarehouseId = lines.get(0).warehouseId();
        for (StockMoveLine line : lines) {
            if (!sourceWarehouseId.equals(line.warehouseId())) {
                throw new IllegalArgumentException(
                        "Для одного перемещения выберите материалы одного склада.");
            }
        }
        if (sourceWarehouseId.equals(destinationWarehouseId)) {
            throw new IllegalArgumentException("Выберите другой склад назначения.");
        }
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        List<TransferDocumentLineInput> documentLines = new ArrayList<>();
                        int order = 1;
                        for (StockMoveLine line : lines) {
                            documentLines.add(
                                    new TransferDocumentLineInput(
                                            null,
                                            line.materialReferenceId(),
                                            line.quantity(),
                                            order++));
                        }
                        TransferDocumentView document =
                                warehouseApi.createTransferDocument(
                                        new CreateTransferDocumentCommand(
                                                sourceWarehouseId,
                                                destinationWarehouseId,
                                                documentLines));
                        if (document.lines().size() != lines.size()) {
                            throw new IllegalStateException(
                                    "Не удалось создать строки перемещения");
                        }
                        List<TransferDocumentSourceAllocationInput> allocations =
                                new ArrayList<>();
                        for (int i = 0; i < lines.size(); i++) {
                            allocations.add(
                                    new TransferDocumentSourceAllocationInput(
                                            document.lines().get(i).lineId(),
                                            lines.get(i).sourceStorageCellId(),
                                            lines.get(i).quantity()));
                        }
                        warehouseApi.sendTransferDocument(
                                new SendTransferDocumentCommand(
                                        document.documentId(),
                                        document.documentVersion(),
                                        document.payloadRevision(),
                                        allocations));
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    statusMessage.set("Перемещение отправлено");
                                    reloadStockByCells("OP_TRANSFER_SEND");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public void executeStockConsumption(List<StockMoveLine> lines) {
        Objects.requireNonNull(lines, "lines");
        if (!canConsumption.get() || commandInFlight.get() || lines.isEmpty()) {
            return;
        }
        validateMoveLines(lines);
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        for (StockMoveLine line : lines) {
                            warehouseApi.executeWarehouseOperation(
                                    ExecuteOperationCommand.consumption(
                                            line.materialReferenceId(),
                                            line.quantity(),
                                            line.warehouseId(),
                                            line.sourceStorageCellId()));
                        }
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    statusMessage.set("Списание выполнено");
                                    reloadStockByCells("OP_CONSUMPTION");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public void executeStockAdjustment(StockMoveLine line, BigDecimal quantityDelta) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(quantityDelta, "quantityDelta");
        if (!canAdjustment.get() || commandInFlight.get()) {
            return;
        }
        if (quantityDelta.signum() == 0) {
            throw new IllegalArgumentException("количество изменения не может быть равным 0");
        }
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        warehouseApi.executeWarehouseOperation(
                                ExecuteOperationCommand.adjustment(
                                        line.materialReferenceId(),
                                        quantityDelta,
                                        line.warehouseId(),
                                        line.sourceStorageCellId()));
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    statusMessage.set("Корректировка выполнена");
                                    reloadStockByCells("OP_ADJUSTMENT");
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public record StockMoveLine(
            UUID warehouseId,
            UUID sourceStorageCellId,
            UUID materialReferenceId,
            BigDecimal availableQuantity,
            BigDecimal quantity,
            String materialLabel,
            String cellCode) {

        public StockMoveLine {
            Objects.requireNonNull(warehouseId, "warehouseId");
            Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(availableQuantity, "availableQuantity");
            Objects.requireNonNull(quantity, "quantity");
        }

        public static StockMoveLine from(StockRow row, BigDecimal quantity) {
            Objects.requireNonNull(row, "row");
            return new StockMoveLine(
                    row.warehouseId(),
                    row.storageCellId(),
                    row.materialReferenceId(),
                    row.availableQuantity(),
                    quantity,
                    row.materialLabel(),
                    row.cellCode());
        }
    }

    private void updateStockActionAvailability() {
        List<StockRow> selected = selectedStockRows();
        boolean busy = commandInFlight.get() || loading.get();
        boolean hasSelection = !selected.isEmpty();
        canMoveSelectedStock.set(hasSelection && (canMove.get() || canTransfer.get()) && !busy);
        canConsumeSelectedStock.set(hasSelection && canConsumption.get() && !busy);
        canAdjustSelectedStock.set(selected.size() == 1 && canAdjustment.get() && !busy);
    }

    private void validateMoveLines(List<StockMoveLine> lines) {
        for (StockMoveLine line : lines) {
            if (line.quantity().signum() <= 0) {
                throw new IllegalArgumentException("количество должно быть больше 0");
            }
            if (line.quantity().compareTo(line.availableQuantity()) > 0) {
                throw new IllegalArgumentException(
                        "количество не может превышать доступный остаток");
            }
        }
    }

    private String sessionLoginMatching(UUID workingUserId) {
        if (authenticationService == null || workingUserId == null) {
            return null;
        }
        return authenticationService
                .currentSession()
                .filter(session -> workingUserId.equals(session.userId().value()))
                .map(session -> session.login().value())
                .orElse(null);
    }

    private boolean isAccessibleFilter(WarehouseFilterOption option) {
        if (option.isAll()) {
            return warehouseFilterOptions.stream().anyMatch(WarehouseFilterOption::isAll);
        }
        return accessibleWarehouseIds.contains(option.warehouseId());
    }

    private static WarehouseFilterOption resolveWarehouseFilter(
            List<WarehouseFilterOption> options,
            WarehouseFilterOption current,
            List<WarehouseView> mine) {
        if (options.isEmpty()) {
            return WarehouseFilterOption.all();
        }
        if (current != null) {
            for (WarehouseFilterOption option : options) {
                if (Objects.equals(option.warehouseId(), current.warehouseId())
                        && Objects.equals(option.label(), current.label())) {
                    return option;
                }
                if (current.isAll() && option.isAll()) {
                    return option;
                }
                if (!current.isAll()
                        && Objects.equals(option.warehouseId(), current.warehouseId())) {
                    return option;
                }
            }
        }
        if (mine.size() == 1) {
            return options.stream()
                    .filter(o -> !o.isAll())
                    .findFirst()
                    .orElse(options.get(0));
        }
        return options.stream()
                .filter(WarehouseFilterOption::isAll)
                .findFirst()
                .orElse(options.get(0));
    }

    /**
     * Marks stock cache dirty. Reloads immediately only when the Stocks tab is already active so
     * task mutations do not double-load while the user stays on Tasks.
     */
    private void invalidateStockAfterTaskMutation() {
        invalidateStockLoaded();
        if (selectedTab.get() == WorkspaceTab.STOCK) {
            reloadStockByCells("TASK_MUTATION");
        }
    }

    void invalidateStockLoaded() {
        stockLoadedForCurrentFilter = false;
    }

    boolean isStockLoadedForCurrentFilter() {
        return stockLoadedForCurrentFilter;
    }

    /**
     * Optional UI hooks so TableView scroll can be captured before {@code setAll} and restored
     * after — without the ViewModel depending on JavaFX TableView types.
     */
    public void setStockScrollHooks(IntSupplier anchorSupplier, IntConsumer restoreConsumer) {
        this.stockScrollAnchor = anchorSupplier;
        this.stockScrollRestorer = restoreConsumer;
    }

    private void setSelectedWarehouseFilterIdentity(WarehouseFilterOption option) {
        WarehouseFilterOption current = selectedWarehouseFilter.get();
        if (current == option) {
            return;
        }
        if (Objects.equals(current, option)) {
            selectedWarehouseFilter.set(null);
        }
        selectedWarehouseFilter.set(option);
    }

    private void setSelectedCellFilterIdentity(CellFilterOption option) {
        CellFilterOption current = selectedCellFilter.get();
        if (current == option) {
            return;
        }
        if (Objects.equals(current, option)) {
            selectedCellFilter.set(null);
        }
        selectedCellFilter.set(option);
    }

    private void setSelectedCellFilterToAllInListOrFresh() {
        CellFilterOption allInList =
                cellFilterOptions.stream()
                        .filter(CellFilterOption::isAll)
                        .findFirst()
                        .orElse(null);
        if (allInList != null) {
            setSelectedCellFilterIdentity(allInList);
            return;
        }
        CellFilterOption all = CellFilterOption.all();
        cellFilterOptions.setAll(all);
        setSelectedCellFilterIdentity(all);
    }

    private List<StockSelectionKey> captureSelectedStockKeys() {
        List<StockSelectionKey> keys = new ArrayList<>();
        for (StockRow row : tableRows) {
            if (row.isSelected()) {
                keys.add(
                        new StockSelectionKey(
                                row.warehouseId(), row.storageCellId(), row.materialReferenceId()));
            }
        }
        return keys;
    }

    private void restoreStockSelection(List<StockSelectionKey> previouslySelected) {
        for (StockRow row : tableRows) {
            row.setSelected(false);
        }
        if (previouslySelected.isEmpty()) {
            updateStockActionAvailability();
            return;
        }
        for (StockRow row : tableRows) {
            StockSelectionKey key =
                    new StockSelectionKey(
                            row.warehouseId(), row.storageCellId(), row.materialReferenceId());
            if (previouslySelected.contains(key)) {
                row.setSelected(true);
            }
        }
        updateStockActionAvailability();
    }

    private record StockSelectionKey(
            UUID warehouseId, UUID storageCellId, UUID materialReferenceId) {}

    private void reloadCellFilterOptionsThenStock() {
        if (!canView.get()) {
            deny();
            return;
        }
        WarehouseFilterOption filter = selectedWarehouseFilter.get();
        UUID warehouseId = filter == null ? null : filter.warehouseId();
        boolean allMode = filter != null && filter.isAll();
        long requestId = ++cellFilterLoadGeneration;
        backgroundExecutor.execute(
                () -> {
                    try {
                        List<WarehouseStockCellFilterOptionView> options =
                                warehouseApi.listStockCellFilterOptions(warehouseId);
                        uiExecutor.accept(
                                () -> applyCellFilterOptions(options, allMode, requestId));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyCellFilterLoadError(ex, requestId));
                    }
                });
    }

    private void applyCellFilterOptions(
            List<WarehouseStockCellFilterOptionView> options, boolean allMode, long requestId) {
        if (requestId != cellFilterLoadGeneration) {
            return;
        }
        List<CellFilterOption> mapped = new ArrayList<>();
        mapped.add(CellFilterOption.all());
        for (WarehouseStockCellFilterOptionView option : options) {
            mapped.add(CellFilterOption.from(option, allMode));
        }
        cellFilterOptions.setAll(mapped);
        CellFilterOption current = selectedCellFilter.get();
        boolean keep =
                current != null
                        && (current.isAll()
                                || mapped.stream()
                                        .anyMatch(
                                                o ->
                                                        Objects.equals(
                                                                o.storageCellId(),
                                                                current.storageCellId())));
        if (!keep || (current != null && current.isAll())) {
            setSelectedCellFilterIdentity(mapped.get(0));
        } else if (current != null) {
            CellFilterOption resolved =
                    mapped.stream()
                            .filter(o -> Objects.equals(o.storageCellId(), current.storageCellId()))
                            .findFirst()
                            .orElse(mapped.get(0));
            setSelectedCellFilterIdentity(resolved);
        }
        reloadStockByCells(resolveCellOptionsReloadReason());
    }

    private void applyCellFilterLoadError(RuntimeException ex, long requestId) {
        if (requestId != cellFilterLoadGeneration) {
            return;
        }
        CellFilterOption all = CellFilterOption.all();
        cellFilterOptions.setAll(all);
        setSelectedCellFilterIdentity(all);
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
        reloadStockByCells(resolveCellOptionsReloadReason());
    }

    private String resolveCellOptionsReloadReason() {
        if ("UNSPECIFIED".equals(pendingStockReloadReason)) {
            return "CELL_OPTIONS";
        }
        return pendingStockReloadReason;
    }

    private void reloadStockByCells(String reason) {
        pendingStockReloadReason = reason == null ? "UNSPECIFIED" : reason;
        if (!canView.get()) {
            deny();
            return;
        }
        WarehouseFilterOption filter = selectedWarehouseFilter.get();
        if (filter == null && warehouseFilterOptions.isEmpty()) {
            tableRows.clear();
            totalElements.set(0);
            updatePaginationFlags();
            stockLoadedForCurrentFilter = true;
            if (selectedTab.get() == WorkspaceTab.STOCK) {
                statusMessage.set(EMPTY_STOCK_MESSAGE);
            }
            return;
        }
        UUID warehouseId = filter == null ? null : filter.warehouseId();
        CellFilterOption cellFilter = selectedCellFilter.get();
        UUID storageCellId = cellFilter == null || cellFilter.isAll() ? null : cellFilter.storageCellId();
        long requestId = ++stockLoadGeneration;
        // Soft refresh: keep existing rows visible without a layout-shifting loading chrome.
        // Hard loading indicator only when the table is empty (initial / cleared).
        boolean showLoadingChrome = tableRows.isEmpty();
        if (showLoadingChrome) {
            loading.set(true);
        }
        errorMessage.set("");
        if (showLoadingChrome) {
            statusMessage.set("");
        }
        int rowsBefore = tableRows.size();
        StocksRefreshTrace.reloadRequested(
                pendingStockReloadReason,
                rowsBefore,
                tableRows,
                filterLabel(filter),
                cellFilterLabel(cellFilter));
        int page = pageIndex.get();
        String search = blankToNull(committedSearch);
        boolean cellSelected = storageCellId != null;
        backgroundExecutor.execute(
                () -> {
                    try {
                        WarehouseStockCellPage pageResult =
                                warehouseApi.listStockByCells(
                                        warehouseId, storageCellId, search, page, PAGE_SIZE);
                        uiExecutor.accept(
                                () ->
                                        applyStockPage(
                                                pageResult, requestId, search != null, cellSelected));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyStockLoadError(ex, requestId));
                    }
                });
    }

    private void applyStockPage(
            WarehouseStockCellPage pageResult,
            long requestId,
            boolean searchActive,
            boolean cellSelected) {
        if (requestId != stockLoadGeneration) {
            return;
        }
        if (pageResult.content().isEmpty() && pageIndex.get() > 0) {
            long total = pageResult.totalElements();
            int maxPage = total <= 0 ? 0 : (int) ((total - 1) / PAGE_SIZE);
            if (pageIndex.get() > maxPage) {
                pageIndex.set(maxPage);
                reloadStockByCells("PAGE_CLAMP");
                return;
            }
        }
        if (selectedTab.get() == WorkspaceTab.STOCK) {
            loading.set(false);
        }
        stockLoadedForCurrentFilter = true;
        totalElements.set(pageResult.totalElements());
        updatePaginationFlags();
        int rowsBefore = tableRows.size();
        Object itemsBefore = tableRows;
        List<StockSelectionKey> previouslySelected = captureSelectedStockKeys();
        int scrollAnchor =
                stockScrollAnchor == null ? -1 : stockScrollAnchor.getAsInt();
        List<StockRow> rows = new ArrayList<>();
        for (WarehouseStockCellLineView line : pageResult.content()) {
            rows.add(StockRow.from(line));
        }
        tableRows.setAll(rows);
        restoreStockSelection(previouslySelected);
        if (stockScrollRestorer != null && scrollAnchor >= 0) {
            stockScrollRestorer.accept(scrollAnchor);
        }
        StocksRefreshTrace.applyPage(
                pendingStockReloadReason,
                rowsBefore,
                tableRows.size(),
                itemsBefore,
                tableRows,
                previouslySelected.toString(),
                filterLabel(selectedWarehouseFilter.get()),
                cellFilterLabel(selectedCellFilter.get()),
                true);
        if (selectedTab.get() == WorkspaceTab.STOCK && pageResult.totalElements() == 0) {
            if (searchActive) {
                statusMessage.set(EMPTY_SEARCH_MESSAGE);
            } else if (cellSelected) {
                statusMessage.set(EMPTY_CELL_STOCK_MESSAGE);
            } else {
                statusMessage.set(EMPTY_STOCK_MESSAGE);
            }
        } else if (selectedTab.get() == WorkspaceTab.STOCK) {
            statusMessage.set("");
        }
    }

    private static String filterLabel(WarehouseFilterOption filter) {
        if (filter == null) {
            return "null";
        }
        return filter.isAll() ? "ALL" : String.valueOf(filter.warehouseId());
    }

    private static String cellFilterLabel(CellFilterOption filter) {
        if (filter == null) {
            return "null";
        }
        return filter.isAll() ? "ALL" : String.valueOf(filter.storageCellId());
    }

    private void applyStockLoadError(RuntimeException ex, long requestId) {
        if (requestId != stockLoadGeneration) {
            return;
        }
        if (selectedTab.get() == WorkspaceTab.STOCK) {
            loading.set(false);
        }
        stockLoadedForCurrentFilter = false;
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
        if (selectedTab.get() == WorkspaceTab.STOCK) {
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        }
        tableRows.clear();
        totalElements.set(0);
        updatePaginationFlags();
    }

    private void updatePaginationFlags() {
        canGoPrevious.set(pageIndex.get() > 0);
        long total = totalElements.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / PAGE_SIZE);
        canGoNext.set(pageIndex.get() < maxPage);
    }

    private void updateHistoryPaginationFlags() {
        historyCanGoPrevious.set(historyPageIndex.get() > 0);
        long total = historyTotalElements.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / HISTORY_PAGE_SIZE);
        historyCanGoNext.set(historyPageIndex.get() < maxPage);
    }

    private void reloadHistory() {
        reloadHistory("HISTORY_RELOAD");
    }

    private void reloadHistory(String reason) {
        if (!canView.get()) {
            deny();
            return;
        }
        LocalDate from = historyFromDate.get();
        LocalDate to = historyToDate.get();
        if (from == null || to == null) {
            errorMessage.set(WarehouseUiErrorMapper.VALIDATION);
            statusMessage.set("");
            historyRows.clear();
            historyTotalElements.set(0);
            updateHistoryPaginationFlags();
            return;
        }
        if (from.isAfter(to)) {
            errorMessage.set(WarehouseUiErrorMapper.VALIDATION);
            statusMessage.set("");
            historyRows.clear();
            historyTotalElements.set(0);
            updateHistoryPaginationFlags();
            return;
        }
        WarehouseFilterOption filter = selectedWarehouseFilter.get();
        UUID warehouseId = filter == null ? null : filter.warehouseId();
        boolean allMode = filter != null && filter.isAll();
        ZoneId zone = ZoneId.systemDefault();
        Instant fromInclusive = from.atStartOfDay(zone).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(zone).toInstant();
        String materialSearch = blankToNull(committedHistorySearch);
        HistoryOperationOption operation = selectedHistoryOperation.get();
        String operationType = operation == null ? null : operation.operationType();
        WarehouseHistoryFilter historyFilter =
                new WarehouseHistoryFilter(fromInclusive, toExclusive, materialSearch, operationType);
        long requestId = ++historyLoadGeneration;
        int rowsBefore = historyRows.size();
        Object itemsIdentity = historyRows;
        HistoryRefreshTrace.reloadRequested(
                reason, rowsBefore, itemsIdentity, String.valueOf(warehouseId), operationType);
        loading.set(true);
        errorMessage.set("");
        statusMessage.set("");
        int page = historyPageIndex.get();
        boolean filtersActive = materialSearch != null || operationType != null;
        backgroundExecutor.execute(
                () -> {
                    try {
                        WarehouseHistoryPage pageResult =
                                warehouseApi.listHistory(
                                        warehouseId, historyFilter, page, HISTORY_PAGE_SIZE);
                        uiExecutor.accept(
                                () ->
                                        applyHistoryPage(
                                                pageResult,
                                                requestId,
                                                allMode,
                                                filtersActive,
                                                reason));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyHistoryLoadError(ex, requestId));
                    }
                });
    }

    private void applyHistoryPage(
            WarehouseHistoryPage pageResult,
            long requestId,
            boolean allMode,
            boolean filtersActive,
            String reason) {
        if (requestId != historyLoadGeneration) {
            return;
        }
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            loading.set(false);
        }
        historyTotalElements.set(pageResult.totalElements());
        updateHistoryPaginationFlags();
        int rowsBefore = historyRows.size();
        Object itemsBefore = historyRows;
        List<HistoryRow> rows = new ArrayList<>();
        for (WarehouseHistoryEntryView entry : pageResult.content()) {
            rows.add(HistoryRow.from(entry, allMode));
        }
        historyRows.setAll(rows);
        HistoryRefreshTrace.applyPage(
                reason,
                rowsBefore,
                historyRows.size(),
                itemsBefore,
                historyRows,
                selectedTab.get() == WorkspaceTab.HISTORY);
        if (selectedTab.get() == WorkspaceTab.HISTORY && pageResult.totalElements() == 0) {
            statusMessage.set(
                    filtersActive ? EMPTY_HISTORY_FILTER_MESSAGE : EMPTY_HISTORY_PERIOD_MESSAGE);
        } else if (selectedTab.get() == WorkspaceTab.HISTORY) {
            statusMessage.set("");
        }
    }

    private void applyHistoryLoadError(RuntimeException ex, long requestId) {
        if (requestId != historyLoadGeneration) {
            return;
        }
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            loading.set(false);
        }
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        }
        historyRows.clear();
        historyTotalElements.set(0);
        updateHistoryPaginationFlags();
    }

    static String formatHistoryQuantity(String operationType, BigDecimal quantity) {
        Objects.requireNonNull(quantity, "quantity");
        if ("MOVE".equals(operationType)) {
            return DecimalUiFormat.formatRu(quantity.abs());
        }
        int sign = quantity.signum();
        if (sign > 0) {
            return "+" + DecimalUiFormat.formatRu(quantity);
        }
        if (sign < 0) {
            return "-" + DecimalUiFormat.formatRu(quantity.abs());
        }
        return "0";
    }

    static String formatHistoryLocation(
            String warehouseName, String cellCode, boolean allWarehousesMode) {
        boolean hasWarehouse = warehouseName != null && !warehouseName.isBlank();
        boolean hasCell = cellCode != null && !cellCode.isBlank();
        if (!hasWarehouse && !hasCell) {
            return "—";
        }
        if (allWarehousesMode) {
            String warehouse = hasWarehouse ? warehouseName.trim() : "—";
            String cell = hasCell ? cellCode.trim() : "—";
            return warehouse + " / " + cell;
        }
        if (hasWarehouse && hasCell) {
            return warehouseName.trim() + " / " + cellCode.trim();
        }
        if (hasCell) {
            return cellCode.trim();
        }
        return warehouseName.trim();
    }

    private static String blankDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private void deny() {
        errorMessage.set(WarehouseUiErrorMapper.ACCESS_DENIED);
        statusMessage.set("");
    }

    private boolean has(String permission) {
        return authorizationService.hasPermission(PermissionId.of(permission));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DetailPackage(
            List<TransferDocumentSourceSuggestionLine> suggestions,
            List<TransferDocumentReturnPlanItem> returnPlan,
            List<StorageCellChoice> cells) {}
}
