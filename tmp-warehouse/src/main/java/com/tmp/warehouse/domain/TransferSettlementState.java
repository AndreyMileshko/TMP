package com.tmp.warehouse.domain;

/**
 * Warehouse-owned post-send operational settlement state for a Transfer Document (Stage 3.5.8).
 *
 * <p>Not a Document Engine lifecycle. Document remains {@code POSTED} while settlement is open and
 * becomes {@code CLOSED} when settlement reaches {@link #SETTLED}.
 */
public enum TransferSettlementState {
    AWAITING_RECEIPT,
    RETURN_PENDING,
    SETTLED;

    public static TransferSettlementState parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("settlementState must not be blank");
        }
        try {
            return TransferSettlementState.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown settlementState: " + raw, ex);
        }
    }
}
