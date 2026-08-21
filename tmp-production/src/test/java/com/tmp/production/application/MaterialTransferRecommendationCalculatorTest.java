package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MaterialTransferRecommendationCalculatorTest {

    private final MaterialTransferRecommendationCalculator calculator =
            new MaterialTransferRecommendationCalculator();

    @Test
    void caseA_productionStockDeductedFromRequired() {
        MaterialTransferRecommendationCalculator.Recommendation result =
                calculator.calculate(bd(10), bd(4), bd(20));
        assertEquals(0, result.recommendedTransferQuantity().compareTo(bd(6)));
        assertEquals(0, result.uncoveredDeficit().compareTo(BigDecimal.ZERO));
    }

    @Test
    void caseB_mainWarehouseCapsRecommendationAndExposesUncoveredDeficit() {
        MaterialTransferRecommendationCalculator.Recommendation result =
                calculator.calculate(bd(10), bd(2), bd(5));
        assertEquals(0, result.recommendedTransferQuantity().compareTo(bd(5)));
        assertEquals(0, result.uncoveredDeficit().compareTo(bd(3)));
    }

    @Test
    void caseC_alreadyOnProductionYieldsZeroRecommendation() {
        MaterialTransferRecommendationCalculator.Recommendation result =
                calculator.calculate(bd(10), bd(12), bd(20));
        assertEquals(0, result.recommendedTransferQuantity().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.uncoveredDeficit().compareTo(BigDecimal.ZERO));
    }

    @Test
    void caseD_fullTransferFromMain() {
        MaterialTransferRecommendationCalculator.Recommendation result =
                calculator.calculate(bd(10), bd(0), bd(10));
        assertEquals(0, result.recommendedTransferQuantity().compareTo(bd(10)));
        assertEquals(0, result.uncoveredDeficit().compareTo(BigDecimal.ZERO));
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }
}
