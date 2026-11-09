package com.tmp.ui.shell.screen.reference;

import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.api.ColorMatchingSettingApi.ColorMatchingSettingDto;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Reference Data / Colors screen ViewModel (TMP-SPEC-015 §14.1 / §14.3 / §14.4, COL-030A).
 *
 * <p>Uses only Reference Data public APIs. No JDBC. SO7 mappings tab is out of scope (COL-030B).
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class ReferenceColorsViewModel {

    public enum StatusFilter {
        ACTIVE("Активные", ColorDto.ActiveFilter.ACTIVE),
        ARCHIVED("Архив", ColorDto.ActiveFilter.ARCHIVED),
        ALL("Все", ColorDto.ActiveFilter.ALL);

        private final String label;
        private final ColorDto.ActiveFilter apiFilter;

        StatusFilter(String label, ColorDto.ActiveFilter apiFilter) {
            this.label = label;
            this.apiFilter = apiFilter;
        }

        public String label() {
            return label;
        }

        ColorDto.ActiveFilter apiFilter() {
            return apiFilter;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ColorCatalogQueryApi queryApi;
    private final ColorCatalogCommandApi commandApi;
    private final ColorMatchingSettingApi settingApi;
    private final AuthorizationService authorizationService;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> uiExecutor;

    private final ObservableList<ColorDto> colors = FXCollections.observableArrayList();
    private final ObjectProperty<ColorDto> selectedColor = new SimpleObjectProperty<>();
    private final ObjectProperty<StatusFilter> statusFilter =
            new SimpleObjectProperty<>(StatusFilter.ACTIVE);
    private final StringProperty searchText = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty inlineFormError = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty commandInFlight = new SimpleBooleanProperty(false);
    private final BooleanProperty canView = new SimpleBooleanProperty(false);
    private final BooleanProperty canEdit = new SimpleBooleanProperty(false);
    private final BooleanProperty canEditSetting = new SimpleBooleanProperty(false);
    private final BooleanProperty automaticMatchingEnabled = new SimpleBooleanProperty(false);

    private long matchingSettingVersion;
    private long loadGeneration;

    public ReferenceColorsViewModel(
            ColorCatalogQueryApi queryApi,
            ColorCatalogCommandApi commandApi,
            ColorMatchingSettingApi settingApi,
            AuthorizationService authorizationService) {
        this(
                queryApi,
                commandApi,
                settingApi,
                authorizationService,
                Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread = new Thread(runnable, "reference-colors");
                            thread.setDaemon(true);
                            return thread;
                        }),
                Platform::runLater);
    }

    ReferenceColorsViewModel(
            ColorCatalogQueryApi queryApi,
            ColorCatalogCommandApi commandApi,
            ColorMatchingSettingApi settingApi,
            AuthorizationService authorizationService,
            Executor backgroundExecutor,
            Consumer<Runnable> uiExecutor) {
        this.queryApi = Objects.requireNonNull(queryApi, "queryApi");
        this.commandApi = Objects.requireNonNull(commandApi, "commandApi");
        this.settingApi = Objects.requireNonNull(settingApi, "settingApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        statusFilter.addListener((obs, o, n) -> reloadColors());
        searchText.addListener((obs, o, n) -> reloadColors());
        refreshPermissions();
    }

    public void onScreenOpened() {
        refreshPermissions();
        if (!canView.get()) {
            errorMessage.set("Недостаточно прав для просмотра справочника цветов.");
            colors.clear();
            selectedColor.set(null);
            return;
        }
        errorMessage.set("");
        statusMessage.set("");
        inlineFormError.set("");
        reloadColors();
        reloadMatchingSetting();
    }

    public void refreshPermissions() {
        canView.set(has(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION));
        canEdit.set(has(UiShellScreens.REFERENCE_COLOR_EDIT_PERMISSION));
        canEditSetting.set(has(UiShellScreens.REFERENCE_COLOR_SETTING_EDIT_PERMISSION));
    }

    public void reloadColors() {
        if (!canView.get()) {
            return;
        }
        long generation = ++loadGeneration;
        loading.set(true);
        errorMessage.set("");
        StatusFilter filter = statusFilter.get() == null ? StatusFilter.ACTIVE : statusFilter.get();
        String search = searchText.get();
        backgroundExecutor.execute(
                () -> {
                    try {
                        List<ColorDto> loaded = queryApi.listColors(filter.apiFilter(), search);
                        uiExecutor.accept(
                                () -> {
                                    if (generation != loadGeneration) {
                                        return;
                                    }
                                    colors.setAll(loaded);
                                    restoreSelectionAfterReload();
                                    loading.set(false);
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> {
                                    if (generation != loadGeneration) {
                                        return;
                                    }
                                    loading.set(false);
                                    colors.clear();
                                    errorMessage.set(ReferenceColorsMessages.mapError(ex));
                                });
                    }
                });
    }

    public void reloadMatchingSetting() {
        if (!canView.get()) {
            return;
        }
        backgroundExecutor.execute(
                () -> {
                    try {
                        ColorMatchingSettingDto dto = settingApi.getAutomaticMatchingSetting();
                        uiExecutor.accept(
                                () -> {
                                    matchingSettingVersion = dto.version();
                                    automaticMatchingEnabled.set(dto.enabled());
                                });
                    } catch (RuntimeException ex) {
                        uiExecutor.accept(
                                () -> errorMessage.set(ReferenceColorsMessages.mapError(ex)));
                    }
                });
    }

    public long previewArchiveImpact(ColorDto color) {
        Objects.requireNonNull(color, "color");
        return queryApi.getArchiveImpact(color.id());
    }

    /**
     * Creates a catalog color.
     *
     * @return {@code null} on success; Russian error text otherwise (dialog stays open)
     */
    public String createColor(String code) {
        if (!canEdit.get() || commandInFlight.get()) {
            return "Недостаточно прав для изменения каталога цветов.";
        }
        inlineFormError.set("");
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            ColorDto created = commandApi.createColor(code);
            statusMessage.set("Цвет «" + created.code() + "» добавлен.");
            reloadColors();
            selectedColor.set(created);
            return null;
        } catch (RuntimeException ex) {
            String mapped = ReferenceColorsMessages.mapError(ex);
            inlineFormError.set(mapped);
            return mapped;
        } finally {
            commandInFlight.set(false);
        }
    }

    public boolean archiveSelected(ColorDto color) {
        if (!canEdit.get() || commandInFlight.get() || color == null || !color.active()) {
            return false;
        }
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            int deactivated = commandApi.archiveColor(color.id(), color.version());
            statusMessage.set(
                    "Цвет «"
                            + color.code()
                            + "» архивирован. Отключено соответствий СО7: "
                            + deactivated
                            + ".");
            reloadColors();
            return true;
        } catch (RuntimeException ex) {
            errorMessage.set(ReferenceColorsMessages.mapError(ex));
            return false;
        } finally {
            commandInFlight.set(false);
        }
    }

    public boolean restoreSelected(ColorDto color) {
        if (!canEdit.get() || commandInFlight.get() || color == null || color.active()) {
            return false;
        }
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            ColorDto restored = commandApi.restoreColor(color.id(), color.version());
            statusMessage.set(
                    "Цвет «"
                            + restored.code()
                            + "» восстановлен. Ранее отключённые соответствия СО7 автоматически не"
                            + " включаются.");
            reloadColors();
            return true;
        } catch (RuntimeException ex) {
            errorMessage.set(ReferenceColorsMessages.mapError(ex));
            return false;
        } finally {
            commandInFlight.set(false);
        }
    }

    public boolean applyAutomaticMatchingEnabled(boolean enabled) {
        if (!canEditSetting.get() || commandInFlight.get()) {
            return false;
        }
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            ColorMatchingSettingDto dto =
                    settingApi.setAutomaticMatchingEnabled(enabled, matchingSettingVersion);
            matchingSettingVersion = dto.version();
            automaticMatchingEnabled.set(dto.enabled());
            statusMessage.set(
                    dto.enabled()
                            ? "Автосопоставление цветов СО7 включено для будущих импортов."
                            : "Автосопоставление цветов СО7 выключено для будущих импортов.");
            return true;
        } catch (RuntimeException ex) {
            errorMessage.set(ReferenceColorsMessages.mapError(ex));
            reloadMatchingSetting();
            return false;
        } finally {
            commandInFlight.set(false);
        }
    }

    private void restoreSelectionAfterReload() {
        ColorDto previous = selectedColor.get();
        if (previous == null) {
            return;
        }
        UUID id = previous.id();
        ColorDto found =
                colors.stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
        selectedColor.set(found);
    }

    private boolean has(String permission) {
        try {
            return authorizationService.hasPermission(PermissionId.of(permission));
        } catch (AccessDeniedException | IllegalArgumentException ex) {
            return false;
        }
    }

    public ObservableList<ColorDto> colors() {
        return colors;
    }

    public ObjectProperty<ColorDto> selectedColorProperty() {
        return selectedColor;
    }

    public ObjectProperty<StatusFilter> statusFilterProperty() {
        return statusFilter;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public StringProperty errorMessageProperty() {
        return errorMessage;
    }

    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    public StringProperty inlineFormErrorProperty() {
        return inlineFormError;
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty commandInFlightProperty() {
        return commandInFlight;
    }

    public BooleanProperty canViewProperty() {
        return canView;
    }

    public BooleanProperty canEditProperty() {
        return canEdit;
    }

    public BooleanProperty canEditSettingProperty() {
        return canEditSetting;
    }

    public BooleanProperty automaticMatchingEnabledProperty() {
        return automaticMatchingEnabled;
    }
}