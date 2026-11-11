package com.tmp.ui.shell.screen.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ImportMappingSaveReport;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingDto;
import com.tmp.reference.api.So7ColorMappingQueryApi;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.reference.So7MappingsTabViewModel.StatusFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** COL-030B SO7 mappings tab ViewModel tests. */
class So7MappingsTabViewModelTest {

    private FakeMappingQuery query;
    private FakeMappingCommand command;
    private FakeColorQuery colors;
    private FakeAuth auth;
    private So7MappingsTabViewModel viewModel;

    @BeforeEach
    void setUp() {
        query = new FakeMappingQuery();
        command = new FakeMappingCommand(query);
        colors = new FakeColorQuery();
        auth = new FakeAuth();
        viewModel =
                new So7MappingsTabViewModel(
                        query, command, colors, auth, Runnable::run, Runnable::run);
    }

    @Test
    void loadsActiveMappingsWhenViewPermitted() {
        auth.allow(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION);
        So7ColorMappingDto active = mapping("RAL7016/30т", "RAL7016|30", true);
        So7ColorMappingDto inactive = mapping("Old", "00", false);
        query.seed(active, inactive);
        viewModel.onTabActivated();
        assertEquals(1, viewModel.mappings().size());
        assertEquals("RAL7016/30т", viewModel.mappings().get(0).sourceText());
    }

    @Test
    void deniesWithoutViewPermission() {
        viewModel.onTabActivated();
        assertFalse(viewModel.canViewProperty().get());
        assertTrue(viewModel.errorMessageProperty().get().contains("Недостаточно"));
        assertEquals(0, query.listCalls);
    }

    @Test
    void createRequiresMappingEditAndMapsDuplicate() {
        auth.allow(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION);
        ColorDto inside = color("Белый");
        ColorDto outside = color("RAL7016");
        colors.seed(inside, outside);
        viewModel.onTabActivated();
        assertEquals(
                "Недостаточно прав для изменения соответствий СО7.",
                viewModel.createMapping("X", inside.id(), outside.id()));
        assertEquals(0, command.createCalls);

        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_MAPPING_EDIT_PERMISSION);
        viewModel.refreshPermissions();
        assertNull(viewModel.createMapping("RAL7016/30т", inside.id(), outside.id()));
        assertEquals(1, command.createCalls);

        String duplicate =
                viewModel.createMapping("  ral7016/30т  ", inside.id(), outside.id());
        assertTrue(duplicate.contains("уже существует"));
    }

    @Test
    void updateActivateDeactivateAndDelete() {
        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_MAPPING_EDIT_PERMISSION);
        ColorDto inside = color("Белый");
        ColorDto outside = color("RAL7016");
        colors.seed(inside, outside);
        So7ColorMappingDto row = mapping("Белый", "Белый", true);
        query.seed(row);
        viewModel.onTabActivated();
        viewModel.selectedMappingProperty().set(row);

        assertNull(viewModel.updateMappingPair(row, inside.id(), outside.id()));
        assertEquals(1, command.updateCalls);

        So7ColorMappingDto updated = query.byId(row.id());
        assertTrue(viewModel.setMappingActive(updated, false));
        assertFalse(query.byId(row.id()).active());
        assertTrue(viewModel.statusMessageProperty().get().contains("отключено"));

        So7ColorMappingDto inactive = query.byId(row.id());
        viewModel.statusFilterProperty().set(StatusFilter.INACTIVE);
        assertTrue(viewModel.setMappingActive(inactive, true));
        assertTrue(query.byId(row.id()).active());

        So7ColorMappingDto activeAgain = query.byId(row.id());
        assertTrue(viewModel.deleteMapping(activeAgain));
        assertEquals(1, command.deleteCalls);
        assertTrue(query.store.isEmpty());
    }

    @Test
    void searchReloadDiscardsStaleAsyncResults() {
        auth.allow(UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION);
        query.seed(mapping("RAL7016/30т", "RAL7016|30", true), mapping("Белый", "Белый", true));

        java.util.concurrent.BlockingQueue<Runnable> tasks =
                new java.util.concurrent.LinkedBlockingQueue<>();
        Executor queued = tasks::add;
        viewModel =
                new So7MappingsTabViewModel(
                        query, command, colors, auth, queued, Runnable::run, 0L);
        viewModel.onTabActivated();
        assertEquals(1, tasks.size());
        tasks.remove().run();
        assertEquals(2, viewModel.mappings().size());

        viewModel.searchTextProperty().set("7016");
        Runnable first = tasks.remove();
        viewModel.searchTextProperty().set("Белый");
        Runnable second = tasks.remove();

        first.run();
        assertEquals(2, viewModel.mappings().size(), "stale search discarded");
        second.run();
        assertEquals(1, viewModel.mappings().size());
        assertEquals("Белый", viewModel.mappings().get(0).sourceText());
    }

    @Test
    void formatsArchiveMarkerAndActiveLabel() {
        assertEquals("RAL7016 (архив)", So7MappingsTabViewModel.formatSideCode("RAL7016", false));
        assertEquals("RAL7016", So7MappingsTabViewModel.formatSideCode("RAL7016", true));
        assertEquals("Активно", So7MappingsTabViewModel.formatActiveLabel(true));
        assertEquals("Отключено", So7MappingsTabViewModel.formatActiveLabel(false));
    }

    @Test
    void mapsDuplicateAndInactiveColorErrors() {
        assertTrue(
                ReferenceColorsMessages.mapMappingError(
                                new DuplicateSourceKeyException(
                                        "Сопоставление для этого текста уже существует"))
                        .contains("уже существует"));
        assertTrue(
                ReferenceColorsMessages.mapMappingError(
                                new RuntimeException("Referenced color is inactive: X"))
                        .contains("неактивен"));
    }

    private static ColorDto color(String code) {
        return new ColorDto(UUID.randomUUID(), code, true, 0L);
    }

    private static So7ColorMappingDto mapping(String source, String code, boolean active) {
        UUID inside = UUID.randomUUID();
        UUID outside = UUID.randomUUID();
        return new So7ColorMappingDto(
                UUID.randomUUID(),
                source,
                source.trim().toLowerCase(Locale.ROOT),
                inside,
                outside,
                code,
                code,
                true,
                true,
                code,
                active,
                0L);
    }

    private static final class DuplicateSourceKeyException extends RuntimeException {
        DuplicateSourceKeyException(String message) {
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

    private static final class FakeColorQuery implements ColorCatalogQueryApi {
        private final List<ColorDto> store = new ArrayList<>();

        void seed(ColorDto... colors) {
            store.clear();
            store.addAll(List.of(colors));
        }

        @Override
        public List<ColorDto> listColors(ColorDto.ActiveFilter filter, String search) {
            return store.stream().filter(ColorDto::active).toList();
        }

        @Override
        public long getArchiveImpact(UUID colorId) {
            return 0L;
        }
    }

    private static final class FakeMappingQuery implements So7ColorMappingQueryApi {
        private final List<So7ColorMappingDto> store = new ArrayList<>();
        private int listCalls;

        void seed(So7ColorMappingDto... mappings) {
            store.clear();
            store.addAll(List.of(mappings));
        }

        So7ColorMappingDto byId(UUID id) {
            return store.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
        }

        @Override
        public List<So7ColorMappingDto> listMappings(
                So7ColorMappingDto.ActiveFilter filter, String search) {
            listCalls++;
            String needle =
                    search == null || search.isBlank()
                            ? null
                            : search.toLowerCase(Locale.ROOT);
            return store.stream()
                    .filter(
                            m ->
                                    switch (filter) {
                                        case ACTIVE -> m.active();
                                        case INACTIVE -> !m.active();
                                        case ALL -> true;
                                    })
                    .filter(
                            m ->
                                    needle == null
                                            || m.sourceText()
                                                    .toLowerCase(Locale.ROOT)
                                                    .contains(needle)
                                            || m.insideCode()
                                                    .toLowerCase(Locale.ROOT)
                                                    .contains(needle)
                                            || m.outsideCode()
                                                    .toLowerCase(Locale.ROOT)
                                                    .contains(needle))
                    .toList();
        }
    }

    private static final class FakeMappingCommand implements So7ColorMappingCommandApi {
        private final FakeMappingQuery query;
        private int createCalls;
        private int updateCalls;
        private int deleteCalls;

        FakeMappingCommand(FakeMappingQuery query) {
            this.query = query;
        }

        @Override
        public So7ColorMappingDto createMapping(
                String sourceText, UUID insideColorId, UUID outsideColorId) {
            createCalls++;
            String trimmed = sourceText == null ? "" : sourceText.trim();
            String key = trimmed.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            boolean duplicate =
                    query.store.stream().anyMatch(m -> m.sourceKey().equals(key));
            if (duplicate) {
                throw new DuplicateSourceKeyException(
                        "Сопоставление для этого текста уже существует");
            }
            So7ColorMappingDto created =
                    new So7ColorMappingDto(
                            UUID.randomUUID(),
                            trimmed,
                            key,
                            insideColorId,
                            outsideColorId,
                            "IN",
                            "OUT",
                            true,
                            true,
                            "IN",
                            true,
                            0L);
            query.store.add(created);
            return created;
        }

        @Override
        public So7ColorMappingDto updateMappingPair(
                UUID mappingId, UUID insideColorId, UUID outsideColorId, long expectedVersion) {
            updateCalls++;
            So7ColorMappingDto existing = query.byId(mappingId);
            So7ColorMappingDto updated =
                    new So7ColorMappingDto(
                            existing.id(),
                            existing.sourceText(),
                            existing.sourceKey(),
                            insideColorId,
                            outsideColorId,
                            "IN",
                            "OUT",
                            true,
                            true,
                            "IN",
                            existing.active(),
                            expectedVersion + 1);
            replace(updated);
            return updated;
        }

        @Override
        public So7ColorMappingDto setMappingActive(
                UUID mappingId, boolean active, long expectedVersion) {
            So7ColorMappingDto existing = query.byId(mappingId);
            So7ColorMappingDto updated =
                    new So7ColorMappingDto(
                            existing.id(),
                            existing.sourceText(),
                            existing.sourceKey(),
                            existing.insideColorId(),
                            existing.outsideColorId(),
                            existing.insideCode(),
                            existing.outsideCode(),
                            existing.insideActive(),
                            existing.outsideActive(),
                            existing.displayColor(),
                            active,
                            expectedVersion + 1);
            replace(updated);
            return updated;
        }

        @Override
        public void deleteMapping(UUID mappingId, long expectedVersion) {
            deleteCalls++;
            query.store.removeIf(m -> m.id().equals(mappingId));
        }

        @Override
        public ImportMappingSaveReport saveMappingsFromImport(
                List<ImportMappingSaveReport.Candidate> candidates) {
            throw new UnsupportedOperationException();
        }

        private void replace(So7ColorMappingDto updated) {
            query.store.removeIf(m -> m.id().equals(updated.id()));
            query.store.add(updated);
        }
    }
}
