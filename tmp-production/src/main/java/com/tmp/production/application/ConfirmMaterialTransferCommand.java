package com.tmp.production.application;

import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Confirmation of a persisted Material Transfer Template.
 *
 * <p>Client may supply only template identity/version and physical cell allocations. Material
 * identity and requested quantity are taken from the saved template.
 */
public record ConfirmMaterialTransferCommand(
        MaterialTransferTemplateId templateId,
        long expectedVersion,
        List<CellAllocation> allocations) {

    public ConfirmMaterialTransferCommand {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(allocations, "allocations");
        allocations = List.copyOf(allocations);
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be >= 0");
        }
    }

    /**
     * One physical cell allocation for a template line. A single line may split across multiple
     * source cells; allocation quantities for that line must sum to persisted requestedQuantity.
     */
    public record CellAllocation(
            MaterialTransferTemplateLineId templateLineId,
            UUID sourceStorageCellId,
            UUID destinationStorageCellId,
            BigDecimal quantity) {

        public CellAllocation {
            Objects.requireNonNull(templateLineId, "templateLineId");
            Objects.requireNonNull(sourceStorageCellId, "sourceStorageCellId");
            Objects.requireNonNull(destinationStorageCellId, "destinationStorageCellId");
            Objects.requireNonNull(quantity, "quantity");
            if (quantity.signum() <= 0) {
                throw new IllegalArgumentException("allocation quantity must be > 0: " + quantity);
            }
        }
    }
}
