package com.tmp.warehouse.application;

import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import com.tmp.warehouse.api.WarehouseDemandQueryApi;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLine;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandStatusDeriver;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseDemandWaitingReason;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
import com.tmp.warehouse.persistence.JdbcWarehouseDemandFulfillmentReadQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default Warehouse Demand query API (B3B-3A). Derives received quantity from settled receipt facts
 * and status from receipt totals + active linked Transfers. No mutable Demand counters.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed collaborators.")
public final class DefaultWarehouseDemandQueryApi implements WarehouseDemandQueryApi {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWarehouseDemandQueryApi.class);

    private final WarehouseDemandRepository demands;
    private final JdbcWarehouseDemandFulfillmentReadQuery fulfillmentRead;

    public DefaultWarehouseDemandQueryApi(
            WarehouseDemandRepository demands,
            JdbcWarehouseDemandFulfillmentReadQuery fulfillmentRead) {
        this.demands = Objects.requireNonNull(demands, "demands");
        this.fulfillmentRead = Objects.requireNonNull(fulfillmentRead, "fulfillmentRead");
    }

    @Override
    public Optional<WarehouseDemandView> getDemand(UUID demandId) {
        Objects.requireNonNull(demandId, "demandId");
        return demands.findById(WarehouseDemandId.of(demandId)).map(this::toView);
    }

    @Override
    public Optional<WarehouseDemandView> getDemandBySourceMaterialRequirementId(
            UUID sourceMaterialRequirementId) {
        Objects.requireNonNull(sourceMaterialRequirementId, "sourceMaterialRequirementId");
        return demands
                .findBySourceMaterialRequirementId(sourceMaterialRequirementId)
                .map(this::toView);
    }

    private WarehouseDemandView toView(WarehouseDemand demand) {
        Map<WarehouseDemandLineId, BigDecimal> receivedByLine =
                fulfillmentRead.receivedQuantitiesByDemandLine(
                        demand.id(), demand.destinationWarehouseId());
        Set<WarehouseDemandLineId> activeLines =
                fulfillmentRead.demandLinesWithActiveTransfer(demand.id());
        Map<WarehouseDemandLineId, List<WarehouseDemandTransferLink>> linksByLine =
                demands.findTransferLinksByDemandId(demand.id()).stream()
                        .collect(Collectors.groupingBy(WarehouseDemandTransferLink::demandLineId));

        List<WarehouseDemandLineView> lineViews = new ArrayList<>(demand.lines().size());
        List<WarehouseDemandDerivedStatus> lineStatuses = new ArrayList<>(demand.lines().size());
        boolean cancelled = demand.isCancelled();

        for (WarehouseDemandLine line : demand.lines()) {
            BigDecimal required = line.requiredQuantity().value();
            BigDecimal received =
                    receivedByLine.getOrDefault(line.id(), BigDecimal.ZERO);
            if (received.compareTo(required) > 0) {
                LOG.warn(
                        "Demand line over-receipt detected: demandId={}, demandLineId={}, required={}, received={}",
                        demand.id().value(),
                        line.id().value(),
                        required,
                        received);
            }
            BigDecimal remaining =
                    WarehouseDemandStatusDeriver.remainingQuantity(required, received);
            boolean hasActive = activeLines.contains(line.id());
            WarehouseDemandDerivedStatus derivedStatus =
                    WarehouseDemandStatusDeriver.deriveLineStatus(
                            cancelled, required, received, hasActive);
            WarehouseDemandWaitingReason effectiveReason =
                    WarehouseDemandStatusDeriver.effectiveWaitingReason(
                            derivedStatus, line.waitingReason().orElse(null));
            List<LinkedTransferRef> linkedRefs =
                    linksByLine.getOrDefault(line.id(), List.of()).stream()
                            .map(
                                    link ->
                                            new LinkedTransferRef(
                                                    link.transferDocumentId(),
                                                    link.transferLineId().value(),
                                                    link.linkedQuantity().value()))
                            .toList();
            lineStatuses.add(derivedStatus);
            lineViews.add(
                    new WarehouseDemandLineView(
                            line.id().value(),
                            line.sourceMaterialRequirementLineId(),
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure(),
                            line.lengthMm().orElse(null),
                            required,
                            received,
                            remaining,
                            line.materialReferenceId().map(MaterialReferenceId::value).orElse(null),
                            derivedStatus,
                            effectiveReason == null ? null : effectiveReason.name(),
                            linkedRefs));
        }

        WarehouseDemandDerivedStatus headerStatus =
                WarehouseDemandStatusDeriver.deriveHeaderStatus(cancelled, lineStatuses);
        return new WarehouseDemandView(
                demand.id().value(),
                demand.sourceMaterialRequirementId(),
                demand.destinationWarehouseId().value(),
                demand.acceptedAt(),
                demand.acceptedBy().orElse(null),
                demand.cancelledAt().orElse(null),
                demand.cancelledBy().orElse(null),
                headerStatus,
                lineViews);
    }
}
