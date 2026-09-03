package com.tmp.production.domain.repository;

import com.tmp.production.domain.SourceOrderId;

/**
 * Read port for posted whole-order Production Cancellation evidence (Production Spec §16).
 */
public interface ProductionCancellationQuery {

    /** Returns {@code true} when a POSTED Cancellation exists for the source order. */
    boolean hasPostedCancellation(SourceOrderId sourceOrderId);

    /**
     * Returns source order ids among {@code sourceOrderIds} that have a POSTED Cancellation.
     *
     * <p>Default loops {@link #hasPostedCancellation}. JDBC adapters MUST use a single query.
     */
    default java.util.Set<SourceOrderId> findPostedCancellationOrderIds(
            java.util.Collection<SourceOrderId> sourceOrderIds) {
        java.util.Objects.requireNonNull(sourceOrderIds, "sourceOrderIds");
        if (sourceOrderIds.isEmpty()) {
            return java.util.Set.of();
        }
        java.util.LinkedHashSet<SourceOrderId> posted = new java.util.LinkedHashSet<>();
        for (SourceOrderId sourceOrderId : sourceOrderIds) {
            if (hasPostedCancellation(sourceOrderId)) {
                posted.add(sourceOrderId);
            }
        }
        return java.util.Set.copyOf(posted);
    }
}
