package com.tmp.ui.shell.screen.orderimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportFileParseResult;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.api.imports.StxtOrderFileParser;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.navigation.NavigationServices;
import com.tmp.ui.shell.navigation.ScreenRegistration;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
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
        assertEquals("Импорт заказа", ((Label) loaded.root.lookup("#titleLabel")).getText());
    }

    @Test
    void selectFileShowsPreview() throws Exception {
        FakeImportService imports = successService();
        OrderImportViewModel viewModel = newFakeViewModel(imports, new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> viewModel.selectFile(Path.of("preview.stxt")));

        TextField fileName = (TextField) loaded.root.lookup("#fileNameField");
        Label orderNumber = (Label) loaded.root.lookup("#previewOrderNumberLabel");
        Label positions = (Label) loaded.root.lookup("#previewPositionCountLabel");
        Label products = (Label) loaded.root.lookup("#previewProductQuantityLabel");
        Label lines = (Label) loaded.root.lookup("#previewSpecificationLineCountLabel");
        assertEquals("preview.stxt", fileName.getText());
        assertEquals("ORD-FX", orderNumber.getText());
        assertEquals("2", positions.getText());
        assertEquals("3", products.getText());
        assertEquals("4", lines.getText());
        assertEquals(1, imports.previewCalls.get());
    }

    @Test
    void errorsAreDisplayedAndImportDisabled() throws Exception {
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
        assertTrue(importButton.isDisabled());
    }

    @Test
    void importEnabledOnSuccessPreview() throws Exception {
        OrderImportViewModel viewModel =
                newFakeViewModel(successService(), new FakeStxtParser());
        Loaded loaded = loadScreen(viewModel);

        runFx(() -> viewModel.selectFile(Path.of("ok.stxt")));

        Button importButton = (Button) loaded.root.lookup("#importButton");
        assertFalse(importButton.isDisabled());
    }

    @Test
    void confirmCallsImportCoreAndShowsResult() throws Exception {
        FakeImportService imports = successService();
        imports.confirmResult = OrderImportConfirmResult.of(
                OrderId.generate(),
                "ORD-FX",
                UUID.randomUUID(),
                2,
                4,
                Instant.parse("2026-07-31T00:00:00Z"));
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
        assertTrue(success.getText().contains("ORD-FX"));
        assertTrue(success.getText().contains("Позиций: 2"));
        Button importButton = (Button) loaded.root.lookup("#importButton");
        assertTrue(importButton.isDisabled());
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
        var navigation = NavigationServices.createDefault();
        navigation.register(new ScreenRegistration(
                UiShellScreens.ORDER_IMPORT_SCREEN_ID,
                UiShellScreens.ORDER_IMPORT_FXML,
                () -> viewModel));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<Parent> rootRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Parent root = navigation.load(UiShellScreens.ORDER_IMPORT_SCREEN_ID);
                Stage stage = new Stage();
                stage.setScene(new Scene(root));
                rootRef.set(root);
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
        return new Loaded(rootRef.get());
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

    private record Loaded(Parent root) {
    }

    private static final class FakeStxtParser implements StxtOrderFileParser {
        private OrderImportFileParseResult result = OrderImportFileParseResult.of(
                OrderImportBatch.of("STXT", "sample.stxt", "checksum", "ORD-FX", List.of()),
                List.of(),
                List.of(),
                "UTF-8");

        @Override
        public OrderImportFileParseResult parseFile(Path file) {
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
