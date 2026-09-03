package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.CuttingPlanLinkView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionCuttingPlanLink;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.MaterialAvailabilityCheckResult;
import com.tmp.production.domain.MaterialCheckNotAllowedException;
import com.tmp.production.domain.MaterialAvailabilityOverallStatus;
import com.tmp.production.security.ProductionPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProductionQueryApiAuthorizationTest {

    private static final Instant T0 = Instant.parse("2026-08-20T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-20T10:00:00Z");

    @Test
    void deniedOrderViewFailsBeforeAnyDownstreamRead() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        assertThrows(
                AccessDeniedException.class, () -> api.getOrderProductionView(UUID.randomUUID()));
        Mockito.verifyNoInteractions(orderViewService, materialQueryService, historyService);
    }

    @Test
    void deniedOrderListFactsFailsBeforeAnyDownstreamRead() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        assertThrows(
                AccessDeniedException.class,
                () -> api.getOrderProductionListFacts(List.of(UUID.randomUUID())));
        Mockito.verifyNoInteractions(orderViewService, materialQueryService, historyService);
    }

    @Test
    void deniedItemStateFailsBeforeAnyDownstreamRead() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        assertThrows(AccessDeniedException.class, () -> api.getItemProductionState(UUID.randomUUID()));
        Mockito.verifyNoInteractions(orderViewService, materialQueryService, historyService);
    }

    @Test
    void deniedHistoryFailsBeforeAnyDownstreamRead() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        assertThrows(
                AccessDeniedException.class, () -> api.listProductionHistory(UUID.randomUUID()));
        Mockito.verifyNoInteractions(orderViewService, materialQueryService, historyService);
    }

    @Test
    void itemStateIsOptionalEmptyWhenNoLaunchedRowAndPermissionAllowed() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doNothing().when(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_VIEW);
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        Mockito.when(orderViewService.findItemProductionStateByOrderItemId(itemId))
                .thenReturn(Optional.empty());

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        Optional<ItemProductionStateView> dto = api.getItemProductionState(itemId.value());
        assertTrue(dto.isEmpty());
    }

    @Test
    void mapsItemProductionStateFrozenSnapshot() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doNothing().when(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();

        MaterialReferenceId materialReferenceId = MaterialReferenceId.generate();
        CuttingPlanId cuttingPlanId = CuttingPlanId.generate();
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(materialReferenceId, cuttingPlanId));

        ProductionFoundation foundation = ProductionFoundation.freeze(orderId, itemId, specId, T0);
        ProductionItemState launched =
                ProductionItemState.launch(foundation, ProductionQuantity.positive(7), T0, links);
        ProductionItemState checked = launched.recordMaterialCheck(T1);

        Mockito.when(orderViewService.findItemProductionStateByOrderItemId(itemId))
                .thenReturn(Optional.of(checked));

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        ItemProductionStateView dto = api.getItemProductionState(itemId.value()).orElseThrow();
        assertEquals(orderId.value(), dto.sourceOrderId());
        assertEquals(itemId.value(), dto.sourceOrderItemId());
        assertEquals(specId.value(), dto.specificationId());
        assertEquals(ProductionQueryApi.ItemProductionStateStatus.IN_PRODUCTION, dto.status());
        assertEquals(7L, dto.orderedQuantity());
        assertEquals(7L, dto.launchedQuantity());
        assertEquals(7L, dto.activeProductionQuantity());
        assertEquals(0L, dto.releasedQuantity());
        assertEquals(T1, dto.lastMaterialCheckAt().orElseThrow());
        assertEquals(T0, dto.lastStatusChangedAt());

        assertEquals(1, dto.cuttingPlanLinks().size());
        CuttingPlanLinkView link = dto.cuttingPlanLinks().get(0);
        assertEquals(materialReferenceId.value(), link.materialReferenceId());
        assertEquals(cuttingPlanId.value(), link.cuttingPlanId());
    }

    @Test
    void materialAvailabilityReturnsEmptyWhenMaterialCheckNotAllowedForNotInProductionOrder() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doNothing().when(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        SourceOrderId orderId = SourceOrderId.generate();
        Mockito.doThrow(
                        new MaterialCheckNotAllowedException(
                                orderId, OrderProductionViewStatus.NOT_ACCEPTED))
                .when(materialQueryService)
                .evaluate(orderId);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        Optional<ProductionQueryApi.MaterialAvailabilityResultView> dto =
                api.getMaterialAvailabilityResult(orderId.value());
        assertTrue(dto.isEmpty());
        Mockito.verifyNoInteractions(orderViewService, historyService);
    }

    @Test
    void deniedMaterialAvailabilityFailsBeforeAnyCalculatorCall() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationService)
                .requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        SourceOrderId orderId = SourceOrderId.generate();
        assertThrows(AccessDeniedException.class, () -> api.getMaterialAvailabilityResult(orderId.value()));
        Mockito.verifyNoInteractions(orderViewService, materialQueryService, historyService);
    }

    @Test
    void materialAvailabilityPropagatesWarehouseReadDeniedAccessDenied() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doNothing().when(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        SourceOrderId orderId = SourceOrderId.generate();
        Mockito.doThrow(new AccessDeniedException("warehouse denied"))
                .when(materialQueryService)
                .evaluate(orderId);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        assertThrows(
                AccessDeniedException.class, () -> api.getMaterialAvailabilityResult(orderId.value()));
        Mockito.verifyNoInteractions(historyService);
    }

    @Test
    void materialAvailabilityDoesNotAppendHistory() {
        AuthorizationService authorizationService = Mockito.mock(AuthorizationService.class);
        ProductionOrderViewService orderViewService = Mockito.mock(ProductionOrderViewService.class);
        CurrentMaterialAvailabilityQueryService materialQueryService =
                Mockito.mock(CurrentMaterialAvailabilityQueryService.class);
        ProductionHistoryService historyService = Mockito.mock(ProductionHistoryService.class);

        Mockito.doNothing().when(authorizationService).requirePermission(ProductionPermissions.PRODUCTION_VIEW);

        SourceOrderId orderId = SourceOrderId.generate();

        MaterialAvailabilityCheckResult result =
                new MaterialAvailabilityCheckResult(
                        orderId,
                        T0,
                        MaterialAvailabilityOverallStatus.ALL_AVAILABLE,
                        List.of());

        Mockito.when(materialQueryService.evaluate(orderId)).thenReturn(result);

        DefaultProductionQueryApi api =
                new DefaultProductionQueryApi(
                        authorizationService,
                        orderViewService,
                        materialQueryService,
                        historyService);

        assertNotNull(api.getMaterialAvailabilityResult(orderId.value()).orElseThrow());
        Mockito.verifyNoInteractions(orderViewService, historyService);
    }
}

