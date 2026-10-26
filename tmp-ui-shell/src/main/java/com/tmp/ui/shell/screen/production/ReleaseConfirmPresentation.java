package com.tmp.ui.shell.screen.production;

import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.theme.TmpTheme;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.stage.Window;
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Compact Release confirmation: summary by default, optional expandable material details.
 * Display aggregation never mutates Step 2 rows or the posting payload.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "SIC_INNER_SHOULD_BE_STATIC_ANON"},
        justification = "Presentation records and JavaFX cells expose display-only state")
public final class ReleaseConfirmPresentation {

    public static final String SHOW_MATERIALS = "Показать материалы";
    public static final String HIDE_MATERIALS = "Скрыть материалы";
    public static final String SEARCH_PROMPT = "Поиск: артикул или наименование";
    public static final String DEVIATION_MARK = "≠";
    public static final String DEVIATION_OK = "";

    /** Preferred confirm dialog width (dialog pane). */
    public static final double CONFIRM_DIALOG_PREF_WIDTH = 920;

    /** Minimum dialog width so action buttons and columns remain usable on smaller displays. */
    public static final double CONFIRM_DIALOG_MIN_WIDTH = 640;

    /** Fixed row height for predictable material-table viewport sizing. */
    public static final double MATERIALS_TABLE_ROW_HEIGHT = 28;

    /** Header band included in the materials table preferred height. */
    public static final double MATERIALS_TABLE_HEADER_HEIGHT = 32;

    /** At least this many material rows should fit without internal table scrolling. */
    public static final int MATERIALS_TABLE_MIN_VISIBLE_ROWS = 6;

    /**
     * Preferred materials table height: header + six data rows. Larger lists scroll inside the
     * table; the outer ScrollPane still scrolls multi-order groups.
     */
    public static final double MATERIALS_TABLE_PREF_HEIGHT =
            MATERIALS_TABLE_HEADER_HEIGHT
                    + MATERIALS_TABLE_MIN_VISIBLE_ROWS * MATERIALS_TABLE_ROW_HEIGHT;

    /** Materials detail viewport preferred height when expanded (titled pane + six-row table). */
    public static final double MATERIALS_VIEWPORT_PREF_HEIGHT = 360;

    /** Materials detail viewport max height — keeps large lists scrollable inside the dialog. */
    public static final double MATERIALS_VIEWPORT_MAX_HEIGHT = 480;

    /** Expanded dialog may use at most this fraction of the screen visual height. */
    public static final double CONFIRM_DIALOG_MAX_SCREEN_HEIGHT_FRACTION = 0.9;

    /** Expanded dialog may use at most this fraction of the screen visual width. */
    public static final double CONFIRM_DIALOG_MAX_SCREEN_WIDTH_FRACTION = 0.92;

    private ReleaseConfirmPresentation() {}

    /** Compact summary counters for the collapsed confirmation view. */
    public record ConfirmSummaryStats(
            int orderCount,
            int positionCount,
            long productCount,
            int uniqueMaterialCount,
            int deviationCount,
            List<String> orderLabels) {

        public ConfirmSummaryStats {
            orderLabels = orderLabels == null ? List.of() : List.copyOf(orderLabels);
        }
    }

    /**
     * Display-only aggregate of identical materials within one order. Does not replace per-item
     * Step 2 rows used for posting.
     */
    public record ConfirmMaterialAggregate(
            UUID sourceOrderId,
            String orderNumberLabel,
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String unitOfMeasure,
            BigDecimal plannedQuantity,
            BigDecimal actualQuantity) {

        public ConfirmMaterialAggregate {
            Objects.requireNonNull(sourceOrderId, "sourceOrderId");
            Objects.requireNonNull(orderNumberLabel, "orderNumberLabel");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            materialCode = materialCode == null ? "" : materialCode;
            materialName = materialName == null ? "" : materialName;
            unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
            Objects.requireNonNull(plannedQuantity, "plannedQuantity");
            Objects.requireNonNull(actualQuantity, "actualQuantity");
        }

        public boolean hasDeviation() {
            return plannedQuantity.compareTo(actualQuantity) != 0;
        }

        public String plannedLabel() {
            return DecimalUiFormat.format(plannedQuantity);
        }

        public String actualLabel() {
            return DecimalUiFormat.format(actualQuantity);
        }

        public String deviationLabel() {
            return hasDeviation() ? DEVIATION_MARK : DEVIATION_OK;
        }

        boolean matchesSearch(String query) {
            if (query == null || query.isBlank()) {
                return true;
            }
            String needle = query.trim().toLowerCase(Locale.ROOT);
            return materialCode.toLowerCase(Locale.ROOT).contains(needle)
                    || materialName.toLowerCase(Locale.ROOT).contains(needle);
        }
    }

    public static ConfirmSummaryStats summarize(
            List<ReleaseQuantityRow> quantityRows, List<ReleaseMaterialRow> materials) {
        Objects.requireNonNull(quantityRows, "quantityRows");
        Objects.requireNonNull(materials, "materials");
        Map<UUID, String> orderLabels = new LinkedHashMap<>();
        int positions = 0;
        long products = 0L;
        for (ReleaseQuantityRow row : quantityRows) {
            orderLabels.putIfAbsent(row.sourceOrderId(), row.orderNumberLabel());
            positions++;
            products += ReleaseDialogSupport.resolvedReleaseQuantity(row);
        }
        List<ConfirmMaterialAggregate> aggregates = aggregateMaterialsForDisplay(materials);
        int deviations = 0;
        for (ConfirmMaterialAggregate aggregate : aggregates) {
            if (aggregate.hasDeviation()) {
                deviations++;
            }
        }
        return new ConfirmSummaryStats(
                orderLabels.size(),
                positions,
                products,
                aggregates.size(),
                deviations,
                List.copyOf(orderLabels.values()));
    }

    /**
     * Aggregates identical materials within the same order for confirmation display only.
     * Key: orderId + materialReferenceId + canonical UoM.
     */
    public static List<ConfirmMaterialAggregate> aggregateMaterialsForDisplay(
            List<ReleaseMaterialRow> materials) {
        Objects.requireNonNull(materials, "materials");
        Map<String, MutableAggregate> byKey = new LinkedHashMap<>();
        for (ReleaseMaterialRow row : materials) {
            String uom = row.unitOfMeasure() == null ? "" : row.unitOfMeasure();
            String key =
                    row.sourceOrderId()
                            + "|"
                            + row.materialReferenceId()
                            + "|"
                            + uom.trim().toLowerCase(Locale.ROOT);
            MutableAggregate aggregate =
                    byKey.computeIfAbsent(
                            key,
                            ignored ->
                                    new MutableAggregate(
                                            row.sourceOrderId(),
                                            row.orderNumberLabel(),
                                            row.materialReferenceId(),
                                            row.materialCode(),
                                            row.materialLabel(),
                                            uom));
            aggregate.planned = aggregate.planned.add(parseQuantity(row.plannedQuantity()));
            aggregate.actual = aggregate.actual.add(parseQuantity(row.actualQuantity()));
        }
        List<ConfirmMaterialAggregate> result = new ArrayList<>(byKey.size());
        for (MutableAggregate aggregate : byKey.values()) {
            result.add(
                    new ConfirmMaterialAggregate(
                            aggregate.sourceOrderId,
                            aggregate.orderNumberLabel,
                            aggregate.materialReferenceId,
                            aggregate.materialCode,
                            aggregate.materialName,
                            aggregate.unitOfMeasure,
                            aggregate.planned,
                            aggregate.actual));
        }
        return List.copyOf(result);
    }

    public static List<ConfirmMaterialAggregate> filterAggregates(
            List<ConfirmMaterialAggregate> aggregates, String query) {
        Objects.requireNonNull(aggregates, "aggregates");
        if (query == null || query.isBlank()) {
            return List.copyOf(aggregates);
        }
        List<ConfirmMaterialAggregate> filtered = new ArrayList<>();
        for (ConfirmMaterialAggregate aggregate : aggregates) {
            if (aggregate.matchesSearch(query)) {
                filtered.add(aggregate);
            }
        }
        return List.copyOf(filtered);
    }

    public static String buildSummaryText(ConfirmSummaryStats stats) {
        Objects.requireNonNull(stats, "stats");
        StringBuilder text = new StringBuilder();
        if (stats.orderCount() > 1) {
            text.append("Заказов: ").append(stats.orderCount()).append('\n');
            text.append(String.join(", ", stats.orderLabels())).append('\n');
        } else if (stats.orderCount() == 1) {
            text.append(stats.orderLabels().getFirst()).append('\n');
        }
        text.append("Позиций: ").append(stats.positionCount()).append('\n');
        text.append("Изделий к выпуску: ").append(stats.productCount()).append('\n');
        text.append("Уникальных материалов: ").append(stats.uniqueMaterialCount()).append('\n');
        text.append("Отклонений план/факт: ").append(stats.deviationCount());
        return text.toString();
    }

    /**
     * Confirm body text used by unit tests — summary + stock note; does not claim materials are
     * already verified.
     */
    public static String confirmSummaryContent(
            List<ReleaseQuantityRow> quantityRows, List<ReleaseMaterialRow> materials) {
        return buildSummaryText(summarize(quantityRows, materials))
                + "\n\n"
                + ProductionUiErrorMapper.RELEASE_CONFIRM_STOCK_NOTE;
    }

    public static boolean showConfirmSummary(
            List<ReleaseQuantityRow> quantityRows, List<ReleaseMaterialRow> materials) {
        ConfirmDialogSession session = createConfirmDialog(quantityRows, materials);
        Optional<ButtonType> result = session.dialog().showAndWait();
        return result.isPresent() && result.get() == session.releaseType();
    }

    /**
     * Builds the compact/expandable confirm dialog without showing it. Used by production UI and
     * focused JavaFX sizing tests.
     */
    public static ConfirmDialogSession createConfirmDialog(
            List<ReleaseQuantityRow> quantityRows, List<ReleaseMaterialRow> materials) {
        Objects.requireNonNull(quantityRows, "quantityRows");
        Objects.requireNonNull(materials, "materials");
        if (quantityRows.isEmpty()) {
            throw new IllegalArgumentException("quantityRows must not be empty");
        }

        List<ConfirmMaterialAggregate> allAggregates = aggregateMaterialsForDisplay(materials);
        ConfirmSummaryStats stats = summarize(quantityRows, materials);

        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle(ReleaseDialogSupport.CONFIRM_TITLE);
        dialog.setHeaderText("ВЫПУСТИТЬ ИЗДЕЛИЯ?");
        dialog.setResizable(true);

        Label summaryLabel = new Label(buildSummaryText(stats));
        summaryLabel.setWrapText(true);
        summaryLabel.setMaxWidth(Double.MAX_VALUE);

        Button toggleButton = new Button(SHOW_MATERIALS);
        TextField searchField = new TextField();
        searchField.setPromptText(SEARCH_PROMPT);
        searchField.setVisible(false);
        searchField.setManaged(false);

        VBox orderGroupsBox = new VBox(8);
        orderGroupsBox.setFillWidth(true);
        ScrollPane materialsScroll = new ScrollPane(orderGroupsBox);
        materialsScroll.setFitToWidth(true);
        materialsScroll.setPrefViewportHeight(MATERIALS_VIEWPORT_PREF_HEIGHT);
        materialsScroll.setMinViewportHeight(MATERIALS_TABLE_PREF_HEIGHT);
        materialsScroll.setMaxHeight(MATERIALS_VIEWPORT_MAX_HEIGHT);
        materialsScroll.setMinHeight(0);
        materialsScroll.setVisible(false);
        materialsScroll.setManaged(false);

        List<OrderGroupView> groupViews = buildOrderGroupViews(allAggregates);
        for (OrderGroupView group : groupViews) {
            orderGroupsBox.getChildren().add(group.pane());
        }

        Runnable applyFilter =
                () -> {
                    String query = searchField.getText();
                    List<ConfirmMaterialAggregate> filtered =
                            filterAggregates(allAggregates, query);
                    Map<UUID, List<ConfirmMaterialAggregate>> byOrder = groupByOrder(filtered);
                    for (OrderGroupView group : groupViews) {
                        List<ConfirmMaterialAggregate> rows =
                                byOrder.getOrDefault(group.orderId(), List.of());
                        group.items().setAll(rows);
                        group.pane().setVisible(!rows.isEmpty() || query == null || query.isBlank());
                        group.pane().setManaged(group.pane().isVisible());
                        group.pane()
                                .setText(
                                        group.orderLabel()
                                                + " — "
                                                + rows.size()
                                                + (rows.size() == 1
                                                        ? " материал"
                                                        : " материалов"));
                    }
                };
        searchField.textProperty().addListener((obs, old, value) -> applyFilter.run());

        Label footer = new Label(ProductionUiErrorMapper.RELEASE_CONFIRM_STOCK_NOTE);
        footer.setWrapText(true);
        footer.setMaxWidth(Double.MAX_VALUE);

        VBox root =
                new VBox(
                        10,
                        summaryLabel,
                        toggleButton,
                        searchField,
                        materialsScroll,
                        footer);
        root.setPadding(new Insets(8));
        root.setPrefWidth(CONFIRM_DIALOG_PREF_WIDTH - 40);
        root.setMinWidth(CONFIRM_DIALOG_MIN_WIDTH - 40);
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMinHeight(Region.USE_PREF_SIZE);
        VBox.setVgrow(materialsScroll, Priority.ALWAYS);

        ButtonType releaseType =
                new ButtonType(ReleaseDialogSupport.RELEASE_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType =
                new ButtonType(ReleaseDialogSupport.CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(releaseType, cancelType);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefWidth(CONFIRM_DIALOG_PREF_WIDTH);
        dialog.getDialogPane().setMinWidth(CONFIRM_DIALOG_MIN_WIDTH);

        ConfirmDialogSizing sizing = new ConfirmDialogSizing(dialog, materialsScroll);
        toggleButton.setOnAction(
                event -> {
                    boolean show = !materialsScroll.isVisible();
                    materialsScroll.setVisible(show);
                    materialsScroll.setManaged(show);
                    searchField.setVisible(show);
                    searchField.setManaged(show);
                    toggleButton.setText(show ? HIDE_MATERIALS : SHOW_MATERIALS);
                    if (show) {
                        applyFilter.run();
                    }
                    sizing.applyMaterialsExpanded(show);
                });

        ConfirmDialogSession session =
                new ConfirmDialogSession(
                        dialog,
                        toggleButton,
                        searchField,
                        materialsScroll,
                        root,
                        releaseType,
                        cancelType,
                        List.copyOf(groupViews),
                        sizing);
        sizing.install(dialog);
        return session;
    }

    /** Clamps a desired dialog height to the usable fraction of screen visual bounds. */
    public static double clampToScreenVisualHeight(double desiredHeight, double visualScreenHeight) {
        if (desiredHeight <= 0) {
            return desiredHeight;
        }
        if (visualScreenHeight <= 0) {
            return desiredHeight;
        }
        double max = visualScreenHeight * CONFIRM_DIALOG_MAX_SCREEN_HEIGHT_FRACTION;
        return Math.min(desiredHeight, max);
    }

    /** Clamps a desired dialog width to the usable fraction of screen visual bounds. */
    public static double clampToScreenVisualWidth(double desiredWidth, double visualScreenWidth) {
        if (desiredWidth <= 0) {
            return desiredWidth;
        }
        if (visualScreenWidth <= 0) {
            return desiredWidth;
        }
        double max = visualScreenWidth * CONFIRM_DIALOG_MAX_SCREEN_WIDTH_FRACTION;
        return Math.min(desiredWidth, max);
    }

    /**
     * Materials viewport max height for the current window: prefers the design max, but shrinks on
     * short screens so confirmation buttons stay reachable.
     */
    public static double materialsViewportMaxForScreen(double visualScreenHeight) {
        if (visualScreenHeight <= 0) {
            return MATERIALS_VIEWPORT_MAX_HEIGHT;
        }
        // Reserve chrome for header, summary, toggle, search, footer, and button bar.
        double available =
                visualScreenHeight * CONFIRM_DIALOG_MAX_SCREEN_HEIGHT_FRACTION - 280;
        double capped = Math.min(MATERIALS_VIEWPORT_MAX_HEIGHT, available);
        return Math.max(MATERIALS_TABLE_PREF_HEIGHT, capped);
    }

    /**
     * Testable confirm dialog handle. Presentation only — does not post Release.
     */
    public record ConfirmDialogSession(
            Dialog<ButtonType> dialog,
            Button toggleButton,
            TextField searchField,
            ScrollPane materialsScroll,
            VBox root,
            ButtonType releaseType,
            ButtonType cancelType,
            List<OrderGroupView> orderGroups,
            ConfirmDialogSizing sizing) {

        public ConfirmDialogSession {
            Objects.requireNonNull(dialog, "dialog");
            Objects.requireNonNull(toggleButton, "toggleButton");
            Objects.requireNonNull(searchField, "searchField");
            Objects.requireNonNull(materialsScroll, "materialsScroll");
            Objects.requireNonNull(root, "root");
            Objects.requireNonNull(releaseType, "releaseType");
            Objects.requireNonNull(cancelType, "cancelType");
            orderGroups = orderGroups == null ? List.of() : List.copyOf(orderGroups);
            Objects.requireNonNull(sizing, "sizing");
        }

        public boolean materialsExpanded() {
            return materialsScroll.isVisible();
        }

        public void expandMaterials() {
            if (!materialsExpanded()) {
                toggleButton.fire();
            }
        }

        public void collapseMaterials() {
            if (materialsExpanded()) {
                toggleButton.fire();
            }
        }

        public boolean wasManuallyResized() {
            return sizing.userResized();
        }

        public void markManuallyResizedForTest() {
            sizing.markUserResized();
        }
    }

    /** Auto-resize for expand/collapse while respecting manual window resize. */
    public static final class ConfirmDialogSizing {
        private final Dialog<?> dialog;
        private final ScrollPane materialsScroll;
        private double compactHeight = -1;
        private boolean userResized;
        private boolean applyingSize;

        ConfirmDialogSizing(Dialog<?> dialog, ScrollPane materialsScroll) {
            this.dialog = Objects.requireNonNull(dialog, "dialog");
            this.materialsScroll = Objects.requireNonNull(materialsScroll, "materialsScroll");
        }

        void install(Dialog<?> dialog) {
            dialog.setOnShown(
                    event -> {
                        Window window = windowOrNull();
                        if (window == null) {
                            return;
                        }
                        captureCompactHeight();
                        window.widthProperty()
                                .addListener((obs, oldV, newV) -> noteExternalResize());
                        window.heightProperty()
                                .addListener((obs, oldV, newV) -> noteExternalResize());
                    });
        }

        public boolean userResized() {
            return userResized;
        }

        void markUserResized() {
            userResized = true;
        }

        void applyMaterialsExpanded(boolean expanded) {
            Window window = windowOrNull();
            if (window == null) {
                // Dialog not shown yet (unit construction) — still layout content sizes.
                materialsScroll.setMaxHeight(MATERIALS_VIEWPORT_MAX_HEIGHT);
                layoutContent();
                return;
            }
            if (compactHeight <= 0) {
                captureCompactHeight();
            }
            applyingSize = true;
            try {
                double visualHeight = visualScreenHeight(window);
                double visualWidth = visualScreenWidth(window);
                materialsScroll.setMaxHeight(materialsViewportMaxForScreen(visualHeight));
                layoutContent();
                if (expanded) {
                    window.sizeToScene();
                    double desiredHeight = window.getHeight();
                    double clampedHeight = clampToScreenVisualHeight(desiredHeight, visualHeight);
                    if (clampedHeight < desiredHeight) {
                        window.setHeight(clampedHeight);
                    }
                    if (!userResized) {
                        double targetWidth =
                                Math.max(
                                        CONFIRM_DIALOG_MIN_WIDTH,
                                        clampToScreenVisualWidth(
                                                CONFIRM_DIALOG_PREF_WIDTH, visualWidth));
                        window.setWidth(targetWidth);
                    } else if (window.getWidth() < CONFIRM_DIALOG_MIN_WIDTH) {
                        window.setWidth(CONFIRM_DIALOG_MIN_WIDTH);
                    }
                } else if (!userResized && compactHeight > 0) {
                    layoutContent();
                    window.setHeight(compactHeight);
                } else {
                    layoutContent();
                }
            } finally {
                applyingSize = false;
            }
        }

        private void captureCompactHeight() {
            Window window = windowOrNull();
            if (window == null) {
                return;
            }
            if (!materialsScroll.isVisible() && !userResized) {
                compactHeight = window.getHeight();
            }
        }

        private void noteExternalResize() {
            if (!applyingSize) {
                userResized = true;
            }
        }

        private void layoutContent() {
            var pane = dialog.getDialogPane();
            if (pane.getContent() instanceof Parent content) {
                content.applyCss();
                content.layout();
            }
            pane.applyCss();
            pane.layout();
        }

        private Window windowOrNull() {
            var scene = dialog.getDialogPane().getScene();
            return scene == null ? null : scene.getWindow();
        }

        private static double visualScreenHeight(Window window) {
            return visualScreenBounds(window).getHeight();
        }

        private static double visualScreenWidth(Window window) {
            return visualScreenBounds(window).getWidth();
        }

        private static Rectangle2D visualScreenBounds(Window window) {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            if (window.getX() >= 0) {
                for (Screen screen : Screen.getScreens()) {
                    Rectangle2D vb = screen.getVisualBounds();
                    if (vb.contains(window.getX() + 8, window.getY() + 8)) {
                        return vb;
                    }
                }
            }
            return bounds;
        }
    }

    private static List<OrderGroupView> buildOrderGroupViews(
            List<ConfirmMaterialAggregate> aggregates) {
        Map<UUID, List<ConfirmMaterialAggregate>> byOrder = groupByOrder(aggregates);
        List<OrderGroupView> views = new ArrayList<>();
        for (Map.Entry<UUID, List<ConfirmMaterialAggregate>> entry : byOrder.entrySet()) {
            List<ConfirmMaterialAggregate> rows = entry.getValue();
            String orderLabel = rows.getFirst().orderNumberLabel();
            ObservableList<ConfirmMaterialAggregate> items =
                    FXCollections.observableArrayList(rows);
            TableView<ConfirmMaterialAggregate> table = materialTable(items);
            VBox tableHost = new VBox(table);
            tableHost.setFillWidth(true);
            VBox.setVgrow(table, Priority.ALWAYS);
            TitledPane pane =
                    new TitledPane(
                            orderLabel
                                    + " — "
                                    + rows.size()
                                    + (rows.size() == 1 ? " материал" : " материалов"),
                            tableHost);
            pane.setExpanded(false);
            pane.setAnimated(false);
            pane.setMaxWidth(Double.MAX_VALUE);
            views.add(new OrderGroupView(entry.getKey(), orderLabel, pane, items));
        }
        return views;
    }

    private static Map<UUID, List<ConfirmMaterialAggregate>> groupByOrder(
            List<ConfirmMaterialAggregate> aggregates) {
        Map<UUID, List<ConfirmMaterialAggregate>> byOrder = new LinkedHashMap<>();
        for (ConfirmMaterialAggregate aggregate : aggregates) {
            byOrder.computeIfAbsent(aggregate.sourceOrderId(), id -> new ArrayList<>())
                    .add(aggregate);
        }
        return byOrder;
    }

    private static TableView<ConfirmMaterialAggregate> materialTable(
            ObservableList<ConfirmMaterialAggregate> items) {
        TableView<ConfirmMaterialAggregate> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFixedCellSize(MATERIALS_TABLE_ROW_HEIGHT);
        table.setPrefHeight(MATERIALS_TABLE_PREF_HEIGHT);
        table.setMinHeight(MATERIALS_TABLE_PREF_HEIGHT);
        table.setMaxHeight(Double.MAX_VALUE);
        table.setPlaceholder(new Label("Нет данных"));
        TableColumn<ConfirmMaterialAggregate, String> codeColumn =
                stringColumn("Артикул", 150, 110, ConfirmMaterialAggregate::materialCode);
        TableColumn<ConfirmMaterialAggregate, String> nameColumn =
                stringColumn("Материал", 320, 160, ConfirmMaterialAggregate::materialName);
        TableColumn<ConfirmMaterialAggregate, String> actualColumn =
                stringColumn("Факт", 90, 70, ConfirmMaterialAggregate::actualLabel);
        TableColumn<ConfirmMaterialAggregate, String> uomColumn =
                stringColumn("Ед.", 56, 44, ConfirmMaterialAggregate::unitOfMeasure);
        table.getColumns()
                .setAll(codeColumn, nameColumn, actualColumn, uomColumn, deviationColumn());
        return table;
    }

    private static TableColumn<ConfirmMaterialAggregate, String> stringColumn(
            String title,
            double pref,
            double min,
            java.util.function.Function<ConfirmMaterialAggregate, String> value) {
        TableColumn<ConfirmMaterialAggregate, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setMinWidth(min);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null || item.isBlank()) {
                                    setText(null);
                                    setTooltip(null);
                                    return;
                                }
                                setText(item);
                                setTooltip(new Tooltip(item));
                            }
                        });
        return column;
    }

    private static TableColumn<ConfirmMaterialAggregate, String> deviationColumn() {
        TableColumn<ConfirmMaterialAggregate, String> column = new TableColumn<>("П/Ф");
        column.setPrefWidth(40);
        column.setMaxWidth(48);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().deviationLabel()));
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || item == null || item.isBlank()) {
                                    setText(null);
                                    setTooltip(null);
                                    setStyle("");
                                    return;
                                }
                                setText(item);
                                setTooltip(new Tooltip("Факт отличается от плана"));
                                setStyle("-fx-text-fill: #6d4c41; -fx-font-weight: bold;");
                            }
                        });
        return column;
    }

    private static BigDecimal parseQuantity(String raw) {
        try {
            return DecimalQuantityParser.parseNonNegative(raw, "Количество");
        } catch (RuntimeException ex) {
            return BigDecimal.ZERO;
        }
    }

    private static final class MutableAggregate {
        private final UUID sourceOrderId;
        private final String orderNumberLabel;
        private final UUID materialReferenceId;
        private final String materialCode;
        private final String materialName;
        private final String unitOfMeasure;
        private BigDecimal planned = BigDecimal.ZERO;
        private BigDecimal actual = BigDecimal.ZERO;

        private MutableAggregate(
                UUID sourceOrderId,
                String orderNumberLabel,
                UUID materialReferenceId,
                String materialCode,
                String materialName,
                String unitOfMeasure) {
            this.sourceOrderId = sourceOrderId;
            this.orderNumberLabel = orderNumberLabel;
            this.materialReferenceId = materialReferenceId;
            this.materialCode = materialCode == null ? "" : materialCode;
            this.materialName = materialName == null ? "" : materialName;
            this.unitOfMeasure = unitOfMeasure == null ? "" : unitOfMeasure;
        }
    }

    public record OrderGroupView(
            UUID orderId,
            String orderLabel,
            TitledPane pane,
            ObservableList<ConfirmMaterialAggregate> items) {}
}
