package com.tmp.ui.shell.screen.login;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UserActivationDialogFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void validationErrorKeepsButtonsInsideDialogBounds() throws Exception {
        String[] errorMessages = {
            "Пароли не совпадают",
            "Пароль должен содержать не менее 8 символов. Повторите ввод и убедитесь, что пароль "
                    + "соответствует требованиям безопасности.",
            "Неверный или недействительный код активации"
        };

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                for (String errorMessage : errorMessages) {
                    assertLayoutWithError(errorMessage);
                }
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("activation dialog layout test failed", failure.get());
        }
    }

    private static void assertLayoutWithError(String errorMessage) {
        Dialog<Boolean> dialog = UserActivationDialog.createDialog(
                null, "smoke-user", input -> Optional.of(errorMessage));
        try {
            DialogPane dialogPane = dialog.getDialogPane();
            dialog.show();
            dialogPane.applyCss();
            dialogPane.layout();

            GridPane grid = findGrid(dialogPane);
            findPlainTextField(grid, 0).setText("smoke-user");
            findPlainTextField(grid, 1).setText("AAAA-BBBB-CCCC");
            findChild(grid, PasswordField.class, 0).setText("short");
            findChild(grid, PasswordField.class, 1).setText("short");

            ButtonType saveType = dialogPane.getButtonTypes().stream()
                    .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                    .findFirst()
                    .orElseThrow();
            Button saveButton = (Button) dialogPane.lookupButton(saveType);
            saveButton.fire();

            dialogPane.applyCss();
            dialogPane.layout();
            if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() != null) {
                dialogPane.getScene().getWindow().sizeToScene();
            }
            dialogPane.applyCss();
            dialogPane.layout();

            Label errorLabel = findErrorLabel(dialogPane);
            assertTrue(errorLabel.isWrapText(), "error label must wrap long validation text");
            assertTrue(errorLabel.isVisible(), "error label must be visible after validation failure");
            assertFalse(errorLabel.getText().isBlank(), "error label must show validation message");

            Bounds paneBounds = dialogPane.localToScene(dialogPane.getBoundsInLocal());
            Button cancelButton = (Button) dialogPane.lookupButton(ButtonType.CANCEL);
            Bounds cancelBounds = cancelButton.localToScene(cancelButton.getBoundsInLocal());
            Bounds saveBounds = saveButton.localToScene(saveButton.getBoundsInLocal());

            assertTrue(
                    cancelBounds.getMaxY() <= paneBounds.getMaxY() + 1.0,
                    "cancel button must stay inside dialog pane bounds for: " + errorMessage);
            assertTrue(
                    saveBounds.getMaxY() <= paneBounds.getMaxY() + 1.0,
                    "save button must stay inside dialog pane bounds for: " + errorMessage);
            assertTrue(
                    errorLabel.localToScene(errorLabel.getBoundsInLocal()).getMaxY()
                            <= cancelBounds.getMinY(),
                    "error label must appear above button bar for: " + errorMessage);
        } finally {
            dialog.close();
        }
    }

    private static Label findErrorLabel(DialogPane dialogPane) {
        VBox content = (VBox) dialogPane.getContent();
        return content.getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static GridPane findGrid(DialogPane dialogPane) {
        VBox content = (VBox) dialogPane.getContent();
        return content.getChildren().stream()
                .filter(GridPane.class::isInstance)
                .map(GridPane.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static TextField findPlainTextField(GridPane grid, int index) {
        return grid.getChildren().stream()
                .filter(TextField.class::isInstance)
                .map(TextField.class::cast)
                .filter(field -> !(field instanceof PasswordField))
                .skip(index)
                .findFirst()
                .orElseThrow();
    }

    private static <T> T findChild(GridPane grid, Class<T> type, int index) {
        return grid.getChildren().stream()
                .filter(type::isInstance)
                .map(type::cast)
                .skip(index)
                .findFirst()
                .orElseThrow();
    }
}
