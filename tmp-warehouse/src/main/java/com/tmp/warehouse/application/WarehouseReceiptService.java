package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.Optional;

/**
 * Receipt operation — increases stock via {@link WarehouseOperationEngine} (Specification §12).
 *
 * <p>Flow: Receipt Request → resolve MaterialReference (find or create) → Warehouse Operation
 * (RECEIPT) → Warehouse Movement → Stock Position. Only Receipt may create MaterialReference rows.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores injected engine and repositories.")
public final class WarehouseReceiptService {

    private final WarehouseOperationEngine operationEngine;
    private final StockPositionRepository stockPositions;
    private final MaterialReferenceRepository materials;

    public WarehouseReceiptService(
            WarehouseOperationEngine operationEngine,
            StockPositionRepository stockPositions,
            MaterialReferenceRepository materials) {
        this.operationEngine = Objects.requireNonNull(operationEngine, "operationEngine");
        this.stockPositions = Objects.requireNonNull(stockPositions, "stockPositions");
        this.materials = Objects.requireNonNull(materials, "materials");
    }

    /**
     * Executes a receipt: resolves material reference, creates a RECEIPT operation, records a
     * positive movement delta, and creates or increases the AVAILABLE stock position.
     *
     * @return completed warehouse operation
     */
    public WarehouseOperation receive(ReceiptRequest request) {
        Objects.requireNonNull(request, "request");
        MaterialReference material = resolveMaterial(request);
        StockQuantity targetQuantity = resolveTargetQuantity(request, material);
        WarehouseOperation draft =
                operationEngine.create(
                        WarehouseOperationType.RECEIPT,
                        material,
                        request.warehouseId(),
                        request.storageCellId(),
                        StockState.AVAILABLE,
                        targetQuantity);
        return operationEngine.execute(draft.id());
    }

    private MaterialReference resolveMaterial(ReceiptRequest request) {
        return materials
                .findByNaturalKey(
                        request.article(), request.color(), request.size(), request.unitOfMeasure())
                .orElseGet(
                        () ->
                                materials.create(
                                        MaterialReference.create(
                                                request.article(),
                                                request.name(),
                                                request.color(),
                                                request.size(),
                                                request.unitOfMeasure())));
    }

    private StockQuantity resolveTargetQuantity(ReceiptRequest request, MaterialReference material) {
        Optional<StockPosition> existing =
                stockPositions.findByNaturalKey(
                        request.warehouseId(),
                        request.storageCellId(),
                        material,
                        StockState.AVAILABLE);
        if (existing.isEmpty()) {
            return request.quantity();
        }
        return StockQuantity.of(
                existing.get().quantity().value().add(request.quantity().value()));
    }
}
