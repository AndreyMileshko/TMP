package com.tmp.production.domain;

import java.util.Objects;

/**
 * One Production Order Item contribution of product units covered by a Material Requirement.
 *
 * <p>{@code requestedProductQuantity} is the number of products (not material quantity) included in
 * this requirement for the given order item.
 *
 * <p>{@link ProductRequestKind} is frozen at prepare time. {@link ProductRequestKind#COUNTED} rows
 * participate in authoritative submitted product coverage ({@code countsTowardProductCoverage =
 * true}). {@link ProductRequestKind#REPEATED} and {@link ProductRequestKind#LEGACY_EXCLUDED} never
 * do. Legacy V49 FALSE rows map to {@link ProductRequestKind#LEGACY_EXCLUDED} — never to {@link
 * ProductRequestKind#REPEATED}.
 */
public final class MaterialRequirementSourceItem {

    private final SourceOrderId sourceOrderId;
    private final SourceOrderItemId sourceOrderItemId;
    private final long requestedProductQuantity;
    private final ProductRequestKind productRequestKind;

    private MaterialRequirementSourceItem(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity,
            ProductRequestKind productRequestKind) {
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        if (requestedProductQuantity <= 0L) {
            throw new IllegalArgumentException(
                    "requestedProductQuantity must be > 0: " + requestedProductQuantity);
        }
        this.requestedProductQuantity = requestedProductQuantity;
        this.productRequestKind = Objects.requireNonNull(productRequestKind, "productRequestKind");
    }

    /** Authoritative counted source item (counts toward submitted product coverage). */
    public static MaterialRequirementSourceItem of(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        return new MaterialRequirementSourceItem(
                sourceOrderId,
                sourceOrderItemId,
                requestedProductQuantity,
                ProductRequestKind.COUNTED);
    }

    /** Explicit repeated additional request (does not count toward product coverage). */
    public static MaterialRequirementSourceItem repeated(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity) {
        return new MaterialRequirementSourceItem(
                sourceOrderId,
                sourceOrderItemId,
                requestedProductQuantity,
                ProductRequestKind.REPEATED);
    }

    /** Persistence / historical reconstruction entry point. */
    public static MaterialRequirementSourceItem rehydrate(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity,
            ProductRequestKind productRequestKind) {
        return new MaterialRequirementSourceItem(
                sourceOrderId, sourceOrderItemId, requestedProductQuantity, productRequestKind);
    }

    /**
     * Compatibility reconstruction from the legacy coverage flag alone. {@code true} → {@link
     * ProductRequestKind#COUNTED}; {@code false} → {@link ProductRequestKind#LEGACY_EXCLUDED}
     * (never {@link ProductRequestKind#REPEATED}).
     */
    public static MaterialRequirementSourceItem rehydrate(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity,
            boolean countsTowardProductCoverage) {
        return rehydrate(
                sourceOrderId,
                sourceOrderItemId,
                requestedProductQuantity,
                countsTowardProductCoverage
                        ? ProductRequestKind.COUNTED
                        : ProductRequestKind.LEGACY_EXCLUDED);
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public SourceOrderItemId sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public long requestedProductQuantity() {
        return requestedProductQuantity;
    }

    public ProductRequestKind productRequestKind() {
        return productRequestKind;
    }

    public boolean countsTowardProductCoverage() {
        return productRequestKind.countsTowardProductCoverage();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MaterialRequirementSourceItem that)) {
            return false;
        }
        return requestedProductQuantity == that.requestedProductQuantity
                && productRequestKind == that.productRequestKind
                && sourceOrderId.equals(that.sourceOrderId)
                && sourceOrderItemId.equals(that.sourceOrderItemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                sourceOrderId, sourceOrderItemId, requestedProductQuantity, productRequestKind);
    }

    @Override
    public String toString() {
        return "MaterialRequirementSourceItem{"
                + sourceOrderId
                + "/"
                + sourceOrderItemId
                + " x"
                + requestedProductQuantity
                + ", kind="
                + productRequestKind
                + "}";
    }
}
