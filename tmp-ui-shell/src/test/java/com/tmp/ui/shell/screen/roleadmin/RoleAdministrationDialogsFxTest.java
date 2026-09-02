package com.tmp.ui.shell.screen.roleadmin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.security.api.RoleId;
import com.tmp.security.api.RoleSummary;
import com.tmp.ui.shell.JavaFxTestSupport;
import java.time.Instant;
import java.util.Set;
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

class RoleAdministrationDialogsFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void createDialogHasRequiredLabelsAndStyledButtons() throws Exception {
        AtomicReference<Dialog<RoleAdministrationDialogs.RoleFormResult>> dialogRef = new AtomicReference<>();
        AtomicReference<DialogPane> paneRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Dialog<RoleAdministrationDialogs.RoleFormResult> dialog =
                    RoleAdministrationDialogs.prepareCreateDialog(null, true);
            dialogRef.set(dialog);
            paneRef.set(dialog.getDialogPane());
            paneRef.get().applyCss();
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        DialogPane pane = paneRef.get();
        assertNotNull(pane);
        assertNotNull(findLabelWithText(pane, "Имя *"));
        assertNotNull(findLabelWithText(pane, "Описание"));
        Button createButton = (Button) pane.lookupButton(findButtonType(pane, "Создать"));
        Button cancelButton = (Button) pane.lookupButton(findButtonType(pane, "Отмена"));
        assertTrue(createButton.getStyleClass().contains("tmp-button-action"));
        assertTrue(cancelButton.getStyleClass().contains("tmp-button-secondary"));
        assertEquals("Новая роль", dialogRef.get().getTitle());
    }

    @Test
    void emptyNameDoesNotCloseCreateDialog() throws Exception {
        AtomicReference<Dialog<RoleAdministrationDialogs.RoleFormResult>> dialogRef = new AtomicReference<>();
        AtomicReference<Button> submitRef = new AtomicReference<>();
        AtomicReference<Label> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            Dialog<RoleAdministrationDialogs.RoleFormResult> dialog =
                    RoleAdministrationDialogs.prepareCreateDialog(null, true);
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
        assertEquals("Заполните имя роли.", errorRef.get().getText());
        assertTrue(dialogRef.get().getResult() == null);
    }

    @Test
    void editDialogLoadsExistingValues() throws Exception {
        RoleSummary role = new RoleSummary(
                RoleId.generate(),
                "Operator",
                "Operations role",
                Set.of(),
                0L,
                Instant.parse("2026-07-23T04:00:00Z"),
                Instant.parse("2026-07-23T04:00:00Z"));
        AtomicReference<DialogPane> paneRef = new AtomicReference<>();
        AtomicReference<String> dialogTitleRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Dialog<RoleAdministrationDialogs.RoleFormResult> dialog =
                    RoleAdministrationDialogs.prepareEditDialog(null, true, role);
            paneRef.set(dialog.getDialogPane());
            paneRef.get().applyCss();
            dialogTitleRef.set(dialog.getTitle());
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        TextField nameField = findTextField(paneRef.get(), 0);
        TextField descriptionField = findTextField(paneRef.get(), 1);
        assertEquals("Operator", nameField.getText());
        assertEquals("Operations role", descriptionField.getText());
        assertEquals("Редактирование роли", dialogTitleRef.get());
    }

    @Test
    void deleteDialogUsesDangerStyle() throws Exception {
        RoleSummary role = new RoleSummary(
                RoleId.generate(),
                "Operator",
                "",
                Set.of(),
                0L,
                Instant.parse("2026-07-23T04:00:00Z"),
                Instant.parse("2026-07-23T04:00:00Z"));
        AtomicReference<DialogPane> paneRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Dialog<Boolean> dialog = RoleAdministrationDialogs.prepareDeleteConfirmation(null, role);
            paneRef.set(dialog.getDialogPane());
            paneRef.get().applyCss();
            latch.countDown();
        });
        assertTrue(latch.await(10, TimeUnit.SECONDS));

        Button deleteButton = (Button) paneRef.get().lookupButton(findButtonType(paneRef.get(), "Удалить"));
        assertTrue(deleteButton.getStyleClass().contains("tmp-button-danger"));
        assertNotNull(findLabelContaining(paneRef.get(), "Удалить роль «Operator»?"));
    }

    private static Label findLabelWithText(DialogPane pane, String text) {
        for (javafx.scene.Node node : pane.lookupAll(".tmp-form-label")) {
            if (node instanceof Label label && text.equals(label.getText())) {
                return label;
            }
        }
        return null;
    }

    private static Label findLabelContaining(DialogPane pane, String fragment) {
        for (javafx.scene.Node node : pane.lookupAll(".tmp-dialog-title")) {
            if (node instanceof Label label && label.getText().contains(fragment)) {
                return label;
            }
        }
        return null;
    }

    private static Label findInlineErrorLabel(DialogPane pane) {
        for (javafx.scene.Node node : pane.lookupAll(".tmp-text-error")) {
            if (node instanceof Label label) {
                return label;
            }
        }
        return null;
    }

    private static ButtonType findButtonType(DialogPane pane, String text) {
        return pane.getButtonTypes().stream()
                .filter(type -> text.equals(type.getText()))
                .findFirst()
                .orElseThrow();
    }

    private static TextField findTextField(DialogPane pane, int index) {
        int found = 0;
        for (javafx.scene.Node node : pane.lookupAll(".text-field")) {
            if (node instanceof TextField field) {
                if (found == index) {
                    return field;
                }
                found++;
            }
        }
        throw new AssertionError("TextField not found at index " + index);
    }
}
