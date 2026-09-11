package com.tmp.ui.shell.screen.warehouse;

import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.screen.warehouse.WarehouseSettingsViewModel.ResponsibilityRow;
import com.tmp.ui.shell.screen.warehouse.WarehouseSettingsViewModel.SettingsSection;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/** Warehouse Settings FXML controller. */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class WarehouseSettingsController
        implements ViewModelAware<WarehouseSettingsViewModel> {

    private WarehouseSettingsViewModel viewModel;

    @FXML private ToggleButton warehousesTabButton;
    @FXML private ToggleButton cellsTabButton;
    @FXML private ToggleButton responsibilitiesTabButton;
    @FXML private Button backToWorkspaceButton;
    @FXML private VBox warehousesPane;
    @FXML private VBox cellsPane;
    @FXML private VBox responsibilitiesPane;
    @FXML private Label loadingLabel;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;

    @FXML private TableView<WarehouseView> warehousesTable;
    @FXML private TableColumn<WarehouseView, String> warehouseCodeColumn;
    @FXML private TableColumn<WarehouseView, String> warehouseNameColumn;
    @FXML private TableColumn<WarehouseView, String> warehouseActiveColumn;
    @FXML private TextField newWarehouseCodeField;
    @FXML private TextField newWarehouseNameField;
    @FXML private Button createWarehouseButton;
    @FXML private TextField editWarehouseCodeField;
    @FXML private TextField editWarehouseNameField;
    @FXML private CheckBox editWarehouseActiveCheck;
    @FXML private Button saveWarehouseButton;
    @FXML private Button cancelWarehouseButton;

    @FXML private ComboBox<WarehouseView> cellsWarehouseCombo;
    @FXML private TableView<StorageCellView> cellsTable;
    @FXML private TableColumn<StorageCellView, String> cellCodeColumn;
    @FXML private TableColumn<StorageCellView, String> cellActiveColumn;
    @FXML private TextField newCellCodeField;
    @FXML private Button createCellButton;
    @FXML private TextField editCellCodeField;
    @FXML private CheckBox editCellActiveCheck;
    @FXML private Button saveCellButton;
    @FXML private Button cancelCellButton;

    @FXML private ComboBox<WarehouseView> responsibilityWarehouseCombo;
    @FXML private TableView<ResponsibilityRow> responsibilitiesTable;
    @FXML private TableColumn<ResponsibilityRow, Boolean> responsibilityAssignedColumn;
    @FXML private TableColumn<ResponsibilityRow, String> responsibilityLoginColumn;
    @FXML private TableColumn<ResponsibilityRow, String> responsibilityNameColumn;

    @Override
    public void setViewModel(WarehouseSettingsViewModel viewModel) {
        this.viewModel = viewModel;
        bind();
        viewModel.onScreenOpened();
    }

    private void bind() {
        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        loadingLabel.managedProperty().bind(viewModel.loadingProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());
        backToWorkspaceButton.setOnAction(e -> viewModel.navigateBackToWorkspace());

        warehousesTable.setItems(viewModel.warehouses());
        warehousesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        warehouseCodeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().code()));
        warehouseNameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        warehouseActiveColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().active() ? "Активен" : "Неактивен"));
        warehousesTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> viewModel.selectedWarehouseProperty().set(n));

        newWarehouseCodeField.textProperty().bindBidirectional(viewModel.newWarehouseCodeProperty());
        newWarehouseNameField.textProperty().bindBidirectional(viewModel.newWarehouseNameProperty());
        createWarehouseButton
                .disableProperty()
                .bind(
                        viewModel
                                .canCreateWarehouseProperty()
                                .not()
                                .or(viewModel.commandInFlightProperty()));
        createWarehouseButton.setOnAction(e -> viewModel.createWarehouse());

        editWarehouseCodeField
                .textProperty()
                .bindBidirectional(viewModel.editWarehouseCodeProperty());
        editWarehouseNameField
                .textProperty()
                .bindBidirectional(viewModel.editWarehouseNameProperty());
        editWarehouseActiveCheck
                .selectedProperty()
                .bindBidirectional(viewModel.editWarehouseActiveProperty());
        saveWarehouseButton
                .disableProperty()
                .bind(
                        viewModel
                                .canUpdateWarehouseProperty()
                                .not()
                                .or(viewModel.commandInFlightProperty()));
        saveWarehouseButton.setOnAction(e -> viewModel.saveSelectedWarehouse());
        cancelWarehouseButton
                .disableProperty()
                .bind(viewModel.commandInFlightProperty());
        cancelWarehouseButton.setOnAction(e -> viewModel.cancelWarehouseEdit());

        cellsWarehouseCombo.setItems(viewModel.warehouses());
        cellsWarehouseCombo.setConverter(warehouseConverter());
        cellsWarehouseCombo.valueProperty().bindBidirectional(viewModel.cellsWarehouseProperty());
        cellsTable.setItems(viewModel.cells());
        cellsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label cellsPlaceholder = new Label();
        cellsPlaceholder.textProperty().bind(viewModel.statusMessageProperty());
        cellsPlaceholder.getStyleClass().add("tmp-empty-state-hint");
        cellsPlaceholder.setWrapText(true);
        cellsTable.setPlaceholder(cellsPlaceholder);
        cellCodeColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().code()));
        cellActiveColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().active() ? "Активна" : "Неактивна"));
        cellsTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> viewModel.selectedCellProperty().set(n));
        newCellCodeField.textProperty().bindBidirectional(viewModel.newCellCodeProperty());
        createCellButton
                .disableProperty()
                .bind(viewModel.canCreateCellProperty().not().or(viewModel.commandInFlightProperty()));
        createCellButton.setOnAction(e -> viewModel.createCell());
        editCellCodeField.textProperty().bindBidirectional(viewModel.editCellCodeProperty());
        editCellActiveCheck.selectedProperty().bindBidirectional(viewModel.editCellActiveProperty());
        saveCellButton
                .disableProperty()
                .bind(viewModel.canUpdateCellProperty().not().or(viewModel.commandInFlightProperty()));
        saveCellButton.setOnAction(e -> viewModel.saveSelectedCell());
        cancelCellButton.disableProperty().bind(viewModel.commandInFlightProperty());
        cancelCellButton.setOnAction(e -> viewModel.cancelCellEdit());

        responsibilityWarehouseCombo.setItems(viewModel.warehouses());
        responsibilityWarehouseCombo.setConverter(warehouseConverter());
        responsibilityWarehouseCombo
                .valueProperty()
                .bindBidirectional(viewModel.responsibilityWarehouseProperty());
        responsibilitiesTable.setItems(viewModel.responsibilities());
        responsibilitiesTable.setEditable(true);
        responsibilitiesTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        responsibilityLoginColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().login()));
        responsibilityNameColumn.setCellValueFactory(
                c -> new SimpleStringProperty(c.getValue().displayName()));
        responsibilityAssignedColumn.setEditable(true);
        responsibilityAssignedColumn.setCellValueFactory(
                c -> new SimpleBooleanProperty(c.getValue().assigned()));
        responsibilityAssignedColumn.setCellFactory(
                CheckBoxTableCell.forTableColumn(
                        index -> {
                            ResponsibilityRow row = responsibilitiesTable.getItems().get(index);
                            SimpleBooleanProperty prop = new SimpleBooleanProperty(row.assigned());
                            prop.addListener(
                                    (obs, was, now) -> {
                                        if (!viewModel.canManageResponsibilityProperty().get()
                                                || viewModel.commandInFlightProperty().get()
                                                || java.util.Objects.equals(now, was)) {
                                            return;
                                        }
                                        viewModel.setResponsibilityAssigned(row, Boolean.TRUE.equals(now));
                                    });
                            return prop;
                        }));

        warehousesTabButton.setOnAction(e -> viewModel.selectSection(SettingsSection.WAREHOUSES));
        cellsTabButton.setOnAction(e -> viewModel.selectSection(SettingsSection.CELLS));
        responsibilitiesTabButton.setOnAction(
                e -> viewModel.selectSection(SettingsSection.RESPONSIBILITIES));
        viewModel.sectionProperty().addListener((obs, o, n) -> applySection(n));
        applySection(viewModel.sectionProperty().get());
    }

    private void applySection(SettingsSection section) {
        boolean warehouses = section == SettingsSection.WAREHOUSES;
        boolean cells = section == SettingsSection.CELLS;
        boolean responsibilities = section == SettingsSection.RESPONSIBILITIES;
        warehousesPane.setVisible(warehouses);
        warehousesPane.setManaged(warehouses);
        cellsPane.setVisible(cells);
        cellsPane.setManaged(cells);
        responsibilitiesPane.setVisible(responsibilities);
        responsibilitiesPane.setManaged(responsibilities);
        warehousesTabButton.setSelected(warehouses);
        cellsTabButton.setSelected(cells);
        responsibilitiesTabButton.setSelected(responsibilities);
    }

    private static StringConverter<WarehouseView> warehouseConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(WarehouseView object) {
                return object == null ? "" : object.code() + " — " + object.name();
            }

            @Override
            public WarehouseView fromString(String string) {
                return null;
            }
        };
    }
}
