package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperation;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Adjustment request: signed quantity correction for a confirmed discrepancy (Specification §11).
 *
 * <p>Positive {@code quantityDelta} increases stock; negative decreases. Zero delta is rejected.
 * Comment (reason) is mandatory: trimmed, non-blank, at most {@link
 * WarehouseOperation#COMMENT_MAX_LENGTH} characters. Does not create Batch or Material Master data.
 */
public record AdjustmentRequest(
        MaterialReference material,
        BigDecimal quantityDelta,
        WarehouseId warehouseId,
        StorageCellId storageCellId,
        String comment) {

    public AdjustmentRequest {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(quantityDelta, "quantityDelta");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(comment, "comment");
        if (quantityDelta.signum() == 0) {
            throw new IllegalArgumentException("Adjustment quantityDelta must not be zero");
        }
        String trimmed = comment.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Adjustment comment must not be blank");
        }
        if (trimmed.length() > WarehouseOperation.COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Adjustment comment must be at most "
                            + WarehouseOperation.COMMENT_MAX_LENGTH
                            + " characters");
        }
        comment = trimmed;
    }
}
