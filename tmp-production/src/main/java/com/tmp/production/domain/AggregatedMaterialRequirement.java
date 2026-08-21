package com.tmp.production.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** Aggregated planned material requirement for one specification material identity. */
public record AggregatedMaterialRequirement(
        SpecificationMaterialIdentity identity,
        String materialName,
        BigDecimal requiredQuantity) {

    public AggregatedMaterialRequirement {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        if (requiredQuantity.signum() < 0) {
            throw new IllegalArgumentException("requiredQuantity must be >= 0: " + requiredQuantity);
        }
    }
}
