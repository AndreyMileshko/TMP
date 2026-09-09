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
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.ReleaseResultView;
import com.tmp.production.api.ProductionQueryApi;
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
import java.util.HashMap;
import java.util.HashSet;
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
        Optional<MaterialAvailabilityResultView> availability = Optional.empty();
        final List<ProductionHistoryEntryView> history = new ArrayList<>();
        int getOrderProductionViewCalls;
        int getMaterialAvailabilityCalls;

        @Override
        public OrderProductionView getOrderProductionView(UUID orderId) {
            getOrderProductionViewCalls++;
            return view;
        }

        @Override
        public Optional<ItemProductionStateView> getItemProductionState(UUID orderItemId) {
            return Optional.ofNullable(itemStates.get(orderItemId));
        }

        @Override
        public Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(
                UUID orderId) {
            getMaterialAvailabilityCalls++;
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
        final List<UUID> receiptCalls = new CopyOnWriteArrayList<>();
        final List<List<ItemReleaseView>> prepareReleaseCalls = new CopyOnWriteArrayList<>();
        final List<List<ItemReleaseView>> releaseProductCalls = new CopyOnWriteArrayList<>();
        final List<List<MaterialActualUsageView>> releaseUsageCalls = new CopyOnWriteArrayList<>();
        final List<UUID> cancelCalls = new CopyOnWriteArrayList<>();
        final List<Optional<String>> cancelReasons = new CopyOnWriteArrayList<>();

        MaterialRequirementView requirement;
        ReleasePreviewView releasePreview;
        List<LogicalTransferView> logicalTransfers = List.of();
        ReceiptResultView receiptResult =
                new ReceiptResultView(ReceiptStatusView.RECEIVED, "ok");
        ReleaseResultView releaseResult;
        UUID productionWarehouseId = UUID.fromString("22222222-2222-4222-8222-222222222222");

        @Override
        public DestinationWarehouseView destinationWarehouse() {
            return new DestinationWarehouseView(productionWarehouseId);
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

        @Override
        public MaterialRequirementView prepareMaterialRequirement(
                UUID orderId, List<UUID> selectedOrderItemIds) {
            prepareMaterialRequirementCalls.add(orderId);
            prepareMaterialRequirementItemIds.add(List.copyOf(selectedOrderItemIds));
            return requirement;
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
                                requirement.sourceOrderId(),
                                requirement.destinationWarehouseId(),
                                requirement.createdAt(),
                                Instant.parse("2026-01-02T00:00:00Z"),
                                requirement.version() + 1,
                                MaterialRequirementStatusView.DRAFT,
                                lines);
            }
            return requirement;
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
    }

    static final class StubOrderQuery implements OrderQueryService {
        OrderDto order;
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
            if (order != null && order.orderId().equals(orderId)) {
                return Optional.of(order);
            }
            return Optional.empty();
        }

        @Override
        public PageResult<OrderItemDto> getOrderItems(OrderId orderId, PageRequest pageRequest) {
            return PageResult.of(
                    items, pageRequest.pageIndex(), pageRequest.pageSize(), items.size());
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
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            return cellsByWarehouse.getOrDefault(warehouseId, List.of());
        }

        @Override
        public StorageCellView createStorageCell(CreateStorageCellCommand command) {
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
}
