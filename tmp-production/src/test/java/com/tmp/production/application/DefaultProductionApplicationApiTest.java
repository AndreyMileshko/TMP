package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateStatusView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateView;
import com.tmp.production.application.MaterialReceiptConfirmationResult.MaterialReceiptConfirmationStatus;
import com.tmp.production.application.ReleaseProductsResult.ItemResult;
import com.tmp.production.application.ReleaseProductsResult.MaterialResult;
import com.tmp.production.application.ReleaseProductsResult.PrepareReleasePreview;
import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.MaterialTransferTemplateOptimisticLockException;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AuthorizationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DefaultProductionApplicationApiTest {

    private static final UUID MAIN_WH = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID PROD_WH = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");

    private AuthorizationService authorizationService;
    private ProductionLaunchService launchService;
    private CheckMaterialAvailabilityService checkMaterialAvailabilityService;
    private MaterialTransferTemplateService transferTemplateService;
    private ConfirmMaterialTransferService confirmMaterialTransferService;
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
        transferTemplateService = mock(MaterialTransferTemplateService.class);
        confirmMaterialTransferService = mock(ConfirmMaterialTransferService.class);
        confirmMaterialReceiptService = mock(ConfirmMaterialReceiptService.class);
        releaseProductsService = mock(ReleaseProductsService.class);
        cancelOrderProductionService = mock(CancelOrderProductionService.class);
        materialTransferRepository = mock(ProductionMaterialTransferRepository.class);
        api =
                new DefaultProductionApplicationApi(
                        authorizationService,
                        new ProductionWarehouseScope(MAIN_WH, PROD_WH),
                        launchService,
                        checkMaterialAvailabilityService,
                        transferTemplateService,
                        confirmMaterialTransferService,
                        confirmMaterialReceiptService,
                        releaseProductsService,
                        cancelOrderProductionService,
                        materialTransferRepository);
    }

    @Test
    void prepareMaterialTransferTemplateMapsDomainToDto() {
        MaterialTransferTemplate template = sampleTemplate();
        when(transferTemplateService.prepareMaterialTransferTemplate(any())).thenReturn(template);

        TransferTemplateView view =
                api.prepareMaterialTransferTemplate(template.sourceOrderId().value());

        verify(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        assertEquals(template.templateId().value(), view.templateId());
        assertEquals(template.sourceOrderId().value(), view.sourceOrderId());
        assertEquals(TransferTemplateStatusView.DRAFT, view.status());
        assertEquals(1, view.lines().size());
        assertEquals(
                template.lines().getFirst().lineId().value(), view.lines().getFirst().lineId());
        assertEquals(
                template.lines().getFirst().requestedQuantity(),
                view.lines().getFirst().requestedQuantity());
    }

    @Test
    void changeTransferRequestedQuantityRejectsStaleVersion() {
        MaterialTransferTemplate template = sampleTemplate();
        when(transferTemplateService.findTemplateById(template.templateId()))
                .thenReturn(Optional.of(template));

        assertThrows(
                MaterialTransferTemplateOptimisticLockException.class,
                () ->
                        api.changeTransferRequestedQuantity(
                                template.templateId().value(),
                                template.lines().getFirst().lineId().value(),
                                BigDecimal.TEN,
                                template.version() + 1));
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

    private static MaterialTransferTemplate sampleTemplate() {
        UUID main = UUID.fromString("11111111-1111-4111-8111-111111111111");
        UUID production = UUID.fromString("22222222-2222-4222-8222-222222222222");
        MaterialTransferTemplateLine line =
                MaterialTransferTemplateLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-1",
                        "Material 1",
                        "RED",
                        "m",
                        new BigDecimal("5.000"),
                        MaterialPlanningSource.SPECIFICATION,
                        null,
                        CuttingLinkStatus.NONE,
                        List.of(),
                        Set.of(SourceOrderItemId.generate()),
                        new BigDecimal("8.000"),
                        new BigDecimal("10.000"),
                        BigDecimal.ZERO,
                        new BigDecimal("3.000"));
        return MaterialTransferTemplate.create(
                SourceOrderId.generate(),
                main,
                production,
                Instant.parse("2026-08-20T09:00:00Z"),
                List.of(line));
    }
}
