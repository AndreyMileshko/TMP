package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchController.ReleasePostConfirmOutcome;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.AllowAllAuthorization;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubApplicationApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubAuthentication;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubOrderQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubQueryApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWarehouseApi;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchUiTestSupport.StubWorklistQuery;
import com.tmp.ui.shell.screen.production.ProductionWorkbenchViewModel.ReleaseCompletionKind;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Regression for bound releaseButton.setDisable(true) aborting before confirmReleases / WARNING UI. */
class ReleasePostConfirmControllerTest {

    private static final UUID ORDER_A = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ITEM_A1 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID SPEC = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CELL = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID MAT = UUID.fromString("77777777-7777-4777-8777-777777777777");

    private StubQueryApi queryApi;
    private StubApplicationApi applicationApi;
    private StubOrderQuery orderQuery;
    private StubWorklistQuery worklistQuery;
    private StubWarehouseApi warehouseApi;
    private ProductionWorkbenchViewModel viewModel;

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @BeforeEach
    void setUp() {
        queryApi = new StubQueryApi();
        applicationApi = new StubApplicationApi();
        orderQuery = new StubOrderQuery();
        worklistQuery = new StubWorklistQuery();
        warehouseApi = new StubWarehouseApi();
        viewModel =
                new ProductionWorkbenchViewModel(
                        queryApi,
                        applicationApi,
                        orderQuery,
                        worklistQuery,
                        warehouseApi,
                        new AllowAllAuthorization(),
                        new StubAuthentication());
        seedOrder();
    }

    @Test
    void boundReleaseButtonSetDisableThrowsDocumentingOriginalBug() throws Exception {
        runOnFxThread(
                () -> {
                    Button releaseButton = new Button("Release");
                    releaseButton
                            .disableProperty()
                            .bind(
                                    viewModel
                                            .releaseEnabledProperty()
                                            .not()
                                            .or(viewModel.loadingProperty()));
                    RuntimeException thrown =
                            assertThrows(
                                    RuntimeException.class, () -> releaseButton.setDisable(true));
                    assertTrue(
                            thrown.getMessage() != null
                                    && thrown.getMessage().contains("A bound value cannot be set"));
                });
    }

    @Test
    void executeConfirmedReleaseReachesConfirmWithoutBoundSetDisableAndRestoresBusyState()
            throws Exception {
        runOnFxThread(
                () -> {
                    Button releaseButton = new Button("Release");
                    releaseButton
                            .disableProperty()
                            .bind(
                                    viewModel
                                            .releaseEnabledProperty()
                                            .not()
                                            .or(viewModel.loadingProperty()));
                    viewModel.loadTree();
                    viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
                    var quantityRows = viewModel.loadReleaseStep1().rows();
                    List<ReleaseMaterialRow> materials = allocateDefaults(quantityRows);

                    assertFalse(viewModel.loadingProperty().get());
                    assertFalse(releaseButton.isDisable());

                    ReleasePostConfirmOutcome outcome =
                            ProductionWorkbenchController.executeConfirmedRelease(
                                    viewModel, quantityRows, materials);

                    assertFalse(viewModel.loadingProperty().get());
                    // Success clears released selection → releaseEnabled becomes false via existing bind.
                    assertFalse(viewModel.releaseEnabledProperty().get());
                    assertTrue(releaseButton.isDisable());
                    assertFalse(outcome.unexpected());
                    assertEquals(ReleaseCompletionKind.FULL_SUCCESS, outcome.kind());
                    assertEquals(1, applicationApi.releaseOrderCalls.size());
                    assertTrue(outcome.summary().contains("\u0412\u044B\u043F\u0443\u0449\u0435\u043D\u043E"));
                    assertEquals("", viewModel.errorMessageProperty().get());
                });
    }

    @Test
    void executeConfirmedReleaseInsufficientStockYieldsTotalFailureWithoutThrowingOnBoundButton()
            throws Exception {
        runOnFxThread(
                () -> {
                    Button releaseButton = new Button("Release");
                    releaseButton
                            .disableProperty()
                            .bind(
                                    viewModel
                                            .releaseEnabledProperty()
                                            .not()
                                            .or(viewModel.loadingProperty()));
                    applicationApi.releaseFailuresByOrder.put(
                            ORDER_A,
                            new RuntimeException(
                                    "Insufficient production warehouse stock for material "
                                            + MAT
                                            + " in cell "
                                            + CELL
                                            + ": available=0, required=12.5"));
                    viewModel.loadTree();
                    viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
                    var quantityRows = viewModel.loadReleaseStep1().rows();
                    List<ReleaseMaterialRow> materials = allocateDefaults(quantityRows);

                    ReleasePostConfirmOutcome outcome =
                            ProductionWorkbenchController.executeConfirmedRelease(
                                    viewModel, quantityRows, materials);

                    assertFalse(outcome.unexpected());
                    assertEquals(ReleaseCompletionKind.TOTAL_FAILURE, outcome.kind());
                    assertTrue(
                            outcome.summary()
                                    .contains(
                                            "\u041D\u0435\u0434\u043E\u0441\u0442\u0430\u0442\u043E\u0447\u043D\u043E \u043C\u0430\u0442\u0435\u0440\u0438\u0430\u043B\u0430"));
                    assertTrue(
                            outcome.summary()
                                    .contains(
                                            "\u0414\u043E\u0441\u0442\u0443\u043F\u043D\u043E: 0"));
                    assertTrue(
                            outcome.summary()
                                    .contains(
                                            "\u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F: 12.5"));
                    assertTrue(
                            outcome.summary()
                                    .contains(
                                            "\u0412\u044B\u043F\u0443\u0441\u043A \u043D\u0435 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D"));
                    assertEquals(outcome.summary(), viewModel.errorMessageProperty().get());
                    assertFalse(viewModel.loadingProperty().get());
                    assertFalse(releaseButton.isDisable());
                    assertEquals(1, applicationApi.releaseOrderCalls.size());
                });
    }

    @Test
    void executeConfirmedReleaseUnexpectedExceptionSurfacesMappedMessageAndClearsBusy()
            throws Exception {
        runOnFxThread(
                () -> {
                    Button releaseButton = new Button("Release");
                    releaseButton
                            .disableProperty()
                            .bind(
                                    viewModel
                                            .releaseEnabledProperty()
                                            .not()
                                            .or(viewModel.loadingProperty()));
                    viewModel.loadTree();
                    viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
                    var quantityRows = viewModel.loadReleaseStep1().rows();
                    List<ReleaseMaterialRow> materials = allocateDefaults(quantityRows);
                    worklistQuery.listFailure =
                            new RuntimeException("unexpected-post-confirm-boom");

                    ReleasePostConfirmOutcome outcome =
                            ProductionWorkbenchController.executeConfirmedRelease(
                                    viewModel, quantityRows, materials);

                    assertTrue(outcome.unexpected());
                    assertEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, outcome.summary());
                    assertEquals(
                            ProductionUiErrorMapper.TECHNICAL_FAILURE,
                            viewModel.errorMessageProperty().get());
                    assertFalse(viewModel.loadingProperty().get());
                    // Confirm succeeded before refresh failed; success cleanup already cleared selection.
                    assertTrue(releaseButton.isDisable());
                    assertEquals(1, applicationApi.releaseOrderCalls.size());
                });
    }

    private List<ReleaseMaterialRow> allocateDefaults(List<ReleaseQuantityRow> quantityRows) {
        List<ReleaseMaterialRow> materials = viewModel.prepareReleaseMaterialRows(quantityRows);
        for (ReleaseMaterialRow row : materials) {
            ReleaseMaterialRow.CellAllocation allocation = row.addAllocation();
            StorageCellChoice choice =
                    row.cellChoices().isEmpty()
                            ? new StorageCellChoice(
                                    CELL, applicationApi.productionWarehouseId, "P-01", true)
                            : row.cellChoices().getFirst();
            if (row.cellChoices().isEmpty()) {
                row.cellChoices().add(choice);
            }
            allocation.setProductionCell(choice);
            allocation.setQuantity(row.actualQuantity());
        }
        return materials;
    }

    private void seedOrder() {
        worklistQuery.rows.add(
                ProductionWorkbenchUiTestSupport.worklistRow(ORDER_A, "4183", "Client"));
        orderQuery.orders.put(
                OrderId.of(ORDER_A), ProductionWorkbenchUiTestSupport.order(ORDER_A, "4183"));
        orderQuery.items.add(ProductionWorkbenchUiTestSupport.item(ORDER_A, ITEM_A1, "1"));
        queryApi.listFacts.put(
                ORDER_A,
                ProductionWorkbenchUiTestSupport.productionListFacts(
                        ORDER_A, OrderProductionViewStatus.IN_PRODUCTION, 10L, 6L, 4L));
        queryApi.putItemState(
                new ItemProductionStateView(
                        ORDER_A,
                        ITEM_A1,
                        SPEC,
                        ItemProductionStateStatus.PARTIALLY_RELEASED,
                        10L,
                        10L,
                        4L,
                        6L,
                        Optional.empty(),
                        Instant.parse("2026-01-01T00:00:00Z"),
                        List.of()));
        applicationApi.putOrderQuantityMode(ORDER_A, QuantityModeView.STANDARD, 1L);
        warehouseApi.cellsByWarehouse.put(
                applicationApi.productionWarehouseId,
                List.of(
                        new StorageCellView(
                                CELL, applicationApi.productionWarehouseId, "P-01", true)));
    }

    private static void runOnFxThread(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        action.run();
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        if (error.get() != null) {
            if (error.get() instanceof Error err) {
                throw err;
            }
            if (error.get() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError("FX test failed", error.get());
        }
    }
}