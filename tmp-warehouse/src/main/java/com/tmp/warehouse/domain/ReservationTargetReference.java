package com.tmp.warehouse.domain;

import java.util.Objects;

/**
 * Order or production demand reference for an informational reservation link (Specification §8).
 *
 * <p>Opaque to Warehouse: does not load Order Management or Production internals.
 */
public final class ReservationTargetReference {

    private final ReservationTargetType type;
    private final String reference;

    private ReservationTargetReference(ReservationTargetType type, String reference) {
        this.type = type;
        this.reference = reference;
    }

    public static ReservationTargetReference of(ReservationTargetType type, String reference) {
        Objects.requireNonNull(type, "type");
        return new ReservationTargetReference(type, requireNonBlank(reference, "reference"));
    }

    public static ReservationTargetReference order(String orderReference) {
        return of(ReservationTargetType.ORDER, orderReference);
    }

    public static ReservationTargetReference productionDemand(String demandReference) {
        return of(ReservationTargetType.PRODUCTION_DEMAND, demandReference);
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    public ReservationTargetType type() {
        return type;
    }

    public String reference() {
        return reference;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReservationTargetReference that)) {
            return false;
        }
        return type == that.type && reference.equals(that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, reference);
    }

    @Override
    public String toString() {
        return type + ":" + reference;
    }
}
