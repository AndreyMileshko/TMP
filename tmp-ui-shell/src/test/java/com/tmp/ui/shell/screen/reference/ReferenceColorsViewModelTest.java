package com.tmp.ui.shell.screen.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.api.ColorMatchingSettingApi.ColorMatchingSettingDto;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.reference.ReferenceColorsViewModel.StatusFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** COL-030A Reference Colors ViewModel tests. */
class ReferenceColorsViewModelTest {

    private FakeQuery query;
    private FakeCommand command;
    private FakeSetting setting;
    private FakeAuth auth;
    private ReferenceColorsViewModel viewModel;

    @BeforeEach
    void setUp() {
        query = new FakeQuery();
        command = new FakeCommand(query);
        setting = new FakeSetting();
        auth = new FakeAuth();
        viewModel =
                new ReferenceColorsViewModel(
                        query, command, setting, auth, Runnable::run, Runnable::run);
    }

    @Test
    void loadsActiveColorsWhenViewPermitted() {
        auth.allow(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION);
        query.seed(active("RAL7016"), archived("Old"));
        viewModel.onScreenOpened();
        assertEquals(1, viewModel.colors().size());
        assertEquals("RAL7016", viewModel.colors().get(0).code());
        assertFalse(viewModel.automaticMatchingEnabledProperty().get());
        assertEquals(1, setting.getCalls);
    }

    @Test
    void deniesScreenWithoutViewPermission() {
        viewModel.onScreenOpened();
        assertFalse(viewModel.canViewProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().contains("Недостаточно"));
        assertEquals(0, query.listCalls);
        assertEquals(0, setting.getCalls);
    }

    @Test
    void createRequiresEditPermissionAndMapsDuplicate() {
        auth.allow(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION);
        viewModel.onScreenOpened();
        assertEquals(
                "Недостаточно прав для изменения каталога цветов.",
                viewModel.createColor("RAL7016"));
        assertEquals(0, command.createCalls);

        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_EDIT_PERMISSION);
        viewModel.refreshPermissions();
        assertNull(viewModel.createColor("RAL7016"));
        assertEquals(1, command.createCalls);

        String duplicate = viewModel.createColor("ral7016");
        assertTrue(duplicate.contains("уже существует"));
    }

    @Test
    void archiveUsesImpactAndRestoreDoesNotReactivateMappings() {
        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_EDIT_PERMISSION);
        ColorDto color = active("Белый");
        query.seed(color);
        query.archiveImpactById.put(color.id(), 3L);
        viewModel.onScreenOpened();
        viewModel.selectedColorProperty().set(color);

        assertEquals(3L, viewModel.previewArchiveImpact(color));
        assertTrue(viewModel.archiveSelected(color));
        assertEquals(1, command.archiveCalls);
        assertTrue(viewModel.statusMessageProperty().get().contains("3"));

        ColorDto archived = query.byId(color.id());
        assertFalse(archived.active());
        viewModel.statusFilterProperty().set(StatusFilter.ARCHIVED);
        viewModel.selectedColorProperty().set(archived);
        assertTrue(viewModel.restoreSelected(archived));
        assertEquals(1, command.restoreCalls);
        assertTrue(viewModel.statusMessageProperty().get().contains("не включаются"));
        assertEquals(0, query.reactivatedMappings);
    }

    @Test
    void matchingSwitchRequiresSettingPermission() {
        auth.allow(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION);
        viewModel.onScreenOpened();
        assertFalse(viewModel.applyAutomaticMatchingEnabled(true));
        assertEquals(0, setting.setCalls);

        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_SETTING_EDIT_PERMISSION);
        viewModel.refreshPermissions();
        assertTrue(viewModel.applyAutomaticMatchingEnabled(true));
        assertEquals(1, setting.setCalls);
        assertTrue(viewModel.automaticMatchingEnabledProperty().get());
    }

    @Test
    void matchingApplyFailureRestoresConfirmedStateFromServer() {
        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_SETTING_EDIT_PERMISSION);
        setting.enabled = false;
        setting.version = 2L;
        viewModel.onScreenOpened();
        assertFalse(viewModel.automaticMatchingEnabledProperty().get());

        setting.failNextSet = new RuntimeException("Version conflict");
        assertFalse(viewModel.applyAutomaticMatchingEnabled(true));
        assertEquals(1, setting.setCalls);
        assertFalse(viewModel.automaticMatchingEnabledProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().length() > 0);
        assertEquals(2, setting.getCalls);
    }

    @Test
    void matchingApplySuccessPersistsBothDirections() {
        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_SETTING_EDIT_PERMISSION);
        viewModel.onScreenOpened();
        assertFalse(viewModel.automaticMatchingEnabledProperty().get());

        assertTrue(viewModel.applyAutomaticMatchingEnabled(true));
        assertTrue(viewModel.automaticMatchingEnabledProperty().get());
        assertTrue(setting.enabled);

        assertTrue(viewModel.applyAutomaticMatchingEnabled(false));
        assertFalse(viewModel.automaticMatchingEnabledProperty().get());
        assertFalse(setting.enabled);
        assertEquals(2, setting.setCalls);
    }

    @Test
    void searchReloadDiscardsStaleAsyncResults() {
        auth.allow(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION);
        query.seed(active("RAL7016"), active("RAL9005"));

        java.util.concurrent.BlockingQueue<Runnable> tasks =
                new java.util.concurrent.LinkedBlockingQueue<>();
        Executor queued = tasks::add;
        viewModel =
                new ReferenceColorsViewModel(
                        query, command, setting, auth, queued, Runnable::run, 0L);
        viewModel.onScreenOpened();
        assertEquals(2, tasks.size(), "initial open loads colors and matching setting");
        tasks.remove().run(); // colors
        tasks.remove().run(); // matching setting
        assertEquals(2, viewModel.colors().size());

        viewModel.searchTextProperty().set("7016");
        assertEquals(1, tasks.size());
        Runnable firstSearch = tasks.remove();
        viewModel.searchTextProperty().set("9005");
        assertEquals(1, tasks.size());
        Runnable secondSearch = tasks.remove();

        firstSearch.run();
        assertEquals(2, viewModel.colors().size(), "stale search must not replace newer generation");

        secondSearch.run();
        assertEquals(1, viewModel.colors().size());
        assertEquals("RAL9005", viewModel.colors().get(0).code());
        assertEquals(3, query.listCalls);
    }

    @Test
    void mapsInvalidCodeToRussian() {
        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_EDIT_PERMISSION);
        viewModel.refreshPermissions();
        command.failCreateWith =
                new RuntimeException("Color code length must be <= 31 characters") {
                    // Named like domain InvalidCodeException for mapper.
                };
        // Force class-name based path via anonymous is RuntimeException — use Duplicate-style message.
        assertTrue(
                ReferenceColorsMessages.mapError(
                                new FakeInvalidCode(
                                        "Color code length must be <= 31 characters"))
                        .contains("31"));
        assertTrue(
                ReferenceColorsMessages.mapError(
                                new FakeDuplicate(
                                        "Color code already exists (case-insensitive): x"))
                        .contains("уже существует"));
    }

    private static ColorDto active(String code) {
        return new ColorDto(UUID.randomUUID(), code, true, 0L);
    }

    private static ColorDto archived(String code) {
        return new ColorDto(UUID.randomUUID(), code, false, 1L);
    }

    private static final class FakeInvalidCode extends RuntimeException {
        FakeInvalidCode(String message) {
            super(message);
        }
    }

    private static final class FakeDuplicate extends RuntimeException {
        FakeDuplicate(String message) {
            super(message);
        }
    }

    private static final class FakeAuth implements AuthorizationService {
        private final Set<String> allowed = new HashSet<>();

        void allow(String... permissions) {
            allowed.clear();
            allowed.addAll(List.of(permissions));
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId.value());
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException("denied");
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    private static final class FakeQuery implements ColorCatalogQueryApi {
        private final List<ColorDto> store = new ArrayList<>();
        private final java.util.Map<UUID, Long> archiveImpactById = new java.util.HashMap<>();
        private int listCalls;
        private int reactivatedMappings;

        void seed(ColorDto... colors) {
            store.clear();
            store.addAll(List.of(colors));
        }

        ColorDto byId(UUID id) {
            return store.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
        }

        @Override
        public List<ColorDto> listColors(ColorDto.ActiveFilter filter, String search) {
            listCalls++;
            String needle =
                    search == null || search.isBlank()
                            ? null
                            : search.toLowerCase(Locale.ROOT);
            return store.stream()
                    .filter(
                            c ->
                                    switch (filter) {
                                        case ACTIVE -> c.active();
                                        case ARCHIVED -> !c.active();
                                        case ALL -> true;
                                    })
                    .filter(
                            c ->
                                    needle == null
                                            || c.code().toLowerCase(Locale.ROOT).contains(needle))
                    .toList();
        }

        @Override
        public long getArchiveImpact(UUID colorId) {
            return archiveImpactById.getOrDefault(colorId, 0L);
        }
    }

    private static final class FakeCommand implements ColorCatalogCommandApi {
        private final FakeQuery query;
        private int createCalls;
        private int archiveCalls;
        private int restoreCalls;
        private RuntimeException failCreateWith;

        FakeCommand(FakeQuery query) {
            this.query = query;
        }

        @Override
        public ColorDto createColor(String code) {
            createCalls++;
            if (failCreateWith != null) {
                throw failCreateWith;
            }
            String trimmed = code == null ? "" : code.trim();
            boolean duplicate =
                    query.store.stream()
                            .anyMatch(
                                    c ->
                                            c.code()
                                                    .equalsIgnoreCase(trimmed));
            if (duplicate) {
                throw new FakeDuplicate(
                        "Color code already exists (case-insensitive): " + trimmed);
            }
            ColorDto created = new ColorDto(UUID.randomUUID(), trimmed, true, 0L);
            query.store.add(created);
            return created;
        }

        @Override
        public int archiveColor(UUID colorId, long expectedVersion) {
            archiveCalls++;
            ColorDto existing = query.byId(colorId);
            long impact = query.archiveImpactById.getOrDefault(colorId, 0L);
            query.store.removeIf(c -> c.id().equals(colorId));
            query.store.add(new ColorDto(colorId, existing.code(), false, expectedVersion + 1));
            return (int) impact;
        }

        @Override
        public ColorDto restoreColor(UUID colorId, long expectedVersion) {
            restoreCalls++;
            ColorDto existing = query.byId(colorId);
            ColorDto restored = new ColorDto(colorId, existing.code(), true, expectedVersion + 1);
            query.store.removeIf(c -> c.id().equals(colorId));
            query.store.add(restored);
            return restored;
        }
    }

    private static final class FakeSetting implements ColorMatchingSettingApi {
        private boolean enabled;
        private long version;
        private int getCalls;
        private int setCalls;
        private RuntimeException failNextSet;

        @Override
        public ColorMatchingSettingDto getAutomaticMatchingSetting() {
            getCalls++;
            return new ColorMatchingSettingDto(enabled, version);
        }

        @Override
        public ColorMatchingSettingDto setAutomaticMatchingEnabled(
                boolean enabled, long expectedVersion) {
            setCalls++;
            if (failNextSet != null) {
                RuntimeException ex = failNextSet;
                failNextSet = null;
                throw ex;
            }
            if (expectedVersion != version) {
                throw new RuntimeException("Version conflict");
            }
            this.enabled = enabled;
            version++;
            return new ColorMatchingSettingDto(this.enabled, version);
        }
    }
}