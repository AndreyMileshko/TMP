package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.production.application.PartialReleaseMaterialPlanCalculator.Input;
import com.tmp.production.application.PartialReleaseMaterialPlanCalculator.LinePlan;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PartialReleaseMaterialPlanCalculatorTest {

    private final PartialReleaseMaterialPlanCalculator calculator =
            new PartialReleaseMaterialPlanCalculator();

    @Test
    void perProductNormTimesReleaseQuantity() {
        // Q = 2 per product; release 115 of 115 → required 230
        LinePlan plan = calculator.calculate(new Input(new BigDecimal("2"), 115, 0, 115));
        assertEquals(new BigDecimal("230.000000"), plan.planCurrent());
        assertEquals(new BigDecimal("230.000000"), plan.cumulativeAfter());
    }

    @Test
    void partialRemainderUsesRemainingProductsOnly() {
        // N=10, releasedBefore=4, release remaining 6, Q=2 → 12
        LinePlan plan = calculator.calculate(new Input(new BigDecimal("2"), 10, 4, 6));
        assertEquals(new BigDecimal("12.000000"), plan.planCurrent());
        assertEquals(new BigDecimal("20.000000"), plan.cumulativeAfter());
    }

    @Test
    void selectedReleaseQuantityNotFullOrder() {
        // remaining 10, select 3, Q=2 → 6
        LinePlan plan = calculator.calculate(new Input(new BigDecimal("2"), 10, 0, 3));
        assertEquals(new BigDecimal("6.000000"), plan.planCurrent());
    }

    @Test
    void cumulativePartialReleasesCloseAtNormTimesOrdered() {
        // Q = 1.7 per product, N = 10 → total 17
        BigDecimal q = new BigDecimal("1.7");
        long n = 10;

        LinePlan first = calculator.calculate(new Input(q, n, 0, 3));
        assertEquals(new BigDecimal("5.100000"), first.planCurrent());
        assertEquals(new BigDecimal("5.100000"), first.cumulativeAfter());

        LinePlan second = calculator.calculate(new Input(q, n, 3, 4));
        assertEquals(new BigDecimal("6.800000"), second.planCurrent());
        assertEquals(new BigDecimal("11.900000"), second.cumulativeAfter());

        LinePlan third = calculator.calculate(new Input(q, n, 7, 3));
        assertEquals(new BigDecimal("5.100000"), third.planCurrent());
        assertEquals(new BigDecimal("17.000000"), third.cumulativeAfter());

        BigDecimal total =
                first.planCurrent().add(second.planCurrent()).add(third.planCurrent());
        assertEquals(new BigDecimal("17.000000"), total);
    }

    @Test
    void zeroReleasedBeforeDoesNotRequireFullOrderedQuantity() {
        LinePlan plan = calculator.calculate(new Input(new BigDecimal("2"), 10, 0, 1));
        assertEquals(new BigDecimal("2.000000"), plan.planCurrent());
    }
}
