package com.tmp.ui.shell.screen.orderimport.colors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

class ColorResolutionCoordinatorTest {

    private static final UUID CAT_BELYI = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CAT_RAL7016 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAT_RAL7016_PIPE_30 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID CAT_SERYY = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void scenario01_offReturnsSameBatchList() {
        FakeColorApi api = new FakeColorApi(false);
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        List<OrderImportBatch> batches = List.of(sampleBatch("ORD-1", "Цвет", null));
        ColorResolutionCoordinator.Preparation prep = coordinator.prepare(batches);
        assertFalse(prep.automaticMatchingEnabled());
        assertFalse(prep.needsResolutionWindow());
        assertNull(prep.session());
        assertSame(batches, prep.readyBatches());
    }

    @Test
    void scenario02_offDoesNotCallResolve() {
        FakeColorApi api = new FakeColorApi(false);
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", "RAL7016")));
        assertEquals(0, api.resolveCalls.get());
        assertEquals(1, api.matchingEnabledCalls.get());
    }

    @Test
    void scenario03_onKnownColorsPrefilled() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionCoordinator.Preparation prep =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Белый", null)));
        assertTrue(prep.automaticMatchingEnabled());
        assertFalse(prep.needsResolutionWindow());
        assertNotNull(prep.session());
        assertNotNull(prep.readyBatches());
        assertEquals("Белый/RAL7016", prep.readyBatches().get(0).positions().get(0).specificationLines().get(0).color());
    }

    @Test
    void scenario04_unknownRequiresChoiceNoReadyBatches() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionCoordinator.Preparation prep =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null)));
        assertTrue(prep.needsResolutionWindow());
        assertNull(prep.readyBatches());
        assertEquals(ColorResolutionRow.UserState.NEEDS_CHOICE, prep.session().rows().get(0).userState());
    }

    @Test
    void scenario05_mappingInactiveRequiresChoice() {
        FakeColorApi api = inactiveApi("Серый", "серый");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionCoordinator.Preparation prep =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Серый", null)));
        assertTrue(prep.needsResolutionWindow());
        assertFalse(prep.session().rows().get(0).saveMappingAvailable());
    }

    @Test
    void scenario06_blankColorNotResolvedStaysNull() {
        FakeColorApi api = matchedApi(Map.of());
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        coordinator.prepare(List.of(sampleBatch("ORD-1", " ", null)));
        assertEquals(0, api.resolveCalls.get());
    }

    @Test
    void scenario07_slashColorPassedWholeToApi() {
        FakeColorApi api = unmatchedApi("RAL7016/30т", "ral7016/30т");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        coordinator.prepare(List.of(sampleBatch("ORD-1", "RAL7016/30т", null)));
        assertEquals(List.of("RAL7016/30т"), api.lastResolvedTexts);
    }

    @Test
    void scenario08_bezCvetaHandledGenerically() {
        FakeColorApi api = unmatchedApi("Без цвета", "без цвета");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionCoordinator.Preparation prep =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Без цвета", null)));
        assertTrue(prep.needsResolutionWindow());
        assertEquals("без цвета", prep.session().rows().get(0).sourceKey());
    }

    @Test
    void scenario09_sameTextTwoProductsIndependentRows() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                OrderImportBatch.of(
                        "STXT",
                        "f.stxt",
                        "chk",
                        "ORD-1",
                        LocalDate.of(2026, 1, 1),
                        null,
                        "Client",
                        List.of(
                                positionWithColor("1", "P1", "Цвет"),
                                positionWithColor("2", "P2", "Цвет")));
        ColorResolutionCoordinator.Preparation prep = coordinator.prepare(List.of(batch));
        assertEquals(2, prep.session().rows().size());
        assertEquals(new ColorResolutionRow.Id(0, 0, 0), prep.session().rows().get(0).id());
        assertEquals(new ColorResolutionRow.Id(0, 1, 0), prep.session().rows().get(1).id());
    }

    @Test
    void scenario10_twoLinesSameProductDifferentPairsAllowed() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportPosition position =
                OrderImportPosition.of(
                        "1",
                        "P1",
                        "Item",
                        1,
                        List.of(
                                line("M1", "Цвет"),
                                line("M2", "Цвет")));
        OrderImportBatch batch = batchWithPositions("ORD-1", List.of(position));
        ColorResolutionCoordinator.Preparation prep = coordinator.prepare(List.of(batch));
        ColorResolutionSession session = prep.session();
        session = session.withSelection(Set.of(new ColorResolutionRow.Id(0, 0, 0)));
        session = session.applyCatalogPairToHighlighted(CAT_BELYI, CAT_RAL7016);
        session = session.withSelection(Set.of(new ColorResolutionRow.Id(0, 0, 1)));
        session = session.applyCatalogPairToHighlighted(CAT_RAL7016, CAT_BELYI);
        assertEquals("Белый/RAL7016", session.rows().get(0).finalColor());
        assertEquals("RAL7016/Белый", session.rows().get(1).finalColor());
    }

    @Test
    void scenario11_manualOverrideMatchedBecomesManuallyChanged() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Белый", null))).session();
        session = session.applyCatalogPair(List.of(session.rows().get(0).id()), CAT_BELYI, CAT_RAL7016_PIPE_30);
        assertEquals(ColorResolutionRow.UserState.MANUALLY_CHANGED, session.rows().get(0).userState());
    }

    @Test
    void scenario12_massApplyPairToHighlighted() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch = batchWithPositions("ORD-1", List.of(positionWithColor("1", "P1", "Цвет"), positionWithColor("2", "P2", "Цвет")));
        ColorResolutionSession session = coordinator.prepare(List.of(batch)).session();
        session = session.withSelection(Set.of(new ColorResolutionRow.Id(0, 0, 0)));
        session = session.applyCatalogPairToHighlighted(CAT_BELYI, CAT_RAL7016);
        assertEquals("Белый/RAL7016", session.rows().get(0).finalColor());
        assertNull(session.rows().get(1).finalColor());
    }

    @Test
    void scenario13_massApplyBySourceKey() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch = batchWithPositions("ORD-1", List.of(positionWithColor("1", "P1", "Цвет"), positionWithColor("2", "P2", "Цвет")));
        ColorResolutionSession session = coordinator.prepare(List.of(batch)).session();
        session = session.applyCatalogPairToSourceKey("цвет", CAT_BELYI, CAT_RAL7016);
        assertEquals("Белый/RAL7016", session.rows().get(0).finalColor());
        assertEquals("Белый/RAL7016", session.rows().get(1).finalColor());
    }

    @Test
    void scenario14_sourceTextKeepsOriginalSpelling() {
        FakeColorApi api = unmatchedApi("RAL 7016", "ral 7016");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "RAL 7016", null))).session();
        session =
                session.withSelection(Set.of(session.rows().get(0).id())).keepSourceTextForHighlighted();
        assertEquals("RAL 7016", session.rows().get(0).finalColor());
        assertNotEquals(session.rows().get(0).sourceKey(), session.rows().get(0).finalColor());
    }

    @Test
    void scenario15_customColorNotCandidate() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        session =
                session.withSelection(Set.of(session.rows().get(0).id()))
                        .applyCustomColorToHighlighted("Мой цвет");
        assertTrue(session.mappingSaveCandidates().isEmpty());
    }

    @Test
    void scenario16_constraintErrorBlocksRebuild() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        FakeConstraint constraint = new FakeConstraint();
        constraint.reject.add("bad");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, constraint);
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        session =
                session.withSelection(Set.of(session.rows().get(0).id())).applyCustomColorToHighlighted("bad");
        assertThrows(IllegalStateException.class, session::rebuild);
    }

    @Test
    void scenario17_rebuildPreservesOrderAndFields() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                OrderImportBatch.of(
                        "STXT",
                        "ref",
                        "sum",
                        "ORD-9",
                        LocalDate.of(2026, 2, 2),
                        LocalDate.of(2026, 3, 3),
                        "ACME",
                        List.of(
                                OrderImportPosition.of(
                                        "10",
                                        "PC",
                                        "Name",
                                        2,
                                        List.of(
                                                OrderImportSpecificationLine.of(
                                                        "M",
                                                        "Mat",
                                                        "Белый",
                                                        BigDecimal.ONE,
                                                        "шт",
                                                        BigDecimal.TEN)))));
        List<OrderImportBatch> rebuilt =
                coordinator.prepare(List.of(batch)).readyBatches();
        assertNotNull(rebuilt);
        OrderImportBatch out = rebuilt.get(0);
        assertEquals("ORD-9", out.orderNumber());
        assertEquals("ACME", out.customerName());
        assertEquals("10", out.positions().get(0).externalPositionNumber());
        assertEquals("Белый/RAL7016", out.positions().get(0).specificationLines().get(0).color());
        assertEquals(BigDecimal.TEN, out.positions().get(0).specificationLines().get(0).quantity());
    }

    @Test
    void scenario18_conflictingPairsDoNotBlockReadiness() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch = batchWithPositions("ORD-1", List.of(positionWithColor("1", "P1", "Цвет"), positionWithColor("2", "P2", "Цвет")));
        ColorResolutionSession session = coordinator.prepare(List.of(batch)).session();
        session = session.applyCatalogPair(List.of(new ColorResolutionRow.Id(0, 0, 0)), CAT_BELYI, CAT_RAL7016);
        session = session.applyCatalogPair(List.of(new ColorResolutionRow.Id(0, 1, 0)), CAT_BELYI, CAT_RAL7016_PIPE_30);
        assertEquals(1, session.mappingSaveConflicts().size());
        assertTrue(session.isReady());
    }

    @Test
    void scenario19_identicalPairsDeduplicated() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch = batchWithPositions("ORD-1", List.of(positionWithColor("1", "P1", "Цвет"), positionWithColor("2", "P2", "Цвет")));
        ColorResolutionSession session = coordinator.prepare(List.of(batch)).session();
        session = session.applyCatalogPairToSourceKey("цвет", CAT_BELYI, CAT_RAL7016);
        assertEquals(1, session.mappingSaveCandidates().size());
    }

    @Test
    void scenario20_matchedAndInactiveNeverCandidates() {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put("Known", matched("Known", "known", pairBelyiRal7016()));
        api.resolutions.put("Inactive", inactive("Inactive", "inactive"));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch =
                batchWithPositions(
                        "ORD-1",
                        List.of(
                                positionWithColor("1", "P1", "Known"),
                                positionWithColor("2", "P2", "Inactive")));
        ColorResolutionSession session = coordinator.prepare(List.of(batch)).session();
        session = session.applyCatalogPair(List.of(session.rows().get(1).id()), CAT_BELYI, CAT_RAL7016);
        assertTrue(session.mappingSaveCandidates().isEmpty());
    }

    @Test
    void scenario21_unknownUnfilledNoBlankErrorNotReady() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionRow row =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session().rows().get(0);
        assertNull(row.constraintResult());
        assertFalse(row.isRowReady());
    }

    @Test
    void scenario22_emptyCustomColorBlankError() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        session =
                session.withSelection(Set.of(session.rows().get(0).id())).applyCustomColorToHighlighted("   ");
        assertEquals(
                SpecificationColorConstraint.RejectionReason.BLANK,
                session.rows().get(0).constraintResult().rejectionReason());
    }

    @Test
    void scenario23_manualUnknownResolutionManuallyChanged() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        session = session.applyCatalogPair(List.of(session.rows().get(0).id()), CAT_BELYI, CAT_RAL7016);
        assertEquals(ColorResolutionRow.UserState.MANUALLY_CHANGED, session.rows().get(0).userState());
    }

    @Test
    void scenario24_changingAutoPairManuallyChanged() {
        FakeColorApi api = matchedApi(Map.of("Белый", pairBelyiRal7016()));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Белый", null))).session();
        session = session.applyCatalogPair(List.of(session.rows().get(0).id()), CAT_BELYI, CAT_RAL7016_PIPE_30);
        assertEquals(ColorResolutionRow.UserState.MANUALLY_CHANGED, session.rows().get(0).userState());
    }

    @Test
    void scenario25_noSavePermissionStillResolvesWithoutCandidates() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        api.canSave = false;
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        session = session.applyCatalogPair(List.of(session.rows().get(0).id()), CAT_BELYI, CAT_RAL7016);
        assertTrue(session.isReady());
        assertTrue(session.mappingSaveCandidates().isEmpty());
    }

    @Test
    void scenario26_composePairExceptionLeavesSessionUnchanged() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        api.composeThrows = true;
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession before =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        ColorResolutionRow.Id rowId = before.rows().get(0).id();
        assertThrows(
                IllegalArgumentException.class,
                () -> before.withSelection(Set.of(rowId))
                        .applyCatalogPairToHighlighted(CAT_BELYI, CAT_RAL7016));
        assertNull(before.rows().get(0).finalColor());
        assertEquals(ColorResolutionRow.UserState.NEEDS_CHOICE, before.rows().get(0).userState());
    }

    @Test
    void scenario27_resolveFailureNoPartialPreparation() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        api.resolveThrows = true;
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        assertThrows(
                RuntimeException.class,
                () -> coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))));
    }

    @Test
    void scenario28_invalidAutoMatchedNeedsWindow() {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put(
                "Bad",
                SourceColorResolution.matched(
                        "Bad",
                        "bad",
                        new ResolvedColorPair(CAT_BELYI, CAT_RAL7016, "X", "Y", "!!invalid!!")));
        FakeConstraint constraint = new FakeConstraint();
        constraint.reject.add("!!invalid!!");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, constraint);
        ColorResolutionCoordinator.Preparation prep =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Bad", null)));
        assertTrue(prep.needsResolutionWindow());
        assertNull(prep.readyBatches());
        assertEquals(ColorResolutionRow.UserState.MATCHED, prep.session().rows().get(0).userState());
    }

    @Test
    void scenario29_conflictDoesNotBlockRebuild() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportBatch batch = batchWithPositions("ORD-1", List.of(positionWithColor("1", "P1", "Цвет"), positionWithColor("2", "P2", "Цвет")));
        ColorResolutionSession session = coordinator.prepare(List.of(batch)).session();
        session = session.applyCatalogPair(List.of(new ColorResolutionRow.Id(0, 0, 0)), CAT_BELYI, CAT_RAL7016);
        session = session.applyCatalogPair(List.of(new ColorResolutionRow.Id(0, 1, 0)), CAT_BELYI, CAT_RAL7016_PIPE_30);
        assertDoesNotThrow(session::rebuild);
    }

    @Test
    void scenario30_explicitlyDisabledSaveFlagStaysOffWhileAvailable() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        session = session.applyCatalogPair(List.of(session.rows().get(0).id()), CAT_BELYI, CAT_RAL7016);
        session =
                session.withSelection(Set.of(session.rows().get(0).id()))
                        .setSaveMappingForAvailableHighlighted(false)
                        .applyCatalogPairToHighlighted(CAT_BELYI, CAT_RAL7016_PIPE_30);
        assertFalse(session.rows().get(0).saveMappingSelected());
        assertTrue(session.rows().get(0).saveMappingAvailable());
    }

    @Test
    void autoMatchedPairWithPipeModificationPassesThroughCoordinator() {
        ResolvedColorPair pairWithModification =
                catalogPair(CAT_BELYI, "Белый", CAT_RAL7016_PIPE_30, "RAL7016|30");
        FakeColorApi api = matchedApi(Map.of("Mod", pairWithModification));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionCoordinator.Preparation prep =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Mod", null)));
        assertEquals("Белый/RAL7016|30", prep.readyBatches().get(0).positions().get(0).specificationLines().get(0).color());
    }

    @Test
    void composePairCollapsesEqualCatalogCodesToSingleDisplay() {
        FakeColorApi api = unmatchedApi("Цвет", "цвет");
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        ColorResolutionSession session =
                coordinator.prepare(List.of(sampleBatch("ORD-1", "Цвет", null))).session();
        session = session.applyCatalogPair(List.of(session.rows().get(0).id()), CAT_BELYI, CAT_BELYI);
        assertEquals("Белый", session.rows().get(0).finalColor());
    }

    @Test
    void resolveCalledOnceWithDistinctTextsInOrder() {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put("A", unmatched("A", "a"));
        api.resolutions.put("B", unmatched("B", "b"));
        ColorResolutionCoordinator coordinator = new ColorResolutionCoordinator(api, new FakeConstraint());
        OrderImportPosition position =
                OrderImportPosition.of(
                        "1",
                        "P",
                        "N",
                        1,
                        List.of(line("M1", "A"), line("M2", "B"), line("M3", "A")));
        coordinator.prepare(List.of(batchWithPositions("ORD", List.of(position))));
        assertEquals(1, api.resolveCalls.get());
        assertEquals(List.of("A", "B"), api.lastResolvedTexts);
    }

    private static void assertDoesNotThrow(Runnable action) {
        action.run();
    }

    private static OrderImportBatch sampleBatch(String orderNumber, String color, String color2) {
        List<OrderImportPosition> positions = new ArrayList<>();
        positions.add(positionWithColor("1", "P1", color));
        if (color2 != null) {
            positions.add(positionWithColor("2", "P2", color2));
        }
        return batchWithPositions(orderNumber, positions);
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

    private static String composeDisplay(String insideCode, String outsideCode) {
        if (insideCode.equals(outsideCode)) {
            return insideCode;
        }
        return insideCode + "/" + outsideCode;
    }

    private static ResolvedColorPair catalogPair(
            UUID insideId, String insideCode, UUID outsideId, String outsideCode) {
        return new ResolvedColorPair(
                insideId, outsideId, insideCode, outsideCode, composeDisplay(insideCode, outsideCode));
    }

    private static ResolvedColorPair pairBelyiRal7016() {
        return catalogPair(CAT_BELYI, "Белый", CAT_RAL7016, "RAL7016");
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

    private static SourceColorResolution matched(String text, String key, ResolvedColorPair pair) {
        return SourceColorResolution.matched(text, key, pair);
    }

    private static SourceColorResolution unmatched(String text, String key) {
        return SourceColorResolution.noMapping(text, key);
    }

    private static SourceColorResolution inactive(String text, String key) {
        return SourceColorResolution.mappingInactive(text, key);
    }

    private static FakeColorApi inactiveApi(String text, String key) {
        FakeColorApi api = new FakeColorApi(true);
        api.resolutions.put(text, inactive(text, key));
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
        private boolean resolveThrows;
        private boolean composeThrows;
        private final Map<String, SourceColorResolution> resolutions = new HashMap<>();
        private final AtomicInteger resolveCalls = new AtomicInteger();
        private final AtomicInteger matchingEnabledCalls = new AtomicInteger();
        private List<String> lastResolvedTexts = List.of();

        private FakeColorApi(boolean automatic) {
            this.automatic = automatic;
        }

        @Override
        public boolean isAutomaticMatchingEnabled() {
            matchingEnabledCalls.incrementAndGet();
            return automatic;
        }

        @Override
        public List<SourceColorResolution> resolveSourceTexts(List<String> sourceTexts) {
            resolveCalls.incrementAndGet();
            lastResolvedTexts = List.copyOf(sourceTexts);
            if (resolveThrows) {
                throw new IllegalStateException("resolve failed");
            }
            List<SourceColorResolution> result = new ArrayList<>();
            for (String text : sourceTexts) {
                SourceColorResolution resolution = resolutions.get(text);
                if (resolution == null) {
                    result.add(SourceColorResolution.noMapping(text, text.toLowerCase()));
                } else {
                    result.add(resolution);
                }
            }
            return result;
        }

        @Override
        public List<ColorDto> listActiveColors() {
            return List.of(
                    new ColorDto(CAT_BELYI, "Белый", true, 1),
                    new ColorDto(CAT_RAL7016, "RAL7016", true, 1),
                    new ColorDto(CAT_RAL7016_PIPE_30, "RAL7016|30", true, 1),
                    new ColorDto(CAT_SERYY, "Серый", true, 1));
        }

        @Override
        public ResolvedColorPair composePair(UUID insideColorId, UUID outsideColorId) {
            if (composeThrows) {
                throw new IllegalArgumentException("compose failed");
            }
            if (insideColorId.equals(CAT_BELYI) && outsideColorId.equals(CAT_RAL7016)) {
                return pairBelyiRal7016();
            }
            if (insideColorId.equals(CAT_RAL7016) && outsideColorId.equals(CAT_BELYI)) {
                return catalogPair(CAT_RAL7016, "RAL7016", CAT_BELYI, "Белый");
            }
            if (insideColorId.equals(CAT_BELYI) && outsideColorId.equals(CAT_BELYI)) {
                return catalogPair(CAT_BELYI, "Белый", CAT_BELYI, "Белый");
            }
            if (insideColorId.equals(CAT_BELYI) && outsideColorId.equals(CAT_RAL7016_PIPE_30)) {
                return catalogPair(CAT_BELYI, "Белый", CAT_RAL7016_PIPE_30, "RAL7016|30");
            }
            throw new IllegalArgumentException("unknown catalog pair for test fake");
        }

        @Override
        public boolean canSaveGlobalMappings() {
            return canSave;
        }
    }
}