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
    void normativeExampleTenSeventeenPartialReleases() {
        BigDecimal q = new BigDecimal("17");
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
    void normativeRoundingClosureThreeOne() {
        BigDecimal q = BigDecimal.ONE;
        long n = 3;

        LinePlan first = calculator.calculate(new Input(q, n, 0, 1));
        assertEquals(new BigDecimal("0.333333"), first.planCurrent());

        LinePlan second = calculator.calculate(new Input(q, n, 1, 1));
        assertEquals(new BigDecimal("0.333334"), second.planCurrent());

        LinePlan third = calculator.calculate(new Input(q, n, 2, 1));
        assertEquals(new BigDecimal("0.333333"), third.planCurrent());
        assertEquals(new BigDecimal("1.000000"), third.cumulativeAfter());

        BigDecimal total =
                first.planCurrent().add(second.planCurrent()).add(third.planCurrent());
        assertEquals(new BigDecimal("1.000000"), total);
    }

    @Test
    void finalReleaseUsesExactLineQuantityNotRoundedRatio() {
        BigDecimal q = new BigDecimal("1");
        long n = 3;
        LinePlan finalRelease = calculator.calculate(new Input(q, n, 2, 1));
        assertEquals(new BigDecimal("1.000000"), finalRelease.cumulativeAfter());
    }
}
