package com.tmp.ui.shell.screen.reference;

import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.ui.shell.screen.reference.So7MappingsTabViewModel.StatusFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.fxml.FXML;

/** SO7 mappings tab controller (COL-030B / TMP-SPEC-015 §14.2). */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class So7MappingsTabController {

    private So7MappingsTabViewModel viewModel;

    @FXML private VBox root;
    @FXML private TextField searchField;
    @FXML private ComboBox<StatusFilter> statusFilterCombo;
    @FXML private Button addMappingButton;
    @FXML private Button editPairButton;
    @FXML private Button toggleActiveButton;
    @FXML private Button deleteMappingButton;
    @FXML private Label loadingLabel;
    @FXML private TableView<So7ColorMappingDto> mappingsTable;
    @FXML private TableColumn<So7ColorMappingDto, String> sourceTextColumn;
    @FXML private TableColumn<So7ColorMappingDto, String> insideColorColumn;
    @FXML private TableColumn<So7ColorMappingDto, String> outsideColorColumn;
    @FXML private TableColumn<So7ColorMappingDto, String> displayColorColumn;
    @FXML private TableColumn<So7ColorMappingDto, String> activeStatusColumn;

    public void setViewModel(So7MappingsTabViewModel viewModel) {
        this.viewModel = viewModel;
        bind();
    }

    public void onActivated() {
        if (viewModel != null) {
            viewModel.onTabActivated();
        }
    }

    private void bind() {
        // Loading overlay must never participate in layout (UI-COL-001).
        loadingLabel.setManaged(false);
        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        statusFilterCombo.getItems().setAll(StatusFilter.values());
        statusFilterCombo.valueProperty().bindBidirectional(viewModel.statusFilterProperty());

        mappingsTable.setItems(viewModel.mappings());
        mappingsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        mappingsTable.setPlaceholder(createEmptyState());

        sourceTextColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().sourceText()));
        insideColorColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                So7MappingsTabViewModel.formatSideCode(
                                        c.getValue().insideCode(), c.getValue().insideActive())));
        outsideColorColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                So7MappingsTabViewModel.formatSideCode(
                                        c.getValue().outsideCode(), c.getValue().outsideActive())));
        displayColorColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().displayColor()));
        activeStatusColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                So7MappingsTabViewModel.formatActiveLabel(c.getValue().active())));
        activeStatusColumn.setCellFactory(column -> new ActiveBadgeCell());

        mappingsTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> viewModel.selectedMappingProperty().set(n));
        viewModel
                .selectedMappingProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (n == null) {
                                mappingsTable.getSelectionModel().clearSelection();
                            } else if (n != mappingsTable.getSelectionModel().getSelectedItem()) {
                                mappingsTable.getSelectionModel().select(n);
                            }
                            updateActionAvailability();
                        });

        addMappingButton
                .disableProperty()
                .bind(
                        viewModel
                                .canEditMappingsProperty()
                                .not()
                                .or(viewModel.commandInFlightProperty()));
        addMappingButton.visibleProperty().bind(viewModel.canEditMappingsProperty());
        addMappingButton.managedProperty().bind(viewModel.canEditMappingsProperty());
        addMappingButton.setOnAction(e -> onAddMapping());

        editPairButton.setOnAction(e -> onEditPair());
        toggleActiveButton.setOnAction(e -> onToggleActive());
        deleteMappingButton.setOnAction(e -> onDeleteMapping());
        editPairButton.visibleProperty().bind(viewModel.canEditMappingsProperty());
        editPairButton.managedProperty().bind(viewModel.canEditMappingsProperty());
        toggleActiveButton.visibleProperty().bind(viewModel.canEditMappingsProperty());
        toggleActiveButton.managedProperty().bind(viewModel.canEditMappingsProperty());
        deleteMappingButton.visibleProperty().bind(viewModel.canEditMappingsProperty());
        deleteMappingButton.managedProperty().bind(viewModel.canEditMappingsProperty());

        mappingsTable.setRowFactory(
                table -> {
                    TableRow<So7ColorMappingDto> row = new TableRow<>();
                    ContextMenu menu = new ContextMenu();
                    MenuItem editItem = new MenuItem("Изменить пару");
                    editItem.setOnAction(e -> onEditPair());
                    MenuItem toggleItem = new MenuItem("Отключить");
                    toggleItem.setOnAction(e -> onToggleActive());
                    MenuItem deleteItem = new MenuItem("Удалить");
                    deleteItem.setOnAction(e -> onDeleteMapping());
                    menu.getItems()
                            .addAll(
                                    editItem,
                                    toggleItem,
                                    new SeparatorMenuItem(),
                                    deleteItem);
                    row.contextMenuProperty()
                            .bind(
                                    Bindings.when(
                                                    row.emptyProperty()
                                                            .or(
                                                                    viewModel
                                                                            .canEditMappingsProperty()
                                                                            .not()))
                                            .then((ContextMenu) null)
                                            .otherwise(menu));
                    row.itemProperty()
                            .addListener(
                                    (obs, o, n) -> {
                                        if (n == null) {
                                            return;
                                        }
                                        toggleItem.setText(n.active() ? "Отключить" : "Включить");
                                    });
                    return row;
                });

        viewModel.commandInFlightProperty().addListener((obs, o, n) -> updateActionAvailability());
        viewModel.canEditMappingsProperty().addListener((obs, o, n) -> updateActionAvailability());
        updateActionAvailability();
    }

    private void updateActionAvailability() {
        So7ColorMappingDto selected = viewModel.selectedMappingProperty().get();
        boolean edit = viewModel.canEditMappingsProperty().get();
        boolean busy = viewModel.commandInFlightProperty().get();
        boolean hasSelection = selected != null;
        editPairButton.setDisable(!edit || busy || !hasSelection);
        deleteMappingButton.setDisable(!edit || busy || !hasSelection);
        toggleActiveButton.setDisable(!edit || busy || !hasSelection);
        if (hasSelection) {
            toggleActiveButton.setText(selected.active() ? "Отключить" : "Включить");
        } else {
            toggleActiveButton.setText("Отключить");
        }
    }

    private void onAddMapping() {
        if (!viewModel.canEditMappingsProperty().get()) {
            return;
        }
        List<ColorDto> colors;
        try {
            colors = viewModel.listActiveColorsForDialog();
        } catch (RuntimeException ex) {
            viewModel.errorMessageProperty().set(ReferenceColorsMessages.mapMappingError(ex));
            return;
        }
        So7MappingDialogSupport.showCreateMappingDialog(
                window(), colors, viewModel::createMapping);
        updateActionAvailability();
    }

    private void onEditPair() {
        So7ColorMappingDto selected = viewModel.selectedMappingProperty().get();
        if (selected == null || !viewModel.canEditMappingsProperty().get()) {
            return;
        }
        List<ColorDto> colors;
        try {
            colors = viewModel.listActiveColorsForDialog();
        } catch (RuntimeException ex) {
            viewModel.errorMessageProperty().set(ReferenceColorsMessages.mapMappingError(ex));
            return;
        }
        So7MappingDialogSupport.showEditPairDialog(
                window(),
                selected,
                colors,
                (insideId, outsideId) ->
                        viewModel.updateMappingPair(selected, insideId, outsideId));
        updateActionAvailability();
    }

    private void onToggleActive() {
        So7ColorMappingDto selected = viewModel.selectedMappingProperty().get();
        if (selected == null || !viewModel.canEditMappingsProperty().get()) {
            return;
        }
        viewModel.setMappingActive(selected, !selected.active());
        updateActionAvailability();
    }

    private void onDeleteMapping() {
        So7ColorMappingDto selected = viewModel.selectedMappingProperty().get();
        if (selected == null || !viewModel.canEditMappingsProperty().get()) {
            return;
        }
        if (!So7MappingDialogSupport.showDeleteConfirmation(window(), selected.sourceText())) {
            return;
        }
        viewModel.deleteMapping(selected);
        updateActionAvailability();
    }

    Label loadingLabelForTests() {
        return loadingLabel;
    }

    TableView<So7ColorMappingDto> mappingsTableForTests() {
        return mappingsTable;
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static VBox createEmptyState() {
        Label title = new Label("Нет соответствий СО7 для отображения");
        title.getStyleClass().add("tmp-empty-state-title");
        Label hint =
                new Label("Измените поиск или фильтр, либо добавьте новое соответствие.");
        hint.getStyleClass().add("tmp-empty-state-hint");
        hint.setWrapText(true);
        VBox empty = new VBox(8, title, hint);
        empty.getStyleClass().add("tmp-empty-state");
        empty.setAlignment(Pos.CENTER);
        return empty;
    }

    private static final class ActiveBadgeCell extends TableCell<So7ColorMappingDto, String> {
        private final Label badge = new Label();
        private final HBox box = new HBox(badge);

        private ActiveBadgeCell() {
            badge.getStyleClass().add("tmp-badge");
            box.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            badge.setText(item);
            badge.getStyleClass().setAll("tmp-badge");
            if ("Активно".equals(item)) {
                badge.getStyleClass().add("tmp-badge-success");
            } else {
                badge.getStyleClass().add("tmp-badge-neutral");
            }
            setGraphic(box);
        }
    }
}
