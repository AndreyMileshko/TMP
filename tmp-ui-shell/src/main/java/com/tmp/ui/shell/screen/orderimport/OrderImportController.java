package com.tmp.ui.shell.screen.orderimport;

import com.tmp.order.api.imports.OrderImportProblem;
import com.tmp.order.api.imports.OrderImportProblemSeverity;
import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Order import FXML controller. FileChooser → ViewModel preview/confirm. No Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderImportController implements ViewModelAware<OrderImportViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Button selectFileButton;

    @FXML
    private TextField fileNameField;

    @FXML
    private Label previewOrderNumberLabel;

    @FXML
    private Label previewPositionCountLabel;

    @FXML
    private Label previewProductQuantityLabel;

    @FXML
    private Label previewSpecificationLineCountLabel;

    @FXML
    private TableView<OrderImportProblem> errorsTable;

    @FXML
    private TableColumn<OrderImportProblem, String> errorLevelColumn;

    @FXML
    private TableColumn<OrderImportProblem, String> errorFieldColumn;

    @FXML
    private TableColumn<OrderImportProblem, String> errorRowColumn;

    @FXML
    private TableColumn<OrderImportProblem, String> errorMessageColumn;

    @FXML
    private TableView<OrderImportProblem> warningsTable;

    @FXML
    private TableColumn<OrderImportProblem, String> warningLevelColumn;

    @FXML
    private TableColumn<OrderImportProblem, String> warningFieldColumn;

    @FXML
    private TableColumn<OrderImportProblem, String> warningRowColumn;

    @FXML
    private TableColumn<OrderImportProblem, String> warningMessageColumn;

    @FXML
    private Button validateButton;

    @FXML
    private Button importButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Label loadingLabel;

    @FXML
    private Label statusLabel;

    @FXML
    private Label successLabel;

    @FXML
    private Label errorLabel;

    private OrderImportViewModel viewModel;

    @Override
    public void setViewModel(OrderImportViewModel viewModel) {
        this.viewModel = viewModel;
        titleLabel.textProperty().bind(viewModel.titleProperty());
        fileNameField.textProperty().bind(viewModel.fileNameProperty());
        fileNameField.setEditable(false);

        previewOrderNumberLabel.textProperty().bind(viewModel.previewOrderNumberProperty());
        previewPositionCountLabel.textProperty().bind(viewModel.previewPositionCountProperty());
        previewProductQuantityLabel.textProperty().bind(viewModel.previewProductQuantityProperty());
        previewSpecificationLineCountLabel
                .textProperty()
                .bind(viewModel.previewSpecificationLineCountProperty());

        bindProblemTable(
                errorsTable,
                errorLevelColumn,
                errorFieldColumn,
                errorRowColumn,
                errorMessageColumn,
                viewModel.errors());
        bindProblemTable(
                warningsTable,
                warningLevelColumn,
                warningFieldColumn,
                warningRowColumn,
                warningMessageColumn,
                viewModel.warnings());

        selectFileButton.disableProperty().bind(viewModel.canSelectFileProperty().not());
        validateButton.disableProperty().bind(viewModel.canValidateProperty().not());
        importButton.disableProperty().bind(viewModel.canImportProperty().not());

        selectFileButton.setOnAction(e -> chooseFile());
        validateButton.setOnAction(e -> viewModel.validatePreview());
        importButton.setOnAction(e -> viewModel.confirmImport());
        cancelButton.setOnAction(e -> viewModel.cancel());

        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        successLabel.textProperty().bind(viewModel.successMessageProperty());
        successLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.successMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.successMessageProperty()));
        successLabel.managedProperty().bind(successLabel.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        viewModel.open();
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбор файла выгрузки");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Файлы выгрузки STXT", "*.stxt", "*.txt"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*"));
        Window window = selectFileButton.getScene() == null
                ? null
                : selectFileButton.getScene().getWindow();
        File chosen = chooser.showOpenDialog(window);
        if (chosen != null) {
            viewModel.selectFile(chosen.toPath());
        }
    }

    private static void bindProblemTable(
            TableView<OrderImportProblem> table,
            TableColumn<OrderImportProblem, String> levelColumn,
            TableColumn<OrderImportProblem, String> fieldColumn,
            TableColumn<OrderImportProblem, String> rowColumn,
            TableColumn<OrderImportProblem, String> messageColumn,
            javafx.collections.ObservableList<OrderImportProblem> items) {
        levelColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(levelLabel(cell.getValue().severity())));
        fieldColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        nullToEmpty(cell.getValue().fieldName())));
        rowColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(rowLabel(cell.getValue())));
        messageColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().message()));
        table.setItems(items);
    }

    private static String levelLabel(OrderImportProblemSeverity severity) {
        if (severity == OrderImportProblemSeverity.WARNING) {
            return "Предупреждение";
        }
        return "Ошибка";
    }

    private static String rowLabel(OrderImportProblem problem) {
        if (problem.location() != null && !problem.location().isBlank()) {
            return problem.location();
        }
        StringBuilder builder = new StringBuilder();
        if (problem.positionIndex() != null) {
            builder.append("позиция ").append(problem.positionIndex());
        }
        if (problem.specificationLineIndex() != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("строка ").append(problem.specificationLineIndex());
        }
        return builder.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
