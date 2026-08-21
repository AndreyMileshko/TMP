package com.tmp.production.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Opaque Production-owned reference to a Cutting Plan owned by Cutting Optimization.
 *
 * <p>Stores identity only — no revision, status, or Cutting Plan contents (ADR-034).
 */
public final class CuttingPlanId {

    private final UUID value;

    private CuttingPlanId(UUID value) {
        this.value = value;
    }

    public static CuttingPlanId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new CuttingPlanId(value);
    }

    public static CuttingPlanId generate() {
        return new CuttingPlanId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CuttingPlanId that)) {
            return false;
        }
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
