package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.domain.AggregatedMaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure calculator for planned material requirements from frozen specification material lines.
 *
 * <p>Stage 7 Phase 2: {@link ResolvedMaterialLine#lineQuantity()} is the material norm <strong>per
 * one product</strong>. Material Requirement contribution =
 * {@code lineQuantity × requestedProductQuantity}.
 *
 * <p>{@link #aggregate(List)} remains for informational availability checks that still sum raw
 * specification line quantities without product scaling (unchanged readiness path).
 */
public final class SpecificationMaterialRequirementCalculator {

    /**
     * Sums raw specification line quantities by canonical material identity (no product scaling).
     * Used by material availability checks only.
     */
    public List<AggregatedMaterialRequirement> aggregate(List<ResolvedMaterialLine> materialLines) {
        Objects.requireNonNull(materialLines, "materialLines");
        Map<SpecificationMaterialIdentity, MutableAggregate> aggregates = new LinkedHashMap<>();
        for (ResolvedMaterialLine line : materialLines) {
            addContribution(
                    aggregates,
                    line.materialCode(),
                    line.materialName(),
                    line.color(),
                    line.unitOfMeasure(),
                    line.lineQuantity(),
                    null,
                    null);
        }
        return toAggregates(aggregates);
    }

    /**
     * Aggregates material requirements using per-product norms × requested product quantities, and
     * retains source-item contribution provenance.
     */
    public List<ScaledAggregate> aggregateScaled(List<ScaledMaterialInput> inputs) {
        Objects.requireNonNull(inputs, "inputs");
        Map<SpecificationMaterialIdentity, MutableAggregate> aggregates = new LinkedHashMap<>();
        for (ScaledMaterialInput input : inputs) {
            Objects.requireNonNull(input, "input");
            if (input.requestedProductQuantity() <= 0L) {
                throw new IllegalArgumentException(
                        "requestedProductQuantity must be > 0: "
                                + input.requestedProductQuantity());
            }
            for (ResolvedMaterialLine line : input.materialLines()) {
                BigDecimal contribution =
                        line.lineQuantity()
                                .multiply(BigDecimal.valueOf(input.requestedProductQuantity()));
                addContribution(
                        aggregates,
                        line.materialCode(),
                        line.materialName(),
                        line.color(),
                        line.unitOfMeasure(),
                        contribution,
                        input.sourceOrderId(),
                        input.sourceOrderItemId());
            }
        }
        List<ScaledAggregate> result = new ArrayList<>(aggregates.size());
        for (Map.Entry<SpecificationMaterialIdentity, MutableAggregate> entry :
                aggregates.entrySet()) {
            MutableAggregate aggregate = entry.getValue();
            result.add(
                    new ScaledAggregate(
                            entry.getKey(),
                            aggregate.materialName,
                            aggregate.requiredQuantity,
                            List.copyOf(aggregate.contributions)));
        }
        return List.copyOf(result);
    }

    private static void addContribution(
            Map<SpecificationMaterialIdentity, MutableAggregate> aggregates,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity,
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId) {
        SpecificationMaterialIdentity identity =
                SpecificationMaterialIdentity.of(materialCode, color, unitOfMeasure);
        MutableAggregate aggregate =
                aggregates.computeIfAbsent(identity, ignored -> new MutableAggregate());
        if (aggregate.materialName == null && materialName != null) {
            aggregate.materialName = materialName;
        }
        aggregate.requiredQuantity = aggregate.requiredQuantity.add(quantity);
        if (sourceOrderId != null && sourceOrderItemId != null && quantity.signum() > 0) {
            mergeContribution(aggregate, sourceOrderId, sourceOrderItemId, quantity);
        }
    }

    private static void mergeContribution(
            MutableAggregate aggregate,
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            BigDecimal quantity) {
        for (int i = 0; i < aggregate.contributions.size(); i++) {
            MaterialRequirementLineContribution existing = aggregate.contributions.get(i);
            if (existing.sourceOrderId().equals(sourceOrderId)
                    && existing.sourceOrderItemId().equals(sourceOrderItemId)) {
                aggregate.contributions.set(
                        i,
                        MaterialRequirementLineContribution.of(
                                sourceOrderId,
                                sourceOrderItemId,
                                existing.contributedMaterialQuantity().add(quantity)));
                return;
            }
        }
        aggregate.contributions.add(
                MaterialRequirementLineContribution.of(
                        sourceOrderId, sourceOrderItemId, quantity));
    }

    private static List<AggregatedMaterialRequirement> toAggregates(
            Map<SpecificationMaterialIdentity, MutableAggregate> aggregates) {
        List<AggregatedMaterialRequirement> result = new ArrayList<>(aggregates.size());
        for (Map.Entry<SpecificationMaterialIdentity, MutableAggregate> entry :
                aggregates.entrySet()) {
            MutableAggregate aggregate = entry.getValue();
            result.add(
                    new AggregatedMaterialRequirement(
                            entry.getKey(),
                            aggregate.materialName,
                            aggregate.requiredQuantity));
        }
        return List.copyOf(result);
    }

    public record ScaledMaterialInput(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            long requestedProductQuantity,
            List<ResolvedMaterialLine> materialLines) {
        public ScaledMaterialInput {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialLines, "materialLines");
            materialLines = List.copyOf(materialLines);
        }
    }

    public record ScaledAggregate(
            SpecificationMaterialIdentity identity,
            String materialName,
            BigDecimal requiredQuantity,
            List<MaterialRequirementLineContribution> contributions) {
        public ScaledAggregate {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(requiredQuantity, "requiredQuantity");
            Objects.requireNonNull(contributions, "contributions");
            contributions = List.copyOf(contributions);
        }
    }

    private static final class MutableAggregate {
        private String materialName;
        private BigDecimal requiredQuantity = BigDecimal.ZERO;
        private final List<MaterialRequirementLineContribution> contributions = new ArrayList<>();
    }
}
