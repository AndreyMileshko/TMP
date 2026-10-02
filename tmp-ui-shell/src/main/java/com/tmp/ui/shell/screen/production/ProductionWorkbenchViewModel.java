package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderForProductionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemForProductionDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.ItemReleaseView;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualUsageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.SubmitMaterialRequirementResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.ReleaseResultView;
import com.tmp.production.api.ProductionApplicationApi.CellAllocationView;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityLineView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityResultView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    private static final String TREE_LOAD_FAILED = "Не удалось загрузить производство";
    private static final String EMPTY_TREE = "Нет заказов в производстве";
    private static final String EMPTY_FILTERED = "Нет заказов, подходящих под фильтр";

    public enum ScreenMode {
        TREE,
        DETAIL
    }

    public static final class TreeOrderModel {
        private final ProductionTreeNode orderNode;
        private final List<ProductionTreeNode> items;
        private final boolean expanded;

        public TreeOrderModel(
                ProductionTreeNode orderNode, List<ProductionTreeNode> items, boolean expanded) {
            this.orderNode = Objects.requireNonNull(orderNode, "orderNode");
            this.items = List.copyOf(items);
            this.expanded = expanded;
        }

        public ProductionTreeNode orderNode() {
            return orderNode;
        }

        public List<ProductionTreeNode> items() {
            return items;
        }

        public boolean expanded() {
            return expanded;
        }
    }

    public static final class LoadedProductionItem {
        private final OrderItemDto item;
        private final ItemProductionStateView state;
        private final int index1Based;

        LoadedProductionItem(OrderItemDto item, ItemProductionStateView state, int index1Based) {
            this.item = Objects.requireNonNull(item, "item");
            this.state = state;
            this.index1Based = index1Based;
        }

        ProductionOrderItemRef ref() {
            return new ProductionOrderItemRef(
                    item.orderId().value(), item.orderItemId().value());
        }
    }

    public static final class LoadedProductionOrder {
        private final UUID orderId;
        private final String orderNumber;
        private final String customerName;
        private final OrderProductionViewStatus status;
        private final OrderProductionListFacts facts;
        private final List<LoadedProductionItem> items;
        private final Map<UUID, Long> orderedQuantityFallback;

        LoadedProductionOrder(
                UUID orderId,
                String orderNumber,
                String customerName,
                OrderProductionViewStatus status,
                OrderProductionListFacts facts,
                List<LoadedProductionItem> items,
                Map<UUID, Long> orderedQuantityFallback) {
            this.orderId = Objects.requireNonNull(orderId, "orderId");
            this.orderNumber = Objects.requireNonNull(orderNumber, "orderNumber");
            this.customerName = customerName == null ? "" : customerName;
            this.status = Objects.requireNonNull(status, "status");
            this.facts = facts;
            this.items = List.copyOf(items);
            this.orderedQuantityFallback = Map.copyOf(orderedQuantityFallback);
        }

        List<ProductionOrderItemRef> allItemRefs() {
            return items.stream().map(LoadedProductionItem::ref).toList();
        }
    }

    private final ProductionQueryApi queryApi;
    private final ProductionApplicationApi applicationApi;
    private final OrderQueryService orderQueryService;
    private final OrderWorklistQuery worklistQuery;
    private final WarehouseApi warehouseApi;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final Clock clock;
    private final ZoneId zoneId;

    private final ProductionTreeSelectionModel treeSelection = new ProductionTreeSelectionModel();

    private final StringProperty orderSelectorInput = new SimpleStringProperty("");
    private final StringProperty orderNumber = new SimpleStringProperty("");
    private final StringProperty customerLabel = new SimpleStringProperty("");
    private final StringProperty statusLabel = new SimpleStringProperty("");
    private final StringProperty statusDetailLabel = new SimpleStringProperty("");
    private final StringProperty emptyStateMessage = new SimpleStringProperty(EMPTY_TREE);
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty selectionCountLabel =
            new SimpleStringProperty("Выбрано: 0 позиций");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty orderSelected = new SimpleBooleanProperty(false);
    private final BooleanProperty materialRequirementPanelVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty requirementSubmitted = new SimpleBooleanProperty(false);
    private final BooleanProperty releasePanelVisible = new SimpleBooleanProperty(false);

    private final BooleanProperty canAccept = new SimpleBooleanProperty(false);
    private final BooleanProperty canCheck = new SimpleBooleanProperty(false);
    private final BooleanProperty canTransfer = new SimpleBooleanProperty(false);
    private final BooleanProperty canReceipt = new SimpleBooleanProperty(false);
    private final BooleanProperty canRelease = new SimpleBooleanProperty(false);
    private final BooleanProperty canCancel = new SimpleBooleanProperty(false);

    private final BooleanProperty treeVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty detailVisible = new SimpleBooleanProperty(false);

    private final StringProperty searchText = new SimpleStringProperty("");
    private final ObjectProperty<ProductionTreeStatusFilter> statusFilter =
            new SimpleObjectProperty<>(ProductionTreeStatusFilter.IN_PROGRESS);
    private final ObjectProperty<OrderListPeriod.Preset> periodPreset =
            new SimpleObjectProperty<>(OrderListPeriod.Preset.LAST_30_DAYS);
    private final ObjectProperty<List<TreeOrderModel>> visibleTree =
            new SimpleObjectProperty<>(List.of());

    private final ObservableList<ProductionItemRow> itemRows = FXCollections.observableArrayList();
    private final ObservableList<MaterialAvailabilityRow> materialRows =
            FXCollections.observableArrayList();
    private final ObservableList<ProductionHistoryRow> historyRows =
            FXCollections.observableArrayList();
    private final ObservableList<LogicalTransferRow> logicalTransfers =
            FXCollections.observableArrayList();
    private final ObservableList<MaterialRequirementLineRow> requirementLines =
            FXCollections.observableArrayList();
    private final ObservableList<ReleaseMaterialRow> releaseMaterialRows =
            FXCollections.observableArrayList();
    private final ObservableList<StorageCellChoice> productionCellChoices =
            FXCollections.observableArrayList();

    private final ObjectProperty<LogicalTransferRow> selectedLogicalTransfer =
            new SimpleObjectProperty<>();

    private final ObjectProperty<UUID> selectedRequirementLineId = new SimpleObjectProperty<>();

    private ScreenMode screenMode = ScreenMode.TREE;
    private List<LoadedProductionOrder> authoritativeOrders = List.of();
    private final Set<UUID> expandedOrderIds = new LinkedHashSet<>();

    private UUID currentOrderId;
    private OrderProductionViewStatus currentStatus;
    private MaterialRequirementView currentRequirement;
    private ReleasePreviewView currentReleasePreview;
    private UUID releaseProductionWarehouseId;

    public ProductionWorkbenchViewModel(
            ProductionQueryApi queryApi,
            ProductionApplicationApi applicationApi,
            OrderQueryService orderQueryService,
            OrderWorklistQuery worklistQuery,
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService) {
        this(
                queryApi,
                applicationApi,
                orderQueryService,
                worklistQuery,
                warehouseApi,
                authorizationService,
                authenticationService,
                Clock.systemDefaultZone(),
                ZoneId.systemDefault());
    }

    ProductionWorkbenchViewModel(
            ProductionQueryApi queryApi,
            ProductionApplicationApi applicationApi,
            OrderQueryService orderQueryService,
            OrderWorklistQuery worklistQuery,
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            Clock clock,
            ZoneId zoneId) {
        this.queryApi = Objects.requireNonNull(queryApi, "queryApi");
        this.applicationApi = Objects.requireNonNull(applicationApi, "applicationApi");
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.worklistQuery = Objects.requireNonNull(worklistQuery, "worklistQuery");
        this.warehouseApi = Objects.requireNonNull(warehouseApi, "warehouseApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService =
                Objects.requireNonNull(authenticationService, "authenticationService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        selectedLogicalTransfer.addListener((obs, oldValue, newValue) -> refreshActionPolicy());
        treeSelection
                .selectedCountProperty()
                .addListener(
                        (obs, oldValue, newValue) ->
                                selectionCountLabel.set(
                                        "Выбрано: " + newValue.intValue() + " позиций"));
        searchText.addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilter.addListener((obs, oldValue, newValue) -> applyFilters());
        periodPreset.addListener(
                (obs, oldValue, newValue) -> {
                    if (oldValue != null && !detailMode()) {
                        loadTree();
                    }
                });
    }

    public void loadTree() {
        if (loading.get()) {
            return;
        }
        run(null, this::loadTreeInternal);
    }

    public void applyFilters() {
        if (detailMode()) {
            return;
        }
        List<LoadedProductionOrder> visible = visibleOrders();
        visibleTree.set(buildVisibleTreeModels(visible));
        updateTreeEmptyStateMessage(visible);
    }

    public List<LoadedProductionOrder> visibleOrders() {
        String needle = normalizeSearchNeedle(searchText.get());
        ProductionTreeStatusFilter filter =
                statusFilter.get() == null
                        ? ProductionTreeStatusFilter.IN_PROGRESS
                        : statusFilter.get();
        List<LoadedProductionOrder> result = new ArrayList<>();
        for (LoadedProductionOrder order : authoritativeOrders) {
            if (!filter.matches(order.status)) {
                continue;
            }
            if (needle.isEmpty()) {
                result.add(order);
                continue;
            }
            if (orderMatchesSearch(order, needle)) {
                result.add(order);
                continue;
            }
            List<LoadedProductionItem> matchingItems = new ArrayList<>();
            for (LoadedProductionItem item : order.items) {
                if (itemMatchesSearch(item, needle)) {
                    matchingItems.add(item);
                }
            }
            if (!matchingItems.isEmpty()) {
                result.add(
                        new LoadedProductionOrder(
                                order.orderId,
                                order.orderNumber,
                                order.customerName,
                                order.status,
                                order.facts,
                                matchingItems,
                                order.orderedQuantityFallback));
            }
        }
        return List.copyOf(result);
    }

    public ProductionTreeSelectionModel selectionModel() {
        return treeSelection;
    }

    public void selectOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        LoadedProductionOrder order = findAuthoritativeOrder(orderId);
        if (order != null) {
            treeSelection.selectAll(order.allItemRefs());
        }
    }

    public void deselectOrder(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        LoadedProductionOrder order = findAuthoritativeOrder(orderId);
        if (order != null) {
            treeSelection.deselectAll(order.allItemRefs());
        }
    }

    public void setItemSelected(ProductionOrderItemRef ref, boolean selected) {
        treeSelection.setItemSelected(ref, selected);
    }

    public List<ProductionOrderItemRef> selectedOrderItemRefs() {
        return treeSelection.selectedOrderItemRefs();
    }

    public List<MaterialRequirementSourceItemRefView> selectedItemsForMaterialRequirement() {
        List<MaterialRequirementSourceItemRefView> refs = new ArrayList<>();
        for (ProductionOrderItemRef ref : treeSelection.selectedOrderItemRefs()) {
            refs.add(
                    new MaterialRequirementSourceItemRefView(
                            ref.sourceOrderId(), ref.sourceOrderItemId()));
        }
        return List.copyOf(refs);
    }

    public int selectedCount() {
        return treeSelection.size();
    }

    public Set<UUID> expandedOrderIds() {
        return Set.copyOf(expandedOrderIds);
    }

    public void setOrderExpanded(UUID orderId, boolean expanded) {
        Objects.requireNonNull(orderId, "orderId");
        if (expanded) {
            expandedOrderIds.add(orderId);
        } else {
            expandedOrderIds.remove(orderId);
        }
    }

    public boolean detailMode() {
        return screenMode == ScreenMode.DETAIL;
    }

    public void openOrderDetail(OrderId orderId) {
        openForOrder(orderId);
    }

    public void backToTree() {
        screenMode = ScreenMode.TREE;
        treeVisible.set(true);
        detailVisible.set(false);
        materialRequirementPanelVisible.set(false);
        releasePanelVisible.set(false);
        currentOrderId = null;
        currentStatus = null;
        orderSelected.set(false);
        orderNumber.set("");
        customerLabel.set("");
        statusLabel.set("");
        statusDetailLabel.set("");
        itemRows.clear();
        materialRows.clear();
        historyRows.clear();
        logicalTransfers.clear();
        requirementLines.clear();
        releaseMaterialRows.clear();
        productionCellChoices.clear();
        selectedLogicalTransfer.set(null);
        selectedRequirementLineId.set(null);
        refreshActionPolicy();
        loadTree();
    }

    public void openSelectedOrder() {
        if (loading.get()) {
            return;
        }
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
        if (loading.get()) {
            return;
        }
        Objects.requireNonNull(orderId, "orderId");
        orderSelectorInput.set(orderId.value().toString());
        run("Заказ открыт", () -> loadOrder(orderId));
    }

    public void acceptOrder() {
        if (loading.get()) {
            return;
        }
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
        if (loading.get()) {
            return;
        }
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

    public void prepareMaterialRequirement() {
        if (loading.get()) {
            return;
        }
        if (!canTransfer.get() || currentOrderId == null) {
            deny();
            return;
        }
        List<UUID> selectedItemIds =
                itemRows.stream()
                        .filter(ProductionItemRow::isSelected)
                        .map(ProductionItemRow::orderItemId)
                        .toList();
        if (selectedItemIds.isEmpty()) {
            errorMessage.set("Выберите хотя бы одну позицию заказа.");
            statusMessage.set("");
            return;
        }
        run(
                "Потребность в материалах подготовлена",
                () -> {
                    MaterialRequirementView requirement =
                            applicationApi.prepareMaterialRequirement(
                                    currentOrderId, selectedItemIds);
                    applyRequirement(requirement);
                    materialRequirementPanelVisible.set(true);
                    releasePanelVisible.set(false);
                });
    }

    public void applyRequirementQuantity(MaterialRequirementLineRow row) {
        Objects.requireNonNull(row, "row");
        if (currentRequirement == null) {
            errorMessage.set(ProductionUiErrorMapper.VALIDATION);
            return;
        }
        if (currentRequirement.status() == MaterialRequirementStatusView.SUBMITTED) {
            errorMessage.set("Требование отправлено на склад — количество изменить нельзя.");
            return;
        }
        UUID lineId = row.lineId();
        run(
                "Количество потребности обновлено",
                () -> {
                    BigDecimal qty = parsePositiveDecimal(row.quantity(), "количество");
                    MaterialRequirementView updated =
                            applicationApi.changeMaterialRequirementQuantity(
                                    currentRequirement.requirementId(),
                                    lineId,
                                    qty,
                                    currentRequirement.version());
                    applyRequirement(updated, lineId);
                },
                true);
    }

    public void submitMaterialRequirement() {
        if (!canTransfer.get()) {
            deny();
            return;
        }
        if (currentRequirement == null) {
            errorMessage.set("Сначала подготовьте требование в материалах.");
            return;
        }
        if (currentRequirement.status() == MaterialRequirementStatusView.SUBMITTED) {
            statusMessage.set("Требование уже отправлено на склад.");
            return;
        }
        run(
                null,
                () -> {
                    SubmitMaterialRequirementResultView result =
                            applicationApi.submitMaterialRequirement(
                                    currentRequirement.requirementId(),
                                    currentRequirement.version());
                    applySubmitted(result);
                    statusMessage.set(
                            "Требование отправлено на склад. Создано перемещений: "
                                    + result.documents().size());
                },
                true);
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
                            applicationApi
                                    .destinationWarehouse()
                                    .productionWarehouseId()
                                    .orElse(null);
                    applyReleasePreview(preview, productionWarehouseId);
                    releasePanelVisible.set(true);
                    materialRequirementPanelVisible.set(false);
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
        if (loading.get()) {
            return;
        }
        if (detailMode()) {
            if (currentOrderId == null) {
                clearOrderState();
                return;
            }
            run("Данные обновлены", this::reloadCurrentOrder);
        } else {
            run(null, this::loadTreeInternal);
        }
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

    public StringProperty selectionCountLabelProperty() {
        return selectionCountLabel;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public ObjectProperty<ProductionTreeStatusFilter> statusFilterProperty() {
        return statusFilter;
    }

    public ObjectProperty<OrderListPeriod.Preset> periodPresetProperty() {
        return periodPreset;
    }

    public ObjectProperty<List<TreeOrderModel>> visibleTreeProperty() {
        return visibleTree;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty orderSelectedProperty() {
        return orderSelected;
    }

    public BooleanProperty treeVisibleProperty() {
        return treeVisible;
    }

    public BooleanProperty detailVisibleProperty() {
        return detailVisible;
    }

    public BooleanProperty materialRequirementPanelVisibleProperty() {
        return materialRequirementPanelVisible;
    }

    public BooleanProperty requirementSubmittedProperty() {
        return requirementSubmitted;
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

    public ObservableList<MaterialRequirementLineRow> requirementLines() {
        return requirementLines;
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

    public ObjectProperty<UUID> selectedRequirementLineIdProperty() {
        return selectedRequirementLineId;
    }

    public void selectRequirementLine(UUID lineId) {
        selectedRequirementLineId.set(lineId);
    }

    public MaterialRequirementLineRow findRequirementLine(UUID lineId) {
        if (lineId == null) {
            return null;
        }
        return requirementLines.stream()
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

    public MaterialRequirementView currentRequirement() {
        return currentRequirement;
    }

    public ReleasePreviewView currentReleasePreview() {
        return currentReleasePreview;
    }

    private void loadTreeInternal() {
        OrderListPeriod.Range range = resolvePeriod();
        OrderWorklistCriteria criteria =
                OrderWorklistCriteria.builder()
                        .createdFrom(range.fromInclusive())
                        .createdToExclusive(range.toExclusive())
                        .build();
        List<OrderWorklistRowDto> rows = worklistQuery.listWorklistRows(criteria);
        List<UUID> ids = rows.stream().map(row -> row.orderId().value()).toList();
        Map<UUID, OrderProductionListFacts> factsByOrder =
                ids.isEmpty() ? Map.of() : queryApi.getOrderProductionListFacts(ids);

        List<LoadedProductionOrder> loaded = new ArrayList<>();
        List<ProductionOrderItemRef> allRefs = new ArrayList<>();
        for (OrderWorklistRowDto row : rows) {
            UUID orderId = row.orderId().value();
            OrderProductionListFacts facts = factsByOrder.get(orderId);
            OrderProductionViewStatus status =
                    facts == null ? OrderProductionViewStatus.NOT_ACCEPTED : facts.status();

            List<OrderItemDto> orderItems =
                    ProductionOrderItemsLoader.loadAll(orderQueryService, row.orderId());
            Map<UUID, ItemProductionStateView> statesByItem =
                    queryApi.getItemProductionStatesByOrderId(orderId);

            Map<UUID, Long> orderedFromProduction =
                    resolveOrderedQuantitiesWhenNeeded(orderId, orderItems, statesByItem);

            List<LoadedProductionItem> items = new ArrayList<>();
            int index = 1;
            for (OrderItemDto item : orderItems) {
                ItemProductionStateView state = statesByItem.get(item.orderItemId().value());
                items.add(new LoadedProductionItem(item, state, index));
                allRefs.add(
                        new ProductionOrderItemRef(
                                orderId, item.orderItemId().value()));
                index++;
            }

            loaded.add(
                    new LoadedProductionOrder(
                            orderId,
                            row.orderNumber(),
                            blankToEmpty(row.customerName()),
                            status,
                            facts,
                            items,
                            orderedFromProduction));
        }

        authoritativeOrders = List.copyOf(loaded);
        treeSelection.retainOnly(allRefs);
        Set<UUID> stillPresent = new HashSet<>();
        for (LoadedProductionOrder order : loaded) {
            stillPresent.add(order.orderId);
        }
        expandedOrderIds.retainAll(stillPresent);
        applyFilters();
    }

    private Map<UUID, Long> resolveOrderedQuantitiesWhenNeeded(
            UUID orderId,
            List<OrderItemDto> orderItems,
            Map<UUID, ItemProductionStateView> statesByItem) {
        boolean needsProductionOrder = false;
        for (OrderItemDto item : orderItems) {
            ItemProductionStateView state = statesByItem.get(item.orderItemId().value());
            if (state == null) {
                needsProductionOrder = true;
                break;
            }
        }
        if (!needsProductionOrder) {
            return Map.of();
        }
        Optional<OrderForProductionDto> productionOrder =
                orderQueryService.getOrderForProduction(OrderId.of(orderId));
        if (productionOrder.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> quantities = new HashMap<>();
        for (OrderItemForProductionDto item : productionOrder.get().items()) {
            BigDecimal ordered = item.specification().orderedQuantity();
            if (ordered != null) {
                quantities.put(item.orderItemId().value(), ordered.longValue());
            }
        }
        return quantities;
    }

    private List<TreeOrderModel> buildVisibleTreeModels(List<LoadedProductionOrder> visible) {
        List<TreeOrderModel> models = new ArrayList<>();
        for (LoadedProductionOrder order : visible) {
            // Parent checkbox semantics always use authoritative children, not the filter-visible
            // subset — selection must survive temporary search/filter hiding.
            LoadedProductionOrder authoritative = findAuthoritativeOrder(order.orderId);
            List<ProductionOrderItemRef> childRefs =
                    authoritative == null ? order.allItemRefs() : authoritative.allItemRefs();
            String orderQty;
            String orderReleased;
            String orderRemaining;
            if (order.status == OrderProductionViewStatus.NOT_ACCEPTED || order.facts == null) {
                orderQty = "—";
                orderReleased = "—";
                orderRemaining = "—";
            } else {
                orderQty = Long.toString(order.facts.orderedQuantity());
                orderReleased = Long.toString(order.facts.releasedQuantity());
                orderRemaining = Long.toString(order.facts.activeProductionQuantity());
            }
            ProductionTreeNode orderNode =
                    ProductionTreeNode.order(
                            order.orderId,
                            order.orderNumber,
                            order.customerName,
                            order.status,
                            orderQty,
                            orderReleased,
                            orderRemaining,
                            childRefs);

            List<ProductionTreeNode> itemNodes = new ArrayList<>();
            for (LoadedProductionItem loadedItem : order.items) {
                itemNodes.add(buildItemTreeNode(loadedItem, order.orderedQuantityFallback));
            }
            models.add(
                    new TreeOrderModel(
                            orderNode, itemNodes, expandedOrderIds.contains(order.orderId)));
        }
        return List.copyOf(models);
    }

    private ProductionTreeNode buildItemTreeNode(
            LoadedProductionItem loadedItem, Map<UUID, Long> orderedFallback) {
        OrderItemDto item = loadedItem.item;
        ItemProductionStateView state = loadedItem.state;
        String position =
                ProductionTreeNode.humanReadablePosition(
                        item.externalPositionNumber(), loadedItem.index1Based);
        String productLabel = formatProductLabel(item.productCode(), item.name());
        String quantity;
        String released;
        String remaining;
        if (state != null) {
            quantity = Long.toString(state.orderedQuantity());
            released = Long.toString(state.releasedQuantity());
            remaining = Long.toString(state.activeProductionQuantity());
        } else {
            Long fallback = orderedFallback.get(item.orderItemId().value());
            quantity = fallback == null ? "—" : Long.toString(fallback);
            released = "—";
            remaining = "—";
        }
        String status =
                ProductionPresentationLabels.itemStatus(
                        state == null ? null : state.status());
        return ProductionTreeNode.item(
                item.orderId().value(),
                item.orderItemId().value(),
                position,
                productLabel,
                quantity,
                status,
                released,
                remaining,
                loadedItem.index1Based);
    }

    private void updateTreeEmptyStateMessage(List<LoadedProductionOrder> visible) {
        if (detailMode()) {
            return;
        }
        if (authoritativeOrders.isEmpty()) {
            emptyStateMessage.set(EMPTY_TREE);
        } else if (visible.isEmpty()) {
            emptyStateMessage.set(EMPTY_FILTERED);
        } else {
            emptyStateMessage.set("");
        }
    }

    private LoadedProductionOrder findAuthoritativeOrder(UUID orderId) {
        for (LoadedProductionOrder order : authoritativeOrders) {
            if (order.orderId.equals(orderId)) {
                return order;
            }
        }
        return null;
    }

    private static boolean orderMatchesSearch(LoadedProductionOrder order, String needle) {
        return containsIgnoreCase(order.orderNumber, needle)
                || containsIgnoreCase(order.customerName, needle);
    }

    private static boolean itemMatchesSearch(LoadedProductionItem loadedItem, String needle) {
        OrderItemDto item = loadedItem.item;
        return containsIgnoreCase(item.externalPositionNumber(), needle)
                || containsIgnoreCase(item.name(), needle)
                || containsIgnoreCase(item.productCode(), needle);
    }

    private static String normalizeSearchNeedle(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        if (needle.isEmpty() || value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String formatProductLabel(String productCode, String name) {
        String code = blankToEmpty(productCode).trim();
        String itemName = blankToEmpty(name).trim();
        if (!code.isEmpty() && !itemName.isEmpty()) {
            return code + " — " + itemName;
        }
        if (!itemName.isEmpty()) {
            return itemName;
        }
        if (!code.isEmpty()) {
            return code;
        }
        return "—";
    }

    private OrderListPeriod.Range resolvePeriod() {
        OrderListPeriod.Preset preset =
                periodPreset.get() == null
                        ? OrderListPeriod.Preset.LAST_30_DAYS
                        : periodPreset.get();
        return OrderListPeriod.resolve(preset, zoneId, clock, null, null);
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
        screenMode = ScreenMode.DETAIL;
        treeVisible.set(false);
        detailVisible.set(true);
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

        List<OrderItemDto> orderItems =
                ProductionOrderItemsLoader.loadAll(
                        orderQueryService, OrderId.of(currentOrderId));
        Map<UUID, ItemProductionStateView> statesByItem =
                resolveDetailItemStates(currentOrderId, orderItems);

        List<ProductionItemRow> mappedItems = new ArrayList<>();
        int index = 1;
        for (OrderItemDto item : orderItems) {
            ItemProductionStateView state = statesByItem.get(item.orderItemId().value());
            mappedItems.add(mapItemRow(item, state, index));
            index++;
        }
        itemRows.setAll(mappedItems);

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

        materialRows.clear();
        if (view.status() == OrderProductionViewStatus.IN_PRODUCTION) {
            loadMaterialAvailabilityRows();
        }
    }

    private Map<UUID, ItemProductionStateView> resolveDetailItemStates(
            UUID orderId, List<OrderItemDto> items) {
        Map<UUID, ItemProductionStateView> states =
                new HashMap<>(queryApi.getItemProductionStatesByOrderId(orderId));
        if (states.size() < items.size()) {
            for (OrderItemDto item : items) {
                UUID itemId = item.orderItemId().value();
                if (!states.containsKey(itemId)) {
                    queryApi.getItemProductionState(itemId).ifPresent(state -> states.put(itemId, state));
                }
            }
        }
        return states;
    }

    private void loadMaterialAvailabilityRows() {
        try {
            queryApi.getMaterialAvailabilityResult(currentOrderId)
                    .ifPresent(result -> materialRows.setAll(mapMaterialRows(result)));
        } catch (RuntimeException ex) {
            materialRows.clear();
            String mapped = ProductionUiErrorMapper.text(ex);
            if (ProductionUiErrorMapper.ORDER_NOT_FOUND.equals(mapped)) {
                errorMessage.set(ProductionUiErrorMapper.MATERIALS_LOAD_FAILED);
            } else {
                errorMessage.set(mapped);
            }
        }
    }

    private void applyRequirement(MaterialRequirementView requirement) {
        applyRequirement(requirement, selectedRequirementLineId.get());
    }

    private void applyRequirement(MaterialRequirementView requirement, UUID reselectLineId) {
        currentRequirement = requirement;
        releaseProductionWarehouseId = requirement.destinationWarehouseId();
        List<MaterialRequirementLineRow> rows = new ArrayList<>();
        for (var line : requirement.lines()) {
            rows.add(
                    new MaterialRequirementLineRow(
                            line.lineId(),
                            blankToEmpty(line.materialCode()),
                            blankToEmpty(line.materialName()),
                            blankToEmpty(line.color()),
                            blankToEmpty(line.unitOfMeasure()),
                            line.quantity().toPlainString()));
        }
        requirementLines.setAll(rows);
        requirementSubmitted.set(
                requirement.status() == MaterialRequirementStatusView.SUBMITTED);
        if (reselectLineId != null
                && rows.stream().anyMatch(row -> row.lineId().equals(reselectLineId))) {
            selectedRequirementLineId.set(reselectLineId);
        } else if (reselectLineId != null) {
            selectedRequirementLineId.set(null);
        }
    }

    private void applySubmitted(SubmitMaterialRequirementResultView result) {
        MaterialRequirementView current = currentRequirement;
        if (current == null) {
            return;
        }
        currentRequirement =
                new MaterialRequirementView(
                        current.requirementId(),
                        current.sourceItems(),
                        current.destinationWarehouseId(),
                        current.createdAt(),
                        current.updatedAt(),
                        result.version(),
                        result.status(),
                        current.submittedAt(),
                        current.submittedBy(),
                        current.lines());
        requirementSubmitted.set(
                result.status() == MaterialRequirementStatusView.SUBMITTED);
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
            rows.add(row);
        }
        releaseMaterialRows.setAll(rows);
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

    private ProductionItemRow mapItemRow(
            OrderItemDto item, ItemProductionStateView state, int index1Based) {
        String position =
                ProductionTreeNode.humanReadablePosition(
                        item.externalPositionNumber(), index1Based);
        if (state == null) {
            return new ProductionItemRow(
                    item.orderItemId().value(),
                    position,
                    ProductionPresentationLabels.itemStatus(null),
                    "—",
                    "—",
                    "—",
                    "—",
                    "—",
                    0L,
                    "",
                    false,
                    false);
        }
        boolean eligible =
                state.status()
                                == ProductionQueryApi.ItemProductionStateStatus.IN_PRODUCTION
                        || state.status()
                                == ProductionQueryApi.ItemProductionStateStatus
                                        .PARTIALLY_RELEASED;
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
                "",
                eligible,
                eligible);
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
        currentRequirement = null;
        currentReleasePreview = null;
        orderSelected.set(false);
        orderNumber.set("");
        customerLabel.set("");
        statusLabel.set("");
        statusDetailLabel.set("");
        if (detailMode()) {
            emptyStateMessage.set("Выберите заказ для работы с производством.");
        } else {
            updateTreeEmptyStateMessage(visibleOrders());
        }
        itemRows.clear();
        materialRows.clear();
        historyRows.clear();
        logicalTransfers.clear();
        requirementLines.clear();
        releaseMaterialRows.clear();
        productionCellChoices.clear();
        selectedLogicalTransfer.set(null);
        selectedRequirementLineId.set(null);
        materialRequirementPanelVisible.set(false);
        requirementSubmitted.set(false);
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
            if (successMessage != null
                    && (statusMessage.get() == null || statusMessage.get().isBlank())) {
                statusMessage.set(successMessage);
            }
        } catch (AccessDeniedException ex) {
            if (detailMode() || currentOrderId != null) {
                errorMessage.set(ProductionUiErrorMapper.text(ex));
            } else {
                errorMessage.set(TREE_LOAD_FAILED);
            }
            statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
        } catch (IllegalArgumentException ex) {
            if ("Order not found".equals(ex.getMessage())) {
                errorMessage.set(ProductionUiErrorMapper.ORDER_NOT_FOUND);
            } else {
                errorMessage.set(ProductionUiErrorMapper.text(ex));
            }
            statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
        } catch (RuntimeException ex) {
            if (detailMode() || currentOrderId != null) {
                errorMessage.set(ProductionUiErrorMapper.text(ex));
            } else {
                errorMessage.set(TREE_LOAD_FAILED);
            }
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
        return DecimalQuantityParser.parsePositive(raw, field);
    }

    private static BigDecimal parseNonNegativeDecimal(String raw, String field) {
        return DecimalQuantityParser.parseNonNegative(raw, field);
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
