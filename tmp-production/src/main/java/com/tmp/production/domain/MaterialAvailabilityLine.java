package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** One material requirement line in an availability check result. */
public record MaterialAvailabilityLine(
        String materialCode,
        String materialName,
        String color,
        String unitOfMeasure,
        UUID materialReferenceId,
        BigDecimal requiredQuantity,
        BigDecimal mainWarehouseAvailable,
        BigDecimal productionWarehouseAvailable,
        BigDecimal totalAvailable,
        BigDecimal deficit,
        MaterialAvailabilityLineStatus status,
        MaterialPlanningSource planningSource) {

    public MaterialAvailabilityLine {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        Objects.requireNonNull(mainWarehouseAvailable, "mainWarehouseAvailable");
        Objects.requireNonNull(productionWarehouseAvailable, "productionWarehouseAvailable");
        Objects.requireNonNull(totalAvailable, "totalAvailable");
        Objects.requireNonNull(deficit, "deficit");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(planningSource, "planningSource");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        color = normalizeColor(color);

        if (status == MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED
                || status == MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS) {
            requireZero(mainWarehouseAvailable, "mainWarehouseAvailable");
            requireZero(productionWarehouseAvailable, "productionWarehouseAvailable");
            requireZero(totalAvailable, "totalAvailable");
            if (deficit.compareTo(requiredQuantity) != 0) {
                throw new IllegalArgumentException(
                        "Unresolved/ambiguous deficit must equal requiredQuantity");
            }
            if (materialReferenceId != null) {
                throw new IllegalArgumentException(
                        "Unresolved/ambiguous line must not carry materialReferenceId");
            }
        } else {
            BigDecimal sum = mainWarehouseAvailable.add(productionWarehouseAvailable);
            if (totalAvailable.compareTo(sum) != 0) {
                throw new IllegalArgumentException(
                        "totalAvailable must equal mainWarehouseAvailable +"
                                + " productionWarehouseAvailable");
            }
            if (materialReferenceId == null) {
                throw new IllegalArgumentException(
                        "Resolved availability line requires materialReferenceId");
            }
        }
    }

    private static String normalizeColor(String color) {
        return color == null ? "" : color.trim();
    }

    private static void requireZero(BigDecimal value, String name) {
        if (value.signum() != 0) {
            throw new IllegalArgumentException(name + " must be 0 for unresolved/ambiguous line");
        }
    }
}
