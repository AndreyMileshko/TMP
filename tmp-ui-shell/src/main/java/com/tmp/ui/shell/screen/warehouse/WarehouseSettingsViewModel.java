package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.UpdateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.UpdateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Warehouse Settings (Склады / Ячейки / Ответственные). Structure administration via {@link
 * WarehouseApi} only — no stock mutation, no material→warehouse mapping.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class WarehouseSettingsViewModel {

    public enum SettingsSection {
        WAREHOUSES,
        CELLS,
        RESPONSIBILITIES
    }

    private final WarehouseApi warehouseApi;
    private final AuthorizationService authorizationService;
    private final UserAdministrationService users;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> uiExecutor;

    private final ObjectProperty<SettingsSection> section =
            new SimpleObjectProperty<>(SettingsSection.WAREHOUSES);
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty commandInFlight = new SimpleBooleanProperty(false);

    private final BooleanProperty canViewStructure = new SimpleBooleanProperty(false);
    private final BooleanProperty canCreateWarehouse = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdateWarehouse = new SimpleBooleanProperty(false);
    private final BooleanProperty canCreateCell = new SimpleBooleanProperty(false);
    private final BooleanProperty canUpdateCell = new SimpleBooleanProperty(false);
    private final BooleanProperty canManageResponsibility = new SimpleBooleanProperty(false);

    private final ObservableList<WarehouseView> warehouses = FXCollections.observableArrayList();
    private final ObservableList<StorageCellView> cells = FXCollections.observableArrayList();
    private final ObservableList<ResponsibilityRow> responsibilities =
            FXCollections.observableArrayList();
    private final ObjectProperty<WarehouseView> selectedWarehouse = new SimpleObjectProperty<>();
    private final ObjectProperty<WarehouseView> cellsWarehouse = new SimpleObjectProperty<>();
    private final ObjectProperty<WarehouseView> responsibilityWarehouse =
            new SimpleObjectProperty<>();

    private final StringProperty newWarehouseCode = new SimpleStringProperty("");
    private final StringProperty newWarehouseName = new SimpleStringProperty("");
    private final StringProperty editWarehouseCode = new SimpleStringProperty("");
    private final StringProperty editWarehouseName = new SimpleStringProperty("");
    private final BooleanProperty editWarehouseActive = new SimpleBooleanProperty(true);
    private final StringProperty newCellCode = new SimpleStringProperty("");
    private final StringProperty editCellCode = new SimpleStringProperty("");
    private final BooleanProperty editCellActive = new SimpleBooleanProperty(true);
    private final ObjectProperty<StorageCellView> selectedCell = new SimpleObjectProperty<>();

    private long loadGeneration;

    public WarehouseSettingsViewModel(
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            UserAdministrationService users) {
        this(
                warehouseApi,
                authorizationService,
                users,
                Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread = new Thread(runnable, "warehouse-settings");
                            thread.setDaemon(true);
                            return thread;
                        }),
                Platform::runLater);
    }

    WarehouseSettingsViewModel(
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            UserAdministrationService users,
            Executor backgroundExecutor,
            Consumer<Runnable> uiExecutor) {
        this.warehouseApi = Objects.requireNonNull(warehouseApi, "warehouseApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.users = Objects.requireNonNull(users, "users");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        selectedWarehouse.addListener((obs, o, n) -> applyWarehouseEditFields(n));
        selectedCell.addListener((obs, o, n) -> applyCellEditFields(n));
        cellsWarehouse.addListener(
                (obs, o, n) -> {
                    if (section.get() == SettingsSection.CELLS) {
                        reloadCells();
                    }
                });
        responsibilityWarehouse.addListener(
                (obs, o, n) -> {
                    if (section.get() == SettingsSection.RESPONSIBILITIES) {
                        reloadResponsibilities();
                    }
                });
        refreshPermissions();
    }

    public void onScreenOpened() {
        refreshPermissions();
        if (!canViewStructure.get()) {
            errorMessage.set("Недостаточно прав для просмотра настроек склада.");
            warehouses.clear();
            cells.clear();
            responsibilities.clear();
            return;
        }
        selectSection(SettingsSection.WAREHOUSES);
        reloadWarehouses();
    }

    public void refreshPermissions() {
        canViewStructure.set(has(UiShellScreens.WAREHOUSE_STRUCTURE_VIEW_PERMISSION));
        canCreateWarehouse.set(has(UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION));
        canUpdateWarehouse.set(has(UiShellScreens.WAREHOUSE_STRUCTURE_UPDATE_PERMISSION));
        canCreateCell.set(has(UiShellScreens.WAREHOUSE_STORAGE_CELL_CREATE_PERMISSION));
        canUpdateCell.set(has(UiShellScreens.WAREHOUSE_STORAGE_CELL_UPDATE_PERMISSION));
        canManageResponsibility.set(has(UiShellScreens.WAREHOUSE_STRUCTURE_UPDATE_PERMISSION));
    }

    public void selectSection(SettingsSection next) {
        Objects.requireNonNull(next, "next");
        section.set(next);
        errorMessage.set("");
        if (next == SettingsSection.CELLS) {
            if (cellsWarehouse.get() == null && !warehouses.isEmpty()) {
                cellsWarehouse.set(warehouses.get(0));
            }
            reloadCells();
        } else if (next == SettingsSection.RESPONSIBILITIES) {
            if (responsibilityWarehouse.get() == null && !warehouses.isEmpty()) {
                responsibilityWarehouse.set(warehouses.get(0));
            }
            reloadResponsibilities();
        }
    }

    public void reloadWarehouses() {
        if (!canViewStructure.get() || commandInFlight.get()) {
            return;
        }
        long requestId = ++loadGeneration;
        loading.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        List<WarehouseView> listed = warehouseApi.listWarehouses();
                        uiExecutor.accept(
                                () -> {
                                    if (requestId != loadGeneration) {
                                        return;
                                    }
                                    warehouses.setAll(listed);
                                    loading.set(false);
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    if (requestId != loadGeneration) {
                                        return;
                                    }
                                    loading.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public void createWarehouse() {
        if (!canCreateWarehouse.get() || commandInFlight.get()) {
            return;
        }
        String code = blankToNull(newWarehouseCode.get());
        String name = blankToNull(newWarehouseName.get());
        if (code == null || name == null) {
            errorMessage.set("Укажите код и название склада.");
            return;
        }
        runMutation(
                () ->
                        warehouseApi.createWarehouse(
                                new CreateWarehouseCommand(code, name, true)),
                () -> {
                    newWarehouseCode.set("");
                    newWarehouseName.set("");
                    reloadWarehouses();
                });
    }

    public void saveSelectedWarehouse() {
        WarehouseView selected = selectedWarehouse.get();
        if (selected == null || !canUpdateWarehouse.get() || commandInFlight.get()) {
            return;
        }
        String code = blankToNull(editWarehouseCode.get());
        String name = blankToNull(editWarehouseName.get());
        if (code == null || name == null) {
            errorMessage.set("Укажите код и название склада.");
            return;
        }
        runMutation(
                () ->
                        warehouseApi.updateWarehouse(
                                new UpdateWarehouseCommand(
                                        selected.warehouseId(),
                                        code,
                                        name,
                                        editWarehouseActive.get())),
                this::reloadWarehouses);
    }

    public void setCellsWarehouse(WarehouseView warehouse) {
        cellsWarehouse.set(warehouse);
    }

    public void reloadCells() {
        WarehouseView warehouse = cellsWarehouse.get();
        if (warehouse == null || !canViewStructure.get() || commandInFlight.get()) {
            cells.clear();
            return;
        }
        if (!has(UiShellScreens.WAREHOUSE_STORAGE_CELL_VIEW_PERMISSION)
                && !canViewStructure.get()) {
            cells.clear();
            return;
        }
        long requestId = ++loadGeneration;
        loading.set(true);
        errorMessage.set("");
        UUID warehouseId = warehouse.warehouseId();
        backgroundExecutor.execute(
                () -> {
                    try {
                        List<StorageCellView> listed = warehouseApi.listStorageCells(warehouseId);
                        uiExecutor.accept(
                                () -> {
                                    if (requestId != loadGeneration) {
                                        return;
                                    }
                                    cells.setAll(listed);
                                    loading.set(false);
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    if (requestId != loadGeneration) {
                                        return;
                                    }
                                    loading.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public void createCell() {
        WarehouseView warehouse = cellsWarehouse.get();
        if (warehouse == null || !canCreateCell.get() || commandInFlight.get()) {
            return;
        }
        String code = blankToNull(newCellCode.get());
        if (code == null) {
            errorMessage.set("Укажите код ячейки.");
            return;
        }
        runMutation(
                () ->
                        warehouseApi.createStorageCell(
                                new CreateStorageCellCommand(
                                        warehouse.warehouseId(), code, true)),
                () -> {
                    newCellCode.set("");
                    reloadCells();
                });
    }

    public void saveSelectedCell() {
        StorageCellView selected = selectedCell.get();
        if (selected == null || !canUpdateCell.get() || commandInFlight.get()) {
            return;
        }
        String code = blankToNull(editCellCode.get());
        if (code == null) {
            errorMessage.set("Укажите код ячейки.");
            return;
        }
        runMutation(
                () ->
                        warehouseApi.updateStorageCell(
                                new UpdateStorageCellCommand(
                                        selected.storageCellId(), code, editCellActive.get())),
                this::reloadCells);
    }

    public void setResponsibilityWarehouse(WarehouseView warehouse) {
        responsibilityWarehouse.set(warehouse);
    }

    public void reloadResponsibilities() {
        WarehouseView warehouse = responsibilityWarehouse.get();
        if (warehouse == null || !canViewStructure.get() || commandInFlight.get()) {
            responsibilities.clear();
            return;
        }
        long requestId = ++loadGeneration;
        loading.set(true);
        errorMessage.set("");
        UUID warehouseId = warehouse.warehouseId();
        backgroundExecutor.execute(
                () -> {
                    try {
                        Set<UUID> assigned =
                                new HashSet<>(warehouseApi.listResponsibleUserIds(warehouseId));
                        List<UserSummary> catalogue = List.of();
                        try {
                            catalogue = users.listUsers(0, 200, null);
                        } catch (RuntimeException ignored) {
                            // Structure view alone may lack users.view — show assigned ids only.
                        }
                        List<ResponsibilityRow> rows = new ArrayList<>();
                        Set<UUID> seen = new HashSet<>();
                        for (UserSummary user : catalogue) {
                            if (!"ACTIVE".equals(user.status())) {
                                continue;
                            }
                            UUID id = user.id().value();
                            seen.add(id);
                            rows.add(
                                    new ResponsibilityRow(
                                            id,
                                            user.login().value(),
                                            user.displayName().value(),
                                            assigned.contains(id)));
                        }
                        for (UUID id : assigned) {
                            if (!seen.contains(id)) {
                                rows.add(new ResponsibilityRow(id, id.toString(), "", true));
                            }
                        }
                        List<ResponsibilityRow> result = List.copyOf(rows);
                        uiExecutor.accept(
                                () -> {
                                    if (requestId != loadGeneration) {
                                        return;
                                    }
                                    responsibilities.setAll(result);
                                    loading.set(false);
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    if (requestId != loadGeneration) {
                                        return;
                                    }
                                    loading.set(false);
                                    errorMessage.set(WarehouseUiErrorMapper.text(ex));
                                });
                    }
                });
    }

    public void setResponsibilityAssigned(ResponsibilityRow row, boolean assigned) {
        WarehouseView warehouse = responsibilityWarehouse.get();
        if (row == null || warehouse == null || !canManageResponsibility.get() || commandInFlight.get()) {
            return;
        }
        if (assigned == row.assigned()) {
            return;
        }
        runMutation(
                () -> {
                    if (assigned) {
                        warehouseApi.assignUserToWarehouse(warehouse.warehouseId(), row.userId());
                    } else {
                        warehouseApi.removeUserFromWarehouse(warehouse.warehouseId(), row.userId());
                    }
                    return null;
                },
                this::reloadResponsibilities);
    }

    private void runMutation(MutationAction action, Runnable onSuccess) {
        commandInFlight.set(true);
        errorMessage.set("");
        backgroundExecutor.execute(
                () -> {
                    try {
                        action.run();
                        uiExecutor.accept(
                                () -> {
                                    commandInFlight.set(false);
                                    onSuccess.run();
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

    private void applyWarehouseEditFields(WarehouseView view) {
        if (view == null) {
            editWarehouseCode.set("");
            editWarehouseName.set("");
            editWarehouseActive.set(true);
            return;
        }
        editWarehouseCode.set(view.code());
        editWarehouseName.set(view.name());
        editWarehouseActive.set(view.active());
    }

    private void applyCellEditFields(StorageCellView view) {
        if (view == null) {
            editCellCode.set("");
            editCellActive.set(true);
            return;
        }
        editCellCode.set(view.code());
        editCellActive.set(view.active());
    }

    private boolean has(String permission) {
        return authorizationService.hasPermission(PermissionId.of(permission));
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @FunctionalInterface
    private interface MutationAction {
        Object run();
    }

    public record ResponsibilityRow(
            UUID userId, String login, String displayName, boolean assigned) {
        public ResponsibilityRow {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(login, "login");
            displayName = displayName == null ? "" : displayName;
        }
    }

    public ObjectProperty<SettingsSection> sectionProperty() {
        return section;
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

    public BooleanProperty canViewStructureProperty() {
        return canViewStructure;
    }

    public BooleanProperty canCreateWarehouseProperty() {
        return canCreateWarehouse;
    }

    public BooleanProperty canUpdateWarehouseProperty() {
        return canUpdateWarehouse;
    }

    public BooleanProperty canCreateCellProperty() {
        return canCreateCell;
    }

    public BooleanProperty canUpdateCellProperty() {
        return canUpdateCell;
    }

    public BooleanProperty canManageResponsibilityProperty() {
        return canManageResponsibility;
    }

    public ObservableList<WarehouseView> warehouses() {
        return warehouses;
    }

    public ObservableList<StorageCellView> cells() {
        return cells;
    }

    public ObservableList<ResponsibilityRow> responsibilities() {
        return responsibilities;
    }

    public ObjectProperty<WarehouseView> selectedWarehouseProperty() {
        return selectedWarehouse;
    }

    public ObjectProperty<WarehouseView> cellsWarehouseProperty() {
        return cellsWarehouse;
    }

    public ObjectProperty<WarehouseView> responsibilityWarehouseProperty() {
        return responsibilityWarehouse;
    }

    public ObjectProperty<StorageCellView> selectedCellProperty() {
        return selectedCell;
    }

    public StringProperty newWarehouseCodeProperty() {
        return newWarehouseCode;
    }

    public StringProperty newWarehouseNameProperty() {
        return newWarehouseName;
    }

    public StringProperty editWarehouseCodeProperty() {
        return editWarehouseCode;
    }

    public StringProperty editWarehouseNameProperty() {
        return editWarehouseName;
    }

    public BooleanProperty editWarehouseActiveProperty() {
        return editWarehouseActive;
    }

    public StringProperty newCellCodeProperty() {
        return newCellCode;
    }

    public StringProperty editCellCodeProperty() {
        return editCellCode;
    }

    public BooleanProperty editCellActiveProperty() {
        return editCellActive;
    }
}
