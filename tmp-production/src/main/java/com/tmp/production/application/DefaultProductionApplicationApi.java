package com.tmp.production.application;

import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.application.ReleaseMaterialPlanBuilder.PlannedMaterialLine;
import com.tmp.production.application.ReleaseProductsCommand.ItemRelease;
import com.tmp.production.application.ReleaseProductsCommand.MaterialActualUsage;
import com.tmp.production.application.ReleaseProductsResult.PrepareReleasePreview;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.OrderQuantityModeSetting;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.ProductionQuantityMode;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.OrderQuantityModeRepository;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default UI-facing Production Application API (Production Spec §18.2).
 *
 * <p>Delegates to mutating application services; maps domain results to public DTOs only.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-injected collaborators for the Application API facade.")
public final class DefaultProductionApplicationApi implements ProductionApplicationApi {

    private final AuthorizationService authorizationService;
    private final AuthenticationService authenticationService;
    private final ProductionDestinationWarehouse destinationWarehouse;
    private final ProductionLaunchService launchService;
    private final CheckMaterialAvailabilityService checkMaterialAvailabilityService;
    private final MaterialRequirementService materialRequirementService;
    private final MaterialRequirementCoverageService materialRequirementCoverageService;
    private final SubmitMaterialRequirementService submitMaterialRequirementService;
    private final ConfirmMaterialReceiptService confirmMaterialReceiptService;
    private final ReleaseProductsService releaseProductsService;
    private final CancelOrderProductionService cancelOrderProductionService;
    private final MaterialReadinessQueryService materialReadinessQueryService;
    private final ProductionMaterialTransferRepository materialTransferRepository;
    private final OrderQuantityModeRepository quantityModeRepository;

    public DefaultProductionApplicationApi(
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            ProductionDestinationWarehouse destinationWarehouse,
            ProductionLaunchService launchService,
            CheckMaterialAvailabilityService checkMaterialAvailabilityService,
            MaterialRequirementService materialRequirementService,
            MaterialRequirementCoverageService materialRequirementCoverageService,
            SubmitMaterialRequirementService submitMaterialRequirementService,
            ConfirmMaterialReceiptService confirmMaterialReceiptService,
            ReleaseProductsService releaseProductsService,
            CancelOrderProductionService cancelOrderProductionService,
            MaterialReadinessQueryService materialReadinessQueryService,
            ProductionMaterialTransferRepository materialTransferRepository,
            OrderQuantityModeRepository quantityModeRepository) {
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.authenticationService =
                Objects.requireNonNull(authenticationService, "authenticationService");
        this.destinationWarehouse =
                Objects.requireNonNull(destinationWarehouse, "destinationWarehouse");
        this.launchService = Objects.requireNonNull(launchService, "launchService");
        this.checkMaterialAvailabilityService =
                Objects.requireNonNull(
                        checkMaterialAvailabilityService, "checkMaterialAvailabilityService");
        this.materialRequirementService =
                Objects.requireNonNull(materialRequirementService, "materialRequirementService");
        this.materialRequirementCoverageService =
                Objects.requireNonNull(
                        materialRequirementCoverageService, "materialRequirementCoverageService");
        this.submitMaterialRequirementService =
                Objects.requireNonNull(
                        submitMaterialRequirementService, "submitMaterialRequirementService");
        this.confirmMaterialReceiptService =
                Objects.requireNonNull(
                        confirmMaterialReceiptService, "confirmMaterialReceiptService");
        this.releaseProductsService =
                Objects.requireNonNull(releaseProductsService, "releaseProductsService");
        this.cancelOrderProductionService =
                Objects.requireNonNull(
                        cancelOrderProductionService, "cancelOrderProductionService");
        this.materialReadinessQueryService =
                Objects.requireNonNull(
                        materialReadinessQueryService, "materialReadinessQueryService");
        this.materialTransferRepository =
                Objects.requireNonNull(materialTransferRepository, "materialTransferRepository");
        this.quantityModeRepository =
                Objects.requireNonNull(quantityModeRepository, "quantityModeRepository");
    }

    @Override
    public DestinationWarehouseView destinationWarehouse() {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        return new DestinationWarehouseView(destinationWarehouse.findProductionWarehouseId());
    }

    @Override
    public void acceptOrderIntoProduction(UUID orderId, String createdBy) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_ACCEPT);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(createdBy, "createdBy");
        launchService.launch(new LaunchProductionCommand(orderId, createdBy));
    }

    @Override
    public void checkMaterialAvailability(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CHECK_MATERIALS);
        Objects.requireNonNull(orderId, "orderId");
        checkMaterialAvailabilityService.check(SourceOrderId.of(orderId));
    }

    @Override
    public MaterialRequirementView prepareMaterialRequirement(
            List<MaterialRequirementProductSelectionView> selections) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(selections, "selections");
        List<MaterialRequirementProductSelection> domainSelections =
                selections.stream().map(this::map).toList();
        return map(materialRequirementService.prepareMaterialRequirement(domainSelections));
    }

    @Override
    public Optional<MaterialRequirementView> getMaterialRequirement(UUID requirementId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(requirementId, "requirementId");
        return materialRequirementService
                .findById(MaterialRequirementId.of(requirementId))
                .map(this::map);
    }

    @Override
    public List<MaterialRequirementDraftSummaryView> listMaterialRequirementDrafts() {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        return materialRequirementService.listDraftsNewestFirst().stream()
                .map(this::mapDraftSummary)
                .toList();
    }

    @Override
    public List<MaterialRequirementProductCoverageView> getMaterialRequirementProductCoverage(
            List<MaterialRequirementSourceItemRefView> sourceItems) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(sourceItems, "sourceItems");
        List<MaterialRequirementSourceItemKey> keys =
                sourceItems.stream()
                        .map(
                                item ->
                                        MaterialRequirementSourceItemKey.of(
                                                SourceOrderId.of(item.sourceOrderId()),
                                                SourceOrderItemId.of(item.sourceOrderItemId())))
                        .toList();
        Map<MaterialRequirementSourceItemKey, MaterialRequirementProductCoverageCalculator.ProductItemCoverage>
                coverage = materialRequirementCoverageService.coverageForItems(keys);
        List<MaterialRequirementProductCoverageView> views = new java.util.ArrayList<>();
        for (MaterialRequirementSourceItemKey key : keys) {
            var item = coverage.get(key);
            views.add(
                    new MaterialRequirementProductCoverageView(
                            key.sourceOrderId().value(),
                            key.sourceOrderItemId().value(),
                            item.orderedQuantity(),
                            item.activeProductionQuantity(),
                            item.releasedQuantity(),
                            item.submittedProductCoverage(),
                            item.outstandingSubmittedCoverage(),
                            item.requestableProductQuantity()));
        }
        return List.copyOf(views);
    }

    @Override
    public MaterialRequirementView changeMaterialRequirementQuantity(
            UUID requirementId, UUID lineId, BigDecimal quantity, long expectedVersion) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(quantity, "quantity");
        return map(
                materialRequirementService.changeQuantity(
                        MaterialRequirementId.of(requirementId),
                        MaterialRequirementLineId.of(lineId),
                        quantity,
                        expectedVersion));
    }

    @Override
    public SubmitMaterialRequirementResultView submitMaterialRequirement(
            UUID requirementId, long expectedVersion) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(requirementId, "requirementId");
        String submittedBy = currentUserRef();
        SubmitMaterialRequirementResult result =
                submitMaterialRequirementService.submit(
                        MaterialRequirementId.of(requirementId), expectedVersion, submittedBy);
        MaterialRequirement requirement = result.requirement();
        List<GeneratedTransferDocumentView> documents =
                result.documents().stream()
                        .map(this::map)
                        .toList();
        return new SubmitMaterialRequirementResultView(
                requirement.requirementId().value(),
                requirement.version(),
                map(requirement.status()),
                result.created(),
                documents);
    }

    private String currentUserRef() {
        return authenticationService
                .currentSession()
                .orElseThrow(
                        () ->
                                new AccessDeniedException(
                                        "Access denied: authentication required"))
                .userId()
                .value()
                .toString();
    }

    private GeneratedTransferDocumentView map(GeneratedDocumentLink link) {
        return new GeneratedTransferDocumentView(
                link.warehouseDocumentId(),
                link.sourceWarehouseId(),
                link.destinationWarehouseId());
    }

    @Override
    public List<LogicalTransferView> listLogicalTransfers(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderId, "orderId");
        return materialTransferRepository.findBySourceOrderId(SourceOrderId.of(orderId)).stream()
                .map(this::map)
                .toList();
    }

    @Override
    public ReceiptResultView confirmMaterialReceipt(UUID logicalTransferId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CONFIRM_RECEIPT);
        Objects.requireNonNull(logicalTransferId, "logicalTransferId");
        MaterialReceiptConfirmationResult result =
                confirmMaterialReceiptService.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(
                                ProductionMaterialTransferId.of(logicalTransferId)));
        return switch (result.status()) {
            case RECEIVED ->
                    new ReceiptResultView(
                            ReceiptStatusView.RECEIVED, "Material receipt confirmed");
            case ALREADY_RECEIVED ->
                    new ReceiptResultView(
                            ReceiptStatusView.ALREADY_RECEIVED,
                            "Material receipt was already confirmed");
        };
    }

    @Override
    public ReleasePreviewView prepareRelease(UUID orderId, List<ItemReleaseView> itemReleases) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_RELEASE);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(itemReleases, "itemReleases");
        PrepareReleasePreview preview =
                releaseProductsService.prepareRelease(
                        new PrepareReleaseCommand(orderId, mapItemReleases(itemReleases)));
        return map(preview);
    }

    @Override
    public ReleaseResultView releaseProducts(
            UUID orderId,
            List<ItemReleaseView> itemReleases,
            List<MaterialActualUsageView> materialActualUsages) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_RELEASE);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(itemReleases, "itemReleases");
        Objects.requireNonNull(materialActualUsages, "materialActualUsages");
        ReleaseProductsResult result =
                releaseProductsService.releaseProducts(
                        new ReleaseProductsCommand(
                                orderId,
                                mapItemReleases(itemReleases),
                                mapMaterialActualUsages(materialActualUsages)));
        return new ReleaseResultView(
                result.documentId(), result.sourceOrderId(), result.releasedAt());
    }

    @Override
    public void cancelOrderProduction(UUID orderId, Optional<String> reason) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CANCEL);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(reason, "reason");
        cancelOrderProductionService.cancelOrderProduction(
                new CancelOrderProductionCommand(orderId, reason));
    }

    @Override
    public OrderQuantityModeView getOrderQuantityMode(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderId, "orderId");
        SourceOrderId sourceOrderId = SourceOrderId.of(orderId);
        return map(
                quantityModeRepository
                        .findBySourceOrderId(sourceOrderId)
                        .orElseGet(() -> OrderQuantityModeSetting.defaultFor(sourceOrderId)));
    }

    @Override
    public List<OrderQuantityModeView> getOrderQuantityModes(List<UUID> orderIds) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderIds, "orderIds");
        List<SourceOrderId> unique =
                orderIds.stream()
                        .map(
                                id -> {
                                    Objects.requireNonNull(id, "orderId");
                                    return SourceOrderId.of(id);
                                })
                        .distinct()
                        .toList();
        Map<SourceOrderId, OrderQuantityModeSetting> stored =
                quantityModeRepository.findBySourceOrderIds(unique);
        List<OrderQuantityModeView> views = new java.util.ArrayList<>(unique.size());
        for (SourceOrderId orderId : unique) {
            views.add(
                    map(
                            stored.getOrDefault(
                                    orderId, OrderQuantityModeSetting.defaultFor(orderId))));
        }
        return List.copyOf(views);
    }

    @Override
    public OrderQuantityModeView changeOrderQuantityMode(
            UUID orderId, QuantityModeView quantityMode, long expectedVersion) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_ACCEPT);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(quantityMode, "quantityMode");
        return map(
                quantityModeRepository.save(
                        SourceOrderId.of(orderId), map(quantityMode), expectedVersion));
    }

    @Override
    public MaterialReadinessView getOrderRemainingMaterialReadiness(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderId, "orderId");
        return map(materialReadinessQueryService.evaluateOrderRemaining(SourceOrderId.of(orderId)));
    }

    @Override
    public MaterialReadinessView getMaterialReadinessForRelease(
            UUID orderId, List<ItemReleaseView> itemReleases) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(itemReleases, "itemReleases");
        List<MaterialReadinessQueryService.ItemReleaseQuantity> quantities =
                itemReleases.stream()
                        .map(
                                item ->
                                        new MaterialReadinessQueryService.ItemReleaseQuantity(
                                                item.sourceOrderItemId(), item.releaseQuantity()))
                        .toList();
        return map(
                materialReadinessQueryService.evaluateForRelease(
                        SourceOrderId.of(orderId), quantities));
    }

    private MaterialReadinessView map(MaterialReadinessResult result) {
        return new MaterialReadinessView(
                map(result.status()),
                map(result.reason()),
                result.deficientLineCount(),
                result.lines().stream().map(this::map).toList());
    }

    private MaterialReadinessStatusView map(MaterialReadinessResult.MaterialReadinessStatus status) {
        return switch (status) {
            case READY -> MaterialReadinessStatusView.READY;
            case NOT_READY -> MaterialReadinessStatusView.NOT_READY;
            case NO_PRODUCTION_WAREHOUSE -> MaterialReadinessStatusView.NO_PRODUCTION_WAREHOUSE;
            case NOT_APPLICABLE -> MaterialReadinessStatusView.NOT_APPLICABLE;
            case MATERIAL_REFERENCE_UNRESOLVED ->
                    MaterialReadinessStatusView.MATERIAL_REFERENCE_UNRESOLVED;
        };
    }

    private MaterialReadinessReasonView map(MaterialReadinessResult.MaterialReadinessReason reason) {
        return switch (reason) {
            case NONE -> MaterialReadinessReasonView.NONE;
            case NOT_ACCEPTED -> MaterialReadinessReasonView.NOT_ACCEPTED;
            case MANUFACTURED -> MaterialReadinessReasonView.MANUFACTURED;
            case CANCELLED -> MaterialReadinessReasonView.CANCELLED;
            case NO_RELEASABLE_QUANTITY -> MaterialReadinessReasonView.NO_RELEASABLE_QUANTITY;
            case INSUFFICIENT_STOCK -> MaterialReadinessReasonView.INSUFFICIENT_STOCK;
            case NO_PRODUCTION_WAREHOUSE -> MaterialReadinessReasonView.NO_PRODUCTION_WAREHOUSE;
            case MATERIAL_REFERENCE_UNRESOLVED ->
                    MaterialReadinessReasonView.MATERIAL_REFERENCE_UNRESOLVED;
        };
    }

    private MaterialReadinessLineView map(MaterialReadinessResult.MaterialReadinessLine line) {
        return new MaterialReadinessLineView(
                line.materialReferenceId(),
                line.materialCode(),
                line.materialName(),
                line.color(),
                line.unitOfMeasure(),
                line.requiredQuantity(),
                line.availableQuantity(),
                line.shortageQuantity());
    }

    private OrderQuantityModeView map(OrderQuantityModeSetting setting) {
        QuantityModeView mode =
                switch (setting.quantityMode()) {
                    case STANDARD -> QuantityModeView.STANDARD;
                    case FLEXIBLE -> QuantityModeView.FLEXIBLE;
                };
        return new OrderQuantityModeView(
                setting.sourceOrderId().value(), mode, setting.version());
    }

    private ProductionQuantityMode map(QuantityModeView mode) {
        return switch (mode) {
            case STANDARD -> ProductionQuantityMode.STANDARD;
            case FLEXIBLE -> ProductionQuantityMode.FLEXIBLE;
        };
    }

    private List<ItemRelease> mapItemReleases(List<ItemReleaseView> itemReleases) {
        return itemReleases.stream()
                .map(i -> new ItemRelease(i.sourceOrderItemId(), i.releaseQuantity()))
                .toList();
    }

    private List<MaterialActualUsage> mapMaterialActualUsages(
            List<MaterialActualUsageView> usages) {
        return usages.stream()
                .map(
                        u ->
                                new MaterialActualUsage(
                                        u.sourceOrderItemId(),
                                        u.materialReferenceId(),
                                        u.actualQuantity(),
                                        u.allocations().stream()
                                                .map(
                                                        a ->
                                                                new ReleaseProductsCommand
                                                                        .CellAllocation(
                                                                        a.storageCellId(),
                                                                        a.quantity()))
                                                .toList()))
                .toList();
    }

    private MaterialRequirementProductSelection map(
            MaterialRequirementProductSelectionView selection) {
        if (selection.requestedProductQuantity().isPresent()) {
            return MaterialRequirementProductSelection.of(
                    SourceOrderId.of(selection.sourceOrderId()),
                    SourceOrderItemId.of(selection.sourceOrderItemId()),
                    selection.requestedProductQuantity().orElseThrow());
        }
        return MaterialRequirementProductSelection.of(
                SourceOrderId.of(selection.sourceOrderId()),
                SourceOrderItemId.of(selection.sourceOrderItemId()));
    }

    private MaterialRequirementView map(MaterialRequirement requirement) {
        return new MaterialRequirementView(
                requirement.requirementId().value(),
                requirement.sourceItems().stream().map(this::map).toList(),
                requirement.destinationWarehouseId(),
                requirement.createdAt(),
                requirement.updatedAt(),
                requirement.version(),
                map(requirement.status()),
                requirement.submittedAt(),
                requirement.submittedBy(),
                requirement.lines().stream().map(this::map).toList());
    }

    private MaterialRequirementDraftSummaryView mapDraftSummary(MaterialRequirement requirement) {
        long orderCount =
                requirement.sourceItems().stream()
                        .map(MaterialRequirementSourceItem::sourceOrderId)
                        .distinct()
                        .count();
        return new MaterialRequirementDraftSummaryView(
                requirement.requirementId().value(),
                requirement.createdAt(),
                requirement.sourceItems().size(),
                Math.toIntExact(orderCount));
    }

    private MaterialRequirementSourceItemView map(MaterialRequirementSourceItem item) {
        return new MaterialRequirementSourceItemView(
                item.sourceOrderId().value(),
                item.sourceOrderItemId().value(),
                item.requestedProductQuantity());
    }

    private MaterialRequirementLineView map(MaterialRequirementLine line) {
        return new MaterialRequirementLineView(
                line.lineId().value(),
                line.materialReferenceId().value(),
                line.materialCode(),
                line.materialName() == null ? "" : line.materialName(),
                line.color(),
                line.unitOfMeasure(),
                line.quantity(),
                line.sourceOrderItemIds().stream().map(SourceOrderItemId::value).toList());
    }

    private LogicalTransferView map(ProductionMaterialTransfer transfer) {
        return new LogicalTransferView(
                transfer.logicalTransferId().value(),
                transfer.templateId().value(),
                transfer.createdAt(),
                transfer.warehouseOperationRefs().stream()
                        .map(
                                ref ->
                                        new WarehouseTransferRefView(
                                                ref.warehouseDraftOperationId(),
                                                ref.materialReferenceId().value(),
                                                ref.quantity()))
                        .toList());
    }

    private ReleasePreviewView map(PrepareReleasePreview preview) {
        return new ReleasePreviewView(
                preview.sourceOrderId(),
                preview.itemReleases().stream()
                        .map(i -> new ItemReleaseView(i.sourceOrderItemId(), i.releaseQuantity()))
                        .toList(),
                preview.plannedMaterialLines().stream().map(this::map).toList(),
                preview.defaultActuals().stream()
                        .map(
                                a ->
                                        new MaterialActualDefaultView(
                                                a.sourceOrderItemId(),
                                                a.materialReferenceId(),
                                                a.plannedQuantity(),
                                                a.actualQuantity()))
                        .toList());
    }

    private PlannedMaterialLineView map(PlannedMaterialLine line) {
        return new PlannedMaterialLineView(
                line.sourceOrderItemId().value(),
                line.materialReferenceId().value(),
                line.specificationId().value(),
                line.plannedQuantity(),
                map(line.planningSource()),
                line.cuttingPlanId(),
                Optional.ofNullable(line.materialName()));
    }

    private MaterialRequirementStatusView map(MaterialRequirementStatus status) {
        return switch (status) {
            case DRAFT -> MaterialRequirementStatusView.DRAFT;
            case SUBMITTED -> MaterialRequirementStatusView.SUBMITTED;
        };
    }

    private MaterialPlanningSourceView map(MaterialPlanningSource source) {
        return switch (source) {
            case SPECIFICATION -> MaterialPlanningSourceView.SPECIFICATION;
            case CUTTING_PLAN -> MaterialPlanningSourceView.CUTTING_PLAN;
        };
    }
}
