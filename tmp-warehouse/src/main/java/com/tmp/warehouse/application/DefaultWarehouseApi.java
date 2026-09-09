package com.tmp.warehouse.application;

import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import com.tmp.warehouse.api.MaterialReferenceDisplay;
import com.tmp.warehouse.api.MaterialReferenceDisplayPort;
import com.tmp.warehouse.api.WarehouseApi.MaterialDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult;
import com.tmp.warehouse.api.WarehouseApi.ReceiveTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.RejectTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.ReturnTransferMaterialsCommand;
import com.tmp.warehouse.api.WarehouseApi.SendTransferDocumentCommand;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReceiveResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentRejectResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnAllocationInput;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentReturnResult;
import com.tmp.warehouse.api.WarehouseApi.TransferDocumentSendResult;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import com.tmp.warehouse.domain.InvalidWarehouseStateException;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.ReservationTargetType;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferDocumentSendAllocation;
import com.tmp.warehouse.domain.TransferDocumentSettlement;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.TransferReceiptSettlementItem;
import com.tmp.warehouse.domain.TransferReturnSettlementItem;
import com.tmp.warehouse.domain.TransferSettlementDecision;
import com.tmp.warehouse.domain.TransferSettlementState;
import com.tmp.warehouse.domain.UnitOfMeasure;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSendAllocationRepository;
import com.tmp.warehouse.domain.repository.TransferDocumentSettlementRepository;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.TransferReceiptSettlementItemRepository;
import com.tmp.warehouse.domain.repository.TransferReturnSettlementItemRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.repository.WarehouseUserResponsibilityRepository;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Default Public API adapter for Warehouse (Specification §17 / §18).
 *
 * <p>Maps public DTOs to existing application services without changing Warehouse business rules.
 * Enforces Warehouse permissions via the public {@link AuthorizationService} and operational
 * warehouse responsibility via {@link WarehouseResponsibilityGuard} (ADR-037). Does not expose
 * domain aggregates, does not allow direct Stock Position or Movement mutation.
 *
 * <p>Responsibility {@code userId} values are opaque Security user UUIDs; Warehouse does not
 * validate user existence against Security persistence (no public exists-by-id API). Future
 * administration UI selects users through Security public directory APIs.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected application collaborators.")
public final class DefaultWarehouseApi implements WarehouseApi {

    private final AuthorizationService authorization;
    private final AuthenticationService authentication;
    private final WarehouseResponsibilityGuard responsibilityGuard;
    private final WarehouseUserResponsibilityRepository responsibilities;
    private final WarehouseCatalogRepository warehouses;
    private final StockPositionRepository stockPositions;
    private final MaterialReferenceRepository materials;
    private final MaterialReferenceDisplayPort materialDisplay;
    private final WarehouseReservationLinkService reservationLinks;
    private final WarehouseReceiptService receipts;
    private final WarehouseMoveService moves;
    private final WarehouseTransferService transfers;
    private final WarehouseTransferDocumentService transferDocuments;
    private final WarehouseConsumptionService consumptions;
    private final WarehouseAdjustmentService adjustments;
    private final WarehouseOperationRepository operations;
    private final TransferOperationContextRepository transferContexts;
    private final MaterialSourceRoutingService sourceRouting;
    private final WarehouseOperationalInboxService operationalInbox;
    private final WarehouseTransferSendService transferSend;
    private final WarehouseTransferReceiveService transferReceive;
    private final WarehouseTransferRejectService transferReject;
    private final WarehouseTransferReturnService transferReturn;
    private final TransferDocumentSendAllocationRepository sendAllocations;
    private final TransferDocumentSettlementRepository settlements;
    private final TransferReceiptSettlementItemRepository receiptItems;
    private final TransferReturnSettlementItemRepository returnItems;

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            AuthenticationService authentication,
            WarehouseResponsibilityGuard responsibilityGuard,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials,
            MaterialReferenceDisplayPort materialDisplay,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts) {
        this(
                authorization,
                authentication,
                responsibilityGuard,
                responsibilities,
                warehouses,
                stockPositions,
                materials,
                materialDisplay,
                reservationLinks,
                receipts,
                moves,
                transfers,
                transferDocuments,
                consumptions,
                adjustments,
                operations,
                transferContexts,
                new MaterialSourceRoutingService(
                        new CatalogAvailableStockAggregationQuery(
                                stockPositions, warehouses, materials)),
                null);
    }

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            AuthenticationService authentication,
            WarehouseResponsibilityGuard responsibilityGuard,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials,
            MaterialReferenceDisplayPort materialDisplay,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            MaterialSourceRoutingService sourceRouting) {
        this(
                authorization,
                authentication,
                responsibilityGuard,
                responsibilities,
                warehouses,
                stockPositions,
                materials,
                materialDisplay,
                reservationLinks,
                receipts,
                moves,
                transfers,
                transferDocuments,
                consumptions,
                adjustments,
                operations,
                transferContexts,
                sourceRouting,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            AuthenticationService authentication,
            WarehouseResponsibilityGuard responsibilityGuard,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials,
            MaterialReferenceDisplayPort materialDisplay,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            MaterialSourceRoutingService sourceRouting,
            WarehouseOperationalInboxService operationalInbox) {
        this(
                authorization,
                authentication,
                responsibilityGuard,
                responsibilities,
                warehouses,
                stockPositions,
                materials,
                materialDisplay,
                reservationLinks,
                receipts,
                moves,
                transfers,
                transferDocuments,
                consumptions,
                adjustments,
                operations,
                transferContexts,
                sourceRouting,
                operationalInbox,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            AuthenticationService authentication,
            WarehouseResponsibilityGuard responsibilityGuard,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials,
            MaterialReferenceDisplayPort materialDisplay,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            MaterialSourceRoutingService sourceRouting,
            WarehouseOperationalInboxService operationalInbox,
            WarehouseTransferSendService transferSend) {
        this(
                authorization,
                authentication,
                responsibilityGuard,
                responsibilities,
                warehouses,
                stockPositions,
                materials,
                materialDisplay,
                reservationLinks,
                receipts,
                moves,
                transfers,
                transferDocuments,
                consumptions,
                adjustments,
                operations,
                transferContexts,
                sourceRouting,
                operationalInbox,
                transferSend,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            AuthenticationService authentication,
            WarehouseResponsibilityGuard responsibilityGuard,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials,
            MaterialReferenceDisplayPort materialDisplay,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            MaterialSourceRoutingService sourceRouting,
            WarehouseOperationalInboxService operationalInbox,
            WarehouseTransferSendService transferSend,
            WarehouseTransferReceiveService transferReceive,
            TransferDocumentSendAllocationRepository sendAllocations,
            TransferDocumentSettlementRepository settlements) {
        this(
                authorization,
                authentication,
                responsibilityGuard,
                responsibilities,
                warehouses,
                stockPositions,
                materials,
                materialDisplay,
                reservationLinks,
                receipts,
                moves,
                transfers,
                transferDocuments,
                consumptions,
                adjustments,
                operations,
                transferContexts,
                sourceRouting,
                operationalInbox,
                transferSend,
                transferReceive,
                null,
                null,
                sendAllocations,
                settlements,
                null,
                null);
    }

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            AuthenticationService authentication,
            WarehouseResponsibilityGuard responsibilityGuard,
            WarehouseUserResponsibilityRepository responsibilities,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials,
            MaterialReferenceDisplayPort materialDisplay,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseTransferDocumentService transferDocuments,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts,
            MaterialSourceRoutingService sourceRouting,
            WarehouseOperationalInboxService operationalInbox,
            WarehouseTransferSendService transferSend,
            WarehouseTransferReceiveService transferReceive,
            WarehouseTransferRejectService transferReject,
            WarehouseTransferReturnService transferReturn,
            TransferDocumentSendAllocationRepository sendAllocations,
            TransferDocumentSettlementRepository settlements,
            TransferReceiptSettlementItemRepository receiptItems,
            TransferReturnSettlementItemRepository returnItems) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.authentication = Objects.requireNonNull(authentication, "authentication");
        this.responsibilityGuard =
                Objects.requireNonNull(responsibilityGuard, "responsibilityGuard");
        this.responsibilities = Objects.requireNonNull(responsibilities, "responsibilities");
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.materialDisplay = Objects.requireNonNull(materialDisplay, "materialDisplay");
        this.reservationLinks = Objects.requireNonNull(reservationLinks, "reservationLinks");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.transferDocuments = Objects.requireNonNull(transferDocuments, "transferDocuments");
        this.consumptions = Objects.requireNonNull(consumptions, "consumptions");
        this.adjustments = Objects.requireNonNull(adjustments, "adjustments");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.transferContexts = Objects.requireNonNull(transferContexts, "transferContexts");
        this.sourceRouting = Objects.requireNonNull(sourceRouting, "sourceRouting");
        this.operationalInbox = operationalInbox;
        this.transferSend = transferSend;
        this.transferReceive = transferReceive;
        this.transferReject = transferReject;
        this.transferReturn = transferReturn;
        this.sendAllocations = sendAllocations;
        this.settlements = settlements;
        this.receiptItems = receiptItems;
        this.returnItems = returnItems;
    }

    @Override
    public List<WarehouseView> listWarehouses() {
        requireCatalogueListAccess(WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW);
        return warehouses.findAll().stream().map(this::toWarehouseView).toList();
    }

    @Override
    public List<WarehouseView> listMyWarehouses() {
        requireCatalogueListAccess(WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW);
        UUID userId =
                authentication
                        .currentSession()
                        .orElseThrow(
                                () ->
                                        new AccessDeniedException(
                                                "Access denied: authentication required"))
                        .userId()
                        .value();
        Set<UUID> responsible =
                new HashSet<>(
                        responsibilities.listWarehouseIdsForUser(userId).stream()
                                .map(WarehouseId::value)
                                .toList());
        return warehouses.findAll().stream()
                .filter(Warehouse::active)
                .filter(warehouse -> responsible.contains(warehouse.id().value()))
                .map(this::toWarehouseView)
                .toList();
    }

    @Override
    public List<UUID> listResponsibleUserIds(UUID warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW);
        requireWarehouseExists(WarehouseId.of(warehouseId));
        return responsibilities.listUserIdsForWarehouse(WarehouseId.of(warehouseId));
    }

    @Override
    public WarehouseView createWarehouse(CreateWarehouseCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_STRUCTURE_CREATE);
        Warehouse warehouse =
                Warehouse.of(
                        WarehouseId.generate(),
                        command.code(),
                        command.name(),
                        command.active());
        try {
            return toWarehouseView(warehouses.save(warehouse));
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "Warehouse code already exists: " + command.code().trim(), ex);
        }
    }

    @Override
    public List<StorageCellView> listStorageCells(UUID warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        requireCatalogueListAccess(WarehousePermissions.STORAGE_CELL_VIEW);
        return warehouses.findStorageCellsByWarehouse(WarehouseId.of(warehouseId)).stream()
                .map(this::toStorageCellView)
                .toList();
    }

    @Override
    public StorageCellView createStorageCell(CreateStorageCellCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.STORAGE_CELL_CREATE);
        WarehouseId warehouseId = WarehouseId.of(command.warehouseId());
        requireWarehouseExists(warehouseId);
        StorageCell cell =
                StorageCell.of(
                        StorageCellId.generate(),
                        warehouseId,
                        command.code(),
                        command.active());
        try {
            return toStorageCellView(warehouses.save(cell));
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalArgumentException(
                    "Storage cell code already exists in warehouse: " + command.code().trim(),
                    ex);
        }
    }

    @Override
    public void assignUserToWarehouse(UUID warehouseId, UUID userId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(userId, "userId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE);
        requireWarehouseExists(WarehouseId.of(warehouseId));
        responsibilities.assign(userId, WarehouseId.of(warehouseId));
    }

    @Override
    public void removeUserFromWarehouse(UUID warehouseId, UUID userId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(userId, "userId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_STRUCTURE_UPDATE);
        requireWarehouseExists(WarehouseId.of(warehouseId));
        responsibilities.remove(userId, WarehouseId.of(warehouseId));
    }

    @Override
    public List<MaterialReferenceView> listMaterialReferences() {
        requireMaterialDisplayAccess();
        return materials.findAll().stream().map(this::toMaterialReferenceView).toList();
    }

    @Override
    public List<String> listUnitOfMeasures() {
        requireMaterialDisplayAccess();
        return UnitOfMeasure.codes();
    }

    @Override
    public List<StockView> getStock(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        return toPositiveQuantityStockViews(stockPositions.findByArticle(materialCode.trim()));
    }

    @Override
    public List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        WarehouseId wh = WarehouseId.of(warehouseId);
        StorageCellId cell = StorageCellId.of(storageCellId);
        return toPositiveQuantityStockViews(stockPositions.findByArticle(materialCode.trim()))
                .stream()
                .filter(
                        view ->
                                view.warehouseId().equals(wh.value())
                                        && view.storageCellId().equals(cell.value()))
                .toList();
    }

    @Override
    public List<StockView> getStockByWarehouse(UUID warehouseId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        return toPositiveQuantityStockViews(
                stockPositions.findByWarehouse(WarehouseId.of(warehouseId)));
    }

    @Override
    public MaterialReferenceDisplayView getMaterialReferenceDisplay(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        requireMaterialDisplayAccess();
        return materials.findByNaturalKey(materialCode, "", "", "")
                .map(material -> toMaterialReferenceDisplayView(material))
                .orElseGet(
                        () ->
                                toMaterialReferenceDisplayView(
                                        materialDisplay.resolve(materialCode)));
    }

    @Override
    public List<StockView> getStockByMaterialReferenceId(UUID materialReferenceId) {
        Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        MaterialReference material = requireMaterial(materialReferenceId);
        return toPositiveQuantityStockViews(stockPositions.findByMaterial(material));
    }

    @Override
    public AvailabilityResult checkAvailability(MaterialIdentityRequest identity, BigDecimal quantity) {
        Objects.requireNonNull(identity, "identity");
        MaterialReference material = requireMaterialByIdentity(identity);
        return availabilityForMaterial(material, null, quantity);
    }

    @Override
    public AvailabilityResult checkAvailability(
            MaterialIdentityRequest identity, UUID warehouseId, BigDecimal quantity) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(warehouseId, "warehouseId");
        MaterialReference material = requireMaterialByIdentity(identity);
        return availabilityForMaterial(material, WarehouseId.of(warehouseId), quantity);
    }

    @Override
    public AvailabilityResult checkAvailability(UUID materialReferenceId, BigDecimal quantity) {
        MaterialReference material = requireMaterial(materialReferenceId);
        return availabilityForMaterial(material, null, quantity);
    }

    @Override
    public AvailabilityResult checkAvailability(
            UUID materialReferenceId, UUID warehouseId, BigDecimal quantity) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        MaterialReference material = requireMaterial(materialReferenceId);
        return availabilityForMaterial(material, WarehouseId.of(warehouseId), quantity);
    }

    @Override
    public AvailabilityResult checkAvailabilityByLegacyArticle(
            String materialCode, BigDecimal quantity) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(quantity, "quantity");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Requested quantity must be positive: " + quantity);
        }
        MaterialReference material =
                materials
                        .findByNaturalKey(materialCode, "", "", "")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Material reference not found: " + materialCode));
        return availabilityForMaterial(material, null, quantity);
    }

    @Override
    public TransferStatusView getTransferStatus(UUID operationId) {
        Objects.requireNonNull(operationId, "operationId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        WarehouseOperation operation =
                operations
                        .findById(com.tmp.warehouse.domain.WarehouseOperationId.of(operationId))
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Warehouse operation not found: " + operationId));
        if (operation.type() != WarehouseOperationType.TRANSFER_SEND
                && operation.type() != WarehouseOperationType.TRANSFER_RECEIVE) {
            throw new IllegalArgumentException(
                    "Not a transfer operation: " + operationId + ", type=" + operation.type());
        }
        // Document-managed sends are settled via transfer_document_settlement — not legacy
        // TransferOperationContext.receiveOperationId. Exclude them from the single-shot legacy
        // SENT→RECEIVED interpretation so they are not left as false pending legacy receipts.
        if (operation.type() == WarehouseOperationType.TRANSFER_SEND
                && sendAllocations != null
                && sendAllocations.existsBySendOperationId(operation.id())) {
            return documentManagedTransferStatus(operation);
        }
        var context =
                operation.type() == WarehouseOperationType.TRANSFER_RECEIVE
                        ? transferContexts.findByReceiveOperationId(operation.id())
                        : transferContexts.findByOperationId(operation.id());
        UUID receiveOperationId =
                context.map(ctx -> ctx.receiveOperationId())
                        .map(com.tmp.warehouse.domain.WarehouseOperationId::value)
                        .orElse(
                                operation.type() == WarehouseOperationType.TRANSFER_RECEIVE
                                        ? operation.id().value()
                                        : null);
        return new TransferStatusView(
                operation.id().value(),
                OperationKind.valueOf(operation.type().name()),
                logicalTransferStatus(operation, context.orElse(null)),
                operation.material().id().value(),
                operation.quantity().value(),
                operation.warehouseId().value(),
                operation.storageCellId().value(),
                context.map(ctx -> ctx.destinationWarehouseId().value()).orElse(null),
                context
                        .flatMap(TransferOperationContext::destinationStorageCellIdOptional)
                        .map(StorageCellId::value)
                        .orElse(null),
                receiveOperationId);
    }

    private TransferStatusView documentManagedTransferStatus(WarehouseOperation send) {
        UUID documentId =
                sendAllocations
                        .findDocumentIdBySendOperationId(send.id())
                        .orElseThrow(
                                () ->
                                        new InvalidWarehouseStateException(
                                                "Document-managed send allocation missing document: "
                                                        + send.id()));
        TransferOperationContext context =
                transferContexts.findByOperationId(send.id()).orElse(null);
        TransferDocumentSettlement settlement =
                settlements == null ? null : settlements.findByDocumentId(documentId).orElse(null);
        TransferDocumentSendAllocation allocation =
                sendAllocations.findByDocumentId(documentId).stream()
                        .filter(
                                a ->
                                        a.sendOperationIdOptional()
                                                .map(id -> id.equals(send.id()))
                                                .orElse(false))
                        .findFirst()
                        .orElse(null);
        String status;
        if (send.status() == WarehouseOperationStatus.DRAFT) {
            status = "DRAFT";
        } else if (settlement == null) {
            status =
                    send.status() == WarehouseOperationStatus.COMPLETED
                            ? "SENT"
                            : send.status().name();
        } else if (settlement.settlementState() == TransferSettlementState.AWAITING_RECEIPT) {
            status =
                    send.status() == WarehouseOperationStatus.COMPLETED
                            ? "SENT"
                            : send.status().name();
        } else if (settlement.settlementState() == TransferSettlementState.RETURN_PENDING) {
            TransferSettlementDecision decision = settlement.decision().orElse(null);
            if (decision == TransferSettlementDecision.REJECTED) {
                status = "REJECTED";
            } else {
                status = "PARTIALLY_RECEIVED";
            }
        } else if (settlement.settlementState() == TransferSettlementState.SETTLED) {
            TransferSettlementDecision decision = settlement.decision().orElse(null);
            if (decision == TransferSettlementDecision.REJECTED) {
                status = "RETURNED";
            } else if (allocation == null
                    || receiptItems == null
                    || returnItems == null) {
                status = "RECEIVED";
            } else {
                BigDecimal accepted =
                        receiptItems.findBySendAllocationIds(List.of(allocation.id())).stream()
                                .map(item -> item.quantity().value())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal returned =
                        returnItems.findBySendAllocationIds(List.of(allocation.id())).stream()
                                .map(item -> item.quantity().value())
                                .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (accepted.compareTo(allocation.quantity().value()) == 0
                        && returned.signum() == 0) {
                    status = "RECEIVED";
                } else {
                    status = "PARTIALLY_RECEIVED";
                }
            }
        } else if (send.status() == WarehouseOperationStatus.COMPLETED) {
            status = "SENT";
        } else {
            status = send.status().name();
        }
        return new TransferStatusView(
                send.id().value(),
                OperationKind.TRANSFER_SEND,
                status,
                send.material().id().value(),
                send.quantity().value(),
                send.warehouseId().value(),
                send.storageCellId().value(),
                context == null ? null : context.destinationWarehouseId().value(),
                null,
                null);
    }

    @Override
    public List<TransferRequestView> listTransferDrafts() {
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        return operations
                .findByTypeAndStatus(
                        WarehouseOperationType.TRANSFER_SEND, WarehouseOperationStatus.DRAFT)
                .stream()
                .map(this::toTransferRequestView)
                .toList();
    }

    private TransferRequestView toTransferRequestView(WarehouseOperation draft) {
        TransferOperationContext context =
                transferContexts
                        .findByOperationId(draft.id())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Transfer context not found for draft: "
                                                        + draft.id().value()));
        return new TransferRequestView(
                draft.id().value(),
                logicalTransferStatus(draft, context),
                draft.material().id().value(),
                draft.quantity().value(),
                draft.warehouseId().value(),
                draft.storageCellId().value(),
                context.destinationWarehouseId().value(),
                context.destinationStorageCellIdOptional().map(StorageCellId::value).orElse(null));
    }

    @Override
    public OperationResult receive(ReceiptCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_RECEIPT);
        responsibilityGuard.requireResponsible(WarehouseId.of(command.warehouseId()));
        WarehouseOperation completed =
                receipts.receive(
                        new ReceiptRequest(
                                command.article(),
                                command.name(),
                                normalize(command.color()),
                                normalize(command.size()),
                                normalize(command.unitOfMeasure()),
                                StockQuantity.of(command.quantity()),
                                WarehouseId.of(command.warehouseId()),
                                StorageCellId.of(command.storageCellId())));
        return toOperationResult(OperationKind.RECEIPT, completed);
    }

    @Override
    public OperationResult consume(ConsumptionCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_CONSUMPTION);
        responsibilityGuard.requireResponsible(WarehouseId.of(command.warehouseId()));
        WarehouseOperation completed =
                consumptions.consume(
                        new ConsumptionRequest(
                                requireMaterial(command.materialReferenceId()),
                                StockQuantity.of(command.quantity()),
                                WarehouseId.of(command.warehouseId()),
                                StorageCellId.of(command.storageCellId())));
        return toOperationResult(OperationKind.CONSUMPTION, completed);
    }

    @Override
    public TransferRequestView createTransferDraft(CreateTransferDraftCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        responsibilityGuard.requireResponsible(WarehouseId.of(command.sourceWarehouseId()));
        WarehouseOperation draft =
                transfers.createDraft(
                        new WarehouseTransferService.TransferDraftRequest(
                                requireMaterial(command.materialReferenceId()),
                                StockQuantity.of(command.quantity()),
                                WarehouseId.of(command.sourceWarehouseId()),
                                StorageCellId.of(command.sourceStorageCellId()),
                                WarehouseId.of(command.destinationWarehouseId()),
                                StorageCellId.of(command.destinationStorageCellId())));
        return new TransferRequestView(
                draft.id().value(),
                draft.status().name(),
                draft.material().id().value(),
                draft.quantity().value(),
                draft.warehouseId().value(),
                draft.storageCellId().value(),
                command.destinationWarehouseId(),
                command.destinationStorageCellId());
    }

    @Override
    public OperationResult sendTransfer(UUID transferDraftOperationId) {
        Objects.requireNonNull(transferDraftOperationId, "transferDraftOperationId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseOperationId draftId = WarehouseOperationId.of(transferDraftOperationId);
        WarehouseOperation draft =
                operations
                        .findById(draftId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Warehouse operation not found: "
                                                        + transferDraftOperationId));
        responsibilityGuard.requireResponsible(draft.warehouseId());
        WarehouseOperation completed = transfers.sendDraft(draftId);
        return toOperationResult(OperationKind.TRANSFER_SEND, completed);
    }

    @Override
    public OperationResult receiveTransfer(UUID sendOperationId) {
        Objects.requireNonNull(sendOperationId, "sendOperationId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseOperationId sendId = WarehouseOperationId.of(sendOperationId);
        if (sendAllocations != null && sendAllocations.existsBySendOperationId(sendId)) {
            throw new InvalidWarehouseStateException(
                    "Document-managed transfer must be received through document-level settlement: sendOperationId="
                            + sendOperationId);
        }
        TransferOperationContext context =
                transferContexts
                        .findByOperationId(sendId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Transfer context not found: " + sendOperationId));
        responsibilityGuard.requireResponsible(context.destinationWarehouseId());
        WarehouseOperation completed = transfers.receiveFromSend(sendId);
        return toOperationResult(OperationKind.TRANSFER_RECEIVE, completed);
    }

    @Override
    public TransferDocumentReceiveResult receiveTransferDocument(
            ReceiveTransferDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseTransferReceiveService.ReceiveResult result =
                requireTransferReceive()
                        .receive(
                                new WarehouseTransferReceiveService.ReceiveCommand(
                                        command.documentId(),
                                        command.expectedOperationalRevision(),
                                        command.destinationAllocations().stream()
                                                .map(
                                                        a ->
                                                                new WarehouseTransferReceiveService
                                                                        .DestinationAllocationInput(
                                                                        a.lineId(),
                                                                        a.destinationStorageCellId(),
                                                                        a.quantity()))
                                                .toList()));
        return new TransferDocumentReceiveResult(
                result.documentId(),
                result.documentStatus(),
                result.documentVersion(),
                result.settlementState(),
                result.decision(),
                result.operationalRevision(),
                result.receiveOperationIds(),
                result.continuationDocumentId());
    }

    @Override
    public TransferDocumentRejectResult rejectTransferDocument(
            RejectTransferDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseTransferRejectService.RejectResult result =
                requireTransferReject()
                        .reject(
                                new WarehouseTransferRejectService.RejectCommand(
                                        command.documentId(),
                                        command.expectedOperationalRevision(),
                                        command.rejectionReason()));
        return new TransferDocumentRejectResult(
                result.documentId(),
                result.documentStatus(),
                result.settlementState(),
                result.decision(),
                result.operationalRevision(),
                result.rejectionReason());
    }

    @Override
    public TransferDocumentReturnResult returnTransferMaterials(
            ReturnTransferMaterialsCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseTransferReturnService.ReturnResult result =
                requireTransferReturn()
                        .returnMaterials(
                                new WarehouseTransferReturnService.ReturnCommand(
                                        command.documentId(),
                                        command.expectedOperationalRevision(),
                                        command.returnAllocations().stream()
                                                .map(
                                                        a ->
                                                                new WarehouseTransferReturnService
                                                                        .ReturnTargetInput(
                                                                        a.lineId(),
                                                                        a.returnStorageCellId(),
                                                                        a.quantity()))
                                                .toList()));
        return new TransferDocumentReturnResult(
                result.documentId(),
                result.documentStatus(),
                result.settlementState(),
                result.decision(),
                result.operationalRevision(),
                result.returnOperationIds());
    }

    @Override
    public TransferDocumentView createTransferDocument(CreateTransferDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseTransferDocumentService.CreatedTransferDocument created =
                transferDocuments.create(
                        new WarehouseTransferDocumentService.CreateCommand(
                                command.sourceWarehouseId(),
                                command.destinationWarehouseId(),
                                mapLineInputs(command.lines())));
        return toTransferDocumentView(created.metadata(), created.payload());
    }

    @Override
    public TransferDocumentView updateTransferDocument(UpdateTransferDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseTransferDocumentService.LoadedTransferDocument updated =
                transferDocuments.update(
                        new WarehouseTransferDocumentService.UpdateCommand(
                                command.documentId(),
                                command.expectedPayloadRevision(),
                                command.sourceWarehouseId(),
                                command.destinationWarehouseId(),
                                mapLineInputs(command.lines())));
        return toTransferDocumentView(updated.metadata(), updated.payload());
    }

    @Override
    public void deleteTransferDocument(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        transferDocuments.delete(documentId);
    }

    @Override
    public TransferDocumentSendResult sendTransferDocument(SendTransferDocumentCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseTransferSendService.SendResult result =
                requireTransferSend()
                        .send(
                                new WarehouseTransferSendService.SendCommand(
                                        command.documentId(),
                                        command.expectedDocumentVersion(),
                                        command.expectedPayloadRevision(),
                                        command.sourceAllocations().stream()
                                                .map(
                                                        a ->
                                                                new WarehouseTransferSendService
                                                                        .SourceAllocationInput(
                                                                        a.lineId(),
                                                                        a.sourceStorageCellId(),
                                                                        a.quantity()))
                                                .toList()));
        return new TransferDocumentSendResult(
                result.documentId(),
                result.documentStatus(),
                result.documentVersion(),
                result.payloadRevision(),
                result.sendOperationIds(),
                result.continuationDocumentId());
    }

    @Override
    public TransferDocumentView getTransferDocument(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        WarehouseTransferDocumentService.LoadedTransferDocument loaded =
                transferDocuments
                        .findByDocumentId(documentId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Transfer document not found: " + documentId));
        return toTransferDocumentView(loaded.metadata(), loaded.payload());
    }

    @Override
    public List<MaterialSourceRoutingResult> routeMaterials(
            UUID destinationWarehouseId, List<MaterialDemand> demands) {
        // Capability planning query: no RBAC / responsibility filter (ADR-037 / Stage 3.5.4).
        // Caller use-case owns user-facing authorization. Does not mutate warehouse facts.
        return sourceRouting.routeMaterials(destinationWarehouseId, demands);
    }

    @Override
    public List<WarehouseTaskView> listMyWarehouseTasks(UUID warehouseId) {
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        return requireOperationalInbox().listMyWarehouseTasks(warehouseId);
    }

    @Override
    public WarehouseTaskView takeTransferTaskInWork(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        return requireOperationalInbox().takeTransferTaskInWork(documentId);
    }

    private WarehouseOperationalInboxService requireOperationalInbox() {
        if (operationalInbox == null) {
            throw new IllegalStateException("Warehouse operational inbox is not configured");
        }
        return operationalInbox;
    }

    private WarehouseTransferSendService requireTransferSend() {
        if (transferSend == null) {
            throw new IllegalStateException("Warehouse transfer document send is not configured");
        }
        return transferSend;
    }

    private WarehouseTransferReceiveService requireTransferReceive() {
        if (transferReceive == null) {
            throw new IllegalStateException(
                    "Warehouse transfer document receive is not configured");
        }
        return transferReceive;
    }

    private WarehouseTransferRejectService requireTransferReject() {
        if (transferReject == null) {
            throw new IllegalStateException(
                    "Warehouse transfer document reject is not configured");
        }
        return transferReject;
    }

    private WarehouseTransferReturnService requireTransferReturn() {
        if (transferReturn == null) {
            throw new IllegalStateException(
                    "Warehouse transfer document return is not configured");
        }
        return transferReturn;
    }

    private static List<WarehouseTransferDocumentService.LineInput> mapLineInputs(
            List<TransferDocumentLineInput> lines) {
        return lines.stream()
                .map(
                        line ->
                                new WarehouseTransferDocumentService.LineInput(
                                        line.lineId(),
                                        line.materialReferenceId(),
                                        line.quantity(),
                                        line.lineOrder()))
                .toList();
    }

    private TransferDocumentView toTransferDocumentView(
            com.tmp.document.api.DocumentMetadata metadata,
            com.tmp.warehouse.domain.WarehouseTransferDocument payload) {
        List<TransferDocumentLineView> lineViews =
                payload.orderedLines().stream()
                        .map(
                                line ->
                                        new TransferDocumentLineView(
                                                line.id().value(),
                                                line.materialReferenceId().value(),
                                                line.quantity().value(),
                                                line.lineOrder()))
                        .toList();
        TransferDocumentSettlement settlement =
                settlements == null
                        ? null
                        : settlements.findByDocumentId(payload.documentId()).orElse(null);
        return new TransferDocumentView(
                metadata.id(),
                metadata.documentNumber(),
                metadata.title(),
                metadata.status().name(),
                payload.sourceWarehouseId().value(),
                payload.destinationWarehouseId().value(),
                payload.payloadSchemaVersion(),
                payload.payloadRevision(),
                lineViews,
                payload.continuationOfDocumentId().orElse(null),
                payload.continuationReason().map(Enum::name).orElse(null),
                settlement == null ? null : settlement.settlementState().name(),
                settlement == null ? null : settlement.operationalRevision(),
                settlement == null
                        ? null
                        : settlement.decision().map(Enum::name).orElse(null),
                settlement == null ? null : settlement.rejectionReason().orElse(null));
    }

    private AvailabilityResult availabilityForMaterial(
            MaterialReference material, WarehouseId warehouseScope, BigDecimal quantity) {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantity, "quantity");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Requested quantity must be positive: " + quantity);
        }
        BigDecimal available =
                stockPositions.findByMaterial(material).stream()
                        .filter(position -> position.stockState() == StockState.AVAILABLE)
                        .filter(
                                position ->
                                        warehouseScope == null
                                                || position.warehouseId().equals(warehouseScope))
                        .map(position -> position.quantity().value())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        AvailabilityStatus status =
                available.compareTo(quantity) >= 0
                        ? AvailabilityStatus.AVAILABLE
                        : AvailabilityStatus.INSUFFICIENT;
        return new AvailabilityResult(status, material.article(), quantity, available);
    }

    private MaterialReference requireMaterialByIdentity(MaterialIdentityRequest identity) {
        return materials
                .findByNaturalKey(
                        identity.article(),
                        identity.color(),
                        identity.size(),
                        identity.unitOfMeasure())
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Material reference not found for identity: "
                                                + identity.article()));
    }

    @Override
    public ReservationLinkView createReservationLink(CreateReservationLinkCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_RESERVATION);
        MaterialReference material = requireMaterial(command.materialReferenceId());
        MaterialReservationLink link =
                reservationLinks.createLink(
                        material,
                        toTarget(command.targetType(), command.targetReference()),
                        StockQuantity.of(command.quantity()));
        return toReservationLinkView(link);
    }

    @Override
    public List<ReservationLinkView> listReservationLinks(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_RESERVATION);
        MaterialReference material =
                materials
                        .findByNaturalKey(materialCode, "", "", "")
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Material reference not found: " + materialCode));
        return reservationLinks.findByMaterial(material).stream()
                .map(DefaultWarehouseApi::toReservationLinkView)
                .toList();
    }

    @Override
    public OperationResult executeWarehouseOperation(ExecuteOperationCommand command) {
        Objects.requireNonNull(command, "command");
        requireOperationPermission(command.kind());
        requireResponsibilityForExecute(command);
        WarehouseOperation completed =
                switch (command.kind()) {
                    case RECEIPT ->
                            receipts.receive(
                                    new ReceiptRequest(
                                            requireText(command.materialCode(), "article"),
                                            requireText(command.materialName(), "name"),
                                            normalize(command.color()),
                                            normalize(command.size()),
                                            normalize(command.unitOfMeasure()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId())));
                    case MOVE ->
                            moves.move(
                                    new MoveRequest(
                                            requireMaterial(command.materialReferenceId()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId()),
                                            WarehouseId.of(requireDestinationWarehouse(command)),
                                            StorageCellId.of(requireDestinationCell(command))));
                    case TRANSFER_SEND ->
                            transfers.send(
                                    new TransferSendRequest(
                                            requireMaterial(command.materialReferenceId()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId()),
                                            WarehouseId.of(requireDestinationWarehouse(command))));
                    case TRANSFER_RECEIVE ->
                            transfers.receive(
                                    new TransferReceiveRequest(
                                            requireMaterial(command.materialReferenceId()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId()),
                                            WarehouseId.of(requireDestinationWarehouse(command)),
                                            StorageCellId.of(requireDestinationCell(command))));
                    case CONSUMPTION ->
                            consumptions.consume(
                                    new ConsumptionRequest(
                                            requireMaterial(command.materialReferenceId()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId())));
                    case ADJUSTMENT ->
                            adjustments.adjust(
                                    new AdjustmentRequest(
                                            requireMaterial(command.materialReferenceId()),
                                            command.quantity(),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId())));
                    case TRANSFER_RETURN ->
                            throw new InvalidWarehouseStateException(
                                    "TRANSFER_RETURN must be executed via Transfer Document settlement return command");
                };
        return toOperationResult(command.kind(), completed);
    }

    private void requireResponsibilityForExecute(ExecuteOperationCommand command) {
        switch (command.kind()) {
            case RECEIPT, CONSUMPTION, ADJUSTMENT, MOVE, TRANSFER_SEND ->
                    responsibilityGuard.requireResponsible(WarehouseId.of(command.warehouseId()));
            case TRANSFER_RECEIVE ->
                    responsibilityGuard.requireResponsible(
                            WarehouseId.of(requireDestinationWarehouse(command)));
            case TRANSFER_RETURN ->
                    throw new InvalidWarehouseStateException(
                            "TRANSFER_RETURN must be executed via Transfer Document settlement return command");
        }
    }

    private void requireWarehouseExists(WarehouseId warehouseId) {
        boolean warehouseExists =
                warehouses.findAll().stream().anyMatch(w -> w.id().equals(warehouseId));
        if (!warehouseExists) {
            throw new IllegalArgumentException("Warehouse not found: " + warehouseId.value());
        }
    }

    private static String logicalTransferStatus(
            WarehouseOperation operation, TransferOperationContext context) {
        if (operation.type() == WarehouseOperationType.TRANSFER_RECEIVE) {
            return operation.status() == WarehouseOperationStatus.COMPLETED
                    ? "RECEIVED"
                    : operation.status().name();
        }
        if (operation.status() == WarehouseOperationStatus.DRAFT) {
            return "DRAFT";
        }
        if (context != null && context.isReceived()) {
            return "RECEIVED";
        }
        if (operation.status() == WarehouseOperationStatus.COMPLETED) {
            return "SENT";
        }
        return operation.status().name();
    }

    private MaterialReference requireMaterial(UUID materialReferenceId) {
        if (materialReferenceId == null) {
            throw new IllegalArgumentException("materialReferenceId is required");
        }
        return materials
                .findById(MaterialReferenceId.of(materialReferenceId))
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Material reference not found: " + materialReferenceId));
    }

    private void requireOperationPermission(OperationKind kind) {
        switch (kind) {
            case RECEIPT -> authorization.requirePermission(WarehousePermissions.WAREHOUSE_RECEIPT);
            case MOVE -> authorization.requirePermission(WarehousePermissions.WAREHOUSE_MOVE);
            case TRANSFER_SEND, TRANSFER_RECEIVE, TRANSFER_RETURN ->
                    authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
            case CONSUMPTION ->
                    authorization.requirePermission(WarehousePermissions.WAREHOUSE_CONSUMPTION);
            case ADJUSTMENT ->
                    authorization.requirePermission(WarehousePermissions.WAREHOUSE_ADJUSTMENT);
        }
    }

    private static final List<PermissionId> OPERATION_PERMISSIONS =
            List.of(
                    WarehousePermissions.WAREHOUSE_RECEIPT,
                    WarehousePermissions.WAREHOUSE_MOVE,
                    WarehousePermissions.WAREHOUSE_TRANSFER,
                    WarehousePermissions.WAREHOUSE_RESERVATION,
                    WarehousePermissions.WAREHOUSE_CONSUMPTION,
                    WarehousePermissions.WAREHOUSE_ADJUSTMENT,
                    WarehousePermissions.WAREHOUSE_INVENTORY);

    private boolean hasAnyOperationPermission() {
        return OPERATION_PERMISSIONS.stream().anyMatch(authorization::hasPermission);
    }

    private void requireCatalogueListAccess(PermissionId structureViewPermission) {
        if (authorization.hasPermission(WarehousePermissions.WAREHOUSE_VIEW)
                || authorization.hasPermission(structureViewPermission)
                || hasAnyOperationPermission()) {
            return;
        }
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
    }

    private void requireMaterialDisplayAccess() {
        if (authorization.hasPermission(WarehousePermissions.WAREHOUSE_VIEW)
                || hasAnyOperationPermission()) {
            return;
        }
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
    }

    private WarehouseView toWarehouseView(Warehouse warehouse) {
        return new WarehouseView(
                warehouse.id().value(), warehouse.code(), warehouse.name(), warehouse.active());
    }

    private StorageCellView toStorageCellView(StorageCell cell) {
        return new StorageCellView(
                cell.id().value(),
                cell.warehouseId().value(),
                cell.code(),
                cell.active());
    }

    private MaterialReferenceView toMaterialReferenceView(MaterialReference material) {
        return new MaterialReferenceView(
                material.id().value(),
                material.article(),
                material.name(),
                material.color(),
                material.size(),
                material.unitOfMeasure());
    }

    private List<StockView> toPositiveQuantityStockViews(List<StockPosition> positions) {
        return positions.stream()
                .filter(position -> position.quantity().value().signum() > 0)
                .map(this::toStockView)
                .toList();
    }

    private StockView toStockView(StockPosition position) {
        MaterialReference material = position.material();
        return StockView.of(
                material.id().value(),
                material.article(),
                material.name(),
                material.color(),
                material.size(),
                material.unitOfMeasure(),
                resolveWarehouseLabel(position.warehouseId()),
                resolveStorageCellLabel(position.warehouseId(), position.storageCellId()),
                position.quantity().value(),
                StockStateView.valueOf(position.stockState().name()),
                position.warehouseId().value(),
                position.storageCellId().value());
    }

    private String resolveWarehouseLabel(WarehouseId warehouseId) {
        return warehouses.findAll().stream()
                .filter(warehouse -> warehouse.id().equals(warehouseId))
                .findFirst()
                .map(warehouse -> warehouse.code() + " — " + warehouse.name())
                .orElse(warehouseId.value().toString());
    }

    private String resolveStorageCellLabel(WarehouseId warehouseId, StorageCellId storageCellId) {
        return warehouses.findStorageCellsByWarehouse(warehouseId).stream()
                .filter(cell -> cell.id().equals(storageCellId))
                .findFirst()
                .map(StorageCell::code)
                .orElse(storageCellId.value().toString());
    }

    private static MaterialReferenceDisplayView toMaterialReferenceDisplayView(
            MaterialReferenceDisplay display) {
        return new MaterialReferenceDisplayView(
                display.article(),
                display.materialName(),
                display.color(),
                display.size(),
                display.unitOfMeasure());
    }

    private static MaterialReferenceDisplayView toMaterialReferenceDisplayView(
            MaterialReference material) {
        return new MaterialReferenceDisplayView(
                material.article(),
                material.name(),
                material.color(),
                material.size(),
                material.unitOfMeasure());
    }

    private static ReservationLinkView toReservationLinkView(MaterialReservationLink link) {
        return new ReservationLinkView(
                link.id().value(),
                link.material().id().value(),
                link.material().article(),
                ReservationTargetTypeView.valueOf(link.target().type().name()),
                link.target().reference(),
                link.quantity().value(),
                link.createdAt());
    }

    private static OperationResult toOperationResult(
            OperationKind kind, WarehouseOperation operation) {
        return new OperationResult(
                operation.id().value(),
                kind,
                operation.status().name(),
                operation.material().id().value(),
                operation.material().article(),
                operation.warehouseId().value(),
                operation.storageCellId().value(),
                operation.quantity().value());
    }

    private static ReservationTargetReference toTarget(
            ReservationTargetTypeView typeView, String reference) {
        ReservationTargetType type = ReservationTargetType.valueOf(typeView.name());
        return ReservationTargetReference.of(type, reference);
    }

    private static UUID requireDestinationWarehouse(ExecuteOperationCommand command) {
        UUID destination = command.destinationWarehouseId();
        if (destination == null) {
            throw new IllegalArgumentException(
                    "destinationWarehouseId is required for " + command.kind());
        }
        return destination;
    }

    private static UUID requireDestinationCell(ExecuteOperationCommand command) {
        UUID destination = command.destinationStorageCellId();
        if (destination == null) {
            throw new IllegalArgumentException(
                    "destinationStorageCellId is required for " + command.kind());
        }
        return destination;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
