package com.tmp.ui.shell.screen.warehouse;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
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
 *
 * <p>Exposes eight capability-gated sections matching Warehouse UI v1.0 screens. Does not mutate
 * Stock Position or Movement directly.
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

    private final ObservableList<WarehouseView> warehouses = FXCollections.observableArrayList();
    private final ObservableList<StockView> stockRows = FXCollections.observableArrayList();
    private final ObservableList<ReservationLinkView> reservationLinks =
            FXCollections.observableArrayList();

    private final StringProperty stockWarehouseId = new SimpleStringProperty("");
    private final StringProperty stockMaterialCode = new SimpleStringProperty("");

    private final StringProperty receiptMaterial = new SimpleStringProperty("");
    private final StringProperty receiptQuantity = new SimpleStringProperty("");
    private final StringProperty receiptWarehouseId = new SimpleStringProperty("");
    private final StringProperty receiptCellId = new SimpleStringProperty("");

    private final StringProperty moveMaterial = new SimpleStringProperty("");
    private final StringProperty moveQuantity = new SimpleStringProperty("");
    private final StringProperty moveSourceWarehouseId = new SimpleStringProperty("");
    private final StringProperty moveSourceCellId = new SimpleStringProperty("");
    private final StringProperty moveDestWarehouseId = new SimpleStringProperty("");
    private final StringProperty moveDestCellId = new SimpleStringProperty("");

    private final StringProperty transferMaterial = new SimpleStringProperty("");
    private final StringProperty transferQuantity = new SimpleStringProperty("");
    private final StringProperty transferSourceWarehouseId = new SimpleStringProperty("");
    private final StringProperty transferSourceCellId = new SimpleStringProperty("");
    private final StringProperty transferDestWarehouseId = new SimpleStringProperty("");

    private final StringProperty consumptionMaterial = new SimpleStringProperty("");
    private final StringProperty consumptionQuantity = new SimpleStringProperty("");
    private final StringProperty consumptionWarehouseId = new SimpleStringProperty("");
    private final StringProperty consumptionCellId = new SimpleStringProperty("");
    private final StringProperty consumptionBasis = new SimpleStringProperty("");

    private final StringProperty adjustmentMaterial = new SimpleStringProperty("");
    private final StringProperty adjustmentQuantityDelta = new SimpleStringProperty("");
    private final StringProperty adjustmentWarehouseId = new SimpleStringProperty("");
    private final StringProperty adjustmentCellId = new SimpleStringProperty("");
    private final StringProperty adjustmentReason = new SimpleStringProperty("");

    private final StringProperty reservationMaterial = new SimpleStringProperty("");
    private final StringProperty reservationOrderRef = new SimpleStringProperty("");
    private final StringProperty reservationQuantity = new SimpleStringProperty("");
    private final StringProperty reservationFilterMaterial = new SimpleStringProperty("");

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
            warehouses.setAll(warehouseApi.listWarehouses());
            if (warehouses.isEmpty()) {
                statusMessage.set("Склады не найдены");
            }
        });
    }

    public void loadStock() {
        if (!canView.get()) {
            deny();
            return;
        }
        String warehouseRaw = blankToNull(stockWarehouseId.get());
        String materialRaw = blankToNull(stockMaterialCode.get());
        if (warehouseRaw == null && materialRaw == null) {
            errorMessage.set("Укажите склад или материал для загрузки остатков.");
            return;
        }
        run("Остатки загружены", () -> {
            if (warehouseRaw != null) {
                UUID warehouseId = parseUuid(warehouseRaw, "склад");
                stockRows.setAll(warehouseApi.getStockByWarehouse(warehouseId));
            } else {
                stockRows.setAll(warehouseApi.getStock(materialRaw));
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
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.receipt(
                                    requireText(receiptMaterial.get(), "материал"),
                                    parsePositiveDecimal(receiptQuantity.get(), "количество"),
                                    parseUuid(receiptWarehouseId.get(), "склад"),
                                    parseUuid(receiptCellId.get(), "ячейка")));
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
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.move(
                                    requireText(moveMaterial.get(), "материал"),
                                    parsePositiveDecimal(moveQuantity.get(), "количество"),
                                    parseUuid(moveSourceWarehouseId.get(), "склад источник"),
                                    parseUuid(moveSourceCellId.get(), "ячейка источник"),
                                    parseUuid(moveDestWarehouseId.get(), "склад назначения"),
                                    parseUuid(moveDestCellId.get(), "ячейка назначения")));
            statusMessage.set("Перемещение выполнено: operationId=" + result.operationId());
        });
    }

    public void submitTransferSend() {
        if (!canTransfer.get()) {
            deny();
            return;
        }
        run("Межскладское перемещение (отправка) выполнено", () -> {
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.transferSend(
                                    requireText(transferMaterial.get(), "материал"),
                                    parsePositiveDecimal(transferQuantity.get(), "количество"),
                                    parseUuid(transferSourceWarehouseId.get(), "склад источник"),
                                    parseUuid(transferSourceCellId.get(), "ячейка источник"),
                                    parseUuid(transferDestWarehouseId.get(), "склад назначения")));
            statusMessage.set(
                    "Межскладская отправка выполнена: operationId=" + result.operationId());
        });
    }

    public void submitConsumption() {
        if (!canConsumption.get()) {
            deny();
            return;
        }
        run("Списание выполнено", () -> {
            String basis = requireText(consumptionBasis.get(), "основание");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.consumption(
                                    requireText(consumptionMaterial.get(), "материал"),
                                    parsePositiveDecimal(consumptionQuantity.get(), "количество"),
                                    parseUuid(consumptionWarehouseId.get(), "склад"),
                                    parseUuid(consumptionCellId.get(), "ячейка")));
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
            String reason = requireText(adjustmentReason.get(), "причина");
            OperationResult result =
                    warehouseApi.executeWarehouseOperation(
                            ExecuteOperationCommand.adjustment(
                                    requireText(adjustmentMaterial.get(), "материал"),
                                    parseNonZeroDecimal(
                                            adjustmentQuantityDelta.get(), "количество изменения"),
                                    parseUuid(adjustmentWarehouseId.get(), "склад"),
                                    parseUuid(adjustmentCellId.get(), "ячейка")));
            statusMessage.set(
                    "Корректировка выполнена: operationId="
                            + result.operationId()
                            + "; причина="
                            + reason);
        });
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
            ReservationLinkView created =
                    warehouseApi.createReservationLink(
                            new CreateReservationLinkCommand(
                                    requireText(reservationMaterial.get(), "материал"),
                                    ReservationTargetTypeView.ORDER,
                                    requireText(reservationOrderRef.get(), "заказ"),
                                    parsePositiveDecimal(reservationQuantity.get(), "количество")));
            statusMessage.set("Связь создана: linkId=" + created.linkId());
            reservationFilterMaterial.set(created.materialCode());
            reservationLinks.setAll(warehouseApi.listReservationLinks(created.materialCode()));
        });
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

    public ObservableList<WarehouseView> warehouses() {
        return warehouses;
    }

    public ObservableList<StockView> stockRows() {
        return stockRows;
    }

    public ObservableList<ReservationLinkView> reservationLinks() {
        return reservationLinks;
    }

    public StringProperty stockWarehouseIdProperty() {
        return stockWarehouseId;
    }

    public StringProperty stockMaterialCodeProperty() {
        return stockMaterialCode;
    }

    public StringProperty receiptMaterialProperty() {
        return receiptMaterial;
    }

    public StringProperty receiptQuantityProperty() {
        return receiptQuantity;
    }

    public StringProperty receiptWarehouseIdProperty() {
        return receiptWarehouseId;
    }

    public StringProperty receiptCellIdProperty() {
        return receiptCellId;
    }

    public StringProperty moveMaterialProperty() {
        return moveMaterial;
    }

    public StringProperty moveQuantityProperty() {
        return moveQuantity;
    }

    public StringProperty moveSourceWarehouseIdProperty() {
        return moveSourceWarehouseId;
    }

    public StringProperty moveSourceCellIdProperty() {
        return moveSourceCellId;
    }

    public StringProperty moveDestWarehouseIdProperty() {
        return moveDestWarehouseId;
    }

    public StringProperty moveDestCellIdProperty() {
        return moveDestCellId;
    }

    public StringProperty transferMaterialProperty() {
        return transferMaterial;
    }

    public StringProperty transferQuantityProperty() {
        return transferQuantity;
    }

    public StringProperty transferSourceWarehouseIdProperty() {
        return transferSourceWarehouseId;
    }

    public StringProperty transferSourceCellIdProperty() {
        return transferSourceCellId;
    }

    public StringProperty transferDestWarehouseIdProperty() {
        return transferDestWarehouseId;
    }

    public StringProperty consumptionMaterialProperty() {
        return consumptionMaterial;
    }

    public StringProperty consumptionQuantityProperty() {
        return consumptionQuantity;
    }

    public StringProperty consumptionWarehouseIdProperty() {
        return consumptionWarehouseId;
    }

    public StringProperty consumptionCellIdProperty() {
        return consumptionCellId;
    }

    public StringProperty consumptionBasisProperty() {
        return consumptionBasis;
    }

    public StringProperty adjustmentMaterialProperty() {
        return adjustmentMaterial;
    }

    public StringProperty adjustmentQuantityDeltaProperty() {
        return adjustmentQuantityDelta;
    }

    public StringProperty adjustmentWarehouseIdProperty() {
        return adjustmentWarehouseId;
    }

    public StringProperty adjustmentCellIdProperty() {
        return adjustmentCellId;
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

    private void onSectionOpened(WarehouseSection value) {
        refreshPermissions();
        errorMessage.set("");
        statusMessage.set("");
        switch (value) {
            case WAREHOUSES -> loadWarehouses();
            case STOCK, RECEIPT, MOVE, TRANSFER, CONSUMPTION, ADJUSTMENT, RESERVATIONS -> {
                // forms load on demand
            }
        }
    }

    private boolean isSectionAllowed(WarehouseSection value) {
        return switch (value) {
            case WAREHOUSES, STOCK -> canView.get();
            case RECEIPT -> canReceipt.get();
            case MOVE -> canMove.get();
            case TRANSFER -> canTransfer.get();
            case CONSUMPTION -> canConsumption.get();
            case ADJUSTMENT -> canAdjustment.get();
            case RESERVATIONS -> canReservation.get();
        };
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

    private static String requireText(String raw, String field) {
        String value = blankToNull(raw);
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static UUID parseUuid(String raw, String field) {
        String value = requireText(raw, field);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Некорректный UUID для поля \"" + field + "\"");
        }
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
