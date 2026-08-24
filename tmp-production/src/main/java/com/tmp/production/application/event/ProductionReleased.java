package com.tmp.production.application.event;

import com.tmp.core.api.event.DomainEvent;
import com.tmp.production.domain.ProductionStatus;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Published after a successful Production Release document POST and outer transaction commit
 * (Production Spec §19).
 */
public record ProductionReleased(
        String eventId,
        Instant releasedAt,
        UUID productionReleaseDocumentId,
        UUID sourceOrderId,
        List<ReleasedItem> releasedItems) implements DomainEvent {

    public static final String EVENT_TYPE = "production.released";
    private static final String SOURCE_CAPABILITY = "production";

    public ProductionReleased {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(releasedAt, "releasedAt");
        Objects.requireNonNull(productionReleaseDocumentId, "productionReleaseDocumentId");
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(releasedItems, "releasedItems");
        if (releasedItems.isEmpty()) {
            throw new IllegalArgumentException("releasedItems must not be empty");
        }
        releasedItems = List.copyOf(releasedItems);
        Set<UUID> uniqueItems = new HashSet<>();
        for (ReleasedItem item : releasedItems) {
            if (!uniqueItems.add(item.sourceOrderItemId())) {
                throw new IllegalArgumentException(
                        "releasedItems must not contain duplicate sourceOrderItemId: "
                                + item.sourceOrderItemId());
            }
        }
    }

    @Override
    public Instant occurredAt() {
        return releasedAt;
    }

    @Override
    public String eventType() {
        return EVENT_TYPE;
    }

    @Override
    public String sourceCapabilityId() {
        return SOURCE_CAPABILITY;
    }

    /** One released item line from the Production Release document. */
    public record ReleasedItem(
            UUID sourceOrderItemId,
            UUID specificationId,
            long releasedQuantity,
            ProductionStatus resultingStatus) {

        public ReleasedItem {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(resultingStatus, "resultingStatus");
            if (releasedQuantity <= 0) {
                throw new IllegalArgumentException(
                        "releasedQuantity must be > 0: " + releasedQuantity);
            }
        }
    }
}
