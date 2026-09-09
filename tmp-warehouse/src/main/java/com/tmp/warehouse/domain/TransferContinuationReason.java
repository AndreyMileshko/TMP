package com.tmp.warehouse.domain;

/**
 * Reason a Transfer Document was created as an automatic continuation of another (ADR-037 §L).
 *
 * <p>{@link #SHORTFALL}: sender could not physically send the full DRAFT requirement before POST
 * (Stage 3.5.7).
 *
 * <p>{@link #RECEIVE_SHORTFALL}: sender physically sent the immutable POSTED quantity, but the
 * receiver accepted less (Stage 3.5.8.2). These reasons must remain distinct.
 */
public enum TransferContinuationReason {
    SHORTFALL,
    RECEIVE_SHORTFALL;

    public static TransferContinuationReason parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("continuationReason must not be blank");
        }
        try {
            return TransferContinuationReason.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown continuationReason: " + raw, ex);
        }
    }
}
