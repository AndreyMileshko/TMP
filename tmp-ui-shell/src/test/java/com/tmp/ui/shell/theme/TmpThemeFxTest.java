package com.tmp.ui.shell.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TmpThemeFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void stylesheetResourceExistsAndLoads() {
        String url = TmpTheme.stylesheetUrl();
        assertNotNull(url);
        assertFalse(url.isBlank());
        assertTrue(url.contains("tmp-theme.css"));
    }

    @Test
    void applyToSceneDoesNotDuplicateStylesheet() {
        Scene scene = new Scene(new VBox(), 400, 300);
        TmpTheme.apply(scene);
        TmpTheme.apply(scene);
        assertEquals(1, scene.getStylesheets().size());
    }

    @Test
    void applyToDialogPaneDoesNotDuplicateStylesheet() {
        DialogPane dialogPane = new DialogPane();
        TmpTheme.apply(dialogPane);
        TmpTheme.apply(dialogPane);
        assertEquals(1, dialogPane.getStylesheets().size());
    }

    @Test
    void representativeControlsApplyCssWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            VBox root = new VBox(8);
            root.getChildren().addAll(
                    new Button("Primary"),
                    new TextField(),
                    new PasswordField(),
                    new CheckBox("Option"),
                    createTableView(),
                    createContextMenuButton());

            Scene scene = new Scene(root, 480, 360);
            TmpTheme.apply(scene);
            root.applyCss();
            root.layout();
        });
    }

    @Test
    void representativeFxmlScreensApplyCssWithoutException() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            try {
                loadAndLayout("com/tmp/ui/shell/screen/login/LoginScreen.fxml");
                loadAndLayout("com/tmp/ui/shell/screen/main/MainWindow.fxml");
                loadAndLayout("com/tmp/ui/shell/screen/useradmin/UserAdministrationScreen.fxml");
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    @Test
    void dialogPaneWithThemeAppliesCssWithoutException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            DialogPane dialogPane = new DialogPane();
            TmpTheme.apply(dialogPane);
            dialogPane.setContent(new Label("Dialog content"));
            dialogPane.applyCss();
            dialogPane.layout();
        });
    }

    @Test
    void enabledActionButtonsUseActionSemanticStyleClasses() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            Button standard = new Button("Action");
            Button primary = new Button("Primary");
            primary.getStyleClass().add("tmp-button-primary");
            Button secondary = new Button("Cancel");
            secondary.getStyleClass().add("tmp-button-secondary");
            Button danger = new Button("Delete");
            danger.getStyleClass().add("tmp-button-danger");
            Button ghost = new Button("Ghost");
            ghost.getStyleClass().add("tmp-button-ghost");

            VBox root = new VBox(8, standard, primary, secondary, danger, ghost);
            Scene scene = new Scene(root, 320, 260);
            TmpTheme.apply(scene);
            root.applyCss();
            root.layout();

            assertTrue(standard.getStyleClass().contains("button"));
            assertTrue(primary.getStyleClass().contains("tmp-button-primary"));
            assertTrue(secondary.getStyleClass().contains("tmp-button-secondary"));
            assertTrue(danger.getStyleClass().contains("tmp-button-danger"));
            assertTrue(ghost.getStyleClass().contains("tmp-button-ghost"));
            assertTrue(standard.isDisable() == false);
        });
    }

    @Test
    void disabledButtonsKeepSemanticStyleClassesWithoutCssException() throws InterruptedException {
        JavaFxTestSupport.runOnFxThread(() -> {
            Button enabled = new Button("Enabled");
            Button disabled = new Button("Disabled");
            disabled.setDisable(true);
            Button disabledDanger = new Button("Disabled delete");
            disabledDanger.getStyleClass().add("tmp-button-danger");
            disabledDanger.setDisable(true);

            VBox root = new VBox(8, enabled, disabled, disabledDanger);
            Scene scene = new Scene(root, 320, 180);
            TmpTheme.apply(scene);
            root.applyCss();
            root.layout();

            assertFalse(enabled.isDisable());
            assertTrue(disabled.isDisable());
            assertTrue(disabledDanger.isDisable());
        });
    }

    @Test
    void shellLogoutStyleRemainsNeutralWithThemeApplied() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            Button logout = new Button("Выход");
            logout.getStyleClass().add("main-window-logout");
            VBox root = new VBox(logout);
            Scene scene = new Scene(root, 240, 120);
            TmpTheme.apply(scene);
            TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
            root.applyCss();
            root.layout();

            assertTrue(logout.getStyleClass().contains("main-window-logout"));
            assertFalse(logout.getStyleClass().contains("tmp-button-primary"));
        });
    }

    private static void loadAndLayout(String fxmlPath) throws Exception {
        FXMLLoader loader = new FXMLLoader(TmpThemeFxTest.class.getClassLoader().getResource(fxmlPath));
        if (loader.getLocation() == null) {
            throw new IllegalStateException("FXML not found: " + fxmlPath);
        }
        Parent root = loader.load();
        Scene scene = new Scene(root, 960, 640);
        TmpTheme.apply(scene);
        TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/login/LoginScreen.css");
        TmpTheme.addStylesheet(scene, "com/tmp/ui/shell/screen/main/MainWindow.css");
        root.applyCss();
        root.layout();
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

    private static Button createContextMenuButton() {
        Button button = new Button("Menu");
        ContextMenu menu = new ContextMenu(new MenuItem("Action"));
        button.setOnAction(event -> menu.show(button, 0, button.getHeight()));
        return button;
    }
}
