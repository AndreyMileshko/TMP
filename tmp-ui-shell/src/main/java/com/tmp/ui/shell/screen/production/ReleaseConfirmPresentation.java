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
        summaryLabel.setMaxWidth(640);

        Button toggleButton = new Button(SHOW_MATERIALS);
        TextField searchField = new TextField();
        searchField.setPromptText(SEARCH_PROMPT);
        searchField.setVisible(false);
        searchField.setManaged(false);

        VBox orderGroupsBox = new VBox(8);
        ScrollPane materialsScroll = new ScrollPane(orderGroupsBox);
        materialsScroll.setFitToWidth(true);
        materialsScroll.setPrefViewportHeight(260);
        materialsScroll.setMaxHeight(320);
        materialsScroll.setVisible(false);
        materialsScroll.setManaged(false);
        VBox.setVgrow(materialsScroll, Priority.ALWAYS);

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
                });

        Label footer = new Label(ProductionUiErrorMapper.RELEASE_CONFIRM_STOCK_NOTE);
        footer.setWrapText(true);
        footer.setMaxWidth(640);

        VBox root =
                new VBox(
                        10,
                        summaryLabel,
                        toggleButton,
                        searchField,
                        materialsScroll,
                        footer);
        root.setPadding(new Insets(8));
        root.setPrefWidth(680);
        root.setMinHeight(Region.USE_PREF_SIZE);

        ButtonType releaseType =
                new ButtonType(ReleaseDialogSupport.RELEASE_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType =
                new ButtonType(ReleaseDialogSupport.CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(releaseType, cancelType);
        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().setPrefWidth(720);

        Optional<ButtonType> result = dialog.showAndWait();
        return result.isPresent() && result.get() == releaseType;
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
            TitledPane pane =
                    new TitledPane(
                            orderLabel
                                    + " — "
                                    + rows.size()
                                    + (rows.size() == 1 ? " материал" : " материалов"),
                            table);
            pane.setExpanded(false);
            pane.setAnimated(false);
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
        table.setPrefHeight(160);
        table.setPlaceholder(new Label("Нет данных"));
        table.getColumns()
                .setAll(
                        stringColumn("Артикул", 90, ConfirmMaterialAggregate::materialCode),
                        stringColumn("Материал", 180, ConfirmMaterialAggregate::materialName),
                        stringColumn("Факт", 80, ConfirmMaterialAggregate::actualLabel),
                        stringColumn("Ед.", 50, ConfirmMaterialAggregate::unitOfMeasure),
                        deviationColumn());
        return table;
    }

    private static TableColumn<ConfirmMaterialAggregate, String> stringColumn(
            String title,
            double pref,
            java.util.function.Function<ConfirmMaterialAggregate, String> value) {
        TableColumn<ConfirmMaterialAggregate, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(value.apply(data.getValue())));
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

    private record OrderGroupView(
            UUID orderId,
            String orderLabel,
            TitledPane pane,
            ObservableList<ConfirmMaterialAggregate> items) {}
}
