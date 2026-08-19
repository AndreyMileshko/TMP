package com.tmp.production.persistence;

import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Instant;
import java.util.Objects;

/**
 * Mutable persistence representation of {@code production.production_item_states}.
 *
 * <p>Not a domain model; mapped to {@link com.tmp.production.domain.ProductionItemState} by
 * {@link ProductionItemStateMapper}.
 */
public final class ProductionItemStateEntity {

    private ProductionItemId id;
    private SourceOrderId sourceOrderId;
    private SourceOrderItemId sourceOrderItemId;
    private SpecificationId specificationId;
    private ProductionStatus status;
    private ProductionQuantity orderedQuantity;
    private ProductionQuantity launchedQuantity;
    private ProductionQuantity activeProductionQuantity;
    private ProductionQuantity releasedQuantity;
    private Instant lastMaterialCheckAt;
    private Instant lastStatusChangedAt;
    private long version;
    private Instant createdAt;
    private Instant updatedAt;

    public ProductionItemId id() {
        return id;
    }

    public void setId(ProductionItemId id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public void setSourceOrderId(SourceOrderId sourceOrderId) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public void setSourceOrderItemId(SourceOrderItemId sourceOrderItemId) {
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
    }

    public SpecificationId specificationId() {
        return specificationId;
    }

    public void setSpecificationId(SpecificationId specificationId) {
        this.specificationId = Objects.requireNonNull(specificationId, "specificationId");
    }

    public ProductionStatus status() {
        return status;
    }

    public void setStatus(ProductionStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public ProductionQuantity orderedQuantity() {
        return orderedQuantity;
    }

    public void setOrderedQuantity(ProductionQuantity orderedQuantity) {
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "orderedQuantity");
    }

    public ProductionQuantity launchedQuantity() {
        return launchedQuantity;
    }

    public void setLaunchedQuantity(ProductionQuantity launchedQuantity) {
        this.launchedQuantity = Objects.requireNonNull(launchedQuantity, "launchedQuantity");
    }

    public ProductionQuantity activeProductionQuantity() {
        return activeProductionQuantity;
    }

    public void setActiveProductionQuantity(ProductionQuantity activeProductionQuantity) {
        this.activeProductionQuantity =
                Objects.requireNonNull(activeProductionQuantity, "activeProductionQuantity");
    }

    public ProductionQuantity releasedQuantity() {
        return releasedQuantity;
    }

    public void setReleasedQuantity(ProductionQuantity releasedQuantity) {
        this.releasedQuantity = Objects.requireNonNull(releasedQuantity, "releasedQuantity");
    }

    public Instant lastMaterialCheckAt() {
        return lastMaterialCheckAt;
    }

    public void setLastMaterialCheckAt(Instant lastMaterialCheckAt) {
        this.lastMaterialCheckAt = lastMaterialCheckAt;
    }

    public Instant lastStatusChangedAt() {
        return lastStatusChangedAt;
    }

    public void setLastStatusChangedAt(Instant lastStatusChangedAt) {
        this.lastStatusChangedAt =
                Objects.requireNonNull(lastStatusChangedAt, "lastStatusChangedAt");
    }

    public long version() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }
}
