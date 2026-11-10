package com.tmp.ui.shell.screen.reference;

import com.tmp.reference.api.ColorDto;
import com.tmp.ui.shell.navigation.ViewModelAware;
import com.tmp.ui.shell.screen.reference.ReferenceColorsViewModel.StatusFilter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.fxml.FXML;

/** Reference Data / Colors FXML controller (COL-030A: «Цвета TMP» + matching switch). */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_FIELD"},
        justification = "JavaFX Controller retains ViewModel for FXML wiring")
public final class ReferenceColorsController implements ViewModelAware<ReferenceColorsViewModel> {

    private ReferenceColorsViewModel viewModel;
    private boolean suppressMatchingToggle;
    private BooleanSupplier matchingConfirmation;

    @FXML private VBox root;
    @FXML private ToggleButton colorsTmpTabButton;
    @FXML private CheckBox automaticMatchingCheck;
    @FXML private TextField searchField;
    @FXML private ComboBox<StatusFilter> statusFilterCombo;
    @FXML private Button addColorButton;
    @FXML private Button archiveColorButton;
    @FXML private Button restoreColorButton;
    @FXML private Label loadingLabel;
    @FXML private Label errorLabel;
    @FXML private Label statusLabel;
    @FXML private TableView<ColorDto> colorsTable;
    @FXML private TableColumn<ColorDto, String> colorCodeColumn;
    @FXML private TableColumn<ColorDto, String> colorStatusColumn;

    @Override
    public void setViewModel(ReferenceColorsViewModel viewModel) {
        this.viewModel = viewModel;
        bind();
        viewModel.onScreenOpened();
    }

    private void bind() {
        colorsTmpTabButton.setSelected(true);
        colorsTmpTabButton.setOnAction(e -> colorsTmpTabButton.setSelected(true));

        // Loading overlay must never participate in layout (UI-COL-001 / warehouse jitter pattern).
        loadingLabel.setManaged(false);
        loadingLabel.visibleProperty().bind(viewModel.loadingProperty());
        errorLabel.textProperty().bind(viewModel.errorMessageProperty());
        errorLabel.visibleProperty().bind(viewModel.errorMessageProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        statusLabel.textProperty().bind(viewModel.statusMessageProperty());

        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        statusFilterCombo.getItems().setAll(StatusFilter.values());
        statusFilterCombo.valueProperty().bindBidirectional(viewModel.statusFilterProperty());

        colorsTable.setItems(viewModel.colors());
        colorsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        colorsTable.setPlaceholder(createEmptyState());
        colorCodeColumn.setCellValueFactory(
                c -> new javafx.beans.property.SimpleStringProperty(c.getValue().code()));
        colorStatusColumn.setCellValueFactory(
                c ->
                        new javafx.beans.property.SimpleStringProperty(
                                c.getValue().active() ? "Активен" : "Архив"));
        colorStatusColumn.setCellFactory(column -> new StatusBadgeCell());
        colorsTable
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, o, n) -> viewModel.selectedColorProperty().set(n));
        viewModel
                .selectedColorProperty()
                .addListener(
                        (obs, o, n) -> {
                            if (n == null) {
                                colorsTable.getSelectionModel().clearSelection();
                            } else if (n != colorsTable.getSelectionModel().getSelectedItem()) {
                                colorsTable.getSelectionModel().select(n);
                            }
                            updateActionAvailability();
                        });

        addColorButton
                .disableProperty()
                .bind(
                        viewModel
                                .canEditProperty()
                                .not()
                                .or(viewModel.commandInFlightProperty()));
        addColorButton.visibleProperty().bind(viewModel.canEditProperty());
        addColorButton.managedProperty().bind(viewModel.canEditProperty());
        addColorButton.setOnAction(e -> onAddColor());

        archiveColorButton.setOnAction(e -> onArchiveColor());
        restoreColorButton.setOnAction(e -> onRestoreColor());
        archiveColorButton.visibleProperty().bind(viewModel.canEditProperty());
        archiveColorButton.managedProperty().bind(viewModel.canEditProperty());
        restoreColorButton.visibleProperty().bind(viewModel.canEditProperty());
        restoreColorButton.managedProperty().bind(viewModel.canEditProperty());

        colorsTable.setRowFactory(
                table -> {
                    TableRow<ColorDto> row = new TableRow<>();
                    ContextMenu menu = new ContextMenu();
                    MenuItem archiveItem = new MenuItem("Архивировать");
                    archiveItem.setOnAction(e -> onArchiveColor());
                    MenuItem restoreItem = new MenuItem("Восстановить");
                    restoreItem.setOnAction(e -> onRestoreColor());
                    menu.getItems().addAll(archiveItem, new SeparatorMenuItem(), restoreItem);
                    row.contextMenuProperty()
                            .bind(
                                    Bindings.when(
                                                    row.emptyProperty()
                                                            .or(viewModel.canEditProperty().not()))
                                            .then((ContextMenu) null)
                                            .otherwise(menu));
                    row.itemProperty()
                            .addListener(
                                    (obs, o, n) -> {
                                        boolean active = n != null && n.active();
                                        archiveItem.setDisable(!active);
                                        restoreItem.setDisable(n == null || active);
                                    });
                    return row;
                });

        automaticMatchingCheck
                .disableProperty()
                .bind(
                        viewModel
                                .canEditSettingProperty()
                                .not()
                                .or(viewModel.commandInFlightProperty()));
        restoreMatchingCheckboxFromConfirmed();
        viewModel
                .automaticMatchingEnabledProperty()
                .addListener((obs, o, n) -> restoreMatchingCheckboxFromConfirmed());
        automaticMatchingCheck
                .selectedProperty()
                .addListener(
                        (obs, was, isNow) -> {
                            if (suppressMatchingToggle || was == null || Objects.equals(was, isNow)) {
                                return;
                            }
                            boolean intended = Boolean.TRUE.equals(isNow);
                            // Persisted setting is source of truth: revert visual immediately.
                            restoreMatchingCheckboxFromConfirmed();
                            onMatchingToggle(intended);
                        });
        viewModel.commandInFlightProperty().addListener((obs, o, n) -> updateActionAvailability());
        viewModel.canEditProperty().addListener((obs, o, n) -> updateActionAvailability());
        updateActionAvailability();
    }

    private void updateActionAvailability() {
        ColorDto selected = viewModel.selectedColorProperty().get();
        boolean edit = viewModel.canEditProperty().get();
        boolean busy = viewModel.commandInFlightProperty().get();
        boolean hasSelection = selected != null;
        archiveColorButton.setDisable(!edit || busy || !hasSelection || !selected.active());
        restoreColorButton.setDisable(!edit || busy || !hasSelection || selected.active());
    }

    private void onAddColor() {
        Window owner = window();
        ReferenceColorsDialogs.showCreateColorDialog(owner, viewModel::createColor);
        updateActionAvailability();
    }

    private void onArchiveColor() {
        ColorDto selected = viewModel.selectedColorProperty().get();
        if (selected == null || !selected.active() || !viewModel.canEditProperty().get()) {
            return;
        }
        long impact;
        try {
            impact = viewModel.previewArchiveImpact(selected);
        } catch (RuntimeException ex) {
            viewModel.errorMessageProperty().set(ReferenceColorsMessages.mapError(ex));
            return;
        }
        if (!ReferenceColorsDialogs.showArchiveConfirmation(window(), selected.code(), impact)) {
            return;
        }
        viewModel.archiveSelected(selected);
        updateActionAvailability();
    }

    private void onRestoreColor() {
        ColorDto selected = viewModel.selectedColorProperty().get();
        if (selected == null || selected.active() || !viewModel.canEditProperty().get()) {
            return;
        }
        if (!ReferenceColorsDialogs.showRestoreConfirmation(window(), selected.code())) {
            return;
        }
        viewModel.restoreSelected(selected);
        updateActionAvailability();
    }

    private void onMatchingToggle(boolean enable) {
        if (!viewModel.canEditSettingProperty().get()) {
            restoreMatchingCheckboxFromConfirmed();
            return;
        }
        if (!confirmMatchingChange(enable)) {
            restoreMatchingCheckboxFromConfirmed();
            return;
        }
        if (!viewModel.applyAutomaticMatchingEnabled(enable)) {
            restoreMatchingCheckboxFromConfirmed();
        }
    }

    private boolean confirmMatchingChange(boolean enable) {
        if (matchingConfirmation != null) {
            return matchingConfirmation.getAsBoolean();
        }
        return ReferenceColorsDialogs.showMatchingSettingConfirmation(window(), enable);
    }

    /**
     * Restores the checkbox from the last confirmed / persisted ViewModel value. Always sets the
     * control explicitly so Cancel works when the property value is unchanged (UI-COL-002).
     */
    void restoreMatchingCheckboxFromConfirmed() {
        boolean confirmed = viewModel.automaticMatchingEnabledProperty().get();
        if (automaticMatchingCheck.isSelected() == confirmed) {
            return;
        }
        suppressMatchingToggle = true;
        automaticMatchingCheck.setSelected(confirmed);
        suppressMatchingToggle = false;
    }

    /** Test seam: replaces the confirmation dialog. */
    void setMatchingConfirmation(BooleanSupplier matchingConfirmation) {
        this.matchingConfirmation = matchingConfirmation;
    }

    CheckBox automaticMatchingCheckForTests() {
        return automaticMatchingCheck;
    }

    Label loadingLabelForTests() {
        return loadingLabel;
    }

    TableView<ColorDto> colorsTableForTests() {
        return colorsTable;
    }

    private Window window() {
        return root.getScene() == null ? null : root.getScene().getWindow();
    }

    private static VBox createEmptyState() {
        Label title = new Label("Нет цветов для отображения");
        title.getStyleClass().add("tmp-empty-state-title");
        Label hint = new Label("Измените поиск или фильтр, либо добавьте новый цвет TMP.");
        hint.getStyleClass().add("tmp-empty-state-hint");
        hint.setWrapText(true);
        VBox empty = new VBox(8, title, hint);
        empty.getStyleClass().add("tmp-empty-state");
        empty.setAlignment(Pos.CENTER);
        return empty;
    }

    private static final class StatusBadgeCell extends TableCell<ColorDto, String> {
        private final Label badge = new Label();
        private final HBox box = new HBox(badge);

        private StatusBadgeCell() {
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
            if ("Активен".equals(item)) {
                badge.getStyleClass().add("tmp-badge-success");
            } else {
                badge.getStyleClass().add("tmp-badge-neutral");
            }
            setGraphic(box);
        }
    }
}
