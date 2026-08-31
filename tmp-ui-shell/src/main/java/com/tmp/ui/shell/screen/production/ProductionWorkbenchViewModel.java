package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.ItemReleaseView;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualUsageView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.ReleaseResultView;
import com.tmp.production.api.ProductionApplicationApi.TransferCellAllocation;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateLineView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateView;
import com.tmp.production.api.ProductionApplicationApi.CellAllocationView;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityLineView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityResultView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
 * Production workbench ViewModel. Reads/writes go through public Production/Order/Warehouse APIs
 * only — no business logic beyond presentation mapping and button policy.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class ProductionWorkbenchViewModel {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final ProductionQueryApi queryApi;
    private final ProductionApplicationApi applicationApi;
    private final OrderQueryService orderQueryService;
    private final WarehouseApi warehouseApi;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;

    private final StringProperty orderSelectorInput = new SimpleStringProperty("");
    private final StringProperty orderNumber = new SimpleStringProperty("");
    private final StringProperty customerLabel = new SimpleStringProperty("");
    private final StringProperty statusLabel = new SimpleStringProperty("");
    private final StringProperty statusDetailLabel = new SimpleStringProperty("");
    private final StringProperty emptyStateMessage =
            new SimpleStringProperty("Выберите заказ для работы с производством.");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty orderSelected = new SimpleBooleanProperty(false);
    private final BooleanProperty transferPanelVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty releasePanelVisible = new SimpleBooleanProperty(false);

    private final BooleanProperty canAccept = new SimpleBooleanProperty(false);
    private final BooleanProperty canCheck = new SimpleBooleanProperty(false);
    private final BooleanProperty canTransfer = new SimpleBooleanProperty(false);
    private final BooleanProperty canReceipt = new SimpleBooleanProperty(false);
    private final BooleanProperty canRelease = new SimpleBooleanProperty(false);
    private final BooleanProperty canCancel = new SimpleBooleanProperty(false);

    private final ObservableList<ProductionItemRow> itemRows = FXCollections.observableArrayList();
    private final ObservableList<MaterialAvailabilityRow> materialRows =
            FXCollections.observableArrayList();
    private final ObservableList<ProductionHistoryRow> historyRows =
            FXCollections.observableArrayList();
    private final ObservableList<LogicalTransferRow> logicalTransfers =
            FXCollections.observableArrayList();
    private final ObservableList<TransferLineRow> transferLines =
            FXCollections.observableArrayList();
    private final ObservableList<ReleaseMaterialRow> releaseMaterialRows =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> productionCellChoices =
            FXCollections.observableArrayList();

    private final ObjectProperty<LogicalTransferRow> selectedLogicalTransfer =
            new SimpleObjectProperty<>();

    private final ObjectProperty<UUID> selectedTransferLineId = new SimpleObjectProperty<>();

    private UUID currentOrderId;
    private OrderProductionViewStatus currentStatus;
    private TransferTemplateView currentTemplate;
    private ReleasePreviewView currentReleasePreview;
    private UUID releaseProductionWarehouseId;

    public ProductionWorkbenchViewModel(
            ProductionQueryApi queryApi,
            ProductionApplicationApi applicationApi,
            OrderQueryService orderQueryService,
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService) {
        this.queryApi = Objects.requireNonNull(queryApi, "queryApi");
        this.applicationApi = Objects.requireNonNull(applicationApi, "applicationApi");
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.warehouseApi = Objects.requireNonNull(warehouseApi, "warehouseApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService =
                Objects.requireNonNull(authenticationService, "authenticationService");
        selectedLogicalTransfer.addListener((obs, oldValue, newValue) -> refreshActionPolicy());
    }

    public void openSelectedOrder() {
        String raw = blankToEmpty(orderSelectorInput.get()).trim();
        if (raw.isEmpty()) {
            errorMessage.set("Укажите UUID заказа или номер заказа.");
            return;
        }
        run(
                "Заказ открыт",
                () -> {
                    OrderId orderId = resolveOrderId(raw);
                    loadOrder(orderId);
                });
    }

    public void openForOrder(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId");
        orderSelectorInput.set(orderId.value().toString());
        run("Заказ открыт", () -> loadOrder(orderId));
    }

    public void acceptOrder() {
        if (!canAccept.get() || currentOrderId == null) {
            deny();
            return;
        }
        run(
                "Заказ принят в производство",
                () -> {
                    applicationApi.acceptOrderIntoProduction(currentOrderId, currentActor());
                    reloadCurrentOrder();
                });
    }

    public void checkMaterials() {
        if (!canCheck.get() || currentOrderId == null) {
            deny();
            return;
        }
        run(
                "Проверка материалов выполнена",
                () -> {
                    applicationApi.checkMaterialAvailability(currentOrderId);
                    reloadCurrentOrder();
                });
    }

    public void prepareTransfer() {
        if (!canTransfer.get() || currentOrderId == null) {
            deny();
            return;
        }
        run(
                "Шаблон перемещения подготовлен",
                () -> {
                    TransferTemplateView template =
                            applicationApi.prepareMaterialTransferTemplate(currentOrderId);
                    applyTemplate(template);
                    transferPanelVisible.set(true);
                    releasePanelVisible.set(false);
                });
    }

    public void applyTransferRequestedQuantity(TransferLineRow row) {
        Objects.requireNonNull(row, "row");
        if (currentTemplate == null) {
            errorMessage.set(ProductionUiErrorMapper.VALIDATION);
            return;
        }
        UUID lineId = row.lineId();
        run(
                "Количество перемещения обновлено",
                () -> {
                    BigDecimal qty = parseNonNegativeDecimal(row.requestedQuantity(), "количество");
                    TransferTemplateView updated =
                            applicationApi.changeTransferRequestedQuantity(
                                    currentTemplate.templateId(),
                                    lineId,
                                    qty,
                                    currentTemplate.version());
                    applyTemplate(updated, lineId);
                },
                true);
    }

    public void excludeTransferLine(TransferLineRow row) {
        Objects.requireNonNull(row, "row");
        if (currentTemplate == null) {
            return;
        }
        run(
                "Строка исключена",
                () -> {
                    UUID lineId = row.lineId();
                    TransferTemplateView updated =
                            applicationApi.excludeTransferLine(
                                    currentTemplate.templateId(),
                                    lineId,
                                    currentTemplate.version());
                    applyTemplate(updated, lineId);
                },
                true);
    }

    public void restoreTransferLine(TransferLineRow row) {
        Objects.requireNonNull(row, "row");
        if (currentTemplate == null) {
            return;
        }
        run(
                "Строка восстановлена",
                () -> {
                    UUID lineId = row.lineId();
                    TransferTemplateView updated =
                            applicationApi.restoreTransferLine(
                                    currentTemplate.templateId(),
                                    lineId,
                                    currentTemplate.version());
                    applyTemplate(updated, lineId);
                },
                true);
    }

    public void confirmTransfer() {
        if (!canTransfer.get() || currentTemplate == null) {
            deny();
            return;
        }
        run(
                "Перемещение создано",
                () -> {
                    List<TransferCellAllocation> allocations = buildTransferAllocations();
                    applicationApi.confirmMaterialTransferCreate(
                            currentTemplate.templateId(),
                            currentTemplate.version(),
                            allocations);
                    transferPanelVisible.set(false);
                    currentTemplate = null;
                    transferLines.clear();
                    reloadCurrentOrder();
                });
    }

    public TransferAllocationRow addTransferAllocation(TransferLineRow line) {
        Objects.requireNonNull(line, "line");
        return line.addAllocation();
    }

    public void removeTransferAllocation(TransferLineRow line, TransferAllocationRow allocation) {
        Objects.requireNonNull(line, "line");
        Objects.requireNonNull(allocation, "allocation");
        line.removeAllocation(allocation);
    }

    public ReleaseCellAllocationRow addReleaseAllocation(ReleaseMaterialRow material) {
        Objects.requireNonNull(material, "material");
        return material.addAllocation();
    }

    public void removeReleaseAllocation(
            ReleaseMaterialRow material, ReleaseCellAllocationRow allocation) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(allocation, "allocation");
        material.removeAllocation(allocation);
    }

    public void confirmReceipt() {
        if (!canReceipt.get()) {
            deny();
            return;
        }
        LogicalTransferRow selected = selectedLogicalTransfer.get();
        if (selected == null) {
            errorMessage.set("Выберите перемещение для подтверждения получения.");
            return;
        }
        run(
                "Получение подтверждено",
                () -> {
                    ReceiptResultView result =
                            applicationApi.confirmMaterialReceipt(selected.id());
                    if (result.status() == ReceiptStatusView.ALREADY_RECEIVED) {
                        statusMessage.set("Получение уже было подтверждено ранее.");
                    } else {
                        statusMessage.set("Получение материалов подтверждено.");
                    }
                    reloadCurrentOrder();
                });
    }

    public void prepareRelease() {
        if (!canRelease.get() || currentOrderId == null) {
            deny();
            return;
        }
        run(
                "Предпросмотр выпуска подготовлен",
                () -> {
                    List<ItemReleaseView> releases = buildItemReleasesFromRows();
                    ReleasePreviewView preview =
                            applicationApi.prepareRelease(currentOrderId, releases);
                    UUID productionWarehouseId =
                            applicationApi.warehouseScope().productionWarehouseId();
                    applyReleasePreview(preview, productionWarehouseId);
                    releasePanelVisible.set(true);
                    transferPanelVisible.set(false);
                });
    }

    public void confirmRelease() {
        if (!canRelease.get() || currentOrderId == null || currentReleasePreview == null) {
            deny();
            return;
        }
        run(
                "Изделия выпущены",
                () -> {
                    List<ItemReleaseView> releases = currentReleasePreview.itemReleases();
                    List<MaterialActualUsageView> usages = buildMaterialActualUsages();
                    ReleaseResultView result =
                            applicationApi.releaseProducts(currentOrderId, releases, usages);
                    statusMessage.set("Выпуск выполнен: " + result.documentId());
                    releasePanelVisible.set(false);
                    currentReleasePreview = null;
                    releaseMaterialRows.clear();
                    reloadCurrentOrder();
                });
    }

    public void cancelProduction() {
        if (!canCancel.get() || currentOrderId == null) {
            deny();
            return;
        }
        run(
                "Производство заказа отменено",
                () -> {
                    applicationApi.cancelOrderProduction(currentOrderId, Optional.empty());
                    reloadCurrentOrder();
                });
    }

    public void refresh() {
        if (currentOrderId == null) {
            clearOrderState();
            return;
        }
        run("Данные обновлены", this::reloadCurrentOrder);
    }

    public StringProperty orderSelectorInputProperty() {
        return orderSelectorInput;
    }

    public StringProperty orderNumberProperty() {
        return orderNumber;
    }

    public StringProperty customerLabelProperty() {
        return customerLabel;
    }

    public StringProperty statusLabelProperty() {
        return statusLabel;
    }

    public StringProperty statusDetailLabelProperty() {
        return statusDetailLabel;
    }

    public StringProperty emptyStateMessageProperty() {
        return emptyStateMessage;
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

    public BooleanProperty orderSelectedProperty() {
        return orderSelected;
    }

    public BooleanProperty transferPanelVisibleProperty() {
        return transferPanelVisible;
    }

    public BooleanProperty releasePanelVisibleProperty() {
        return releasePanelVisible;
    }

    public BooleanProperty canAcceptProperty() {
        return canAccept;
    }

    public BooleanProperty canCheckProperty() {
        return canCheck;
    }

    public BooleanProperty canTransferProperty() {
        return canTransfer;
    }

    public BooleanProperty canReceiptProperty() {
        return canReceipt;
    }

    public BooleanProperty canReleaseProperty() {
        return canRelease;
    }

    public BooleanProperty canCancelProperty() {
        return canCancel;
    }

    public ObservableList<ProductionItemRow> itemRows() {
        return itemRows;
    }

    public ObservableList<MaterialAvailabilityRow> materialRows() {
        return materialRows;
    }

    public ObservableList<ProductionHistoryRow> historyRows() {
        return historyRows;
    }

    public ObservableList<LogicalTransferRow> logicalTransfers() {
        return logicalTransfers;
    }

    public ObservableList<TransferLineRow> transferLines() {
        return transferLines;
    }

    public ObservableList<ReleaseMaterialRow> releaseMaterialRows() {
        return releaseMaterialRows;
    }

    public ObservableList<StorageCellChoice> productionCellChoices() {
        return productionCellChoices;
    }

    public ObjectProperty<LogicalTransferRow> selectedLogicalTransferProperty() {
        return selectedLogicalTransfer;
    }

    public ObjectProperty<UUID> selectedTransferLineIdProperty() {
        return selectedTransferLineId;
    }

    public void selectTransferLine(UUID lineId) {
        selectedTransferLineId.set(lineId);
    }

    public TransferLineRow findTransferLine(UUID lineId) {
        if (lineId == null) {
            return null;
        }
        return transferLines.stream()
                .filter(row -> row.lineId().equals(lineId))
                .findFirst()
                .orElse(null);
    }

    public UUID currentOrderId() {
        return currentOrderId;
    }

    public OrderProductionViewStatus currentStatus() {
        return currentStatus;
    }

    public TransferTemplateView currentTemplate() {
        return currentTemplate;
    }

    public ReleasePreviewView currentReleasePreview() {
        return currentReleasePreview;
    }

    private void loadOrder(OrderId orderId) {
        OrderDto order =
                orderQueryService
                        .getOrder(orderId)
                        .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        currentOrderId = order.orderId().value();
        orderNumber.set(order.orderNumber());
        customerLabel.set(
                blankToDash(order.customerName())
                        + (order.customerRef() == null || order.customerRef().isBlank()
                                ? ""
                                : " (" + order.customerRef() + ")"));
        orderSelected.set(true);
        emptyStateMessage.set("");
        reloadCurrentOrder();
    }

    private void reloadCurrentOrder() {
        if (currentOrderId == null) {
            clearOrderState();
            return;
        }
        OrderProductionView view = queryApi.getOrderProductionView(currentOrderId);
        currentStatus = view.status();
        statusLabel.set(ProductionPresentationLabels.orderStatus(view.status()));
        statusDetailLabel.set(ProductionPresentationLabels.orderStatusDetail(view.status()));

        PageResult<OrderItemDto> itemsPage =
                orderQueryService.getOrderItems(OrderId.of(currentOrderId), PageRequest.firstPage());
        List<ProductionItemRow> mappedItems = new ArrayList<>();
        for (OrderItemDto item : itemsPage.content()) {
            Optional<ItemProductionStateView> state =
                    queryApi.getItemProductionState(item.orderItemId().value());
            mappedItems.add(mapItemRow(item, state.orElse(null)));
        }
        itemRows.setAll(mappedItems);

        materialRows.clear();
        if (view.status() == OrderProductionViewStatus.IN_PRODUCTION) {
            queryApi.getMaterialAvailabilityResult(currentOrderId)
                    .ifPresent(
                            result -> materialRows.setAll(mapMaterialRows(result)));
        }

        List<ProductionHistoryEntryView> history = queryApi.listProductionHistory(currentOrderId);
        historyRows.setAll(mapHistoryRows(history));

        List<LogicalTransferView> transfers = applicationApi.listLogicalTransfers(currentOrderId);
        UUID previouslySelected =
                selectedLogicalTransfer.get() == null
                        ? null
                        : selectedLogicalTransfer.get().id();
        List<LogicalTransferRow> transferRows = mapLogicalTransfers(transfers);
        logicalTransfers.setAll(transferRows);
        if (previouslySelected != null) {
            selectedLogicalTransfer.set(
                    transferRows.stream()
                            .filter(row -> row.id().equals(previouslySelected))
                            .findFirst()
                            .orElse(transferRows.isEmpty() ? null : transferRows.get(0)));
        } else if (!transferRows.isEmpty() && selectedLogicalTransfer.get() == null) {
            selectedLogicalTransfer.set(transferRows.get(0));
        } else if (transferRows.isEmpty()) {
            selectedLogicalTransfer.set(null);
        }

        refreshActionPolicy();
    }

    private void applyTemplate(TransferTemplateView template) {
        applyTemplate(template, selectedTransferLineId.get());
    }

    private void applyTemplate(TransferTemplateView template, UUID reselectLineId) {
        currentTemplate = template;
        releaseProductionWarehouseId = template.destinationWarehouseId();
        List<StorageCellChoice> sourceCells =
                loadCells(template.sourceWarehouseId());
        List<StorageCellChoice> destCells =
                loadCells(template.destinationWarehouseId());
        Map<UUID, List<PreservedTransferAllocation>> previous =
                snapshotTransferAllocations();
        List<TransferLineRow> rows = new ArrayList<>();
        for (TransferTemplateLineView line : template.lines()) {
            TransferLineRow row =
                    new TransferLineRow(
                            line.lineId(),
                            line.materialReferenceId(),
                            formatMaterial(
                                    line.materialCode(),
                                    line.materialName(),
                                    line.color(),
                                    line.unitOfMeasure()),
                            line.recommendedQuantity().toPlainString(),
                            line.requiredQuantity().toPlainString(),
                            line.requestedQuantity().toPlainString(),
                            line.included());
            row.sourceCellChoices().setAll(sourceCells);
            row.destinationCellChoices().setAll(destCells);
            // No auto-first-cell selection. Excluded lines stay empty; included lines keep
            // prior explicit allocations (not scaled) when requested quantity changes.
            if (line.included()) {
                List<PreservedTransferAllocation> preserved =
                        previous.getOrDefault(line.lineId(), List.of());
                for (PreservedTransferAllocation item : preserved) {
                    TransferAllocationRow allocation = row.addAllocation();
                    allocation.setSourceCell(findChoice(sourceCells, item.sourceCellId()));
                    allocation.setDestinationCell(findChoice(destCells, item.destinationCellId()));
                    allocation.setQuantity(item.quantity());
                }
            }
            rows.add(row);
        }
        transferLines.setAll(rows);
        if (reselectLineId != null
                && rows.stream().anyMatch(row -> row.lineId().equals(reselectLineId))) {
            selectedTransferLineId.set(reselectLineId);
        } else if (reselectLineId != null) {
            selectedTransferLineId.set(null);
        }
    }

    private void applyReleasePreview(ReleasePreviewView preview, UUID productionWarehouseId) {
        currentReleasePreview = preview;
        releaseProductionWarehouseId = productionWarehouseId;
        List<StorageCellChoice> cells =
                productionWarehouseId == null ? List.of() : loadCells(productionWarehouseId);
        productionCellChoices.setAll(cells);

        List<ReleaseMaterialRow> rows = new ArrayList<>();
        for (var actual : preview.defaultActuals()) {
            String materialName =
                    preview.plannedMaterialLines().stream()
                            .filter(
                                    line ->
                                            line.sourceOrderItemId()
                                                            .equals(actual.sourceOrderItemId())
                                                    && line.materialReferenceId()
                                                            .equals(actual.materialReferenceId()))
                            .map(line -> line.materialName().orElse(line.materialReferenceId().toString()))
                            .findFirst()
                            .orElse(actual.materialReferenceId().toString());
            ReleaseMaterialRow row =
                    new ReleaseMaterialRow(
                            actual.sourceOrderItemId(),
                            actual.materialReferenceId(),
                            materialName,
                            actual.plannedQuantity().toPlainString(),
                            actual.actualQuantity().toPlainString());
            row.cellChoices().setAll(cells);
            // No auto-first-cell selection — user adds allocations explicitly.
            rows.add(row);
        }
        releaseMaterialRows.setAll(rows);
    }

    private List<TransferCellAllocation> buildTransferAllocations() {
        List<TransferCellAllocation> allocations = new ArrayList<>();
        for (TransferLineRow row : transferLines) {
            if (!row.included()) {
                continue;
            }
            if (row.allocations().isEmpty()) {
                throw new IllegalArgumentException(
                        "Добавьте хотя бы одно распределение ячеек для каждой включённой строки");
            }
            BigDecimal requested =
                    parseNonNegativeDecimal(row.requestedQuantity(), "запрошенное количество");
            BigDecimal sum = BigDecimal.ZERO;
            Set<String> pairs = new HashSet<>();
            for (TransferAllocationRow allocation : row.allocations()) {
                if (allocation.sourceCell() == null || allocation.destinationCell() == null) {
                    throw new IllegalArgumentException(
                            "Выберите ячейки источника и назначения для каждого распределения");
                }
                String pairKey =
                        allocation.sourceCell().id() + "->" + allocation.destinationCell().id();
                if (!pairs.add(pairKey)) {
                    throw new IllegalArgumentException(
                            "Дублирующая пара ячеек источника/назначения в одной строке шаблона");
                }
                BigDecimal qty =
                        parsePositiveDecimal(allocation.quantity(), "количество размещения");
                sum = sum.add(qty);
                allocations.add(
                        new TransferCellAllocation(
                                row.lineId(),
                                allocation.sourceCell().id(),
                                allocation.destinationCell().id(),
                                qty));
            }
            if (sum.compareTo(requested) != 0) {
                throw new IllegalArgumentException(
                        "Сумма распределений ("
                                + sum.toPlainString()
                                + ") должна равняться запрошенному количеству ("
                                + requested.toPlainString()
                                + ")");
            }
        }
        if (allocations.isEmpty()) {
            throw new IllegalArgumentException("Нет строк для создания перемещения");
        }
        return allocations;
    }

    private List<ItemReleaseView> buildItemReleasesFromRows() {
        List<ItemReleaseView> releases = new ArrayList<>();
        for (ProductionItemRow row : itemRows) {
            String raw = blankToEmpty(row.releaseQuantityInput()).trim();
            if (raw.isEmpty() || "0".equals(raw)) {
                continue;
            }
            long qty;
            try {
                qty = Long.parseLong(raw);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid release quantity");
            }
            if (qty <= 0) {
                continue;
            }
            if (qty > row.activeQuantityValue()) {
                throw new IllegalArgumentException(
                        "Release quantity exceeds active production quantity");
            }
            releases.add(new ItemReleaseView(row.orderItemId(), qty));
        }
        if (releases.isEmpty()) {
            throw new IllegalArgumentException("Укажите количество выпуска хотя бы для одной позиции");
        }
        return releases;
    }

    private List<MaterialActualUsageView> buildMaterialActualUsages() {
        List<MaterialActualUsageView> usages = new ArrayList<>();
        for (ReleaseMaterialRow row : releaseMaterialRows) {
            BigDecimal actual = parseNonNegativeDecimal(row.actualQuantity(), "фактическое количество");
            List<CellAllocationView> allocations = new ArrayList<>();
            if (actual.signum() == 0) {
                if (!row.allocations().isEmpty()) {
                    throw new IllegalArgumentException(
                            "При фактическом количестве 0 распределения по ячейкам должны быть пустыми");
                }
            } else {
                if (row.allocations().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Добавьте хотя бы одно распределение по ячейке производства"
                                    + " для положительного факта");
                }
                BigDecimal sum = BigDecimal.ZERO;
                Set<UUID> cells = new HashSet<>();
                for (ReleaseCellAllocationRow allocation : row.allocations()) {
                    if (allocation.productionCell() == null) {
                        throw new IllegalArgumentException(
                                "Выберите ячейку склада производства для каждого распределения");
                    }
                    if (!cells.add(allocation.productionCell().id())) {
                        throw new IllegalArgumentException(
                                "Дублирующая ячейка производства в одном материале выпуска");
                    }
                    BigDecimal qty =
                            parsePositiveDecimal(
                                    allocation.quantity(), "количество размещения выпуска");
                    sum = sum.add(qty);
                    allocations.add(
                            new CellAllocationView(allocation.productionCell().id(), qty));
                }
                if (sum.compareTo(actual) != 0) {
                    throw new IllegalArgumentException(
                            "Сумма распределений ("
                                    + sum.toPlainString()
                                    + ") должна равняться фактическому количеству ("
                                    + actual.toPlainString()
                                    + ")");
                }
            }
            usages.add(
                    new MaterialActualUsageView(
                            row.sourceOrderItemId(),
                            row.materialReferenceId(),
                            actual,
                            allocations));
        }
        return usages;
    }

    private Map<UUID, List<PreservedTransferAllocation>> snapshotTransferAllocations() {
        Map<UUID, List<PreservedTransferAllocation>> snapshot = new HashMap<>();
        for (TransferLineRow row : transferLines) {
            if (!row.included() || row.allocations().isEmpty()) {
                continue;
            }
            List<PreservedTransferAllocation> items = new ArrayList<>();
            for (TransferAllocationRow allocation : row.allocations()) {
                items.add(
                        new PreservedTransferAllocation(
                                allocation.sourceCell() == null
                                        ? null
                                        : allocation.sourceCell().id(),
                                allocation.destinationCell() == null
                                        ? null
                                        : allocation.destinationCell().id(),
                                allocation.quantity()));
            }
            snapshot.put(row.lineId(), items);
        }
        return snapshot;
    }

    private static StorageCellChoice findChoice(List<StorageCellChoice> choices, UUID cellId) {
        if (cellId == null) {
            return null;
        }
        for (StorageCellChoice choice : choices) {
            if (choice.id().equals(cellId)) {
                return choice;
            }
        }
        return null;
    }

    private record PreservedTransferAllocation(
            UUID sourceCellId, UUID destinationCellId, String quantity) {}

    private List<StorageCellChoice> loadCells(UUID warehouseId) {
        List<StorageCellView> cells = warehouseApi.listStorageCells(warehouseId);
        List<StorageCellChoice> choices = new ArrayList<>();
        for (StorageCellView cell : cells) {
            if (cell.active()) {
                choices.add(StorageCellChoice.from(cell));
            }
        }
        return choices;
    }

    private ProductionItemRow mapItemRow(OrderItemDto item, ItemProductionStateView state) {
        String position =
                blankToEmpty(item.externalPositionNumber()).isBlank()
                        ? item.orderItemId().value().toString()
                        : item.externalPositionNumber() + " / " + item.orderItemId().value();
        if (state == null) {
            return new ProductionItemRow(
                    item.orderItemId().value(),
                    position,
                    "—",
                    "—",
                    "—",
                    "—",
                    "—",
                    "—",
                    0L,
                    "");
        }
        return new ProductionItemRow(
                item.orderItemId().value(),
                position,
                ProductionPresentationLabels.itemStatus(state.status()),
                Long.toString(state.orderedQuantity()),
                Long.toString(state.activeProductionQuantity()),
                Long.toString(state.releasedQuantity()),
                state.specificationId().toString(),
                ProductionPresentationLabels.cuttingPlanRefs(state),
                state.activeProductionQuantity(),
                "");
    }

    private List<MaterialAvailabilityRow> mapMaterialRows(MaterialAvailabilityResultView result) {
        List<MaterialAvailabilityRow> rows = new ArrayList<>();
        for (MaterialAvailabilityLineView line : result.lines()) {
            boolean unresolved =
                    ProductionPresentationLabels.isUnresolvedOrAmbiguous(line.status());
            String material =
                    formatMaterial(
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure());
            rows.add(
                    new MaterialAvailabilityRow(
                            material,
                            line.requiredQuantity().toPlainString(),
                            unresolved ? "—" : line.mainWarehouseAvailable().toPlainString(),
                            unresolved
                                    ? "—"
                                    : line.productionWarehouseAvailable().toPlainString(),
                            unresolved ? "—" : line.totalAvailable().toPlainString(),
                            unresolved
                                    ? ProductionPresentationLabels.materialLineStatus(line)
                                    : line.deficit().toPlainString(),
                            ProductionPresentationLabels.planningSource(line.planningSource()),
                            ProductionPresentationLabels.materialLineStatus(line),
                            unresolved));
        }
        return rows;
    }

    private List<ProductionHistoryRow> mapHistoryRows(List<ProductionHistoryEntryView> history) {
        List<ProductionHistoryRow> rows = new ArrayList<>();
        for (ProductionHistoryEntryView entry : history) {
            rows.add(
                    new ProductionHistoryRow(
                            entry.entryId(),
                            TIME_FORMAT.format(entry.occurredAt()),
                            ProductionPresentationLabels.historyType(entry.historyType()),
                            entry.actorRef().orElse("—"),
                            entry.summary().orElse("")));
        }
        return rows;
    }

    private List<LogicalTransferRow> mapLogicalTransfers(List<LogicalTransferView> transfers) {
        List<LogicalTransferRow> rows = new ArrayList<>();
        for (LogicalTransferView transfer : transfers) {
            rows.add(
                    new LogicalTransferRow(
                            transfer.id(),
                            transfer.templateId(),
                            TIME_FORMAT.format(transfer.createdAt()),
                            TransferReceiptEligibility.lifecycleSummary(
                                    transfer.warehouseOperations(), warehouseApi),
                            transfer.warehouseOperations()));
        }
        return rows;
    }

    private void refreshActionPolicy() {
        ProductionActionPolicy.Permissions permissions =
                new ProductionActionPolicy.Permissions(
                        has(UiShellScreens.PRODUCTION_ACCEPT_PERMISSION),
                        has(UiShellScreens.PRODUCTION_CHECK_PERMISSION),
                        has(UiShellScreens.PRODUCTION_TRANSFER_PERMISSION),
                        has(UiShellScreens.PRODUCTION_RECEIPT_PERMISSION),
                        has(UiShellScreens.PRODUCTION_RELEASE_PERMISSION),
                        has(UiShellScreens.PRODUCTION_CANCEL_PERMISSION));
        LogicalTransferRow selected = selectedLogicalTransfer.get();
        boolean transferReceivable =
                selected != null
                        && TransferReceiptEligibility.isReceivable(
                                selected.warehouseOperations(), warehouseApi);
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        orderSelected.get(), currentStatus, permissions, transferReceivable);
        canAccept.set(decision.accept());
        canCheck.set(decision.check());
        canTransfer.set(decision.transfer());
        canReceipt.set(decision.receipt());
        canRelease.set(decision.release());
        canCancel.set(decision.cancel());
    }

    private void clearOrderState() {
        currentOrderId = null;
        currentStatus = null;
        currentTemplate = null;
        currentReleasePreview = null;
        orderSelected.set(false);
        orderNumber.set("");
        customerLabel.set("");
        statusLabel.set("");
        statusDetailLabel.set("");
        emptyStateMessage.set("Выберите заказ для работы с производством.");
        itemRows.clear();
        materialRows.clear();
        historyRows.clear();
        logicalTransfers.clear();
        transferLines.clear();
        releaseMaterialRows.clear();
        productionCellChoices.clear();
        selectedLogicalTransfer.set(null);
        selectedTransferLineId.set(null);
        transferPanelVisible.set(false);
        releasePanelVisible.set(false);
        refreshActionPolicy();
    }

    private OrderId resolveOrderId(String raw) {
        try {
            return OrderId.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException ignored) {
            PageResult<OrderSummaryDto> page =
                    orderQueryService.searchOrders(
                            OrderSearchCriteria.builder().orderNumber(raw.trim()).build(),
                            PageRequest.firstPage());
            if (page.content().isEmpty()) {
                throw new IllegalArgumentException("Order not found");
            }
            if (page.content().size() > 1) {
                throw new IllegalArgumentException(
                        "Найдено несколько заказов с таким номером. Укажите UUID.");
            }
            return page.content().get(0).orderId();
        }
    }

    private String currentActor() {
        return authenticationService
                .currentSession()
                .map(session -> session.login().value())
                .orElse("system");
    }

    private void run(String successMessage, Runnable action) {
        run(successMessage, action, false);
    }

    private void run(String successMessage, Runnable action, boolean refreshOnStale) {
        loading.set(true);
        errorMessage.set("");
        statusMessage.set("");
        try {
            action.run();
            if (statusMessage.get() == null || statusMessage.get().isBlank()) {
                statusMessage.set(successMessage);
            }
        } catch (AccessDeniedException ex) {
            errorMessage.set(ProductionUiErrorMapper.text(ex));
            statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
        } catch (RuntimeException ex) {
            errorMessage.set(ProductionUiErrorMapper.text(ex));
            statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
            if (refreshOnStale || ProductionUiErrorMapper.isConcurrentOrStale(ex)) {
                try {
                    if (currentOrderId != null) {
                        reloadCurrentOrder();
                    }
                } catch (RuntimeException ignored) {
                    // keep original error
                }
            }
        } finally {
            loading.set(false);
        }
    }

    private void deny() {
        errorMessage.set(ProductionUiErrorMapper.ACCESS_DENIED);
        statusMessage.set("");
    }

    private boolean has(String permission) {
        return authorizationService.hasPermission(PermissionId.of(permission));
    }

    private static String formatMaterial(
            String code, String name, String color, String unitOfMeasure) {
        StringBuilder builder = new StringBuilder();
        builder.append(blankToDash(code));
        if (name != null && !name.isBlank()) {
            builder.append(" — ").append(name);
        }
        if (color != null && !color.isBlank()) {
            builder.append(" / ").append(color);
        }
        if (unitOfMeasure != null && !unitOfMeasure.isBlank()) {
            builder.append(" (").append(unitOfMeasure).append(')');
        }
        return builder.toString();
    }

    private static BigDecimal parsePositiveDecimal(String raw, String field) {
        BigDecimal value = parseNonNegativeDecimal(raw, field);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static BigDecimal parseNonNegativeDecimal(String raw, String field) {
        String value = blankToEmpty(raw).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        try {
            BigDecimal parsed = new BigDecimal(value);
            if (parsed.signum() < 0) {
                throw new IllegalArgumentException(field + " must be >= 0");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToDash(String value) {
        if (value == null || value.isBlank()) {
            return "—";
        }
        return value;
    }
}
