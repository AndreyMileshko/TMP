package com.tmp.production.application;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialAvailabilityCheckResult;
import com.tmp.production.domain.MaterialAvailabilityLine;
import com.tmp.production.domain.MaterialAvailabilityLineStatus;
import com.tmp.production.domain.MaterialCheckNotAllowedException;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferNotAllowedException;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.MaterialTransferTemplateNotReadyException;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application use cases for Production-owned editable Material Transfer Templates.
 *
 * <p>prepare → persist → edit/read. Confirmation / WarehouseCommandApi is {@link
 * ConfirmMaterialTransferService} (STAGE7-010).
 */
public final class MaterialTransferTemplateService {

    private final CheckMaterialAvailabilityService availabilityService;
    private final ProductionOrderViewService orderViewService;
    private final ProductionFoundationQueryService foundationQuery;
    private final ProductionWarehouseScope warehouseScope;
    private final MaterialTransferTemplateRepository templateRepository;
    private final MaterialTransferRecommendationCalculator recommendationCalculator;
    private final Clock clock;

    public MaterialTransferTemplateService(
            CheckMaterialAvailabilityService availabilityService,
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            ProductionWarehouseScope warehouseScope,
            MaterialTransferTemplateRepository templateRepository,
            Clock clock) {
        this(
                availabilityService,
                orderViewService,
                foundationQuery,
                warehouseScope,
                templateRepository,
                new MaterialTransferRecommendationCalculator(),
                clock);
    }

    MaterialTransferTemplateService(
            CheckMaterialAvailabilityService availabilityService,
            ProductionOrderViewService orderViewService,
            ProductionFoundationQueryService foundationQuery,
            ProductionWarehouseScope warehouseScope,
            MaterialTransferTemplateRepository templateRepository,
            MaterialTransferRecommendationCalculator recommendationCalculator,
            Clock clock) {
        this.availabilityService =
                Objects.requireNonNull(availabilityService, "availabilityService");
        this.orderViewService = Objects.requireNonNull(orderViewService, "orderViewService");
        this.foundationQuery = Objects.requireNonNull(foundationQuery, "foundationQuery");
        this.warehouseScope = Objects.requireNonNull(warehouseScope, "warehouseScope");
        this.templateRepository =
                Objects.requireNonNull(templateRepository, "templateRepository");
        this.recommendationCalculator =
                Objects.requireNonNull(recommendationCalculator, "recommendationCalculator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Creates and persists a fresh editable template from a current Material Availability Check.
     */
    public MaterialTransferTemplate prepareMaterialTransferTemplate(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");

        MaterialAvailabilityCheckResult check;
        try {
            check = availabilityService.check(sourceOrderId);
        } catch (MaterialCheckNotAllowedException ex) {
            throw new MaterialTransferNotAllowedException(ex.sourceOrderId(), ex.viewStatus());
        }

        List<MaterialAvailabilityLine> blocking =
                check.lines().stream()
                        .filter(
                                line ->
                                        line.status()
                                                        == MaterialAvailabilityLineStatus
                                                                .MATERIAL_UNRESOLVED
                                                || line.status()
                                                        == MaterialAvailabilityLineStatus
                                                                .MATERIAL_AMBIGUOUS)
                        .toList();
        if (!blocking.isEmpty()) {
            throw new MaterialTransferTemplateNotReadyException(sourceOrderId, blocking);
        }

        List<ProductionItemState> itemStates = orderViewService.listItemStates(sourceOrderId);
        List<MaterialTransferTemplateLine> lines = new ArrayList<>();
        for (MaterialAvailabilityLine availabilityLine : check.lines()) {
            if (availabilityLine.materialReferenceId() == null) {
                throw new IllegalStateException(
                        "Resolved availability line unexpectedly missing materialReferenceId");
            }
            Optional<MaterialTransferTemplateLine> transferLine =
                    buildTransferLine(availabilityLine, itemStates);
            transferLine.ifPresent(lines::add);
        }

        Instant now = clock.instant();
        MaterialTransferTemplate template =
                MaterialTransferTemplate.create(
                        sourceOrderId,
                        warehouseScope.mainWarehouseId(),
                        warehouseScope.productionWarehouseId(),
                        now,
                        lines);
        return templateRepository.save(template);
    }

    public Optional<MaterialTransferTemplate> findTemplateById(MaterialTransferTemplateId templateId) {
        Objects.requireNonNull(templateId, "templateId");
        return templateRepository.findById(templateId);
    }

    public MaterialTransferTemplate changeRequestedQuantity(
            MaterialTransferTemplateId templateId,
            MaterialTransferTemplateLineId lineId,
            BigDecimal quantity) {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(quantity, "quantity");
        MaterialTransferTemplate template = requireTemplate(templateId);
        MaterialTransferTemplate edited =
                template.changeRequestedQuantity(lineId, quantity, clock.instant());
        return templateRepository.save(edited);
    }

    public MaterialTransferTemplate excludeLine(
            MaterialTransferTemplateId templateId, MaterialTransferTemplateLineId lineId) {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(lineId, "lineId");
        MaterialTransferTemplate template = requireTemplate(templateId);
        return templateRepository.save(template.excludeLine(lineId, clock.instant()));
    }

    public MaterialTransferTemplate restoreLine(
            MaterialTransferTemplateId templateId, MaterialTransferTemplateLineId lineId) {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(lineId, "lineId");
        MaterialTransferTemplate template = requireTemplate(templateId);
        return templateRepository.save(template.restoreLine(lineId, clock.instant()));
    }

    private MaterialTransferTemplate requireTemplate(MaterialTransferTemplateId templateId) {
        return templateRepository
                .findById(templateId)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Material transfer template not found: " + templateId));
    }

    private Optional<MaterialTransferTemplateLine> buildTransferLine(
            MaterialAvailabilityLine availabilityLine, List<ProductionItemState> itemStates) {
        UUID materialRefUuid = availabilityLine.materialReferenceId();
        MaterialReferenceId materialReferenceId = MaterialReferenceId.of(materialRefUuid);

        MaterialTransferRecommendationCalculator.Recommendation recommendation =
                recommendationCalculator.calculate(
                        availabilityLine.requiredQuantity(),
                        availabilityLine.productionWarehouseAvailable(),
                        availabilityLine.mainWarehouseAvailable());

        if (recommendation.recommendedTransferQuantity().signum() <= 0) {
            return Optional.empty();
        }

        SpecificationMaterialIdentity identity =
                SpecificationMaterialIdentity.of(
                        availabilityLine.materialCode(),
                        availabilityLine.color(),
                        availabilityLine.unitOfMeasure());

        Set<SourceOrderItemId> sourceItems = new LinkedHashSet<>();
        LinkedHashSet<CuttingPlanId> cuttingPlans = new LinkedHashSet<>();
        for (ProductionItemState state : itemStates) {
            if (itemContributesMaterial(state, identity)) {
                sourceItems.add(state.sourceOrderItemId());
                state.cuttingPlanLinks()
                        .findCuttingPlanId(materialReferenceId)
                        .ifPresent(cuttingPlans::add);
            }
        }

        CuttingResolution cutting = resolveCutting(cuttingPlans);

        return Optional.of(
                MaterialTransferTemplateLine.create(
                        materialReferenceId,
                        availabilityLine.materialCode(),
                        availabilityLine.materialName(),
                        availabilityLine.color(),
                        availabilityLine.unitOfMeasure(),
                        recommendation.recommendedTransferQuantity(),
                        MaterialPlanningSource.SPECIFICATION,
                        cutting.cuttingPlanId(),
                        cutting.status(),
                        cutting.references(),
                        sourceItems,
                        availabilityLine.requiredQuantity(),
                        availabilityLine.mainWarehouseAvailable(),
                        availabilityLine.productionWarehouseAvailable(),
                        recommendation.uncoveredDeficit()));
    }

    private boolean itemContributesMaterial(
            ProductionItemState state, SpecificationMaterialIdentity identity) {
        List<ResolvedMaterialLine> lines = foundationQuery.materialLines(state);
        for (ResolvedMaterialLine line : lines) {
            SpecificationMaterialIdentity lineIdentity =
                    SpecificationMaterialIdentity.of(
                            line.materialCode(), line.color(), line.unitOfMeasure());
            if (lineIdentity.equals(identity)) {
                return true;
            }
        }
        return false;
    }

    private static CuttingResolution resolveCutting(LinkedHashSet<CuttingPlanId> plans) {
        if (plans.isEmpty()) {
            return new CuttingResolution(CuttingLinkStatus.NONE, null, List.of());
        }
        if (plans.size() == 1) {
            CuttingPlanId only = plans.getFirst();
            return new CuttingResolution(CuttingLinkStatus.SINGLE, only, List.of(only));
        }
        return new CuttingResolution(
                CuttingLinkStatus.MULTIPLE_REFERENCES, null, List.copyOf(plans));
    }

    private record CuttingResolution(
            CuttingLinkStatus status, CuttingPlanId cuttingPlanId, List<CuttingPlanId> references) {}
}
