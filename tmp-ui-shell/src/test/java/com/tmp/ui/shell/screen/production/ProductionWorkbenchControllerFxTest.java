package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
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
                        stage.setScene(new Scene(root));

                        assertNotNull(root.lookup("#acceptButton"));
                        assertNotNull(root.lookup("#checkMaterialsButton"));
                        assertNotNull(root.lookup("#prepareTransferButton"));
                        assertNotNull(root.lookup("#confirmReceiptButton"));
                        assertNotNull(root.lookup("#prepareReleaseButton"));
                        assertNotNull(root.lookup("#cancelProductionButton"));
                        assertEquals(
                                "Принять в производство",
                                ((Button) root.lookup("#acceptButton")).getText());
                        assertEquals(
                                "Проверить наличие материалов",
                                ((Button) root.lookup("#checkMaterialsButton")).getText());
                        assertEquals(
                                "Создать перемещение материалов",
                                ((Button) root.lookup("#prepareTransferButton")).getText());
                        assertEquals(
                                "Подтвердить получение",
                                ((Button) root.lookup("#confirmReceiptButton")).getText());
                        assertEquals(
                                "Выпустить изделия",
                                ((Button) root.lookup("#prepareReleaseButton")).getText());
                        assertEquals(
                                "Отменить производство заказа",
                                ((Button) root.lookup("#cancelProductionButton")).getText());
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
}
