package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Normalized material identity key for aggregating specification requirements across order items.
 *
 * <p>{@code lengthMm} from Order Management is intentionally excluded — it is a part length, not a
 * Warehouse material identity field.
 */
public record SpecificationMaterialIdentity(
        String materialCode, String color, String unitOfMeasure) {

    public SpecificationMaterialIdentity {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        color = normalizeColor(color);
        unitOfMeasure = unitOfMeasure.trim();
    }

    public static SpecificationMaterialIdentity of(
            String materialCode, String color, String unitOfMeasure) {
        return new SpecificationMaterialIdentity(materialCode, color, unitOfMeasure);
    }

    public static String normalizeColor(String color) {
        return color == null ? "" : color.trim();
    }
}
