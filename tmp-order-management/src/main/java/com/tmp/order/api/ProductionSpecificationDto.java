package com.tmp.order.api;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Production-facing read-only specification view (OM Specification v1.10 / ADR-033).
 *
 * <p>Contains a stable {@link SpecificationId} and specification content without
 * {@link RevisionNumber}. External consumers (Production) use this DTO exclusively.
 */
public final class ProductionSpecificationDto {

    private final SpecificationId specificationId;
    private final OrderItemId orderItemId;
    private final BigDecimal orderedQuantity;
    private final List<SpecificationLineDto> lines;

    private ProductionSpecificationDto(
            SpecificationId specificationId,
            OrderItemId orderItemId,
            BigDecimal orderedQuantity,
            List<SpecificationLineDto> lines) {
        this.specificationId = specificationId;
        this.orderItemId = orderItemId;
        this.orderedQuantity = orderedQuantity;
        this.lines = List.copyOf(lines);
    }

    public static ProductionSpecificationDto of(
            SpecificationId specificationId,
            OrderItemId orderItemId,
            BigDecimal orderedQuantity,
            List<SpecificationLineDto> lines) {
        Objects.requireNonNull(specificationId, "specificationId");
        Objects.requireNonNull(orderItemId, "orderItemId");
        Objects.requireNonNull(orderedQuantity, "orderedQuantity");
        Objects.requireNonNull(lines, "lines");
        return new ProductionSpecificationDto(specificationId, orderItemId, orderedQuantity, lines);
    }

    public SpecificationId specificationId() {
        return specificationId;
    }

    public OrderItemId orderItemId() {
        return orderItemId;
    }

    public BigDecimal orderedQuantity() {
        return orderedQuantity;
    }

    public List<SpecificationLineDto> lines() {
        return Collections.unmodifiableList(lines);
    }
}
