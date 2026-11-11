package com.tmp.ui.shell.screen.reference;

import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.api.So7ColorMappingQueryApi;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
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
 * SO7 mappings tab ViewModel (TMP-SPEC-015 §14.2 / COL-030B).
 *
 * <p>Uses only Reference Data public APIs. No JDBC. Display color and archive markers come from the
 * DTO — UI does not recompute pair composition.
 */
@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "JavaFX ViewModel intentionally exposes observable properties")
public final class So7MappingsTabViewModel {

    static final long DEFAULT_SEARCH_DEBOUNCE_MS = 280L;

    public enum StatusFilter {
        ACTIVE("Активные", So7ColorMappingDto.ActiveFilter.ACTIVE),
        INACTIVE("Отключённые", So7ColorMappingDto.ActiveFilter.INACTIVE),
        ALL("Все", So7ColorMappingDto.ActiveFilter.ALL);

        private final String label;
        private final So7ColorMappingDto.ActiveFilter apiFilter;

        StatusFilter(String label, So7ColorMappingDto.ActiveFilter apiFilter) {
            this.label = label;
            this.apiFilter = apiFilter;
        }

        public String label() {
            return label;
        }

        So7ColorMappingDto.ActiveFilter apiFilter() {
            return apiFilter;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final So7ColorMappingQueryApi queryApi;
    private final So7ColorMappingCommandApi commandApi;
    private final ColorCatalogQueryApi colorCatalogQueryApi;
    private final AuthorizationService authorizationService;
    private final Executor backgroundExecutor;
    private final Consumer<Runnable> uiExecutor;
    private final long searchDebounceMillis;
    private final ScheduledExecutorService searchDebounceExecutor;

    private final ObservableList<So7ColorMappingDto> mappings = FXCollections.observableArrayList();
    private final ObjectProperty<So7ColorMappingDto> selectedMapping = new SimpleObjectProperty<>();
    private final ObjectProperty<StatusFilter> statusFilter =
            new SimpleObjectProperty<>(StatusFilter.ACTIVE);
    private final StringProperty searchText = new SimpleStringProperty("");
    private final StringProperty errorMessage = new SimpleStringProperty("");
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final StringProperty inlineFormError = new SimpleStringProperty("");
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty commandInFlight = new SimpleBooleanProperty(false);
    private final BooleanProperty canView = new SimpleBooleanProperty(false);
    private final BooleanProperty canEditMappings = new SimpleBooleanProperty(false);

    private long loadGeneration;
    private ScheduledFuture<?> pendingSearchReload;

    public So7MappingsTabViewModel(
            So7ColorMappingQueryApi queryApi,
            So7ColorMappingCommandApi commandApi,
            ColorCatalogQueryApi colorCatalogQueryApi,
            AuthorizationService authorizationService) {
        this(
                queryApi,
                commandApi,
                colorCatalogQueryApi,
                authorizationService,
                Executors.newCachedThreadPool(
                        runnable -> {
                            Thread thread = new Thread(runnable, "so7-mappings");
                            thread.setDaemon(true);
                            return thread;
                        }),
                Platform::runLater,
                DEFAULT_SEARCH_DEBOUNCE_MS);
    }

    So7MappingsTabViewModel(
            So7ColorMappingQueryApi queryApi,
            So7ColorMappingCommandApi commandApi,
            ColorCatalogQueryApi colorCatalogQueryApi,
            AuthorizationService authorizationService,
            Executor backgroundExecutor,
            Consumer<Runnable> uiExecutor) {
        this(
                queryApi,
                commandApi,
                colorCatalogQueryApi,
                authorizationService,
                backgroundExecutor,
                uiExecutor,
                0L);
    }

    So7MappingsTabViewModel(
            So7ColorMappingQueryApi queryApi,
            So7ColorMappingCommandApi commandApi,
            ColorCatalogQueryApi colorCatalogQueryApi,
            AuthorizationService authorizationService,
            Executor backgroundExecutor,
            Consumer<Runnable> uiExecutor,
            long searchDebounceMillis) {
        this.queryApi = Objects.requireNonNull(queryApi, "queryApi");
        this.commandApi = Objects.requireNonNull(commandApi, "commandApi");
        this.colorCatalogQueryApi =
                Objects.requireNonNull(colorCatalogQueryApi, "colorCatalogQueryApi");
        this.authorizationService =
                Objects.requireNonNull(authorizationService, "authorizationService");
        this.backgroundExecutor = Objects.requireNonNull(backgroundExecutor, "backgroundExecutor");
        this.uiExecutor = Objects.requireNonNull(uiExecutor, "uiExecutor");
        this.searchDebounceMillis = Math.max(0L, searchDebounceMillis);
        this.searchDebounceExecutor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {
                            Thread thread = new Thread(runnable, "so7-mappings-search");
                            thread.setDaemon(true);
                            return thread;
                        });
        statusFilter.addListener(
                (obs, o, n) -> {
                    cancelPendingSearchReload();
                    reloadMappings();
                });
        searchText.addListener((obs, o, n) -> scheduleSearchReload());
        refreshPermissions();
    }

    public void onTabActivated() {
        refreshPermissions();
        if (!canView.get()) {
            errorMessage.set("Недостаточно прав для просмотра соответствий СО7.");
            mappings.clear();
            selectedMapping.set(null);
            return;
        }
        errorMessage.set("");
        statusMessage.set("");
        inlineFormError.set("");
        cancelPendingSearchReload();
        reloadMappings();
    }

    public void refreshPermissions() {
        canView.set(has(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION));
        canEditMappings.set(has(UiShellScreens.REFERENCE_COLOR_MAPPING_EDIT_PERMISSION));
    }

    public void reloadMappings() {
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
                        List<So7ColorMappingDto> loaded =
                                queryApi.listMappings(filter.apiFilter(), search);
                        uiExecutor.accept(
                                () -> {
                                    if (generation != loadGeneration) {
                                        return;
                                    }
                                    mappings.setAll(loaded);
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
                                    mappings.clear();
                                    errorMessage.set(ReferenceColorsMessages.mapMappingError(ex));
                                });
                    }
                });
    }

    /** Active TMP colors for inside/outside selectors (dialog). */
    public List<ColorDto> listActiveColorsForDialog() {
        return colorCatalogQueryApi.listColors(ColorDto.ActiveFilter.ACTIVE, null);
    }

    /**
     * Creates a mapping.
     *
     * @return {@code null} on success; Russian error text otherwise (dialog stays open)
     */
    public String createMapping(String sourceText, UUID insideColorId, UUID outsideColorId) {
        if (!canEditMappings.get() || commandInFlight.get()) {
            return "Недостаточно прав для изменения соответствий СО7.";
        }
        inlineFormError.set("");
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            So7ColorMappingDto created =
                    commandApi.createMapping(sourceText, insideColorId, outsideColorId);
            statusMessage.set("Соответствие «" + created.sourceText() + "» добавлено.");
            reloadMappings();
            selectedMapping.set(created);
            return null;
        } catch (RuntimeException ex) {
            String mapped = ReferenceColorsMessages.mapMappingError(ex);
            inlineFormError.set(mapped);
            return mapped;
        } finally {
            commandInFlight.set(false);
        }
    }

    /**
     * Updates the inside/outside pair.
     *
     * @return {@code null} on success; Russian error text otherwise
     */
    public String updateMappingPair(
            So7ColorMappingDto mapping, UUID insideColorId, UUID outsideColorId) {
        if (!canEditMappings.get()
                || commandInFlight.get()
                || mapping == null) {
            return "Недостаточно прав для изменения соответствий СО7.";
        }
        inlineFormError.set("");
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            So7ColorMappingDto updated =
                    commandApi.updateMappingPair(
                            mapping.id(), insideColorId, outsideColorId, mapping.version());
            statusMessage.set("Пара для «" + updated.sourceText() + "» изменена.");
            reloadMappings();
            selectedMapping.set(updated);
            return null;
        } catch (RuntimeException ex) {
            String mapped = ReferenceColorsMessages.mapMappingError(ex);
            inlineFormError.set(mapped);
            return mapped;
        } finally {
            commandInFlight.set(false);
        }
    }

    public boolean setMappingActive(So7ColorMappingDto mapping, boolean active) {
        if (!canEditMappings.get() || commandInFlight.get() || mapping == null) {
            return false;
        }
        if (mapping.active() == active) {
            return true;
        }
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            So7ColorMappingDto updated =
                    commandApi.setMappingActive(mapping.id(), active, mapping.version());
            statusMessage.set(
                    active
                            ? "Соответствие «" + updated.sourceText() + "» включено."
                            : "Соответствие «" + updated.sourceText() + "» отключено.");
            reloadMappings();
            selectedMapping.set(updated);
            return true;
        } catch (RuntimeException ex) {
            errorMessage.set(ReferenceColorsMessages.mapMappingError(ex));
            return false;
        } finally {
            commandInFlight.set(false);
        }
    }

    public boolean deleteMapping(So7ColorMappingDto mapping) {
        if (!canEditMappings.get() || commandInFlight.get() || mapping == null) {
            return false;
        }
        errorMessage.set("");
        commandInFlight.set(true);
        try {
            commandApi.deleteMapping(mapping.id(), mapping.version());
            statusMessage.set("Соответствие «" + mapping.sourceText() + "» удалено.");
            selectedMapping.set(null);
            reloadMappings();
            return true;
        } catch (RuntimeException ex) {
            errorMessage.set(ReferenceColorsMessages.mapMappingError(ex));
            return false;
        } finally {
            commandInFlight.set(false);
        }
    }

    static String formatSideCode(String code, boolean active) {
        if (active) {
            return code;
        }
        return code + " (архив)";
    }

    static String formatActiveLabel(boolean active) {
        return active ? "Активно" : "Отключено";
    }

    private void scheduleSearchReload() {
        cancelPendingSearchReload();
        if (searchDebounceMillis <= 0L) {
            reloadMappings();
            return;
        }
        pendingSearchReload =
                searchDebounceExecutor.schedule(
                        () -> uiExecutor.accept(this::reloadMappings),
                        searchDebounceMillis,
                        TimeUnit.MILLISECONDS);
    }

    private void cancelPendingSearchReload() {
        if (pendingSearchReload != null) {
            pendingSearchReload.cancel(false);
            pendingSearchReload = null;
        }
    }

    private void restoreSelectionAfterReload() {
        So7ColorMappingDto previous = selectedMapping.get();
        if (previous == null) {
            return;
        }
        UUID id = previous.id();
        So7ColorMappingDto found =
                mappings.stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
        selectedMapping.set(found);
    }

    private boolean has(String permission) {
        try {
            return authorizationService.hasPermission(PermissionId.of(permission));
        } catch (AccessDeniedException | IllegalArgumentException ex) {
            return false;
        }
    }

    public ObservableList<So7ColorMappingDto> mappings() {
        return mappings;
    }

    public ObjectProperty<So7ColorMappingDto> selectedMappingProperty() {
        return selectedMapping;
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

    public BooleanProperty canEditMappingsProperty() {
        return canEditMappings;
    }
}
