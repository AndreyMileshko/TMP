package com.tmp.warehouse.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Raised when Warehouse already accepted a Demand for {@code sourceMaterialRequirementId} but the
 * repeat accept carries a different immutable payload. Never silently returns the old Demand.
 */
public final class WarehouseDemandPayloadConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID sourceMaterialRequirementId;
    private final UUID existingDemandId;

    public WarehouseDemandPayloadConflictException(
            UUID sourceMaterialRequirementId, UUID existingDemandId, String detail) {
        super(
                "Warehouse Demand payload conflict for sourceMaterialRequirementId="
                        + Objects.requireNonNull(
                                sourceMaterialRequirementId, "sourceMaterialRequirementId")
                        + ", existingDemandId="
                        + Objects.requireNonNull(existingDemandId, "existingDemandId")
                        + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.sourceMaterialRequirementId = sourceMaterialRequirementId;
        this.existingDemandId = existingDemandId;
    }

    public UUID sourceMaterialRequirementId() {
        return sourceMaterialRequirementId;
    }

    public UUID existingDemandId() {
        return existingDemandId;
    }
}
