package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.MaterialDisplayFormatting;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Warehouse workbench ViewModel. All reads/writes go through {@link WarehouseApi} only.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class WarehouseWorkbenchViewModel {

    private final WarehouseApi warehouseApi;
    private final AuthorizationService authorizationService;

    private final ObjectProperty<WarehouseSection> section =
            new SimpleObjectProperty<>(WarehouseSection.WAREHOUSES);
    private final StringProperty title = new SimpleStringProperty(WarehouseSection.WAREHOUSES.title());
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);

    private final BooleanProperty canView = new SimpleBooleanProperty(false);
    private final BooleanProperty canReceipt = new SimpleBooleanProperty(false);
    private final BooleanProperty canMove = new SimpleBooleanProperty(false);
    private final BooleanProperty canTransfer = new SimpleBooleanProperty(false);
    private final BooleanProperty canConsumption = new SimpleBooleanProperty(false);
    private final BooleanProperty canAdjustment = new SimpleBooleanProperty(false);
    private final BooleanProperty canReservation = new SimpleBooleanProperty(false);
    private final BooleanProperty canCreateWarehouse = new SimpleBooleanProperty(false);
    private final BooleanProperty canCreateStorageCell = new SimpleBooleanProperty(false);
    private final BooleanProperty canDeleteWarehouse = new SimpleBooleanProperty(false);
    private final BooleanProperty canDeleteStorageCell = new SimpleBooleanProperty(false);

    private final ObservableList<WarehouseView> warehouses = FXCollections.observableArrayList();
    private final ObservableList<StorageCellView> warehouseCells = FXCollections.observableArrayList();
    private final ObservableList<WarehouseChoice> warehouseChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StockView> stockRows = FXCollections.observableArrayList();
    private final ObservableList<ReservationLinkView> reservationLinks =
            FXCollections.observableArrayList();

    private final ObservableList<StorageCellChoice> receiptCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> moveSourceCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> moveDestCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> transferSendCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> transferReceiveSourceCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> transferReceiveDestCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> consumptionCellChoices =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> adjustmentCellChoices =
            FXCollections.observableArrayList();

    private final StringProperty newWarehouseCode = new SimpleStringProperty("");
    private final StringProperty newWarehouseName = new SimpleStringProperty("");
    private final BooleanProperty newWarehouseActive = new SimpleBooleanProperty(true);
    private final StringProperty newCellCode = new SimpleStringProperty("");
    private final BooleanProperty newCellActive = new SimpleBooleanProperty(true);
    private final ObjectProperty<WarehouseChoice> newCellWarehouse =
            new SimpleObjectProperty<>();

    private final ObjectProperty<WarehouseChoice> stockWarehouse = new SimpleObjectProperty<>();
    private final StringProperty stockMaterialCode = new SimpleStringProperty("");

    private final ObservableList<MaterialChoice> materialChoices =
            FXCollections.observableArrayList();

    private final StringProperty receiptArticle = new SimpleStringProperty("");
    private final StringProperty receiptName = new SimpleStringProperty("");
    private final StringProperty receiptColor = new SimpleStringProperty("");
    private final StringProperty receiptSize = new SimpleStringProperty("");
    private final StringProperty receiptUnit = new SimpleStringProperty("");
    private final StringProperty receiptQuantity = new SimpleStringProperty("");
    private final ObjectProperty<WarehouseChoice> receiptWarehouse = new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> receiptCell = new SimpleObjectProperty<>();

    private final ObjectProperty<MaterialChoice> moveMaterial = new SimpleObjectProperty<>();
    private final StringProperty moveQuantity = new SimpleStringProperty("");
    private final ObjectProperty<WarehouseChoice> moveSourceWarehouse = new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> moveSourceCell = new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> moveDestCell = new SimpleObjectProperty<>();

    private final ObjectProperty<MaterialChoice> transferMaterial = new SimpleObjectProperty<>();
    private final StringProperty transferQuantity = new SimpleStringProperty("");
    private final ObjectProperty<WarehouseChoice> transferSourceWarehouse =
            new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> transferSourceCell =
            new SimpleObjectProperty<>();
    private final ObjectProperty<WarehouseChoice> transferDestWarehouse =
            new SimpleObjectProperty<>();

    private final ObjectProperty<MaterialChoice> transferReceiveMaterial = new SimpleObjectProperty<>();
    private final StringProperty transferReceiveQuantity = new SimpleStringProperty("");
    private final ObjectProperty<WarehouseChoice> transferReceiveSourceWarehouse =
            new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> transferReceiveSourceCell =
            new SimpleObjectProperty<>();
    private final ObjectProperty<WarehouseChoice> transferReceiveDestWarehouse =
            new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> transferReceiveDestCell =
            new SimpleObjectProperty<>();

    private final ObjectProperty<MaterialChoice> consumptionMaterial = new SimpleObjectProperty<>();
    private final StringProperty consumptionQuantity = new SimpleStringProperty("");
    private final ObjectProperty<WarehouseChoice> consumptionWarehouse =
            new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> consumptionCell = new SimpleObjectProperty<>();
    private final StringProperty consumptionBasis = new SimpleStringProperty("");

    private final ObjectProperty<MaterialChoice> adjustmentMaterial = new SimpleObjectProperty<>();
    private final StringProperty adjustmentQuantityDelta = new SimpleStringProperty("");
    private final ObjectProperty<WarehouseChoice> adjustmentWarehouse =
            new SimpleObjectProperty<>();
    private final ObjectProperty<StorageCellChoice> adjustmentCell = new SimpleObjectProperty<>();
    private final StringProperty adjustmentReason = new SimpleStringProperty("");

    private final StringProperty reservationMaterial = new SimpleStringProperty("");
    private final StringProperty reservationOrderRef = new SimpleStringProperty("");
    private final StringProperty reservationQuantity = new SimpleStringProperty("");
    private final StringProperty reservationFilterMaterial = new SimpleStringProperty("");

    private final Map<UUID, String> warehouseLabels = new HashMap<>();
    private final Map<UUID, String> cellLabels = new HashMap<>();

    public WarehouseWorkbenchViewModel(
            WarehouseApi warehouseApi, AuthorizationService authorizationService) {
        this.warehouseApi = Objects.requireNonNull(warehouseApi, "warehouseApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        section.addListener((obs, oldValue, newValue) -> {
            if (newValue != null) {
                title.set(newValue.title());
                onSectionOpened(newValue);
            }
        });
        receiptWarehouse.addListener(
                (obs, oldValue, newValue) -> reloadCells(newValue, receiptCellChoices, receiptCell));
        moveSourceWarehouse.addListener((obs, oldValue, newValue) -> {
            reloadCells(newValue, moveSourceCellChoices, moveSourceCell);
            reloadCells(newValue, moveDestCellChoices, moveDestCell);
        });
        transferSourceWarehouse.addListener(
                (obs, oldValue, newValue) ->
                        reloadCells(newValue, transferSendCellChoices, transferSourceCell));
        transferReceiveSourceWarehouse.addListener(
                (obs, oldValue, newValue) ->
                        reloadCells(
                                newValue,
                                transferReceiveSourceCellChoices,
                                transferReceiveSourceCell));
        transferReceiveDestWarehouse.addListener(
                (obs, oldValue, newValue) ->
                        reloadCells(
                                newValue,
                                transferReceiveDestCellChoices,
                                transferReceiveDestCell));
        consumptionWarehouse.addListener(
                (obs, oldValue, newValue) ->
                        reloadCells(newValue, consumptionCellChoices, consumptionCell));
        adjustmentWarehouse.addListener(
                (obs, oldValue, newValue) ->
                        reloadCells(newValue, adjustmentCellChoices, adjustmentCell));
        newCellWarehouse.addListener(
                (obs, oldValue, newValue) -> {
                    if (newValue != null) {
                        loadCellsForWarehousePane(newValue.id());
                    } else {
                        warehouseCells.clear();
                    }
                });
        refreshPermissions();
    }

    public void refreshPermissions() {
        canView.set(has(UiShellScreens.WAREHOUSE_VIEW_PERMISSION));
        canReceipt.set(has(UiShellScreens.WAREHOUSE_RECEIPT_PERMISSION));
        canMove.set(has(UiShellScreens.WAREHOUSE_MOVE_PERMISSION));
        canTransfer.set(has(UiShellScreens.WAREHOUSE_TRANSFER_PERMISSION));
        canConsumption.set(has(UiShellScreens.WAREHOUSE_CONSUMPTION_PERMISSION));
        canAdjustment.set(has(UiShellScreens.WAREHOUSE_ADJUSTMENT_PERMISSION));
        canReservation.set(has(UiShellScreens.WAREHOUSE_RESERVATION_PERMISSION));
        canCreateWarehouse.set(has(UiShellScreens.WAREHOUSE_STRUCTURE_CREATE_PERMISSION));
        canCreateStorageCell.set(has(UiShellScreens.WAREHOUSE_STORAGE_CELL_CREATE_PERMISSION));
        canDeleteWarehouse.set(has(UiShellScreens.WAREHOUSE_STRUCTURE_DELETE_PERMISSION));
        canDeleteStorageCell.set(has(UiShellScreens.WAREHOUSE_STORAGE_CELL_DELETE_PERMISSION));
    }

    public void selectSection(WarehouseSection value) {
        Objects.requireNonNull(value, "value");
        if (!isSectionAllowed(value)) {
            errorMessage.set(WarehouseUiErrorMapper.ACCESS_DENIED);
            statusMessage.set("");
            return;
        }
        errorMessage.set("");
        section.set(value);
    }

    public void refreshCurrent() {
        WarehouseSection current = section.get();
        if (current != null) {
            onSectionOpened(current);
        }
    }

    public void loadWarehouses() {
        if (!canView.get()) {
            deny();
            return;
        }
        run("Список складов загружен", () -> {
            List<WarehouseView> listed = warehouseApi.listWarehouses();
            warehouses.setAll(listed);
            refreshWarehouseChoices(listed);
            if (warehouses.isEmpty()) {
                statusMessage.set("Склады не найдены");
            }
        });
    }

    public void createWarehouse() {
        if (!canCreateWarehouse.get()) {
            deny();
            return;
        }
        run("Склад создан", () -> {
            WarehouseView created =
                    warehouseApi.createWarehouse(
                            new CreateWarehouseCommand(
                                    requireText(newWarehouseCode.get(), "код склада"),
                                    requireText(newWarehouseName.get(), "название склада"),
                                    newWarehouseActive.get()));
            statusMessage.set("Склад создан: " + created.code());
            newWarehouseCode.set("");
            newWarehouseName.set("");
            newWarehouseActive.set(true);
            List<WarehouseView> listed = warehouseApi.listWarehouses();
            warehouses.setAll(listed);
            refreshWarehouseChoices(listed);
        });
    }

    public void createStorageCell() {
        if (!canCreateStorageCell.get()) {
            deny();
            return;
        }
        run("Ячейка создана", () -> {
            WarehouseChoice warehouse = requireChoice(newCellWarehouse.get(), "склад");
            StorageCellView created =
                    warehouseApi.createStorageCell(
                            new CreateStorageCellCommand(
                                    warehouse.id(),
                                    requireText(newCellCode.get(), "код ячейки"),
                                    newCellActive.get()));
            statusMessage.set("Ячейка создана: " + created.code());
            newCellCode.set("");
            newCellActive.set(true);
            loadCellsForWarehousePane(warehouse.id());
            rememberCell(created);
        });
    }

    public void loadStock() {
        if (!canView.get()) {
            deny();
            return;
        }
        WarehouseChoice warehouse = stockWarehouse.get();
        String materialRaw = blankToNull(stockMaterialCode.get());
        if (warehouse == null && materialRaw == null) {
            errorMessage.set("Укажите склад или материал для загрузки остатков.");
            return;
        }
        run("Остатки загружены", () -> {
            ensureWarehouseChoicesLoaded();
            if (warehouse != null) {
                stockRows.setAll(warehouseApi.getStockByWarehouse(warehouse.id()));
                loadCellLabels(warehouse.id());
            } else {
                stockRows.setAll(warehouseApi.getStock(materialRaw));
                for (StockView row : stockRows) {
                    loadCellLabels(row.warehouseId());
                }
            }
            if (stockRows.isEmpty()) {
                statusMessage.set("Остатки не найдены");
            }
        });
    }

    public void submitReceipt() {
        if (!canReceipt.get()) {
            deny();
            return;
        }
        run("Поступление выполнено", () -> {
            WarehouseChoice warehouse = requireChoice(receiptWarehouse.get(), "склад");
            StorageCellChoice cell = requireChoice(receiptCell.get(), "ячейка");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.receipt(
                                    requireText(receiptArticle.get(), "артикул"),
                                    requireText(receiptName.get(), "наименование"),
                                    blankToNull(receiptColor.get()) == null ? "" : receiptColor.get().trim(),
                                    blankToNull(receiptSize.get()) == null ? "" : receiptSize.get().trim(),
                                    blankToNull(receiptUnit.get()) == null ? "" : receiptUnit.get().trim(),
                                    parsePositiveDecimal(receiptQuantity.get(), "количество"),
                                    warehouse.id(),
                                    cell.id()));
            loadMaterialChoices();
            statusMessage.set(
                    "Поступление выполнено: operationId=" + result.operationId());
        });
    }

    public void submitMove() {
        if (!canMove.get()) {
            deny();
            return;
        }
        run("Перемещение выполнено", () -> {
            MaterialChoice material = requireChoice(moveMaterial.get(), "материал");
            WarehouseChoice warehouse = requireChoice(moveSourceWarehouse.get(), "склад источник");
            StorageCellChoice sourceCell = requireChoice(moveSourceCell.get(), "ячейка источник");
            StorageCellChoice destCell = requireChoice(moveDestCell.get(), "ячейка назначения");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.move(
                                    material.id(),
                                    parsePositiveDecimal(moveQuantity.get(), "количество"),
                                    warehouse.id(),
                                    sourceCell.id(),
                                    warehouse.id(),
                                    destCell.id()));
            statusMessage.set("Перемещение выполнено: operationId=" + result.operationId());
        });
    }

    public void submitTransferSend() {
        if (!canTransfer.get()) {
            deny();
            return;
        }
        run("Межскладское перемещение (отправка) выполнено", () -> {
            MaterialChoice material = requireChoice(transferMaterial.get(), "материал");
            WarehouseChoice source =
                    requireChoice(transferSourceWarehouse.get(), "склад источник");
            StorageCellChoice sourceCell =
                    requireChoice(transferSourceCell.get(), "ячейка источник");
            WarehouseChoice destination =
                    requireChoice(transferDestWarehouse.get(), "склад назначения");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.transferSend(
                                    material.id(),
                                    parsePositiveDecimal(transferQuantity.get(), "количество"),
                                    source.id(),
                                    sourceCell.id(),
                                    destination.id()));
            statusMessage.set(
                    "Межскладская отправка выполнена: operationId=" + result.operationId());
        });
    }

    public void submitTransferReceive() {
        if (!canTransfer.get()) {
            deny();
            return;
        }
        run("Межскладское перемещение (приём) выполнено", () -> {
            MaterialChoice material = requireChoice(transferReceiveMaterial.get(), "материал");
            WarehouseChoice source =
                    requireChoice(transferReceiveSourceWarehouse.get(), "склад источник");
            StorageCellChoice sourceCell =
                    requireChoice(transferReceiveSourceCell.get(), "ячейка источник");
            WarehouseChoice destination =
                    requireChoice(transferReceiveDestWarehouse.get(), "склад назначения");
            StorageCellChoice destCell =
                    requireChoice(transferReceiveDestCell.get(), "ячейка назначения");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.transferReceive(
                                    material.id(),
                                    parsePositiveDecimal(
                                            transferReceiveQuantity.get(), "количество"),
                                    source.id(),
                                    sourceCell.id(),
                                    destination.id(),
                                    destCell.id()));
            statusMessage.set(
                    "Межскладской приём выполнен: operationId=" + result.operationId());
        });
    }

    public void submitConsumption() {
        if (!canConsumption.get()) {
            deny();
            return;
        }
        run("Списание выполнено", () -> {
            MaterialChoice material = requireChoice(consumptionMaterial.get(), "материал");
            String basis = requireText(consumptionBasis.get(), "основание");
            WarehouseChoice warehouse = requireChoice(consumptionWarehouse.get(), "склад");
            StorageCellChoice cell = requireChoice(consumptionCell.get(), "ячейка");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.consumption(
                                    material.id(),
                                    parsePositiveDecimal(consumptionQuantity.get(), "количество"),
                                    warehouse.id(),
                                    cell.id()));
            statusMessage.set(
                    "Списание выполнено: operationId="
                            + result.operationId()
                            + "; основание="
                            + basis);
        });
    }

    public void submitAdjustment() {
        if (!canAdjustment.get()) {
            deny();
            return;
        }
        run("Корректировка выполнена", () -> {
            MaterialChoice material = requireChoice(adjustmentMaterial.get(), "материал");
            String reason = requireText(adjustmentReason.get(), "причина");
            WarehouseChoice warehouse = requireChoice(adjustmentWarehouse.get(), "склад");
            StorageCellChoice cell = requireChoice(adjustmentCell.get(), "ячейка");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.adjustment(
                                    material.id(),
                                    parseNonZeroDecimal(
                                            adjustmentQuantityDelta.get(), "количество изменения"),
                                    warehouse.id(),
                                    cell.id()));
            statusMessage.set(
                    "Корректировка выполнена: operationId="
                            + result.operationId()
                            + "; причина="
                            + reason);
        });
    }

    public void openAdjustmentFromInventory() {
        selectSection(WarehouseSection.ADJUSTMENT);
    }

    public void loadReservationLinks() {
        if (!canReservation.get()) {
            deny();
            return;
        }
        run("Информационные связи загружены", () -> {
            String material = requireText(reservationFilterMaterial.get(), "материал");
            reservationLinks.setAll(warehouseApi.listReservationLinks(material));
            if (reservationLinks.isEmpty()) {
                statusMessage.set("Информационные связи не найдены");
            }
        });
    }

    public void submitReservationLink() {
        if (!canReservation.get()) {
            deny();
            return;
        }
        run("Информационная связь создана", () -> {
            UUID materialReferenceId =
                    warehouseApi.listMaterialReferences().stream()
                            .filter(
                                    material ->
                                            material.article()
                                                    .equals(
                                                            requireText(
                                                                    reservationMaterial.get(),
                                                                    "материал")))
                            .map(MaterialReferenceView::materialReferenceId)
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Material reference not found"));
            ReservationLinkView created =
                    warehouseApi.createReservationLink(
                            new CreateReservationLinkCommand(
                                    materialReferenceId,
                                    ReservationTargetTypeView.ORDER,
                                    requireText(reservationOrderRef.get(), "заказ"),
                                    parsePositiveDecimal(reservationQuantity.get(), "количество")));
            statusMessage.set("Связь создана: linkId=" + created.linkId());
            reservationFilterMaterial.set(created.materialCode());
            reservationLinks.setAll(warehouseApi.listReservationLinks(created.materialCode()));
        });
    }

    public String warehouseLabel(UUID warehouseId) {
        if (warehouseId == null) {
            return "";
        }
        return warehouseLabels.getOrDefault(warehouseId, warehouseId.toString());
    }

    public String cellLabel(UUID cellId) {
        if (cellId == null) {
            return "";
        }
        return cellLabels.getOrDefault(cellId, cellId.toString());
    }

    public ObjectProperty<WarehouseSection> sectionProperty() {
        return section;
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

    public BooleanProperty canReceiptProperty() {
        return canReceipt;
    }

    public BooleanProperty canMoveProperty() {
        return canMove;
    }

    public BooleanProperty canTransferProperty() {
        return canTransfer;
    }

    public BooleanProperty canConsumptionProperty() {
        return canConsumption;
    }

    public BooleanProperty canAdjustmentProperty() {
        return canAdjustment;
    }

    public BooleanProperty canReservationProperty() {
        return canReservation;
    }

    public BooleanProperty canCreateWarehouseProperty() {
        return canCreateWarehouse;
    }

    public BooleanProperty canCreateStorageCellProperty() {
        return canCreateStorageCell;
    }

    public BooleanProperty canDeleteWarehouseProperty() {
        return canDeleteWarehouse;
    }

    public BooleanProperty canDeleteStorageCellProperty() {
        return canDeleteStorageCell;
    }

    public ObservableList<WarehouseView> warehouses() {
        return warehouses;
    }

    public ObservableList<StorageCellView> warehouseCells() {
        return warehouseCells;
    }

    public ObservableList<WarehouseChoice> warehouseChoices() {
        return warehouseChoices;
    }

    public ObservableList<StockView> stockRows() {
        return stockRows;
    }

    public ObservableList<ReservationLinkView> reservationLinks() {
        return reservationLinks;
    }

    public ObservableList<StorageCellChoice> receiptCellChoices() {
        return receiptCellChoices;
    }

    public ObservableList<StorageCellChoice> moveSourceCellChoices() {
        return moveSourceCellChoices;
    }

    public ObservableList<StorageCellChoice> moveDestCellChoices() {
        return moveDestCellChoices;
    }

    public ObservableList<StorageCellChoice> transferSendCellChoices() {
        return transferSendCellChoices;
    }

    public ObservableList<StorageCellChoice> transferReceiveSourceCellChoices() {
        return transferReceiveSourceCellChoices;
    }

    public ObservableList<StorageCellChoice> transferReceiveDestCellChoices() {
        return transferReceiveDestCellChoices;
    }

    public ObservableList<StorageCellChoice> consumptionCellChoices() {
        return consumptionCellChoices;
    }

    public ObservableList<StorageCellChoice> adjustmentCellChoices() {
        return adjustmentCellChoices;
    }

    public StringProperty newWarehouseCodeProperty() {
        return newWarehouseCode;
    }

    public StringProperty newWarehouseNameProperty() {
        return newWarehouseName;
    }

    public BooleanProperty newWarehouseActiveProperty() {
        return newWarehouseActive;
    }

    public StringProperty newCellCodeProperty() {
        return newCellCode;
    }

    public BooleanProperty newCellActiveProperty() {
        return newCellActive;
    }

    public ObjectProperty<WarehouseChoice> newCellWarehouseProperty() {
        return newCellWarehouse;
    }

    public ObjectProperty<WarehouseChoice> stockWarehouseProperty() {
        return stockWarehouse;
    }

    public StringProperty stockMaterialCodeProperty() {
        return stockMaterialCode;
    }

    public StringProperty receiptArticleProperty() {
        return receiptArticle;
    }

    public StringProperty receiptNameProperty() {
        return receiptName;
    }

    public StringProperty receiptColorProperty() {
        return receiptColor;
    }

    public StringProperty receiptSizeProperty() {
        return receiptSize;
    }

    public StringProperty receiptUnitProperty() {
        return receiptUnit;
    }

    public StringProperty receiptQuantityProperty() {
        return receiptQuantity;
    }

    public ObjectProperty<WarehouseChoice> receiptWarehouseProperty() {
        return receiptWarehouse;
    }

    public ObjectProperty<StorageCellChoice> receiptCellProperty() {
        return receiptCell;
    }

    public ObjectProperty<MaterialChoice> moveMaterialProperty() {
        return moveMaterial;
    }

    public StringProperty moveQuantityProperty() {
        return moveQuantity;
    }

    public ObjectProperty<WarehouseChoice> moveSourceWarehouseProperty() {
        return moveSourceWarehouse;
    }

    public ObjectProperty<StorageCellChoice> moveSourceCellProperty() {
        return moveSourceCell;
    }

    public ObjectProperty<StorageCellChoice> moveDestCellProperty() {
        return moveDestCell;
    }

    public ObjectProperty<MaterialChoice> transferMaterialProperty() {
        return transferMaterial;
    }

    public StringProperty transferQuantityProperty() {
        return transferQuantity;
    }

    public ObjectProperty<WarehouseChoice> transferSourceWarehouseProperty() {
        return transferSourceWarehouse;
    }

    public ObjectProperty<StorageCellChoice> transferSourceCellProperty() {
        return transferSourceCell;
    }

    public ObjectProperty<WarehouseChoice> transferDestWarehouseProperty() {
        return transferDestWarehouse;
    }

    public ObjectProperty<MaterialChoice> transferReceiveMaterialProperty() {
        return transferReceiveMaterial;
    }

    public StringProperty transferReceiveQuantityProperty() {
        return transferReceiveQuantity;
    }

    public ObjectProperty<WarehouseChoice> transferReceiveSourceWarehouseProperty() {
        return transferReceiveSourceWarehouse;
    }

    public ObjectProperty<StorageCellChoice> transferReceiveSourceCellProperty() {
        return transferReceiveSourceCell;
    }

    public ObjectProperty<WarehouseChoice> transferReceiveDestWarehouseProperty() {
        return transferReceiveDestWarehouse;
    }

    public ObjectProperty<StorageCellChoice> transferReceiveDestCellProperty() {
        return transferReceiveDestCell;
    }

    public ObjectProperty<MaterialChoice> consumptionMaterialProperty() {
        return consumptionMaterial;
    }

    public StringProperty consumptionQuantityProperty() {
        return consumptionQuantity;
    }

    public ObjectProperty<WarehouseChoice> consumptionWarehouseProperty() {
        return consumptionWarehouse;
    }

    public ObjectProperty<StorageCellChoice> consumptionCellProperty() {
        return consumptionCell;
    }

    public StringProperty consumptionBasisProperty() {
        return consumptionBasis;
    }

    public ObjectProperty<MaterialChoice> adjustmentMaterialProperty() {
        return adjustmentMaterial;
    }

    public StringProperty adjustmentQuantityDeltaProperty() {
        return adjustmentQuantityDelta;
    }

    public ObjectProperty<WarehouseChoice> adjustmentWarehouseProperty() {
        return adjustmentWarehouse;
    }

    public ObjectProperty<StorageCellChoice> adjustmentCellProperty() {
        return adjustmentCell;
    }

    public StringProperty adjustmentReasonProperty() {
        return adjustmentReason;
    }

    public StringProperty reservationMaterialProperty() {
        return reservationMaterial;
    }

    public StringProperty reservationOrderRefProperty() {
        return reservationOrderRef;
    }

    public StringProperty reservationQuantityProperty() {
        return reservationQuantity;
    }

    public StringProperty reservationFilterMaterialProperty() {
        return reservationFilterMaterial;
    }

    private void loadMaterialChoices() {
        if (!canLoadOperationCatalogue()) {
            return;
        }
        try {
            materialChoices.setAll(
                    warehouseApi.listMaterialReferences().stream()
                            .map(MaterialChoice::from)
                            .toList());
        } catch (RuntimeException ex) {
            errorMessage.set(WarehouseUiErrorMapper.text(ex));
        }
    }

    public ObservableList<MaterialChoice> materialChoices() {
        return materialChoices;
    }

    static String formatMaterialDisplay(MaterialReferenceView view) {
        String description =
                MaterialDisplayFormatting.formatDescription(
                        view.name(), view.color(), view.size(), view.unitOfMeasure());
        if (description.isBlank()) {
            return view.article();
        }
        return view.article() + " — " + description;
    }

    private void onSectionOpened(WarehouseSection value) {
        refreshPermissions();
        errorMessage.set("");
        statusMessage.set("");
        switch (value) {
            case WAREHOUSES -> loadWarehouses();
            case STOCK, RECEIPT, MOVE, TRANSFER, CONSUMPTION, ADJUSTMENT, INVENTORY -> {
                    ensureWarehouseChoicesLoaded();
                    loadMaterialChoices();
                }
            case RESERVATIONS -> {
                // forms load on demand
            }
        }
    }

    private boolean isSectionAllowed(WarehouseSection value) {
        return switch (value) {
            case WAREHOUSES, STOCK, INVENTORY -> canView.get();
            case RECEIPT -> canReceipt.get();
            case MOVE -> canMove.get();
            case TRANSFER -> canTransfer.get();
            case CONSUMPTION -> canConsumption.get();
            case ADJUSTMENT -> canAdjustment.get();
            case RESERVATIONS -> canReservation.get();
        };
    }

    private void ensureWarehouseChoicesLoaded() {
        if (!warehouseChoices.isEmpty() || !canLoadOperationCatalogue()) {
            return;
        }
        try {
            refreshWarehouseChoices(warehouseApi.listWarehouses());
        } catch (RuntimeException ex) {
            errorMessage.set(WarehouseUiErrorMapper.text(ex));
        }
    }

    private void refreshWarehouseChoices(List<WarehouseView> listed) {
        warehouseChoices.setAll(listed.stream().map(WarehouseChoice::from).toList());
        warehouseLabels.clear();
        for (WarehouseView view : listed) {
            warehouseLabels.put(view.warehouseId(), view.code() + " — " + view.name());
        }
    }

    private void loadCellsForWarehousePane(UUID warehouseId) {
        List<StorageCellView> cells = warehouseApi.listStorageCells(warehouseId);
        warehouseCells.setAll(cells);
        for (StorageCellView cell : cells) {
            rememberCell(cell);
        }
    }

    private void reloadCells(
            WarehouseChoice warehouse,
            ObservableList<StorageCellChoice> target,
            ObjectProperty<StorageCellChoice> selected) {
        selected.set(null);
        target.clear();
        if (warehouse == null || !canLoadOperationCatalogue()) {
            return;
        }
        try {
            List<StorageCellView> cells = warehouseApi.listStorageCells(warehouse.id());
            target.setAll(cells.stream().map(StorageCellChoice::from).toList());
            for (StorageCellView cell : cells) {
                rememberCell(cell);
            }
        } catch (RuntimeException ex) {
            errorMessage.set(WarehouseUiErrorMapper.text(ex));
        }
    }

    private void loadCellLabels(UUID warehouseId) {
        try {
            for (StorageCellView cell : warehouseApi.listStorageCells(warehouseId)) {
                rememberCell(cell);
            }
        } catch (RuntimeException ignored) {
            // keep UUID fallback in table
        }
    }

    private void rememberCell(StorageCellView cell) {
        cellLabels.put(cell.storageCellId(), cell.code());
    }

    private boolean canLoadOperationCatalogue() {
        return canView.get()
                || canReceipt.get()
                || canMove.get()
                || canTransfer.get()
                || canConsumption.get()
                || canAdjustment.get()
                || canReservation.get();
    }

    private void run(String successMessage, Runnable action) {
        loading.set(true);
        errorMessage.set("");
        statusMessage.set("");
        try {
            action.run();
            if (statusMessage.get() == null || statusMessage.get().isBlank()) {
                statusMessage.set(successMessage);
            }
        } catch (AccessDeniedException ex) {
            errorMessage.set(WarehouseUiErrorMapper.text(ex));
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        } catch (RuntimeException ex) {
            errorMessage.set(WarehouseUiErrorMapper.text(ex));
            statusMessage.set(WarehouseUiErrorMapper.LOAD_FAILED);
        } finally {
            loading.set(false);
        }
    }

    private void deny() {
        errorMessage.set(WarehouseUiErrorMapper.ACCESS_DENIED);
        statusMessage.set("");
    }

    private boolean has(String permission) {
        return authorizationService.hasPermission(PermissionId.of(permission));
    }

    private static <T> T requireChoice(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Выберите " + field);
        }
        return value;
    }

    private static String requireText(String raw, String field) {
        String value = blankToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static BigDecimal parsePositiveDecimal(String raw, String field) {
        BigDecimal value = parseDecimal(raw, field);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static BigDecimal parseNonZeroDecimal(String raw, String field) {
        BigDecimal value = parseDecimal(raw, field);
        if (value.signum() == 0) {
            throw new IllegalArgumentException(field + " must not be zero");
        }
        return value;
    }

    private static BigDecimal parseDecimal(String raw, String field) {
        String value = requireText(raw, field);
        try {
            return new BigDecimal(value.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректное число для поля \"" + field + "\"");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
