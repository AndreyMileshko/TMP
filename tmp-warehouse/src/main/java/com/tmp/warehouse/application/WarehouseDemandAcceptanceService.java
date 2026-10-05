package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandLine;
import com.tmp.warehouse.domain.WarehouseDemandWaitingReason;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal Warehouse service that atomically persists one accepted Demand snapshot.
 *
 * <p>No Production public API, no MaterialReference resolution, no Transfer creation, no routing.
 * Repeat accept for the same {@code sourceMaterialRequirementId} returns the existing Demand.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed repository and Clock.")
public final class WarehouseDemandAcceptanceService {

    private final WarehouseDemandRepository demands;
    private final Clock clock;

    public WarehouseDemandAcceptanceService(WarehouseDemandRepository demands, Clock clock) {
        this.demands = Objects.requireNonNull(demands, "demands");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public WarehouseDemand accept(AcceptWarehouseDemandCommand command) {
        Objects.requireNonNull(command, "command");
        return demands
                .findBySourceMaterialRequirementId(command.sourceMaterialRequirementId())
                .orElseGet(() -> insertNew(command));
    }

    private WarehouseDemand insertNew(AcceptWarehouseDemandCommand command) {
        Instant acceptedAt = clock.instant();
        List<WarehouseDemandLine> lines = new ArrayList<>(command.lines().size());
        for (AcceptWarehouseDemandLineDraft draft : command.lines()) {
            lines.add(
                    WarehouseDemandLine.create(
                            draft.sourceMaterialRequirementLineId(),
                            draft.materialCode(),
                            draft.materialName(),
                            draft.color(),
                            draft.unitOfMeasure(),
                            draft.lengthMm(),
                            StockQuantity.of(draft.requiredQuantity()),
                            draft.materialReferenceId(),
                            draft.waitingReason()));
        }
        WarehouseDemand demand =
                WarehouseDemand.accept(
                        command.sourceMaterialRequirementId(),
                        command.destinationWarehouseId(),
                        acceptedAt,
                        command.acceptedBy(),
                        lines);
        demands.insert(demand);
        return demand;
    }

    /**
     * Internal acceptance snapshot. Not a Production integration contract.
     */
    public record AcceptWarehouseDemandCommand(
            UUID sourceMaterialRequirementId,
            WarehouseId destinationWarehouseId,
            String acceptedBy,
            List<AcceptWarehouseDemandLineDraft> lines) {

        public AcceptWarehouseDemandCommand {
            Objects.requireNonNull(sourceMaterialRequirementId, "sourceMaterialRequirementId");
            Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
            Objects.requireNonNull(lines, "lines");
            lines = List.copyOf(lines);
        }
    }

    public record AcceptWarehouseDemandLineDraft(
            UUID sourceMaterialRequirementLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal requiredQuantity,
            MaterialReferenceId materialReferenceId,
            WarehouseDemandWaitingReason waitingReason) {

        public AcceptWarehouseDemandLineDraft {
            Objects.requireNonNull(
                    sourceMaterialRequirementLineId, "sourceMaterialRequirementLineId");
            Objects.requireNonNull(materialCode, "materialCode");
            Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        }
    }
}
