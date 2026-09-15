package com.tmp.ui.shell.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalQuantityParserTest {

    @Test
    void acceptsCommaAndDotDecimals() {
        assertEquals(new BigDecimal("0.5"), DecimalQuantityParser.parseRequired("0,5", "количество"));
        assertEquals(new BigDecimal("0.5"), DecimalQuantityParser.parseRequired("0.5", "количество"));
        assertEquals(new BigDecimal("1"), DecimalQuantityParser.parsePositive("1", "количество"));
        assertEquals(new BigDecimal("0.050"), DecimalQuantityParser.parsePositive("0,050", "количество"));
    }

    @Test
    void rejectsBlankInvalidZeroAndNegative() {
        IllegalArgumentException blank =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DecimalQuantityParser.parseRequired("  ", "количество"));
        assertTrue(blank.getMessage().contains("Укажите"));

        IllegalArgumentException invalid =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DecimalQuantityParser.parseRequired("abc", "количество"));
        assertTrue(invalid.getMessage().contains("Некорректное число"));

        IllegalArgumentException zero =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DecimalQuantityParser.parsePositive("0", "количество"));
        assertTrue(zero.getMessage().contains("больше 0"));

        IllegalArgumentException negative =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> DecimalQuantityParser.parseNonNegative("-1", "количество"));
        assertTrue(negative.getMessage().contains("отрицательным"));
    }

    @Test
    void tryParsePositiveReturnsNullForSoftFailures() {
        assertNull(DecimalQuantityParser.tryParsePositive(null));
        assertNull(DecimalQuantityParser.tryParsePositive(""));
        assertNull(DecimalQuantityParser.tryParsePositive("abc"));
        assertNull(DecimalQuantityParser.tryParsePositive("0"));
        assertNull(DecimalQuantityParser.tryParsePositive("-1"));
        assertEquals(new BigDecimal("0.5"), DecimalQuantityParser.tryParsePositive("0,5"));
    }
}
