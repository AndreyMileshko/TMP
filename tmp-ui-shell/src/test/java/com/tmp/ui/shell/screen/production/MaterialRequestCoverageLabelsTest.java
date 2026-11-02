package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaterialRequestCoverageLabelsTest {

    @Test
    void formatsAuthoritativeCoveredLabelForSinglePosition() {
        assertEquals(
                MaterialRequestCoverageLabels.COVERED_PREFIX + "3/5",
                MaterialRequestCoverageLabels.formatPrimary(3L, 5L));
    }

    @Test
    void noSubmittedRequestsShowsDash() {
        assertEquals(MaterialRequestCoverageLabels.EMPTY, MaterialRequestCoverageLabels.formatPrimary(0L, 5L));
        assertEquals(MaterialRequestCoverageLabels.EMPTY, MaterialRequestCoverageLabels.formatAggregated(0L, 10L));
    }

    @Test
    void aggregatesParentWithoutInventingRepeatedHistory() {
        String label = MaterialRequestCoverageLabels.formatAggregated(4L, 8L);
        assertEquals(MaterialRequestCoverageLabels.COVERED_PREFIX + "4/8", label);
        assertFalse(label.contains("\u041f\u043e\u0432\u0442\u043e\u0440\u043d\u043e"));
        assertFalse(label.startsWith("\u041f\u0435\u0440\u0432\u0438\u0447\u043d\u043e"));
        assertTrue(MaterialRequestCoverageLabels.hasCoverage(label));
    }

    @Test
    void rejectsNegativeOrderedQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MaterialRequestCoverageLabels.formatPrimary(1L, -1L));
    }

    @Test
    void fullyCoveredPositionStillShowsCoveredNotRequestable() {
        assertEquals(
                MaterialRequestCoverageLabels.COVERED_PREFIX + "1/1",
                MaterialRequestCoverageLabels.formatPrimary(1L, 1L));
    }

    @Test
    void tooltipExplainsCumulativeCoverageNotUniquePhysicalProducts() {
        String tip = MaterialRequestCoverageLabels.tooltipFor("\u0423\u0447\u0442\u0435\u043d\u043e: 3/5");
        assertTrue(tip.contains("\u0423\u0447\u0442\u0435\u043d\u043e: 3/5"));
        assertTrue(tip.contains(MaterialRequestCoverageLabels.COVERAGE_TOOLTIP));
        assertTrue(tip.contains("\u043d\u0430\u043a\u043e\u043f\u0438\u0442\u0435\u043b\u044c\u043d"));
        assertTrue(tip.contains("\u0443\u043d\u0438\u043a\u0430\u043b\u044c\u043d"));
        assertEquals(
                MaterialRequestCoverageLabels.COVERAGE_TOOLTIP,
                MaterialRequestCoverageLabels.tooltipFor(MaterialRequestCoverageLabels.EMPTY));
    }
}