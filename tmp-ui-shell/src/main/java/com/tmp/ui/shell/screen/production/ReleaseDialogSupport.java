package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessView;
import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.theme.TmpTheme;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Cross-order Release wizard dialogs: quantities, readiness details, plan/fact, confirm summary.
 * Presentation only — callers invoke ProductionApplicationApi.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "SIC_INNER_SHOULD_BE_STATIC_ANON"},
        justification =
                "Dialog outcome records and JavaFX TableCell factories intentionally expose"
                        + " presentation state")
public final class ReleaseDialogSupport {

    public static final String RELEASE_BUTTON = "Выпустить";
    public static final String STEP1_TITLE = "Выпуск изделий";
    public static final String STEP1_HEADER = "КОЛИЧЕСТВО К ВЫПУСКУ";
    public static final String STEP2_TITLE = "Фактический расход материалов";
    public static final String CONFIRM_TITLE = "Выпустить изделия?";
    public static final String NEXT_BUTTON = "Далее";
    public static final String CANCEL_BUTTON = "Отмена";
    public static final String DETAILS_BUTTON = "Подробнее";
    public static final String ADD_ALLOCATION_BUTTON = "Добавить ячейку";
    public static final String REMOVE_ALLOCATION_BUTTON = "Удалить";

    private ReleaseDialogSupport() {}

    public record Step1Outcome(boolean proceed, List<ReleaseQuantityRow> rows) {
        public Step1Outcome {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public static Step1Outcome cancelled() {
            return new Step1Outcome(false, List.of());
        }

        public static Step1Outcome next(List<ReleaseQuantityRow> rows) {
            return new Step1Outcome(true, rows);
        }
    }

    public record Step2Outcome(boolean proceed, List<ReleaseMaterialRow> materials) {
        public Step2Outcome {
            materials = materials == null ? List.of() : List.copyOf(materials);
        }

        public static Step2Outcome cancelled() {
            return new Step2Outcome(false, List.of());
        }

        public static Step2Outcome next(List<ReleaseMaterialRow> materials) {
            return new Step2Outcome(true, materials);
        }
    }

    public static Optional<String> validateStep1Quantities(List<ReleaseQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        for (ReleaseQuantityRow row : rows) {
            if (row.standardMode()) {
                if (row.releaseQuantity() != row.activeProductionQuantity()) {
                    return Optional.of(
                            "Для стандартного режима количество к выпуску должно равняться"
                                    + " остатку ("
                                    + row.activeProductionQuantity()
                                    + ") для "
                                    + row.orderNumberLabel()
                                    + " / "
                                    + row.positionLabel()
                                    + ".");
                }
                continue;
            }
            long qty = row.releaseQuantity();
            if (qty <= 0L) {
                return Optional.of(
                        "Укажите количество к выпуску больше 0 для "
                                + row.orderNumberLabel()
                                + " / "
                                + row.positionLabel()
                                + ".");
            }
            if (qty > row.activeProductionQuantity()) {
                return Optional.of(
                        "Количество к выпуску не может превышать остаток ("
                                + row.activeProductionQuantity()
                                + ") для "
                                + row.orderNumberLabel()
                                + " / "
                                + row.positionLabel()
                                + ".");
            }
        }
        return Optional.empty();
    }

    public static long resolvedReleaseQuantity(ReleaseQuantityRow row) {
        Objects.requireNonNull(row, "row");
        if (row.standardMode()) {
            return row.activeProductionQuantity();
        }
        return row.releaseQuantity();
    }

    public static Step1Outcome showStep1(List<ReleaseQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows must not be empty");
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle(STEP1_TITLE);
        dialog.setHeaderText(STEP1_HEADER);

        VBox root = new VBox(10);
        root.setPadding(new Insets(8));
        root.getChildren().add(new Label(buildOrderModeSummary(rows)));

        TableView<ReleaseQuantityRow> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Нет данных"));
        table.setPrefHeight(320);
        table.getColumns()
                .setAll(
                        stringColumn("Заказ", 110, ReleaseQuantityRow::orderNumberLabel),
                        stringColumn("Позиция", 80, ReleaseQuantityRow::positionLabel),
                        stringColumn("Изделие", 160, ReleaseQuantityRow::productLabel),
                        stringColumn("Режим", 100, ReleaseQuantityRow::modeLabel),
                        stringColumn(
                                "Заказано",
                                80,
                                row -> Long.toString(row.orderedQuantity())),
                        stringColumn(
                                "Изготовлено",
                                90,
                                row -> Long.toString(row.releasedQuantity())),
                        stringColumn(
                                "Осталось",
                                80,
                                row -> Long.toString(row.activeProductionQuantity())),
                        releaseQuantityColumn());
        root.getChildren().add(table);

        ButtonType nextType =
                new ButtonType(NEXT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType =
                new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(nextType, cancelType);
        dialog.getDialogPane().setContent(root);

        Button nextButton = (Button) dialog.getDialogPane().lookupButton(nextType);
        nextButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    Optional<String> error = validateStep1Quantities(table.getItems());
                    if (error.isPresent()) {
                        showValidation(error.get());
                        event.consume();
                    }
                });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != nextType) {
            return Step1Outcome.cancelled();
        }
        return Step1Outcome.next(List.copyOf(table.getItems()));
    }

    public static void showReadinessBlocked(MaterialReadinessView readiness) {
        Objects.requireNonNull(readiness, "readiness");
        Alert alert = new Alert(Alert.AlertType.WARNING);
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle(STEP1_TITLE);
        alert.setHeaderText(readinessBlockedHeader(readiness));
        if (!readinessDetailsAvailable(readiness)) {
            alert.setContentText("Выпуск нельзя продолжить.");
            alert.showAndWait();
            return;
        }
        alert.setContentText("Нажмите «Подробнее», чтобы увидеть дефицит по материалам.");
        ButtonType details =
                new ButtonType(DETAILS_BUTTON, ButtonBar.ButtonData.OTHER);
        ButtonType close =
                new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(details, close);
        Optional<ButtonType> choice = alert.showAndWait();
        if (choice.isPresent() && choice.get() == details) {
            showReadinessDetails(readiness);
        }
    }

    /** Header text for a release readiness block — never treats unresolved identity as shortage. */
    public static String readinessBlockedHeader(MaterialReadinessView readiness) {
        Objects.requireNonNull(readiness, "readiness");
        return switch (readiness.status()) {
            case NO_PRODUCTION_WAREHOUSE -> ProductionUiErrorMapper.NO_PRODUCTION_WAREHOUSE;
            case MATERIAL_REFERENCE_UNRESOLVED -> ProductionUiErrorMapper.MATERIALS_UNRESOLVED;
            default -> ProductionUiErrorMapper.RELEASE_MATERIALS_NOT_READY;
        };
    }

    /**
     * Details are available only for true shortage readiness with at least one shortage line.
     * Unresolved identity with empty lines must not open an empty table.
     */
    public static boolean readinessDetailsAvailable(MaterialReadinessView readiness) {
        Objects.requireNonNull(readiness, "readiness");
        if (readiness.status()
                != com.tmp.production.api.ProductionApplicationApi.MaterialReadinessStatusView
                        .NOT_READY) {
            return false;
        }
        return readiness.lines() != null
                && readiness.lines().stream()
                        .anyMatch(line -> line.shortageQuantity().signum() > 0);
    }

    public static void showReadinessDetails(MaterialReadinessView readiness) {
        Objects.requireNonNull(readiness, "readiness");
        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle("Готовность материалов");
        dialog.setHeaderText(ProductionUiErrorMapper.RELEASE_MATERIALS_NOT_READY);

        TableView<MaterialReadinessLineView> table = new TableView<>();
        List<MaterialReadinessLineView> lines =
                readiness.lines().stream()
                        .filter(line -> line.shortageQuantity().signum() > 0)
                        .toList();
        if (lines.isEmpty()) {
            lines = readiness.lines();
        }
        table.setItems(FXCollections.observableArrayList(lines));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Нет данных"));
        table.setPrefHeight(280);
        table.getColumns()
                .setAll(
                        readinessStringColumn(
                                "Материал",
                                180,
                                line ->
                                        line.materialCode()
                                                + " "
                                                + line.materialName()
                                                + (line.color().isBlank()
                                                        ? ""
                                                        : " (" + line.color() + ")")),
                        readinessStringColumn(
                                "Требуется",
                                90,
                                line -> DecimalUiFormat.format(line.requiredQuantity())),
                        readinessStringColumn(
                                "Доступно",
                                90,
                                line -> DecimalUiFormat.format(line.availableQuantity())),
                        readinessStringColumn(
                                "Не хватает",
                                90,
                                line -> DecimalUiFormat.format(line.shortageQuantity())),
                        readinessStringColumn("Ед.", 50, MaterialReadinessLineView::unitOfMeasure));
        dialog.getDialogPane().setContent(table);
        dialog.getDialogPane()
                .getButtonTypes()
                .setAll(new ButtonType("Закрыть", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }

    public static Step2Outcome showStep2(List<ReleaseMaterialRow> materials, boolean canEdit) {
        Objects.requireNonNull(materials, "materials");
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("materials must not be empty");
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle(STEP2_TITLE);
        dialog.setHeaderText("План / факт расхода");

        TableView<ReleaseMaterialRow> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(materials));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Нет данных"));
        table.setPrefHeight(300);
        table.getColumns()
                .setAll(
                        materialStringColumn("Заказ", 100, ReleaseMaterialRow::orderNumberLabel),
                        materialStringColumn("Материал", 180, ReleaseMaterialRow::materialLabel),
                        materialStringColumn("План", 80, ReleaseMaterialRow::plannedQuantity),
                        actualQuantityColumn(canEdit),
                        materialStringColumn("Ячейки", 160, ReleaseMaterialRow::allocationSummary));

        HBox allocationBar = new HBox(8);
        Button addAllocation = new Button(ADD_ALLOCATION_BUTTON);
        Button removeAllocation = new Button(REMOVE_ALLOCATION_BUTTON);
        addAllocation.setDisable(!canEdit);
        removeAllocation.setDisable(!canEdit);
        addAllocation.setOnAction(
                e -> {
                    ReleaseMaterialRow selected = table.getSelectionModel().getSelectedItem();
                    if (selected == null) {
                        showValidation("Выберите материал для добавления ячейки.");
                        return;
                    }
                    ReleaseMaterialRow.CellAllocation allocation = selected.addAllocation();
                    allocation.setQuantity(selected.actualQuantity());
                    openAllocationEditor(selected, allocation, canEdit);
                    table.refresh();
                });
        removeAllocation.setOnAction(
                e -> {
                    ReleaseMaterialRow selected = table.getSelectionModel().getSelectedItem();
                    if (selected == null || selected.allocations().isEmpty()) {
                        return;
                    }
                    selected.removeAllocation(
                            selected.allocations().get(selected.allocations().size() - 1));
                    table.refresh();
                });
        allocationBar.getChildren().addAll(addAllocation, removeAllocation);

        VBox root = new VBox(10, table, allocationBar);
        root.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);

        ButtonType nextType =
                new ButtonType(NEXT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType =
                new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(nextType, cancelType);
        dialog.getDialogPane().setContent(root);

        Button nextButton = (Button) dialog.getDialogPane().lookupButton(nextType);
        nextButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    Optional<String> error = validatePlanFact(table.getItems());
                    if (error.isPresent()) {
                        showValidation(error.get());
                        event.consume();
                    }
                });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != nextType) {
            return Step2Outcome.cancelled();
        }
        return Step2Outcome.next(List.copyOf(table.getItems()));
    }

    public static boolean showConfirmSummary(List<ReleaseQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle(CONFIRM_TITLE);
        alert.setHeaderText("ВЫПУСТИТЬ ИЗДЕЛИЯ?");
        alert.setContentText(buildConfirmSummary(rows) + "\n\nМатериалы проверены.");
        Button ok = (Button) alert.getDialogPane().lookupButton(ButtonType.OK);
        if (ok != null) {
            ok.setText(RELEASE_BUTTON);
        }
        Button cancel = (Button) alert.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancel != null) {
            cancel.setText(CANCEL_BUTTON);
        }
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    public static void showValidation(String message) {
        showWrappedMessage(Alert.AlertType.WARNING, STEP1_TITLE, message);
    }

    public static void showInfo(String title, String message) {
        showWrappedMessage(Alert.AlertType.INFORMATION, title, message);
    }

    private static void showWrappedMessage(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type);
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle(title);
        alert.setHeaderText(null);
        Label content = new Label(message == null ? "" : message);
        content.setWrapText(true);
        content.setMaxWidth(560);
        content.setMinHeight(Label.USE_PREF_SIZE);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(620);
        alert.getDialogPane().setMinHeight(180);
        alert.setResizable(true);
        alert.showAndWait();
    }

    public static Optional<String> validatePlanFact(List<ReleaseMaterialRow> materials) {
        Objects.requireNonNull(materials, "materials");
        for (ReleaseMaterialRow row : materials) {
            BigDecimal actual;
            try {
                actual =
                        DecimalQuantityParser.parseNonNegative(
                                row.actualQuantity(), "Фактическое количество");
            } catch (RuntimeException ex) {
                return Optional.of(
                        "Проверьте фактическое количество для материала "
                                + row.materialLabel()
                                + ".");
            }
            if (actual.signum() == 0) {
                if (!row.allocations().isEmpty()) {
                    return Optional.of(
                            "При фактическом количестве 0 распределения по ячейкам должны быть"
                                    + " пустыми ("
                                    + row.materialLabel()
                                    + ").");
                }
                continue;
            }
            if (row.allocations().isEmpty()) {
                return Optional.of(
                        "Добавьте распределение по ячейке производства для "
                                + row.materialLabel()
                                + ".");
            }
            BigDecimal sum = BigDecimal.ZERO;
            java.util.Set<UUID> cells = new java.util.HashSet<>();
            for (ReleaseMaterialRow.CellAllocation allocation : row.allocations()) {
                if (allocation.productionCell() == null) {
                    return Optional.of(
                            "Выберите ячейку склада производства для "
                                    + row.materialLabel()
                                    + ".");
                }
                if (!cells.add(allocation.productionCell().id())) {
                    return Optional.of(
                            "Дублирующая ячейка в распределении для "
                                    + row.materialLabel()
                                    + ".");
                }
                BigDecimal qty;
                try {
                    qty =
                            DecimalQuantityParser.parsePositive(
                                    allocation.quantity(), "Количество по ячейке");
                } catch (RuntimeException ex) {
                    return Optional.of(
                            "Проверьте количество по ячейке для "
                                    + row.materialLabel()
                                    + ".");
                }
                sum = sum.add(qty);
            }
            if (sum.compareTo(actual) != 0) {
                return Optional.of(
                        "Сумма распределений ("
                                + DecimalUiFormat.format(sum)
                                + ") должна равняться факту ("
                                + DecimalUiFormat.format(actual)
                                + ") для "
                                + row.materialLabel()
                                + ".");
            }
        }
        return Optional.empty();
    }

    private static void openAllocationEditor(
            ReleaseMaterialRow material,
            ReleaseMaterialRow.CellAllocation allocation,
            boolean canEdit) {
        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle("Ячейка расхода");
        dialog.setHeaderText(material.materialLabel());

        ComboBox<StorageCellChoice> cells = new ComboBox<>(material.cellChoices());
        cells.setValue(allocation.productionCell());
        cells.setDisable(!canEdit);
        TextField quantity = new TextField(allocation.quantity());
        quantity.setDisable(!canEdit);

        VBox root =
                new VBox(
                        8,
                        new Label("Ячейка"),
                        cells,
                        new Label("Количество"),
                        quantity);
        root.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(root);
        ButtonType save =
                new ButtonType("Сохранить", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel =
                new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().setAll(save, cancel);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == save && canEdit) {
            allocation.setProductionCell(cells.getValue());
            allocation.setQuantity(quantity.getText());
        }
    }

    private static String buildOrderModeSummary(List<ReleaseQuantityRow> rows) {
        Map<UUID, String> modes = new LinkedHashMap<>();
        for (ReleaseQuantityRow row : rows) {
            modes.putIfAbsent(
                    row.sourceOrderId(),
                    row.orderNumberLabel() + " — " + row.modeLabel());
        }
        return String.join("\n", modes.values());
    }

    private static String buildConfirmSummary(List<ReleaseQuantityRow> rows) {
        Map<UUID, long[]> aggregates = new LinkedHashMap<>();
        Map<UUID, String> labels = new LinkedHashMap<>();
        for (ReleaseQuantityRow row : rows) {
            labels.putIfAbsent(row.sourceOrderId(), row.orderNumberLabel());
            long[] agg = aggregates.computeIfAbsent(row.sourceOrderId(), id -> new long[2]);
            agg[0] += 1;
            agg[1] += resolvedReleaseQuantity(row);
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<UUID, long[]> entry : aggregates.entrySet()) {
            if (text.length() > 0) {
                text.append("\n\n");
            }
            long[] agg = entry.getValue();
            text.append(labels.get(entry.getKey()))
                    .append("\n")
                    .append(agg[0])
                    .append(agg[0] == 1 ? " позиция" : " позиций")
                    .append("\nВсего изделий: ")
                    .append(agg[1]);
        }
        return text.toString();
    }

    private static TableColumn<ReleaseQuantityRow, String> stringColumn(
            String title, double pref, Function<ReleaseQuantityRow, String> value) {
        TableColumn<ReleaseQuantityRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(value.apply(data.getValue())));
        return column;
    }

    private static TableColumn<ReleaseQuantityRow, String> releaseQuantityColumn() {
        TableColumn<ReleaseQuantityRow, String> column = new TableColumn<>("К выпуску");
        column.setPrefWidth(90);
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final TextField field = new TextField();

                            {
                                field.setOnAction(e -> commit());
                                field.focusedProperty()
                                        .addListener(
                                                (obs, was, is) -> {
                                                    if (was && !is) {
                                                        commit();
                                                    }
                                                });
                            }

                            private void commit() {
                                ReleaseQuantityRow row = getTableRow() == null
                                        ? null
                                        : getTableRow().getItem();
                                if (row == null || row.standardMode()) {
                                    return;
                                }
                                try {
                                    long value = Long.parseLong(field.getText().trim());
                                    row.setReleaseQuantity(value);
                                } catch (NumberFormatException ignored) {
                                    field.setText(Long.toString(row.releaseQuantity()));
                                }
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    setText(null);
                                    return;
                                }
                                ReleaseQuantityRow row = getTableRow().getItem();
                                if (row.standardMode()) {
                                    setGraphic(null);
                                    setText(Long.toString(row.activeProductionQuantity()));
                                } else {
                                    field.setText(Long.toString(row.releaseQuantity()));
                                    setText(null);
                                    setGraphic(field);
                                }
                            }
                        });
        return column;
    }

    private static TableColumn<ReleaseMaterialRow, String> materialStringColumn(
            String title, double pref, Function<ReleaseMaterialRow, String> value) {
        TableColumn<ReleaseMaterialRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(value.apply(data.getValue())));
        return column;
    }

    private static TableColumn<ReleaseMaterialRow, String> actualQuantityColumn(boolean canEdit) {
        TableColumn<ReleaseMaterialRow, String> column = new TableColumn<>("Факт");
        column.setPrefWidth(90);
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final TextField field = new TextField();

                            {
                                field.setDisable(!canEdit);
                                field.setOnAction(e -> commit());
                                field.focusedProperty()
                                        .addListener(
                                                (obs, was, is) -> {
                                                    if (was && !is) {
                                                        commit();
                                                    }
                                                });
                            }

                            private void commit() {
                                ReleaseMaterialRow row =
                                        getTableRow() == null ? null : getTableRow().getItem();
                                if (row == null || !canEdit) {
                                    return;
                                }
                                row.setActualQuantity(field.getText());
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    setText(null);
                                    return;
                                }
                                ReleaseMaterialRow row = getTableRow().getItem();
                                field.setText(row.actualQuantity());
                                setText(null);
                                setGraphic(field);
                            }
                        });
        return column;
    }

    private static TableColumn<MaterialReadinessLineView, String> readinessStringColumn(
            String title, double pref, Function<MaterialReadinessLineView, String> value) {
        TableColumn<MaterialReadinessLineView, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setCellValueFactory(
                data -> new SimpleStringProperty(value.apply(data.getValue())));
        return column;
    }
}
