package com.tmp.warehouse.domain;

/**
 * Whether a linked Transfer Document currently covers an open Demand obligation (B3B-3A).
 *
 * <p>Active: Document {@code DRAFT}, or Document {@code POSTED} with settlement {@code
 * AWAITING_RECEIPT}.
 *
 * <p>Terminal (not active): {@code CLOSED}/{@code SETTLED}, {@code RETURN_PENDING} (partial accept
 * or reject — remaining obligation is on a continuation DRAFT when demand-driven), and any other
 * combination.
 */
public final class WarehouseDemandActiveTransferRules {

    private WarehouseDemandActiveTransferRules() {}

    /**
     * @param documentStatus Document Engine status name ({@code DRAFT}/{@code POSTED}/{@code
     *     CLOSED})
     * @param settlementState Warehouse settlement state name, or {@code null} when no settlement
     *     row exists (typical for DRAFT)
     */
    public static boolean isActive(String documentStatus, String settlementState) {
        if (documentStatus == null || documentStatus.isBlank()) {
            return false;
        }
        String status = documentStatus.trim();
        if ("DRAFT".equals(status)) {
            return true;
        }
        if ("POSTED".equals(status)) {
            return "AWAITING_RECEIPT".equals(settlementState);
        }
        return false;
    }
}
