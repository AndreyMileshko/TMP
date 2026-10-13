package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SpecificationWarehouseQuantityCalculatorTest {

    @Test
    void test004_onePiece1226mmTimesTwelveProductsIs14712Meters() {
        // Product qty 12; detail count 1; length 1226 mm; UoM м.
        BigDecimal demand =
                SpecificationWarehouseQuantityCalculator.warehouseDemand(
                        BigDecimal.ONE, new BigDecimal("1226"), "м.", 12L);
        assertEquals(0, new BigDecimal("14.712000").compareTo(demand));
        assertEquals(
                0,
                new BigDecimal("12")
                        .compareTo(
                                SpecificationWarehouseQuantityCalculator.technologicalPieceCount(
                                        demand, new BigDecimal("1226"), "м.")));
    }

    @Test
    void multiplePiecesOfOneLength() {
        // 3 pieces × 1226 mm × 12 products / 1000 = 44.136 m
        BigDecimal demand =
                SpecificationWarehouseQuantityCalculator.warehouseDemand(
                        new BigDecimal("3"), new BigDecimal("1226"), "м.", 12L);
        assertEquals(0, new BigDecimal("44.136000").compareTo(demand));
    }

    @Test
    void meterWithoutLengthRemainsLineQuantityTimesProducts() {
        BigDecimal demand =
                SpecificationWarehouseQuantityCalculator.warehouseDemand(
                        new BigDecimal("4"), null, "м.", 10L);
        assertEquals(0, new BigDecimal("40.000000").compareTo(demand));
        assertNull(
                SpecificationWarehouseQuantityCalculator.technologicalPieceCount(
                        demand, null, "м."));
    }

    @Test
    void nonMeterUomUnchangedEvenWithLengthMm() {
        BigDecimal demand =
                SpecificationWarehouseQuantityCalculator.warehouseDemand(
                        new BigDecimal("2"), new BigDecimal("1226"), "шт.", 12L);
        assertEquals(0, new BigDecimal("24.000000").compareTo(demand));
        assertFalse(
                SpecificationWarehouseQuantityCalculator.isDimensionalMeter(
                        "шт.", new BigDecimal("1226")));
    }

    @Test
    void meterAliasesUseCanonicalUomMatching() {
        assertTrue(SpecificationWarehouseQuantityCalculator.isMeters("м"));
        assertTrue(SpecificationWarehouseQuantityCalculator.isMeters("метры"));
        BigDecimal demand =
                SpecificationWarehouseQuantityCalculator.warehouseDemand(
                        BigDecimal.ONE, new BigDecimal("1000"), "метр", 1L);
        assertEquals(0, new BigDecimal("1.000000").compareTo(demand));
    }

    @Test
    void perProductWarehouseQuantityForRuleA() {
        BigDecimal perProduct =
                SpecificationWarehouseQuantityCalculator.warehouseQuantityPerProduct(
                        BigDecimal.ONE, new BigDecimal("1226"), "м.");
        assertEquals(0, new BigDecimal("1.226000").compareTo(perProduct));
    }
}
