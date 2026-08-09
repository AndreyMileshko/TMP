package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Informational link of material quantity to an order or production demand (Specification §8).
 *
 * <p>Does not change {@link StockPosition}, does not create {@link WarehouseMovement}, and does not
 * introduce {@code StockState.RESERVED}. Release of links is out of scope for v1.0.
 */
public final class MaterialReservationLink {

    private final MaterialReservationLinkId id;
    private final MaterialReference material;
    private final ReservationTargetReference target;
    private final StockQuantity quantity;
    private final Instant createdAt;

    private MaterialReservationLink(
            MaterialReservationLinkId id,
            MaterialReference material,
            ReservationTargetReference target,
            StockQuantity quantity,
            Instant createdAt) {
        this.id = id;
        this.material = material;
        this.target = target;
        this.quantity = quantity;
        this.createdAt = createdAt;
    }

    /**
     * Creates a new informational reservation link.
     */
    public static MaterialReservationLink create(
            MaterialReservationLinkId id,
            MaterialReference material,
            ReservationTargetReference target,
            StockQuantity quantity,
            Instant createdAt) {
        return rehydrate(id, material, target, quantity, createdAt);
    }

    /**
     * Rehydrates a persisted reservation link.
     */
    public static MaterialReservationLink rehydrate(
            MaterialReservationLinkId id,
            MaterialReference material,
            ReservationTargetReference target,
            StockQuantity quantity,
            Instant createdAt) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Reservation link quantity must be positive: " + quantity.value());
        }
        return new MaterialReservationLink(id, material, target, quantity, createdAt);
    }

    public MaterialReservationLinkId id() {
        return id;
    }

    public MaterialReference material() {
        return material;
    }

    public ReservationTargetReference target() {
        return target;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialReservationLink that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "MaterialReservationLink{id="
                + id
                + ", material="
                + material
                + ", target="
                + target
                + ", quantity="
                + quantity
                + '}';
    }
}
