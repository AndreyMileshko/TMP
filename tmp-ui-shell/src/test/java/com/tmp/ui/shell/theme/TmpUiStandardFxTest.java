package com.tmp.ui.shell.theme;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Test-only showcase proving TMP UI Standard style classes exist and apply without CSS exceptions.
 */
class TmpUiStandardFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void compactContentClassesApplyWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            TextField authField = new TextField();
            authField.getStyleClass().add("tmp-auth-form-field");
            TableView<String> table = createTableView();
            table.getStyleClass().add("tmp-table-compact");
            VBox content = new VBox(8, table);
            content.getStyleClass().addAll("tmp-content", "tmp-content-compact");

            Scene scene = new Scene(content, 1200, 800);
            TmpTheme.apply(scene);
            content.applyCss();
            content.layout();

            assertTrue(authField.getStyleClass().contains("tmp-auth-form-field"));
            assertTrue(content.getStyleClass().contains("tmp-content-compact"));
            assertTrue(table.getStyleClass().contains("tmp-table-compact"));
        });
    }

    @Test
    void standardScreenStructureClassesApplyWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            Label title = new Label("Screen title");
            title.getStyleClass().add("tmp-screen-title");
            Label subtitle = new Label("Context hint");
            subtitle.getStyleClass().add("tmp-screen-subtitle");
            Button action = new Button("Create");
            action.getStyleClass().add("tmp-button-action");

            HBox headerActions = new HBox(8, action);
            headerActions.getStyleClass().add("tmp-screen-actions");
            VBox header = new VBox(4, title, subtitle, headerActions);
            header.getStyleClass().add("tmp-screen-header");

            HBox toolbar = new HBox(8, new TextField());
            toolbar.getStyleClass().add("tmp-toolbar");

            TableView<String> table = createTableView();
            VBox content = new VBox(8, table);
            content.getStyleClass().add("tmp-content");

            VBox screen = new VBox(12, header, toolbar, content);
            screen.getStyleClass().add("tmp-screen");

            Scene scene = new Scene(screen, 800, 600);
            TmpTheme.apply(scene);
            screen.applyCss();
            screen.layout();

            assertTrue(screen.getStyleClass().contains("tmp-screen"));
            assertTrue(title.getStyleClass().contains("tmp-screen-title"));
        });
    }

    @Test
    void standardButtonSemanticsApplyWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            Button action = new Button("Save");
            action.getStyleClass().add("tmp-button-action");
            Button secondary = new Button("Cancel");
            secondary.getStyleClass().add("tmp-button-secondary");
            Button danger = new Button("Delete");
            danger.getStyleClass().add("tmp-button-danger");
            Button ghost = new Button("Exit");
            ghost.getStyleClass().add("tmp-button-ghost");
            Button disabledDanger = new Button("Disabled delete");
            disabledDanger.getStyleClass().add("tmp-button-danger");
            disabledDanger.setDisable(true);

            VBox root = new VBox(8, action, secondary, danger, ghost, disabledDanger);
            Scene scene = new Scene(root, 320, 280);
            TmpTheme.apply(scene);
            root.applyCss();
            root.layout();

            assertTrue(action.getStyleClass().contains("tmp-button-action"));
            assertTrue(danger.isDisable() == false);
            assertTrue(disabledDanger.isDisable());
        });
    }

    @Test
    void standardBadgesAndMessagesApplyWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            Label successBadge = new Label("ACTIVE");
            successBadge.getStyleClass().addAll("tmp-badge", "tmp-badge-success");
            Label warningBadge = new Label("PENDING");
            warningBadge.getStyleClass().addAll("tmp-badge", "tmp-badge-warning");
            Label dangerBadge = new Label("DELETED");
            dangerBadge.getStyleClass().addAll("tmp-badge", "tmp-badge-danger");
            Label errorText = new Label("Validation failed");
            errorText.getStyleClass().add("tmp-text-error");
            Label errorMessage = new Label("Operation failed");
            errorMessage.getStyleClass().add("tmp-message-error");

            VBox root = new VBox(8, successBadge, warningBadge, dangerBadge, errorText, errorMessage);
            Scene scene = new Scene(root, 400, 300);
            TmpTheme.apply(scene);
            root.applyCss();
            root.layout();

            assertTrue(successBadge.getStyleClass().contains("tmp-badge-success"));
            assertTrue(errorMessage.getStyleClass().contains("tmp-message-error"));
        });
    }

    @Test
    void standardFormAndEmptyStateApplyWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            GridPane form = new GridPane();
            form.getStyleClass().add("tmp-form");
            Label label = new Label("Login *");
            label.getStyleClass().add("tmp-form-label");
            TextField field = new TextField();
            Label help = new Label("Use corporate login");
            help.getStyleClass().add("tmp-form-help");
            form.add(label, 0, 0);
            form.add(field, 1, 0);
            form.add(help, 1, 1);

            Label emptyTitle = new Label("Пользователей нет");
            emptyTitle.getStyleClass().add("tmp-empty-state-title");
            Label emptyHint = new Label("Создайте первого пользователя");
            emptyHint.getStyleClass().add("tmp-empty-state-hint");
            VBox emptyState = new VBox(4, emptyTitle, emptyHint);
            emptyState.getStyleClass().add("tmp-empty-state");

            VBox root = new VBox(16, form, emptyState);
            Scene scene = new Scene(root, 480, 320);
            TmpTheme.apply(scene);
            root.applyCss();
            root.layout();

            assertTrue(form.getStyleClass().contains("tmp-form"));
            assertTrue(emptyState.getStyleClass().contains("tmp-empty-state"));
        });
    }

    @Test
    void tabPaneCardAndControlsApplyWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            TabPane tabPane = new TabPane();
            Tab tab = new Tab("Основное", new Label("Content"));
            tabPane.getTabs().add(tab);

            VBox card = new VBox(8, new CheckBox("Option"), tabPane);
            card.getStyleClass().add("tmp-card");
            card.setPrefSize(520, 360);

            Scene scene = new Scene(card, 560, 400);
            TmpTheme.apply(scene);
            card.applyCss();
            card.layout();

            assertNotNull(tabPane.lookup(".tab-header-area"));
            assertTrue(card.getStyleClass().contains("tmp-card"));
        });
    }

    @Test
    void dialogPaneWithStandardThemeAppliesWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            DialogPane dialogPane = new DialogPane();
            TmpTheme.apply(dialogPane);
            dialogPane.setHeaderText("Dialog title");
            dialogPane.setContent(new Label("Content"));
            dialogPane.getButtonTypes().add(javafx.scene.control.ButtonType.OK);
            dialogPane.applyCss();
            dialogPane.layout();

            assertFalse(dialogPane.getStylesheets().isEmpty());
        });
    }

    @SuppressWarnings("unchecked")
    private static TableView<String> createTableView() {
        TableView<String> table = new TableView<>();
        TableColumn<String, String> column = new TableColumn<>("Name");
        column.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue()));
        table.getColumns().add(column);
        table.getItems().add("Row");
        return table;
    }
}
