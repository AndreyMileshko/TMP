package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.application.MaterialReceiptConfirmationResult.MaterialReceiptConfirmationStatus;
import com.tmp.production.application.ReleaseProductsResult.ItemResult;
import com.tmp.production.application.ReleaseProductsResult.MaterialResult;
import com.tmp.production.application.ReleaseProductsResult.PrepareReleasePreview;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AuthorizationService;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultProductionApplicationApiTest {

    private static final UUID PROD_WH = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    private AuthorizationService authorizationService;
    private ProductionLaunchService launchService;
    private CheckMaterialAvailabilityService checkMaterialAvailabilityService;
    private MaterialRequirementService materialRequirementService;
    private ConfirmMaterialReceiptService confirmMaterialReceiptService;
    private ReleaseProductsService releaseProductsService;
    private CancelOrderProductionService cancelOrderProductionService;
    private ProductionMaterialTransferRepository materialTransferRepository;
    private DefaultProductionApplicationApi api;

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationService.class);
        launchService = mock(ProductionLaunchService.class);
        checkMaterialAvailabilityService = mock(CheckMaterialAvailabilityService.class);
        materialRequirementService = mock(MaterialRequirementService.class);
        confirmMaterialReceiptService = mock(ConfirmMaterialReceiptService.class);
        releaseProductsService = mock(ReleaseProductsService.class);
        cancelOrderProductionService = mock(CancelOrderProductionService.class);
        materialTransferRepository = mock(ProductionMaterialTransferRepository.class);
        api =
                new DefaultProductionApplicationApi(
                        authorizationService,
                        new ProductionDestinationWarehouse(PROD_WH),
                        launchService,
                        checkMaterialAvailabilityService,
                        materialRequirementService,
                        confirmMaterialReceiptService,
                        releaseProductsService,
                        cancelOrderProductionService,
                        materialTransferRepository);
    }

    @Test
    void prepareMaterialRequirementMapsDomainToDto() {
        MaterialRequirement requirement = sampleRequirement();
        when(materialRequirementService.prepareMaterialRequirement(any(), any()))
                .thenReturn(requirement);

        MaterialRequirementView view =
                api.prepareMaterialRequirement(
                        requirement.sourceOrderId().value(),
                        requirement.lines().getFirst().sourceOrderItemIds().stream()
                                .map(SourceOrderItemId::value)
                                .toList());

        verify(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        assertEquals(requirement.requirementId().value(), view.requirementId());
        assertEquals(requirement.sourceOrderId().value(), view.sourceOrderId());
        assertEquals(MaterialRequirementStatusView.DRAFT, view.status());
        assertEquals(1, view.lines().size());
        assertEquals(
                requirement.lines().getFirst().lineId().value(),
                view.lines().getFirst().lineId());
        assertEquals(
                requirement.lines().getFirst().quantity(), view.lines().getFirst().quantity());
    }

    @Test
    void changeMaterialRequirementQuantityDelegatesExpectedVersion() {
        MaterialRequirement requirement = sampleRequirement();
        when(materialRequirementService.changeQuantity(
                        eq(requirement.requirementId()),
                        eq(requirement.lines().getFirst().lineId()),
                        eq(BigDecimal.TEN),
                        eq(requirement.version())))
                .thenReturn(requirement);

        MaterialRequirementView view =
                api.changeMaterialRequirementQuantity(
                        requirement.requirementId().value(),
                        requirement.lines().getFirst().lineId().value(),
                        BigDecimal.TEN,
                        requirement.version());

        verify(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        verify(materialRequirementService)
                .changeQuantity(
                        requirement.requirementId(),
                        requirement.lines().getFirst().lineId(),
                        BigDecimal.TEN,
                        requirement.version());
        assertEquals(requirement.requirementId().value(), view.requirementId());
    }

    @Test
    void changeMaterialRequirementQuantityPropagatesOptimisticLock() {
        MaterialRequirement requirement = sampleRequirement();
        when(materialRequirementService.changeQuantity(any(), any(), any(), anyLong()))
                .thenThrow(
                        new MaterialRequirementOptimisticLockException(
                                requirement.requirementId(), requirement.version() + 1));

        assertThrows(
                MaterialRequirementOptimisticLockException.class,
                () ->
                        api.changeMaterialRequirementQuantity(
                                requirement.requirementId().value(),
                                requirement.lines().getFirst().lineId().value(),
                                BigDecimal.TEN,
                                requirement.version() + 1));
    }

    @Test
    void materialRequirementLineViewHasOnlyExpectedComponents() {
        Set<String> names =
                Arrays.stream(MaterialRequirementLineView.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(
                Set.of(
                        "lineId",
                        "materialReferenceId",
                        "materialCode",
                        "materialName",
                        "color",
                        "unitOfMeasure",
                        "quantity",
                        "sourceOrderItemIds"),
                names);
        assertFalse(names.contains("recommendedQuantity"));
        assertFalse(names.contains("requestedQuantity"));
        assertFalse(names.contains("mainWarehouseAvailable"));
        assertFalse(names.contains("productionWarehouseAvailable"));
        assertFalse(names.contains("uncoveredDeficit"));
        assertFalse(names.contains("sourceWarehouseId"));
        assertFalse(names.contains("mainWarehouseId"));
    }

    @Test
    void listLogicalTransfersMapsRepositoryResults() {
        SourceOrderId orderId = SourceOrderId.generate();
        Instant createdAt = Instant.parse("2026-08-20T10:00:00Z");
        ProductionMaterialTransfer transfer =
                ProductionMaterialTransfer.rehydrate(
                        ProductionMaterialTransferId.generate(),
                        MaterialTransferTemplateId.generate(),
                        orderId,
                        createdAt,
                        List.of(
                                new WarehouseTransferOperationRef(
                                        MaterialTransferTemplateLineId.of(UUID.randomUUID()),
                                        UUID.randomUUID(),
                                        MaterialReferenceId.generate(),
                                        BigDecimal.ONE,
                                        UUID.randomUUID(),
                                        UUID.randomUUID())));
        when(materialTransferRepository.findBySourceOrderId(orderId)).thenReturn(List.of(transfer));

        List<LogicalTransferView> views = api.listLogicalTransfers(orderId.value());

        verify(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        assertEquals(1, views.size());
        assertEquals(transfer.logicalTransferId().value(), views.getFirst().id());
        assertEquals(transfer.templateId().value(), views.getFirst().templateId());
        assertEquals(createdAt, views.getFirst().createdAt());
        assertEquals(1, views.getFirst().warehouseOperations().size());
        assertEquals(
                transfer.warehouseOperationRefs().getFirst().warehouseDraftOperationId(),
                views.getFirst().warehouseOperations().getFirst().warehouseDraftOperationId());
    }

    @Test
    void confirmMaterialReceiptMapsStatusAndMessage() {
        UUID logicalId = UUID.randomUUID();
        MaterialReceiptConfirmationResult result =
                new MaterialReceiptConfirmationResult(
                        ProductionMaterialTransferId.of(logicalId),
                        SourceOrderId.generate(),
                        Instant.parse("2026-08-20T11:00:00Z"),
                        MaterialReceiptConfirmationStatus.ALREADY_RECEIVED,
                        List.of(
                                new MaterialReceiptConfirmationResult.MaterialReceiptReferenceResult(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        MaterialReferenceId.generate(),
                                        BigDecimal.ONE,
                                        "RECEIVED")));
        when(confirmMaterialReceiptService.confirmMaterialReceipt(any())).thenReturn(result);

        ReceiptResultView view = api.confirmMaterialReceipt(logicalId);

        verify(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_CONFIRM_RECEIPT);
        assertEquals(ReceiptStatusView.ALREADY_RECEIVED, view.status());
        assertTrue(view.message().toLowerCase().contains("already"));
    }

    @Test
    void prepareReleaseMapsPreviewDto() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID materialId = UUID.randomUUID();
        PrepareReleasePreview preview =
                new PrepareReleasePreview(
                        orderId,
                        List.of(new ItemResult(itemId, 2L)),
                        List.of(),
                        List.of(
                                new MaterialResult(
                                        itemId, materialId, BigDecimal.TEN, BigDecimal.TEN)));
        when(releaseProductsService.prepareRelease(any())).thenReturn(preview);

        ReleasePreviewView view =
                api.prepareRelease(
                        orderId,
                        List.of(new ProductionApplicationApi.ItemReleaseView(itemId, 2L)));

        verify(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_RELEASE);
        assertEquals(orderId, view.sourceOrderId());
        assertEquals(1, view.itemReleases().size());
        assertEquals(itemId, view.itemReleases().getFirst().sourceOrderItemId());
        assertEquals(2L, view.itemReleases().getFirst().releaseQuantity());
        assertEquals(1, view.defaultActuals().size());
        assertEquals(BigDecimal.TEN, view.defaultActuals().getFirst().actualQuantity());
    }

    @Test
    void cancelOrderProductionDelegatesWithOptionalReason() {
        UUID orderId = UUID.randomUUID();
        ArgumentCaptor<CancelOrderProductionCommand> captor =
                ArgumentCaptor.forClass(CancelOrderProductionCommand.class);

        api.cancelOrderProduction(orderId, Optional.of(" stop "));

        verify(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_CANCEL);
        verify(cancelOrderProductionService).cancelOrderProduction(captor.capture());
        assertEquals(orderId, captor.getValue().sourceOrderId());
        assertEquals(Optional.of("stop"), captor.getValue().reason());
    }

    private static MaterialRequirement sampleRequirement() {
        MaterialRequirementLine line =
                MaterialRequirementLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-1",
                        "Material 1",
                        "RED",
                        "m",
                        new BigDecimal("5.000"),
                        Set.of(SourceOrderItemId.generate()));
        return MaterialRequirement.create(
                SourceOrderId.generate(),
                PROD_WH,
                Instant.parse("2026-08-20T09:00:00Z"),
                List.of(line));
    }
}
