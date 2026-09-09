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
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.List;
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
    private final SubmitMaterialRequirementService submitMaterialRequirementService;
    private final ConfirmMaterialReceiptService confirmMaterialReceiptService;
    private final ReleaseProductsService releaseProductsService;
    private final CancelOrderProductionService cancelOrderProductionService;
    private final ProductionMaterialTransferRepository materialTransferRepository;

    public DefaultProductionApplicationApi(
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            ProductionDestinationWarehouse destinationWarehouse,
            ProductionLaunchService launchService,
            CheckMaterialAvailabilityService checkMaterialAvailabilityService,
            MaterialRequirementService materialRequirementService,
            SubmitMaterialRequirementService submitMaterialRequirementService,
            ConfirmMaterialReceiptService confirmMaterialReceiptService,
            ReleaseProductsService releaseProductsService,
            CancelOrderProductionService cancelOrderProductionService,
            ProductionMaterialTransferRepository materialTransferRepository) {
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
        this.materialTransferRepository =
                Objects.requireNonNull(materialTransferRepository, "materialTransferRepository");
    }

    @Override
    public DestinationWarehouseView destinationWarehouse() {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        return new DestinationWarehouseView(destinationWarehouse.productionWarehouseId());
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
            UUID orderId, List<UUID> selectedOrderItemIds) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(selectedOrderItemIds, "selectedOrderItemIds");
        List<SourceOrderItemId> itemIds =
                selectedOrderItemIds.stream().map(SourceOrderItemId::of).toList();
        return map(
                materialRequirementService.prepareMaterialRequirement(
                        SourceOrderId.of(orderId), itemIds));
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

    private MaterialRequirementView map(MaterialRequirement requirement) {
        return new MaterialRequirementView(
                requirement.requirementId().value(),
                requirement.sourceOrderId().value(),
                requirement.destinationWarehouseId(),
                requirement.createdAt(),
                requirement.updatedAt(),
                requirement.version(),
                map(requirement.status()),
                requirement.submittedAt(),
                requirement.submittedBy(),
                requirement.lines().stream().map(this::map).toList());
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
