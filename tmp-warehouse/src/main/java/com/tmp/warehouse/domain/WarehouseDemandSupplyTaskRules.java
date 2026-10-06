package com.tmp.warehouse.domain;

import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Demand-backed {@code PRODUCTION_MATERIAL_SUPPLY} eligibility (B3B-3C1).
 *
 * <p>Reuses {@link WarehouseDemandStatusDeriver} and {@link WarehouseDemandActiveTransferRules}
 * semantics — does not invent a parallel status model. A supply task exists when the Demand is not
 * cancelled and at least one line is {@code WAITING_FOR_SUPPLY} with {@code remainingQuantity > 0}
 * (equivalently: uncovered open obligation with no ACTIVE linked Transfer for that line).
 */
public final class WarehouseDemandSupplyTaskRules {

    private WarehouseDemandSupplyTaskRules() {}

    public static List<WaitingLine> waitingLines(
            WarehouseDemand demand,
            Map<WarehouseDemandLineId, BigDecimal> receivedByLine,
            Set<WarehouseDemandLineId> activeLines) {
        Objects.requireNonNull(demand, "demand");
        Objects.requireNonNull(receivedByLine, "receivedByLine");
        Objects.requireNonNull(activeLines, "activeLines");
        if (demand.isCancelled()) {
            return List.of();
        }
        List<WaitingLine> waiting = new ArrayList<>();
        for (WarehouseDemandLine line : demand.lines()) {
            BigDecimal required = line.requiredQuantity().value();
            BigDecimal received = receivedByLine.getOrDefault(line.id(), BigDecimal.ZERO);
            BigDecimal remaining =
                    WarehouseDemandStatusDeriver.remainingQuantity(required, received);
            boolean hasActive = activeLines.contains(line.id());
            WarehouseDemandDerivedStatus status =
                    WarehouseDemandStatusDeriver.deriveLineStatus(
                            false, required, received, hasActive);
            if (status != WarehouseDemandDerivedStatus.WAITING_FOR_SUPPLY) {
                continue;
            }
            if (remaining.signum() <= 0) {
                continue;
            }
            WarehouseDemandWaitingReason reason =
                    WarehouseDemandStatusDeriver.effectiveWaitingReason(
                            status, line.waitingReason().orElse(null));
            waiting.add(new WaitingLine(line, required, received, remaining, reason));
        }
        return List.copyOf(waiting);
    }

    public static boolean qualifiesForSupplyTask(
            WarehouseDemand demand,
            Map<WarehouseDemandLineId, BigDecimal> receivedByLine,
            Set<WarehouseDemandLineId> activeLines) {
        return !waitingLines(demand, receivedByLine, activeLines).isEmpty();
    }

    public record WaitingLine(
            WarehouseDemandLine line,
            BigDecimal requiredQuantity,
            BigDecimal receivedQuantity,
            BigDecimal remainingQuantity,
            WarehouseDemandWaitingReason effectiveWaitingReason) {

        public WaitingLine {
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(receivedQuantity, "receivedQuantity");
            Objects.requireNonNull(remainingQuantity, "remainingQuantity");
            Objects.requireNonNull(effectiveWaitingReason, "effectiveWaitingReason");
        }
    }
}
