package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementDraftSummaryView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineProductionStockView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementLineView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementSourceItemView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementStatusView;
import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.ui.shell.order.DecimalQuantityParser;
import com.tmp.ui.shell.order.DecimalUiFormat;
import com.tmp.ui.shell.theme.TmpTheme;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
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
 * Cross-order Material Request dialogs (STEP 1 product quantities, STEP 2 DRAFT, draft list).
 * Presentation only — callers invoke ProductionApplicationApi.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "SIC_INNER_SHOULD_BE_STATIC_ANON"},
        justification =
                "Dialog outcome records and JavaFX TableCell factories intentionally expose"
                        + " presentation state")
public final class MaterialRequestDialogSupport {

    public static final String REQUEST_BUTTON = "Запросить материалы";
    public static final String DRAFTS_BUTTON = "Черновики материалов";
    public static final String STEP1_TITLE = "Запрос материалов";
    public static final String STEP1_HEADER = "КОЛИЧЕСТВО ИЗДЕЛИЙ";
    public static final String STEP2_TITLE = "Потребность в материалах";
    public static final String STEP2_STATUS_DRAFT = "Статус: Черновик";
    public static final String NEXT_BUTTON = "Далее";
    public static final String SUBMIT_BUTTON = "Отправить на склад";
    public static final String SAVE_LINE_BUTTON = "Сохранить";
    public static final String CANCEL_BUTTON = "Отмена";
    public static final String DELETE_DRAFT_BUTTON = "Удалить черновик";
    public static final String DRAFT_LIST_TITLE = "Черновики запросов материалов";
    public static final String COLUMN_ARTICLE = "Артикул";
    public static final String COLUMN_NAME = "Наименование";
    public static final String COLUMN_COLOR = "Цвет";
    public static final String COLUMN_UOM = "Ед.";
    public static final String COLUMN_REQUIRED = "Требуется";
    public static final String COLUMN_ON_PRODUCTION = "На производстве";
    public static final String COLUMN_TO_REQUEST = "К запросу";
    public static final String COLUMN_DRAFT_CREATED = "Создан";
    public static final String COLUMN_DRAFT_ORDERS = "Заказ";
    public static final String COLUMN_DRAFT_POSITIONS = "Позиции";
    public static final String COLUMN_DRAFT_PRODUCTS = "Изделия";
    public static final String STOCK_INFO_HINT =
            "Остаток на производстве показан справочно. Он общий для всех заказов и не уменьшает"
                    + " количество к запросу.";
    public static final String REQUEST_QUANTITY_RANGE_MESSAGE =
            "Количество к запросу должно быть не меньше 0.";
    public static final String DRAFT_SAVED_MESSAGE = "Черновик сохранён.";
    public static final String DELETE_DRAFT_CONFIRM_TITLE = "Удалить черновик?";
    public static final String DELETE_DRAFT_CONFIRM_HEADER =
            "Черновик будет удалён безвозвратно.";

    private static final DateTimeFormatter DRAFT_WHEN =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private MaterialRequestDialogSupport() {}

    public record Step1Outcome(boolean proceed, List<MaterialRequestQuantityRow> rows) {
        public Step1Outcome {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }

        public static Step1Outcome cancelled() {
            return new Step1Outcome(false, List.of());
        }

        public static Step1Outcome next(List<MaterialRequestQuantityRow> rows) {
            return new Step1Outcome(true, rows);
        }
    }

    public enum Step2Action {
        CLOSED,
        SUBMITTED
    }

    public record Step2Outcome(Step2Action action, boolean explicitlySaved) {
        public static Step2Outcome closed(boolean explicitlySaved) {
            return new Step2Outcome(Step2Action.CLOSED, explicitlySaved);
        }

        public static Step2Outcome submitted(boolean explicitlySaved) {
            return new Step2Outcome(Step2Action.SUBMITTED, explicitlySaved);
        }
    }

    /** Display row for the saved-drafts table (bound to exact {@code requirementId}). */
    public record DraftListRow(
            MaterialRequirementDraftSummaryView draft,
            String createdLabel,
            String ordersLabel,
            String positionsLabel,
            String productsLabel) {
        public DraftListRow {
            Objects.requireNonNull(draft, "draft");
            createdLabel = createdLabel == null ? "" : createdLabel;
            ordersLabel = ordersLabel == null ? "" : ordersLabel;
            positionsLabel = positionsLabel == null ? "" : positionsLabel;
            productsLabel = productsLabel == null ? "" : productsLabel;
        }

        public UUID requirementId() {
            return draft.requirementId();
        }
    }

    public record DraftListOutcome(Optional<UUID> openRequirementId) {
        public static DraftListOutcome cancelled() {
            return new DraftListOutcome(Optional.empty());
        }

        public static DraftListOutcome open(UUID requirementId) {
            return new DraftListOutcome(Optional.of(requirementId));
        }
    }

    public static Optional<String> validateStep1Quantities(List<MaterialRequestQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        for (MaterialRequestQuantityRow row : rows) {
            if (row.standardMode()) {
                continue;
            }
            long requested = row.requestedProductQuantity();
            long max = row.requestableProductQuantity();
            if (requested < 1L || requested > max) {
                return Optional.of(
                        "Количество должно быть от 1 до "
                                + max
                                + " для "
                                + row.orderNumberLabel()
                                + " / "
                                + row.positionLabel()
                                + ".");
            }
        }
        return Optional.empty();
    }

    public static boolean step1QuantitiesValid(List<MaterialRequestQuantityRow> rows) {
        return validateStep1Quantities(rows).isEmpty();
    }

    public static List<MaterialRequestDraftLineRow> toLineRows(
            MaterialRequirementView requirement,
            List<MaterialRequirementLineProductionStockView> productionStock) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(productionStock, "productionStock");
        Map<UUID, BigDecimal> stockByLineId = new HashMap<>();
        for (MaterialRequirementLineProductionStockView stock : productionStock) {
            stockByLineId.put(stock.lineId(), stock.onProductionAvailable());
        }
        List<MaterialRequestDraftLineRow> rows = new ArrayList<>();
        for (MaterialRequirementLineView line : requirement.lines()) {
            rows.add(
                    new MaterialRequestDraftLineRow(
                            line.lineId(),
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure(),
                            line.lengthMm(),
                            line.technologicalPieceCount(),
                            line.requiredQuantity(),
                            line.quantity(),
                            stockByLineId.get(line.lineId())));
        }
        return rows;
    }

    /**
     * Validates request quantity («К запросу»): {@code quantity >= 0}. May be below, equal to, or
     * above preliminary required.
     *
     * @return empty when valid; otherwise a concise Russian validation message
     */
    public static Optional<String> validateRequestQuantity(
            BigDecimal quantity, BigDecimal requiredQuantity) {
        Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        if (quantity == null) {
            return Optional.of(REQUEST_QUANTITY_RANGE_MESSAGE);
        }
        if (quantity.signum() < 0) {
            return Optional.of(REQUEST_QUANTITY_RANGE_MESSAGE);
        }
        return Optional.empty();
    }

    public static String sourceSummaryLine(
            MaterialRequirementSourceItemView source,
            Function<UUID, String> orderNumberResolver,
            Function<UUID, String> positionResolver,
            Function<UUID, String> productResolver) {
        Objects.requireNonNull(source, "source");
        String order =
                orderNumberResolver.apply(source.sourceOrderId());
        String position = positionResolver.apply(source.sourceOrderItemId());
        String product = productResolver.apply(source.sourceOrderItemId());
        return order
                + " / "
                + position
                + " — "
                + source.requestedProductQuantity()
                + " изд."
                + (product == null || product.isBlank() ? "" : " (" + product + ")");
    }

    public static String formatDraftCreatedAt(Instant createdAt) {
        Objects.requireNonNull(createdAt, "createdAt");
        return DRAFT_WHEN.format(createdAt.atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    /** Compact fallback caption used by focused unit tests. */
    public static String draftListCaption(MaterialRequirementDraftSummaryView draft) {
        Objects.requireNonNull(draft, "draft");
        return formatDraftCreatedAt(draft.createdAt())
                + "\n"
                + draft.sourceItemCount()
                + " "
                + pluralPositions(draft.sourceItemCount())
                + " / "
                + draft.orderCount()
                + " "
                + pluralOrders(draft.orderCount());
    }

    private static String pluralPositions(int count) {
        return count == 1 ? "позиция" : "позиции";
    }

    private static String pluralOrders(int count) {
        return count == 1 ? "заказ" : "заказа";
    }

    public static Optional<String> validateAllRequestQuantities(
            List<MaterialRequestDraftLineRow> rows) {
        Objects.requireNonNull(rows, "rows");
        for (MaterialRequestDraftLineRow row : rows) {
            Optional<String> validation =
                    validateRequestQuantity(row.quantity(), row.requiredQuantity());
            if (validation.isPresent()) {
                return validation;
            }
        }
        return Optional.empty();
    }

    public static Step1Outcome showStep1(List<MaterialRequestQuantityRow> rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("rows must not be empty");
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle(STEP1_TITLE);
        dialog.setHeaderText(STEP1_HEADER);

        TableView<MaterialRequestQuantityRow> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(rows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320);
        table.setPlaceholder(new Label("Нет данных"));

        table.getColumns()
                .addAll(
                        stringColumn("Заказ", 100, MaterialRequestQuantityRow::orderNumberLabel),
                        stringColumn("Позиция", 90, MaterialRequestQuantityRow::positionLabel),
                        stringColumn("Изделие", 180, MaterialRequestQuantityRow::productLabel),
                        stringColumn("Режим", 110, MaterialRequestQuantityRow::modeLabel),
                        stringColumn(
                                "Доступно запросить",
                                130,
                                row -> Long.toString(row.requestableProductQuantity())),
                        quantityColumn());

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("tmp-text-error");
        errorLabel.setWrapText(true);

        ButtonType nextType = new ButtonType(NEXT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(nextType, cancelType);

        Button nextButton = (Button) dialog.getDialogPane().lookupButton(nextType);
        Runnable refreshNextEnablement =
                () -> {
                    boolean valid = step1QuantitiesValid(table.getItems());
                    nextButton.setDisable(!valid);
                    if (valid) {
                        errorLabel.setText("");
                    }
                };
        for (MaterialRequestQuantityRow row : table.getItems()) {
            row.requestedProductQuantityProperty()
                    .addListener((obs, oldValue, newValue) -> refreshNextEnablement.run());
        }
        refreshNextEnablement.run();
        nextButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    Optional<String> validation = validateStep1Quantities(table.getItems());
                    if (validation.isPresent()) {
                        errorLabel.setText(validation.get());
                        nextButton.setDisable(true);
                        event.consume();
                    } else {
                        errorLabel.setText("");
                    }
                });

        VBox content = new VBox(8, table, errorLabel);
        content.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(900);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != nextType) {
            return Step1Outcome.cancelled();
        }
        return Step1Outcome.next(List.copyOf(table.getItems()));
    }

    public static Step2Outcome showStep2(
            MaterialRequirementView requirement,
            List<MaterialRequirementLineProductionStockView> productionStock,
            List<String> sourceSummaryLines,
            boolean canMutate,
            boolean alreadySaved,
            Function<List<MaterialRequestDraftLineRow>, Optional<MaterialRequirementView>> saveDraft,
            Function<List<MaterialRequestDraftLineRow>, Optional<String>> submit) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(productionStock, "productionStock");
        Objects.requireNonNull(sourceSummaryLines, "sourceSummaryLines");
        Objects.requireNonNull(saveDraft, "saveDraft");
        Objects.requireNonNull(submit, "submit");

        MaterialRequirementView[] current = {requirement};
        boolean[] explicitlySaved = {alreadySaved};
        List<MaterialRequirementLineProductionStockView> stockSnapshot = List.copyOf(productionStock);
        List<MaterialRequestDraftLineRow> lineRows = toLineRows(requirement, stockSnapshot);

        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle(STEP2_TITLE);

        Label statusLabel =
                new Label(
                        requirement.status() == MaterialRequirementStatusView.DRAFT
                                ? STEP2_STATUS_DRAFT
                                : "Статус: Отправлен");
        Label sourcesHeader = new Label("ИСТОЧНИКИ");
        sourcesHeader.getStyleClass().add("tmp-section-title");
        VBox sourcesBox = new VBox(2);
        for (String line : sourceSummaryLines) {
            sourcesBox.getChildren().add(new Label(line));
        }

        Label materialsHeader = new Label("МАТЕРИАЛЫ");
        materialsHeader.getStyleClass().add("tmp-section-title");

        TableView<MaterialRequestDraftLineRow> table = new TableView<>();
        table.setItems(FXCollections.observableArrayList(lineRows));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Нет данных"));
        table.setPrefHeight(280);
        boolean editable =
                canMutate && requirement.status() == MaterialRequirementStatusView.DRAFT;
        table.setEditable(editable);

        table.getColumns()
                .addAll(
                        draftStringColumn(COLUMN_ARTICLE, 100, MaterialRequestDraftLineRow::materialCode),
                        draftStringColumn(
                                COLUMN_NAME, 180, MaterialRequestDraftLineRow::materialName),
                        draftStringColumn(COLUMN_COLOR, 100, MaterialRequestDraftLineRow::color),
                        draftStringColumn(COLUMN_UOM, 60, MaterialRequestDraftLineRow::unitOfMeasure),
                        ProductionTableCells.tooltipStringColumn(
                                COLUMN_REQUIRED,
                                160,
                                ProductionTableCells.REQUIRED_QUANTITY_MIN_WIDTH,
                                MaterialRequestDraftLineRow::requiredQuantityLabel),
                        draftStringColumn(
                                COLUMN_ON_PRODUCTION,
                                120,
                                MaterialRequestDraftLineRow::onProductionAvailableLabel),
                        draftRequestQuantityColumn(editable));

        Label stockHintLabel = new Label(STOCK_INFO_HINT);
        stockHintLabel.setWrapText(true);
        stockHintLabel.getStyleClass().add("tmp-text-muted");

        Label messageLabel = new Label();
        messageLabel.setWrapText(true);

        ButtonType submitType = new ButtonType(SUBMIT_BUTTON, ButtonBar.ButtonData.APPLY);
        ButtonType saveType = new ButtonType(SAVE_LINE_BUTTON, ButtonBar.ButtonData.OTHER);
        ButtonType closeType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        if (editable) {
            dialog.getDialogPane().getButtonTypes().addAll(submitType, saveType, closeType);
        } else {
            dialog.getDialogPane().getButtonTypes().add(closeType);
        }

        if (editable) {
            Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
            Button submitButton = (Button) dialog.getDialogPane().lookupButton(submitType);
            saveButton.addEventFilter(
                    javafx.event.ActionEvent.ACTION,
                    event -> {
                        event.consume();
                        Optional<String> validation =
                                validateAllRequestQuantities(table.getItems());
                        if (validation.isPresent()) {
                            showStep2Error(messageLabel, validation.get());
                            return;
                        }
                        saveButton.setDisable(true);
                        submitButton.setDisable(true);
                        try {
                            Optional<MaterialRequirementView> saved =
                                    saveDraft.apply(List.copyOf(table.getItems()));
                            if (saved.isPresent()) {
                                current[0] = saved.get();
                                explicitlySaved[0] = true;
                                table.setItems(
                                        FXCollections.observableArrayList(
                                                toLineRows(saved.get(), stockSnapshot)));
                                showStep2Success(messageLabel, DRAFT_SAVED_MESSAGE);
                            }
                        } finally {
                            saveButton.setDisable(false);
                            submitButton.setDisable(false);
                        }
                    });
            submitButton.addEventFilter(
                    javafx.event.ActionEvent.ACTION,
                    event -> {
                        event.consume();
                        Optional<String> validation =
                                validateAllRequestQuantities(table.getItems());
                        if (validation.isPresent()) {
                            showStep2Error(messageLabel, validation.get());
                            return;
                        }
                        saveButton.setDisable(true);
                        submitButton.setDisable(true);
                        try {
                            Optional<String> failure =
                                    submit.apply(List.copyOf(table.getItems()));
                            if (failure.isEmpty()) {
                                explicitlySaved[0] = true;
                                dialog.setResult(submitType);
                                dialog.close();
                            } else {
                                showStep2Error(messageLabel, failure.get());
                            }
                        } finally {
                            saveButton.setDisable(false);
                            submitButton.setDisable(false);
                        }
                    });
        }

        VBox content =
                new VBox(
                        8,
                        statusLabel,
                        sourcesHeader,
                        sourcesBox,
                        materialsHeader,
                        table,
                        stockHintLabel,
                        messageLabel);
        content.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(1040);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == submitType) {
            return Step2Outcome.submitted(explicitlySaved[0]);
        }
        return Step2Outcome.closed(explicitlySaved[0]);
    }

    private static void showStep2Error(Label messageLabel, String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().remove("tmp-message-success");
        if (!messageLabel.getStyleClass().contains("tmp-message-error")) {
            messageLabel.getStyleClass().add("tmp-message-error");
        }
    }

    private static void showStep2Success(Label messageLabel, String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().remove("tmp-message-error");
        if (!messageLabel.getStyleClass().contains("tmp-message-success")) {
            messageLabel.getStyleClass().add("tmp-message-success");
        }
    }

    public static DraftListOutcome showDraftList(
            List<DraftListRow> drafts,
            Function<DraftListRow, Optional<String>> deleteDraft) {
        Objects.requireNonNull(drafts, "drafts");
        Objects.requireNonNull(deleteDraft, "deleteDraft");
        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle(DRAFT_LIST_TITLE);

        TableView<DraftListRow> table = new TableView<>();
        table.setPlaceholder(new Label("Нет данных"));
        table.setItems(FXCollections.observableArrayList(drafts));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320);
        table.getColumns()
                .addAll(
                        draftListStringColumn(COLUMN_DRAFT_CREATED, 140, DraftListRow::createdLabel),
                        draftListStringColumn(COLUMN_DRAFT_ORDERS, 140, DraftListRow::ordersLabel),
                        draftListStringColumn(
                                COLUMN_DRAFT_POSITIONS, 120, DraftListRow::positionsLabel),
                        draftListStringColumn(
                                COLUMN_DRAFT_PRODUCTS, 90, DraftListRow::productsLabel));

        ButtonType deleteType = new ButtonType(DELETE_DRAFT_BUTTON, ButtonBar.ButtonData.LEFT);
        ButtonType cancelType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(deleteType, cancelType);
        Button deleteButton = (Button) dialog.getDialogPane().lookupButton(deleteType);
        deleteButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteButton.addEventFilter(
                javafx.event.ActionEvent.ACTION,
                event -> {
                    event.consume();
                    DraftListRow selected = table.getSelectionModel().getSelectedItem();
                    if (selected == null) {
                        return;
                    }
                    if (!confirmDeleteDraft(selected)) {
                        return;
                    }
                    Optional<String> failure = deleteDraft.apply(selected);
                    if (failure.isPresent()) {
                        showValidation(failure.get());
                        return;
                    }
                    table.getItems().remove(selected);
                });

        Label empty =
                new Label(
                        drafts.isEmpty()
                                ? "Нет сохранённых черновиков запросов материалов."
                                : "Откройте черновик двойным щелчком.");
        empty.setWrapText(true);
        UUID[] openId = {null};
        table.setOnMouseClicked(
                event -> {
                    if (event.getClickCount() == 2
                            && table.getSelectionModel().getSelectedItem() != null) {
                        openId[0] = table.getSelectionModel().getSelectedItem().requirementId();
                        dialog.setResult(cancelType);
                        dialog.close();
                    }
                });

        VBox content = new VBox(8, empty, table);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(640);

        dialog.showAndWait();
        if (openId[0] != null) {
            return DraftListOutcome.open(openId[0]);
        }
        return DraftListOutcome.cancelled();
    }

    public static boolean confirmDeleteDraft(DraftListRow row) {
        Objects.requireNonNull(row, "row");
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle(DELETE_DRAFT_CONFIRM_TITLE);
        alert.setHeaderText(DELETE_DRAFT_CONFIRM_HEADER);
        Label content =
                new Label(
                        "Заказ: "
                                + row.ordersLabel()
                                + "\nПозиции: "
                                + row.positionsLabel()
                                + "\nИзделия: "
                                + row.productsLabel()
                                + "\n\nУдалить выбранный черновик?");
        content.setWrapText(true);
        content.setMaxWidth(480);
        alert.getDialogPane().setContent(content);
        ButtonType delete = new ButtonType(DELETE_DRAFT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(delete, cancel);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == delete;
    }

    private static TableColumn<DraftListRow, String> draftListStringColumn(
            String title, double pref, Function<DraftListRow, String> value) {
        TableColumn<DraftListRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        return column;
    }

    public static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showValidation(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        TmpTheme.apply(alert.getDialogPane());
        alert.setTitle(STEP1_TITLE);
        alert.setHeaderText(null);
        Label content = new Label(message == null ? "" : message);
        content.setWrapText(true);
        content.setMaxWidth(560);
        content.setMinHeight(Label.USE_PREF_SIZE);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setPrefWidth(620);
        alert.setResizable(true);
        alert.showAndWait();
    }

    private static TableColumn<MaterialRequestQuantityRow, String> stringColumn(
            String title, double pref, Function<MaterialRequestQuantityRow, String> value) {
        TableColumn<MaterialRequestQuantityRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        return column;
    }

    private static TableColumn<MaterialRequestQuantityRow, String> quantityColumn() {
        TableColumn<MaterialRequestQuantityRow, String> column = new TableColumn<>("Количество");
        column.setPrefWidth(110);
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final TextField field = new TextField();
                            private boolean syncing;

                            {
                                field.textProperty()
                                        .addListener(
                                                (obs, oldValue, newValue) -> {
                                                    if (syncing
                                                            || getTableRow() == null
                                                            || getTableRow().getItem() == null) {
                                                        return;
                                                    }
                                                    applyFlexibleQuantityText(
                                                            newValue, getTableRow().getItem());
                                                });
                                field.focusedProperty()
                                        .addListener(
                                                (obs, was, focused) -> {
                                                    if (!focused
                                                            && getTableRow() != null
                                                            && getTableRow().getItem() != null) {
                                                        MaterialRequestQuantityRow row =
                                                                getTableRow().getItem();
                                                        if (!row.standardMode()) {
                                                            syncing = true;
                                                            try {
                                                                field.setText(
                                                                        Long.toString(
                                                                                Math.max(
                                                                                        0L,
                                                                                        row
                                                                                                .requestedProductQuantity())));
                                                            } finally {
                                                                syncing = false;
                                                            }
                                                        }
                                                    }
                                                });
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    setText(null);
                                    return;
                                }
                                MaterialRequestQuantityRow row = getTableRow().getItem();
                                if (row.standardMode()) {
                                    setGraphic(null);
                                    setText(Long.toString(row.requestableProductQuantity()));
                                } else {
                                    syncing = true;
                                    try {
                                        long shown =
                                                row.requestedProductQuantity() < 0L
                                                        ? 0L
                                                        : row.requestedProductQuantity();
                                        field.setText(Long.toString(shown));
                                    } finally {
                                        syncing = false;
                                    }
                                    setText(null);
                                    setGraphic(field);
                                }
                            }
                        });
        return column;
    }

    private static void applyFlexibleQuantityText(String text, MaterialRequestQuantityRow row) {
        if (row == null || row.standardMode()) {
            return;
        }
        if (text == null || text.isBlank()) {
            row.setRequestedProductQuantity(0L);
            return;
        }
        try {
            BigDecimal parsed = DecimalQuantityParser.parseRequired(text, "Количество");
            if (parsed.signum() < 0
                    || (parsed.scale() > 0 && parsed.stripTrailingZeros().scale() > 0)) {
                row.setRequestedProductQuantity(0L);
                return;
            }
            row.setRequestedProductQuantity(parsed.longValueExact());
        } catch (RuntimeException ex) {
            row.setRequestedProductQuantity(0L);
        }
    }

    private static TableColumn<MaterialRequestDraftLineRow, String> draftStringColumn(
            String title, double pref, Function<MaterialRequestDraftLineRow, String> value) {
        TableColumn<MaterialRequestDraftLineRow, String> column = new TableColumn<>(title);
        column.setPrefWidth(pref);
        column.setEditable(false);
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        return column;
    }

    private static TableColumn<MaterialRequestDraftLineRow, String> draftRequestQuantityColumn(
            boolean editable) {
        TableColumn<MaterialRequestDraftLineRow, String> column = new TableColumn<>(COLUMN_TO_REQUEST);
        column.setPrefWidth(110);
        column.setCellFactory(
                col ->
                        new TableCell<>() {
                            private final TextField field = new TextField();

                            {
                                field.focusedProperty()
                                        .addListener(
                                                (obs, was, focused) -> {
                                                    if (!focused
                                                            && getTableRow() != null
                                                            && getTableRow().getItem() != null) {
                                                        commitRequestQuantity(
                                                                field, getTableRow().getItem());
                                                    }
                                                });
                            }

                            @Override
                            protected void updateItem(String item, boolean empty) {
                                super.updateItem(item, empty);
                                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                                    setGraphic(null);
                                    setText(null);
                                    return;
                                }
                                MaterialRequestDraftLineRow row = getTableRow().getItem();
                                if (!editable) {
                                    setGraphic(null);
                                    setText(DecimalUiFormat.format(row.quantity()));
                                } else {
                                    field.setText(DecimalUiFormat.format(row.quantity()));
                                    setText(null);
                                    setGraphic(field);
                                }
                            }
                        });
        return column;
    }

    private static void commitRequestQuantity(TextField field, MaterialRequestDraftLineRow row) {
        try {
            BigDecimal value =
                    DecimalQuantityParser.parseNonNegative(field.getText(), COLUMN_TO_REQUEST);
            Optional<String> validation = validateRequestQuantity(value, row.requiredQuantity());
            if (validation.isPresent()) {
                field.setText(DecimalUiFormat.format(row.quantity()));
                return;
            }
            row.setQuantity(value);
            field.setText(DecimalUiFormat.format(value));
        } catch (RuntimeException ex) {
            field.setText(DecimalUiFormat.format(row.quantity()));
        }
    }

    /** Test seam: STANDARD quantity is always requestable, never user-edited. */
    public static long resolvedProductQuantityForPrepare(MaterialRequestQuantityRow row) {
        Objects.requireNonNull(row, "row");
        if (row.quantityMode() == QuantityModeView.STANDARD) {
            return row.requestableProductQuantity();
        }
        return row.requestedProductQuantity();
    }

    public static void bindBusy(Button button, boolean busy, Consumer<Boolean> setBusy) {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(setBusy, "setBusy");
        setBusy.accept(busy);
        button.setDisable(busy);
    }

    public static HBox toolbarSpacer() {
        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }
}
