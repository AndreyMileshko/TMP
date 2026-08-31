package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.CellAllocationView;
import com.tmp.production.api.ProductionApplicationApi.CuttingLinkStatusView;
import com.tmp.production.api.ProductionApplicationApi.ItemReleaseView;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualDefaultView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualUsageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialPlanningSourceView;
import com.tmp.production.api.ProductionApplicationApi.PlannedMaterialLineView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.ReleaseResultView;
import com.tmp.production.api.ProductionApplicationApi.TransferCellAllocation;
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
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
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
    private UUID sourceCellB;
    private UUID destCell;
    private UUID destCellB;
    private UUID materialRef;
    private UUID templateId;
    private UUID lineId;
    private UUID logicalTransferId;

    private UUID warehouseDraftId;

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
        sourceCellB = UUID.fromString("66666666-6666-6666-6666-666666666667");
        destCell = UUID.fromString("77777777-7777-7777-7777-777777777777");
        destCellB = UUID.fromString("77777777-7777-7777-7777-777777777778");
        materialRef = UUID.fromString("88888888-8888-8888-8888-888888888888");
        templateId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        lineId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        logicalTransferId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        warehouseDraftId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccd");

        orderQuery.order = ProductionWorkbenchUiTestSupport.order(orderId, "ORD-1");
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(orderId, itemId, "1"));
        warehouseApi.cellsByWarehouse.put(
                sourceWh,
                List.of(
                        new StorageCellView(sourceCell, sourceWh, "S-A", true),
                        new StorageCellView(sourceCellB, sourceWh, "S-B", true)));
        warehouseApi.cellsByWarehouse.put(
                destWh,
                List.of(
                        new StorageCellView(destCell, destWh, "P-X", true),
                        new StorageCellView(destCellB, destWh, "P-Y", true)));
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
        TransferAllocationRow transferAlloc = row.addAllocation();
        transferAlloc.setSourceCell(row.sourceCellChoices().get(0));
        transferAlloc.setDestinationCell(row.destinationCellChoices().get(0));
        transferAlloc.setQuantity("5");
        viewModel.confirmTransfer();
        assertEquals(1, applicationApi.confirmTransferCalls.size());

        applicationApi.logicalTransfers =
                List.of(
                        new LogicalTransferView(
                                logicalTransferId,
                                templateId,
                                Instant.parse("2026-01-01T12:00:00Z"),
                                List.of(
                                        new WarehouseTransferRefView(
                                                warehouseDraftId, materialRef, new BigDecimal("5")))));
        warehouseApi.transferStatuses.put(warehouseDraftId, "SENT");
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
        ReleaseCellAllocationRow releaseAlloc = materialRow.addAllocation();
        releaseAlloc.setProductionCell(materialRow.cellChoices().get(0));
        releaseAlloc.setQuantity(materialRow.actualQuantity());
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
        ReleaseCellAllocationRow allocation = row.addAllocation();
        allocation.setProductionCell(row.cellChoices().get(0));
        allocation.setQuantity("3.5");

        viewModel.confirmRelease();
        assertEquals(prepared, applicationApi.releaseProductCalls.get(0));
        assertEquals(
                new BigDecimal("3.5"),
                applicationApi.releaseUsageCalls.get(0).get(0).actualQuantity());
    }

    @Test
    void transferSupportsMultipleCellAllocationsForOneTemplateLine() {
        seedInProduction();
        applicationApi.template = sampleTemplate(new BigDecimal("10"));
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.prepareTransfer();

        TransferLineRow row = viewModel.transferLines().get(0);
        TransferAllocationRow first = row.addAllocation();
        first.setSourceCell(choiceById(row.sourceCellChoices(), sourceCell));
        first.setDestinationCell(choiceById(row.destinationCellChoices(), destCell));
        first.setQuantity("6");
        TransferAllocationRow second = row.addAllocation();
        second.setSourceCell(choiceById(row.sourceCellChoices(), sourceCellB));
        second.setDestinationCell(choiceById(row.destinationCellChoices(), destCell));
        second.setQuantity("4");

        viewModel.confirmTransfer();

        assertEquals(1, applicationApi.confirmTransferAllocationCalls.size());
        List<TransferCellAllocation> allocations =
                applicationApi.confirmTransferAllocationCalls.get(0);
        assertEquals(2, allocations.size());
        assertEquals(lineId, allocations.get(0).templateLineId());
        assertEquals(lineId, allocations.get(1).templateLineId());
        assertEquals(sourceCell, allocations.get(0).sourceStorageCellId());
        assertEquals(destCell, allocations.get(0).destinationStorageCellId());
        assertEquals(new BigDecimal("6"), allocations.get(0).quantity());
        assertEquals(sourceCellB, allocations.get(1).sourceStorageCellId());
        assertEquals(destCell, allocations.get(1).destinationStorageCellId());
        assertEquals(new BigDecimal("4"), allocations.get(1).quantity());
    }

    @Test
    void transferSupportsMultipleDestinationsForOneTemplateLine() {
        seedInProduction();
        applicationApi.template = sampleTemplate(new BigDecimal("10"));
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.prepareTransfer();

        TransferLineRow row = viewModel.transferLines().get(0);
        TransferAllocationRow first = row.addAllocation();
        first.setSourceCell(choiceById(row.sourceCellChoices(), sourceCell));
        first.setDestinationCell(choiceById(row.destinationCellChoices(), destCell));
        first.setQuantity("6");
        TransferAllocationRow second = row.addAllocation();
        second.setSourceCell(choiceById(row.sourceCellChoices(), sourceCellB));
        second.setDestinationCell(choiceById(row.destinationCellChoices(), destCellB));
        second.setQuantity("4");

        viewModel.confirmTransfer();

        List<TransferCellAllocation> allocations =
                applicationApi.confirmTransferAllocationCalls.get(0);
        assertEquals(2, allocations.size());
        assertEquals(destCell, allocations.get(0).destinationStorageCellId());
        assertEquals(destCellB, allocations.get(1).destinationStorageCellId());
        assertEquals(new BigDecimal("6"), allocations.get(0).quantity());
        assertEquals(new BigDecimal("4"), allocations.get(1).quantity());
    }

    @Test
    void transferAllocationMismatchBlocksConfirm() {
        seedInProduction();
        applicationApi.template = sampleTemplate(new BigDecimal("10"));
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.prepareTransfer();

        TransferLineRow row = viewModel.transferLines().get(0);
        TransferAllocationRow first = row.addAllocation();
        first.setSourceCell(choiceById(row.sourceCellChoices(), sourceCell));
        first.setDestinationCell(choiceById(row.destinationCellChoices(), destCell));
        first.setQuantity("6");
        TransferAllocationRow second = row.addAllocation();
        second.setSourceCell(choiceById(row.sourceCellChoices(), sourceCellB));
        second.setDestinationCell(choiceById(row.destinationCellChoices(), destCell));
        second.setQuantity("3");

        viewModel.confirmTransfer();

        assertEquals(0, applicationApi.confirmTransferCalls.size());
        assertTrue(viewModel.errorMessageProperty().get().contains("Сумма распределений"));
    }

    @Test
    void transferPrepareLeavesAllocationsEmptyWithoutAutoCellPick() {
        seedInProduction();
        applicationApi.template = sampleTemplate(new BigDecimal("10"));
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.prepareTransfer();

        TransferLineRow row = viewModel.transferLines().get(0);
        assertEquals(2, row.sourceCellChoices().size());
        assertEquals(2, row.destinationCellChoices().size());
        assertTrue(row.allocations().isEmpty());
        TransferAllocationRow added = row.addAllocation();
        assertNull(added.sourceCell());
        assertNull(added.destinationCell());
        assertEquals("", added.quantity());
    }

    @Test
    void releaseSupportsMultipleProductionCellAllocations() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.itemRows().get(0).setReleaseQuantityInput("2");
        applicationApi.releasePreview =
                sampleReleasePreview(new BigDecimal("10.000000"), new BigDecimal("10.000000"));
        applicationApi.releaseResult =
                new ReleaseResultView(UUID.randomUUID(), orderId, Instant.now());

        viewModel.prepareRelease();
        ReleaseMaterialRow row = viewModel.releaseMaterialRows().get(0);
        row.setActualQuantity("10");
        ReleaseCellAllocationRow first = row.addAllocation();
        first.setProductionCell(choiceById(row.cellChoices(), destCell));
        first.setQuantity("6");
        ReleaseCellAllocationRow second = row.addAllocation();
        second.setProductionCell(choiceById(row.cellChoices(), destCellB));
        second.setQuantity("4");

        viewModel.confirmRelease();

        MaterialActualUsageView usage = applicationApi.releaseUsageCalls.get(0).get(0);
        assertEquals(new BigDecimal("10"), usage.actualQuantity());
        assertEquals(2, usage.allocations().size());
        assertEquals(destCell, usage.allocations().get(0).storageCellId());
        assertEquals(new BigDecimal("6"), usage.allocations().get(0).quantity());
        assertEquals(destCellB, usage.allocations().get(1).storageCellId());
        assertEquals(new BigDecimal("4"), usage.allocations().get(1).quantity());
    }

    @Test
    void releaseAllocationsValidateAgainstActualNotPlan() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.itemRows().get(0).setReleaseQuantityInput("2");
        applicationApi.releasePreview =
                sampleReleasePreview(new BigDecimal("10.000000"), new BigDecimal("10.000000"));
        applicationApi.releaseResult =
                new ReleaseResultView(UUID.randomUUID(), orderId, Instant.now());

        viewModel.prepareRelease();
        ReleaseMaterialRow row = viewModel.releaseMaterialRows().get(0);
        assertEquals("10.000000", row.plannedQuantity());
        row.setActualQuantity("12");
        ReleaseCellAllocationRow first = row.addAllocation();
        first.setProductionCell(choiceById(row.cellChoices(), destCell));
        first.setQuantity("7");
        ReleaseCellAllocationRow second = row.addAllocation();
        second.setProductionCell(choiceById(row.cellChoices(), destCellB));
        second.setQuantity("5");

        viewModel.confirmRelease();

        MaterialActualUsageView usage = applicationApi.releaseUsageCalls.get(0).get(0);
        assertEquals(new BigDecimal("12"), usage.actualQuantity());
        BigDecimal sum =
                usage.allocations().stream()
                        .map(CellAllocationView::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("12"), sum);
    }

    @Test
    void releaseZeroActualRequiresEmptyAllocations() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.itemRows().get(0).setReleaseQuantityInput("2");
        applicationApi.releasePreview =
                sampleReleasePreview(new BigDecimal("4.000000"), new BigDecimal("4.000000"));
        applicationApi.releaseResult =
                new ReleaseResultView(UUID.randomUUID(), orderId, Instant.now());

        viewModel.prepareRelease();
        ReleaseMaterialRow row = viewModel.releaseMaterialRows().get(0);
        row.setActualQuantity("0");
        assertTrue(row.allocations().isEmpty());

        viewModel.confirmRelease();

        MaterialActualUsageView usage = applicationApi.releaseUsageCalls.get(0).get(0);
        assertEquals(0, usage.actualQuantity().signum());
        assertTrue(usage.allocations().isEmpty());
    }

    @Test
    void releaseAllocationMismatchBlocksConfirm() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.itemRows().get(0).setReleaseQuantityInput("2");
        applicationApi.releasePreview =
                sampleReleasePreview(new BigDecimal("10.000000"), new BigDecimal("10.000000"));
        applicationApi.releaseResult =
                new ReleaseResultView(UUID.randomUUID(), orderId, Instant.now());

        viewModel.prepareRelease();
        ReleaseMaterialRow row = viewModel.releaseMaterialRows().get(0);
        row.setActualQuantity("10");
        ReleaseCellAllocationRow first = row.addAllocation();
        first.setProductionCell(choiceById(row.cellChoices(), destCell));
        first.setQuantity("6");
        ReleaseCellAllocationRow second = row.addAllocation();
        second.setProductionCell(choiceById(row.cellChoices(), destCellB));
        second.setQuantity("3");

        viewModel.confirmRelease();

        assertEquals(0, applicationApi.releaseProductCalls.size());
        assertTrue(viewModel.errorMessageProperty().get().contains("Сумма распределений"));
    }

    @Test
    void transferReselectionRetainsVisibleAllocations() {
        seedInProduction();
        applicationApi.template = sampleTemplate(new BigDecimal("1.000000"));
        viewModel.openForOrder(OrderId.of(orderId));
        viewModel.prepareTransfer();

        TransferLineRow line = viewModel.transferLines().get(0);
        TransferAllocationRow first = line.addAllocation();
        first.setSourceCell(choiceById(line.sourceCellChoices(), sourceCell));
        first.setDestinationCell(choiceById(line.destinationCellChoices(), destCell));
        first.setQuantity("0.600000");
        TransferAllocationRow second = line.addAllocation();
        second.setSourceCell(choiceById(line.sourceCellChoices(), sourceCellB));
        second.setDestinationCell(choiceById(line.destinationCellChoices(), destCellB));
        second.setQuantity("0.400000");

        viewModel.selectTransferLine(line.lineId());
        assertEquals(2, viewModel.findTransferLine(line.lineId()).allocations().size());

        viewModel.selectTransferLine(null);
        viewModel.selectTransferLine(line.lineId());
        assertEquals(2, viewModel.findTransferLine(line.lineId()).allocations().size());
        assertEquals("0.600000", line.allocations().get(0).quantity());
        assertEquals("0.400000", line.allocations().get(1).quantity());
    }

    @Test
    void receiptDisabledForDraftEnabledForSentDisabledAfterReceived() {
        seedInProduction();
        viewModel.openForOrder(OrderId.of(orderId));

        WarehouseTransferRefView ref =
                new WarehouseTransferRefView(warehouseDraftId, materialRef, new BigDecimal("1"));
        LogicalTransferView transfer =
                new LogicalTransferView(
                        logicalTransferId,
                        templateId,
                        Instant.parse("2026-01-01T12:00:00Z"),
                        List.of(ref));
        applicationApi.logicalTransfers = List.of(transfer);

        warehouseApi.transferStatuses.put(warehouseDraftId, "DRAFT");
        viewModel.refresh();
        viewModel.selectedLogicalTransferProperty().set(viewModel.logicalTransfers().get(0));
        assertFalse(viewModel.canReceiptProperty().get());

        warehouseApi.transferStatuses.put(warehouseDraftId, "SENT");
        viewModel.refresh();
        viewModel.selectedLogicalTransferProperty().set(viewModel.logicalTransfers().get(0));
        assertTrue(viewModel.canReceiptProperty().get());

        warehouseApi.transferStatuses.put(warehouseDraftId, "RECEIVED");
        viewModel.refresh();
        viewModel.selectedLogicalTransferProperty().set(viewModel.logicalTransfers().get(0));
        assertFalse(viewModel.canReceiptProperty().get());
    }

    private static StorageCellChoice choiceById(
            java.util.Collection<StorageCellChoice> choices, UUID id) {
        return choices.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
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
        return sampleReleasePreview(new BigDecimal("4.000000"), new BigDecimal("4.000000"));
    }

    private ReleasePreviewView sampleReleasePreview(BigDecimal planned, BigDecimal actual) {
        return new ReleasePreviewView(
                orderId,
                List.of(new ItemReleaseView(itemId, 2)),
                List.of(
                        new PlannedMaterialLineView(
                                itemId,
                                materialRef,
                                specId,
                                planned,
                                MaterialPlanningSourceView.SPECIFICATION,
                                Optional.empty(),
                                Optional.of("Материал"))),
                List.of(
                        new MaterialActualDefaultView(
                                itemId, materialRef, planned, actual)));
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
