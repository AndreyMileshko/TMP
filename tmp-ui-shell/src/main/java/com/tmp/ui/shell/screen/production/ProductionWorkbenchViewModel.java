package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderForProductionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemForProductionDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.CellAllocationView;
import com.tmp.production.api.ProductionApplicationApi.ItemReleaseView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualUsageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementDraftSummaryView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductCoverageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductSelectionView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessView;
import com.tmp.production.api.ProductionApplicationApi.OrderQuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.PlannedMaterialLineView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.ReleaseResultView;
import com.tmp.production.api.ProductionApplicationApi.SubmitMaterialRequirementResultView;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryType;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserUiPreferenceService;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * Production workbench ViewModel. LEVEL 1 tree + LEVEL 2 Order Card. Reads/writes go through public
 * Production/Order APIs; Warehouse public API is used only for Release cell choices.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class ProductionWorkbenchViewModel {

    private static final String EMPTY_TREE = "Нет заказов в производстве";
    private static final String EMPTY_FILTERED = "Нет заказов, подходящих под фильтр";
    private static final DateTimeFormatter HISTORY_AT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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
            return new ProductionOrderItemRef(item.orderId().value(), item.orderItemId().value());
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

        List<ProductionOrderItemRef> selectableItemRefs() {
            List<ProductionOrderItemRef> refs = new ArrayList<>();
            for (LoadedProductionItem item : items) {
                if (isItemSelectable(item)) {
                    refs.add(item.ref());
                }
            }
            return List.copyOf(refs);
        }
    }

    private final ProductionQueryApi queryApi;
    private final ProductionApplicationApi applicationApi;
    private final OrderQueryService orderQueryService;
    private final OrderWorklistQuery worklistQuery;
    private final WarehouseApi warehouseApi;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final UserUiPreferenceService preferenceService;
    private final Clock clock;
    private final ZoneId zoneId;

    private final ProductionTreeSelectionModel treeSelection = new ProductionTreeSelectionModel();

    private final StringProperty orderNumber = new SimpleStringProperty("");
    private final StringProperty orderTitle = new SimpleStringProperty("");
    private final StringProperty customerLabel = new SimpleStringProperty("");
    private final StringProperty siteLabel = new SimpleStringProperty("—");
    private final StringProperty statusLabel = new SimpleStringProperty("");
    private final StringProperty progressLabel = new SimpleStringProperty("—");
    private final StringProperty emptyStateMessage = new SimpleStringProperty(EMPTY_TREE);
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty selectionCountLabel =
            new SimpleStringProperty("Выбрано: 0 позиций");
    private final StringProperty quantityModeHint = new SimpleStringProperty("");
    private final StringProperty materialsSummary = new SimpleStringProperty("");
    private final StringProperty materialsDetail = new SimpleStringProperty("");
    private final BooleanProperty materialsDetailsVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty orderSelected = new SimpleBooleanProperty(false);
    private final BooleanProperty canAccept = new SimpleBooleanProperty(false);
    private final BooleanProperty canCancel = new SimpleBooleanProperty(false);
    private final BooleanProperty canEditQuantityMode = new SimpleBooleanProperty(false);
    private final BooleanProperty quantityModeDirty = new SimpleBooleanProperty(false);
    private final BooleanProperty canRequestMaterials = new SimpleBooleanProperty(false);
    private final BooleanProperty canOpenMaterialDrafts = new SimpleBooleanProperty(false);
    private final BooleanProperty requestMaterialsEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty canRelease = new SimpleBooleanProperty(false);
    private final BooleanProperty releaseEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty treeVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty detailVisible = new SimpleBooleanProperty(false);
    private final BooleanProperty historyEmpty = new SimpleBooleanProperty(true);
    private final BooleanProperty historyDetailsVisible = new SimpleBooleanProperty(false);
    private final StringProperty historyLatestAt = new SimpleStringProperty("");
    private final StringProperty historyLatestOperation = new SimpleStringProperty("");

    private final StringProperty searchText = new SimpleStringProperty("");
    private final ObjectProperty<ProductionTreeStatusFilter> statusFilter =
            new SimpleObjectProperty<>(ProductionTreeStatusFilter.IN_PROGRESS);
    private final ObjectProperty<OrderListPeriod.Preset> periodPreset =
            new SimpleObjectProperty<>(OrderListPeriod.Preset.LAST_30_DAYS);
    private final ObjectProperty<LocalDate> customFrom = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> customTo = new SimpleObjectProperty<>();
    private final ObjectProperty<List<TreeOrderModel>> visibleTree =
            new SimpleObjectProperty<>(List.of());
    private final ObjectProperty<QuantityModeView> selectedQuantityMode =
            new SimpleObjectProperty<>(QuantityModeView.STANDARD);

    private final ObservableList<ProductionItemRow> itemRows = FXCollections.observableArrayList();
    private final ObservableList<ProductionHistoryRow> historyRows =
            FXCollections.observableArrayList();

    private ScreenMode screenMode = ScreenMode.TREE;
    private List<LoadedProductionOrder> authoritativeOrders = List.of();
    private final Set<UUID> expandedOrderIds = new LinkedHashSet<>();

    private UUID currentOrderId;
    private String currentOrderNumber = "";
    private OrderProductionViewStatus currentStatus;
    private long currentRemainingQuantity;
    private long currentReleasedQuantity;
    private QuantityModeView savedQuantityMode = QuantityModeView.STANDARD;
    private long quantityModeVersion;
    private boolean suppressingModeListener;
    private boolean suppressingPeriodListener;
    private boolean preferencesLoaded;
    private MaterialReadinessView currentMaterialReadiness;

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
                null,
                Clock.systemDefaultZone(),
                ZoneId.systemDefault());
    }

    public ProductionWorkbenchViewModel(
            ProductionQueryApi queryApi,
            ProductionApplicationApi applicationApi,
            OrderQueryService orderQueryService,
            OrderWorklistQuery worklistQuery,
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            UserUiPreferenceService preferenceService) {
        this(
                queryApi,
                applicationApi,
                orderQueryService,
                worklistQuery,
                warehouseApi,
                authorizationService,
                authenticationService,
                preferenceService,
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
        this(
                queryApi,
                applicationApi,
                orderQueryService,
                worklistQuery,
                warehouseApi,
                authorizationService,
                authenticationService,
                null,
                clock,
                zoneId);
    }

    ProductionWorkbenchViewModel(
            ProductionQueryApi queryApi,
            ProductionApplicationApi applicationApi,
            OrderQueryService orderQueryService,
            OrderWorklistQuery worklistQuery,
            WarehouseApi warehouseApi,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            UserUiPreferenceService preferenceService,
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
        this.preferenceService = preferenceService;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        applyPeriodPreference(ProductionWorkbenchFilterPreference.defaults(), false);
        treeSelection
                .selectedCountProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            selectionCountLabel.set(
                                    "Выбрано: " + newValue.intValue() + " позиций");
                            refreshMaterialRequestActionState();
                            refreshReleaseActionState();
                        });
        searchText.addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilter.addListener((obs, oldValue, newValue) -> applyFilters());
        periodPreset.addListener(
                (obs, oldValue, newValue) -> {
                    if (suppressingPeriodListener || oldValue == null || detailMode()) {
                        return;
                    }
                    persistPeriodPreference();
                    loadTree();
                });
        customFrom.addListener(
                (obs, oldValue, newValue) -> {
                    if (suppressingPeriodListener
                            || detailMode()
                            || periodPreset.get() != OrderListPeriod.Preset.CUSTOM) {
                        return;
                    }
                    persistPeriodPreference();
                    loadTree();
                });
        customTo.addListener(
                (obs, oldValue, newValue) -> {
                    if (suppressingPeriodListener
                            || detailMode()
                            || periodPreset.get() != OrderListPeriod.Preset.CUSTOM) {
                        return;
                    }
                    persistPeriodPreference();
                    loadTree();
                });
        selectedQuantityMode.addListener(
                (obs, oldValue, newValue) -> {
                    if (suppressingModeListener || newValue == null) {
                        return;
                    }
                    quantityModeDirty.set(newValue != savedQuantityMode);
                    quantityModeHint.set(ProductionPresentationLabels.quantityModeHint(newValue));
                });
        refreshMaterialRequestActionState();
        refreshReleaseActionState();
    }

    public void loadTree() {
        if (loading.get()) {
            return;
        }
        run(null, this::loadTreeInternal, false);
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
            treeSelection.selectAll(order.selectableItemRefs());
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
        Objects.requireNonNull(ref, "ref");
        if (selected) {
            LoadedProductionOrder order = findAuthoritativeOrder(ref.sourceOrderId());
            LoadedProductionItem item =
                    order == null ? null : findAuthoritativeItem(order, ref.sourceOrderItemId());
            if (item == null || !isItemSelectable(item)) {
                return;
            }
        }
        treeSelection.setItemSelected(ref, selected);
    }

    public void toggleOrderSelection(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId");
        LoadedProductionOrder order = findAuthoritativeOrder(orderId);
        if (order == null) {
            return;
        }
        List<ProductionOrderItemRef> selectable = order.selectableItemRefs();
        if (selectable.isEmpty()) {
            return;
        }
        ProductionTreeSelectionModel.OrderCheckState state =
                treeSelection.orderCheckState(orderId, selectable);
        if (state == ProductionTreeSelectionModel.OrderCheckState.CHECKED) {
            deselectOrder(orderId);
        } else {
            selectOrder(orderId);
        }
    }

    public void toggleItemSelection(ProductionOrderItemRef ref) {
        setItemSelected(ref, !treeSelection.isItemSelected(ref));
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
        clearOrderCardState();
        loadTree();
    }

    /** Loads persisted period preference once per session open (search is never persisted). */
    public void ensurePreferencesLoaded() {
        if (preferencesLoaded || preferenceService == null) {
            preferencesLoaded = true;
            return;
        }
        preferencesLoaded = true;
        Optional<UserId> userId = currentUserId();
        if (userId.isEmpty()) {
            return;
        }
        Optional<String> raw =
                preferenceService.load(
                        userId.get(),
                        ProductionWorkbenchFilterPreference.NAMESPACE,
                        ProductionWorkbenchFilterPreference.KEY);
        if (raw.isEmpty()) {
            return;
        }
        ProductionWorkbenchFilterPreference preference =
                ProductionWorkbenchFilterPreferenceCodec.decode(raw.get());
        applyPeriodPreference(preference, false);
    }

    public void openForOrder(OrderId orderId) {
        if (loading.get()) {
            return;
        }
        Objects.requireNonNull(orderId, "orderId");
        run(
                null,
                () -> loadOrder(orderId),
                false,
                ProductionUiErrorMapper.CARD_LOAD_FAILED);
    }

    public void acceptOrder() {
        if (loading.get()) {
            return;
        }
        if (!canAccept.get() || currentOrderId == null) {
            deny();
            return;
        }
        String success =
                currentOrderNumber.isBlank()
                        ? "Заказ принят в производство"
                        : "Заказ №" + currentOrderNumber + " принят в производство";
        run(
                success,
                () -> {
                    applicationApi.acceptOrderIntoProduction(currentOrderId, currentActor());
                    reloadCurrentOrder(false);
                },
                true,
                ProductionUiErrorMapper.ACCEPT_FAILED);
    }

    public void cancelOrderProduction(Optional<String> reason) {
        if (loading.get()) {
            return;
        }
        if (!canCancel.get() || currentOrderId == null) {
            deny();
            return;
        }
        Optional<String> safeReason = reason == null ? Optional.empty() : reason;
        String success = ProductionUiErrorMapper.cancelSuccess(currentOrderNumber);
        run(
                success,
                () -> {
                    applicationApi.cancelOrderProduction(currentOrderId, safeReason);
                    reloadCurrentOrder(true);
                },
                true,
                ProductionUiErrorMapper.CANCEL_FAILED);
    }

    public long currentRemainingQuantity() {
        return currentRemainingQuantity;
    }

    public long currentReleasedQuantity() {
        return currentReleasedQuantity;
    }

    public void selectQuantityMode(QuantityModeView mode) {
        Objects.requireNonNull(mode, "mode");
        if (!canEditQuantityMode.get()) {
            return;
        }
        selectedQuantityMode.set(mode);
    }

    public void saveQuantityMode() {
        if (loading.get() || currentOrderId == null) {
            return;
        }
        if (!canEditQuantityMode.get()) {
            deny();
            return;
        }
        QuantityModeView selected = selectedQuantityMode.get();
        if (selected == null || selected == savedQuantityMode) {
            quantityModeDirty.set(false);
            return;
        }
        run(
                "Режим работы сохранён",
                () -> {
                    OrderQuantityModeView saved =
                            applicationApi.changeOrderQuantityMode(
                                    currentOrderId, selected, quantityModeVersion);
                    applyQuantityMode(saved, true);
                },
                true,
                ProductionUiErrorMapper.QUANTITY_MODE_SAVE_FAILED);
    }

    public void refresh() {
        if (loading.get()) {
            return;
        }
        if (detailMode()) {
            if (currentOrderId == null) {
                clearOrderCardState();
                return;
            }
            run(
                    "Данные обновлены",
                    () -> reloadCurrentOrder(true),
                    false,
                    ProductionUiErrorMapper.CARD_LOAD_FAILED);
        } else {
            run(null, this::loadTreeInternal, false);
        }
    }

    public StringProperty orderNumberProperty() {
        return orderNumber;
    }

    public StringProperty orderTitleProperty() {
        return orderTitle;
    }

    public StringProperty customerLabelProperty() {
        return customerLabel;
    }

    public StringProperty siteLabelProperty() {
        return siteLabel;
    }

    public StringProperty statusLabelProperty() {
        return statusLabel;
    }

    public StringProperty progressLabelProperty() {
        return progressLabel;
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

    public StringProperty quantityModeHintProperty() {
        return quantityModeHint;
    }

    public StringProperty materialsSummaryProperty() {
        return materialsSummary;
    }

    public StringProperty materialsDetailProperty() {
        return materialsDetail;
    }

    public BooleanProperty materialsDetailsVisibleProperty() {
        return materialsDetailsVisible;
    }

    public MaterialReadinessView currentMaterialReadiness() {
        return currentMaterialReadiness;
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

    public ObjectProperty<QuantityModeView> selectedQuantityModeProperty() {
        return selectedQuantityMode;
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

    public BooleanProperty canAcceptProperty() {
        return canAccept;
    }

    public BooleanProperty canCancelProperty() {
        return canCancel;
    }

    public BooleanProperty canEditQuantityModeProperty() {
        return canEditQuantityMode;
    }

    public BooleanProperty quantityModeDirtyProperty() {
        return quantityModeDirty;
    }

    public BooleanProperty canRequestMaterialsProperty() {
        return canRequestMaterials;
    }

    public BooleanProperty canOpenMaterialDraftsProperty() {
        return canOpenMaterialDrafts;
    }

    public BooleanProperty requestMaterialsEnabledProperty() {
        return requestMaterialsEnabled;
    }

    public BooleanProperty canReleaseProperty() {
        return canRelease;
    }

    public BooleanProperty releaseEnabledProperty() {
        return releaseEnabled;
    }

    public BooleanProperty historyEmptyProperty() {
        return historyEmpty;
    }

    public BooleanProperty historyDetailsVisibleProperty() {
        return historyDetailsVisible;
    }

    public StringProperty historyLatestAtProperty() {
        return historyLatestAt;
    }

    public StringProperty historyLatestOperationProperty() {
        return historyLatestOperation;
    }

    public ObservableList<ProductionItemRow> itemRows() {
        return itemRows;
    }

    public ObservableList<ProductionHistoryRow> historyRows() {
        return historyRows;
    }

    public UUID currentOrderId() {
        return currentOrderId;
    }

    public String currentOrderNumber() {
        return currentOrderNumber;
    }

    public OrderProductionViewStatus currentStatus() {
        return currentStatus;
    }

    public QuantityModeView savedQuantityMode() {
        return savedQuantityMode;
    }

    public long quantityModeVersion() {
        return quantityModeVersion;
    }

    /**
     * Loads authoritative coverage + Quantity Modes for the current tree selection. Never silently
     * drops invalid items — returns a validation message instead.
     */
    public MaterialRequestStep1LoadResult loadMaterialRequestStep1() {
        if (!has(UiShellScreens.PRODUCTION_TRANSFER_PERMISSION)) {
            return MaterialRequestStep1LoadResult.permissionDenied();
        }
        List<ProductionOrderItemRef> selected = treeSelection.selectedOrderItemRefs();
        if (selected.isEmpty()) {
            return MaterialRequestStep1LoadResult.validation("Выберите хотя бы одну позицию.");
        }

        List<MaterialRequirementSourceItemRefView> refs = selectedItemsForMaterialRequirement();
        List<MaterialRequirementProductCoverageView> coverage =
                applicationApi.getMaterialRequirementProductCoverage(refs);

        Map<UUID, MaterialRequirementProductCoverageView> coverageByItem = new HashMap<>();
        for (MaterialRequirementProductCoverageView row : coverage) {
            coverageByItem.put(row.sourceOrderItemId(), row);
        }

        Set<UUID> orderIds = new LinkedHashSet<>();
        for (ProductionOrderItemRef ref : selected) {
            orderIds.add(ref.sourceOrderId());
        }
        Map<UUID, QuantityModeView> modesByOrder = new HashMap<>();
        for (OrderQuantityModeView modeView :
                applicationApi.getOrderQuantityModes(List.copyOf(orderIds))) {
            modesByOrder.put(modeView.orderId(), modeView.quantityMode());
        }

        List<String> invalid = new ArrayList<>();
        List<MaterialRequestQuantityRow> rows = new ArrayList<>();
        for (ProductionOrderItemRef ref : selected) {
            LoadedProductionOrder order = findAuthoritativeOrder(ref.sourceOrderId());
            LoadedProductionItem item = findAuthoritativeItem(order, ref.sourceOrderItemId());
            String tableOrderNumber = order == null ? "—" : order.orderNumber;
            String humanOrderLabel = orderLabel(order);
            String positionLabel =
                    item == null
                            ? "Позиция"
                            : ProductionTreeNode.humanReadablePosition(
                                    item.item.externalPositionNumber(), item.index1Based);
            String productLabel =
                    item == null
                            ? "—"
                            : formatProductLabel(item.item.productCode(), item.item.name());

            MaterialRequirementProductCoverageView itemCoverage =
                    coverageByItem.get(ref.sourceOrderItemId());
            long requestable =
                    itemCoverage == null ? 0L : itemCoverage.requestableProductQuantity();
            if (requestable <= 0L) {
                invalid.add(
                        humanOrderLabel
                                + " / "
                                + positionLabel
                                + ":\n"
                                + describeNotRequestable(item, itemCoverage));
                continue;
            }
            QuantityModeView mode =
                    modesByOrder.getOrDefault(ref.sourceOrderId(), QuantityModeView.STANDARD);
            rows.add(
                    new MaterialRequestQuantityRow(
                            ref.sourceOrderId(),
                            ref.sourceOrderItemId(),
                            tableOrderNumber,
                            positionLabel,
                            productLabel,
                            mode,
                            requestable,
                            requestable));
        }

        if (!invalid.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append(
                    "Для некоторых выбранных позиций материалы больше нельзя запросить:\n\n");
            for (String line : invalid) {
                message.append(line).append("\n\n");
            }
            return MaterialRequestStep1LoadResult.validation(message.toString().trim());
        }
        return MaterialRequestStep1LoadResult.ok(rows);
    }

    public MaterialRequirementView prepareMaterialRequirement(
            List<MaterialRequestQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        List<MaterialRequirementProductSelectionView> selections = new ArrayList<>();
        for (MaterialRequestQuantityRow row : rows) {
            if (row.standardMode()) {
                selections.add(
                        new MaterialRequirementProductSelectionView(
                                row.sourceOrderId(),
                                row.sourceOrderItemId(),
                                Optional.empty()));
            } else {
                selections.add(
                        new MaterialRequirementProductSelectionView(
                                row.sourceOrderId(),
                                row.sourceOrderItemId(),
                                Optional.of(row.requestedProductQuantity())));
            }
        }
        return applicationApi.prepareMaterialRequirement(selections);
    }

    public MaterialRequirementView changeMaterialRequirementLineQuantity(
            UUID requirementId, UUID lineId, BigDecimal quantity, long expectedVersion) {
        return applicationApi.changeMaterialRequirementQuantity(
                requirementId, lineId, quantity, expectedVersion);
    }

    public SubmitMaterialRequirementResultView submitMaterialRequirement(
            UUID requirementId, long expectedVersion) {
        return applicationApi.submitMaterialRequirement(requirementId, expectedVersion);
    }

    public List<MaterialRequirementDraftSummaryView> listMaterialRequirementDrafts() {
        return applicationApi.listMaterialRequirementDrafts();
    }

    public Optional<MaterialRequirementView> getMaterialRequirement(UUID requirementId) {
        return applicationApi.getMaterialRequirement(requirementId);
    }

    public List<String> materialRequirementSourceSummary(MaterialRequirementView requirement) {
        Objects.requireNonNull(requirement, "requirement");
        List<String> lines = new ArrayList<>();
        for (MaterialRequirementSourceItemView source : requirement.sourceItems()) {
            LoadedProductionOrder order = findAuthoritativeOrder(source.sourceOrderId());
            LoadedProductionItem item =
                    findAuthoritativeItem(order, source.sourceOrderItemId());
            String orderLabel =
                    order == null
                            ? "Заказ"
                            : (order.orderNumber.startsWith("№")
                                    ? "Заказ " + order.orderNumber
                                    : "Заказ №" + order.orderNumber);
            String positionLabel =
                    item == null
                            ? "Позиция"
                            : ProductionTreeNode.humanReadablePosition(
                                    item.item.externalPositionNumber(), item.index1Based);
            String productLabel =
                    item == null
                            ? ""
                            : formatProductLabel(item.item.productCode(), item.item.name());
            StringBuilder line = new StringBuilder();
            line.append(orderLabel)
                    .append(" / ")
                    .append(positionLabel)
                    .append(" — ")
                    .append(source.requestedProductQuantity())
                    .append(" изд.");
            if (!productLabel.isBlank() && !"—".equals(productLabel)) {
                line.append(" (").append(productLabel).append(")");
            }
            lines.add(line.toString());
        }
        return List.copyOf(lines);
    }

    public void afterSuccessfulMaterialSubmit(MaterialRequirementView submitted) {
        Objects.requireNonNull(submitted, "submitted");
        List<ProductionOrderItemRef> toClear = new ArrayList<>();
        for (MaterialRequirementSourceItemView source : submitted.sourceItems()) {
            toClear.add(
                    new ProductionOrderItemRef(
                            source.sourceOrderId(), source.sourceOrderItemId()));
        }
        treeSelection.deselectAll(toClear);
        statusMessage.set(ProductionUiErrorMapper.MATERIAL_SUBMIT_SUCCESS);
        errorMessage.set("");
        loadTreeInternal();
        refreshMaterialRequestActionState();
        refreshReleaseActionState();
    }

    /**
     * Loads authoritative active quantities + Quantity Modes for the current tree selection. Never
     * silently drops invalid items — returns a validation message instead.
     */
    public ReleaseStep1LoadResult loadReleaseStep1() {
        if (!has(UiShellScreens.PRODUCTION_RELEASE_PERMISSION)) {
            return ReleaseStep1LoadResult.permissionDenied();
        }
        List<ProductionOrderItemRef> selected = treeSelection.selectedOrderItemRefs();
        if (selected.isEmpty()) {
            return ReleaseStep1LoadResult.validation("Выберите хотя бы одну позицию.");
        }

        Set<UUID> orderIds = new LinkedHashSet<>();
        for (ProductionOrderItemRef ref : selected) {
            orderIds.add(ref.sourceOrderId());
        }
        Map<UUID, QuantityModeView> modesByOrder = new HashMap<>();
        for (OrderQuantityModeView modeView :
                applicationApi.getOrderQuantityModes(List.copyOf(orderIds))) {
            modesByOrder.put(modeView.orderId(), modeView.quantityMode());
        }

        List<String> invalid = new ArrayList<>();
        List<ReleaseQuantityRow> rows = new ArrayList<>();
        for (ProductionOrderItemRef ref : selected) {
            LoadedProductionOrder order = findAuthoritativeOrder(ref.sourceOrderId());
            LoadedProductionItem item = findAuthoritativeItem(order, ref.sourceOrderItemId());
            String humanOrderLabel = orderLabel(order);
            String tableOrderNumber = order == null ? "—" : order.orderNumber;
            String positionLabel = positionLabel(item);
            String productLabel =
                    item == null
                            ? "—"
                            : formatProductLabel(item.item.productCode(), item.item.name());

            if (order != null && order.status == OrderProductionViewStatus.CANCELLED) {
                invalid.add(
                        humanOrderLabel + " / " + positionLabel + ":\nпроизводство заказа отменено.");
                continue;
            }
            if (order != null && order.status == OrderProductionViewStatus.NOT_ACCEPTED) {
                invalid.add(
                        humanOrderLabel
                                + " / "
                                + positionLabel
                                + ":\nзаказ ещё не принят в производство.");
                continue;
            }
            if (item == null || item.state == null) {
                invalid.add(
                        humanOrderLabel
                                + " / "
                                + positionLabel
                                + ":\nпозиция недоступна для выпуска.");
                continue;
            }
            if (item.state.status() == ItemProductionStateStatus.CANCELLED) {
                invalid.add(humanOrderLabel + " / " + positionLabel + ":\nпозиция отменена.");
                continue;
            }
            if (item.state.status() == ItemProductionStateStatus.RELEASED
                    || item.state.activeProductionQuantity() <= 0L) {
                invalid.add(
                        humanOrderLabel
                                + " / "
                                + positionLabel
                                + ":\nактивное количество к выпуску равно 0.");
                continue;
            }

            QuantityModeView mode =
                    modesByOrder.getOrDefault(ref.sourceOrderId(), QuantityModeView.STANDARD);
            long active = item.state.activeProductionQuantity();
            rows.add(
                    new ReleaseQuantityRow(
                            ref.sourceOrderId(),
                            ref.sourceOrderItemId(),
                            tableOrderNumber,
                            positionLabel,
                            productLabel,
                            mode,
                            item.state.orderedQuantity(),
                            item.state.releasedQuantity(),
                            active,
                            active));
        }

        if (!invalid.isEmpty()) {
            StringBuilder message = new StringBuilder();
            message.append("Для некоторых выбранных позиций выпуск невозможен:\n\n");
            for (String line : invalid) {
                message.append(line).append("\n\n");
            }
            return ReleaseStep1LoadResult.validation(message.toString().trim());
        }
        return ReleaseStep1LoadResult.ok(rows);
    }

    /**
     * Re-reads authoritative mode/quantities before leaving STEP 1. Returns a stale reason without
     * reinterpreting user-entered FLEXIBLE quantities.
     */
    public ReleaseStep1StaleResult detectReleaseStep1Stale(List<ReleaseQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        // Refresh authoritative Production item states before leaving STEP 1.
        loadTreeInternal();

        Set<UUID> orderIds = new LinkedHashSet<>();
        for (ReleaseQuantityRow row : rows) {
            orderIds.add(row.sourceOrderId());
        }
        Map<UUID, QuantityModeView> modesByOrder = new HashMap<>();
        for (OrderQuantityModeView modeView :
                applicationApi.getOrderQuantityModes(List.copyOf(orderIds))) {
            modesByOrder.put(modeView.orderId(), modeView.quantityMode());
        }

        boolean modeChanged = false;
        boolean quantityChanged = false;
        for (ReleaseQuantityRow row : rows) {
            QuantityModeView currentMode =
                    modesByOrder.getOrDefault(row.sourceOrderId(), QuantityModeView.STANDARD);
            if (currentMode != row.quantityMode()) {
                modeChanged = true;
            }
            LoadedProductionOrder order = findAuthoritativeOrder(row.sourceOrderId());
            LoadedProductionItem item = findAuthoritativeItem(order, row.sourceOrderItemId());
            long active =
                    item == null || item.state == null ? 0L : item.state.activeProductionQuantity();
            if (active != row.activeProductionQuantity()) {
                quantityChanged = true;
            }
            if (row.standardMode() && ReleaseDialogSupport.resolvedReleaseQuantity(row) != active) {
                quantityChanged = true;
            }
            if (!row.standardMode() && row.releaseQuantity() > active) {
                quantityChanged = true;
            }
            if (order != null && order.status == OrderProductionViewStatus.CANCELLED) {
                return ReleaseStep1StaleResult.cancelled();
            }
        }
        if (modeChanged) {
            return ReleaseStep1StaleResult.staleMode();
        }
        if (quantityChanged) {
            return ReleaseStep1StaleResult.staleQuantity();
        }
        return ReleaseStep1StaleResult.ok();
    }

    public ReleaseReadinessBatchResult checkReleaseReadiness(List<ReleaseQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        Map<UUID, List<ReleaseQuantityRow>> byOrder = groupReleaseRowsByOrder(rows);
        MaterialReadinessView firstBlocking = null;
        for (Map.Entry<UUID, List<ReleaseQuantityRow>> entry : byOrder.entrySet()) {
            List<ItemReleaseView> itemReleases = toItemReleases(entry.getValue());
            MaterialReadinessView readiness =
                    applicationApi.getMaterialReadinessForRelease(entry.getKey(), itemReleases);
            if (readiness.status() == MaterialReadinessStatusView.READY) {
                continue;
            }
            firstBlocking = readiness;
            break;
        }
        if (firstBlocking == null) {
            return ReleaseReadinessBatchResult.allReady();
        }
        return ReleaseReadinessBatchResult.blocked(firstBlocking);
    }

    public List<ReleaseMaterialRow> prepareReleaseMaterialRows(List<ReleaseQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        Optional<UUID> warehouseId = applicationApi.destinationWarehouse().productionWarehouseId();
        List<StorageCellChoice> cells =
                warehouseId.map(this::loadProductionCells).orElse(List.of());

        Map<UUID, List<ReleaseQuantityRow>> byOrder = groupReleaseRowsByOrder(rows);
        List<ReleaseMaterialRow> materials = new ArrayList<>();
        for (Map.Entry<UUID, List<ReleaseQuantityRow>> entry : byOrder.entrySet()) {
            UUID orderId = entry.getKey();
            List<ItemReleaseView> itemReleases = toItemReleases(entry.getValue());
            ReleasePreviewView preview = applicationApi.prepareRelease(orderId, itemReleases);
            String orderLabel = entry.getValue().getFirst().orderNumberLabel();
            Map<String, String> names = new HashMap<>();
            for (PlannedMaterialLineView planned : preview.plannedMaterialLines()) {
                String key =
                        planned.sourceOrderItemId() + "|" + planned.materialReferenceId();
                names.put(
                        key,
                        planned.materialName().orElse("Материал"));
            }
            for (var actual : preview.defaultActuals()) {
                String key = actual.sourceOrderItemId() + "|" + actual.materialReferenceId();
                String label = names.getOrDefault(key, "Материал");
                ReleaseMaterialRow row =
                        new ReleaseMaterialRow(
                                orderId,
                                actual.sourceOrderItemId(),
                                actual.materialReferenceId(),
                                orderLabel,
                                label,
                                DecimalUiFormat.format(actual.plannedQuantity()),
                                DecimalUiFormat.format(actual.actualQuantity()));
                row.cellChoices().setAll(cells);
                materials.add(row);
            }
        }
        return List.copyOf(materials);
    }

    public MultiOrderReleaseResult confirmReleases(
            List<ReleaseQuantityRow> quantityRows, List<ReleaseMaterialRow> materialRows) {
        Objects.requireNonNull(quantityRows, "quantityRows");
        Objects.requireNonNull(materialRows, "materialRows");

        Map<UUID, List<ReleaseQuantityRow>> quantitiesByOrder = groupReleaseRowsByOrder(quantityRows);
        Map<UUID, List<ReleaseMaterialRow>> materialsByOrder = new LinkedHashMap<>();
        for (ReleaseMaterialRow row : materialRows) {
            materialsByOrder
                    .computeIfAbsent(row.sourceOrderId(), id -> new ArrayList<>())
                    .add(row);
        }

        List<UUID> orderIds = new ArrayList<>(quantitiesByOrder.keySet());
        orderIds.sort(Comparator.naturalOrder());

        List<OrderReleaseOutcome> outcomes = new ArrayList<>();
        boolean stopFurther = false;
        for (UUID orderId : orderIds) {
            String orderLabel = quantitiesByOrder.get(orderId).getFirst().orderNumberLabel();
            if (stopFurther) {
                outcomes.add(OrderReleaseOutcome.skipped(orderId, orderLabel));
                continue;
            }
            try {
                List<ItemReleaseView> itemReleases = toItemReleases(quantitiesByOrder.get(orderId));
                List<MaterialActualUsageView> usages =
                        buildMaterialActualUsages(materialsByOrder.getOrDefault(orderId, List.of()));
                ReleaseResultView result =
                        applicationApi.releaseProducts(orderId, itemReleases, usages);
                long total =
                        quantitiesByOrder.get(orderId).stream()
                                .mapToLong(ReleaseDialogSupport::resolvedReleaseQuantity)
                                .sum();
                OrderProductionView view = queryApi.getOrderProductionView(orderId);
                boolean manufactured =
                        view != null && view.status() == OrderProductionViewStatus.MANUFACTURED;
                outcomes.add(
                        OrderReleaseOutcome.succeeded(
                                orderId, orderLabel, total, manufactured, result));
            } catch (RuntimeException ex) {
                outcomes.add(
                        OrderReleaseOutcome.failed(
                                orderId, orderLabel, ProductionUiErrorMapper.text(ex)));
                stopFurther = true;
            }
        }
        return new MultiOrderReleaseResult(List.copyOf(outcomes));
    }

    public void afterSuccessfulRelease(
            MultiOrderReleaseResult result, List<ReleaseQuantityRow> quantityRows) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(quantityRows, "quantityRows");
        List<ProductionOrderItemRef> toClear = new ArrayList<>();
        Set<UUID> successfulOrders = new HashSet<>();
        for (OrderReleaseOutcome outcome : result.outcomes()) {
            if (outcome.success()) {
                successfulOrders.add(outcome.orderId());
            }
        }
        for (ReleaseQuantityRow row : quantityRows) {
            if (successfulOrders.contains(row.sourceOrderId())) {
                toClear.add(
                        new ProductionOrderItemRef(row.sourceOrderId(), row.sourceOrderItemId()));
            }
        }
        treeSelection.deselectAll(toClear);
        statusMessage.set(result.summaryMessage());
        errorMessage.set("");
        loadTreeInternal();
        refreshMaterialRequestActionState();
        refreshReleaseActionState();
    }

    public void setStatusMessage(String message) {
        statusMessage.set(message == null ? "" : message);
    }

    public void setErrorMessage(String message) {
        errorMessage.set(message == null ? "" : message);
    }

    public record MaterialRequestStep1LoadResult(
            boolean ok,
            boolean accessDenied,
            String validationMessage,
            List<MaterialRequestQuantityRow> rows) {

        public static MaterialRequestStep1LoadResult ok(List<MaterialRequestQuantityRow> rows) {
            return new MaterialRequestStep1LoadResult(true, false, "", List.copyOf(rows));
        }

        public static MaterialRequestStep1LoadResult validation(String message) {
            return new MaterialRequestStep1LoadResult(false, false, message, List.of());
        }

        public static MaterialRequestStep1LoadResult permissionDenied() {
            return new MaterialRequestStep1LoadResult(
                    false, true, ProductionUiErrorMapper.ACCESS_DENIED, List.of());
        }
    }

    public record ReleaseStep1LoadResult(
            boolean ok,
            boolean accessDenied,
            String validationMessage,
            List<ReleaseQuantityRow> rows) {

        public static ReleaseStep1LoadResult ok(List<ReleaseQuantityRow> rows) {
            return new ReleaseStep1LoadResult(true, false, "", List.copyOf(rows));
        }

        public static ReleaseStep1LoadResult validation(String message) {
            return new ReleaseStep1LoadResult(false, false, message, List.of());
        }

        public static ReleaseStep1LoadResult permissionDenied() {
            return new ReleaseStep1LoadResult(
                    false, true, ProductionUiErrorMapper.ACCESS_DENIED, List.of());
        }
    }

    public record ReleaseStep1StaleResult(boolean stale, boolean modeChanged, String message) {
        public static ReleaseStep1StaleResult ok() {
            return new ReleaseStep1StaleResult(false, false, "");
        }

        public static ReleaseStep1StaleResult staleMode() {
            return new ReleaseStep1StaleResult(
                    true, true, ProductionUiErrorMapper.RELEASE_MODE_CHANGED);
        }

        public static ReleaseStep1StaleResult staleQuantity() {
            return new ReleaseStep1StaleResult(
                    true, false, ProductionUiErrorMapper.RELEASE_QUANTITY_CHANGED);
        }

        public static ReleaseStep1StaleResult cancelled() {
            return new ReleaseStep1StaleResult(
                    true, false, ProductionUiErrorMapper.RELEASE_CANCELLED);
        }
    }

    public record ReleaseReadinessBatchResult(boolean ready, MaterialReadinessView blocking) {
        public static ReleaseReadinessBatchResult allReady() {
            return new ReleaseReadinessBatchResult(true, null);
        }

        public static ReleaseReadinessBatchResult blocked(MaterialReadinessView readiness) {
            return new ReleaseReadinessBatchResult(false, readiness);
        }
    }

    public record OrderReleaseOutcome(
            UUID orderId,
            String orderLabel,
            boolean success,
            boolean skipped,
            boolean manufactured,
            long releasedQuantity,
            String errorMessage,
            ReleaseResultView result) {

        public static OrderReleaseOutcome succeeded(
                UUID orderId,
                String orderLabel,
                long releasedQuantity,
                boolean manufactured,
                ReleaseResultView result) {
            return new OrderReleaseOutcome(
                    orderId,
                    orderLabel,
                    true,
                    false,
                    manufactured,
                    releasedQuantity,
                    "",
                    result);
        }

        public static OrderReleaseOutcome failed(
                UUID orderId, String orderLabel, String errorMessage) {
            return new OrderReleaseOutcome(
                    orderId, orderLabel, false, false, false, 0L, errorMessage, null);
        }

        public static OrderReleaseOutcome skipped(UUID orderId, String orderLabel) {
            return new OrderReleaseOutcome(
                    orderId, orderLabel, false, true, false, 0L, "не запускалось", null);
        }
    }

    public record MultiOrderReleaseResult(List<OrderReleaseOutcome> outcomes) {
        public MultiOrderReleaseResult {
            outcomes = List.copyOf(outcomes);
        }

        public boolean anySuccess() {
            return outcomes.stream().anyMatch(OrderReleaseOutcome::success);
        }

        public boolean allSuccess() {
            return !outcomes.isEmpty() && outcomes.stream().allMatch(OrderReleaseOutcome::success);
        }

        public String summaryMessage() {
            if (outcomes.isEmpty()) {
                return "";
            }
            if (outcomes.size() == 1) {
                OrderReleaseOutcome only = outcomes.getFirst();
                if (only.success()) {
                    if (only.manufactured()) {
                        return "Изделия выпущены.\nЗаказ изготовлен полностью.";
                    }
                    return "Выпущено: " + only.releasedQuantity() + " изделия.";
                }
                return only.errorMessage();
            }
            if (allSuccess()) {
                StringBuilder text = new StringBuilder("Выпуск выполнен.\n\n");
                for (OrderReleaseOutcome outcome : outcomes) {
                    text.append(outcome.orderLabel())
                            .append(" — Выпущено: ")
                            .append(outcome.releasedQuantity())
                            .append("\n");
                }
                return text.toString().trim();
            }
            if (anySuccess()) {
                StringBuilder text =
                        new StringBuilder(ProductionUiErrorMapper.RELEASE_PARTIAL_SUCCESS)
                                .append("\n\n");
                for (OrderReleaseOutcome outcome : outcomes) {
                    text.append(outcome.orderLabel()).append(" — ");
                    if (outcome.success()) {
                        text.append("Выпущено");
                    } else if (outcome.skipped()) {
                        text.append("Не выполнено / не запускалось");
                    } else {
                        text.append("Не выполнено:\n").append(outcome.errorMessage());
                    }
                    text.append("\n\n");
                }
                return text.toString().trim();
            }
            return outcomes.getFirst().errorMessage();
        }
    }

    private void refreshMaterialRequestActionState() {
        boolean transfer = has(UiShellScreens.PRODUCTION_TRANSFER_PERMISSION);
        boolean view = has(UiShellScreens.PRODUCTION_VIEW_PERMISSION);
        canRequestMaterials.set(transfer);
        canOpenMaterialDrafts.set(view);
        requestMaterialsEnabled.set(transfer && treeSelection.size() > 0 && !detailMode());
    }

    private void refreshReleaseActionState() {
        boolean release = has(UiShellScreens.PRODUCTION_RELEASE_PERMISSION);
        canRelease.set(release);
        releaseEnabled.set(release && treeSelection.size() > 0 && !detailMode());
    }

    private Map<UUID, List<ReleaseQuantityRow>> groupReleaseRowsByOrder(
            List<ReleaseQuantityRow> rows) {
        Map<UUID, List<ReleaseQuantityRow>> byOrder = new LinkedHashMap<>();
        for (ReleaseQuantityRow row : rows) {
            byOrder.computeIfAbsent(row.sourceOrderId(), id -> new ArrayList<>()).add(row);
        }
        return byOrder;
    }

    private static List<ItemReleaseView> toItemReleases(List<ReleaseQuantityRow> rows) {
        List<ItemReleaseView> releases = new ArrayList<>();
        for (ReleaseQuantityRow row : rows) {
            releases.add(
                    new ItemReleaseView(
                            row.sourceOrderItemId(),
                            ReleaseDialogSupport.resolvedReleaseQuantity(row)));
        }
        return releases;
    }

    private List<MaterialActualUsageView> buildMaterialActualUsages(List<ReleaseMaterialRow> rows) {
        List<MaterialActualUsageView> usages = new ArrayList<>();
        for (ReleaseMaterialRow row : rows) {
            BigDecimal actual =
                    DecimalQuantityParser.parseNonNegative(
                            row.actualQuantity(), "Фактическое количество");
            List<CellAllocationView> allocations = new ArrayList<>();
            if (actual.signum() > 0) {
                for (ReleaseMaterialRow.CellAllocation allocation : row.allocations()) {
                    if (allocation.productionCell() == null) {
                        throw new IllegalArgumentException(
                                "Выберите ячейку склада производства");
                    }
                    BigDecimal qty =
                            DecimalQuantityParser.parsePositive(
                                    allocation.quantity(), "Количество по ячейке");
                    allocations.add(
                            new CellAllocationView(allocation.productionCell().id(), qty));
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

    private List<StorageCellChoice> loadProductionCells(UUID warehouseId) {
        List<StorageCellView> cells = warehouseApi.listStorageCells(warehouseId);
        List<StorageCellChoice> choices = new ArrayList<>();
        for (StorageCellView cell : cells) {
            if (cell.active()) {
                choices.add(StorageCellChoice.from(cell));
            }
        }
        return choices;
    }

    private String orderLabel(LoadedProductionOrder order) {
        if (order == null) {
            return "Заказ";
        }
        return order.orderNumber.startsWith("№")
                ? "Заказ " + order.orderNumber
                : "Заказ №" + order.orderNumber;
    }

    private String positionLabel(LoadedProductionItem item) {
        if (item == null) {
            return "Позиция";
        }
        return ProductionTreeNode.humanReadablePosition(
                item.item.externalPositionNumber(), item.index1Based);
    }

    private static String describeNotRequestable(
            LoadedProductionItem item, MaterialRequirementProductCoverageView coverage) {
        if (item == null || item.state == null) {
            return "позиция недоступна для запроса материалов.";
        }
        if (item.state.status() == ItemProductionStateStatus.CANCELLED) {
            return "позиция отменена.";
        }
        if (item.state.status() == ItemProductionStateStatus.RELEASED) {
            return "позиция уже полностью выпущена.";
        }
        if (coverage != null && coverage.requestableProductQuantity() <= 0L) {
            if (coverage.submittedProductCoverage() > 0L) {
                return "всё необходимое количество уже запрошено.";
            }
            return "доступное для запроса количество равно 0.";
        }
        return "материалы больше нельзя запросить.";
    }

    private LoadedProductionItem findAuthoritativeItem(
            LoadedProductionOrder order, UUID sourceOrderItemId) {
        if (order == null) {
            return null;
        }
        for (LoadedProductionItem item : order.items) {
            if (item.item.orderItemId().value().equals(sourceOrderItemId)) {
                return item;
            }
        }
        return null;
    }

    private void loadTreeInternal() {
        ensurePreferencesLoaded();
        OrderListPeriod.Range range;
        try {
            range = resolvePeriod();
        } catch (IllegalArgumentException ex) {
            errorMessage.set(ex.getMessage());
            return;
        }
        errorMessage.set("");
        OrderWorklistCriteria.Builder criteriaBuilder = OrderWorklistCriteria.builder();
        if (range.fromInclusive() != null) {
            criteriaBuilder.createdFrom(range.fromInclusive());
        }
        if (range.toExclusive() != null) {
            criteriaBuilder.createdToExclusive(range.toExclusive());
        }
        OrderWorklistCriteria criteria = criteriaBuilder.build();
        List<OrderWorklistRowDto> rows = worklistQuery.listWorklistRows(criteria);
        List<UUID> ids = rows.stream().map(row -> row.orderId().value()).toList();
        Map<UUID, OrderProductionListFacts> factsByOrder =
                ids.isEmpty() ? Map.of() : queryApi.getOrderProductionListFacts(ids);

        List<LoadedProductionOrder> loaded = new ArrayList<>();
        List<ProductionOrderItemRef> selectableRefs = new ArrayList<>();
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
                LoadedProductionItem loadedItem = new LoadedProductionItem(item, state, index);
                items.add(loadedItem);
                if (isItemSelectable(loadedItem)) {
                    selectableRefs.add(loadedItem.ref());
                }
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
        treeSelection.retainOnly(selectableRefs);
        Set<UUID> stillPresent = new HashSet<>();
        for (LoadedProductionOrder order : loaded) {
            stillPresent.add(order.orderId);
        }
        expandedOrderIds.retainAll(stillPresent);
        applyFilters();
        refreshMaterialRequestActionState();
        refreshReleaseActionState();
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
            LoadedProductionOrder authoritative = findAuthoritativeOrder(order.orderId);
            List<ProductionOrderItemRef> selectableChildRefs =
                    authoritative == null
                            ? order.selectableItemRefs()
                            : authoritative.selectableItemRefs();
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
                            selectableChildRefs);

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
                ProductionPresentationLabels.itemStatus(state == null ? null : state.status());
        return ProductionTreeNode.item(
                item.orderId().value(),
                item.orderItemId().value(),
                position,
                productLabel,
                quantity,
                status,
                released,
                remaining,
                loadedItem.index1Based,
                isItemSelectable(loadedItem));
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
        String positionLabel =
                ProductionTreeNode.humanReadablePosition(
                        item.externalPositionNumber(), loadedItem.index1Based);
        String normalizedNeedle = normalizePositionSearch(needle);
        String normalizedLabel = normalizePositionSearch(positionLabel);
        return containsIgnoreCase(item.externalPositionNumber(), needle)
                || containsIgnoreCase(positionLabel, needle)
                || (!normalizedNeedle.isEmpty() && normalizedLabel.contains(normalizedNeedle))
                || containsIgnoreCase(item.name(), needle)
                || containsIgnoreCase(item.productCode(), needle);
    }

    /** Allows matching «Поз 2» / «поз. 2» against displayed «Поз. 2». */
    private static String normalizePositionSearch(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace(".", "").replace("  ", " ").trim();
    }

    private static boolean isItemSelectable(LoadedProductionItem item) {
        if (item == null || item.state == null) {
            return false;
        }
        ItemProductionStateStatus status = item.state.status();
        if (status == ItemProductionStateStatus.CANCELLED
                || status == ItemProductionStateStatus.RELEASED) {
            return false;
        }
        return item.state.activeProductionQuantity() > 0L;
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
        if (preset == OrderListPeriod.Preset.CUSTOM) {
            LocalDate from = customFrom.get();
            LocalDate to = customTo.get();
            if (from == null || to == null) {
                throw new IllegalArgumentException(
                        "Укажите дату начала и дату окончания периода.");
            }
            if (from.isAfter(to)) {
                throw new IllegalArgumentException(
                        "Дата начала периода не может быть позже даты окончания.");
            }
        }
        return OrderListPeriod.resolve(preset, zoneId, clock, customFrom.get(), customTo.get());
    }

    public ObjectProperty<LocalDate> customFromProperty() {
        return customFrom;
    }

    public ObjectProperty<LocalDate> customToProperty() {
        return customTo;
    }

    private void applyPeriodPreference(
            ProductionWorkbenchFilterPreference preference, boolean persist) {
        suppressingPeriodListener = true;
        try {
            periodPreset.set(preference.periodPreset());
            customFrom.set(preference.customFrom());
            customTo.set(preference.customTo());
        } finally {
            suppressingPeriodListener = false;
        }
        if (persist) {
            persistPeriodPreference();
        }
    }

    private void persistPeriodPreference() {
        if (preferenceService == null) {
            return;
        }
        Optional<UserId> userId = currentUserId();
        if (userId.isEmpty()) {
            return;
        }
        OrderListPeriod.Preset preset =
                periodPreset.get() == null
                        ? OrderListPeriod.Preset.LAST_30_DAYS
                        : periodPreset.get();
        ProductionWorkbenchFilterPreference preference =
                new ProductionWorkbenchFilterPreference(preset, customFrom.get(), customTo.get());
        preferenceService.save(
                userId.get(),
                ProductionWorkbenchFilterPreference.NAMESPACE,
                ProductionWorkbenchFilterPreference.KEY,
                ProductionWorkbenchFilterPreference.VERSION,
                ProductionWorkbenchFilterPreferenceCodec.encode(preference));
    }

    private Optional<UserId> currentUserId() {
        return authenticationService.currentSession().map(SessionSummary::userId);
    }

    private void loadOrder(OrderId orderId) {
        OrderDto order =
                orderQueryService
                        .getOrder(orderId)
                        .orElseThrow(() -> new IllegalArgumentException("Order not found"));
        currentOrderId = order.orderId().value();
        currentOrderNumber = order.orderNumber();
        orderNumber.set(order.orderNumber());
        orderTitle.set(
                order.orderNumber().startsWith("№") || order.orderNumber().startsWith("Заказ")
                        ? order.orderNumber()
                        : "ЗАКАЗ №" + order.orderNumber());
        customerLabel.set(blankToDash(order.customerName()));
        siteLabel.set(blankToDash(order.siteRef()));
        orderSelected.set(true);
        emptyStateMessage.set("");
        screenMode = ScreenMode.DETAIL;
        treeVisible.set(false);
        detailVisible.set(true);
        reloadCurrentOrder(true);
    }

    private void reloadCurrentOrder(boolean discardModeDraft) {
        if (currentOrderId == null) {
            clearOrderCardState();
            return;
        }
        OrderProductionView view = queryApi.getOrderProductionView(currentOrderId);
        currentStatus = view.status();
        statusLabel.set(ProductionPresentationLabels.orderStatus(view.status()));

        List<OrderItemDto> orderItems =
                ProductionOrderItemsLoader.loadAll(orderQueryService, OrderId.of(currentOrderId));
        Map<UUID, ItemProductionStateView> statesByItem =
                resolveDetailItemStates(currentOrderId, orderItems);
        Map<UUID, Long> orderedFallback =
                resolveOrderedQuantitiesWhenNeeded(currentOrderId, orderItems, statesByItem);

        long releasedTotal = 0L;
        long orderedTotal = 0L;
        long remainingTotal = 0L;
        boolean hasState = false;
        List<ProductionItemRow> mappedItems = new ArrayList<>();
        int index = 1;
        for (OrderItemDto item : orderItems) {
            ItemProductionStateView state = statesByItem.get(item.orderItemId().value());
            if (state != null) {
                hasState = true;
                releasedTotal += state.releasedQuantity();
                orderedTotal += state.orderedQuantity();
                remainingTotal += state.activeProductionQuantity();
            } else {
                Long fallback = orderedFallback.get(item.orderItemId().value());
                if (fallback != null) {
                    orderedTotal += fallback;
                }
            }
            mappedItems.add(mapItemRow(item, state, orderedFallback, index));
            index++;
        }
        itemRows.setAll(mappedItems);
        currentReleasedQuantity = releasedTotal;
        currentRemainingQuantity = remainingTotal;
        if (!hasState && orderedTotal == 0L) {
            progressLabel.set("—");
        } else {
            progressLabel.set(releasedTotal + " из " + orderedTotal);
        }

        OrderQuantityModeView modeView = applicationApi.getOrderQuantityMode(currentOrderId);
        applyQuantityMode(modeView, discardModeDraft);
        loadMaterialReadiness();
        loadHistory();
        refreshActionPolicy();
    }

    private void loadHistory() {
        if (currentOrderId == null) {
            clearHistoryState();
            return;
        }
        try {
            List<ProductionHistoryEntryView> entries =
                    queryApi.listProductionHistory(currentOrderId);
            List<ProductionHistoryEntryView> sorted =
                    entries.stream()
                            .sorted(
                                    Comparator.comparing(ProductionHistoryEntryView::occurredAt)
                                            .reversed()
                                            .thenComparing(
                                                    ProductionHistoryEntryView::recordedAt,
                                                    Comparator.reverseOrder()))
                            .toList();
            List<ProductionHistoryRow> rows = new ArrayList<>(sorted.size());
            for (ProductionHistoryEntryView entry : sorted) {
                ProductionHistoryType type = entry.historyType();
                String operation = ProductionPresentationLabels.historyType(type);
                rows.add(
                        new ProductionHistoryRow(
                                formatHistoryAt(entry.occurredAt()),
                                operation,
                                ProductionPresentationLabels.historyActor(entry.actorRef()),
                                ProductionPresentationLabels.historyDescription(type)));
            }
            historyRows.setAll(rows);
            if (rows.isEmpty()) {
                historyEmpty.set(true);
                historyDetailsVisible.set(false);
                historyLatestAt.set("");
                historyLatestOperation.set("");
            } else {
                historyEmpty.set(false);
                historyDetailsVisible.set(true);
                ProductionHistoryRow latest = rows.get(0);
                historyLatestAt.set(latest.occurredAtLabel());
                historyLatestOperation.set(latest.operationLabel());
            }
        } catch (RuntimeException ex) {
            clearHistoryState();
            historyEmpty.set(true);
            historyLatestOperation.set(ProductionUiErrorMapper.HISTORY_LOAD_FAILED);
            historyLatestAt.set("");
            historyDetailsVisible.set(false);
        }
    }

    private String formatHistoryAt(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return HISTORY_AT.format(instant.atZone(zoneId));
    }

    private void clearHistoryState() {
        historyRows.clear();
        historyEmpty.set(true);
        historyDetailsVisible.set(false);
        historyLatestAt.set("");
        historyLatestOperation.set("");
    }

    private void loadMaterialReadiness() {
        try {
            applyMaterialReadiness(
                    applicationApi.getOrderRemainingMaterialReadiness(currentOrderId));
        } catch (RuntimeException ex) {
            currentMaterialReadiness = null;
            materialsSummary.set(ProductionUiErrorMapper.MATERIALS_CHECK_FAILED);
            materialsDetail.set("");
            materialsDetailsVisible.set(false);
        }
    }

    private void applyMaterialReadiness(MaterialReadinessView readiness) {
        currentMaterialReadiness = readiness;
        materialsSummary.set(ProductionPresentationLabels.materialsSummary(readiness));
        materialsDetail.set(ProductionPresentationLabels.materialsDetail(readiness));
        materialsDetailsVisible.set(
                readiness != null
                        && (readiness.status() == MaterialReadinessStatusView.READY
                                || readiness.status() == MaterialReadinessStatusView.NOT_READY)
                        && !readiness.lines().isEmpty());
    }

    private Map<UUID, ItemProductionStateView> resolveDetailItemStates(
            UUID orderId, List<OrderItemDto> items) {
        Map<UUID, ItemProductionStateView> states =
                new HashMap<>(queryApi.getItemProductionStatesByOrderId(orderId));
        if (states.size() < items.size()) {
            for (OrderItemDto item : items) {
                UUID itemId = item.orderItemId().value();
                if (!states.containsKey(itemId)) {
                    queryApi.getItemProductionState(itemId)
                            .ifPresent(state -> states.put(itemId, state));
                }
            }
        }
        return states;
    }

    private void applyQuantityMode(OrderQuantityModeView modeView, boolean discardDraft) {
        savedQuantityMode = modeView.quantityMode();
        quantityModeVersion = modeView.version();
        suppressingModeListener = true;
        try {
            if (discardDraft || !quantityModeDirty.get()) {
                selectedQuantityMode.set(modeView.quantityMode());
                quantityModeDirty.set(false);
            }
            QuantityModeView display =
                    selectedQuantityMode.get() == null
                            ? modeView.quantityMode()
                            : selectedQuantityMode.get();
            quantityModeHint.set(ProductionPresentationLabels.quantityModeHint(display));
        } finally {
            suppressingModeListener = false;
        }
        canEditQuantityMode.set(has(UiShellScreens.PRODUCTION_ACCEPT_PERMISSION));
    }

    private ProductionItemRow mapItemRow(
            OrderItemDto item,
            ItemProductionStateView state,
            Map<UUID, Long> orderedFallback,
            int index1Based) {
        String position =
                ProductionTreeNode.humanReadablePosition(
                        item.externalPositionNumber(), index1Based);
        String product = formatProductLabel(item.productCode(), item.name());
        if (state == null) {
            Long fallback = orderedFallback.get(item.orderItemId().value());
            return new ProductionItemRow(
                    item.orderItemId().value(),
                    position,
                    product,
                    fallback == null ? "—" : Long.toString(fallback),
                    ProductionPresentationLabels.itemStatus(null),
                    "—",
                    "—");
        }
        return new ProductionItemRow(
                item.orderItemId().value(),
                position,
                product,
                Long.toString(state.orderedQuantity()),
                ProductionPresentationLabels.itemStatus(state.status()),
                Long.toString(state.releasedQuantity()),
                Long.toString(state.activeProductionQuantity()));
    }

    private void refreshActionPolicy() {
        ProductionActionPolicy.Decision decision =
                ProductionActionPolicy.evaluate(
                        orderSelected.get(),
                        currentStatus,
                        has(UiShellScreens.PRODUCTION_ACCEPT_PERMISSION),
                        has(UiShellScreens.PRODUCTION_CANCEL_PERMISSION));
        canAccept.set(decision.accept());
        canCancel.set(decision.cancel());
        canEditQuantityMode.set(
                orderSelected.get() && has(UiShellScreens.PRODUCTION_ACCEPT_PERMISSION));
    }

    private void clearOrderCardState() {
        currentOrderId = null;
        currentOrderNumber = "";
        currentStatus = null;
        currentRemainingQuantity = 0L;
        currentReleasedQuantity = 0L;
        savedQuantityMode = QuantityModeView.STANDARD;
        quantityModeVersion = 0L;
        orderSelected.set(false);
        orderNumber.set("");
        orderTitle.set("");
        customerLabel.set("");
        siteLabel.set("—");
        statusLabel.set("");
        progressLabel.set("—");
        itemRows.clear();
        clearHistoryState();
        suppressingModeListener = true;
        try {
            selectedQuantityMode.set(QuantityModeView.STANDARD);
            quantityModeDirty.set(false);
            quantityModeHint.set(
                    ProductionPresentationLabels.quantityModeHint(QuantityModeView.STANDARD));
        } finally {
            suppressingModeListener = false;
        }
        canAccept.set(false);
        canCancel.set(false);
        canEditQuantityMode.set(false);
        currentMaterialReadiness = null;
        materialsSummary.set("");
        materialsDetail.set("");
        materialsDetailsVisible.set(false);
        if (!detailMode()) {
            updateTreeEmptyStateMessage(visibleOrders());
        }
    }

    private String currentActor() {
        return authenticationService
                .currentSession()
                .map(session -> session.login().value())
                .orElse("system");
    }

    private void run(String successMessage, Runnable action, boolean refreshOnConflict) {
        run(successMessage, action, refreshOnConflict, null);
    }

    private void run(
            String successMessage,
            Runnable action,
            boolean refreshOnConflict,
            String failureFallback) {
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
            errorMessage.set(ProductionUiErrorMapper.text(ex));
            statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
        } catch (IllegalArgumentException ex) {
            if ("Order not found".equals(ex.getMessage())) {
                errorMessage.set(ProductionUiErrorMapper.ORDER_NOT_FOUND);
            } else {
                errorMessage.set(ProductionUiErrorMapper.text(ex));
            }
            statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
        } catch (RuntimeException ex) {
            String mapped = ProductionUiErrorMapper.text(ex);
            if (ProductionUiErrorMapper.isQuantityModeConflict(ex)
                    || ProductionUiErrorMapper.isAcceptConflict(ex)
                    || ProductionUiErrorMapper.isCancelConflict(ex)) {
                errorMessage.set(mapped);
                statusMessage.set("");
                if (currentOrderId != null) {
                    try {
                        reloadCurrentOrder(true);
                    } catch (RuntimeException ignored) {
                        // keep conflict message
                    }
                }
            } else if (detailMode() || currentOrderId != null) {
                errorMessage.set(failureFallback == null ? mapped : failureFallback);
                statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
                if (refreshOnConflict && ProductionUiErrorMapper.isConcurrentOrStale(ex)) {
                    try {
                        reloadCurrentOrder(true);
                    } catch (RuntimeException ignored) {
                        // keep original error
                    }
                }
            } else {
                errorMessage.set(ProductionUiErrorMapper.TREE_LOAD_FAILED);
                statusMessage.set(ProductionUiErrorMapper.LOAD_FAILED);
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
