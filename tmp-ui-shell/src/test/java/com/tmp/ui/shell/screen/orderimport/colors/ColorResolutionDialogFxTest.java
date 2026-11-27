package com.tmp.ui.shell.screen.orderimport.colors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import com.tmp.ui.shell.JavaFxTestSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ColorResolutionDialogFxTest {

    private static final UUID CAT_BELYI = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CAT_RAL7016 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @BeforeAll
    static void initFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void initialDisplayShowsRowsAndColumns() throws Exception {
        ColorResolutionSession session = sessionUnmatched("ORD", "Цвет");
        AtomicReference<Optional<ColorResolutionDialog.Result>> result = new AtomicReference<>();
        runDialogAndClose(session, () -> {}, result);
        assertTrue(result.get().isPresent());
        assertFalse(result.get().get().applied());
    }

    @Test
    void multiSelectSurvivesAfterTableRefresh() throws Exception {
        ColorResolutionSession session = sessionTwoRows("Цвет");
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        vm.setRowHighlighted(session.rows().get(0).id(), true);
        vm.setRowHighlighted(session.rows().get(1).id(), true);
        vm.applyInsideColor(session.rows().get(0).id(), CAT_BELYI);
        vm.applyOutsideColor(session.rows().get(0).id(), CAT_RAL7016);
        assertTrue(vm.isRowHighlighted(session.rows().get(1).id()));
    }

    @Test
    void escapeOnEditorCancelDoesNotChangeSession() throws Exception {
        ColorResolutionSession session = sessionUnmatched("ORD", "Цвет");
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        ColorResolutionRow.Id id = session.rows().get(0).id();
        ColorResolutionSession before = vm.session();
        vm.applyInsideColor(id, CAT_BELYI);
        vm.cancelDraft(id);
        assertEquals(before, vm.session());
    }

    @Test
    void inlineErrorsTooLongAndPlaceholder() throws Exception {
        FakeConstraint constraint = new FakeConstraint();
        constraint.reject.add("!!invalid!!");
        ColorResolutionCoordinator coordinator =
                new ColorResolutionCoordinator(unmatchedApi("Цвет", "цвет"), constraint);
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD", "Цвет"))).session();
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        vm.setRowHighlighted(session.rows().get(0).id(), true);
        vm.applyCustomColorToHighlighted("x".repeat(70));
        assertEquals("Цвет длиннее 64 символов", vm.constraintErrorMessage(vm.session().rows().get(0)));
        vm = new ColorResolutionDialogViewModel(session);
        vm.setRowHighlighted(session.rows().get(0).id(), true);
        vm.applyCustomColorToHighlighted("!!invalid!!");
        assertTrue(vm.constraintErrorMessage(vm.session().rows().get(0)).startsWith("Значение"));
    }

    @Test
    void saveColumnHiddenWithoutGlobalSaveRight() throws Exception {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        api.canSave = false;
        ColorResolutionSession session =
                new ColorResolutionCoordinator(api, new FakeConstraint()).prepare(List.of(sampleBatch("ORD", "Цвет"))).session();
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        assertFalse(vm.showSaveMappingColumn());
    }

    @Test
    void inactiveMappingShowsDisabledSaveTooltip() throws Exception {
        FakeColorApi api = inactiveApi("Серый", "серый");
        ColorResolutionSession session =
                new ColorResolutionCoordinator(api, new FakeConstraint()).prepare(List.of(sampleBatch("ORD", "Серый"))).session();
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        assertTrue(vm.saveMappingUnavailableTooltip(session.rows().get(0)).contains("Справочники"));
    }

    @Test
    void applyDisabledUntilReady() throws Exception {
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(sessionUnmatched("ORD", "Цвет"));
        assertFalse(vm.isApplyEnabled());
    }

    @Test
    void applyReturnsRebuildResult() throws Exception {
        ColorResolutionSession session = sessionUnmatched("ORD", "Цвет");
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        ColorResolutionRow.Id id = session.rows().get(0).id();
        vm.applyInsideColor(id, CAT_BELYI);
        vm.applyOutsideColor(id, CAT_RAL7016);
        assertTrue(vm.isApplyEnabled());
        List<OrderImportBatch> rebuilt = vm.session().rebuild();
        assertNotNull(rebuilt.get(0).positions().get(0).specificationLines().get(0).color());
    }

    @Test
    void cancelCloseDoesNotRebuild() throws Exception {
        ColorResolutionSession session = sessionUnmatched("ORD", "Цвет");
        AtomicReference<Optional<ColorResolutionDialog.Result>> result = new AtomicReference<>();
        runDialogAndClose(session, () -> {}, result);
        assertTrue(result.get().isPresent());
        assertFalse(result.get().get().applied());
        assertTrue(result.get().get().rebuiltBatches() == null || result.get().get().rebuiltBatches().isEmpty());
    }

    @Test
    void largeRowCountDialogBuilds() throws Exception {
        ColorResolutionSession session = largeSession(320);
        AtomicBoolean opened = new AtomicBoolean();
        AtomicReference<Optional<ColorResolutionDialog.Result>> result = new AtomicReference<>();
        runDialogAndClose(session, () -> opened.set(true), result);
        assertTrue(opened.get());
    }

    @Test
    void tableDoesNotKeepPermanentComboBoxes() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ColorPairCellEditors.createSearchableColorCombo(
                            sessionUnmatched("ORD", "Цвет").activeColors());
                    TableView<ColorResolutionRow> table = new TableView<>();
                    table.getItems().addAll(sessionUnmatched("ORD", "Цвет").rows());
                    assertEquals(0, countDescendants(table, ComboBox.class));
                });
    }

    @Test
    void enterInMassComboDoesNotDefaultApplyButton() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    Stage owner = new Stage();
                    ColorResolutionSession session = sessionUnmatched("ORD", "Цвет");
                    ComboBox<ColorDto> combo =
                            ColorPairCellEditors.createSearchableColorCombo(session.activeColors());
                    combo.getSelectionModel().select(session.activeColors().get(0));
                    Button apply = new Button("Применить");
                    apply.setDefaultButton(true);
                    AtomicBoolean applyClicked = new AtomicBoolean();
                    apply.setOnAction(event -> applyClicked.set(true));
                    javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(combo, apply);
                    javafx.scene.Scene scene = new javafx.scene.Scene(root);
                    owner.setScene(scene);
                    combo.requestFocus();
                    combo.getEditor().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
                    assertFalse(applyClicked.get());
                });
    }

    @Test
    void dialogToolbarButtonsHaveFullCaptions() throws Exception {
        ColorResolutionSession session = sessionUnmatched("ORD", "Цвет");
        AtomicReference<DialogPane> paneRef = new AtomicReference<>();
        runDialogAndClose(session, () -> findDialogPane(paneRef), new AtomicReference<>());
        DialogPane pane = paneRef.get();
        assertNotNull(pane);
        for (Node node : pane.lookupAll(".button")) {
            if (!(node instanceof Button button)) {
                continue;
            }
            String text = button.getText();
            if (text == null || text.isBlank() || "…".equals(text) || "...".equals(text)) {
                continue;
            }
            assertFalse(text.contains("…"), "Unexpected ellipsis in button: " + text);
            assertFalse(text.equals("..."), "Unexpected ellipsis in button");
        }
    }

    @Test
    void polishedTableColumnHeaders() throws Exception {
        ColorResolutionSession session = sessionUnmatched("ORD", "Цвет");
        AtomicReference<TableView<ColorResolutionRow>> tableRef = new AtomicReference<>();
        AtomicReference<SaveHeaderLayoutProbe> layoutRef = new AtomicReference<>();
        runDialogAndClose(
                session,
                () -> measureSaveMappingHeaderLayout(tableRef, layoutRef),
                new AtomicReference<>());
        TableView<ColorResolutionRow> table = tableRef.get();
        assertNotNull(table);
        List<TableColumn<ColorResolutionRow, ?>> cols = table.getColumns();
        assertTrue(cols.size() >= 10);

        TableColumn<ColorResolutionRow, ?> selectCol = cols.get(0);
        assertTrue(selectCol.getText() == null || selectCol.getText().isBlank());

        TableColumn<ColorResolutionRow, ?> lineCol = cols.get(3);
        assertNotNull(lineCol.getGraphic());
        Label lineHeader = (Label) lineCol.getGraphic();
        assertEquals("Стр.", lineHeader.getText());
        assertFalse(lineHeader.getText().contains("..."));
        assertFalse(lineHeader.getText().contains("…"));

        TableColumn<ColorResolutionRow, ?> saveCol = cols.get(cols.size() - 2);
        assertNotNull(saveCol.getGraphic());
        Label saveHeader = (Label) saveCol.getGraphic();
        assertEquals("Сохр. в\nсправ.", saveHeader.getText());
        assertEquals(2, saveHeader.getText().split("\n", -1).length);
        assertEquals("Сохр. в", saveHeader.getText().split("\n", -1)[0]);
        assertEquals("справ.", saveHeader.getText().split("\n", -1)[1]);
        assertFalse(saveHeader.isWrapText());
        assertFalse(saveHeader.getText().contains("..."));
        assertFalse(saveHeader.getText().contains("…"));
        assertTrue(saveHeader.getStyleClass().contains("tmp-col-res-header-wrap"));
        assertEquals(96, saveCol.getMinWidth(), 0.1);
        assertEquals(104, saveCol.getPrefWidth(), 0.1);
        assertEquals(112, saveCol.getMaxWidth(), 0.1);

        SaveHeaderLayoutProbe layout = layoutRef.get();
        assertNotNull(layout);
        assertTrue(layout.columnWidth() >= 96, "save column width: " + layout.columnWidth());
        assertTrue(layout.columnWidth() <= 112, "save column width: " + layout.columnWidth());
        assertTrue(
                layout.headerWidth() + 1 >= layout.line1Width()
                        && layout.headerWidth() + 1 >= layout.line2Width(),
                "header label must fit both lines without clipping");
        assertEquals(Pos.CENTER, saveHeader.getAlignment());

        TableColumn<ColorResolutionRow, ?> productCol = cols.get(2);
        assertEquals("Изделие", productCol.getText());
    }

    @Test
    void saveMappingColumnHeaderTooltipUnchanged() {
        String source =
                readSourceSafe(
                        "tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/orderimport/colors/ColorResolutionDialog.java");
        assertTrue(source.contains("wrappedHeader(\"Сохр. в\", \"справ.\")"));
        assertTrue(
                source.contains(
                        "После успешного импорта сохранить соответствие исходного текста СО7 паре TMP"));
    }

    @Test
    void columnLayoutMatchesUxStandard() {
        String source =
                readSourceSafe(
                        "tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/orderimport/colors/ColorResolutionDialog.java");
        assertTrue(source.contains("Цветовая пара"));
        assertTrue(source.contains("\"Стр.\""));
        assertTrue(source.contains("Сохр. в"));
        assertTrue(source.contains("tmp-col-res-header-empty"));
        assertFalse(source.contains("\"Выбор\""));
        assertFalse(source.contains("\"Состояние\""));
        assertFalse(source.contains("Ошибка проверки"));
        assertTrue(source.contains("tmp-color-resolution-table"));
    }

    @Test
    void dialogClassDoesNotReferenceOrderImportService() {
        String source =
                readSourceSafe(
                        "tmp-ui-shell/src/main/java/com/tmp/ui/shell/screen/orderimport/colors/ColorResolutionDialog.java");
        assertFalse(source.contains("OrderImportService"));
        assertFalse(source.contains("saveMappingsFromImport"));
    }

    private static void runDialogAndClose(
            ColorResolutionSession session,
            Runnable onOpen,
            AtomicReference<Optional<ColorResolutionDialog.Result>> result)
            throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(
                () -> {
                    new Thread(
                                    () -> {
                                        try {
                                            Thread.sleep(150);
                                            Platform.runLater(
                                                    () -> {
                                                        for (Window window : Window.getWindows()) {
                                                            if (window instanceof Stage stage
                                                                    && "Разрешение цветов СО7".equals(stage.getTitle())) {
                                                                onOpen.run();
                                                                stage.fireEvent(new javafx.stage.WindowEvent(stage, javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST));
                                                                return;
                                                            }
                                                        }
                                                    });
                                        } catch (InterruptedException ignored) {
                                            Thread.currentThread().interrupt();
                                        }
                                    })
                            .start();
                    Stage owner = new Stage();
                    owner.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane()));
                    Optional<ColorResolutionDialog.Result> dialogResult =
                            ColorResolutionDialog.show(owner, session);
                    result.set(dialogResult);
                    done.countDown();
                });
        assertTrue(done.await(20, TimeUnit.SECONDS));
    }

    private record SaveHeaderLayoutProbe(
            double columnWidth, double headerWidth, double line1Width, double line2Width) {}

    private static void measureSaveMappingHeaderLayout(
            AtomicReference<TableView<ColorResolutionRow>> tableRef,
            AtomicReference<SaveHeaderLayoutProbe> layoutRef) {
        findColorResolutionTable(tableRef);
        TableView<ColorResolutionRow> table = tableRef.get();
        if (table == null) {
            return;
        }
        table.setMinWidth(1100);
        table.setPrefWidth(1100);
        if (table.getScene() != null) {
            table.getScene().getRoot().applyCss();
            table.getScene().getRoot().layout();
        }
        table.applyCss();
        table.layout();

        List<TableColumn<ColorResolutionRow, ?>> cols = table.getColumns();
        TableColumn<ColorResolutionRow, ?> saveCol = cols.get(cols.size() - 2);
        Label saveHeader = (Label) saveCol.getGraphic();
        saveHeader.applyCss();
        saveHeader.layout();

        Font font = saveHeader.getFont();
        double line1 = textWidth("Сохр. в", font);
        double line2 = textWidth("справ.", font);
        double headerWidth = saveHeader.getWidth() > 0 ? saveHeader.getWidth() : saveHeader.prefWidth(-1);
        double columnWidth = saveCol.getWidth() > 0 ? saveCol.getWidth() : saveCol.getPrefWidth();
        layoutRef.set(new SaveHeaderLayoutProbe(columnWidth, headerWidth, line1, line2));
    }

    private static double textWidth(String text, Font font) {
        Text probe = new Text(text);
        probe.setFont(font);
        return probe.getLayoutBounds().getWidth();
    }

    private static void findColorResolutionTable(AtomicReference<TableView<ColorResolutionRow>> tableRef) {
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && "Разрешение цветов СО7".equals(stage.getTitle())) {
                if (stage.getScene() != null) {
                    for (Node node : stage.getScene().getRoot().lookupAll(".tmp-color-resolution-table")) {
                        if (node instanceof TableView<?> tv) {
                            @SuppressWarnings("unchecked")
                            TableView<ColorResolutionRow> table = (TableView<ColorResolutionRow>) tv;
                            tableRef.set(table);
                            return;
                        }
                    }
                }
            }
        }
    }

    private static void findDialogPane(AtomicReference<DialogPane> paneRef) {
        for (Window window : Window.getWindows()) {
            if (window instanceof Stage stage && "Разрешение цветов СО7".equals(stage.getTitle())) {
                if (stage.getScene() != null) {
                    for (Node node : stage.getScene().getRoot().lookupAll(".dialog-pane")) {
                        if (node instanceof DialogPane pane) {
                            paneRef.set(pane);
                            return;
                        }
                    }
                }
            }
        }
    }

    private static int countDescendants(Node root, Class<?> type) {
        int count = type.isInstance(root) ? 1 : 0;
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                count += countDescendants(child, type);
            }
        }
        return count;
    }

    private static String readSourceSafe(String relativePath) {
        java.nio.file.Path moduleRelative =
                java.nio.file.Path.of("src/main/java/com/tmp/ui/shell/screen/orderimport/colors/ColorResolutionDialog.java");
        java.util.List<java.nio.file.Path> candidates =
                java.util.List.of(
                        java.nio.file.Path.of(relativePath),
                        moduleRelative,
                        java.nio.file.Path.of("tmp-ui-shell").resolve(moduleRelative));
        for (java.nio.file.Path candidate : candidates) {
            try {
                if (java.nio.file.Files.isRegularFile(candidate)) {
                    return java.nio.file.Files.readString(candidate);
                }
            } catch (Exception ignored) {
                // try next candidate
            }
        }
        return "";
    }

    private static ColorResolutionSession sessionUnmatched(String order, String color) {
        return new ColorResolutionCoordinator(unmatchedApi(color, color.toLowerCase()), new FakeConstraint())
                .prepare(List.of(sampleBatch(order, color)))
                .session();
    }

    private static ColorResolutionSession sessionTwoRows(String color) {
        OrderImportBatch batch =
                batchWithPositions("ORD", List.of(positionWithColor("1", "P1", color), positionWithColor("2", "P2", color)));
        return new ColorResolutionCoordinator(unmatchedApi(color, color.toLowerCase()), new FakeConstraint())
                .prepare(List.of(batch))
                .session();
    }

    private static ColorResolutionSession largeSession(int lineCount) {
        List<OrderImportSpecificationLine> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(OrderImportSpecificationLine.of("M" + i, "Material", "Цвет", BigDecimal.ONE, "шт", BigDecimal.ONE));
        }
        OrderImportPosition position = OrderImportPosition.of("1", "P1", "Item", 1, lines);
        OrderImportBatch batch = batchWithPositions("ORD-L", List.of(position));
        return new ColorResolutionCoordinator(unmatchedApi("Цвет", "цвет"), new FakeConstraint())
                .prepare(List.of(batch))
                .session();
    }

    private static OrderImportBatch sampleBatch(String orderNumber, String color) {
        return batchWithPositions(orderNumber, List.of(positionWithColor("1", "P1", color)));
    }

    private static OrderImportBatch batchWithPositions(String orderNumber, List<OrderImportPosition> positions) {
        return OrderImportBatch.of(
                "STXT",
                "f.stxt",
                "chk",
                orderNumber,
                LocalDate.of(2026, 1, 1),
                null,
                "Client",
                positions);
    }

    private static OrderImportPosition positionWithColor(String pos, String code, String color) {
        return OrderImportPosition.of(pos, code, "Item " + pos, 1, List.of(line("MAT", color)));
    }

    private static OrderImportSpecificationLine line(String material, String color) {
        return OrderImportSpecificationLine.of(material, "Material", color, BigDecimal.ONE, "шт", BigDecimal.ONE);
    }

    private static FakeColorApi unmatchedApi(String text, String key) {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put(text, SourceColorResolution.noMapping(text, key));
        return api;
    }

    private static FakeColorApi inactiveApi(String text, String key) {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put(text, SourceColorResolution.mappingInactive(text, key));
        return api;
    }

    private static final class FakeConstraint implements SpecificationColorConstraint {

        private final java.util.Set<String> reject = new java.util.HashSet<>();

        @Override
        public Result check(String colorValue) {
            if (colorValue == null || colorValue.trim().isEmpty()) {
                return Result.rejected(RejectionReason.BLANK);
            }
            if (reject.contains(colorValue.trim())) {
                return Result.rejected(RejectionReason.PLACEHOLDER);
            }
            if (colorValue.trim().length() > MAX_COLOR_LENGTH) {
                return Result.rejected(RejectionReason.TOO_LONG);
            }
            return Result.allowed();
        }
    }

    private static final class FakeColorApi implements ColorImportResolutionApi {

        private final boolean automatic;
        private boolean canSave = true;
        private final Map<String, SourceColorResolution> resolutions = new HashMap<>();

        private FakeColorApi(boolean automatic) {
            this.automatic = automatic;
        }

        @Override
        public boolean isAutomaticMatchingEnabled() {
            return automatic;
        }

        @Override
        public List<SourceColorResolution> resolveSourceTexts(List<String> sourceTexts) {
            List<SourceColorResolution> result = new ArrayList<>();
            for (String text : sourceTexts) {
                result.add(resolutions.getOrDefault(text, SourceColorResolution.noMapping(text, text.toLowerCase())));
            }
            return result;
        }

        @Override
        public List<ColorDto> listActiveColors() {
            return List.of(
                    new ColorDto(CAT_BELYI, "Белый", true, 1),
                    new ColorDto(CAT_RAL7016, "RAL7016", true, 1));
        }

        @Override
        public ResolvedColorPair composePair(UUID insideColorId, UUID outsideColorId) {
            return new ResolvedColorPair(CAT_BELYI, CAT_RAL7016, "Белый", "RAL7016", "Белый/RAL7016");
        }

        @Override
        public boolean canSaveGlobalMappings() {
            return canSave;
        }
    }
}