package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Stage 7 Phase 2: authoritative STANDARD coverage formula proofs.
 *
 * <pre>
 * outstanding = max(0, submitted - released)
 * requestable = max(0, active - outstanding)
 * </pre>
 */
class MaterialRequirementProductCoverageCalculatorTest {

    private MaterialRequirementProductCoverageCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new MaterialRequirementProductCoverageCalculator();
    }

    @ParameterizedTest
    @CsvSource({
        "10, 0, 0, 10",
        "10, 4, 0, 6",
        "6, 4, 4, 6",
        "6, 10, 4, 0",
        "0, 0, 0, 0",
        "0, 5, 5, 0"
    })
    void standardRequestableFormula(
            long active, long submitted, long released, long expectedRequestable) {
        assertEquals(
                expectedRequestable,
                calculator.requestableProductQuantity(active, submitted, released));
    }

    @Test
    void coverageRecordExposesOutstandingAndRequestable() {
        var coverage = calculator.coverage(10L, 10L, 0L, 4L);
        assertEquals(10L, coverage.orderedQuantity());
        assertEquals(10L, coverage.activeProductionQuantity());
        assertEquals(0L, coverage.releasedQuantity());
        assertEquals(4L, coverage.submittedProductCoverage());
        assertEquals(4L, coverage.outstandingSubmittedCoverage());
        assertEquals(6L, coverage.requestableProductQuantity());
    }

    @Test
    void outstandingIsZeroWhenReleasedExceedsSubmitted() {
        assertEquals(0L, calculator.outstandingSubmittedCoverage(4L, 10L));
        assertEquals(6L, calculator.requestableProductQuantity(6L, 4L, 10L));
    }

    @Test
    void rejectsNegativeInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.requestableProductQuantity(-1L, 0L, 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> calculator.outstandingSubmittedCoverage(0L, -1L));
    }
}
