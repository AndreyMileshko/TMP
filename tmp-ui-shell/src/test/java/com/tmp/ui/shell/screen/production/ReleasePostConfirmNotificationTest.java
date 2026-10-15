package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Focused regression: one post-confirm notification — clear duplicate inline before WARNING. */
class ReleasePostConfirmNotificationTest {

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
    void totalFailureInlineErrorClearedBeforeWarningKeepsModalText() throws Exception {
        runOnFxThread(
                () -> {
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
                    List<ReleaseMaterialRow> materials =
                            viewModel.prepareReleaseMaterialRows(quantityRows);

                    ReleasePostConfirmOutcome outcome =
                            ProductionWorkbenchController.executeConfirmedRelease(
                                    viewModel, quantityRows, materials);
                    assertEquals(ReleaseCompletionKind.TOTAL_FAILURE, outcome.kind());
                    assertEquals(outcome.summary(), viewModel.errorMessageProperty().get());
                    assertTrue(outcome.summary().contains("Недостаточно"));
                    assertTrue(outcome.summary().contains("Доступно: 0"));
                    assertTrue(outcome.summary().contains("Требуется: 12.5"));

                    String modalText =
                            ProductionWorkbenchController.preparePostConfirmWarning(
                                    viewModel, outcome);
                    assertEquals("", viewModel.errorMessageProperty().get());
                    assertEquals(outcome.summary(), modalText);
                    assertTrue(modalText.contains("Недостаточно"));
                    assertTrue(modalText.contains("Доступно: 0"));
                    assertTrue(modalText.contains("Требуется: 12.5"));
                });
    }

    @Test
    void partialAndUnexpectedInlineErrorsClearedBeforeWarning() throws Exception {
        runOnFxThread(
                () -> {
                    viewModel.setErrorMessage("частично");
                    String partialModal =
                            ProductionWorkbenchController.preparePostConfirmWarning(
                                    viewModel,
                                    ReleasePostConfirmOutcome.handled(
                                            ReleaseCompletionKind.PARTIAL_SUCCESS, "частично"));
                    assertEquals("", viewModel.errorMessageProperty().get());
                    assertEquals("частично", partialModal);

                    viewModel.setErrorMessage(ProductionUiErrorMapper.TECHNICAL_FAILURE);
                    String unexpectedModal =
                            ProductionWorkbenchController.preparePostConfirmWarning(
                                    viewModel,
                                    ReleasePostConfirmOutcome.unexpected(
                                            ProductionUiErrorMapper.TECHNICAL_FAILURE));
                    assertEquals("", viewModel.errorMessageProperty().get());
                    assertEquals(ProductionUiErrorMapper.TECHNICAL_FAILURE, unexpectedModal);
                });
    }

    @Test
    void fullSuccessDoesNotRequireInlineClear() throws Exception {
        runOnFxThread(
                () -> {
                    viewModel.loadTree();
                    viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
                    var quantityRows = viewModel.loadReleaseStep1().rows();
                    List<ReleaseMaterialRow> materials =
                            viewModel.prepareReleaseMaterialRows(quantityRows);
                    ReleasePostConfirmOutcome outcome =
                            ProductionWorkbenchController.executeConfirmedRelease(
                                    viewModel, quantityRows, materials);
                    assertEquals(ReleaseCompletionKind.FULL_SUCCESS, outcome.kind());
                    assertEquals("", viewModel.errorMessageProperty().get());
                    ProductionWorkbenchController.clearPostConfirmDuplicateInlineError(
                            viewModel, outcome);
                    assertEquals("", viewModel.errorMessageProperty().get());
                });
    }

    @Test
    void prepareReleaseMaterialRowsCarriesUomFromPreview() throws Exception {
        runOnFxThread(
                () -> {
                    viewModel.loadTree();
                    viewModel.setItemSelected(new ProductionOrderItemRef(ORDER_A, ITEM_A1), true);
                    var quantityRows = viewModel.loadReleaseStep1().rows();
                    List<ReleaseMaterialRow> materials =
                            viewModel.prepareReleaseMaterialRows(quantityRows);
                    assertEquals(1, materials.size());
                    assertEquals("м", materials.getFirst().unitOfMeasure());
                    assertEquals("MAT-001", materials.getFirst().materialCode());
                    assertEquals("Материал", materials.getFirst().materialLabel());
                });
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
