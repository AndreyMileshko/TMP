package com.tmp.ui.shell.screen.useradmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.UserId;
import com.tmp.security.api.UserSummary;
import com.tmp.ui.shell.JavaFxTestSupport;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UserAdministrationDialogsFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void createDialogHasRequiredLabelsAndStyledButtons() throws Exception {
        AtomicReference<Dialog<UserAdministrationDialogs.UserFormResult>> dialogRef = new AtomicReference<>();
        AtomicReference<DialogPane> paneRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Dialog<UserAdministrationDialogs.UserFormResult> dialog =
                    UserAdministrationDialogs.prepareCreateDialog(null, true);
            dialogRef.set(dialog);
            paneRef.set(dialog.getDialogPane());
            paneRef.get().applyCss();
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        DialogPane pane = paneRef.get();
        assertNotNull(pane);
        assertNotNull(findLabelWithText(pane, "Логин *"));
        assertNotNull(findLabelWithText(pane, "Имя *"));
        Button createButton = (Button) pane.lookupButton(findButtonType(pane, "Создать"));
        Button cancelButton = (Button) pane.lookupButton(findButtonType(pane, "Отмена"));
        assertTrue(createButton.getStyleClass().contains("tmp-button-action"));
        assertTrue(cancelButton.getStyleClass().contains("tmp-button-secondary"));
        assertEquals("Новый пользователь", dialogRef.get().getTitle());
    }

    @Test
    void emptyRequiredFieldsDoNotCloseCreateDialog() throws Exception {
        AtomicReference<Dialog<UserAdministrationDialogs.UserFormResult>> dialogRef = new AtomicReference<>();
        AtomicReference<Button> submitRef = new AtomicReference<>();
        AtomicReference<Label> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            Dialog<UserAdministrationDialogs.UserFormResult> dialog =
                    UserAdministrationDialogs.prepareCreateDialog(null, true);
            dialogRef.set(dialog);
            DialogPane pane = dialog.getDialogPane();
            pane.applyCss();
            submitRef.set((Button) pane.lookupButton(findButtonType(pane, "Создать")));
            errorRef.set(findInlineErrorLabel(pane));
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        CountDownLatch submitLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            submitRef.get().fire();
            submitLatch.countDown();
        });
        assertTrue(submitLatch.await(10, TimeUnit.SECONDS));

        assertTrue(errorRef.get().isVisible());
        assertEquals("Заполните логин и имя.", errorRef.get().getText());
        assertTrue(dialogRef.get().getResult() == null);
    }

    @Test
    void deleteDialogRequiresExactConfirmationAndUsesDangerStyle() throws Exception {
        UserSummary user = new UserSummary(
                UserId.generate(),
                Login.of("smoke"),
                DisplayName.of("Smoke User"),
                "ACTIVE",
                0L,
                Instant.parse("2026-07-23T04:00:00Z"),
                Instant.parse("2026-07-23T04:00:00Z"));

        AtomicReference<DialogPane> paneRef = new AtomicReference<>();
        AtomicReference<TextField> fieldRef = new AtomicReference<>();
        AtomicReference<Button> deleteRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Dialog<String> dialog = UserAdministrationDialogs.prepareDeleteConfirmation(null, user);
            DialogPane pane = dialog.getDialogPane();
            pane.applyCss();
            paneRef.set(pane);
            fieldRef.set(findTextField(pane));
            deleteRef.set((Button) pane.lookupButton(findButtonType(pane, "Удалить")));
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        DialogPane pane = paneRef.get();
        Button cancelButton = (Button) pane.lookupButton(findButtonType(pane, "Отмена"));
        assertTrue(deleteRef.get().getStyleClass().contains("tmp-button-danger"));
        assertTrue(cancelButton.getStyleClass().contains("tmp-button-secondary"));
        assertTrue(deleteRef.get().isDisable());
        assertNotNull(findLabelWithText(pane, "Для подтверждения введите УДАЛИТЬ"));

        CountDownLatch enableLatch = new CountDownLatch(1);
        Platform.runLater(() -> {
            fieldRef.get().setText("УДАЛИТЬ");
            assertFalse(deleteRef.get().isDisable());
            enableLatch.countDown();
        });
        assertTrue(enableLatch.await(10, TimeUnit.SECONDS));
    }

    private static ButtonType findButtonType(DialogPane pane, String label) {
        return pane.getButtonTypes().stream()
                .filter(type -> label.equals(type.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static Label findLabelWithText(DialogPane pane, String text) {
        for (javafx.scene.Node node : pane.lookupAll(".label")) {
            if (node instanceof Label label && text.equals(label.getText())) {
                return label;
            }
        }
        return null;
    }

    private static Label findInlineErrorLabel(DialogPane pane) {
        for (javafx.scene.Node node : pane.lookupAll(".label")) {
            if (node instanceof Label label && label.getStyleClass().contains("tmp-text-error")) {
                return label;
            }
        }
        throw new IllegalStateException("Inline error label not found");
    }

    private static TextField findTextField(DialogPane pane) {
        for (javafx.scene.Node node : pane.lookupAll(".text-field")) {
            if (node instanceof TextField field) {
                return field;
            }
        }
        throw new IllegalStateException("TextField not found");
    }
}
