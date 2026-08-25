package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.CuttingLinkStatusView;
import com.tmp.production.api.ProductionApplicationApi.ItemReleaseView;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualDefaultView;
import com.tmp.production.api.ProductionApplicationApi.MaterialPlanningSourceView;
import com.tmp.production.api.ProductionApplicationApi.PlannedMaterialLineView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.ReleaseResultView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateLineView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateStatusView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateView;
import com.tmp.production.api.ProductionQueryApi.CuttingPlanLinkView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityLineStatus;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityLineView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityOverallStatus;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityResultView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryType;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.AllowAllAuthorization;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubApplicationApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubAuthentication;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubQueryApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWarehouseApi;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductionWorkbenchViewModelTest {

    private StubQueryApi queryApi;
    private StubApplicationApi applicationApi;
    private StubOrderQuery orderQuery;
    private StubWarehouseApi warehouseApi;
    private AllowAllAuthorization auth;
    private StubAuthentication authentication;
    private ProductionWorkbenchViewModel viewModel;

    private UUID orderId;
    private UUID itemId;
    private UUID specId;
    private UUID sourceWh;
    private UUID destWh;
    private UUID sourceCell;
    private UUID destCell;
    private UUID materialRef;
    private UUID templateId;
    private UUID lineId;
    private UUID logicalTransferId;

    @BeforeEach
    void setUp() {
        queryApi = new StubQueryApi();
        applicationApi = new StubApplicationApi();
        orderQuery = new StubOrderQuery();
        warehouseApi = new StubWarehouseApi();
        auth = new AllowAllAuthorization();
        authentication = new StubAuthentication();
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        warehouseApi,
                        auth,
                        authentication);

        orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        itemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        specId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        sourceWh = UUID.fromString("44444444-4444-4444-4444-444444444444");
        destWh = UUID.fromString("55555555-5555-5555-5555-555555555555");
        sourceCell = UUID.fromString("66666666-6666-6666-6666-666666666666");
        destCell = UUID.fromString("77777777-7777-7777-7777-777777777777");
        materialRef = UUID.fromString("88888888-8888-8888-8888-888888888888");
        templateId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        lineId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        logicalTransferId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        orderQuery.order = ProductionWorkbenchUiTestSupport.order(orderId, "ORD-1");
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(orderId, itemId, "1"));
        warehouseApi.cellsByWarehouse.put(
                sourceWh, List.of(new StorageCellView(sourceCell, sourceWh, "S-1", true)));
        warehouseApi.cellsByWarehouse.put(
                destWh, List.of(new StorageCellView(destCell, destWh, "P-1", true)));
        applicationApi.mainWarehouseId = sourceWh;
        applicationApi.productionWarehouseId = destWh;
    }

    @Test
    void loadsOrderStatusItemsHistoryAndMaterials() {
        queryApi.view =
                new OrderProductionView(
                        orderId, OrderProductionViewStatus.IN_PRODUCTION, 1, 1, 0, 0, 0);
        UUID cuttingId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        queryApi.itemStates.put(
                itemId,
                new ItemProductionStateView(
                        orderId,
                        itemId,
                        specId,
                        ItemProductionStateStatus.IN_PRODUCTION,
                        10,
                        10,
                        10,
                        0,
                        Optional.empty(),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        List.of(new CuttingPlanLinkView(materialRef, cuttingId))));
        queryApi.availability =
                Optional.of(
                        new MaterialAvailabilityResultView(
                                orderId,
                                Instant.parse("2026-01-01T11:00:00Z"),
                                MaterialAvailabilityOverallStatus.HAS_UNRESOLVED_MATERIALS,
                                List.of(
                                        resolvedLine(),
                                        unresolvedLine(),
                                        ambiguousLine())));
        queryApi.history.add(
                new ProductionHistoryEntryView(
                        UUID.randomUUID(),
                        orderId,
                        ProductionHistoryType.ORDER_ACCEPTED,
                        Instant.parse("2026-01-01T09:00:00Z"),
                        Instant.parse("2026-01-01T09:00:01Z"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("tester"),
                        Optional.of("ok")));

        viewModel.openForOrder(OrderId.of(orderId));

        assertEquals("В производстве", viewModel.statusLabelProperty().get());
        assertEquals("ORD-1", viewModel.orderNumberProperty().get());
        assertEquals(1, viewModel.itemRows().size());
        assertTrue(viewModel.itemRows().get(0).cuttingPlanRefs().contains("Карта раскроя"));
        assertEquals(3, viewModel.materialRows().size());
        MaterialAvailabilityRow unresolved =
                viewModel.materialRows().stream()
                        .filter(MaterialAvailabilityRow::unresolvedOrAmbiguous)
                        .findFirst()
                        .orElseThrow();
        assertEquals("Материал не сопоставлен", unresolved.deficit());
        assertEquals("—", unresolved.totalAvailable());
        assertEquals(1, viewModel.historyRows().size());
        assertEquals("Заказ принят в производство", viewModel.historyRows().get(0).typeLabel());
        assertTrue(viewModel.canCheckProperty().get());
        assertFalse(viewModel.canAcceptProperty().get());
    }

    @Test
    void sixCommandsDelegateToApplicationApi() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));

        queryApi.view =
                new OrderProductionView(
                        orderId, OrderProductionViewStatus.NOT_ACCEPTED, 0, 0, 0, 0, 0);
        viewModel.refresh();
        viewModel.acceptOrder();
        assertEquals(List.of(orderId), applicationApi.acceptCalls);
        assertEquals(List.of("tester"), applicationApi.acceptActors);

        seedInProduction();
        viewModel.refresh();
        viewModel.checkMaterials();
        assertEquals(List.of(orderId), applicationApi.checkCalls);

        applicationApi.template = sampleTemplate(new BigDecimal("5"));
        viewModel.prepareTransfer();
        assertEquals(1, applicationApi.prepareTransferCalls.size());
        TransferLineRow row = viewModel.transferLines().get(0);
        row.setSourceCell(row.sourceCellChoices().get(0));
        row.setDestinationCell(row.destinationCellChoices().get(0));
        viewModel.confirmTransfer();
        assertEquals(1, applicationApi.confirmTransferCalls.size());

        applicationApi.logicalTransfers =
                List.of(
                        new LogicalTransferView(
                                logicalTransferId, templateId, Instant.parse("2026-01-01T12:00:00Z")));
        viewModel.refresh();
        viewModel.selectedLogicalTransferProperty().set(viewModel.logicalTransfers().get(0));
        viewModel.confirmReceipt();
        assertEquals(List.of(logicalTransferId), applicationApi.receiptCalls);

        viewModel.itemRows().get(0).setReleaseQuantityInput("2");
        applicationApi.releasePreview = sampleReleasePreview();
        applicationApi.releaseResult =
                new ReleaseResultView(UUID.randomUUID(), orderId, Instant.now());
        applicationApi.template = sampleTemplate(new BigDecimal("5"));
        viewModel.prepareRelease();
        assertEquals(1, applicationApi.prepareReleaseCalls.size());
        ReleaseMaterialRow materialRow = viewModel.releaseMaterialRows().get(0);
        materialRow.setProductionCell(materialRow.cellChoices().get(0));
        viewModel.confirmRelease();
        assertEquals(1, applicationApi.releaseProductCalls.size());
        assertEquals(
                applicationApi.prepareReleaseCalls.get(0),
                applicationApi.releaseProductCalls.get(0));

        viewModel.cancelProduction();
        assertEquals(List.of(orderId), applicationApi.cancelCalls);
        assertEquals(Optional.empty(), applicationApi.cancelReasons.get(0));
    }

    @Test
    void transferEditChangesRequestedQuantityNotRecommended() {
        seedInProduction();
        applicationApi.template = sampleTemplate(new BigDecimal("5"));
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.prepareTransfer();

        TransferLineRow row = viewModel.transferLines().get(0);
        assertEquals("5", row.recommendedQuantity());
        assertEquals("5", row.requestedQuantity());
        row.setRequestedQuantity("3");
        viewModel.applyTransferRequestedQuantity(row);

        assertEquals(1, applicationApi.changeQtyCalls.size());
        assertEquals(new BigDecimal("3"), applicationApi.changeQtyCalls.get(0)[2]);
        assertEquals("5", viewModel.transferLines().get(0).recommendedQuantity());
        assertEquals("3", viewModel.transferLines().get(0).requestedQuantity());
    }

    @Test
    void cancelSendsOnlyOrderId() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.cancelProduction();
        assertEquals(1, applicationApi.cancelCalls.size());
        assertEquals(orderId, applicationApi.cancelCalls.get(0));
        assertEquals(Optional.empty(), applicationApi.cancelReasons.get(0));
    }

    @Test
    void releasePrepareThenReleaseProductsWithoutRecomputingPlan() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.itemRows().get(0).setReleaseQuantityInput("2");
        applicationApi.releasePreview = sampleReleasePreview();
        applicationApi.releaseResult =
                new ReleaseResultView(UUID.randomUUID(), orderId, Instant.now());
        applicationApi.template = sampleTemplate(new BigDecimal("1"));

        viewModel.prepareRelease();
        List<ItemReleaseView> prepared = applicationApi.prepareReleaseCalls.get(0);
        ReleaseMaterialRow row = viewModel.releaseMaterialRows().get(0);
        assertEquals("4.000000", row.plannedQuantity());
        assertEquals("4.000000", row.actualQuantity());
        row.setActualQuantity("3.5");
        row.setProductionCell(row.cellChoices().get(0));

        viewModel.confirmRelease();
        assertEquals(prepared, applicationApi.releaseProductCalls.get(0));
        assertEquals(
                new BigDecimal("3.5"),
                applicationApi.releaseUsageCalls.get(0).get(0).actualQuantity());
    }

    @Test
    void terminalStatesDisableButtons() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));
        assertTrue(viewModel.canCheckProperty().get());

        queryApi.view =
                new OrderProductionView(
                        orderId, OrderProductionViewStatus.MANUFACTURED, 1, 0, 0, 1, 0);
        viewModel.refresh();
        assertFalse(viewModel.canAcceptProperty().get());
        assertFalse(viewModel.canCheckProperty().get());
        assertFalse(viewModel.canTransferProperty().get());
        assertFalse(viewModel.canReceiptProperty().get());
        assertFalse(viewModel.canReleaseProperty().get());
        assertFalse(viewModel.canCancelProperty().get());

        queryApi.view =
                new OrderProductionView(
                        orderId, OrderProductionViewStatus.CANCELLED, 1, 0, 0, 0, 1);
        viewModel.refresh();
        assertFalse(viewModel.canAcceptProperty().get());
        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void notAcceptedEnablesOnlyAccept() {
        queryApi.view =
                new OrderProductionView(
                        orderId, OrderProductionViewStatus.NOT_ACCEPTED, 0, 0, 0, 0, 0);
        viewModel.openForOrder(OrderId.of(orderId));
        assertTrue(viewModel.canAcceptProperty().get());
        assertFalse(viewModel.canCheckProperty().get());
        assertFalse(viewModel.canTransferProperty().get());
        assertFalse(viewModel.canReleaseProperty().get());
        assertFalse(viewModel.canCancelProperty().get());
    }

    @Test
    void emptyStateDisablesMutations() {
        assertFalse(viewModel.orderSelectedProperty().get());
        assertFalse(viewModel.canAcceptProperty().get());
        assertTrue(viewModel.emptyStateMessageProperty().get().contains("Выберите заказ"));
    }

    private void seedInProduction() {
        queryApi.view =
                new OrderProductionView(
                        orderId, OrderProductionViewStatus.IN_PRODUCTION, 1, 1, 0, 0, 0);
        queryApi.itemStates.put(
                itemId,
                new ItemProductionStateView(
                        orderId,
                        itemId,
                        specId,
                        ItemProductionStateStatus.IN_PRODUCTION,
                        10,
                        10,
                        10,
                        0,
                        Optional.empty(),
                        Instant.parse("2026-01-01T10:00:00Z"),
                        List.of()));
        applicationApi.logicalTransfers = List.of();
    }

    private TransferTemplateView sampleTemplate(BigDecimal recommended) {
        return new TransferTemplateView(
                templateId,
                orderId,
                sourceWh,
                destWh,
                Instant.parse("2026-01-01T12:00:00Z"),
                Instant.parse("2026-01-01T12:00:00Z"),
                1L,
                TransferTemplateStatusView.DRAFT,
                Optional.empty(),
                List.of(
                        new TransferTemplateLineView(
                                lineId,
                                materialRef,
                                "ART-1",
                                "Материал",
                                "белый",
                                "шт",
                                recommended,
                                recommended,
                                true,
                                MaterialPlanningSourceView.SPECIFICATION,
                                Optional.empty(),
                                CuttingLinkStatusView.NONE,
                                List.of(),
                                List.of(itemId),
                                new BigDecimal("10"),
                                new BigDecimal("20"),
                                new BigDecimal("0"),
                                BigDecimal.ZERO)));
    }

    private ReleasePreviewView sampleReleasePreview() {
        return new ReleasePreviewView(
                orderId,
                List.of(new ItemReleaseView(itemId, 2)),
                List.of(
                        new PlannedMaterialLineView(
                                itemId,
                                materialRef,
                                specId,
                                new BigDecimal("4.000000"),
                                MaterialPlanningSourceView.SPECIFICATION,
                                Optional.empty(),
                                Optional.of("Материал"))),
                List.of(
                        new MaterialActualDefaultView(
                                itemId,
                                materialRef,
                                new BigDecimal("4.000000"),
                                new BigDecimal("4.000000"))));
    }

    private MaterialAvailabilityLineView resolvedLine() {
        return new MaterialAvailabilityLineView(
                "ART-OK",
                "OK",
                "белый",
                "шт",
                Optional.of(materialRef),
                new BigDecimal("10"),
                new BigDecimal("7"),
                new BigDecimal("1"),
                new BigDecimal("8"),
                new BigDecimal("2"),
                MaterialAvailabilityLineStatus.INSUFFICIENT,
                com.tmp.production.api.ProductionQueryApi.MaterialPlanningSourceView.SPECIFICATION);
    }

    private MaterialAvailabilityLineView unresolvedLine() {
        return new MaterialAvailabilityLineView(
                "ART-U",
                "U",
                "",
                "шт",
                Optional.empty(),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED,
                com.tmp.production.api.ProductionQueryApi.MaterialPlanningSourceView.SPECIFICATION);
    }

    private MaterialAvailabilityLineView ambiguousLine() {
        return new MaterialAvailabilityLineView(
                "ART-A",
                "A",
                "",
                "шт",
                Optional.empty(),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS,
                com.tmp.production.api.ProductionQueryApi.MaterialPlanningSourceView.SPECIFICATION);
    }
}
