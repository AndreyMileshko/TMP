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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
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
    void tableHeaderUsesDistinctBackgroundFromDataRows() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            TableView<String> table = createTableView();
            table.getItems().addAll("Row A", "Row B");
            VBox root = new VBox(table);
            Scene scene = new Scene(root, 480, 240);
            TmpTheme.apply(scene);
            table.applyCss();
            table.layout();

            Region headerBackground = (Region) table.lookup(".column-header-background");
            assertNotNull(headerBackground);
            Color headerColor = (Color) headerBackground.getBackground().getFills().get(0).getFill();
            table.getSkin();
            table.layout();
            Region oddRow = (Region) table.lookup(".table-row-cell:odd");
            Region evenRow = (Region) table.lookup(".table-row-cell:even");
            assertNotNull(oddRow);
            assertNotNull(evenRow);
            Color oddRowColor = (Color) oddRow.getBackground().getFills().get(0).getFill();
            Color evenRowColor = (Color) evenRow.getBackground().getFills().get(0).getFill();

            assertFalse(colorsEqual(headerColor, oddRowColor),
                    "Table header must not share background with alternating rows");
            assertFalse(colorsEqual(headerColor, evenRowColor),
                    "Table header must not share background with normal rows");
        });
    }

    private static boolean colorsEqual(Color left, Color right) {
        return left != null && right != null
                && Math.abs(left.getRed() - right.getRed()) < 0.001
                && Math.abs(left.getGreen() - right.getGreen()) < 0.001
                && Math.abs(left.getBlue() - right.getBlue()) < 0.001;
    }

    @Test
    void tableSelectionUsesSoftGreenTokenInsteadOfPrimarySoft() {
        String themeCss;
        try (var in = TmpTheme.class.getResourceAsStream("tmp-theme.css")) {
            assertNotNull(in);
            themeCss = new String(in.readAllBytes());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        assertTrue(themeCss.contains("-tmp-table-selection-bg:"));
        assertTrue(themeCss.contains("-tmp-table-selection-hover-bg:"));
        assertTrue(themeCss.contains(".table-view .table-row-cell:filled:selected"));
        assertTrue(themeCss.contains(".table-view:focused .table-row-cell:filled:selected"));
        assertTrue(themeCss.contains("-fx-background-color: -tmp-table-selection-bg"));
        assertFalse(themeCss.contains(
                ".table-view:focused .table-row-cell:filled:selected {\r\n    -fx-background-color: -tmp-primary-soft;"));
        assertFalse(themeCss.contains(
                ".table-view:focused .table-row-cell:filled:selected {\n    -fx-background-color: -tmp-primary-soft;"));
        assertTrue(themeCss.contains("-tmp-status-indicator-waiting:"));
        assertTrue(themeCss.contains("-tmp-status-indicator-ready:"));
        assertTrue(themeCss.contains("-tmp-status-indicator-success:"));
        assertTrue(themeCss.contains("-tmp-success: #2E7D32"));
        assertTrue(themeCss.contains("-tmp-danger: #C62828"));
        assertTrue(themeCss.contains("-tmp-warning: #A86700"));
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
