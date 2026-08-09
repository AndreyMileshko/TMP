package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Technical identity of an informational material reservation link.
 */
public final class MaterialReservationLinkId {

    private final UUID value;

    private MaterialReservationLinkId(UUID value) {
        this.value = value;
    }

    public static MaterialReservationLinkId of(UUID value) {
        Objects.requireNonNull(value, "value");
        return new MaterialReservationLinkId(value);
    }

    public static MaterialReservationLinkId generate() {
        return new MaterialReservationLinkId(UUID.randomUUID());
    }

    public UUID value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialReservationLinkId that)) {
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
