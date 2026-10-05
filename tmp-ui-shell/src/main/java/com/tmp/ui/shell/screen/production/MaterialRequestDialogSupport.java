package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.MaterialRequirementDraftSummaryView;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
    public static final String OPEN_DRAFT_BUTTON = "Открыть";
    public static final String DRAFT_LIST_TITLE = "Черновики запросов материалов";

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

    public static List<MaterialRequestDraftLineRow> toLineRows(MaterialRequirementView requirement) {
        Objects.requireNonNull(requirement, "requirement");
        List<MaterialRequestDraftLineRow> rows = new ArrayList<>();
        for (MaterialRequirementLineView line : requirement.lines()) {
            rows.add(
                    new MaterialRequestDraftLineRow(
                            line.lineId(),
                            line.materialCode(),
                            line.materialName(),
                            line.color(),
                            line.unitOfMeasure(),
                            line.quantity()));
        }
        return rows;
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

    public static String draftListCaption(MaterialRequirementDraftSummaryView draft) {
        Objects.requireNonNull(draft, "draft");
        String when =
                DRAFT_WHEN.format(draft.createdAt().atZone(ZoneId.systemDefault()).toLocalDateTime());
        return when
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

    public static Step2Action showStep2(
            MaterialRequirementView requirement,
            List<String> sourceSummaryLines,
            boolean canMutate,
            Function<MaterialRequestDraftLineRow, Optional<MaterialRequirementView>> saveLine,
            Function<MaterialRequirementView, Optional<String>> submit) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(sourceSummaryLines, "sourceSummaryLines");
        Objects.requireNonNull(saveLine, "saveLine");
        Objects.requireNonNull(submit, "submit");

        MaterialRequirementView[] current = {requirement};
        List<MaterialRequestDraftLineRow> lineRows = toLineRows(requirement);

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
        table.setEditable(canMutate && requirement.status() == MaterialRequirementStatusView.DRAFT);

        table.getColumns()
                .addAll(
                        draftStringColumn("Артикул", 100, MaterialRequestDraftLineRow::materialCode),
                        draftStringColumn(
                                "Наименование", 180, MaterialRequestDraftLineRow::materialName),
                        draftStringColumn("Цвет", 100, MaterialRequestDraftLineRow::color),
                        draftStringColumn("Ед.", 60, MaterialRequestDraftLineRow::unitOfMeasure),
                        draftQuantityColumn(table.isEditable()));

        Label messageLabel = new Label();
        messageLabel.setWrapText(true);

        ButtonType submitType = new ButtonType(SUBMIT_BUTTON, ButtonBar.ButtonData.APPLY);
        ButtonType saveType = new ButtonType(SAVE_LINE_BUTTON, ButtonBar.ButtonData.OTHER);
        ButtonType closeType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        if (canMutate && requirement.status() == MaterialRequirementStatusView.DRAFT) {
            dialog.getDialogPane().getButtonTypes().addAll(submitType, saveType, closeType);
        } else {
            dialog.getDialogPane().getButtonTypes().add(closeType);
        }

        if (canMutate && requirement.status() == MaterialRequirementStatusView.DRAFT) {
            Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveType);
            Button submitButton = (Button) dialog.getDialogPane().lookupButton(submitType);
            saveButton.addEventFilter(
                    javafx.event.ActionEvent.ACTION,
                    event -> {
                        event.consume();
                        MaterialRequestDraftLineRow selected = table.getSelectionModel().getSelectedItem();
                        if (selected == null) {
                            messageLabel.setText("Выберите строку материала для сохранения.");
                            messageLabel.getStyleClass().remove("tmp-message-success");
                            if (!messageLabel.getStyleClass().contains("tmp-message-error")) {
                                messageLabel.getStyleClass().add("tmp-message-error");
                            }
                            return;
                        }
                        saveButton.setDisable(true);
                        submitButton.setDisable(true);
                        try {
                            Optional<MaterialRequirementView> saved = saveLine.apply(selected);
                            if (saved.isPresent()) {
                                current[0] = saved.get();
                                table.setItems(
                                        FXCollections.observableArrayList(toLineRows(saved.get())));
                                messageLabel.setText("Количество сохранено.");
                                messageLabel.getStyleClass().remove("tmp-message-error");
                                if (!messageLabel.getStyleClass().contains("tmp-message-success")) {
                                    messageLabel.getStyleClass().add("tmp-message-success");
                                }
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
                        saveButton.setDisable(true);
                        submitButton.setDisable(true);
                        try {
                            Optional<String> failure = submit.apply(current[0]);
                            if (failure.isEmpty()) {
                                dialog.setResult(submitType);
                                dialog.close();
                            } else {
                                messageLabel.setText(failure.get());
                                messageLabel.getStyleClass().remove("tmp-message-success");
                                if (!messageLabel.getStyleClass().contains("tmp-message-error")) {
                                    messageLabel.getStyleClass().add("tmp-message-error");
                                }
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
                        messageLabel);
        content.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(920);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == submitType) {
            return Step2Action.SUBMITTED;
        }
        return Step2Action.CLOSED;
    }

    public static DraftListOutcome showDraftList(List<MaterialRequirementDraftSummaryView> drafts) {
        Objects.requireNonNull(drafts, "drafts");
        Dialog<ButtonType> dialog = new Dialog<>();
        TmpTheme.apply(dialog.getDialogPane());
        dialog.setTitle(DRAFT_LIST_TITLE);

        TableView<MaterialRequirementDraftSummaryView> table = new TableView<>();
        table.setPlaceholder(new Label("Нет данных"));
        table.setItems(FXCollections.observableArrayList(drafts));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(280);
        TableColumn<MaterialRequirementDraftSummaryView, String> caption =
                new TableColumn<>("Черновик");
        caption.setCellValueFactory(
                c -> new SimpleStringProperty(draftListCaption(c.getValue())));
        table.getColumns().add(caption);

        ButtonType openType = new ButtonType(OPEN_DRAFT_BUTTON, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(openType, cancelType);
        Button openButton = (Button) dialog.getDialogPane().lookupButton(openType);
        openButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        Label empty =
                new Label(
                        drafts.isEmpty()
                                ? "Нет сохранённых черновиков запросов материалов."
                                : "");
        VBox content = new VBox(8, empty, table);
        content.setPadding(new Insets(8));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(480);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != openType) {
            return DraftListOutcome.cancelled();
        }
        MaterialRequirementDraftSummaryView selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return DraftListOutcome.cancelled();
        }
        return DraftListOutcome.open(selected.requirementId());
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
        column.setCellValueFactory(c -> new SimpleStringProperty(value.apply(c.getValue())));
        return column;
    }

    private static TableColumn<MaterialRequestDraftLineRow, String> draftQuantityColumn(
            boolean editable) {
        TableColumn<MaterialRequestDraftLineRow, String> column = new TableColumn<>("Количество");
        column.setPrefWidth(120);
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
                                                        commitMaterialQuantity(
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

    private static void commitMaterialQuantity(TextField field, MaterialRequestDraftLineRow row) {
        try {
            BigDecimal value = DecimalQuantityParser.parsePositive(field.getText(), "Количество");
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
