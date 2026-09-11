package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.order.worklist.DateTimePresentation;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.SourceCellSuggestion;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentDestinationAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentRejectResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnPlanItem;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnResult;
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
        private final String materialText;
        private final String quantityText;
        private final String sourceText;
        private final String destinationText;
        private final String documentText;
        private final String actorText;

        HistoryRow(
                UUID entryId,
                String occurredAtText,
                String operationLabel,
                String materialText,
                String quantityText,
                String sourceText,
                String destinationText,
                String documentText,
                String actorText) {
            this.entryId = Objects.requireNonNull(entryId, "entryId");
            this.occurredAtText = occurredAtText;
            this.operationLabel = operationLabel;
            this.materialText = materialText;
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
                    formatHistoryMaterial(view),
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

        public String materialText() {
            return materialText;
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
        private final String quantityText;

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
            this.quantityText = DecimalUiFormat.formatRu(quantity);
        }

        static StockRow from(WarehouseStockCellLineView view) {
            String warehouseLabel = view.warehouseCode() + " — " + view.warehouseName();
            return new StockRow(
                    view.warehouseId(),
                    view.storageCellId(),
                    view.materialReferenceId(),
                    warehouseLabel,
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

        public String quantityText() {
            return quantityText;
        }
    }

    public static final class TaskRow {

        private final UUID documentId;
        private final WarehouseTaskKind taskKind;
        private final WarehouseTaskState taskState;
        private final UUID workingUserId;
        private final String documentNumber;
        private final String kindLabel;
        private final String stateLabel;
        private final String routeLabel;
        private final int lineCount;
        private final String workerDisplay;

        TaskRow(WarehouseTaskView view) {
            this.documentId = view.documentId();
            this.taskKind = view.taskKind();
            this.taskState = view.taskState();
            this.workingUserId = view.workingUserId();
            this.documentNumber = view.documentNumber();
            this.kindLabel = kindLabel(view.taskKind());
            this.stateLabel = stateLabel(view.taskState());
            this.routeLabel = warehouseRouteLabel(view);
            this.lineCount = view.lineCount();
            this.workerDisplay = formatWorkerDisplay(view.taskState(), view.workingUserId());
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

        public String kindLabel() {
            return kindLabel;
        }

        public String stateLabel() {
            return stateLabel;
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

        private static String warehouseRouteLabel(WarehouseTaskView view) {
            String source = formatWarehouse(view.sourceWarehouseCode(), view.sourceWarehouseName());
            String dest =
                    formatWarehouse(view.destinationWarehouseCode(), view.destinationWarehouseName());
            return source + " → " + dest;
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

        private static String formatWorkerDisplay(WarehouseTaskState state, UUID workingUserId) {
            if (state == WarehouseTaskState.IN_WORK) {
                return workingUserId == null ? "В работе" : "В работе · " + workingUserId;
            }
            return "—";
        }
    }

    public abstract static class ActionEditRow {

        private final UUID lineId;
        private final String materialLabel;
        private final ObjectProperty<StorageCellChoice> storageCell = new SimpleObjectProperty<>();

        ActionEditRow(UUID lineId, String materialLabel, StorageCellChoice initialCell) {
            this.lineId = Objects.requireNonNull(lineId, "lineId");
            this.materialLabel = Objects.requireNonNull(materialLabel, "materialLabel");
            this.storageCell.set(initialCell);
        }

        public UUID lineId() {
            return lineId;
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

    public static final class SourceAllocationEditRow extends ActionEditRow {

        private final BigDecimal requiredQuantity;
        private final StringProperty quantityText = new SimpleStringProperty("");

        SourceAllocationEditRow(
                UUID lineId,
                String materialLabel,
                BigDecimal requiredQuantity,
                StorageCellChoice cell,
                BigDecimal quantity) {
            super(lineId, materialLabel, cell);
            this.requiredQuantity = Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            if (quantity != null) {
                this.quantityText.set(quantity.toPlainString());
            }
        }

        public BigDecimal requiredQuantity() {
            return requiredQuantity;
        }

        @Override
        public String referenceQuantityText() {
            return requiredQuantity.toPlainString();
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
                String materialLabel,
                BigDecimal sentQuantity,
                StorageCellChoice cell,
                BigDecimal acceptQuantity) {
            super(lineId, materialLabel, cell);
            this.sentQuantity = Objects.requireNonNull(sentQuantity, "sentQuantity");
            if (acceptQuantity != null) {
                this.quantityText.set(acceptQuantity.toPlainString());
            }
        }

        public BigDecimal sentQuantity() {
            return sentQuantity;
        }

        @Override
        public String referenceQuantityText() {
            return sentQuantity.toPlainString();
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
                String materialLabel,
                BigDecimal outstandingQuantity,
                UUID defaultReturnStorageCellId,
                StorageCellChoice cell) {
            super(lineId, materialLabel, cell);
            this.outstandingQuantity =
                    Objects.requireNonNull(outstandingQuantity, "outstandingQuantity");
            this.defaultReturnStorageCellId =
                    Objects.requireNonNull(defaultReturnStorageCellId, "defaultReturnStorageCellId");
            this.quantityText = new FixedQuantityProperty(outstandingQuantity.toPlainString());
        }

        public BigDecimal outstandingQuantity() {
            return outstandingQuantity;
        }

        public UUID defaultReturnStorageCellId() {
            return defaultReturnStorageCellId;
        }

        @Override
        public String referenceQuantityText() {
            return outstandingQuantity.toPlainString();
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
    private final BooleanProperty commandInFlight = new SimpleBooleanProperty(false);
    private final BooleanProperty canView = new SimpleBooleanProperty(false);
    private final BooleanProperty canTransfer = new SimpleBooleanProperty(false);
    private final BooleanProperty canTakeSelectedTaskInWork = new SimpleBooleanProperty(false);
    private final BooleanProperty canSendSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty canReceiveSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty canRejectSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty canReturnSelectedTask = new SimpleBooleanProperty(false);
    private final BooleanProperty showWarehouseColumn = new SimpleBooleanProperty(false);

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

    private TransferDocumentView loadedDocument;
    private List<TransferDocumentReturnPlanItem> loadedReturnPlan = List.of();
    private String pendingTaskStatusMessage;
    private String pendingTaskErrorMessage;
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
        this(
                warehouseApi,
                authorizationService,
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
        this.warehouseApi = Objects.requireNonNull(warehouseApi, "warehouseApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        actionLines.addListener(actionLinesListener);
        commandInFlight.addListener((obs, o, n) -> updateActionAvailability());
        loading.addListener((obs, o, n) -> updateActionAvailability());
        canTransfer.addListener((obs, o, n) -> updateActionAvailability());
        selectedCellFilter.set(CellFilterOption.all());
        cellFilterOptions.setAll(CellFilterOption.all());
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
        updateActionAvailability();
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
            reloadHistory();
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
        selectedWarehouseFilter.set(option);
        showWarehouseColumn.set(option != null && option.isAll());
        selectedCellFilter.set(CellFilterOption.all());
        stockLoadedForCurrentFilter = false;
        WorkspaceTab tab = selectedTab.get();
        if (tab == WorkspaceTab.TASKS) {
            reloadTasks();
        } else if (tab == WorkspaceTab.STOCK) {
            reloadCellFilterOptionsThenStock();
        } else if (tab == WorkspaceTab.HISTORY) {
            reloadHistory();
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
            reloadStockByCells();
        }
    }

    public void selectTask(TaskRow row) {
        selectedTask.set(row);
        clearActionEditingState();
        updateActionAvailability();
        updateTransferActionsHint();
        loadSelectedTaskDetails(row);
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
                                    reloadTasks();
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                    if (WarehouseUiErrorMapper.isStaleConflict(ex)) {
                                        reloadTasks();
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
                                    reloadTasks();
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
                                    reloadTasks();
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
                                    reloadTasks();
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
                        TransferDocumentReturnResult result =
                                warehouseApi.returnTransferMaterials(
                                        new ReturnTransferMaterialsCommand(
                                                documentId, operationalRevision, allocations));
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    pendingTaskStatusMessage =
                                            "Возврат выполнен"
                                                    + (result.documentStatus() == null
                                                                    || result.documentStatus()
                                                                            .isBlank()
                                                            ? ""
                                                            : ": " + result.documentStatus());
                                    reloadTasks();
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
                        template.materialLabel(),
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
                        template.materialLabel(),
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
        reloadStockByCells();
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
        reloadHistory();
    }

    public void setHistoryFromDate(LocalDate date) {
        if (Objects.equals(historyFromDate.get(), date)) {
            return;
        }
        historyFromDate.set(date);
        historyPageIndex.set(0);
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            reloadHistory();
        }
    }

    public void setHistoryToDate(LocalDate date) {
        if (Objects.equals(historyToDate.get(), date)) {
            return;
        }
        historyToDate.set(date);
        historyPageIndex.set(0);
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            reloadHistory();
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
            reloadHistory();
        }
    }

    public void refreshHistory() {
        if (selectedTab.get() != WorkspaceTab.HISTORY) {
            return;
        }
        reloadHistory();
    }

    public void nextPage() {
        if (selectedTab.get() != WorkspaceTab.STOCK) {
            return;
        }
        long total = totalElements.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / PAGE_SIZE);
        if (pageIndex.get() < maxPage) {
            pageIndex.set(pageIndex.get() + 1);
            reloadStockByCells();
        }
    }

    public void previousPage() {
        if (selectedTab.get() != WorkspaceTab.STOCK) {
            return;
        }
        if (pageIndex.get() > 0) {
            pageIndex.set(pageIndex.get() - 1);
            reloadStockByCells();
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
            reloadHistory();
        }
    }

    public void previousHistoryPage() {
        if (selectedTab.get() != WorkspaceTab.HISTORY) {
            return;
        }
        if (historyPageIndex.get() > 0) {
            historyPageIndex.set(historyPageIndex.get() - 1);
            reloadHistory();
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

    public BooleanProperty commandInFlightProperty() {
        return commandInFlight;
    }

    public BooleanProperty canViewProperty() {
        return canView;
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
            if (current == null || !isAccessibleFilter(current)) {
                if (mine.size() == 1) {
                    selectedWarehouseFilter.set(WarehouseFilterOption.from(WarehouseChoice.from(mine.get(0))));
                } else if (mine.size() > 1) {
                    selectedWarehouseFilter.set(WarehouseFilterOption.all());
                } else {
                    selectedWarehouseFilter.set(null);
                }
            }
            showWarehouseColumn.set(
                    selectedWarehouseFilter.get() != null && selectedWarehouseFilter.get().isAll());
            if (selectedCellFilter.get() == null) {
                selectedCellFilter.set(CellFilterOption.all());
            }
            stockLoadedForCurrentFilter = false;
            if (selectedTab.get() == WorkspaceTab.STOCK) {
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
            reloadCellFilterOptionsThenStock();
        }
    }

    private void reloadTasks() {
        if (!canView.get()) {
            deny();
            return;
        }
        WarehouseFilterOption filter = selectedWarehouseFilter.get();
        UUID warehouseId = filter == null ? null : filter.warehouseId();
        long requestId = ++taskLoadGeneration;
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
                        uiExecutor.accept(() -> applyTaskList(tasks, requestId));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyTaskLoadError(ex, requestId));
                    }
                });
    }

    private void applyTaskList(List<WarehouseTaskView> tasks, long requestId) {
        if (requestId != taskLoadGeneration) {
            return;
        }
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            loading.set(false);
        }
        UUID previousSelection = selectedTask.get() == null ? null : selectedTask.get().documentId();
        List<TaskRow> rows = new ArrayList<>();
        for (WarehouseTaskView task : tasks) {
            rows.add(new TaskRow(task));
        }
        taskRows.setAll(rows);
        TaskRow restored =
                previousSelection == null
                        ? null
                        : rows.stream()
                                .filter(r -> previousSelection.equals(r.documentId()))
                                .findFirst()
                                .orElse(null);
        selectedTask.set(restored);
        if (restored != null) {
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
    }

    private void applyTaskLoadError(RuntimeException ex, long requestId) {
        if (requestId != taskLoadGeneration) {
            return;
        }
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            loading.set(false);
        }
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
        if (selectedTab.get() == WorkspaceTab.TASKS) {
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        }
        taskRows.clear();
        selectedTask.set(null);
        clearActionEditingState();
        taskDetailsText.set(TASK_DETAILS_PLACEHOLDER);
        updateTransferActionsHint();
        updateActionAvailability();
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
        loading.set(true);
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
        loading.set(false);
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
    }

    private List<ActionEditRow> buildSourceRows(
            List<TransferDocumentSourceSuggestionLine> suggestions, List<StorageCellChoice> cells) {
        Map<UUID, StorageCellChoice> byId = indexCells(cells);
        List<ActionEditRow> rows = new ArrayList<>();
        for (TransferDocumentSourceSuggestionLine line : suggestions) {
            String label = materialLabel(line.materialReferenceId());
            if (line.suggestions().isEmpty()) {
                rows.add(
                        new SourceAllocationEditRow(
                                line.lineId(), label, line.requiredQuantity(), null, null));
                continue;
            }
            for (SourceCellSuggestion suggestion : line.suggestions()) {
                rows.add(
                        new SourceAllocationEditRow(
                                line.lineId(),
                                label,
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
                            materialLabel(line.materialReferenceId()),
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
                            materialLabel(item.materialReferenceId()),
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

    private String materialLabel(UUID materialReferenceId) {
        MaterialReferenceView view = materialById.get(materialReferenceId);
        if (view == null) {
            return materialReferenceId.toString();
        }
        return formatMaterialDisplay(view);
    }

    static String formatMaterialDisplay(MaterialReferenceView view) {
        String description =
                joinParts(view.name(), view.color(), view.size(), view.unitOfMeasure());
        if (description.isBlank()) {
            return view.article();
        }
        return view.article() + " — " + description;
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
        loading.set(false);
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
        boolean base =
                canTransfer.get()
                        && row != null
                        && !commandInFlight.get()
                        && !loading.get()
                        && loadedDocument != null;
        canTakeSelectedTaskInWork.set(
                canTransfer.get() && row != null && !commandInFlight.get() && !loading.get());
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

    private static String formatSendSuccess(
            TransferDocumentSendResult result,
            List<TransferDocumentSourceAllocationInput> allocations) {
        BigDecimal sum =
                allocations.stream()
                        .map(TransferDocumentSourceAllocationInput::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        String message = "Передано: " + sum.toPlainString();
        if (result.continuationDocumentId() != null) {
            message += " Создано дополнительное перемещение.";
        }
        return message;
    }

    private static String formatReceiveSuccess(
            TransferDocumentReceiveResult result,
            List<TransferDocumentDestinationAllocationInput> allocations) {
        BigDecimal sum =
                allocations.stream()
                        .map(TransferDocumentDestinationAllocationInput::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        String message = "Принято: " + sum.toPlainString();
        String settlement = result.settlementState() == null ? "" : result.settlementState();
        if (settlement.contains("RETURN") || result.continuationDocumentId() != null) {
            message += " Остаток ожидает возврата.";
        }
        return message;
    }

    private static BigDecimal parsePositiveQuantity(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(text.trim());
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isAccessibleFilter(WarehouseFilterOption option) {
        if (option.isAll()) {
            return warehouseFilterOptions.stream().anyMatch(WarehouseFilterOption::isAll);
        }
        return accessibleWarehouseIds.contains(option.warehouseId());
    }

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
        if (!keep) {
            selectedCellFilter.set(CellFilterOption.all());
        } else if (current != null && current.isAll()) {
            selectedCellFilter.set(CellFilterOption.all());
        } else if (current != null) {
            selectedCellFilter.set(
                    mapped.stream()
                            .filter(o -> Objects.equals(o.storageCellId(), current.storageCellId()))
                            .findFirst()
                            .orElse(CellFilterOption.all()));
        }
        reloadStockByCells();
    }

    private void applyCellFilterLoadError(RuntimeException ex, long requestId) {
        if (requestId != cellFilterLoadGeneration) {
            return;
        }
        cellFilterOptions.setAll(CellFilterOption.all());
        selectedCellFilter.set(CellFilterOption.all());
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
        reloadStockByCells();
    }

    private void reloadStockByCells() {
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
        loading.set(true);
        errorMessage.set("");
        statusMessage.set("");
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
        if (selectedTab.get() == WorkspaceTab.STOCK) {
            loading.set(false);
        }
        stockLoadedForCurrentFilter = true;
        totalElements.set(pageResult.totalElements());
        updatePaginationFlags();
        List<StockRow> rows = new ArrayList<>();
        for (WarehouseStockCellLineView line : pageResult.content()) {
            rows.add(StockRow.from(line));
        }
        tableRows.setAll(rows);
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
                                () -> applyHistoryPage(pageResult, requestId, allMode, filtersActive));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyHistoryLoadError(ex, requestId));
                    }
                });
    }

    private void applyHistoryPage(
            WarehouseHistoryPage pageResult,
            long requestId,
            boolean allMode,
            boolean filtersActive) {
        if (requestId != historyLoadGeneration) {
            return;
        }
        if (selectedTab.get() == WorkspaceTab.HISTORY) {
            loading.set(false);
        }
        historyTotalElements.set(pageResult.totalElements());
        updateHistoryPaginationFlags();
        List<HistoryRow> rows = new ArrayList<>();
        for (WarehouseHistoryEntryView entry : pageResult.content()) {
            rows.add(HistoryRow.from(entry, allMode));
        }
        historyRows.setAll(rows);
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

    static String formatHistoryMaterial(WarehouseHistoryEntryView view) {
        String article = view.materialArticle() == null ? "" : view.materialArticle().trim();
        String name = view.materialName() == null ? "" : view.materialName().trim();
        String unit = view.unitOfMeasure() == null ? "" : view.unitOfMeasure().trim();
        StringBuilder builder = new StringBuilder();
        if (!article.isEmpty()) {
            builder.append(article);
        }
        if (!name.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(" — ");
            }
            builder.append(name);
        }
        if (!unit.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(" (");
                builder.append(unit);
                builder.append(')');
            } else {
                builder.append(unit);
            }
        }
        return builder.toString();
    }

    static String formatHistoryQuantity(String operationType, BigDecimal quantity) {
        Objects.requireNonNull(quantity, "quantity");
        if ("MOVE".equals(operationType)) {
            return quantity.abs().toPlainString();
        }
        int sign = quantity.signum();
        if (sign > 0) {
            return "+" + quantity.toPlainString();
        }
        if (sign < 0) {
            return quantity.toPlainString();
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
