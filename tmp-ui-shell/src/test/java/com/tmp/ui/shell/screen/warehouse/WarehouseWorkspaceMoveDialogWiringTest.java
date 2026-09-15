package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source/wiring guard: toolbar + context menu both open the TableView Move dialog via {@link
 * WarehouseMoveDialogSupport}; legacy {@code Кол-во:} HBox path must not be used for Move.
 */
class WarehouseWorkspaceMoveDialogWiringTest {

    private static final Path CONTROLLER =
            Path.of(
                    "src/main/java/com/tmp/ui/shell/screen/warehouse/WarehouseWorkspaceController.java");

    @Test
    void toolbarAndContextMenuBothTargetOpenMoveDialog() throws Exception {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(
                source.contains("moveStockButton.setOnAction(e -> openMoveDialog())"),
                "toolbar Move must call openMoveDialog");
        assertTrue(
                source.contains("moveItem.setOnAction(e -> openMoveDialog())"),
                "context menu Move must call openMoveDialog");
    }

    @Test
    void openMoveDialogDelegatesToWarehouseMoveDialogSupport() throws Exception {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        String openMoveMethod = extractMethod(source, "private void openMoveDialog()");
        assertTrue(
                openMoveMethod.contains("WarehouseMoveDialogSupport.createMoveDialog"),
                "openMoveDialog must build UI via WarehouseMoveDialogSupport");
        assertTrue(
                openMoveMethod.contains("executeSameWarehouseMove")
                        || openMoveMethod.contains("executeInterWarehouseMove"),
                "openMoveDialog must still execute moves after OK");
        assertTrue(openMoveMethod.contains("executeSameWarehouseMove"));
        assertTrue(openMoveMethod.contains("executeInterWarehouseMove"));
        assertFalse(
                openMoveMethod.contains("new Label(\"Кол-во:\")"),
                "Move path must not build legacy Кол-во HBox lines");
        assertFalse(
                openMoveMethod.contains("new TableView<>()"),
                "TableView construction must live in WarehouseMoveDialogSupport, not controller");
    }

    @Test
    void movePathContainsTableDialogMarkersAndConsumeMayKeepQtyLabel() throws Exception {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        String openMoveMethod = extractMethod(source, "private void openMoveDialog()");
        // Markers live in Support constants / delegated call; controller must not rebuild legacy UI.
        assertTrue(
                source.contains("WarehouseMoveDialogSupport"),
                "controller must reference WarehouseMoveDialogSupport");
        assertTrue(
                Files.readString(
                                Path.of(
                                        "src/main/java/com/tmp/ui/shell/screen/warehouse/WarehouseMoveDialogSupport.java"),
                                StandardCharsets.UTF_8)
                        .contains("Выбрано позиций"),
                "Move dialog support must contain Выбрано позиций");
        assertTrue(
                Files.readString(
                                Path.of(
                                        "src/main/java/com/tmp/ui/shell/screen/warehouse/WarehouseMoveDialogSupport.java"),
                                StandardCharsets.UTF_8)
                        .contains("Всё доступное"),
                "Move dialog support must contain Всё доступное");
        assertTrue(
                Files.readString(
                                Path.of(
                                        "src/main/java/com/tmp/ui/shell/screen/warehouse/WarehouseMoveDialogSupport.java"),
                                StandardCharsets.UTF_8)
                        .contains("Артикул"),
                "Move dialog support must contain Артикул column");

        assertFalse(
                openMoveMethod.contains("Кол-во:"),
                "controller Move path must not contain Кол-во: label");

        String openConsumeMethod = extractMethod(source, "private void openConsumeDialog()");
        assertTrue(
                openConsumeMethod.contains("Кол-во:"),
                "write-off consume dialog may keep Кол-во label");
    }

    private static String extractMethod(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "missing method: " + signature);
        int brace = source.indexOf('{', start);
        assertTrue(brace >= 0, "missing body for: " + signature);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError("unbalanced braces for: " + signature);
    }
}
