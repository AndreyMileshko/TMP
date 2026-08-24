package com.tmp.production.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable Production-owned business history entry (Production Spec §22, ADR-021).
 *
 * <p>Append-only timeline fact. Not an event-sourcing stream and not Security Audit.
 */
public final class ProductionHistoryEntry {

    private final ProductionHistoryEntryId entryId;
    private final SourceOrderId sourceOrderId;
    private final ProductionHistoryType historyType;
    private final Instant occurredAt;
    private final Instant recordedAt;
    private final Optional<SourceOrderItemId> sourceOrderItemId;
    private final Optional<UUID> sourceDocumentId;
    private final Optional<UUID> businessReferenceId;
    private final Optional<String> actorRef;
    private final Optional<String> summary;
    private final Optional<String> detailsJson;

    private ProductionHistoryEntry(
            ProductionHistoryEntryId entryId,
            SourceOrderId sourceOrderId,
            ProductionHistoryType historyType,
            Instant occurredAt,
            Instant recordedAt,
            Optional<SourceOrderItemId> sourceOrderItemId,
            Optional<UUID> sourceDocumentId,
            Optional<UUID> businessReferenceId,
            Optional<String> actorRef,
            Optional<String> summary,
            Optional<String> detailsJson) {
        this.entryId = Objects.requireNonNull(entryId, "entryId");
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.historyType = Objects.requireNonNull(historyType, "historyType");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.sourceOrderItemId = Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
        this.sourceDocumentId = Objects.requireNonNull(sourceDocumentId, "sourceDocumentId");
        this.businessReferenceId =
                Objects.requireNonNull(businessReferenceId, "businessReferenceId");
        this.actorRef = Objects.requireNonNull(actorRef, "actorRef");
        this.summary = Objects.requireNonNull(summary, "summary");
        this.detailsJson = Objects.requireNonNull(detailsJson, "detailsJson");
    }

    public static ProductionHistoryEntry create(
            ProductionHistoryEntryId entryId,
            SourceOrderId sourceOrderId,
            ProductionHistoryType historyType,
            Instant occurredAt,
            Instant recordedAt,
            Optional<SourceOrderItemId> sourceOrderItemId,
            Optional<UUID> sourceDocumentId,
            Optional<UUID> businessReferenceId,
            Optional<String> actorRef,
            Optional<String> summary,
            Optional<String> detailsJson) {
        return new ProductionHistoryEntry(
                entryId,
                sourceOrderId,
                historyType,
                occurredAt,
                recordedAt,
                sourceOrderItemId,
                sourceDocumentId,
                businessReferenceId,
                normalizeActor(actorRef),
                normalizeText(summary, "summary"),
                normalizeText(detailsJson, "detailsJson"));
    }

    public static ProductionHistoryEntry restore(
            ProductionHistoryEntryId entryId,
            SourceOrderId sourceOrderId,
            ProductionHistoryType historyType,
            Instant occurredAt,
            Instant recordedAt,
            Optional<SourceOrderItemId> sourceOrderItemId,
            Optional<UUID> sourceDocumentId,
            Optional<UUID> businessReferenceId,
            Optional<String> actorRef,
            Optional<String> summary,
            Optional<String> detailsJson) {
        return create(
                entryId,
                sourceOrderId,
                historyType,
                occurredAt,
                recordedAt,
                sourceOrderItemId,
                sourceDocumentId,
                businessReferenceId,
                actorRef,
                summary,
                detailsJson);
    }

    public ProductionHistoryEntryId entryId() {
        return entryId;
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public ProductionHistoryType historyType() {
        return historyType;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public Optional<SourceOrderItemId> sourceOrderItemId() {
        return sourceOrderItemId;
    }

    public Optional<UUID> sourceDocumentId() {
        return sourceDocumentId;
    }

    public Optional<UUID> businessReferenceId() {
        return businessReferenceId;
    }

    public Optional<String> actorRef() {
        return actorRef;
    }

    public Optional<String> summary() {
        return summary;
    }

    public Optional<String> detailsJson() {
        return detailsJson;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ProductionHistoryEntry that)) {
            return false;
        }
        return entryId.equals(that.entryId);
    }

    @Override
    public int hashCode() {
        return entryId.hashCode();
    }

    @Override
    public String toString() {
        return "ProductionHistoryEntry{"
                + "entryId="
                + entryId
                + ", sourceOrderId="
                + sourceOrderId
                + ", historyType="
                + historyType
                + ", occurredAt="
                + occurredAt
                + '}';
    }

    private static Optional<String> normalizeActor(Optional<String> actorRef) {
        Objects.requireNonNull(actorRef, "actorRef");
        return actorRef.map(String::trim).filter(value -> !value.isEmpty());
    }

    private static Optional<String> normalizeText(Optional<String> value, String name) {
        Objects.requireNonNull(value, name);
        return value.map(String::trim).filter(text -> !text.isEmpty());
    }

    /** Stable UUID identity for a history entry. */
    public static final class ProductionHistoryEntryId {

        private final UUID value;

        private ProductionHistoryEntryId(UUID value) {
            this.value = Objects.requireNonNull(value, "value");
        }

        public static ProductionHistoryEntryId of(UUID value) {
            return new ProductionHistoryEntryId(value);
        }

        public static ProductionHistoryEntryId generate() {
            return new ProductionHistoryEntryId(UUID.randomUUID());
        }

        public UUID value() {
            return value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ProductionHistoryEntryId that)) {
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

    /**
     * Business-oriented Production history categories (Production Spec §22).
     *
     * <p>Technical Document Engine / processor / Warehouse call steps are intentionally absent.
     */
    public enum ProductionHistoryType {
        ORDER_ACCEPTED,
        MATERIALS_CHECKED,
        MATERIAL_TRANSFER_CREATED,
        MATERIAL_RECEIPT_CONFIRMED,
        PRODUCTS_RELEASED,
        PLAN_FACT_DEVIATION,
        PRODUCTION_CANCELLED
    }
}
