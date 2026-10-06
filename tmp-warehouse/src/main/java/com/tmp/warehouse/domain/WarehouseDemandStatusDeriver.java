package com.tmp.warehouse.domain;

import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Pure derivation of Warehouse Demand fulfillment status (B3B-3A). No persistence.
 *
 * <p>Precedence for a line: CANCELLED (header terminal) → FULFILLED → IN_FULFILLMENT (active
 * linked Transfer) → WAITING_FOR_SUPPLY. Partial received quantity alone does not imply
 * IN_FULFILLMENT.
 */
public final class WarehouseDemandStatusDeriver {

    private WarehouseDemandStatusDeriver() {}

    public static WarehouseDemandDerivedStatus deriveLineStatus(
            boolean demandCancelled,
            BigDecimal requiredQuantity,
            BigDecimal receivedQuantity,
            boolean hasActiveLinkedTransfer) {
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity");
        if (demandCancelled) {
            return WarehouseDemandDerivedStatus.CANCELLED;
        }
        if (receivedQuantity.compareTo(requiredQuantity) >= 0) {
            return WarehouseDemandDerivedStatus.FULFILLED;
        }
        if (hasActiveLinkedTransfer) {
            return WarehouseDemandDerivedStatus.IN_FULFILLMENT;
        }
        return WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY;
    }

    public static WarehouseDemandDerivedStatus deriveHeaderStatus(
            boolean demandCancelled, List<WarehouseDemandDerivedStatus> lineStatuses) {
        Objects.requireNonNull(lineStatuses, "lineStatuses");
        if (demandCancelled) {
            return WarehouseDemandDerivedStatus.CANCELLED;
        }
        if (lineStatuses.isEmpty()) {
            throw new IllegalArgumentException("Demand must have at least one line status");
        }
        boolean anyInFulfillment = false;
        boolean allFulfilled = true;
        for (WarehouseDemandDerivedStatus status : lineStatuses) {
            Objects.requireNonNull(status, "lineStatus");
            if (status != WarehouseDemandDerivedStatus.FULFILLED) {
                allFulfilled = false;
            }
            if (status == WarehouseDemandDerivedStatus.IN_FULFILLMENT) {
                anyInFulfillment = true;
            }
        }
        if (allFulfilled) {
            return WarehouseDemandDerivedStatus.FULFILLED;
        }
        if (anyInFulfillment) {
            return WarehouseDemandDerivedStatus.IN_FULFILLMENT;
        }
        return WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY;
    }

    /**
     * Effective waiting reason for query output: only when derived status is WAITING_FOR_SUPPLY.
     * Falls back to {@link WarehouseDemandWaitingReason#ROUTING_DEFERRED} when stored reason is
     * null (derived fallback — not necessarily persisted).
     */
    public static WarehouseDemandWaitingReason effectiveWaitingReason(
            WarehouseDemandDerivedStatus derivedStatus,
            WarehouseDemandWaitingReason storedWaitingReason) {
        Objects.requireNonNull(derivedStatus, "derivedStatus");
        if (derivedStatus != WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY) {
            return null;
        }
        return storedWaitingReason == null
                ? WarehouseDemandWaitingReason.ROUTING_DEFERRED
                : storedWaitingReason;
    }

    public static BigDecimal remainingQuantity(
            BigDecimal requiredQuantity, BigDecimal receivedQuantity) {
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity");
        BigDecimal remaining = requiredQuantity.subtract(receivedQuantity);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }
}
