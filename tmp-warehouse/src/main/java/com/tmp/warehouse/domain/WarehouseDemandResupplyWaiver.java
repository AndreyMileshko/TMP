package com.tmp.warehouse.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Durable Warehouse-owned resupply-waiver ledger event (ADR-038 amendment).
 *
 * <p>One event per {@code (transferDocumentId, demandLineId)}. Quantity is attributable only via
 * Demand-Transfer links of the rejected Transfer.
 */
public final class WarehouseDemandResupplyWaiver {

    private final WarehouseDemandResupplyWaiverId id;
    private final WarehouseDemandLineId demandLineId;
    private final UUID transferDocumentId;
    private final StockQuantity waivedQuantity;
    private final Instant waivedAt;
    private final UUID waivedBy;

    private WarehouseDemandResupplyWaiver(
            WarehouseDemandResupplyWaiverId id,
            WarehouseDemandLineId demandLineId,
            UUID transferDocumentId,
            StockQuantity waivedQuantity,
            Instant waivedAt,
            UUID waivedBy) {
        this.id = id;
        this.demandLineId = demandLineId;
        this.transferDocumentId = transferDocumentId;
        this.waivedQuantity = waivedQuantity;
        this.waivedAt = waivedAt;
        this.waivedBy = waivedBy;
    }

    public static WarehouseDemandResupplyWaiver create(
            WarehouseDemandLineId demandLineId,
            UUID transferDocumentId,
            StockQuantity waivedQuantity,
            Instant waivedAt,
            UUID waivedBy) {
        return of(
                WarehouseDemandResupplyWaiverId.generate(),
                demandLineId,
                transferDocumentId,
                waivedQuantity,
                waivedAt,
                waivedBy);
    }

    public static WarehouseDemandResupplyWaiver of(
            WarehouseDemandResupplyWaiverId id,
            WarehouseDemandLineId demandLineId,
            UUID transferDocumentId,
            StockQuantity waivedQuantity,
            Instant waivedAt,
            UUID waivedBy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(demandLineId, "demandLineId");
        Objects.requireNonNull(transferDocumentId, "transferDocumentId");
        Objects.requireNonNull(waivedQuantity, "waivedQuantity");
        Objects.requireNonNull(waivedAt, "waivedAt");
        Objects.requireNonNull(waivedBy, "waivedBy");
        if (waivedQuantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "waivedQuantity must be > 0: " + waivedQuantity.value());
        }
        return new WarehouseDemandResupplyWaiver(
                id, demandLineId, transferDocumentId, waivedQuantity, waivedAt, waivedBy);
    }

    public WarehouseDemandResupplyWaiverId id() {
        return id;
    }

    public WarehouseDemandLineId demandLineId() {
        return demandLineId;
    }

    public UUID transferDocumentId() {
        return transferDocumentId;
    }

    public StockQuantity waivedQuantity() {
        return waivedQuantity;
    }

    public Instant waivedAt() {
        return waivedAt;
    }

    public UUID waivedBy() {
        return waivedBy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemandResupplyWaiver that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}