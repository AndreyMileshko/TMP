package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class WarehouseDemandResupplyWaiverRulesTest {

    @Test
    void waivesLinkedCappedByHeadroom() {
        assertEquals(
                0,
                WarehouseDemandResupplyWaiverRules.computeWaiveQuantity(
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO)
                        .compareTo(new BigDecimal("10")));
        assertEquals(
                0,
                WarehouseDemandResupplyWaiverRules.computeWaiveQuantity(
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                new BigDecimal("2"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO)
                        .compareTo(new BigDecimal("8")));
    }

    @Test
    void reservesOtherActiveLinkedQuantity() {
        assertEquals(
                0,
                WarehouseDemandResupplyWaiverRules.computeWaiveQuantity(
                                new BigDecimal("10"),
                                new BigDecimal("10"),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                new BigDecimal("4"))
                        .compareTo(new BigDecimal("6")));
    }

    @Test
    void zeroWhenNoHeadroom() {
        assertEquals(
                0,
                WarehouseDemandResupplyWaiverRules.computeWaiveQuantity(
                                new BigDecimal("5"),
                                new BigDecimal("10"),
                                new BigDecimal("6"),
                                new BigDecimal("4"),
                                BigDecimal.ZERO)
                        .compareTo(BigDecimal.ZERO));
    }
}