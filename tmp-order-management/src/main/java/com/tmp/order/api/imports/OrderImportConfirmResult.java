package com.tmp.order.api.imports;

import com.tmp.order.api.OrderId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Successful confirm result of a source-neutral order import.
 */
public final class OrderImportConfirmResult {

    private final OrderId orderId;
    private final String orderNumber;
    private final UUID importMetadataId;
    private final int createdPositionCount;
    private final int createdSpecificationLineCount;
    private final Instant importedAt;

    private OrderImportConfirmResult(
            OrderId orderId,
            String orderNumber,
            UUID importMetadataId,
            int createdPositionCount,
            int createdSpecificationLineCount,
            Instant importedAt) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.importMetadataId = importMetadataId;
        this.createdPositionCount = createdPositionCount;
        this.createdSpecificationLineCount = createdSpecificationLineCount;
        this.importedAt = importedAt;
    }

    public static OrderImportConfirmResult of(
            OrderId orderId,
            String orderNumber,
            UUID importMetadataId,
            int createdPositionCount,
            int createdSpecificationLineCount,
            Instant importedAt) {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(orderNumber, "orderNumber");
        Objects.requireNonNull(importMetadataId, "importMetadataId");
        Objects.requireNonNull(importedAt, "importedAt");
        if (createdPositionCount < 0) {
            throw new IllegalArgumentException("createdPositionCount must be >= 0");
        }
        if (createdSpecificationLineCount < 0) {
            throw new IllegalArgumentException("createdSpecificationLineCount must be >= 0");
        }
        return new OrderImportConfirmResult(
                orderId,
                orderNumber,
                importMetadataId,
                createdPositionCount,
                createdSpecificationLineCount,
                importedAt);
    }

    public OrderId orderId() {
        return orderId;
    }

    public String orderNumber() {
        return orderNumber;
    }

    public UUID importMetadataId() {
        return importMetadataId;
    }

    public int createdPositionCount() {
        return createdPositionCount;
    }

    public int createdSpecificationLineCount() {
        return createdSpecificationLineCount;
    }

    public Instant importedAt() {
        return importedAt;
    }
}
