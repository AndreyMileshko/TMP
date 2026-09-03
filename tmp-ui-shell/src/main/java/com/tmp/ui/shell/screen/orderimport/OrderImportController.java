package com.tmp.ui.shell.screen.orderimport;

import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Order import FXML controller. FileChooser → auto preview → confirm dialog. No Spring imports.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class OrderImportController implements ViewModelAware<OrderImportViewModel> {

    @FXML
    private Label titleLabel;

    @FXML
    private Label subtitleLabel;

    @FXML
    private javafx.scene.layout.VBox workingPane;

    @FXML
    private javafx.scene.layout.VBox emptyFilePane;

    @FXML
    private Button selectFileButton;

    @FXML
    private Label formatHintLabel;

    @FXML
    private javafx.scene.layout.VBox selectedFilePane;

    @FXML
    private Label selectedFileNameLabel;

    @FXML
    private Button selectOtherFileButton;

    @FXML
    private Label loadingLabel;

    @FXML
    private javafx.scene.layout.VBox previewPane;

    @FXML
    private Label previewStatusLabel;

    @FXML
    private Label previewOrdersLabel;

    @FXML
    private Label previewOrderNumbersLabel;

    @FXML
    private Label previewPositionCountLabel;

    @FXML
    private Label previewProductQuantityLabel;

    @FXML
    private Label previewSpecificationLineCountLabel;

    @FXML
    private Label previewErrorCountLabel;

    @FXML
    private Label previewWarningCountLabel;

    @FXML
    private Label problemsEmptyLabel;

    @FXML
    private javafx.scene.layout.VBox problemsPane;

    @FXML
    private TableView<OrderImportProblemRow> problemsTable;

    @FXML
    private TableColumn<OrderImportProblemRow, String> problemTypeColumn;

    @FXML
    private TableColumn<OrderImportProblemRow, String> problemWhereColumn;

    @FXML
    private TableColumn<OrderImportProblemRow, String> problemMessageColumn;

    @FXML
    private Button importButton;

    @FXML
    private Label statusLabel;

    @FXML
    private Label errorLabel;

    @FXML
    private javafx.scene.layout.VBox successPane;

    @FXML
    private Label successTitleLabel;

    @FXML
    private Label successMessageLabel;

    @FXML
    private Button openImportedOrderButton;

    @FXML
    private Button goToOrderListButton;

    @FXML
    private Button importAnotherButton;

    private OrderImportViewModel viewModel;
    private Supplier<File> fileChooserOpener = this::showOpenDialog;
    private Supplier<Boolean> confirmDialogOpener;

    @Override
    public void setViewModel(OrderImportViewModel viewModel) {
        this.viewModel = viewModel;
        this.confirmDialogOpener =
                () ->
                        OrderImportDialogs.confirmImport(
                                        ownerWindow(),
                                        viewModel.confirmationTitle(),
                                        viewModel.confirmationBody(),
                                        viewModel.confirmationConfirmLabel())
                                .orElse(false);

        titleLabel.textProperty().bind(viewModel.titleProperty());
        subtitleLabel.textProperty().bind(viewModel.subtitleProperty());

        emptyFilePane.visibleProperty().bind(viewModel.fileSelectedProperty().not()
                .and(viewModel.successVisibleProperty().not()));
        emptyFilePane.managedProperty().bind(emptyFilePane.visibleProperty());

        selectedFilePane.visibleProperty().bind(viewModel.fileSelectedProperty()
                .and(viewModel.successVisibleProperty().not()));
        selectedFilePane.managedProperty().bind(selectedFilePane.visibleProperty());
        selectedFileNameLabel.textProperty().bind(viewModel.fileNameProperty());
        Tooltip pathTooltip = new Tooltip();
        pathTooltip.textProperty().bind(viewModel.filePathTooltipProperty());
        selectedFileNameLabel.setTooltip(pathTooltip);

        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(loadingLabel.visibleProperty());

        previewPane.visibleProperty().bind(viewModel.previewVisibleProperty()
                .and(viewModel.successVisibleProperty().not()));
        previewPane.managedProperty().bind(previewPane.visibleProperty());

        previewStatusLabel.textProperty().bind(viewModel.previewStatusTextProperty());
        previewOrdersLabel.textProperty().bind(viewModel.previewOrdersTextProperty());
        previewOrderNumbersLabel.textProperty().bind(viewModel.previewOrderNumbersTextProperty());
        previewOrderNumbersLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String text = viewModel.previewOrderNumbersTextProperty().get();
                    return text != null && !text.isBlank();
                },
                viewModel.previewOrderNumbersTextProperty()));
        previewOrderNumbersLabel.managedProperty().bind(previewOrderNumbersLabel.visibleProperty());

        previewPositionCountLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    String value = viewModel.previewPositionCountProperty().get();
                    return value == null || value.isBlank() ? "" : "Позиций: " + value;
                },
                viewModel.previewPositionCountProperty()));
        previewProductQuantityLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    String value = viewModel.previewProductQuantityProperty().get();
                    return value == null || value.isBlank() ? "" : "Изделий: " + value;
                },
                viewModel.previewProductQuantityProperty()));
        previewSpecificationLineCountLabel.textProperty().bind(Bindings.createStringBinding(
                () -> {
                    String value = viewModel.previewSpecificationLineCountProperty().get();
                    return value == null || value.isBlank()
                            ? ""
                            : "Строк спецификации: " + value;
                },
                viewModel.previewSpecificationLineCountProperty()));

        previewErrorCountLabel.textProperty().bind(viewModel.previewErrorCountTextProperty());
        previewWarningCountLabel.textProperty().bind(viewModel.previewWarningCountTextProperty());

        problemsEmptyLabel.textProperty().bind(viewModel.problemsEmptyTextProperty());
        problemsEmptyLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String text = viewModel.problemsEmptyTextProperty().get();
                    return text != null && !text.isBlank();
                },
                viewModel.problemsEmptyTextProperty()));
        problemsEmptyLabel.managedProperty().bind(problemsEmptyLabel.visibleProperty());

        problemsPane.visibleProperty().bind(viewModel.problemsTableVisibleProperty());
        problemsPane.managedProperty().bind(problemsPane.visibleProperty());

        problemsTable.setItems(viewModel.problems());
        problemsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        problemTypeColumn.setMaxWidth(180);
        problemWhereColumn.setMaxWidth(320);
        problemMessageColumn.setMaxWidth(Double.MAX_VALUE);
        problemTypeColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().typeLabel()));
        problemWhereColumn.setCellValueFactory(
                cell ->
                        new javafx.beans.property.SimpleStringProperty(
                                cell.getValue().whereLabel()));
        problemMessageColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().message()));
        problemsTable
                .widthProperty()
                .addListener(
                        (obs, oldWidth, newWidth) -> {
                            double width = newWidth.doubleValue();
                            if (width <= 0) {
                                return;
                            }
                            double compact = 140 + 220;
                            problemMessageColumn.setPrefWidth(Math.max(200, width - compact - 24));
                        });

        selectFileButton.disableProperty().bind(viewModel.canSelectFileProperty().not());
        selectOtherFileButton.disableProperty().bind(viewModel.canSelectFileProperty().not());
        importButton.textProperty().bind(viewModel.importButtonTextProperty());
        importButton.disableProperty().bind(viewModel.canImportProperty().not()
                .or(viewModel.loadingProperty()));

        selectFileButton.setOnAction(e -> chooseFile());
        selectOtherFileButton.setOnAction(e -> chooseFile());
        importButton.setOnAction(e -> onImportClicked());

        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        workingPane.visibleProperty().bind(viewModel.workingVisibleProperty());
        workingPane.managedProperty().bind(workingPane.visibleProperty());

        successPane.visibleProperty().bind(viewModel.successVisibleProperty());
        successPane.managedProperty().bind(successPane.visibleProperty());
        successTitleLabel.textProperty().bind(viewModel.successTitleProperty());
        successMessageLabel.textProperty().bind(viewModel.successMessageProperty());

        openImportedOrderButton.visibleProperty().bind(viewModel.canOpenImportedOrderProperty());
        openImportedOrderButton.managedProperty().bind(openImportedOrderButton.visibleProperty());
        openImportedOrderButton.setOnAction(e -> viewModel.openImportedOrder());
        goToOrderListButton.setOnAction(e -> viewModel.goToOrderList());
        importAnotherButton.setOnAction(e -> viewModel.importAnother());

        viewModel.open();
    }

    /** Test hook: replaces native FileChooser. Cancel is simulated by returning {@code null}. */
    void setFileChooserOpenerForTest(Supplier<File> fileChooserOpener) {
        this.fileChooserOpener = Objects.requireNonNull(fileChooserOpener, "fileChooserOpener");
    }

    /** Test hook: replaces native confirmation dialog. */
    void setConfirmDialogOpenerForTest(Supplier<Boolean> confirmDialogOpener) {
        this.confirmDialogOpener =
                Objects.requireNonNull(confirmDialogOpener, "confirmDialogOpener");
    }

    void chooseFileForTest() {
        chooseFile();
    }

    void importForTest() {
        onImportClicked();
    }

    private void onImportClicked() {
        if (!viewModel.canImportProperty().get()) {
            return;
        }
        Boolean confirmed = confirmDialogOpener.get();
        if (Boolean.TRUE.equals(confirmed)) {
            viewModel.confirmImport();
        }
    }

    private void chooseFile() {
        File chosen = fileChooserOpener.get();
        if (chosen != null) {
            viewModel.selectFile(chosen.toPath());
        }
    }

    private File showOpenDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выбор файла выгрузки");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Файлы выгрузки STXT", "*.stxt", "*.txt"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*"));
        Window window = ownerWindow();
        return chooser.showOpenDialog(window);
    }

    private Window ownerWindow() {
        if (selectFileButton.getScene() != null) {
            return selectFileButton.getScene().getWindow();
        }
        if (importButton.getScene() != null) {
            return importButton.getScene().getWindow();
        }
        return null;
    }
}
