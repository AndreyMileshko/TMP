package com.tmp.production.application;

import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.application.ConfirmMaterialTransferCommand.CellAllocation;
import com.tmp.production.application.ReleaseMaterialPlanBuilder.PlannedMaterialLine;
import com.tmp.production.application.ReleaseProductsCommand.ItemRelease;
import com.tmp.production.application.ReleaseProductsCommand.MaterialActualUsage;
import com.tmp.production.application.ReleaseProductsResult.PrepareReleasePreview;
import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.MaterialTransferTemplateOptimisticLockException;
import com.tmp.production.domain.MaterialTransferTemplateStatus;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.security.ProductionPermissions;
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
    private final ProductionWarehouseScope warehouseScope;
    private final ProductionLaunchService launchService;
    private final CheckMaterialAvailabilityService checkMaterialAvailabilityService;
    private final MaterialTransferTemplateService transferTemplateService;
    private final ConfirmMaterialTransferService confirmMaterialTransferService;
    private final ConfirmMaterialReceiptService confirmMaterialReceiptService;
    private final ReleaseProductsService releaseProductsService;
    private final CancelOrderProductionService cancelOrderProductionService;
    private final ProductionMaterialTransferRepository materialTransferRepository;

    public DefaultProductionApplicationApi(
            AuthorizationService authorizationService,
            ProductionWarehouseScope warehouseScope,
            ProductionLaunchService launchService,
            CheckMaterialAvailabilityService checkMaterialAvailabilityService,
            MaterialTransferTemplateService transferTemplateService,
            ConfirmMaterialTransferService confirmMaterialTransferService,
            ConfirmMaterialReceiptService confirmMaterialReceiptService,
            ReleaseProductsService releaseProductsService,
            CancelOrderProductionService cancelOrderProductionService,
            ProductionMaterialTransferRepository materialTransferRepository) {
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.warehouseScope = Objects.requireNonNull(warehouseScope, "warehouseScope");
        this.launchService = Objects.requireNonNull(launchService, "launchService");
        this.checkMaterialAvailabilityService =
                Objects.requireNonNull(
                        checkMaterialAvailabilityService, "checkMaterialAvailabilityService");
        this.transferTemplateService =
                Objects.requireNonNull(transferTemplateService, "transferTemplateService");
        this.confirmMaterialTransferService =
                Objects.requireNonNull(
                        confirmMaterialTransferService, "confirmMaterialTransferService");
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
    public WarehouseScopeView warehouseScope() {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        return new WarehouseScopeView(
                warehouseScope.mainWarehouseId(), warehouseScope.productionWarehouseId());
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
    public TransferTemplateView prepareMaterialTransferTemplate(UUID orderId) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(orderId, "orderId");
        return map(transferTemplateService.prepareMaterialTransferTemplate(SourceOrderId.of(orderId)));
    }

    @Override
    public TransferTemplateView changeTransferRequestedQuantity(
            UUID templateId, UUID lineId, BigDecimal quantity, long expectedVersion) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(lineId, "lineId");
        Objects.requireNonNull(quantity, "quantity");
        MaterialTransferTemplateId id = MaterialTransferTemplateId.of(templateId);
        requireExpectedVersion(id, expectedVersion);
        return map(
                transferTemplateService.changeRequestedQuantity(
                        id, MaterialTransferTemplateLineId.of(lineId), quantity));
    }

    @Override
    public TransferTemplateView excludeTransferLine(
            UUID templateId, UUID lineId, long expectedVersion) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(lineId, "lineId");
        MaterialTransferTemplateId id = MaterialTransferTemplateId.of(templateId);
        requireExpectedVersion(id, expectedVersion);
        return map(
                transferTemplateService.excludeLine(id, MaterialTransferTemplateLineId.of(lineId)));
    }

    @Override
    public TransferTemplateView restoreTransferLine(
            UUID templateId, UUID lineId, long expectedVersion) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(lineId, "lineId");
        MaterialTransferTemplateId id = MaterialTransferTemplateId.of(templateId);
        requireExpectedVersion(id, expectedVersion);
        return map(
                transferTemplateService.restoreLine(id, MaterialTransferTemplateLineId.of(lineId)));
    }

    @Override
    public LogicalTransferView confirmMaterialTransferCreate(
            UUID templateId, long expectedVersion, List<TransferCellAllocation> allocations) {
        authorizationService.requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(allocations, "allocations");
        List<CellAllocation> domainAllocations =
                allocations.stream()
                        .map(
                                a ->
                                        new CellAllocation(
                                                MaterialTransferTemplateLineId.of(a.templateLineId()),
                                                a.sourceStorageCellId(),
                                                a.destinationStorageCellId(),
                                                a.quantity()))
                        .toList();
        ProductionMaterialTransfer transfer =
                confirmMaterialTransferService.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                MaterialTransferTemplateId.of(templateId),
                                expectedVersion,
                                domainAllocations));
        return map(transfer);
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

    private void requireExpectedVersion(MaterialTransferTemplateId templateId, long expectedVersion) {
        MaterialTransferTemplate template =
                transferTemplateService
                        .findTemplateById(templateId)
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Material transfer template not found: "
                                                        + templateId));
        if (template.version() != expectedVersion) {
            throw new MaterialTransferTemplateOptimisticLockException(templateId, expectedVersion);
        }
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

    private TransferTemplateView map(MaterialTransferTemplate template) {
        return new TransferTemplateView(
                template.templateId().value(),
                template.sourceOrderId().value(),
                template.sourceWarehouseId(),
                template.destinationWarehouseId(),
                template.createdAt(),
                template.updatedAt(),
                template.version(),
                map(template.status()),
                template.confirmedAt(),
                template.lines().stream().map(this::map).toList());
    }

    private TransferTemplateLineView map(MaterialTransferTemplateLine line) {
        return new TransferTemplateLineView(
                line.lineId().value(),
                line.materialReferenceId().value(),
                line.materialCode(),
                line.materialName() == null ? "" : line.materialName(),
                line.color(),
                line.unitOfMeasure(),
                line.recommendedQuantity(),
                line.requestedQuantity(),
                line.included(),
                map(line.planningSource()),
                line.cuttingPlanId().map(CuttingPlanId::value),
                map(line.cuttingLinkStatus()),
                line.cuttingPlanReferences().stream().map(CuttingPlanId::value).toList(),
                line.sourceOrderItemIds().stream().map(SourceOrderItemId::value).toList(),
                line.requiredQuantity(),
                line.mainWarehouseAvailable(),
                line.productionWarehouseAvailable(),
                line.uncoveredDeficit());
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

    private TransferTemplateStatusView map(MaterialTransferTemplateStatus status) {
        return switch (status) {
            case DRAFT -> TransferTemplateStatusView.DRAFT;
            case CONFIRMED -> TransferTemplateStatusView.CONFIRMED;
        };
    }

    private MaterialPlanningSourceView map(MaterialPlanningSource source) {
        return switch (source) {
            case SPECIFICATION -> MaterialPlanningSourceView.SPECIFICATION;
            case CUTTING_PLAN -> MaterialPlanningSourceView.CUTTING_PLAN;
        };
    }

    private CuttingLinkStatusView map(CuttingLinkStatus status) {
        return switch (status) {
            case NONE -> CuttingLinkStatusView.NONE;
            case SINGLE -> CuttingLinkStatusView.SINGLE;
            case MULTIPLE_REFERENCES -> CuttingLinkStatusView.MULTIPLE_REFERENCES;
        };
    }
}
