package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.OrderQuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.SubmitMaterialRequirementResultView;
import com.tmp.production.application.MaterialReceiptConfirmationResult.MaterialReceiptConfirmationStatus;
import com.tmp.production.application.ReleaseProductsResult.ItemResult;
import com.tmp.production.application.ReleaseProductsResult.MaterialResult;
import com.tmp.production.application.ReleaseProductsResult.PrepareReleasePreview;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.OrderQuantityModeOptimisticLockException;
import com.tmp.production.domain.OrderQuantityModeSetting;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.ProductionQuantityMode;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository.GeneratedDocumentLink;
import com.tmp.production.domain.repository.OrderQuantityModeRepository;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.Login;
import com.tmp.security.api.SessionId;
import com.tmp.security.api.SessionSummary;
import com.tmp.security.api.UserId;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private AuthenticationService authenticationService;
    private ProductionLaunchService launchService;
    private CheckMaterialAvailabilityService checkMaterialAvailabilityService;
    private MaterialRequirementService materialRequirementService;
    private MaterialRequirementCoverageService materialRequirementCoverageService;
    private SubmitMaterialRequirementService submitMaterialRequirementService;
    private ConfirmMaterialReceiptService confirmMaterialReceiptService;
    private ReleaseProductsService releaseProductsService;
    private CancelOrderProductionService cancelOrderProductionService;
    private ProductionMaterialTransferRepository materialTransferRepository;
    private OrderQuantityModeRepository quantityModeRepository;
    private DefaultProductionApplicationApi api;

    @BeforeEach
    void setUp() {
        authorizationService = mock(AuthorizationService.class);
        authenticationService = mock(AuthenticationService.class);
        when(authenticationService.currentSession())
                .thenReturn(
                        Optional.of(
                                new SessionSummary(
                                        SessionId.of(UUID.randomUUID()),
                                        UserId.of(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")),
                                        Login.of("master"),
                                        Instant.parse("2026-09-10T00:00:00Z"))));
        launchService = mock(ProductionLaunchService.class);
        checkMaterialAvailabilityService = mock(CheckMaterialAvailabilityService.class);
        materialRequirementService = mock(MaterialRequirementService.class);
        materialRequirementCoverageService = mock(MaterialRequirementCoverageService.class);
        submitMaterialRequirementService = mock(SubmitMaterialRequirementService.class);
        confirmMaterialReceiptService = mock(ConfirmMaterialReceiptService.class);
        releaseProductsService = mock(ReleaseProductsService.class);
        cancelOrderProductionService = mock(CancelOrderProductionService.class);
        materialTransferRepository = mock(ProductionMaterialTransferRepository.class);
        quantityModeRepository = mock(OrderQuantityModeRepository.class);
        api =
                new DefaultProductionApplicationApi(
                        authorizationService,
                        authenticationService,
                        new ProductionDestinationWarehouse(PROD_WH),
                        launchService,
                        checkMaterialAvailabilityService,
                        materialRequirementService,
                        materialRequirementCoverageService,
                        submitMaterialRequirementService,
                        confirmMaterialReceiptService,
                        releaseProductsService,
                        cancelOrderProductionService,
                        materialTransferRepository,
                        quantityModeRepository);
    }

    @Test
    void getOrderQuantityModeDefaultsToStandardWhenNothingStored() {
        UUID orderId = UUID.randomUUID();
        when(quantityModeRepository.findBySourceOrderId(SourceOrderId.of(orderId)))
                .thenReturn(Optional.empty());

        OrderQuantityModeView view = api.getOrderQuantityMode(orderId);

        verify(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        assertEquals(orderId, view.orderId());
        assertEquals(QuantityModeView.STANDARD, view.quantityMode());
        assertEquals(0L, view.version());
    }

    @Test
    void getOrderQuantityModeReturnsStoredSetting() {
        UUID orderId = UUID.randomUUID();
        when(quantityModeRepository.findBySourceOrderId(SourceOrderId.of(orderId)))
                .thenReturn(
                        Optional.of(
                                new OrderQuantityModeSetting(
                                        SourceOrderId.of(orderId),
                                        ProductionQuantityMode.FLEXIBLE,
                                        3L)));

        OrderQuantityModeView view = api.getOrderQuantityMode(orderId);

        assertEquals(QuantityModeView.FLEXIBLE, view.quantityMode());
        assertEquals(3L, view.version());
    }

    @Test
    void changeOrderQuantityModeRequiresAcceptPermissionAndReturnsSavedState() {
        UUID orderId = UUID.randomUUID();
        when(quantityModeRepository.save(
                        SourceOrderId.of(orderId), ProductionQuantityMode.FLEXIBLE, 0L))
                .thenReturn(
                        new OrderQuantityModeSetting(
                                SourceOrderId.of(orderId), ProductionQuantityMode.FLEXIBLE, 1L));

        OrderQuantityModeView view =
                api.changeOrderQuantityMode(orderId, QuantityModeView.FLEXIBLE, 0L);

        verify(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_ACCEPT);
        assertEquals(QuantityModeView.FLEXIBLE, view.quantityMode());
        assertEquals(1L, view.version());
    }

    @Test
    void changeOrderQuantityModeWithoutAcceptPermissionIsDeniedBeforePersistence() {
        doThrow(new AccessDeniedException("Access denied: production.order.accept"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_ACCEPT);

        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.changeOrderQuantityMode(
                                UUID.randomUUID(), QuantityModeView.FLEXIBLE, 0L));

        verify(quantityModeRepository, never()).save(any(), any(), anyLong());
    }

    @Test
    void releaseOrTransferPermissionDoesNotGrantQuantityModeChange() {
        doThrow(new AccessDeniedException("Access denied: production.order.accept"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_ACCEPT);

        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.changeOrderQuantityMode(
                                UUID.randomUUID(), QuantityModeView.STANDARD, 1L));

        verify(authorizationService, never())
                .requirePermission(ProductionPermissions.PRODUCTION_RELEASE);
        verify(authorizationService, never())
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        verify(quantityModeRepository, never()).save(any(), any(), anyLong());
    }

    @Test
    void changeOrderQuantityModePropagatesOptimisticLock() {
        UUID orderId = UUID.randomUUID();
        when(quantityModeRepository.save(any(), any(), anyLong()))
                .thenThrow(
                        new OrderQuantityModeOptimisticLockException(
                                SourceOrderId.of(orderId), 1L));

        OrderQuantityModeOptimisticLockException ex =
                assertThrows(
                        OrderQuantityModeOptimisticLockException.class,
                        () -> api.changeOrderQuantityMode(orderId, QuantityModeView.STANDARD, 1L));
        assertTrue(ex.getMessage().contains("another user"));
    }

    @Test
    void orderQuantityModeViewExposesOnlyOrderIdModeAndVersion() {
        Set<String> names =
                Arrays.stream(OrderQuantityModeView.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(Set.of("orderId", "quantityMode", "version"), names);
    }

    @Test
    void prepareMaterialRequirementMapsDomainToDto() {
        MaterialRequirement requirement = sampleRequirement();
        when(materialRequirementService.prepareMaterialRequirement(any(), any()))
                .thenReturn(requirement);

        MaterialRequirementView view =
                api.prepareMaterialRequirement(
                        requirement.sourceItems().getFirst().sourceOrderId().value(),
                        requirement.lines().getFirst().sourceOrderItemIds().stream()
                                .map(SourceOrderItemId::value)
                                .toList());

        verify(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        assertEquals(requirement.requirementId().value(), view.requirementId());
        assertEquals(1, view.sourceItems().size());
        assertEquals(
                requirement.sourceItems().getFirst().sourceOrderId().value(),
                view.sourceItems().getFirst().sourceOrderId());
        assertEquals(MaterialRequirementStatusView.DRAFT, view.status());
        assertEquals(1, view.lines().size());
        assertEquals(
                requirement.lines().getFirst().lineId().value(),
                view.lines().getFirst().lineId());
        assertEquals(
                requirement.lines().getFirst().quantity(), view.lines().getFirst().quantity());
        assertTrue(view.submittedAt().isEmpty());
        assertTrue(view.submittedBy().isEmpty());
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
    void submitMaterialRequirementUsesAuthenticatedUserAndCreateTransferPermission() {
        MaterialRequirement requirement =
                sampleRequirement().submit("user-1", Instant.parse("2026-09-10T01:00:00Z"));
        UUID documentId = UUID.randomUUID();
        UUID sourceWarehouseId = UUID.randomUUID();
        when(submitMaterialRequirementService.submit(any(), anyLong(), any()))
                .thenReturn(
                        new SubmitMaterialRequirementResult(
                                requirement,
                                List.of(
                                        new GeneratedDocumentLink(
                                                documentId,
                                                sourceWarehouseId,
                                                PROD_WH,
                                                1,
                                                Instant.parse("2026-09-10T01:00:00Z"))),
                                List.of(),
                                true));

        SubmitMaterialRequirementResultView view =
                api.submitMaterialRequirement(requirement.requirementId().value(), 0L);

        verify(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
        verify(submitMaterialRequirementService)
                .submit(requirement.requirementId(), 0L, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        assertEquals(requirement.requirementId().value(), view.requirementId());
        assertEquals(MaterialRequirementStatusView.SUBMITTED, view.status());
        assertTrue(view.created());
        assertEquals(1, view.documents().size());
        assertEquals(documentId, view.documents().getFirst().documentId());
        assertEquals(sourceWarehouseId, view.documents().getFirst().sourceWarehouseId());
    }

    @Test
    void materialRequirementMutationsRequireCreateTransferPermission() {
        doThrow(new AccessDeniedException("Access denied: production.transfer.create"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);

        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID requirementId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();

        assertThrows(
                AccessDeniedException.class,
                () -> api.prepareMaterialRequirement(orderId, List.of(itemId)));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.prepareMaterialRequirement(
                                List.of(
                                        new ProductionApplicationApi
                                                .MaterialRequirementProductSelectionView(
                                                orderId, itemId, Optional.empty()))));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.changeMaterialRequirementQuantity(
                                requirementId, lineId, BigDecimal.TEN, 0L));
        assertThrows(
                AccessDeniedException.class,
                () -> api.submitMaterialRequirement(requirementId, 0L));

        verify(materialRequirementService, never()).prepareMaterialRequirement(any(), any());
        verify(materialRequirementService, never()).prepareMaterialRequirement(any());
        verify(materialRequirementService, never()).changeQuantity(any(), any(), any(), anyLong());
        verify(submitMaterialRequirementService, never()).submit(any(), anyLong(), any());
    }

    @Test
    void materialRequirementReadsRequireViewPermission() {
        doThrow(new AccessDeniedException("Access denied: production.order.view"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        UUID requirementId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        assertThrows(
                AccessDeniedException.class, () -> api.getMaterialRequirement(requirementId));
        assertThrows(
                AccessDeniedException.class,
                () ->
                        api.getMaterialRequirementProductCoverage(
                                List.of(
                                        new ProductionApplicationApi
                                                .MaterialRequirementSourceItemRefView(
                                                orderId, itemId))));

        verify(materialRequirementService, never()).findById(any());
        verify(materialRequirementCoverageService, never()).coverageForItems(any());
    }

    @Test
    void materialRequirementReadsAllowedWithViewPermissionWithoutTransfer() {
        MaterialRequirement requirement = sampleRequirement();
        when(materialRequirementService.findById(requirement.requirementId()))
                .thenReturn(Optional.of(requirement));
        when(materialRequirementCoverageService.coverageForItems(any()))
                .thenReturn(
                        Map.of(
                                MaterialRequirementSourceItemKey.of(
                                        requirement.sourceItems().getFirst().sourceOrderId(),
                                        requirement.sourceItems().getFirst().sourceOrderItemId()),
                                new MaterialRequirementProductCoverageCalculator.ProductItemCoverage(
                                        10L, 10L, 0L, 0L, 0L, 10L)));

        assertTrue(api.getMaterialRequirement(requirement.requirementId().value()).isPresent());
        assertEquals(
                1,
                api.getMaterialRequirementProductCoverage(
                                List.of(
                                        new ProductionApplicationApi
                                                .MaterialRequirementSourceItemRefView(
                                                requirement
                                                        .sourceItems()
                                                        .getFirst()
                                                        .sourceOrderId()
                                                        .value(),
                                                requirement
                                                        .sourceItems()
                                                        .getFirst()
                                                        .sourceOrderItemId()
                                                        .value())))
                        .size());

        verify(authorizationService, org.mockito.Mockito.atLeast(2))
                .requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        verify(authorizationService, never())
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
    }

    @Test
    void materialRequirementMutationsAllowedWithCreateTransferPermission() {
        MaterialRequirement requirement = sampleRequirement();
        when(materialRequirementService.prepareMaterialRequirement(any(), any()))
                .thenReturn(requirement);
        when(materialRequirementService.prepareMaterialRequirement(any()))
                .thenReturn(requirement);
        when(materialRequirementService.changeQuantity(any(), any(), any(), anyLong()))
                .thenReturn(requirement);
        when(submitMaterialRequirementService.submit(any(), anyLong(), any()))
                .thenReturn(
                        new SubmitMaterialRequirementResult(
                                requirement.submit("user-1", Instant.parse("2026-09-10T01:00:00Z")),
                                List.of(),
                                List.of(),
                                true));

        api.prepareMaterialRequirement(
                requirement.sourceItems().getFirst().sourceOrderId().value(),
                List.of(requirement.sourceItems().getFirst().sourceOrderItemId().value()));
        api.prepareMaterialRequirement(
                List.of(
                        new ProductionApplicationApi.MaterialRequirementProductSelectionView(
                                requirement.sourceItems().getFirst().sourceOrderId().value(),
                                requirement.sourceItems().getFirst().sourceOrderItemId().value(),
                                Optional.empty())));
        api.changeMaterialRequirementQuantity(
                requirement.requirementId().value(),
                requirement.lines().getFirst().lineId().value(),
                BigDecimal.TEN,
                0L);
        api.submitMaterialRequirement(requirement.requirementId().value(), 0L);

        verify(authorizationService, org.mockito.Mockito.atLeast(4))
                .requirePermission(ProductionPermissions.PRODUCTION_CREATE_TRANSFER);
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
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        BigDecimal quantity = new BigDecimal("5.000");
        MaterialRequirementLine line =
                MaterialRequirementLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-1",
                        "Material 1",
                        "RED",
                        "m",
                        quantity,
                        List.of(
                                MaterialRequirementLineContribution.of(
                                        orderId, itemId, quantity)));
        return MaterialRequirement.create(
                PROD_WH,
                Instant.parse("2026-08-20T09:00:00Z"),
                List.of(MaterialRequirementSourceItem.of(orderId, itemId, 1L)),
                List.of(line));
    }
}
