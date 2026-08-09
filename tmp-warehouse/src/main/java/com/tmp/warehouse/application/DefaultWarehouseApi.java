package com.tmp.warehouse.application;

import com.tmp.warehouse.api.WarehouseApi;
import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.ReservationTargetType;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Default Public API adapter for Warehouse (Specification §17).
 *
 * <p>Maps public DTOs to existing application services without changing Warehouse business rules.
 * Does not expose domain aggregates, does not allow direct Stock Position or Movement mutation.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected application collaborators.")
public final class DefaultWarehouseApi implements WarehouseApi {

    private final StockPositionRepository stockPositions;
    private final WarehouseReservationLinkService reservationLinks;
    private final WarehouseReceiptService receipts;
    private final WarehouseMoveService moves;
    private final WarehouseTransferService transfers;
    private final WarehouseConsumptionService consumptions;
    private final WarehouseAdjustmentService adjustments;

    public DefaultWarehouseApi(
            StockPositionRepository stockPositions,
            WarehouseReservationLinkService reservationLinks,
            WarehouseReceiptService receipts,
            WarehouseMoveService moves,
            WarehouseTransferService transfers,
            WarehouseConsumptionService consumptions,
            WarehouseAdjustmentService adjustments) {
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
        this.reservationLinks = Objects.requireNonNull(reservationLinks, "reservationLinks");
        this.receipts = Objects.requireNonNull(receipts, "receipts");
        this.moves = Objects.requireNonNull(moves, "moves");
        this.transfers = Objects.requireNonNull(transfers, "transfers");
        this.consumptions = Objects.requireNonNull(consumptions, "consumptions");
        this.adjustments = Objects.requireNonNull(adjustments, "adjustments");
    }

    @Override
    public List<StockView> getStock(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        MaterialReference material = MaterialReference.of(materialCode);
        return stockPositions.findByMaterial(material).stream().map(this::toStockView).toList();
    }

    @Override
    public List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        WarehouseId wh = WarehouseId.of(warehouseId);
        StorageCellId cell = StorageCellId.of(storageCellId);
        return getStock(materialCode).stream()
                .filter(
                        view ->
                                view.warehouseId().equals(wh.value())
                                        && view.storageCellId().equals(cell.value()))
                .toList();
    }

    @Override
    public AvailabilityResult checkAvailability(String materialCode, BigDecimal quantity) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(quantity, "quantity");
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
        MaterialReservationLink link =
                reservationLinks.createLink(
                        MaterialReference.of(command.materialCode()),
                        toTarget(command.targetType(), command.targetReference()),
                        StockQuantity.of(command.quantity()));
        return toReservationLinkView(link);
    }

    @Override
    public OperationResult executeWarehouseOperation(ExecuteOperationCommand command) {
        Objects.requireNonNull(command, "command");
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
