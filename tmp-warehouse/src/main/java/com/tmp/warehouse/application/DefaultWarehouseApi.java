package com.tmp.warehouse.application;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import com.tmp.warehouse.api.MaterialReferenceDisplay;
import com.tmp.warehouse.api.MaterialReferenceDisplayPort;
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
import com.tmp.warehouse.domain.UnitOfMeasure;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.security.WarehousePermissions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Default Public API adapter for Warehouse (Specification §17 / §18).
 *
 * <p>Maps public DTOs to existing application services without changing Warehouse business rules.
 * Enforces Warehouse permissions via the public {@link AuthorizationService} before any operation.
 * Does not expose domain aggregates, does not allow direct Stock Position or Movement mutation.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected application collaborators.")
public final class DefaultWarehouseApi implements WarehouseApi {

    private final AuthorizationService authorization;
    private final WarehouseCatalogRepository warehouses;
    private final StockPositionRepository stockPositions;
    private final MaterialReferenceRepository materials;
    private final MaterialReferenceDisplayPort materialDisplay;
    private final WarehouseReservationLinkService reservationLinks;
    private final WarehouseReceiptService receipts;
    private final WarehouseMoveService moves;
    private final WarehouseTransferService transfers;
    private final WarehouseConsumptionService consumptions;
    private final WarehouseAdjustmentService adjustments;
    private final WarehouseOperationRepository operations;
    private final TransferOperationContextRepository transferContexts;

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials,
            MaterialReferenceDisplayPort materialDisplay,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments,
            WarehouseOperationRepository operations,
            TransferOperationContextRepository transferContexts) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
        this.materials = Objects.requireNonNull(materials, "materials");
        this.materialDisplay = Objects.requireNonNull(materialDisplay, "materialDisplay");
        this.reservationLinks = Objects.requireNonNull(reservationLinks, "reservationLinks");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.consumptions = Objects.requireNonNull(consumptions, "consumptions");
        this.adjustments = Objects.requireNonNull(adjustments, "adjustments");
        this.operations = Objects.requireNonNull(operations, "operations");
        this.transferContexts = Objects.requireNonNull(transferContexts, "transferContexts");
    }

    @Override
    public List<WarehouseView> listWarehouses() {
        requireCatalogueListAccess(WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW);
        return warehouses.findAll().stream().map(this::toWarehouseView).toList();
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
        boolean warehouseExists =
                warehouses.findAll().stream().anyMatch(w -> w.id().equals(warehouseId));
        if (!warehouseExists) {
            throw new IllegalArgumentException("Warehouse not found: " + command.warehouseId());
        }
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
        var context = transferContexts.findByOperationId(operation.id());
        return new TransferStatusView(
                operation.id().value(),
                OperationKind.valueOf(operation.type().name()),
                operation.status().name(),
                operation.material().id().value(),
                operation.quantity().value(),
                operation.warehouseId().value(),
                operation.storageCellId().value(),
                context.map(ctx -> ctx.destinationWarehouseId().value()).orElse(null),
                context.map(ctx -> ctx.destinationStorageCellId().value()).orElse(null));
    }

    @Override
    public OperationResult receive(ReceiptCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_RECEIPT);
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
        WarehouseOperation completed =
                transfers.sendDraft(
                        com.tmp.warehouse.domain.WarehouseOperationId.of(
                                transferDraftOperationId));
        return toOperationResult(OperationKind.TRANSFER_SEND, completed);
    }

    @Override
    public OperationResult receiveTransfer(UUID sendOperationId) {
        Objects.requireNonNull(sendOperationId, "sendOperationId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_TRANSFER);
        WarehouseOperation completed =
                transfers.receiveFromSend(
                        com.tmp.warehouse.domain.WarehouseOperationId.of(sendOperationId));
        return toOperationResult(OperationKind.TRANSFER_RECEIVE, completed);
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
                };
        return toOperationResult(command.kind(), completed);
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
            case TRANSFER_SEND, TRANSFER_RECEIVE ->
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
