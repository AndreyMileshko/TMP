package com.tmp.production.application;

import com.tmp.production.application.MaterialReferenceResolver.ResolutionStatus;
import com.tmp.production.application.MaterialReferenceResolver.Result;
import com.tmp.production.application.PartialReleaseMaterialPlanCalculator.Input;
import com.tmp.production.application.PartialReleaseMaterialPlanCalculator.LinePlan;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ReleaseProductsException;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds system-computed planned material lines for a Production Release.
 *
 * <p>Calculates partial plan per frozen Specification material line first, resolves material
 * identity, then aggregates by {@code sourceOrderItemId + materialReferenceId + planningSource}.
 */
public final class ReleaseMaterialPlanBuilder {

    private final PartialReleaseMaterialPlanCalculator calculator;
    private final MaterialReferenceResolver materialReferenceResolver;

    public ReleaseMaterialPlanBuilder() {
        this(new PartialReleaseMaterialPlanCalculator(), new MaterialReferenceResolver());
    }

    ReleaseMaterialPlanBuilder(
            PartialReleaseMaterialPlanCalculator calculator,
            MaterialReferenceResolver materialReferenceResolver) {
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.materialReferenceResolver =
                Objects.requireNonNull(materialReferenceResolver, "materialReferenceResolver");
    }

    public List<PlannedMaterialLine> buildPlannedLines(
            ProductionItemState state,
            long releaseQuantity,
            List<ResolvedMaterialLine> specificationLines,
            List<MaterialReferenceEntry> materialCatalog) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(specificationLines, "specificationLines");
        Objects.requireNonNull(materialCatalog, "materialCatalog");

        long ordered = state.orderedQuantity().value().longValueExact();
        long releasedBefore = state.releasedQuantity().value().longValueExact();
        SourceOrderItemId itemId = state.sourceOrderItemId();
        SpecificationId specificationId = state.specificationId();

        Map<AggregationKey, MutableAggregate> aggregates = new LinkedHashMap<>();
        for (ResolvedMaterialLine specLine : specificationLines) {
            BigDecimal warehousePerProduct =
                    SpecificationWarehouseQuantityCalculator.warehouseQuantityPerProduct(
                            specLine.lineQuantity(),
                            specLine.lengthMm(),
                            specLine.unitOfMeasure());
            LinePlan plan =
                    calculator.calculate(
                            new Input(
                                    warehousePerProduct,
                                    ordered,
                                    releasedBefore,
                                    releaseQuantity));
            SpecificationMaterialIdentity identity =
                    SpecificationMaterialIdentity.of(
                            specLine.materialCode(), specLine.color(), specLine.unitOfMeasure());
            Result resolution =
                    materialReferenceResolver.resolve(identity, toCatalog(materialCatalog));
            if (resolution.status() == ResolutionStatus.UNRESOLVED) {
                throw new ReleaseProductsException(
                        "MATERIAL_UNRESOLVED for item "
                                + itemId.value()
                                + " materialCode="
                                + specLine.materialCode());
            }
            if (resolution.status() == ResolutionStatus.AMBIGUOUS) {
                throw new ReleaseProductsException(
                        "MATERIAL_AMBIGUOUS for item "
                                + itemId.value()
                                + " materialCode="
                                + specLine.materialCode());
            }

            MaterialReferenceId materialReferenceId =
                    MaterialReferenceId.of(resolution.materialReferenceId());
            AggregationKey key =
                    new AggregationKey(
                            itemId, materialReferenceId, MaterialPlanningSource.SPECIFICATION);
            MutableAggregate aggregate =
                    aggregates.computeIfAbsent(key, ignored -> new MutableAggregate());
            aggregate.plannedQuantity = aggregate.plannedQuantity.add(plan.planCurrent());
            if (aggregate.materialCode == null) {
                aggregate.materialCode = specLine.materialCode();
            }
            if (aggregate.unitOfMeasure == null) {
                aggregate.unitOfMeasure = specLine.unitOfMeasure();
            }
            if (aggregate.materialName == null && specLine.materialName() != null) {
                aggregate.materialName = specLine.materialName();
            }
            mergeLengthMm(aggregate, specLine.lengthMm());
            if (SpecificationWarehouseQuantityCalculator.isDimensionalMeter(
                    specLine.unitOfMeasure(), specLine.lengthMm())) {
                aggregate.technologicalPieceCount =
                        aggregate.technologicalPieceCount.add(
                                specLine
                                        .lineQuantity()
                                        .multiply(BigDecimal.valueOf(releaseQuantity)));
            }
        }

        List<PlannedMaterialLine> lines = new ArrayList<>(aggregates.size());
        for (Map.Entry<AggregationKey, MutableAggregate> entry : aggregates.entrySet()) {
            AggregationKey key = entry.getKey();
            MutableAggregate aggregate = entry.getValue();
            lines.add(
                    new PlannedMaterialLine(
                            key.sourceOrderItemId(),
                            key.materialReferenceId(),
                            specificationId,
                            aggregate.plannedQuantity,
                            key.planningSource(),
                            Optional.empty(),
                            aggregate.materialName,
                            aggregate.materialCode == null ? "" : aggregate.materialCode,
                            aggregate.unitOfMeasure == null ? "" : aggregate.unitOfMeasure,
                            aggregate.lengthMmConflict ? null : aggregate.lengthMm,
                            aggregate.technologicalPieceCount.signum() > 0
                                    ? aggregate.technologicalPieceCount
                                    : null));
        }
        return List.copyOf(lines);
    }

    private static void mergeLengthMm(MutableAggregate aggregate, BigDecimal lengthMm) {
        if (aggregate.lengthMmConflict) {
            return;
        }
        if (lengthMm == null) {
            if (aggregate.lengthMmSeen) {
                return;
            }
            aggregate.lengthMmSeen = true;
            return;
        }
        if (!aggregate.lengthMmSeen) {
            aggregate.lengthMm = lengthMm;
            aggregate.lengthMmSeen = true;
            return;
        }
        if (aggregate.lengthMm == null || aggregate.lengthMm.compareTo(lengthMm) != 0) {
            aggregate.lengthMm = null;
            aggregate.lengthMmConflict = true;
        }
    }

    private static List<MaterialReferenceResolver.CatalogEntry> toCatalog(
            List<MaterialReferenceEntry> catalog) {
        return catalog.stream()
                .map(
                        entry ->
                                new MaterialReferenceResolver.CatalogEntry(
                                        entry.materialReferenceId(),
                                        entry.article(),
                                        entry.color(),
                                        entry.unitOfMeasure()))
                .toList();
    }

    public record PlannedMaterialLine(
            SourceOrderItemId sourceOrderItemId,
            MaterialReferenceId materialReferenceId,
            SpecificationId specificationId,
            BigDecimal plannedQuantity,
            MaterialPlanningSource planningSource,
            Optional<UUID> cuttingPlanId,
            String materialName,
            String materialCode,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal technologicalPieceCount) {

        public PlannedMaterialLine {
            Objects.requireNonNull(sourceOrderItemId, "sourceOrderItemId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            Objects.requireNonNull(specificationId, "specificationId");
            Objects.requireNonNull(plannedQuantity, "plannedQuantity");
            Objects.requireNonNull(planningSource, "planningSource");
            Objects.requireNonNull(cuttingPlanId, "cuttingPlanId");
            materialCode = materialCode == null ? "" : materialCode;
            unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
            plannedQuantity = PartialReleaseMaterialPlanCalculator.normalize(plannedQuantity);
        }

        public String stableKey() {
            return sourceOrderItemId.value() + "|" + materialReferenceId.value();
        }
    }

    private record AggregationKey(
            SourceOrderItemId sourceOrderItemId,
            MaterialReferenceId materialReferenceId,
            MaterialPlanningSource planningSource) {}

    private static final class MutableAggregate {
        private String materialName;
        private String materialCode;
        private String unitOfMeasure;
        private BigDecimal plannedQuantity = BigDecimal.ZERO;
        private BigDecimal lengthMm;
        private boolean lengthMmSeen;
        private boolean lengthMmConflict;
        private BigDecimal technologicalPieceCount = BigDecimal.ZERO;
    }
}
