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
    }

    private static String normalizeColor(String color) {
        return color == null ? "" : color.trim();
    }
}
