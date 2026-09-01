package com.tmp.ui.shell.screen.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tmp.ui.shell.JavaFxTestSupport;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NavigationListCellTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void emptyThenPopulatedItemRestoresGraphic() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            NavigationListCell cell = new NavigationListCell();
            cell.updateItem(null, true);
            assertNull(cell.getGraphic());

            NavigationItem users = new NavigationItem(
                    ShellNavigationIcons.NAV_USERS, "Users", "security.view.users");
            cell.updateItem(users, false);
            assertNotNull(cell.getGraphic());
            assertEquals("Users", labelText(cell));
        });
    }

    @Test
    void populatedEmptyThenAnotherItemShowsSecondLabel() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            NavigationListCell cell = new NavigationListCell();
            NavigationItem users = new NavigationItem(
                    ShellNavigationIcons.NAV_USERS, "Users", "security.view.users");
            NavigationItem roles = new NavigationItem(
                    ShellNavigationIcons.NAV_ROLES, "Roles", "security.view.roles");

            cell.updateItem(users, false);
            assertEquals("Users", labelText(cell));

            cell.updateItem(null, true);
            assertNull(cell.getGraphic());

            cell.updateItem(roles, false);
            assertNotNull(cell.getGraphic());
            assertEquals("Roles", labelText(cell));
        });
    }

    @Test
    void selectedStyleClearsWhenCellBecomesEmpty() throws Exception {
        JavaFxTestSupport.runOnFxThread(() -> {
            NavigationListCell cell = new NavigationListCell();
            NavigationItem item = new NavigationItem("nav.test", "Test", "view.test");
            cell.updateItem(item, false);
            cell.updateSelected(true);
            cell.updateItem(null, true);
            assertNull(cell.getGraphic());
        });
    }

    private static String labelText(NavigationListCell cell) {
        attachToScene(cell);
        Label label = (Label) cell.lookup(".main-window-nav-label");
        return label.getText();
    }

    private static void attachToScene(NavigationListCell cell) {
        javafx.scene.layout.StackPane holder = new javafx.scene.layout.StackPane(cell);
        Scene scene = new Scene(holder, 320, 48);
        cell.applyCss();
        cell.layout();
    }
}
