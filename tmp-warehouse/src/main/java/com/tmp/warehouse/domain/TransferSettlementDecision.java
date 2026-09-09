package com.tmp.warehouse.domain;

/**
 * Receiver decision recorded on Transfer Document settlement (Stage 3.5.8).
 *
 * <p>Stage 3.5.8.1 writes {@link #ACCEPTED} only. {@link #REJECTED} is reserved for 3.5.8.3.
 */
public enum TransferSettlementDecision {
    ACCEPTED,
    REJECTED;

    public static TransferSettlementDecision parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return TransferSettlementDecision.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown settlement decision: " + raw, ex);
        }
    }
}
