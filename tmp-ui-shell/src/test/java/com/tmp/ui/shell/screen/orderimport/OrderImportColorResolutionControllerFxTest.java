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
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.PreparedOrderImportPlan;
import com.tmp.order.api.imports.StxtOrderFileParser;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.orderlist.FakeAuthorization;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class OrderImportColorResolutionControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void fxmlExposesCheckColorsButtonBoundToViewModel() throws Exception {
        OrderImportViewModel viewModel = new OrderImportViewModel(new StubImportService(), new StubStxtParser(), auth());
        Parent root = loadRoot(viewModel);
        Button checkColorsButton = (Button) root.lookup("#checkColorsButton");
        assertNotNull(checkColorsButton);
        assertEquals("Проверить цвета", checkColorsButton.getText());
        assertFalse(checkColorsButton.isVisible());
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(
                () -> {
                    viewModel.checkColorsButtonVisibleProperty().set(true);
                    latch.countDown();
                });
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(checkColorsButton.isVisible());
    }

    private static Parent loadRoot(OrderImportViewModel viewModel) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Parent> rootRef = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        OrderImportController.class
                                                .getClassLoader()
                                                .getResource(UiShellScreens.ORDER_IMPORT_FXML));
                        Parent root = loader.load();
                        OrderImportController controller = loader.getController();
                        controller.setViewModel(viewModel);
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
            throw new AssertionError("FXML load failed", error.get());
        }
        return rootRef.get();
    }

    private static FakeAuthorization auth() {
        return new FakeAuthorization(
                PermissionId.of(UiShellScreens.ORDER_CREATE_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_ITEM_CREATE_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_REVISION_EDIT_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_ITEM_APPROVE_PERMISSION),
                PermissionId.of(UiShellScreens.ORDER_APPROVE_PERMISSION));
    }

    private static final class StubImportService implements OrderImportService {
        @Override
        public OrderImportPreview preview(OrderImportBatch batch) {
            return preview(List.of(batch));
        }

        @Override
        public OrderImportPreview preview(List<OrderImportBatch> batches) {
            return OrderImportPreview.of(
                    "f.stxt",
                    "ORD-FX",
                    1,
                    1,
                    BigDecimal.ONE,
                    1,
                    List.of(),
                    List.of(),
                    new StubPlan("ORD-FX"));
        }

        @Override
        public OrderImportConfirmResult confirm(PreparedOrderImportPlan plan) {
            return OrderImportConfirmResult.of(OrderId.generate(), "ORD-FX", 1, 1);
        }
    }

    private static final class StubStxtParser implements StxtOrderFileParser {
        @Override
        public OrderImportFileParseResult parseFile(Path file) {
            return OrderImportFileParseResult.of(
                    List.of(
                            OrderImportBatch.of(
                                    "STXT",
                                    "f.stxt",
                                    "cs",
                                    "ORD-FX",
                                    LocalDate.now(),
                                    null,
                                    "Client",
                                    List.of())),
                    List.of(),
                    List.of(),
                    "UTF-8");
        }

        @Override
        public OrderImportFileParseResult parse(byte[] content, String sourceReference) {
            return parseFile(Path.of("f.stxt"));
        }
    }

    private static final class StubPlan implements PreparedOrderImportPlan {
        private final String orderNumber;

        private StubPlan(String orderNumber) {
            this.orderNumber = orderNumber;
        }

        @Override
        public List<OrderImportBatch> batches() {
            return List.of(batch());
        }

        @Override
        public OrderImportBatch batch() {
            return OrderImportBatch.of(
                    "STXT",
                    "f.stxt",
                    "cs",
                    orderNumber,
                    LocalDate.now(),
                    null,
                    "Client",
                    List.of());
        }

        @Override
        public String sourceType() {
            return "STXT";
        }

        @Override
        public String sourceReference() {
            return "f.stxt";
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