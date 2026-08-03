package com.tmp.order.application.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UiProductQuantityNormalizationTest {

    @ParameterizedTest
    @ValueSource(strings = {"8", "8.0", "8.000000", "8.000"})
    void parsesScaledWholesToScaleZero(String raw) {
        BigDecimal value = UiProductQuantityNormalization.parseForOrderedQuantity(raw);
        assertEquals(0, new BigDecimal("8").compareTo(value));
        assertEquals(0, value.scale());
    }

    @Test
    void forUiContractStripsNumericScaleFromImportedQuantity() {
        BigDecimal ui = UiProductQuantityNormalization.forUiContract(new BigDecimal("8.000000"));
        assertEquals(0, ui.scale());
        assertEquals("8", ui.toPlainString());
    }

    @Test
    void forUiContractLeavesTrueFractionsUnchanged() {
        BigDecimal ui = UiProductQuantityNormalization.forUiContract(new BigDecimal("8.5"));
        assertEquals(0, new BigDecimal("8.5").compareTo(ui));
        assertEquals(1, ui.scale());
    }
}
