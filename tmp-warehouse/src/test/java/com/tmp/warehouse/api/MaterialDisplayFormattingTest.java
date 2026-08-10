package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.MaterialDisplayFormatting;
import org.junit.jupiter.api.Test;

class MaterialDisplayFormattingTest {

    @Test
    void formatDescriptionJoinsNonBlankParts() {
        String formatted =
                MaterialDisplayFormatting.formatDescription(
                        "Профиль VEKA Softline", "Белый", "6000 мм", "шт");
        assertTrue(formatted.contains("Профиль VEKA Softline"));
        assertTrue(formatted.contains("Белый"));
        assertTrue(formatted.contains("6000 мм"));
        assertTrue(formatted.contains("шт"));
    }

    @Test
    void formatDescriptionSkipsBlankParts() {
        String formatted = MaterialDisplayFormatting.formatDescription("Glass", "", "", "m2");
        assertEquals("Glass | m2", formatted);
    }

    @Test
    void formatSizeFromLengthMmAppendsUnit() {
        assertEquals("6000 мм", MaterialDisplayFormatting.formatSizeFromLengthMm(
                new java.math.BigDecimal("6000.000")));
    }

    @Test
    void nonBlankOrEmptyTrimsAndReturnsEmptyForBlank() {
        assertEquals("ALU-6060", MaterialDisplayFormatting.nonBlankOrEmpty("  ALU-6060  "));
        assertEquals("", MaterialDisplayFormatting.nonBlankOrEmpty("   "));
    }

    @Test
    void requireArticleTrimsMaterialCode() {
        assertEquals("VEKA-103", MaterialDisplayFormatting.requireArticle("  VEKA-103  "));
    }
}
