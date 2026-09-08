package com.tmp.warehouse.domain;

/**
 * Reason a Transfer Document was created as an automatic continuation of another (ADR-037 §L).
 *
 * <p>Stage 3.5.7 introduces {@link #SHORTFALL} only. Partial-receive continuation belongs to later
 * stages.
 */
public enum TransferContinuationReason {
    SHORTFALL;

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
