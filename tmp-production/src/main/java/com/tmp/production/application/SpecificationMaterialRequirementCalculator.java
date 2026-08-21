package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.domain.AggregatedMaterialRequirement;
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
 * <p>{@link ResolvedMaterialLine#lineQuantity()} is the total planned quantity for the line and
 * must not be multiplied by product or order quantities (OM Spec v1.10).
 */
public final class SpecificationMaterialRequirementCalculator {

    public List<AggregatedMaterialRequirement> aggregate(
            List<ResolvedMaterialLine> materialLines) {
        Objects.requireNonNull(materialLines, "materialLines");
        Map<SpecificationMaterialIdentity, MutableAggregate> aggregates = new LinkedHashMap<>();
        for (ResolvedMaterialLine line : materialLines) {
            SpecificationMaterialIdentity identity =
                    SpecificationMaterialIdentity.of(
                            line.materialCode(), line.color(), line.unitOfMeasure());
            MutableAggregate aggregate =
                    aggregates.computeIfAbsent(identity, ignored -> new MutableAggregate());
            if (aggregate.materialName == null && line.materialName() != null) {
                aggregate.materialName = line.materialName();
            }
            aggregate.requiredQuantity = aggregate.requiredQuantity.add(line.lineQuantity());
        }
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

    private static final class MutableAggregate {
        private String materialName;
        private BigDecimal requiredQuantity = BigDecimal.ZERO;
    }
}
