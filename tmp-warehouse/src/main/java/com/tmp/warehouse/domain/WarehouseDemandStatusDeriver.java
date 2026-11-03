package com.tmp.warehouse.domain;

import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Pure derivation of Warehouse Demand fulfillment status (B3B-3A + ADR-038 resupply waiver). No
 * persistence.
 *
 * <p>Line precedence: CANCELLED → FULFILLED (received ≥ required) → RESUPPLY_WAIVED (remaining 0
 * with waived &gt; 0) → IN_FULFILLMENT (active linked Transfer) → WAITING_FOR_SUPPLY.
 */
public final class WarehouseDemandStatusDeriver {

    private WarehouseDemandStatusDeriver() {}

    public static WarehouseDemandDerivedStatus deriveLineStatus(
            boolean demandCancelled,
            BigDecimal requiredQuantity,
            BigDecimal receivedQuantity,
            BigDecimal waivedQuantity,
            boolean hasActiveLinkedTransfer) {
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity");
        Objects.requireNonNull(waivedQuantity, "waivedQuantity");
        if (demandCancelled) {
            return WarehouseDemandDerivedStatus.CANCELLED;
        }
        BigDecimal remaining = remainingQuantity(requiredQuantity, receivedQuantity, waivedQuantity);
        if (remaining.signum() == 0 && receivedQuantity.compareTo(requiredQuantity) >= 0) {
            return WarehouseDemandDerivedStatus.FULFILLED;
        }
        if (remaining.signum() == 0 && waivedQuantity.signum() > 0) {
            return WarehouseDemandDerivedStatus.RESUPPLY_WAIVED;
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
        boolean anyResupplyWaived = false;
        boolean allClosed = true;
        for (WarehouseDemandDerivedStatus status : lineStatuses) {
            Objects.requireNonNull(status, "lineStatus");
            if (status == WarehouseDemandDerivedStatus.IN_FULFILLMENT) {
                anyInFulfillment = true;
            }
            if (status == WarehouseDemandDerivedStatus.RESUPPLY_WAIVED) {
                anyResupplyWaived = true;
            }
            if (status != WarehouseDemandDerivedStatus.FULFILLED
                    && status != WarehouseDemandDerivedStatus.RESUPPLY_WAIVED) {
                allClosed = false;
            }
        }
        if (allClosed && !anyResupplyWaived) {
            return WarehouseDemandDerivedStatus.FULFILLED;
        }
        if (allClosed && anyResupplyWaived) {
            return WarehouseDemandDerivedStatus.RESUPPLY_WAIVED;
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
            BigDecimal requiredQuantity,
            BigDecimal receivedQuantity,
            BigDecimal waivedQuantity) {
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        Objects.requireNonNull(receivedQuantity, "receivedQuantity");
        Objects.requireNonNull(waivedQuantity, "waivedQuantity");
        BigDecimal remaining =
                requiredQuantity.subtract(receivedQuantity).subtract(waivedQuantity);
        return remaining.signum() < 0 ? BigDecimal.ZERO : remaining;
    }
}
