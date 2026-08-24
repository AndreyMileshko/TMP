package com.tmp.production.domain.repository;

import com.tmp.production.domain.SourceOrderId;

/**
 * Read port for posted whole-order Production Cancellation evidence (Production Spec §16).
 */
public interface ProductionCancellationQuery {

    /** Returns {@code true} when a POSTED Cancellation exists for the source order. */
    boolean hasPostedCancellation(SourceOrderId sourceOrderId);
}
