package com.tmp.ui.shell.screen.audit;

import com.tmp.security.api.AuditEventSummary;
import com.tmp.ui.shell.navigation.ViewModelAware;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings(value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"}, justification = "JavaFX ViewModel/Controller intentionally expose observable properties and retain ViewModel for FXML wiring")
/**
 * Security audit FXML controller. Read-only; no Spring imports.
 */
public final class SecurityAuditController implements ViewModelAware<SecurityAuditViewModel> {

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
    private TextField operationFilterField;

    @FXML
    private Button applyFilterButton;

    @FXML
    private Button previousPageButton;

    @FXML
    private Button nextPageButton;

    @FXML
    private Label pageLabel;

    @FXML
    private Label errorLabel;

    @Override
    public void setViewModel(SecurityAuditViewModel viewModel) {
        occurredAtColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().occurredAt().toString()));
        actorColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().actorLogin()));
        operationColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().operation()));
        targetColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().targetType() + ":" + String.valueOf(cell.getValue().targetIdentifier())));
        descriptionColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().safeDescription()));
        resultColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(cell.getValue().result()));

        auditTable.setItems(viewModel.events());
        operationFilterField.textProperty().bindBidirectional(viewModel.operationFilterProperty());
        applyFilterButton.setOnAction(e -> viewModel.applyFilters());
        previousPageButton.setOnAction(e -> viewModel.previousPage());
        nextPageButton.setOnAction(e -> viewModel.nextPage());
        pageLabel.textProperty().bind(Bindings.createStringBinding(
                () -> "РЎС‚СЂР°РЅРёС†Р° " + (viewModel.pageIndexProperty().get() + 1)
                        + " / СЃРѕР±С‹С‚РёР№: " + viewModel.totalCountProperty().get(),
                viewModel.pageIndexProperty(),
                viewModel.totalCountProperty()));

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
}
