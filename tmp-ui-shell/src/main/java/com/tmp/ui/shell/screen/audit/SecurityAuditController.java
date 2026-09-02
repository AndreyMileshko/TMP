package com.tmp.ui.shell.screen.audit;

import com.tmp.security.api.AuditEventSummary;
import com.tmp.ui.shell.navigation.ViewModelAware;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Security audit FXML controller. Read-only; no Spring imports.
 */
public final class SecurityAuditController implements ViewModelAware<SecurityAuditViewModel> {

    @FXML
    private VBox root;

    @FXML
    private TableView<AuditEventSummary> auditTable;

    @FXML
    private TableColumn<AuditEventSummary, String> occurredAtColumn;

    @FXML
    private TableColumn<AuditEventSummary, String> actorColumn;

    @FXML
    private TableColumn<AuditEventSummary, String> operationColumn;

    @FXML
    private TableColumn<AuditEventSummary, String> targetColumn;

    @FXML
    private TableColumn<AuditEventSummary, String> descriptionColumn;

    @FXML
    private TableColumn<AuditEventSummary, String> resultColumn;

    @FXML
    private DatePicker fromDatePicker;

    @FXML
    private DatePicker toDatePicker;

    @FXML
    private TextField operationFilterField;

    @FXML
    private Button applyFilterButton;

    @FXML
    private Button resetFilterButton;

    @FXML
    private Button previousPageButton;

    @FXML
    private Button nextPageButton;

    @FXML
    private Label pageLabel;

    @FXML
    private Label errorLabel;

    private SecurityAuditViewModel viewModel;

    @Override
    public void setViewModel(SecurityAuditViewModel viewModel) {
        this.viewModel = viewModel;
        loadScreenStylesheet();

        occurredAtColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        SecurityAuditPresentation.formatOccurredAt(cell.getValue().occurredAt())));
        actorColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().actorLogin()));
        operationColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        SecurityAuditPresentation.formatOperation(cell.getValue().operation())));
        operationColumn.setCellFactory(column -> new OperationTableCell());
        targetColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(SecurityAuditPresentation.formatTarget(
                        cell.getValue().targetType(), cell.getValue().targetIdentifier())));
        descriptionColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().safeDescription()));
        descriptionColumn.setCellFactory(column -> new EllipsisTableCell());
        resultColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().result()));
        resultColumn.setCellFactory(column -> new ResultBadgeTableCell());

        auditTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        resultColumn.setResizable(false);
        auditTable.setItems(viewModel.events());
        auditTable.setPlaceholder(createEmptyState());

        fromDatePicker.valueProperty().bindBidirectional(viewModel.fromDateProperty());
        toDatePicker.valueProperty().bindBidirectional(viewModel.toDateProperty());
        operationFilterField.textProperty().bindBidirectional(viewModel.operationFilterProperty());
        applyFilterButton.setOnAction(e -> viewModel.applyFilters());
        resetFilterButton.setOnAction(e -> viewModel.resetFilters());
        previousPageButton.setOnAction(e -> viewModel.previousPage());
        nextPageButton.setOnAction(e -> viewModel.nextPage());
        previousPageButton.disableProperty().bind(viewModel.canGoPreviousProperty().not());
        nextPageButton.disableProperty().bind(viewModel.canGoNextProperty().not());

        pageLabel.textProperty().bind(Bindings.createStringBinding(
                viewModel::pageLabelText,
                viewModel.pageIndexProperty(),
                viewModel.totalCountProperty()));

        viewModel.emptyStateTitleProperty().addListener((obs, oldValue, newValue) -> auditTable.setPlaceholder(createEmptyState()));
        viewModel.emptyStateHintProperty().addListener((obs, oldValue, newValue) -> auditTable.setPlaceholder(createEmptyState()));
        viewModel.filtersActiveProperty().addListener((obs, oldValue, newValue) -> auditTable.setPlaceholder(createEmptyState()));

        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    String message = viewModel.errorMessageProperty().get();
                    return message != null && !message.isBlank();
                },
                viewModel.errorMessageProperty()));
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        viewModel.refresh();
    }

    private void loadScreenStylesheet() {
        var resource = getClass().getResource("SecurityAuditScreen.css");
        if (resource != null && root != null) {
            String url = resource.toExternalForm();
            if (!root.getStylesheets().contains(url)) {
                root.getStylesheets().add(url);
            }
        }
    }

    private VBox createEmptyState() {
        Label title = new Label(viewModel == null ? "" : viewModel.emptyStateTitleProperty().get());
        title.getStyleClass().add("tmp-empty-state-title");
        Label hint = new Label(viewModel == null ? "" : viewModel.emptyStateHintProperty().get());
        hint.getStyleClass().add("tmp-empty-state-hint");
        hint.setWrapText(true);
        hint.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> hint.getText() != null && !hint.getText().isBlank()));
        hint.managedProperty().bind(hint.visibleProperty());
        VBox emptyState = new VBox(8, title, hint);
        emptyState.getStyleClass().add("tmp-empty-state");
        emptyState.setAlignment(Pos.CENTER);
        return emptyState;
    }

    private static final class OperationTableCell extends TableCell<AuditEventSummary, String> {
        @Override
        protected void updateItem(String displayText, boolean empty) {
            super.updateItem(displayText, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            AuditEventSummary event = getTableRow().getItem();
            setText(displayText);
            if (event.operation() != null && !event.operation().equals(displayText)) {
                setTooltip(SecurityAuditPresentation.fullTextTooltip(event.operation()));
            } else {
                setTooltip(null);
            }
        }
    }

    private static final class EllipsisTableCell extends TableCell<AuditEventSummary, String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setTooltip(null);
                return;
            }
            setText(item);
            setTooltip(SecurityAuditPresentation.fullTextTooltip(item));
        }
    }

    private static final class ResultBadgeTableCell extends TableCell<AuditEventSummary, String> {
        @Override
        protected void updateItem(String result, boolean empty) {
            super.updateItem(result, empty);
            if (empty || result == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            setText(null);
            setAlignment(Pos.CENTER_LEFT);
            setGraphic(SecurityAuditPresentation.centeredResultBadge(result));
        }
    }
}
