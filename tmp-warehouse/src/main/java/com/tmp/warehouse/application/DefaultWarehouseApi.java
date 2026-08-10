package com.tmp.warehouse.application;

import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.ReservationTargetType;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCell;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.Warehouse;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.domain.repository.WarehouseCatalogRepository;
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
    private final WarehouseReservationLinkService reservationLinks;
    private final WarehouseReceiptService receipts;
    private final WarehouseMoveService moves;
    private final WarehouseTransferService transfers;
    private final WarehouseConsumptionService consumptions;
    private final WarehouseAdjustmentService adjustments;

    public DefaultWarehouseApi(
            AuthorizationService authorization,
            WarehouseCatalogRepository warehouses,
            StockPositionRepository stockPositions,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments) {
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.warehouses = Objects.requireNonNull(warehouses, "warehouses");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
        this.reservationLinks = Objects.requireNonNull(reservationLinks, "reservationLinks");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.consumptions = Objects.requireNonNull(consumptions, "consumptions");
        this.adjustments = Objects.requireNonNull(adjustments, "adjustments");
    }

    @Override
    public List<WarehouseView> listWarehouses() {
        requireStockOrStructureView(WarehousePermissions.WAREHOUSE_STRUCTURE_VIEW);
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
        requireStockOrStructureView(
                WarehousePermissions.STORAGE_CELL_VIEW);
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
    public List<StockView> getStock(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        MaterialReference material = MaterialReference.of(materialCode);
        return stockPositions.findByMaterial(material).stream().map(this::toStockView).toList();
    }

    @Override
    public List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        WarehouseId wh = WarehouseId.of(warehouseId);
        StorageCellId cell = StorageCellId.of(storageCellId);
        MaterialReference material = MaterialReference.of(materialCode);
        return stockPositions.findByMaterial(material).stream()
                .map(this::toStockView)
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
        return stockPositions.findByWarehouse(WarehouseId.of(warehouseId)).stream()
                .map(this::toStockView)
                .toList();
    }

    @Override
    public AvailabilityResult checkAvailability(String materialCode, BigDecimal quantity) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(quantity, "quantity");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_VIEW);
        if (quantity.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Requested quantity must be positive: " + quantity);
        }
        MaterialReference material = MaterialReference.of(materialCode);
        BigDecimal available =
                stockPositions.findByMaterial(material).stream()
                        .filter(position -> position.stockState() == StockState.AVAILABLE)
                        .map(position -> position.quantity().value())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        AvailabilityStatus status =
                available.compareTo(quantity) >= 0
                        ? AvailabilityStatus.AVAILABLE
                        : AvailabilityStatus.INSUFFICIENT;
        return new AvailabilityResult(status, material.materialCode(), quantity, available);
    }

    @Override
    public ReservationLinkView createReservationLink(CreateReservationLinkCommand command) {
        Objects.requireNonNull(command, "command");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_RESERVATION);
        MaterialReservationLink link =
                reservationLinks.createLink(
                        MaterialReference.of(command.materialCode()),
                        toTarget(command.targetType(), command.targetReference()),
                        StockQuantity.of(command.quantity()));
        return toReservationLinkView(link);
    }

    @Override
    public List<ReservationLinkView> listReservationLinks(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        authorization.requirePermission(WarehousePermissions.WAREHOUSE_RESERVATION);
        return reservationLinks.findByMaterial(MaterialReference.of(materialCode)).stream()
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
                                            MaterialReference.of(command.materialCode()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId())));
                    case MOVE ->
                            moves.move(
                                    new MoveRequest(
                                            MaterialReference.of(command.materialCode()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId()),
                                            WarehouseId.of(
                                                    requireDestinationWarehouse(command)),
                                            StorageCellId.of(
                                                    requireDestinationCell(command))));
                    case TRANSFER_SEND ->
                            transfers.send(
                                    new TransferSendRequest(
                                            MaterialReference.of(command.materialCode()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId()),
                                            WarehouseId.of(
                                                    requireDestinationWarehouse(command))));
                    case TRANSFER_RECEIVE ->
                            transfers.receive(
                                    new TransferReceiveRequest(
                                            MaterialReference.of(command.materialCode()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId()),
                                            WarehouseId.of(
                                                    requireDestinationWarehouse(command)),
                                            StorageCellId.of(
                                                    requireDestinationCell(command))));
                    case CONSUMPTION ->
                            consumptions.consume(
                                    new ConsumptionRequest(
                                            MaterialReference.of(command.materialCode()),
                                            StockQuantity.of(command.quantity()),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId())));
                    case ADJUSTMENT ->
                            adjustments.adjust(
                                    new AdjustmentRequest(
                                            MaterialReference.of(command.materialCode()),
                                            command.quantity(),
                                            WarehouseId.of(command.warehouseId()),
                                            StorageCellId.of(command.storageCellId())));
                };
        return toOperationResult(command.kind(), completed);
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

    /**
     * Catalogue list reads remain available to stock operators ({@code warehouse.stock.view}) and to
     * structure administrators ({@code warehouse.warehouse.view} / {@code
     * warehouse.storage-cell.view}).
     */
    private void requireStockOrStructureView(PermissionId structureViewPermission) {
        if (authorization.hasPermission(WarehousePermissions.WAREHOUSE_VIEW)
                || authorization.hasPermission(structureViewPermission)) {
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

    private StockView toStockView(StockPosition position) {
        return new StockView(
                position.material().materialCode(),
                position.warehouseId().value(),
                position.storageCellId().value(),
                position.quantity().value(),
                StockStateView.valueOf(position.stockState().name()));
    }

    private static ReservationLinkView toReservationLinkView(MaterialReservationLink link) {
        return new ReservationLinkView(
                link.id().value(),
                link.material().materialCode(),
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
                operation.material().materialCode(),
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
}
