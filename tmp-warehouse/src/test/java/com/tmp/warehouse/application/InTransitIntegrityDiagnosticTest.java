package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.application.InTransitIntegrityDiagnostic.ExplainedOutstanding;
import com.tmp.warehouse.application.InTransitIntegrityDiagnostic.OrphanBalance;
import com.tmp.warehouse.application.InTransitIntegrityDiagnostic.RecordedInTransit;
import com.tmp.warehouse.application.InTransitIntegrityDiagnostic.Report;
import com.tmp.warehouse.application.InTransitIntegrityDiagnostic.StockKey;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InTransitIntegrityDiagnosticTest {

    @Test
    void balancedCompletedTransferProducesNoOrphan() {
        Report report =
                InTransitIntegrityDiagnostic.analyze(List.of(), List.of(), List.of());
        assertFalse(report.hasOrphans());
        assertTrue(report.recordedPositions().isEmpty());
    }

    @Test
    void legitimateDocumentOutstandingIsNotFlagged() {
        StockKey key = newKey();
        UUID positionId = UUID.randomUUID();
        Report report =
                InTransitIntegrityDiagnostic.analyze(
                        List.of(
                                new RecordedInTransit(
                                        key, new BigDecimal("2.000"), positionId, "pcs")),
                        List.of(new ExplainedOutstanding(key, new BigDecimal("2.000"))),
                        List.of());
        assertFalse(report.hasOrphans());
    }

    @Test
    void legitimateLegacyContextOutstandingIsNotFlagged() {
        StockKey key = newKey();
        UUID positionId = UUID.randomUUID();
        Report report =
                InTransitIntegrityDiagnostic.analyze(
                        List.of(
                                new RecordedInTransit(
                                        key, new BigDecimal("1.500"), positionId, "pcs")),
                        List.of(),
                        List.of(new ExplainedOutstanding(key, new BigDecimal("1.500"))));
        assertFalse(report.hasOrphans());
    }

    @Test
    void unlinkedImmediateLegacySendIsFlaggedAsOrphan() {
        StockKey key = newKey();
        UUID positionId = UUID.randomUUID();
        Report report =
                InTransitIntegrityDiagnostic.analyze(
                        List.of(
                                new RecordedInTransit(
                                        key, new BigDecimal("3.000"), positionId, "pcs")),
                        List.of(),
                        List.of());
        assertTrue(report.hasOrphans());
        assertEquals(1, report.orphans().size());
        OrphanBalance orphan = report.orphans().get(0);
        assertEquals(0, orphan.orphanQuantity().compareTo(new BigDecimal("3.000")));
        assertEquals(positionId, orphan.stockPositionId());
        assertEquals("pcs", orphan.unitOfMeasure());
    }

    @Test
    void historicalOrphansReportedSeparatelyWithoutModification() {
        StockKey a01 = newKey();
        StockKey d01 = new StockKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID posA = UUID.randomUUID();
        UUID posD = UUID.randomUUID();
        Report report =
                InTransitIntegrityDiagnostic.analyze(
                        List.of(
                                new RecordedInTransit(a01, new BigDecimal("3"), posA, "pcs"),
                                new RecordedInTransit(d01, new BigDecimal("0.5"), posD, "pcs")),
                        List.of(),
                        List.of());
        assertEquals(2, report.orphans().size());
        assertEquals(0, report.orphans().get(0).orphanQuantity().compareTo(new BigDecimal("3")));
        assertEquals(0, report.orphans().get(1).orphanQuantity().compareTo(new BigDecimal("0.5")));
        assertEquals(2, report.recordedPositions().size());
    }

    private static StockKey newKey() {
        return new StockKey(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}