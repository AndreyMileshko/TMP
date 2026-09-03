package com.tmp.ui.shell.order;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DecimalUiFormatTest {

    @Test
    void stripsTrailingZeros() {
        assertEquals("55", DecimalUiFormat.format(new BigDecimal("55.0000000")));
        assertEquals("310", DecimalUiFormat.format(new BigDecimal("310.0000000")));
        assertEquals("12780", DecimalUiFormat.format(new BigDecimal("12780.0000000")));
        assertEquals("8", DecimalUiFormat.format(new BigDecimal("8.000000")));
        assertEquals("14", DecimalUiFormat.format(new BigDecimal("14.000000")));
    }

    @Test
    void keepsSignificantFraction() {
        assertEquals("55.5", DecimalUiFormat.format(new BigDecimal("55.5")));
        assertEquals("2.5", DecimalUiFormat.format(new BigDecimal("2.500000")));
        assertEquals("0.125", DecimalUiFormat.format(new BigDecimal("0.125000")));
    }

    @Test
    void noScientificNotationForLargeWhole() {
        assertEquals("1000", DecimalUiFormat.format(new BigDecimal("1E+3")));
    }

    @Test
    void nullIsEmpty() {
        assertEquals("", DecimalUiFormat.format(null));
        assertEquals("", DecimalUiFormat.formatOptional(null));
    }
}
