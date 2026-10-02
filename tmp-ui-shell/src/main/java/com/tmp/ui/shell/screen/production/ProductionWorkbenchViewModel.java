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
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementDraftSummaryView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductCoverageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductSelectionView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.OrderQuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.SubmitMaterialRequirementResultView;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.order.worklist.OrderListPeriod;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneId;
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
 * Production workbench ViewModel. LEVEL 1 tree + LEVEL 2 Order Card. Reads/writes go through public
 * Production/Order APIs only.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class ProductionWorkbenchViewModel {

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
    }

    private final ProductionQueryApi queryApi;
    private final ProductionApplicationApi applicationApi;
    private final OrderQueryService orderQueryService;
    private final OrderWorklistQuery worklistQuery;
    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
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
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty orderSelected = new SimpleBooleanProperty(false);
    private final BooleanProperty canAccept = new SimpleBooleanProperty(false);
    private final BooleanProperty canEditQuantityMode = new SimpleBooleanProperty(false);
    private final BooleanProperty quantityModeDirty = new SimpleBooleanProperty(false);
    private final BooleanProperty canRequestMaterials = new SimpleBooleanProperty(false);
    private final BooleanProperty canOpenMaterialDrafts = new SimpleBooleanProperty(false);
    private final BooleanProperty requestMaterialsEnabled = new SimpleBooleanProperty(false);
    private final BooleanProperty treeVisible = new SimpleBooleanProperty(true);
    private final BooleanProperty detailVisible = new SimpleBooleanProperty(false);

    private final StringProperty searchText = new SimpleStringProperty("");
    private final ObjectProperty<ProductionTreeStatusFilter> statusFilter =
            new SimpleObjectProperty<>(ProductionTreeStatusFilter.IN_PROGRESS);
    private final ObjectProperty<OrderListPeriod.Preset> periodPreset =
            new SimpleObjectProperty<>(OrderListPeriod.Preset.LAST_30_DAYS);
    private final ObjectProperty<List<TreeOrderModel>> visibleTree =
            new SimpleObjectProperty<>(List.of());
    private final ObjectProperty<QuantityModeView> selectedQuantityMode =
            new SimpleObjectProperty<>(QuantityModeView.STANDARD);

    private final ObservableList<ProductionItemRow> itemRows = FXCollections.observableArrayList();

    private ScreenMode screenMode = ScreenMode.TREE;
    private List<LoadedProductionOrder> authoritativeOrders = List.of();
    private final Set<UUID> expandedOrderIds = new LinkedHashSet<>();

    private UUID currentOrderId;
    private String currentOrderNumber = "";
    private OrderProductionViewStatus currentStatus;
    private QuantityModeView savedQuantityMode = QuantityModeView.STANDARD;
    private long quantityModeVersion;
    private boolean suppressingModeListener;

    public ProductionWorkbenchViewModel(
            ProductionQueryApi queryApi,
            ProductionApplicationApi applicationApi,
            OrderQueryService orderQueryService,
            OrderWorklistQuery worklistQuery,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService) {
        this(
                queryApi,
                applicationApi,
                orderQueryService,
                worklistQuery,
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
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            Clock clock,
            ZoneId zoneId) {
        this.queryApi = Objects.requireNonNull(queryApi, "queryApi");
        this.applicationApi = Objects.requireNonNull(applicationApi, "applicationApi");
        this.orderQueryService = Objects.requireNonNull(orderQueryService, "orderQueryService");
        this.worklistQuery = Objects.requireNonNull(worklistQuery, "worklistQuery");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService =
                Objects.requireNonNull(authenticationService, "authenticationService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zoneId = Objects.requireNonNull(zoneId, "zoneId");
        treeSelection
                .selectedCountProperty()
                .addListener(
                        (obs, oldValue, newValue) -> {
                            selectionCountLabel.set(
                                    "Выбрано: " + newValue.intValue() + " позиций");
                            refreshMaterialRequestActionState();
                        });
        searchText.addListener((obs, oldValue, newValue) -> applyFilters());
        statusFilter.addListener((obs, oldValue, newValue) -> applyFilters());
        periodPreset.addListener(
                (obs, oldValue, newValue) -> {
                    if (oldValue != null && !detailMode()) {
                        loadTree();
                    }
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
        clearOrderCardState();
        applyFilters();
        refreshMaterialRequestActionState();
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

    public ObservableList<ProductionItemRow> itemRows() {
        return itemRows;
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
                            ? "—"
                            : formatProductLabel(item.item.productCode(), item.item.name());

            MaterialRequirementProductCoverageView itemCoverage =
                    coverageByItem.get(ref.sourceOrderItemId());
            long requestable =
                    itemCoverage == null ? 0L : itemCoverage.requestableProductQuantity();
            if (requestable <= 0L) {
                invalid.add(
                        orderLabel
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
                            orderLabel,
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

    private void refreshMaterialRequestActionState() {
        boolean transfer = has(UiShellScreens.PRODUCTION_TRANSFER_PERMISSION);
        boolean view = has(UiShellScreens.PRODUCTION_VIEW_PERMISSION);
        canRequestMaterials.set(transfer);
        canOpenMaterialDrafts.set(view);
        requestMaterialsEnabled.set(transfer && treeSelection.size() > 0 && !detailMode());
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
                allRefs.add(new ProductionOrderItemRef(orderId, item.orderItemId().value()));
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
        refreshMaterialRequestActionState();
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
        boolean hasState = false;
        List<ProductionItemRow> mappedItems = new ArrayList<>();
        int index = 1;
        for (OrderItemDto item : orderItems) {
            ItemProductionStateView state = statesByItem.get(item.orderItemId().value());
            if (state != null) {
                hasState = true;
                releasedTotal += state.releasedQuantity();
                orderedTotal += state.orderedQuantity();
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
        if (!hasState && orderedTotal == 0L) {
            progressLabel.set("—");
        } else {
            progressLabel.set(releasedTotal + " из " + orderedTotal);
        }

        OrderQuantityModeView modeView = applicationApi.getOrderQuantityMode(currentOrderId);
        applyQuantityMode(modeView, discardModeDraft);
        refreshActionPolicy();
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
                        has(UiShellScreens.PRODUCTION_ACCEPT_PERMISSION));
        canAccept.set(decision.accept());
        canEditQuantityMode.set(
                orderSelected.get() && has(UiShellScreens.PRODUCTION_ACCEPT_PERMISSION));
    }

    private void clearOrderCardState() {
        currentOrderId = null;
        currentOrderNumber = "";
        currentStatus = null;
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
        canEditQuantityMode.set(false);
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
                    || ProductionUiErrorMapper.isAcceptConflict(ex)) {
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
