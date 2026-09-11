package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.WarehouseMaterialStockDetailsView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockCellView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockPage;
import com.tmp.warehouse.api.WarehouseApi.WarehouseStockSummaryView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
import javafx.collections.ObservableList;

/**
 * Modern warehouse workspace (Остатки tab). Reads via {@link WarehouseApi} only; no stock mutations.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class WarehouseWorkspaceViewModel {

    static final int PAGE_SIZE = WarehouseApi.STOCK_SUMMARY_DEFAULT_PAGE_SIZE;

    private static final String EMPTY_STOCK_MESSAGE = "На выбранном складе нет доступных остатков";
    private static final String EMPTY_SEARCH_MESSAGE = "По вашему запросу ничего не найдено";

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

    public abstract static class StockTableRow {

        private StockTableRow() {}
    }

    public static final class SummaryRow extends StockTableRow {

        private final UUID warehouseId;
        private final UUID materialReferenceId;
        private final String warehouseLabel;
        private final String article;
        private final String name;
        private final String color;
        private final String size;
        private final String unitOfMeasure;
        private final String quantityText;
        private final BooleanProperty expanded = new SimpleBooleanProperty(false);
        private final BooleanProperty expanding = new SimpleBooleanProperty(false);

        private SummaryRow(
                UUID warehouseId,
                UUID materialReferenceId,
                String warehouseLabel,
                String article,
                String name,
                String color,
                String size,
                String unitOfMeasure,
                BigDecimal quantity) {
            this.warehouseId = Objects.requireNonNull(warehouseId, "warehouseId");
            this.materialReferenceId = Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            this.warehouseLabel = warehouseLabel == null ? "" : warehouseLabel;
            this.article = article;
            this.name = name;
            this.color = color;
            this.size = size;
            this.unitOfMeasure = unitOfMeasure;
            this.quantityText = quantity.toPlainString();
        }

        static SummaryRow from(WarehouseStockSummaryView view, String warehouseLabel) {
            return new SummaryRow(
                    view.warehouseId(),
                    view.materialReferenceId(),
                    warehouseLabel,
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

        public UUID materialReferenceId() {
            return materialReferenceId;
        }

        public String warehouseLabel() {
            return warehouseLabel;
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

        public BooleanProperty expandedProperty() {
            return expanded;
        }

        public BooleanProperty expandingProperty() {
            return expanding;
        }

        String expandKey() {
            return warehouseId + ":" + materialReferenceId;
        }
    }

    public static final class CellDetailRow extends StockTableRow {

        private final String cellCode;
        private final String quantityText;

        CellDetailRow(String cellCode, BigDecimal quantity) {
            this.cellCode = Objects.requireNonNull(cellCode, "cellCode");
            this.quantityText = quantity.toPlainString();
        }

        public String cellCode() {
            return cellCode;
        }

        public String quantityText() {
            return quantityText;
        }

        public String indentedArticle() {
            return "    Ячейка: " + cellCode;
        }
    }

    private final WarehouseApi warehouseApi;
    private final AuthorizationService authorizationService;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> uiExecutor;

    private final ObjectProperty<WorkspaceTab> selectedTab =
            new SimpleObjectProperty<>(WorkspaceTab.STOCK);
    private final StringProperty title = new SimpleStringProperty("Склад");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty canView = new SimpleBooleanProperty(false);
    private final BooleanProperty showWarehouseColumn = new SimpleBooleanProperty(false);

    private final ObservableList<WarehouseFilterOption> warehouseFilterOptions =
            FXCollections.observableArrayList();
    private final ObjectProperty<WarehouseFilterOption> selectedWarehouseFilter =
            new SimpleObjectProperty<>();
    private final StringProperty searchInput = new SimpleStringProperty("");
    private final ObservableList<StockTableRow> tableRows = FXCollections.observableArrayList();

    private final IntegerProperty pageIndex = new SimpleIntegerProperty(0);
    private final LongProperty totalElements = new SimpleLongProperty(0);
    private final BooleanProperty canGoPrevious = new SimpleBooleanProperty(false);
    private final BooleanProperty canGoNext = new SimpleBooleanProperty(false);

    private final Set<UUID> accessibleWarehouseIds = new HashSet<>();
    private final Map<UUID, String> warehouseLabels = new HashMap<>();
    private String committedSearch = "";
    private long stockLoadGeneration;
    private long expandRequestCounter;
    private final Map<String, Long> expandRequestIds = new HashMap<>();

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
        refreshPermissions();
        updatePaginationFlags();
    }

    public void refreshPermissions() {
        canView.set(has(UiShellScreens.WAREHOUSE_VIEW_PERMISSION));
    }

    public void onScreenOpened() {
        refreshPermissions();
        if (!canView.get()) {
            deny();
            return;
        }
        loadWarehouseFiltersAndStock();
    }

    public void selectTab(WorkspaceTab tab) {
        Objects.requireNonNull(tab, "tab");
        selectedTab.set(tab);
    }

    public void selectWarehouseFilter(WarehouseFilterOption option) {
        if (option != null && !option.isAll() && !accessibleWarehouseIds.contains(option.warehouseId())) {
            return;
        }
        WarehouseFilterOption current = selectedWarehouseFilter.get();
        if (Objects.equals(current, option)) {
            return;
        }
        clearExpandedRows();
        pageIndex.set(0);
        selectedWarehouseFilter.set(option);
        showWarehouseColumn.set(option != null && option.isAll());
        reloadStockSummaries();
    }

    public void commitSearch() {
        committedSearch = blankToNull(searchInput.get()) == null ? "" : searchInput.get().trim();
        clearExpandedRows();
        pageIndex.set(0);
        reloadStockSummaries();
    }

    public void nextPage() {
        long total = totalElements.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / PAGE_SIZE);
        if (pageIndex.get() < maxPage) {
            clearExpandedRows();
            pageIndex.set(pageIndex.get() + 1);
            reloadStockSummaries();
        }
    }

    public void previousPage() {
        if (pageIndex.get() > 0) {
            clearExpandedRows();
            pageIndex.set(pageIndex.get() - 1);
            reloadStockSummaries();
        }
    }

    public void toggleExpand(SummaryRow row) {
        Objects.requireNonNull(row, "row");
        if (row.expandedProperty().get()) {
            collapseSummary(row);
        } else {
            expandSummary(row);
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

    public StringProperty searchInputProperty() {
        return searchInput;
    }

    public ObservableList<StockTableRow> tableRows() {
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

    private void loadWarehouseFiltersAndStock() {
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
            reloadStockSummaries();
        } catch (RuntimeException ex) {
            errorMessage.set(WarehouseUiErrorMapper.text(ex));
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        }
    }

    private boolean isAccessibleFilter(WarehouseFilterOption option) {
        if (option.isAll()) {
            return warehouseFilterOptions.stream().anyMatch(WarehouseFilterOption::isAll);
        }
        return accessibleWarehouseIds.contains(option.warehouseId());
    }

    private void reloadStockSummaries() {
        if (!canView.get()) {
            deny();
            return;
        }
        WarehouseFilterOption filter = selectedWarehouseFilter.get();
        if (filter == null && warehouseFilterOptions.isEmpty()) {
            tableRows.clear();
            totalElements.set(0);
            updatePaginationFlags();
            statusMessage.set(EMPTY_STOCK_MESSAGE);
            return;
        }
        UUID warehouseId = filter == null ? null : filter.warehouseId();
        long requestId = ++stockLoadGeneration;
        loading.set(true);
        errorMessage.set("");
        statusMessage.set("");
        int page = pageIndex.get();
        String search = blankToNull(committedSearch);
        backgroundExecutor.execute(
                () -> {
                    try {
                        WarehouseStockPage pageResult =
                                warehouseApi.listStockSummaries(
                                        warehouseId, search, page, PAGE_SIZE);
                        uiExecutor.accept(
                                () -> applyStockPage(pageResult, requestId, search != null));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyStockLoadError(ex, requestId));
                    }
                });
    }

    private void applyStockPage(WarehouseStockPage pageResult, long requestId, boolean searchActive) {
        if (requestId != stockLoadGeneration) {
            return;
        }
        loading.set(false);
        totalElements.set(pageResult.totalElements());
        updatePaginationFlags();
        List<StockTableRow> rows = new ArrayList<>();
        for (WarehouseStockSummaryView summary : pageResult.content()) {
            rows.add(
                    SummaryRow.from(
                            summary, warehouseLabels.getOrDefault(summary.warehouseId(), "")));
        }
        tableRows.setAll(rows);
        if (pageResult.totalElements() == 0) {
            statusMessage.set(searchActive ? EMPTY_SEARCH_MESSAGE : EMPTY_STOCK_MESSAGE);
        }
    }

    private void applyStockLoadError(RuntimeException ex, long requestId) {
        if (requestId != stockLoadGeneration) {
            return;
        }
        loading.set(false);
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
        statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        tableRows.clear();
        totalElements.set(0);
        updatePaginationFlags();
    }

    private void expandSummary(SummaryRow row) {
        String key = row.expandKey();
        long requestId = ++expandRequestCounter;
        expandRequestIds.put(key, requestId);
        row.expandingProperty().set(true);
        backgroundExecutor.execute(
                () -> {
                    try {
                        WarehouseMaterialStockDetailsView details =
                                warehouseApi.getStockCellBreakdown(
                                        row.warehouseId(), row.materialReferenceId());
                        uiExecutor.accept(() -> applyExpandSuccess(row, details, requestId, key));
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(() -> applyExpandError(row, ex, requestId, key));
                    }
                });
    }

    private void applyExpandSuccess(
            SummaryRow row,
            WarehouseMaterialStockDetailsView details,
            long requestId,
            String key) {
        if (!Objects.equals(expandRequestIds.get(key), requestId)) {
            return;
        }
        row.expandingProperty().set(false);
        int index = tableRows.indexOf(row);
        if (index < 0) {
            return;
        }
        List<StockTableRow> updated = new ArrayList<>(tableRows);
        int insertAt = index + 1;
        for (WarehouseStockCellView cell : details.cells()) {
            updated.add(insertAt++, new CellDetailRow(cell.storageCellCode(), cell.availableQuantity()));
        }
        tableRows.setAll(updated);
        row.expandedProperty().set(true);
    }

    private void applyExpandError(SummaryRow row, RuntimeException ex, long requestId, String key) {
        if (!Objects.equals(expandRequestIds.get(key), requestId)) {
            return;
        }
        row.expandingProperty().set(false);
        errorMessage.set(WarehouseUiErrorMapper.text(ex));
    }

    private void collapseSummary(SummaryRow row) {
        expandRequestIds.remove(row.expandKey());
        row.expandedProperty().set(false);
        int index = tableRows.indexOf(row);
        if (index < 0) {
            return;
        }
        List<StockTableRow> updated = new ArrayList<>(tableRows);
        int removeAt = index + 1;
        while (removeAt < updated.size() && updated.get(removeAt) instanceof CellDetailRow) {
            updated.remove(removeAt);
        }
        tableRows.setAll(updated);
    }

    private void clearExpandedRows() {
        expandRequestIds.clear();
        List<StockTableRow> summariesOnly = new ArrayList<>();
        for (StockTableRow row : tableRows) {
            if (row instanceof SummaryRow summary) {
                summary.expandedProperty().set(false);
                summary.expandingProperty().set(false);
                summariesOnly.add(summary);
            }
        }
        if (summariesOnly.size() != tableRows.size()) {
            tableRows.setAll(summariesOnly);
        }
    }

    private void updatePaginationFlags() {
        canGoPrevious.set(pageIndex.get() > 0);
        long total = totalElements.get();
        int maxPage = total <= 0 ? 0 : (int) ((total - 1) / PAGE_SIZE);
        canGoNext.set(pageIndex.get() < maxPage);
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
}
