package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductionQuantityTest {

    @Test
    void positiveQuantityMustBeGreaterThanZero() {
        assertEquals(ProductionQuantity.positive(5), ProductionQuantity.positive(5));
        assertThrows(IllegalArgumentException.class, () -> ProductionQuantity.positive(0));
        assertThrows(IllegalArgumentException.class, () -> ProductionQuantity.positive(-1));
    }

    @Test
    void nonNegativeQuantityAllowsZero() {
        assertEquals(ProductionQuantity.zero(), ProductionQuantity.nonNegative(0));
        assertThrows(IllegalArgumentException.class, () -> ProductionQuantity.nonNegative(-1));
    }

    @Test
    void fractionalQuantityIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionQuantity.positive(BigDecimal.valueOf(1.5)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionQuantity.nonNegative(BigDecimal.valueOf(0.1)));
    }

    @Test
    void plusAndMinusPreserveWholeNumberRules() {
        ProductionQuantity five = ProductionQuantity.positive(5);
        ProductionQuantity two = ProductionQuantity.nonNegative(2);
        assertEquals(ProductionQuantity.nonNegative(7), five.plus(two));
        assertEquals(ProductionQuantity.nonNegative(3), five.minus(two));
    }

    @Test
    void minusBelowZeroIsRejected() {
        ProductionQuantity one = ProductionQuantity.nonNegative(1);
        ProductionQuantity two = ProductionQuantity.nonNegative(2);
        assertThrows(IllegalArgumentException.class, () -> one.minus(two));
    }

    @Test
    void comparisonHelpersWork() {
        ProductionQuantity three = ProductionQuantity.positive(3);
        ProductionQuantity five = ProductionQuantity.positive(5);
        assertTrue(three.isLessThan(five));
        assertTrue(three.isLessThanOrEqualTo(five));
    }
}
