package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
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
import javafx.scene.control.TableColumn;
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
                        assertEquals(
                                "Запросить материалы",
                                ((Button) root.lookup("#prepareTransferButton")).getText());
                        ScrollPane scroll = (ScrollPane) root.lookup("#rootScroll");
                        assertNotNull(scroll);
                        Parent scrollContent = (Parent) scroll.getContent();
                        assertNotNull(scrollContent.lookup("#materialRequirementPanel"));
                        assertNotNull(scrollContent.lookup("#requirementLinesTable"));
                        assertNotNull(scrollContent.lookup("#applyRequirementQtyButton"));
                        assertNull(scrollContent.lookup("#transferAllocationsTable"));
                        assertNull(scrollContent.lookup("#confirmTransferButton"));
                        assertNotNull(scrollContent.lookup("#releaseAllocationsTable"));
                        @SuppressWarnings("unchecked")
                        TableView<ProductionItemRow> itemsTable =
                                (TableView<ProductionItemRow>) scrollContent.lookup("#itemsTable");
                        assertTrue(
                                itemsTable.getColumns().stream()
                                        .anyMatch(c -> "Выбор".equals(c.getText())));
                        @SuppressWarnings("unchecked")
                        TableView<MaterialAvailabilityRow> materialsTable =
                                (TableView<MaterialAvailabilityRow>)
                                        scrollContent.lookup("#materialsTable");
                        assertTrue(
                                materialsTable.getColumns().stream()
                                        .anyMatch(c -> "Другие склады".equals(c.getText())));
                        @SuppressWarnings("unchecked")
                        TableView<ReleaseCellAllocationRow> releaseAllocationsTable =
                                (TableView<ReleaseCellAllocationRow>)
                                        scrollContent.lookup("#releaseAllocationsTable");
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
    void materialRequirementPrepareAndQuantityEditKeepLineSelection() throws Exception {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID itemId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID specId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID destWh = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID destCell = UUID.fromString("77777777-7777-7777-7777-777777777777");
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
        applicationApi.productionWarehouseId = destWh;
        applicationApi.requirement =
                new MaterialRequirementView(
                        templateId,
                        orderId,
                        destWh,
                        Instant.parse("2026-01-01T12:00:00Z"),
                        Instant.parse("2026-01-01T12:00:00Z"),
                        1L,
                        MaterialRequirementStatusView.DRAFT,
                        List.of(
                                new MaterialRequirementLineView(
                                        lineId,
                                        materialRef,
                                        "ART-1",
                                        "Материал",
                                        "белый",
                                        "шт",
                                        new BigDecimal("1.000000"),
                                        List.of(itemId))));

        ProductionWorkbenchUiTestSupport.StubOrderQuery orderQuery =
                new ProductionWorkbenchUiTestSupport.StubOrderQuery();
        orderQuery.order = ProductionWorkbenchUiTestSupport.order(orderId, "ORD-1");
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(orderId, itemId, "1"));

        ProductionWorkbenchUiTestSupport.StubWarehouseApi warehouseApi =
                new ProductionWorkbenchUiTestSupport.StubWarehouseApi();
        warehouseApi.cellsByWarehouse.put(
                destWh, List.of(new StorageCellView(destCell, destWh, "P-X", true)));

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
                        viewModel.prepareMaterialRequirement();

                        ScrollPane scroll = (ScrollPane) root.lookup("#rootScroll");
                        Parent scrollContent = (Parent) scroll.getContent();
                        @SuppressWarnings("unchecked")
                        TableView<MaterialRequirementLineRow> requirementLinesTable =
                                (TableView<MaterialRequirementLineRow>)
                                        scrollContent.lookup("#requirementLinesTable");
                        Button applyRequirementQtyButton =
                                (Button) scrollContent.lookup("#applyRequirementQtyButton");
                        assertEquals(
                                "Применить количество", applyRequirementQtyButton.getText());
                        assertEquals(5, requirementLinesTable.getColumns().size());
                        assertEquals(
                                List.of("Артикул", "Наименование", "Цвет", "Количество", "Ед."),
                                requirementLinesTable.getColumns().stream()
                                        .map(TableColumn::getText)
                                        .toList());

                        MaterialRequirementLineRow line = viewModel.requirementLines().get(0);
                        requirementLinesTable.getSelectionModel().select(line);
                        viewModel.selectRequirementLine(line.lineId());
                        assertEquals("1.000000", line.quantity());

                        line.setQuantity("2.000000");
                        applyRequirementQtyButton.fire();
                        assertEquals(
                                line.lineId(),
                                viewModel.selectedRequirementLineIdProperty().get());
                        assertEquals(
                                "2.000000", viewModel.requirementLines().get(0).quantity());
                        assertEquals(1, applicationApi.changeQtyCalls.size());
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Material requirement FX regression failed", error.get());
        }
    }
}
