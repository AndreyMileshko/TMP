package com.tmp.ui.shell.screen.orderimport.colors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ResolvedColorPair;
import com.tmp.reference.api.SourceColorResolution;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ColorResolutionDialogViewModelTest {

    private static final UUID CAT_BELYI = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CAT_RAL7016 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAT_RAL7016_PIPE_30 = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void sessionCountersIgnoreFilterAndSearch() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        api.resolutions.put("Цвет", SourceColorResolution.noMapping("Цвет", "цвет"));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                batchWithPositions(
                        "ORD",
                        List.of(positionWithColor("1", "P1", "Белый"), positionWithColor("2", "P2", "Цвет")));
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(coordinator.prepare(List.of(batch)).session());
        assertEquals(1, vm.countMatched());
        assertEquals(1, vm.countNeedsChoice());
        vm.setFilter(ColorResolutionDialogViewModel.DisplayFilter.NEEDS_CHOICE);
        vm.setSearchText("missing");
        assertEquals(1, vm.countMatched());
        assertEquals(1, vm.countNeedsChoice());
    }

    @Test
    void productLineLabelUsesPositionDotNameWithoutProductCode() {
        ColorResolutionDialogViewModel vm = vmForUnmatched("Цвет");
        ColorResolutionRow row = vm.session().rows().get(0);
        assertEquals("1 · Item 1", vm.productLineLabel(row));
        assertTrue(vm.productLineTooltip(row).contains("Код: P1"));
        assertFalse(vm.productLineLabel(row).contains("/"));
    }

    @Test
    void filterNeedsChoiceHidesMatchedRowsOnlyInDisplay() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        api.resolutions.put("Цвет", SourceColorResolution.noMapping("Цвет", "цвет"));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                batchWithPositions(
                        "ORD",
                        List.of(positionWithColor("1", "P1", "Белый"), positionWithColor("2", "P2", "Цвет")));
        ColorResolutionSession session = coordinator.prepare(List.of(batch)).session();
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        assertEquals(2, session.rows().size());
        assertEquals(1, vm.visibleRows().size());
        vm.setFilter(ColorResolutionDialogViewModel.DisplayFilter.ALL);
        assertEquals(2, vm.visibleRows().size());
    }

    @Test
    void searchAffectsDisplayOnly() {
        FakeColorApi api = unmatchedApi("Alpha", "alpha");
        api.resolutions.put("Beta", SourceColorResolution.noMapping("Beta", "beta"));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                batchWithPositions("ORD", List.of(positionWithColor("1", "P1", "Alpha"), positionWithColor("2", "P2", "Beta")));
        ColorResolutionDialogViewModel vm =
                new ColorResolutionDialogViewModel(coordinator.prepare(List.of(batch)).session());
        vm.setFilter(ColorResolutionDialogViewModel.DisplayFilter.ALL);
        vm.setSearchText("alp");
        assertEquals(1, vm.visibleRows().size());
        assertEquals(2, vm.session().rows().size());
    }

    @Test
    void changingOneSideKeepsOtherSide() {
        ColorResolutionDialogViewModel vm = vmForUnmatched("Цвет");
        ColorResolutionRow.Id id = vm.session().rows().get(0).id();
        vm.applyInsideColor(id, CAT_BELYI);
        vm.applyOutsideColor(id, CAT_RAL7016);
        assertEquals("Белый/RAL7016", vm.session().rows().get(0).finalColor());
        vm.applyInsideColor(id, CAT_BELYI);
        vm.applyOutsideColor(id, CAT_RAL7016_PIPE_30);
        assertEquals("Белый/RAL7016|30", vm.session().rows().get(0).finalColor());
    }

    @Test
    void partialSideDoesNotChangeSession() {
        ColorResolutionDialogViewModel vm = vmForUnmatched("Цвет");
        ColorResolutionRow.Id id = vm.session().rows().get(0).id();
        ColorResolutionSession before = vm.session();
        vm.applyInsideColor(id, CAT_BELYI);
        assertEquals(before, vm.session());
        assertNull(vm.session().rows().get(0).constraintResult());
    }

    @Test
    void massPairToVisibleHighlightedRows() {
        ColorResolutionDialogViewModel vm = vmForTwoUnmatched("Цвет");
        ColorResolutionRow.Id first = vm.session().rows().get(0).id();
        vm.setRowHighlighted(first, true);
        vm.applyMassPairToHighlighted(CAT_BELYI, CAT_RAL7016);
        assertEquals("Белый/RAL7016", vm.session().rows().get(0).finalColor());
        assertNull(vm.session().rows().get(1).finalColor());
    }

    @Test
    void massPairBySourceKeyTouchesHiddenRows() {
        ColorResolutionDialogViewModel vm = vmForTwoUnmatched("Цвет");
        vm.setFilter(ColorResolutionDialogViewModel.DisplayFilter.NEEDS_CHOICE);
        vm.setRowHighlighted(vm.session().rows().get(0).id(), true);
        vm.setRowHighlighted(vm.session().rows().get(1).id(), true);
        vm.applyMassPairToSourceKey(CAT_BELYI, CAT_RAL7016);
        assertEquals("Белый/RAL7016", vm.session().rows().get(0).finalColor());
        assertEquals("Белый/RAL7016", vm.session().rows().get(1).finalColor());
    }

    @Test
    void massPairBySourceKeyDisabledForMultipleKeys() {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put("A", SourceColorResolution.noMapping("A", "a"));
        api.resolutions.put("B", SourceColorResolution.noMapping("B", "b"));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                batchWithPositions("ORD", List.of(positionWithColor("1", "P1", "A"), positionWithColor("2", "P2", "B")));
        ColorResolutionDialogViewModel vm =
                new ColorResolutionDialogViewModel(coordinator.prepare(List.of(batch)).session());
        vm.setFilter(ColorResolutionDialogViewModel.DisplayFilter.ALL);
        vm.setRowHighlighted(vm.session().rows().get(0).id(), true);
        vm.setRowHighlighted(vm.session().rows().get(1).id(), true);
        assertFalse(vm.canApplyMassPairToSourceKey(CAT_BELYI, CAT_RAL7016));
    }

    @Test
    void keepSourceForHighlightedAndForAllNeedsChoice() {
        ColorResolutionDialogViewModel vm = vmForTwoUnmatched("Цвет");
        vm.setRowHighlighted(vm.session().rows().get(0).id(), true);
        vm.keepSourceTextForHighlighted();
        assertEquals("Цвет", vm.session().rows().get(0).finalColor());
        vm.keepSourceTextForAllNeedsChoice();
        assertEquals("Цвет", vm.session().rows().get(1).finalColor());
    }

    @Test
    void emptyCustomColorBlocksReadyWithBlank() {
        ColorResolutionDialogViewModel vm = vmForUnmatched("Цвет");
        vm.setRowHighlighted(vm.session().rows().get(0).id(), true);
        vm.applyCustomColorToHighlighted("");
        assertEquals(ColorResolutionRow.UserState.MANUALLY_CHANGED, vm.session().rows().get(0).userState());
        assertEquals("Укажите цвет", vm.constraintErrorMessage(vm.session().rows().get(0)));
        assertFalse(vm.isApplyEnabled());
    }

    @Test
    void repeatAutoPairStaysMatched() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionDialogViewModel vm =
                new ColorResolutionDialogViewModel(
                        coordinator.prepare(List.of(sampleBatch("ORD", "Белый"))).session());
        ColorResolutionRow.Id id = vm.session().rows().get(0).id();
        vm.applyInsideColor(id, CAT_BELYI);
        vm.applyOutsideColor(id, CAT_RAL7016);
        assertEquals(ColorResolutionRow.UserState.MATCHED, vm.session().rows().get(0).userState());
    }

    @Test
    void differentPairBecomesManuallyChanged() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionDialogViewModel vm =
                new ColorResolutionDialogViewModel(
                        coordinator.prepare(List.of(sampleBatch("ORD", "Белый"))).session());
        ColorResolutionRow.Id id = vm.session().rows().get(0).id();
        vm.applyInsideColor(id, CAT_BELYI);
        vm.applyOutsideColor(id, CAT_RAL7016_PIPE_30);
        assertEquals(ColorResolutionRow.UserState.MANUALLY_CHANGED, vm.session().rows().get(0).userState());
    }

    @Test
    void unfilledRowHasNoBlankAndBlocksApply() {
        ColorResolutionDialogViewModel vm = vmForUnmatched("Цвет");
        assertEquals("", vm.constraintErrorMessage(vm.session().rows().get(0)));
        assertFalse(vm.isApplyEnabled());
    }

    @Test
    void saveFlagOffPersistsOnPairChange() {
        ColorResolutionDialogViewModel vm = vmForUnmatched("Цвет");
        ColorResolutionRow.Id id = vm.session().rows().get(0).id();
        vm.applyInsideColor(id, CAT_BELYI);
        vm.applyOutsideColor(id, CAT_RAL7016);
        vm.toggleSaveMapping(id, false);
        vm.applyInsideColor(id, CAT_BELYI);
        vm.applyOutsideColor(id, CAT_RAL7016_PIPE_30);
        assertFalse(vm.session().rows().get(0).saveMappingSelected());
    }

    @Test
    void conflictWarnedButDoesNotBlockReady() {
        ColorResolutionSession session = vmForTwoUnmatched("Цвет").session();
        session = session.applyCatalogPair(List.of(session.rows().get(0).id()), CAT_BELYI, CAT_RAL7016);
        session = session.applyCatalogPair(List.of(session.rows().get(1).id()), CAT_BELYI, CAT_RAL7016_PIPE_30);
        ColorResolutionDialogViewModel vm = new ColorResolutionDialogViewModel(session);
        assertTrue(vm.hasMappingSaveConflicts());
        assertTrue(vm.isApplyEnabled());
    }

    @Test
    void noGlobalSaveStillAllowsResolution() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        api.canSave = false;
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionDialogViewModel vm =
                new ColorResolutionDialogViewModel(
                        coordinator.prepare(List.of(sampleBatch("ORD", "Цвет"))).session());
        assertFalse(vm.showSaveMappingColumn());
        vm.setRowHighlighted(vm.session().rows().get(0).id(), true);
        vm.applyMassPairToHighlighted(CAT_BELYI, CAT_RAL7016);
        assertTrue(vm.isApplyEnabled());
    }

    @Test
    void cancelDraftDoesNotChangeSession() {
        ColorResolutionDialogViewModel vm = vmForUnmatched("Цвет");
        ColorResolutionRow.Id id = vm.session().rows().get(0).id();
        ColorResolutionSession before = vm.session();
        vm.applyInsideColor(id, CAT_BELYI);
        vm.cancelDraft(id);
        assertEquals(before, vm.session());
    }

    @Test
    void filterChangeRemovesHiddenSelection() {
        ColorResolutionDialogViewModel vm = vmForTwoUnmatched("Цвет");
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                batchWithPositions(
                        "ORD", List.of(positionWithColor("1", "P1", "Белый"), positionWithColor("2", "P2", "Цвет")));
        vm = new ColorResolutionDialogViewModel(coordinator.prepare(List.of(batch)).session());
        vm.setFilter(ColorResolutionDialogViewModel.DisplayFilter.ALL);
        vm.setRowHighlighted(vm.session().rows().get(0).id(), true);
        vm.setRowHighlighted(vm.session().rows().get(1).id(), true);
        vm.setFilter(ColorResolutionDialogViewModel.DisplayFilter.NEEDS_CHOICE);
        assertFalse(vm.isRowHighlighted(vm.session().rows().get(0).id()));
        assertTrue(vm.isRowHighlighted(vm.session().rows().get(1).id()));
    }

    private static ColorResolutionDialogViewModel vmForUnmatched(String text) {
        ColorResolutionCoordinator coordinator =
                new ColorResolutionCoordinator(unmatchedApi(text, text.toLowerCase()), new FakeConstraint());
        return new ColorResolutionDialogViewModel(
                coordinator.prepare(List.of(sampleBatch("ORD", text))).session());
    }

    private static ColorResolutionDialogViewModel vmForTwoUnmatched(String text) {
        ColorResolutionCoordinator coordinator =
                new ColorResolutionCoordinator(unmatchedApi(text, text.toLowerCase()), new FakeConstraint());
        OrderImportBatch batch = batchWithPositions("ORD", List.of(positionWithColor("1", "P1", text), positionWithColor("2", "P2", text)));
        return new ColorResolutionDialogViewModel(coordinator.prepare(List.of(batch)).session());
    }

    private static OrderImportBatch sampleBatch(String orderNumber, String color) {
        return batchWithPositions(orderNumber, List.of(positionWithColor("1", "P1", color)));
    }

    private static OrderImportBatch batchWithPositions(String orderNumber, List<OrderImportPosition> positions) {
        return OrderImportBatch.of(
                "STXT",
                "f.stxt",
                "chk",
                orderNumber,
                LocalDate.of(2026, 1, 1),
                null,
                "Client",
                positions);
    }

    private static OrderImportPosition positionWithColor(String pos, String code, String color) {
        return OrderImportPosition.of(pos, code, "Item " + pos, 1, List.of(line("MAT", color)));
    }

    private static OrderImportSpecificationLine line(String material, String color) {
        return OrderImportSpecificationLine.of(material, "Material", color, BigDecimal.ONE, "шт", BigDecimal.ONE);
    }

    private static ResolvedColorPair pairBelyiRal7016() {
        return new ResolvedColorPair(CAT_BELYI, CAT_RAL7016, "Белый", "RAL7016", "Белый/RAL7016");
    }

    private static FakeColorApi matchedApi(Map<String, ResolvedColorPair> pairs) {
        FakeColorApi api = new FakeColorApi(true);
        pairs.forEach(
                (text, pair) -> api.resolutions.put(text, SourceColorResolution.matched(text, text.toLowerCase(), pair)));
        return api;
    }

    private static FakeColorApi unmatchedApi(String text, String key) {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put(text, SourceColorResolution.noMapping(text, key));
        return api;
    }

    private static final class FakeConstraint implements SpecificationColorConstraint {

        private final Set<String> reject = new java.util.HashSet<>();

        @Override
        public Result check(String colorValue) {
            if (colorValue == null) {
                return Result.rejected(RejectionReason.BLANK);
            }
            String trimmed = colorValue.trim();
            if (trimmed.isEmpty()) {
                return Result.rejected(RejectionReason.BLANK);
            }
            if (reject.contains(trimmed)) {
                return Result.rejected(RejectionReason.PLACEHOLDER);
            }
            if (trimmed.length() > MAX_COLOR_LENGTH) {
                return Result.rejected(RejectionReason.TOO_LONG);
            }
            return Result.allowed();
        }
    }

    private static final class FakeColorApi implements ColorImportResolutionApi {

        private final boolean automatic;
        private boolean canSave = true;
        private final Map<String, SourceColorResolution> resolutions = new HashMap<>();
        private final AtomicInteger resolveCalls = new AtomicInteger();

        private FakeColorApi(boolean automatic) {
            this.automatic = automatic;
        }

        @Override
        public boolean isAutomaticMatchingEnabled() {
            return automatic;
        }

        @Override
        public List<SourceColorResolution> resolveSourceTexts(List<String> sourceTexts) {
            resolveCalls.incrementAndGet();
            List<SourceColorResolution> result = new ArrayList<>();
            for (String text : sourceTexts) {
                SourceColorResolution resolution = resolutions.get(text);
                result.add(
                        resolution == null
                                ? SourceColorResolution.noMapping(text, text.toLowerCase())
                                : resolution);
            }
            return result;
        }

        @Override
        public List<ColorDto> listActiveColors() {
            return List.of(
                    new ColorDto(CAT_BELYI, "Белый", true, 1),
                    new ColorDto(CAT_RAL7016, "RAL7016", true, 1),
                    new ColorDto(CAT_RAL7016_PIPE_30, "RAL7016|30", true, 1));
        }

        @Override
        public ResolvedColorPair composePair(UUID insideColorId, UUID outsideColorId) {
            if (insideColorId.equals(CAT_BELYI) && outsideColorId.equals(CAT_RAL7016)) {
                return pairBelyiRal7016();
            }
            if (insideColorId.equals(CAT_BELYI) && outsideColorId.equals(CAT_RAL7016_PIPE_30)) {
                return new ResolvedColorPair(CAT_BELYI, CAT_RAL7016_PIPE_30, "Белый", "RAL7016|30", "Белый/RAL7016|30");
            }
            throw new IllegalArgumentException("unknown pair");
        }

        @Override
        public boolean canSaveGlobalMappings() {
            return canSave;
        }
    }
}