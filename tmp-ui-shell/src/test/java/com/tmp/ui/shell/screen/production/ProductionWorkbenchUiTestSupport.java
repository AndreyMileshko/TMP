package com.tmp.ui.shell.screen.production;

import com.tmp.order.api.ItemSpecificationDto;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderForProductionDto;
import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemRevisionDto;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.OrderCustomerOptionDto;
import com.tmp.order.api.OrderWorklistCriteria;
import com.tmp.order.api.OrderWorklistQuery;
import com.tmp.order.api.OrderWorklistRowDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.SpecificationId;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.DestinationWarehouseView;
import com.tmp.production.api.ProductionApplicationApi.ItemReleaseView;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualUsageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementDraftSummaryView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductCoverageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementProductSelectionView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.OrderQuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.SubmitMaterialRequirementResultView;
import com.tmp.production.api.ProductionApplicationApi.GeneratedTransferDocumentView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.ReleaseResultView;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityResultView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionListFacts;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReservationLinkView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseApi.UpdateTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

final class ProductionWorkbenchUiTestSupport {

    private ProductionWorkbenchUiTestSupport() {}

    static final class AllowAllAuthorization implements AuthorizationService {
        Set<String> allowed =
                new HashSet<>(
                        Set.of(
                                UiShellScreens.PRODUCTION_VIEW_PERMISSION,
                                UiShellScreens.PRODUCTION_ACCEPT_PERMISSION,
                                UiShellScreens.PRODUCTION_CHECK_PERMISSION,
                                UiShellScreens.PRODUCTION_TRANSFER_PERMISSION,
                                UiShellScreens.PRODUCTION_RECEIPT_PERMISSION,
                                UiShellScreens.PRODUCTION_RELEASE_PERMISSION,
                                UiShellScreens.PRODUCTION_CANCEL_PERMISSION));

        AllowAllAuthorization() {}

        AllowAllAuthorization(Set<String> allowed) {
            this.allowed = new HashSet<>(allowed);
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId.value());
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException(
                        "Access denied for permission: " + permissionId.value());
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            Set<PermissionId> result = new HashSet<>();
            for (String value : allowed) {
                result.add(PermissionId.of(value));
            }
            return result;
        }
    }

    static final class StubAuthentication implements AuthenticationService {
        Optional<SessionSummary> session =
                Optional.of(
                        new SessionSummary(
                                SessionId.of(UUID.randomUUID()),
                                UserId.of(UUID.randomUUID()),
                                Login.of("tester"),
                                Instant.parse("2026-01-01T00:00:00Z")));

        @Override
        public SessionSummary login(Login login, char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SessionSummary completePasswordSetup(
                Login login, String activationCode, char[] newPassword, char[] confirmPassword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void logout() {}

        @Override
        public Optional<SessionSummary> currentSession() {
            return session;
        }

        @Override
        public boolean isAuthenticated() {
            return session.isPresent();
        }
    }

    static final class StubQueryApi implements ProductionQueryApi {
        OrderProductionView view;
        final Map<UUID, ItemProductionStateView> itemStates = new HashMap<>();
        final Map<UUID, OrderProductionListFacts> listFacts = new HashMap<>();
        final Map<UUID, Map<UUID, ItemProductionStateView>> statesByOrder = new HashMap<>();
        Optional<MaterialAvailabilityResultView> availability = Optional.empty();
        RuntimeException availabilityFailure;
        final List<ProductionHistoryEntryView> history = new ArrayList<>();
        int getOrderProductionViewCalls;
        int getMaterialAvailabilityCalls;

        void putItemState(ItemProductionStateView state) {
            itemStates.put(state.sourceOrderItemId(), state);
            statesByOrder
                    .computeIfAbsent(state.sourceOrderId(), ignored -> new HashMap<>())
                    .put(state.sourceOrderItemId(), state);
        }

        @Override
        public OrderProductionView getOrderProductionView(UUID orderId) {
            getOrderProductionViewCalls++;
            return view;
        }

        @Override
        public Map<UUID, OrderProductionListFacts> getOrderProductionListFacts(
                Collection<UUID> orderIds) {
            Map<UUID, OrderProductionListFacts> out = new LinkedHashMap<>();
            for (UUID id : orderIds) {
                if (listFacts.containsKey(id)) {
                    out.put(id, listFacts.get(id));
                }
            }
            return out;
        }

        @Override
        public Optional<ItemProductionStateView> getItemProductionState(UUID orderItemId) {
            return Optional.ofNullable(itemStates.get(orderItemId));
        }

        @Override
        public Map<UUID, ItemProductionStateView> getItemProductionStatesByOrderId(UUID orderId) {
            return Map.copyOf(statesByOrder.getOrDefault(orderId, Map.of()));
        }

        @Override
        public Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(
                UUID orderId) {
            getMaterialAvailabilityCalls++;
            if (availabilityFailure != null) {
                throw availabilityFailure;
            }
            return availability;
        }

        @Override
        public List<ProductionHistoryEntryView> listProductionHistory(UUID orderId) {
            return List.copyOf(history);
        }
    }

    static final class StubApplicationApi implements ProductionApplicationApi {
        final List<UUID> acceptCalls = new CopyOnWriteArrayList<>();
        final List<String> acceptActors = new CopyOnWriteArrayList<>();
        final List<UUID> checkCalls = new CopyOnWriteArrayList<>();
        final List<UUID> prepareMaterialRequirementCalls = new CopyOnWriteArrayList<>();
        final List<List<UUID>> prepareMaterialRequirementItemIds = new CopyOnWriteArrayList<>();
        final List<Object[]> changeQtyCalls = new CopyOnWriteArrayList<>();
        final List<Object[]> submitCalls = new CopyOnWriteArrayList<>();
        final List<UUID> receiptCalls = new CopyOnWriteArrayList<>();
        final List<List<ItemReleaseView>> prepareReleaseCalls = new CopyOnWriteArrayList<>();
        final List<List<ItemReleaseView>> releaseProductCalls = new CopyOnWriteArrayList<>();
        final List<List<MaterialActualUsageView>> releaseUsageCalls = new CopyOnWriteArrayList<>();
        final List<UUID> cancelCalls = new CopyOnWriteArrayList<>();
        final List<Optional<String>> cancelReasons = new CopyOnWriteArrayList<>();
        List<MaterialRequirementProductSelectionView> lastPrepareSelections = List.of();

        MaterialRequirementView requirement;
        RuntimeException submitFailure;
        ReleasePreviewView releasePreview;
        List<LogicalTransferView> logicalTransfers = List.of();
        ReceiptResultView receiptResult =
                new ReceiptResultView(ReceiptStatusView.RECEIVED, "ok");
        ReleaseResultView releaseResult;
        UUID productionWarehouseId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        final Map<UUID, OrderQuantityModeView> quantityModes = new HashMap<>();
        final Map<UUID, MaterialRequirementProductCoverageView> coverageByItem = new HashMap<>();
        RuntimeException changeOrderQuantityModeFailure;
        RuntimeException prepareFailure;

        @Override
        public DestinationWarehouseView destinationWarehouse() {
            return new DestinationWarehouseView(Optional.ofNullable(productionWarehouseId));
        }

        @Override
        public void acceptOrderIntoProduction(UUID orderId, String createdBy) {
            acceptCalls.add(orderId);
            acceptActors.add(createdBy);
        }

        @Override
        public void checkMaterialAvailability(UUID orderId) {
            checkCalls.add(orderId);
        }

        void putOrderQuantityMode(UUID orderId, QuantityModeView mode, long version) {
            quantityModes.put(orderId, new OrderQuantityModeView(orderId, mode, version));
        }

        void putCoverage(MaterialRequirementProductCoverageView coverage) {
            coverageByItem.put(coverage.sourceOrderItemId(), coverage);
        }

        @Override
        public MaterialRequirementView prepareMaterialRequirement(
                List<MaterialRequirementProductSelectionView> selections) {
            if (prepareFailure != null) {
                throw prepareFailure;
            }
            if (!selections.isEmpty()) {
                prepareMaterialRequirementCalls.add(selections.getFirst().sourceOrderId());
                prepareMaterialRequirementItemIds.add(
                        selections.stream()
                                .map(MaterialRequirementProductSelectionView::sourceOrderItemId)
                                .toList());
            }
            lastPrepareSelections = List.copyOf(selections);
            return requirement;
        }

        @Override
        public Optional<MaterialRequirementView> getMaterialRequirement(UUID requirementId) {
            if (requirement != null && requirement.requirementId().equals(requirementId)) {
                return Optional.of(requirement);
            }
            return Optional.empty();
        }

        @Override
        public List<MaterialRequirementDraftSummaryView> listMaterialRequirementDrafts() {
            if (requirement == null
                    || requirement.status() != MaterialRequirementStatusView.DRAFT) {
                return List.of();
            }
            long orderCount =
                    requirement.sourceItems().stream()
                            .map(MaterialRequirementSourceItemView::sourceOrderId)
                            .distinct()
                            .count();
            return List.of(
                    new MaterialRequirementDraftSummaryView(
                            requirement.requirementId(),
                            requirement.createdAt(),
                            requirement.sourceItems().size(),
                            Math.toIntExact(orderCount)));
        }

        @Override
        public List<MaterialRequirementProductCoverageView> getMaterialRequirementProductCoverage(
                List<MaterialRequirementSourceItemRefView> sourceItems) {
            if (coverageByItem.isEmpty()) {
                return List.of();
            }
            List<MaterialRequirementProductCoverageView> out = new ArrayList<>();
            for (MaterialRequirementSourceItemRefView ref : sourceItems) {
                MaterialRequirementProductCoverageView coverage =
                        coverageByItem.get(ref.sourceOrderItemId());
                if (coverage != null) {
                    out.add(coverage);
                }
            }
            return List.copyOf(out);
        }

        @Override
        public MaterialRequirementView changeMaterialRequirementQuantity(
                UUID requirementId, UUID lineId, BigDecimal quantity, long expectedVersion) {
            changeQtyCalls.add(new Object[] {requirementId, lineId, quantity, expectedVersion});
            if (requirement != null) {
                List<MaterialRequirementLineView> lines = new ArrayList<>();
                for (MaterialRequirementLineView line : requirement.lines()) {
                    if (line.lineId().equals(lineId)) {
                        lines.add(
                                new MaterialRequirementLineView(
                                        line.lineId(),
                                        line.materialReferenceId(),
                                        line.materialCode(),
                                        line.materialName(),
                                        line.color(),
                                        line.unitOfMeasure(),
                                        quantity,
                                        line.sourceOrderItemIds()));
                    } else {
                        lines.add(line);
                    }
                }
                requirement =
                        new MaterialRequirementView(
                                requirement.requirementId(),
                                requirement.sourceItems(),
                                requirement.destinationWarehouseId(),
                                requirement.createdAt(),
                                Instant.parse("2026-01-02T00:00:00Z"),
                                requirement.version() + 1,
                                MaterialRequirementStatusView.DRAFT,
                                Optional.empty(),
                                Optional.empty(),
                                lines);
            }
            return requirement;
        }

        @Override
        public SubmitMaterialRequirementResultView submitMaterialRequirement(
                UUID requirementId, long expectedVersion) {
            submitCalls.add(new Object[] {requirementId, expectedVersion});
            if (submitFailure != null) {
                throw submitFailure;
            }
            if (requirement != null) {
                requirement =
                        new MaterialRequirementView(
                                requirement.requirementId(),
                                requirement.sourceItems(),
                                requirement.destinationWarehouseId(),
                                requirement.createdAt(),
                                Instant.parse("2026-01-03T00:00:00Z"),
                                requirement.version() + 1,
                                MaterialRequirementStatusView.SUBMITTED,
                                Optional.of(Instant.parse("2026-01-03T00:00:00Z")),
                                Optional.of("master"),
                                requirement.lines());
            }
            List<GeneratedTransferDocumentView> documents =
                    requirement == null
                            ? List.of()
                            : List.of(
                                    new GeneratedTransferDocumentView(
                                            UUID.fromString("dddddddd-dddd-4ddd-8ddd-dddddddddddd"),
                                            UUID.fromString("11111111-1111-4111-8111-111111111111"),
                                            requirement.destinationWarehouseId()));
            return new SubmitMaterialRequirementResultView(
                    requirementId,
                    requirement == null ? expectedVersion : requirement.version(),
                    MaterialRequirementStatusView.SUBMITTED,
                    submitCalls.size() == 1,
                    documents);
        }

        @Override
        public List<LogicalTransferView> listLogicalTransfers(UUID orderId) {
            return logicalTransfers;
        }

        @Override
        public ReceiptResultView confirmMaterialReceipt(UUID logicalTransferId) {
            receiptCalls.add(logicalTransferId);
            return receiptResult;
        }

        @Override
        public ReleasePreviewView prepareRelease(UUID orderId, List<ItemReleaseView> itemReleases) {
            prepareReleaseCalls.add(List.copyOf(itemReleases));
            return releasePreview;
        }

        @Override
        public ReleaseResultView releaseProducts(
                UUID orderId,
                List<ItemReleaseView> itemReleases,
                List<MaterialActualUsageView> materialActualUsages) {
            releaseProductCalls.add(List.copyOf(itemReleases));
            releaseUsageCalls.add(List.copyOf(materialActualUsages));
            return releaseResult;
        }

        @Override
        public void cancelOrderProduction(UUID orderId, Optional<String> reason) {
            cancelCalls.add(orderId);
            cancelReasons.add(reason);
        }

        @Override
        public OrderQuantityModeView getOrderQuantityMode(UUID orderId) {
            return quantityModes.getOrDefault(
                    orderId, new OrderQuantityModeView(orderId, QuantityModeView.STANDARD, 0L));
        }

        @Override
        public List<OrderQuantityModeView> getOrderQuantityModes(List<UUID> orderIds) {
            List<OrderQuantityModeView> views = new ArrayList<>();
            for (UUID orderId : orderIds.stream().distinct().toList()) {
                views.add(getOrderQuantityMode(orderId));
            }
            return List.copyOf(views);
        }

        @Override
        public OrderQuantityModeView changeOrderQuantityMode(
                UUID orderId, QuantityModeView quantityMode, long expectedVersion) {
            if (changeOrderQuantityModeFailure != null) {
                throw changeOrderQuantityModeFailure;
            }
            OrderQuantityModeView current = getOrderQuantityMode(orderId);
            if (current.version() != expectedVersion) {
                throw new OrderQuantityModeOptimisticLockStubException(orderId, expectedVersion);
            }
            OrderQuantityModeView saved =
                    new OrderQuantityModeView(orderId, quantityMode, expectedVersion + 1);
            quantityModes.put(orderId, saved);
            return saved;
        }
    }

    static final class OrderQuantityModeOptimisticLockStubException extends RuntimeException {
        OrderQuantityModeOptimisticLockStubException(UUID orderId, long expectedVersion) {
            super(
                    "OrderQuantityModeOptimisticLock: quantity mode version mismatch for order "
                            + orderId
                            + ", expected "
                            + expectedVersion);
        }
    }

    static final class StubWorklistQuery implements OrderWorklistQuery {
        final List<OrderWorklistRowDto> rows = new ArrayList<>();

        @Override
        public List<OrderWorklistRowDto> listWorklistRows(OrderWorklistCriteria criteria) {
            return List.copyOf(rows);
        }

        @Override
        public List<OrderCustomerOptionDto> listKnownCustomers() {
            return List.of();
        }
    }

    static final class StubOrderQuery implements OrderQueryService {
        OrderDto order;
        final Map<OrderId, OrderDto> orders = new HashMap<>();
        final List<OrderItemDto> items = new ArrayList<>();
        final List<OrderSummaryDto> searchResults = new ArrayList<>();

        @Override
        public PageResult<OrderSummaryDto> searchOrders(
                OrderSearchCriteria criteria, PageRequest pageRequest) {
            return PageResult.of(
                    searchResults,
                    pageRequest.pageIndex(),
                    pageRequest.pageSize(),
                    searchResults.size());
        }

        @Override
        public Optional<OrderDto> getOrder(OrderId orderId) {
            OrderDto mapped = orders.get(orderId);
            if (mapped != null) {
                return Optional.of(mapped);
            }
            if (order != null && order.orderId().equals(orderId)) {
                return Optional.of(order);
            }
            return Optional.empty();
        }

        @Override
        public PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest) {
            List<OrderItemDto> forOrder =
                    items.stream().filter(i -> i.orderId().equals(orderId)).toList();
            int pageIndex = pageRequest.pageIndex();
            int pageSize = pageRequest.pageSize();
            int from = pageIndex * pageSize;
            if (from >= forOrder.size()) {
                return PageResult.of(List.of(), pageIndex, pageSize, forOrder.size());
            }
            int to = Math.min(from + pageSize, forOrder.size());
            return PageResult.of(
                    forOrder.subList(from, to), pageIndex, pageSize, forOrder.size());
        }

        @Override
        public Optional<OrderItemDto> getOrderItem(OrderItemId orderItemId) {
            return items.stream().filter(i -> i.orderItemId().equals(orderItemId)).findFirst();
        }

        @Override
        public PageResult<OrderItemRevisionDto> getOrderItemRevisions(
                OrderItemId orderItemId, PageRequest pageRequest) {
            return PageResult.of(List.of(), 0, pageRequest.pageSize(), 0);
        }

        @Override
        public Optional<OrderItemRevisionDto> getOrderItemRevision(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderItemRevisionDto> getActiveOrderItemRevision(OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<ItemSpecificationDto> getItemSpecification(
                OrderItemId orderItemId, RevisionNumber revisionNumber) {
            return Optional.empty();
        }

        @Override
        public Optional<ProductionSpecificationDto> getCurrentItemSpecification(
                OrderItemId orderItemId) {
            return Optional.empty();
        }

        @Override
        public Optional<ProductionSpecificationDto> getSpecificationById(
                SpecificationId specificationId) {
            return Optional.empty();
        }

        @Override
        public Optional<OrderForProductionDto> getOrderForProduction(OrderId orderId) {
            return Optional.empty();
        }
    }

    static final class StubWarehouseApi implements WarehouseApi {
        final Map<UUID, List<StorageCellView>> cellsByWarehouse = new HashMap<>();

        @Override
        public List<WarehouseView> listWarehouses() {
            return List.of();
        }

        @Override
        public List<WarehouseView> listMyWarehouses() {
            return List.of();
        }

        @Override
        public List<UUID> listResponsibleUserIds(UUID warehouseId) {
            return List.of();
        }

        @Override
        public void assignUserToWarehouse(UUID warehouseId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeUserFromWarehouse(UUID warehouseId, UUID userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WarehouseView createWarehouse(CreateWarehouseCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WarehouseView updateWarehouse(
                com.tmp.warehouse.api.WarehouseApi.UpdateWarehouseCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            return cellsByWarehouse.getOrDefault(warehouseId, List.of());
        }

        @Override
        public StorageCellView createStorageCell(CreateStorageCellCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StorageCellView updateStorageCell(
                com.tmp.warehouse.api.WarehouseApi.UpdateStorageCellCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StockView> getStock(String materialCode) {
            return List.of();
        }

        @Override
        public List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId) {
            return List.of();
        }

        @Override
        public List<StockView> getStockByWarehouse(UUID warehouseId) {
            return List.of();
        }

        @Override
        public List<MaterialReferenceView> listMaterialReferences() {
            return List.of();
        }

        @Override
        public List<String> listUnitOfMeasures() {
            return List.of();
        }

        @Override
        public MaterialReferenceDisplayView getMaterialReferenceDisplay(String materialCode) {
            return new MaterialReferenceDisplayView(materialCode, "", "", "", "");
        }

        @Override
        public AvailabilityResult checkAvailability(String materialCode, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AvailabilityResult checkAvailability(
                WarehouseApi.MaterialIdentityRequest identity, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AvailabilityResult checkAvailability(
                WarehouseApi.MaterialIdentityRequest identity,
                UUID warehouseId,
                BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AvailabilityResult checkAvailability(
                UUID materialReferenceId, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AvailabilityResult checkAvailability(
                UUID materialReferenceId, UUID warehouseId, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AvailabilityResult checkAvailabilityByLegacyArticle(
                String materialCode, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ReservationLinkView> listReservationLinks(String materialCode) {
            return List.of();
        }

        @Override
        public List<StockView> getStockByMaterialReferenceId(UUID materialReferenceId) {
            return List.of();
        }

        final Map<UUID, String> transferStatuses = new HashMap<>();

        @Override
        public TransferStatusView getTransferStatus(UUID operationId) {
            String status = transferStatuses.getOrDefault(operationId, "DRAFT");
            return new TransferStatusView(
                    operationId,
                    OperationKind.TRANSFER_SEND,
                    status,
                    UUID.randomUUID(),
                    BigDecimal.ONE,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null);
        }

        @Override
        public List<TransferRequestView> listTransferDrafts() {
            return List.of();
        }

        @Override
        public ReservationLinkView createReservationLink(CreateReservationLinkCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult executeWarehouseOperation(ExecuteOperationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult receive(WarehouseApi.ReceiptCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult consume(WarehouseApi.ConsumptionCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferRequestView createTransferDraft(CreateTransferDraftCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult sendTransfer(UUID transferDraftOperationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult receiveTransfer(UUID sendOperationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferDocumentReceiveResult receiveTransferDocument(
                ReceiveTransferDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public WarehouseApi.TransferDocumentRejectResult rejectTransferDocument(
                WarehouseApi.RejectTransferDocumentCommand command) {
            throw new UnsupportedOperationException("rejectTransferDocument");
        }

        @Override
        public WarehouseApi.TransferDocumentReturnResult returnTransferMaterials(
                WarehouseApi.ReturnTransferMaterialsCommand command) {
            throw new UnsupportedOperationException("returnTransferMaterials");
        }

        @Override
        public TransferDocumentView createTransferDocument(CreateTransferDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransferDocumentView updateTransferDocument(UpdateTransferDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteTransferDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult sendTransferDocument(
                com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand command) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public TransferDocumentView getTransferDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<com.tmp.warehouse.api.WarehouseApi.TransferDocumentSourceSuggestionLine>
                suggestTransferDocumentSourceAllocations(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnPlanItem>
                listTransferDocumentReturnPlan(UUID documentId) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult>
                routeMaterials(
                        UUID destinationWarehouseId,
                        java.util.List<com.tmp.warehouse.api.WarehouseApi.MaterialDemand> demands) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<WarehouseTaskView> listMyWarehouseTasks(UUID warehouseId) {
            return List.of();
        }

        @Override
        public WarehouseTaskView takeTransferTaskInWork(UUID documentId) {
            throw new UnsupportedOperationException();
        }
    }

    static OrderDto order(UUID orderId, String number) {
        return OrderDto.of(
                OrderId.of(orderId),
                number,
                OrderStatus.ACTIVE,
                "C-1",
                "Клиент",
                null,
                null,
                null,
                null,
                null,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    static OrderItemDto item(UUID orderId, UUID itemId, String position) {
        return OrderItemDto.of(
                OrderItemId.of(itemId),
                OrderId.of(orderId),
                "P-1",
                "Изделие",
                null,
                position,
                OrderItemStatus.ACTIVE,
                RevisionNumber.first(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    static OrderItemDto item(UUID orderId, UUID itemId) {
        return item(orderId, itemId, null);
    }

    static OrderWorklistRowDto worklistRow(UUID orderId, String number, String customer) {
        return OrderWorklistRowDto.of(
                OrderId.of(orderId),
                number,
                OrderStatus.ACTIVE,
                null,
                customer,
                Instant.parse("2026-01-01T00:00:00Z"),
                1L);
    }

    static OrderProductionListFacts productionListFacts(
            UUID orderId,
            OrderProductionViewStatus status,
            long ordered,
            long released,
            long active) {
        return new OrderProductionListFacts(orderId, status, ordered, released, active, false);
    }
}
