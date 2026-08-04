package com.tmp.ui.shell.screen.orderimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import javafx.scene.control.TextField;
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
        assertNotNull(loaded.root.lookup("#selectFileButton"));
        assertNotNull(loaded.root.lookup("#fileNameField"));
        assertNotNull(loaded.root.lookup("#importButton"));
        assertNotNull(loaded.root.lookup("#validateButton"));
        assertNotNull(loaded.root.lookup("#cancelButton"));
        assertNotNull(loaded.root.lookup("#previewErrorCountLabel"));
        assertNotNull(loaded.root.lookup("#previewWarningCountLabel"));
        assertEquals("Импорт заказа", ((Label) loaded.root.lookup("#titleLabel")).getText());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
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
        assertEquals("preview.stxt", ((TextField) loaded.root.lookup("#fileNameField")).getText());
        assertEquals("ORD-FX", ((Label) loaded.root.lookup("#previewOrderNumberLabel")).getText());
        assertEquals("2", ((Label) loaded.root.lookup("#previewPositionCountLabel")).getText());
        assertEquals("4", ((Label) loaded.root.lookup("#previewSpecificationLineCountLabel")).getText());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
        assertEquals(
                "Предупреждения: 0",
                ((Label) loaded.root.lookup("#previewWarningCountLabel")).getText());
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
        assertEquals("", ((TextField) loaded.root.lookup("#fileNameField")).getText());
        assertEquals("", ((Label) loaded.root.lookup("#previewOrderNumberLabel")).getText());
        assertTrue(((Label) loaded.root.lookup("#errorLabel")).getText().isBlank()
                || !((Label) loaded.root.lookup("#errorLabel")).isVisible());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
    }

    @Test
    void previewErrorShowsProblemsAndDisablesImport() throws Exception {
        FakeStxtParser stxt = new FakeStxtParser();
        stxt.result = OrderImportFileParseResult.of(
                null,
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
        TableView<OrderImportProblem> errors =
                (TableView<OrderImportProblem>) loaded.root.lookup("#errorsTable");
        Button importButton = (Button) loaded.root.lookup("#importButton");
        assertEquals(1, errors.getItems().size());
        assertEquals(
                "Файл не содержит обязательную колонку Артикул",
                errors.getItems().get(0).message());
        assertEquals("Ошибки: 1", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
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
            viewModel.confirmImport();
        });

        assertEquals(1, imports.confirmCalls.get());
        assertNotNull(imports.lastConfirmedPlan.get());
        Label success = (Label) loaded.root.lookup("#successLabel");
        assertTrue(success.isVisible());
        assertTrue(success.getText().contains("Заказ 26062891 успешно импортирован."));
        assertTrue(success.getText().contains("Создано позиций: 5."));
        assertTrue(success.getText().contains("Строк спецификации: 20."));
        assertTrue(((Button) loaded.root.lookup("#importButton")).isDisabled());
    }

    @Test
    void confirmConflictShowsExactMessage() throws Exception {
        FakeImportService imports = successService();
        imports.confirmException = new OrderImportConflictException();
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> {
            viewModel.selectFile(Path.of("ok.stxt"));
            viewModel.confirmImport();
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
        assertEquals("", ((TextField) loaded.root.lookup("#fileNameField")).getText());
        assertEquals("", ((Label) loaded.root.lookup("#previewOrderNumberLabel")).getText());
        assertEquals("Ошибки: 0", ((Label) loaded.root.lookup("#previewErrorCountLabel")).getText());
        assertTrue(((Button) loaded.root.lookup("#importButton")).isDisabled());
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
                OrderImportBatch.of("STXT", "sample.stxt", "checksum", "ORD-FX", List.of()),
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
        public OrderImportBatch batch() {
            return OrderImportBatch.of("STXT", "file.stxt", "cs", orderNumber, List.of());
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
}
