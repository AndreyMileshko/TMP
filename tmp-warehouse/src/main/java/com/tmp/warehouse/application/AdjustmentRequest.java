package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Adjustment request: signed quantity correction for a confirmed discrepancy (Specification §11).
 *
 * <p>Positive {@code quantityDelta} increases stock; negative decreases. Zero delta is rejected.
 * Does not create Batch or Material Master data.
 */
public record AdjustmentRequest(
        MaterialReference material,
        BigDecimal quantityDelta,
        WarehouseId warehouseId,
        StorageCellId storageCellId) {

    public AdjustmentRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantityDelta, "quantityDelta");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        if (quantityDelta.signum() == 0) {
            throw new IllegalArgumentException("Adjustment quantityDelta must not be zero");
        }
    }
}
