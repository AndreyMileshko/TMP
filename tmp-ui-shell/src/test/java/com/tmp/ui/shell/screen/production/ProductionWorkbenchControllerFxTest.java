package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.CuttingLinkStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialPlanningSourceView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateLineView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateStatusView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProductionWorkbenchControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void fxmlLoadsWithSixCommandButtons() throws Exception {
        ProductionWorkbenchViewModel viewModel =
                new ProductionWorkbenchViewModel(
                        new ProductionWorkbenchUiTestSupport.StubQueryApi(),
                        new ProductionWorkbenchUiTestSupport.StubApplicationApi(),
                        new ProductionWorkbenchUiTestSupport.StubOrderQuery(),
                        new ProductionWorkbenchUiTestSupport.StubWarehouseApi(),
                        new ProductionWorkbenchUiTestSupport.AllowAllAuthorization(),
                        new ProductionWorkbenchUiTestSupport.StubAuthentication());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Boolean> ok = new AtomicReference<>(false);

        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        Thread.currentThread()
                                                .getContextClassLoader()
                                                .getResource(
                                                        UiShellScreens.PRODUCTION_WORKBENCH_FXML));
                        loader.setControllerFactory(type -> new ProductionWorkbenchController());
                        Parent root = loader.load();
                        ProductionWorkbenchController controller = loader.getController();
                        controller.setViewModel(viewModel);
                        Stage stage = new Stage();
                        stage.setScene(new Scene(root, 1200, 800));
                        stage.show();
                        root.applyCss();
                        root.layout();

                        assertNotNull(root.lookup("#acceptButton"));
                        assertNotNull(root.lookup("#confirmReceiptButton"));
                        assertNotNull(root.lookup("#prepareReleaseButton"));
                        assertEquals(
                                "Принять в производство",
                                ((Button) root.lookup("#acceptButton")).getText());
                        ScrollPane scroll =
                                (ScrollPane) root.lookup("#rootScroll");
                        assertNotNull(scroll);
                        Parent scrollContent = (Parent) scroll.getContent();
                        assertNotNull(scrollContent.lookup("#addTransferAllocationButton"));
                        assertNotNull(scrollContent.lookup("#transferAllocationsTable"));
                        @SuppressWarnings("unchecked")
                        TableView<TransferAllocationRow> transferAllocationsTable =
                                (TableView<TransferAllocationRow>)
                                        scrollContent.lookup("#transferAllocationsTable");
                        @SuppressWarnings("unchecked")
                        TableView<ReleaseCellAllocationRow> releaseAllocationsTable =
                                (TableView<ReleaseCellAllocationRow>)
                                        scrollContent.lookup("#releaseAllocationsTable");
                        assertNotNull(transferAllocationsTable.getItems());
                        assertTrue(transferAllocationsTable.getItems().isEmpty());
                        assertNotNull(releaseAllocationsTable.getItems());
                        assertTrue(releaseAllocationsTable.getItems().isEmpty());
                        ok.set(true);
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Production workbench FX load failed", error.get());
        }
        assertTrue(ok.get());
        assertFalse(viewModel.canAcceptProperty().get());
    }

    @Test
    void transferLineReselectionKeepsAllocationRowsVisibleInTable() throws Exception {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID itemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID specId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID sourceWh = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID destWh = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID sourceCell = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID sourceCellB = UUID.fromString("66666666-6666-6666-6666-666666666667");
        UUID destCell = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID destCellB = UUID.fromString("77777777-7777-7777-7777-777777777778");
        UUID materialRef = UUID.fromString("88888888-8888-8888-8888-888888888888");
        UUID templateId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        UUID lineId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        ProductionWorkbenchUiTestSupport.StubQueryApi queryApi =
                new ProductionWorkbenchUiTestSupport.StubQueryApi();
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

        ProductionWorkbenchUiTestSupport.StubApplicationApi applicationApi =
                new ProductionWorkbenchUiTestSupport.StubApplicationApi();
        applicationApi.mainWarehouseId = sourceWh;
        applicationApi.productionWarehouseId = destWh;
        applicationApi.template =
                new TransferTemplateView(
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
                                        new BigDecimal("1.000000"),
                                        new BigDecimal("1.000000"),
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

        ProductionWorkbenchUiTestSupport.StubOrderQuery orderQuery =
                new ProductionWorkbenchUiTestSupport.StubOrderQuery();
        orderQuery.order = ProductionWorkbenchUiTestSupport.order(orderId, "ORD-1");
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(orderId, itemId, "1"));

        ProductionWorkbenchUiTestSupport.StubWarehouseApi warehouseApi =
                new ProductionWorkbenchUiTestSupport.StubWarehouseApi();
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

        ProductionWorkbenchViewModel viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        warehouseApi,
                        new ProductionWorkbenchUiTestSupport.AllowAllAuthorization(),
                        new ProductionWorkbenchUiTestSupport.StubAuthentication());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        Thread.currentThread()
                                                .getContextClassLoader()
                                                .getResource(
                                                        UiShellScreens.PRODUCTION_WORKBENCH_FXML));
                        loader.setControllerFactory(type -> new ProductionWorkbenchController());
                        Parent root = loader.load();
                        ProductionWorkbenchController controller = loader.getController();
                        controller.setViewModel(viewModel);
                        Stage stage = new Stage();
                        stage.setScene(new Scene(root, 1200, 800));
                        stage.show();

                        viewModel.openForOrder(OrderId.of(orderId));
                        viewModel.prepareTransfer();

                        ScrollPane scroll = (ScrollPane) root.lookup("#rootScroll");
                        Parent scrollContent = (Parent) scroll.getContent();
                        @SuppressWarnings("unchecked")
                        TableView<TransferLineRow> transferLinesTable =
                                (TableView<TransferLineRow>)
                                        scrollContent.lookup("#transferLinesTable");
                        @SuppressWarnings("unchecked")
                        TableView<TransferAllocationRow> transferAllocationsTable =
                                (TableView<TransferAllocationRow>)
                                        scrollContent.lookup("#transferAllocationsTable");
                        Button addAllocationButton =
                                (Button) scrollContent.lookup("#addTransferAllocationButton");
                        Button applyRequestedQtyButton =
                                (Button) scrollContent.lookup("#applyRequestedQtyButton");

                        TransferLineRow line = viewModel.transferLines().get(0);
                        transferLinesTable.getSelectionModel().select(line);
                        viewModel.selectTransferLine(line.lineId());

                        addAllocationButton.fire();
                        TransferAllocationRow first = line.allocations().get(0);
                        first.setSourceCell(
                                line.sourceCellChoices().stream()
                                        .filter(c -> c.id().equals(sourceCell))
                                        .findFirst()
                                        .orElseThrow());
                        first.setDestinationCell(
                                line.destinationCellChoices().stream()
                                        .filter(c -> c.id().equals(destCell))
                                        .findFirst()
                                        .orElseThrow());
                        first.setQuantity("0.600000");

                        addAllocationButton.fire();
                        TransferAllocationRow second = line.allocations().get(1);
                        second.setSourceCell(
                                line.sourceCellChoices().stream()
                                        .filter(c -> c.id().equals(sourceCellB))
                                        .findFirst()
                                        .orElseThrow());
                        second.setDestinationCell(
                                line.destinationCellChoices().stream()
                                        .filter(c -> c.id().equals(destCellB))
                                        .findFirst()
                                        .orElseThrow());
                        second.setQuantity("0.400000");

                        transferLinesTable.getSelectionModel().clearSelection();
                        viewModel.selectTransferLine(null);
                        assertNotNull(transferAllocationsTable.getItems());
                        assertTrue(transferAllocationsTable.getItems().isEmpty());

                        transferLinesTable.getSelectionModel().select(line);
                        viewModel.selectTransferLine(line.lineId());

                        assertNotNull(transferAllocationsTable.getItems());
                        assertEquals(2, transferAllocationsTable.getItems().size());
                        assertEquals("0.600000", transferAllocationsTable.getItems().get(0).quantity());
                        assertEquals("0.400000", transferAllocationsTable.getItems().get(1).quantity());

                        applyRequestedQtyButton.fire();
                        assertEquals(line.lineId(), viewModel.selectedTransferLineIdProperty().get());
                        assertEquals(2, transferAllocationsTable.getItems().size());
                        assertEquals("0.600000", transferAllocationsTable.getItems().get(0).quantity());
                        assertEquals("0.400000", transferAllocationsTable.getItems().size() > 1
                                ? transferAllocationsTable.getItems().get(1).quantity()
                                : null);
                        assertNotNull(transferAllocationsTable.getItems().get(0).sourceCell());
                        assertNotNull(transferAllocationsTable.getItems().get(0).destinationCell());
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Transfer allocation FX regression failed", error.get());
        }
    }
}
