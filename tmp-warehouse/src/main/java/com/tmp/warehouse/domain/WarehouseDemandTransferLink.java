package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Link between a Demand line and a Warehouse Transfer Document line.
 *
 * <p>One Demand line may have 0..N transfer links. A transfer line may be linked at most once.
 */
public final class WarehouseDemandTransferLink {

    private final WarehouseDemandTransferLinkId id;
    private final WarehouseDemandLineId demandLineId;
    private final UUID transferDocumentId;
    private final WarehouseTransferLineId transferLineId;
    private final StockQuantity linkedQuantity;

    private WarehouseDemandTransferLink(
            WarehouseDemandTransferLinkId id,
            WarehouseDemandLineId demandLineId,
            UUID transferDocumentId,
            WarehouseTransferLineId transferLineId,
            StockQuantity linkedQuantity) {
        this.id = id;
        this.demandLineId = demandLineId;
        this.transferDocumentId = transferDocumentId;
        this.transferLineId = transferLineId;
        this.linkedQuantity = linkedQuantity;
    }

    public static WarehouseDemandTransferLink create(
            WarehouseDemandLineId demandLineId,
            UUID transferDocumentId,
            WarehouseTransferLineId transferLineId,
            StockQuantity linkedQuantity) {
        return of(
                WarehouseDemandTransferLinkId.generate(),
                demandLineId,
                transferDocumentId,
                transferLineId,
                linkedQuantity);
    }

    public static WarehouseDemandTransferLink of(
            WarehouseDemandTransferLinkId id,
            WarehouseDemandLineId demandLineId,
            UUID transferDocumentId,
            WarehouseTransferLineId transferLineId,
            StockQuantity linkedQuantity) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(demandLineId, "demandLineId");
        Objects.requireNonNull(transferDocumentId, "transferDocumentId");
        Objects.requireNonNull(transferLineId, "transferLineId");
        Objects.requireNonNull(linkedQuantity, "linkedQuantity");
        if (linkedQuantity.value().signum() <= 0) {
            throw new IllegalArgumentException(
                    "linkedQuantity must be > 0: " + linkedQuantity.value());
        }
        return new WarehouseDemandTransferLink(
                id, demandLineId, transferDocumentId, transferLineId, linkedQuantity);
    }

    public WarehouseDemandTransferLinkId id() {
        return id;
    }

    public WarehouseDemandLineId demandLineId() {
        return demandLineId;
    }

    public UUID transferDocumentId() {
        return transferDocumentId;
    }

    public WarehouseTransferLineId transferLineId() {
        return transferLineId;
    }

    public StockQuantity linkedQuantity() {
        return linkedQuantity;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseDemandTransferLink that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
