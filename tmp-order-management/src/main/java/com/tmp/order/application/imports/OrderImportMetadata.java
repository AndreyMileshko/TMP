package com.tmp.order.application.imports;

import com.tmp.order.api.OrderId;
import com.tmp.security.api.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Capability-owned import metadata record (Specification §27.6 duplicate protection).
 */
public final class OrderImportMetadata {

    private final UUID importId;
    private final String sourceType;
    private final String sourceReference;
    private final String contentChecksum;
    private final Instant importedAt;
    private final UserId importedBy;
    private final OrderId orderId;

    private OrderImportMetadata(
            UUID importId,
            String sourceType,
            String sourceReference,
            String contentChecksum,
            Instant importedAt,
            UserId importedBy,
            OrderId orderId) {
        this.importId = importId;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference;
        this.contentChecksum = contentChecksum;
        this.importedAt = importedAt;
        this.importedBy = importedBy;
        this.orderId = orderId;
    }

    public static OrderImportMetadata of(
            UUID importId,
            String sourceType,
            String sourceReference,
            String contentChecksum,
            Instant importedAt,
            UserId importedBy,
            OrderId orderId) {
        return new OrderImportMetadata(
                Objects.requireNonNull(importId, "importId"),
                requireNonBlank(sourceType, "sourceType"),
                requireNonBlank(sourceReference, "sourceReference"),
                requireNonBlank(contentChecksum, "contentChecksum"),
                Objects.requireNonNull(importedAt, "importedAt"),
                Objects.requireNonNull(importedBy, "importedBy"),
                Objects.requireNonNull(orderId, "orderId"));
    }

    private static String requireNonBlank(String value, String field) {
        Objects.requireNonNull(value, field);
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return trimmed;
    }

    public UUID importId() {
        return importId;
    }

    public String sourceType() {
        return sourceType;
    }

    public String sourceReference() {
        return sourceReference;
    }

    public String contentChecksum() {
        return contentChecksum;
    }

    public Instant importedAt() {
        return importedAt;
    }

    public UserId importedBy() {
        return importedBy;
    }

    public OrderId orderId() {
        return orderId;
    }
}
