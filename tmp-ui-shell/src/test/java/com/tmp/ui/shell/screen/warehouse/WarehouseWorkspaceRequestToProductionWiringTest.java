package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Source wiring guard: context menu exposes «Запросить на производство» and opens the dedicated
 * dialog without calling send / inter-warehouse move.
 */
class WarehouseWorkspaceRequestToProductionWiringTest {

    private static final Path CONTROLLER =
            Path.of(
                    "src/main/java/com/tmp/ui/shell/screen/warehouse/WarehouseWorkspaceController.java");
    private static final Path VIEW_MODEL =
            Path.of(
                    "src/main/java/com/tmp/ui/shell/screen/warehouse/WarehouseWorkspaceViewModel.java");
    private static final Path DIALOG_SUPPORT =
            Path.of(
                    "src/main/java/com/tmp/ui/shell/screen/warehouse/WarehouseRequestToProductionDialogSupport.java");

    @Test
    void contextMenuWiresRequestToProductionAction() throws Exception {
        String source = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        assertTrue(
                source.contains("new MenuItem(\"Запросить на производство\")"),
                "context menu must include «Запросить на производство»");
        assertTrue(
                source.contains(
                        "requestToProductionItem.setOnAction(e -> openRequestToProductionDialog())"),
                "menu item must open request dialog");
        assertTrue(
                source.contains("WarehouseRequestToProductionDialogSupport.createRequestDialog"),
                "dialog must be built via WarehouseRequestToProductionDialogSupport");
    }

    @Test
    void requestPathCreatesDraftOnlyAndNeverSends() throws Exception {
        String controller = Files.readString(CONTROLLER, StandardCharsets.UTF_8);
        String openMethod = extractMethod(controller, "private void openRequestToProductionDialog()");
        assertTrue(openMethod.contains("executeRequestToProduction"));
        assertFalse(openMethod.contains("executeInterWarehouseMove"));
        assertFalse(openMethod.contains("sendTransferDocument"));
        assertTrue(openMethod.contains("setOnValidSubmit"));
        assertTrue(openMethod.contains("session.showError"));
        assertTrue(
                openMethod.contains("viewModel.errorMessageProperty().set(\"\")"),
                "opening dialog must clear stale workspace errors");

        String viewModel = Files.readString(VIEW_MODEL, StandardCharsets.UTF_8);
        String executeMethod = extractMethod(viewModel, "public void executeRequestToProduction");
        assertTrue(executeMethod.contains("createTransferDocument"));
        assertFalse(executeMethod.contains("sendTransferDocument"));
        assertFalse(executeMethod.contains("acceptProductionDemand"));
        assertTrue(executeMethod.contains("REQUEST_TO_PRODUCTION_SUCCESS_MESSAGE"));
    }

    @Test
    void dialogOwnsQuantityValidationAndKeepsOpenOnInvalidSubmit() throws Exception {
        String support = Files.readString(DIALOG_SUPPORT, StandardCharsets.UTF_8);
        assertTrue(support.contains("addEventFilter"));
        assertTrue(support.contains("event.consume()"));
        assertTrue(support.contains("tmp-input-error") || support.contains("INPUT_ERROR_STYLE"));
        assertTrue(support.contains("tmp-message-error") || support.contains("MESSAGE_ERROR_STYLE"));
        assertTrue(support.contains("markQuantityInvalid"));
        assertTrue(support.contains("parseRequestQuantity"));
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