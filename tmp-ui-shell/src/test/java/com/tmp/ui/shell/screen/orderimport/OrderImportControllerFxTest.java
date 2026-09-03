package com.tmp.ui.shell.screen.orderimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportConflictException;
import com.tmp.order.api.imports.OrderImportFileParseResult;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.api.imports.StxtOrderFileParser;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderImportControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void opensImportScreen() throws Exception {
        Loaded loaded = loadScreen(newFakeViewModel(successService(), new FakeStxtParser()));
        assertNotNull(loaded.root.lookup("#titleLabel"));
        assertNotNull(loaded.root.lookup("#subtitleLabel"));
        assertNotNull(loaded.root.lookup("#selectFileButton"));
        assertNotNull(loaded.root.lookup("#formatHintLabel"));
        assertNotNull(loaded.root.lookup("#emptyFilePane"));
        assertNotNull(loaded.root.lookup("#selectedFilePane"));
        assertNotNull(loaded.root.lookup("#selectedFileNameLabel"));
        assertNotNull(loaded.root.lookup("#selectOtherFileButton"));
        assertNotNull(loaded.root.lookup("#loadingLabel"));
        assertNotNull(loaded.root.lookup("#previewPane"));
        assertNotNull(loaded.root.lookup("#previewStatusLabel"));
        assertNotNull(loaded.root.lookup("#previewOrdersLabel"));
        assertNotNull(loaded.root.lookup("#previewOrderNumbersLabel"));
        assertNotNull(loaded.root.lookup("#previewPositionCountLabel"));
        assertNotNull(loaded.root.lookup("#previewProductQuantityLabel"));
        assertNotNull(loaded.root.lookup("#previewSpecificationLineCountLabel"));
        assertNotNull(loaded.root.lookup("#previewErrorCountLabel"));
        assertNotNull(loaded.root.lookup("#previewWarningCountLabel"));
        assertNotNull(loaded.root.lookup("#problemsEmptyLabel"));
        assertNotNull(loaded.root.lookup("#problemsPane"));
        assertNotNull(loaded.root.lookup("#problemsTable"));
        @SuppressWarnings("unchecked")
        TableView<OrderImportProblemRow> problemsTable =
                (TableView<OrderImportProblemRow>) loaded.root.lookup("#problemsTable");
        assertEquals(3, problemsTable.getColumns().size());
        assertEquals("Тип", problemsTable.getColumns().get(0).getText());
        assertEquals("Где", problemsTable.getColumns().get(1).getText());
        assertEquals("Сообщение", problemsTable.getColumns().get(2).getText());
        assertNotNull(loaded.root.lookup("#importButton"));
        assertNotNull(loaded.root.lookup("#statusLabel"));
        assertNotNull(loaded.root.lookup("#errorLabel"));
        assertNotNull(loaded.root.lookup("#successPane"));
        assertNotNull(loaded.root.lookup("#successTitleLabel"));
        assertNotNull(loaded.root.lookup("#successMessageLabel"));
        assertNotNull(loaded.root.lookup("#openImportedOrderButton"));
        assertNotNull(loaded.root.lookup("#goToOrderListButton"));
        assertNotNull(loaded.root.lookup("#importAnotherButton"));
        assertNull(loaded.root.lookup("#fileNameField"));
        assertNull(loaded.root.lookup("#validateButton"));
        assertNull(loaded.root.lookup("#cancelButton"));
        assertNull(loaded.root.lookup("#errorsTable"));
        assertNull(loaded.root.lookup("#warningsTable"));
        assertEquals("Импорт заказа", ((Label) loaded.root.lookup("#titleLabel")).getText());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
        assertTrue(loaded.root.lookup("#emptyFilePane").isVisible());
        assertFalse(loaded.root.lookup("#selectedFilePane").isVisible());
        assertFalse(loaded.root.lookup("#successPane").isVisible());
    }

    @Test
    void fileChooserSelectionCallsAdapterAndShowsFileName() throws Exception {
        FakeStxtParser stxt = new FakeStxtParser();
        FakeImportService imports = successService();
        OrderImportViewModel viewModel = newFakeViewModel(imports, stxt);
        Loaded loaded = loadScreen(viewModel);
        File chosen = new File("preview.stxt");

        runFx(() -> {
            loaded.controller.setFileChooserOpenerForTest(() -> chosen);
            loaded.controller.chooseFileForTest();
        });

        assertEquals(1, stxt.parseCalls.get());
        assertEquals(1, imports.previewCalls.get());
        assertEquals("preview.stxt", ((Label) loaded.root.lookup("#selectedFileNameLabel")).getText());
        assertTrue(loaded.root.lookup("#selectedFilePane").isVisible());
        assertFalse(loaded.root.lookup("#emptyFilePane").isVisible());
        assertEquals("Заказ: ORD-FX", ((Label) loaded.root.lookup("#previewOrdersLabel")).getText());
        assertEquals("Позиций: 2", ((Label) loaded.root.lookup("#previewPositionCountLabel")).getText());
        assertEquals("Изделий: 3", ((Label) loaded.root.lookup("#previewProductQuantityLabel")).getText());
        assertEquals(
                "Строк спецификации: 4",
                ((Label) loaded.root.lookup("#previewSpecificationLineCountLabel")).getText());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
        assertEquals(
                "Предупреждения: 0",
                ((Label) loaded.root.lookup("#previewWarningCountLabel")).getText());
        assertEquals(
                OrderImportViewModel.MSG_PREVIEW_OK,
                ((Label) loaded.root.lookup("#previewStatusLabel")).getText());
    }

    @Test
    void fileChooserCancelDoesNotCallAdapterOrChangePreview() throws Exception {
        FakeStxtParser stxt = new FakeStxtParser();
        FakeImportService imports = successService();
        OrderImportViewModel viewModel = newFakeViewModel(imports, stxt);
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            loaded.controller.setFileChooserOpenerForTest(() -> null);
            loaded.controller.chooseFileForTest();
        });

        assertEquals(0, stxt.parseCalls.get());
        assertEquals(0, imports.previewCalls.get());
        assertEquals("", ((Label) loaded.root.lookup("#selectedFileNameLabel")).getText());
        assertTrue(loaded.root.lookup("#emptyFilePane").isVisible());
        assertEquals("", ((Label) loaded.root.lookup("#previewOrdersLabel")).getText());
        assertTrue(((Label) loaded.root.lookup("#errorLabel")).getText().isBlank()
                || !((Label) loaded.root.lookup("#errorLabel")).isVisible());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
    }

    @Test
    void previewErrorShowsProblemsAndDisablesImport() throws Exception {
        FakeStxtParser stxt = new FakeStxtParser();
        stxt.result = OrderImportFileParseResult.of(
                List.of(),
                List.of(OrderImportProblem.error(
                        "MISSING_COLUMN",
                        "header",
                        null,
                        null,
                        "Артикул",
                        null,
                        "Файл не содержит обязательную колонку Артикул")),
                List.of(),
                null);
        OrderImportViewModel viewModel = newFakeViewModel(new FakeImportService(), stxt);
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> viewModel.selectFile(Path.of("bad.stxt")));

        @SuppressWarnings("unchecked")
        TableView<OrderImportProblemRow> problems =
                (TableView<OrderImportProblemRow>) loaded.root.lookup("#problemsTable");
        Button importButton = (Button) loaded.root.lookup("#importButton");
        assertEquals(1, problems.getItems().size());
        assertEquals(
                "Файл не содержит обязательную колонку Артикул",
                problems.getItems().get(0).message());
        assertEquals("Ошибки: 1", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
        assertEquals(
                OrderImportViewModel.MSG_PREVIEW_ERRORS,
                ((Label) loaded.root.lookup("#previewStatusLabel")).getText());
        assertTrue(loaded.root.lookup("#problemsPane").isVisible());
        assertTrue(importButton.isDisabled());
    }

    @Test
    void previewSuccessEnablesImportAndShowsCounters() throws Exception {
        OrderImportViewModel viewModel =
                newFakeViewModel(successService(), new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> viewModel.selectFile(Path.of("ok.stxt")));

        Button importButton = (Button) loaded.root.lookup("#importButton");
        assertFalse(importButton.isDisabled());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
        assertEquals(
                "Предупреждения: 0",
                ((Label) loaded.root.lookup("#previewWarningCountLabel")).getText());
    }

    @Test
    void noProblemsHidesProblemsTable() throws Exception {
        OrderImportViewModel viewModel =
                newFakeViewModel(successService(), new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> viewModel.selectFile(Path.of("ok.stxt")));

        assertEquals(
                OrderImportViewModel.MSG_NO_PROBLEMS,
                ((Label) loaded.root.lookup("#problemsEmptyLabel")).getText());
        assertTrue(loaded.root.lookup("#problemsEmptyLabel").isVisible());
        assertFalse(loaded.root.lookup("#problemsPane").isVisible());
        assertFalse(((Button) loaded.root.lookup("#importButton")).isDisabled());
    }

    @Test
    void warningsOnlyEnablesImport() throws Exception {
        OrderImportViewModel viewModel = newFakeViewModel(warningService(), new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> viewModel.selectFile(Path.of("warn.stxt")));

        assertFalse(((Button) loaded.root.lookup("#importButton")).isDisabled());
        assertTrue(loaded.root.lookup("#problemsPane").isVisible());
        assertEquals(
                OrderImportViewModel.MSG_PREVIEW_WARNINGS,
                ((Label) loaded.root.lookup("#previewStatusLabel")).getText());
        assertEquals("Предупреждения: 1", ((Label) loaded.root.lookup("#previewWarningCountLabel")).getText());
    }

    @Test
    void multiOrderPreviewShowsOrderCount() throws Exception {
        OrderImportViewModel viewModel = newFakeViewModel(multiSuccessService(), new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> viewModel.selectFile(Path.of("multi.stxt")));

        assertEquals("Заказов: 2", ((Label) loaded.root.lookup("#previewOrdersLabel")).getText());
        assertEquals(
                "Номера: ORD-A, ORD-B",
                ((Label) loaded.root.lookup("#previewOrderNumbersLabel")).getText());
        assertTrue(loaded.root.lookup("#previewOrderNumbersLabel").isVisible());
        assertEquals("Импортировать заказы", ((Button) loaded.root.lookup("#importButton")).getText());
        assertFalse(((Button) loaded.root.lookup("#importButton")).isDisabled());
    }

    @Test
    void confirmDialogCancelDoesNotCallConfirm() throws Exception {
        FakeImportService imports = successService();
        imports.confirmResult = OrderImportConfirmResult.of(OrderId.generate(), "ORD-FX", 2, 4);
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            loaded.controller.setConfirmDialogOpenerForTest(() -> false);
            loaded.controller.importForTest();
        });

        assertEquals(0, imports.confirmCalls.get());
        assertFalse(loaded.root.lookup("#successPane").isVisible());
        assertTrue(viewModel.canImportProperty().get());
    }

    @Test
    void confirmDialogOkCallsConfirmOnce() throws Exception {
        FakeImportService imports = successService();
        imports.confirmResult = OrderImportConfirmResult.of(OrderId.generate(), "ORD-FX", 2, 4);
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            loaded.controller.setConfirmDialogOpenerForTest(() -> true);
            loaded.controller.importForTest();
        });

        assertEquals(1, imports.confirmCalls.get());
        assertNotNull(imports.lastConfirmedPlan.get());
        assertTrue(loaded.root.lookup("#successPane").isVisible());
    }

    @Test
    void confirmSuccessCallsImportCoreAndShowsResult() throws Exception {
        FakeImportService imports = successService();
        imports.confirmResult = OrderImportConfirmResult.of(
                OrderId.generate(),
                "26062891",
                5,
                20);
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            loaded.controller.setConfirmDialogOpenerForTest(() -> true);
            loaded.controller.importForTest();
        });

        assertEquals(1, imports.confirmCalls.get());
        assertNotNull(imports.lastConfirmedPlan.get());
        Label successTitle = (Label) loaded.root.lookup("#successTitleLabel");
        Label success = (Label) loaded.root.lookup("#successMessageLabel");
        assertTrue(loaded.root.lookup("#successPane").isVisible());
        assertFalse(loaded.root.lookup("#workingPane").isVisible());
        assertEquals("Импорт завершён", successTitle.getText());
        assertTrue(success.getText().contains("Заказ №26062891 создан и передан в работу."));
        assertTrue(success.getText().contains("Позиций создано: 5"));
        assertTrue(success.getText().contains("Строк спецификации: 20"));
        assertTrue(((Button) loaded.root.lookup("#importButton")).isDisabled());
        assertTrue(((Button) loaded.root.lookup("#openImportedOrderButton")).isVisible());
    }

    @Test
    void singleSuccessShowsOpenOrderButton() throws Exception {
        FakeImportService imports = successService();
        OrderId created = OrderId.generate();
        imports.confirmResult = OrderImportConfirmResult.of(created, "ORD-FX", 2, 4);
        AtomicReference<OrderId> opened = new AtomicReference<>();
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        viewModel.setOnOpenImportedOrder(opened::set);
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            loaded.controller.setConfirmDialogOpenerForTest(() -> true);
            loaded.controller.importForTest();
            ((Button) loaded.root.lookup("#openImportedOrderButton")).fire();
        });

        assertTrue(((Button) loaded.root.lookup("#openImportedOrderButton")).isVisible());
        assertEquals(created, opened.get());
    }

    @Test
    void multiSuccessHidesOpenOrderButton() throws Exception {
        FakeImportService imports = multiSuccessService();
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("multi.stxt"));
            loaded.controller.setConfirmDialogOpenerForTest(() -> true);
            loaded.controller.importForTest();
        });

        assertTrue(loaded.root.lookup("#successPane").isVisible());
        assertFalse(((Button) loaded.root.lookup("#openImportedOrderButton")).isVisible());
        assertTrue(((Label) loaded.root.lookup("#successMessageLabel"))
                .getText()
                .contains("Импортировано заказов: 2"));
    }

    @Test
    void importAnotherResetsToFileSelection() throws Exception {
        FakeImportService imports = successService();
        imports.confirmResult = OrderImportConfirmResult.of(OrderId.generate(), "ORD-FX", 2, 4);
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            loaded.controller.setConfirmDialogOpenerForTest(() -> true);
            loaded.controller.importForTest();
            ((Button) loaded.root.lookup("#importAnotherButton")).fire();
        });

        assertFalse(loaded.root.lookup("#successPane").isVisible());
        assertTrue(loaded.root.lookup("#workingPane").isVisible());
        assertTrue(loaded.root.lookup("#emptyFilePane").isVisible());
        assertFalse(loaded.root.lookup("#selectedFilePane").isVisible());
        assertEquals("", ((Label) loaded.root.lookup("#selectedFileNameLabel")).getText());
        assertTrue(((Button) loaded.root.lookup("#importButton")).isDisabled());
        assertFalse(((Button) loaded.root.lookup("#selectFileButton")).isDisabled());
    }

    @Test
    void missingCreatePermissionDisablesActions() throws Exception {
        FakeStxtParser stxt = new FakeStxtParser();
        FakeImportService imports = successService();
        OrderImportViewModel viewModel =
                new OrderImportViewModel(imports, stxt, new FakeAuthorization());
        Loaded loaded = loadScreen(viewModel);

        assertTrue(((Button) loaded.root.lookup("#selectFileButton")).isDisabled());
        assertTrue(((Button) loaded.root.lookup("#selectOtherFileButton")).isDisabled());
        assertTrue(((Button) loaded.root.lookup("#importButton")).isDisabled());
        assertFalse(viewModel.canSelectFileProperty().get());
        assertFalse(viewModel.canImportProperty().get());

        runFx(() -> {
            loaded.controller.setFileChooserOpenerForTest(() -> new File("denied.stxt"));
            loaded.controller.chooseFileForTest();
            loaded.controller.importForTest();
        });

        assertEquals(0, stxt.parseCalls.get());
        assertEquals(0, imports.previewCalls.get());
        assertEquals(0, imports.confirmCalls.get());
        assertTrue(loaded.root.lookup("#emptyFilePane").isVisible());
    }

    @Test
    void confirmConflictShowsExactMessage() throws Exception {
        FakeImportService imports = successService();
        imports.confirmException = new OrderImportConflictException();
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            loaded.controller.setConfirmDialogOpenerForTest(() -> true);
            loaded.controller.importForTest();
        });

        Label error = (Label) loaded.root.lookup("#errorLabel");
        assertTrue(error.isVisible());
        assertEquals(OrderImportConflictException.USER_MESSAGE, error.getText());
    }

    @Test
    void cancelDoesNotChangePersistedStateAndLeavesImportCoreIdle() throws Exception {
        FakeImportService imports = successService();
        FakeStxtParser stxt = new FakeStxtParser();
        OrderImportViewModel viewModel = newFakeViewModel(imports, stxt);
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            assertFalse(((Button) loaded.root.lookup("#importButton")).isDisabled());
            viewModel.cancel();
        });

        assertEquals(0, imports.confirmCalls.get());
        assertEquals("", ((Label) loaded.root.lookup("#selectedFileNameLabel")).getText());
        assertEquals("", ((Label) loaded.root.lookup("#previewOrdersLabel")).getText());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
        assertTrue(((Button) loaded.root.lookup("#importButton")).isDisabled());
        assertTrue(loaded.root.lookup("#emptyFilePane").isVisible());
        assertNull(loaded.root.lookup("#cancelButton"));
    }

    private static OrderImportViewModel newFakeViewModel(
            OrderImportService imports, StxtOrderFileParser stxt) {
        return new OrderImportViewModel(
                imports, stxt, new FakeAuthorization(PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION)));
    }

    private static FakeImportService successService() {
        FakeImportService imports = new FakeImportService();
        imports.preview = OrderImportPreview.of(
                "file.stxt",
                "ORD-FX",
                2,
                new BigDecimal("3"),
                4,
                List.of(),
                List.of(),
                new FakePlan("ORD-FX"));
        return imports;
    }

    private static FakeImportService warningService() {
        FakeImportService imports = new FakeImportService();
        imports.preview = OrderImportPreview.of(
                "file.stxt",
                "ORD-W",
                1,
                new BigDecimal("1"),
                1,
                List.of(),
                List.of(OrderImportProblem.warning(
                        "UNKNOWN_HEADER",
                        "header",
                        null,
                        null,
                        "Extra",
                        null,
                        "Неизвестная колонка Extra")),
                new FakePlan("ORD-W"));
        return imports;
    }

    private static FakeImportService multiSuccessService() {
        FakeImportService imports = new FakeImportService();
        imports.preview = OrderImportPreview.of(
                "file.stxt",
                "ORD-A, ORD-B",
                2,
                2,
                new BigDecimal("3"),
                4,
                List.of(),
                List.of(),
                new FakePlan("ORD-A, ORD-B"));
        imports.confirmResult = OrderImportConfirmResult.of(
                List.of(
                        OrderImportConfirmResult.ImportedOrder.of(OrderId.generate(), "ORD-A"),
                        OrderImportConfirmResult.ImportedOrder.of(OrderId.generate(), "ORD-B")),
                5,
                20);
        return imports;
    }

    private static Loaded loadScreen(OrderImportViewModel viewModel) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<OrderImportController> controllerRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(
                        OrderImportController.class.getClassLoader().getResource(
                                UiShellScreens.ORDER_IMPORT_FXML));
                Parent root = loader.load();
                OrderImportController controller = loader.getController();
                controller.setViewModel(viewModel);
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                rootRef.set(root);
                controllerRef.set(controller);
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("Order import FX load failed", error.get());
        }
        assertNotNull(rootRef.get());
        assertNotNull(controllerRef.get());
        return new Loaded(rootRef.get(), controllerRef.get());
    }

    private static void runFx(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable throwable) {
                error.set(throwable);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (error.get() != null) {
            throw new AssertionError("FX action failed", error.get());
        }
    }

    private record Loaded(Parent root, OrderImportController controller) {
    }

    private static final class FakeStxtParser implements StxtOrderFileParser {
        private final AtomicInteger parseCalls = new AtomicInteger();
        private OrderImportFileParseResult result = OrderImportFileParseResult.of(
                List.of(testBatch("ORD-FX")),
                List.of(),
                List.of(),
                "UTF-8");

        @Override
        public OrderImportFileParseResult parseFile(Path file) {
            parseCalls.incrementAndGet();
            return result;
        }

        @Override
        public OrderImportFileParseResult parse(byte[] content, String sourceReference) {
            return result;
        }
    }

    private static final class FakeImportService implements OrderImportService {
        private final AtomicInteger previewCalls = new AtomicInteger();
        private final AtomicInteger confirmCalls = new AtomicInteger();
        private final AtomicReference<PreparedOrderImportPlan> lastConfirmedPlan =
                new AtomicReference<>();
        private OrderImportPreview preview;
        private RuntimeException confirmException;
        private OrderImportConfirmResult confirmResult;

        @Override
        public OrderImportPreview preview(OrderImportBatch batch) {
            previewCalls.incrementAndGet();
            return Objects.requireNonNull(preview, "preview");
        }

        @Override
        public OrderImportPreview preview(List<OrderImportBatch> batches) {
            previewCalls.incrementAndGet();
            return Objects.requireNonNull(preview, "preview");
        }

        @Override
        public OrderImportConfirmResult confirm(PreparedOrderImportPlan plan) {
            confirmCalls.incrementAndGet();
            lastConfirmedPlan.set(plan);
            if (confirmException != null) {
                throw confirmException;
            }
            return Objects.requireNonNull(confirmResult, "confirmResult");
        }
    }

    private static final class FakePlan implements PreparedOrderImportPlan {
        private final String orderNumber;

        private FakePlan(String orderNumber) {
            this.orderNumber = orderNumber;
        }

        @Override
        public List<OrderImportBatch> batches() {
            return List.of(batch());
        }

        @Override
        public OrderImportBatch batch() {
            return testBatch(orderNumber);
        }

        @Override
        public String sourceType() {
            return "STXT";
        }

        @Override
        public String sourceReference() {
            return "file.stxt";
        }

        @Override
        public String contentChecksum() {
            return "cs";
        }

        @Override
        public String orderNumber() {
            return orderNumber;
        }
    }

    private static OrderImportBatch testBatch(String orderNumber) {
        return OrderImportBatch.of(
                "STXT",
                "file.stxt",
                "cs",
                orderNumber,
                LocalDate.of(2026, 6, 25),
                null,
                "Test Client",
                List.of());
    }
}
