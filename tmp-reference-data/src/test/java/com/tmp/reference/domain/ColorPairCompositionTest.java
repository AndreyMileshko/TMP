package com.tmp.reference.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ColorPairCompositionTest {

    @Test
    void sameCodesCollapseToSingleCode() {
        assertEquals("Белый", ColorPairComposition.compose("Белый", "Белый"));
        assertEquals("RAL7016|30", ColorPairComposition.compose("RAL7016|30", "RAL7016|30"));
    }

    @Test
    void differentCodesUseInsideSlashOutsideOrder() {
        assertEquals(
                "Белый/RAL7016|30",
                ColorPairComposition.compose("Белый", "RAL7016|30"));
        assertEquals(
                "RAL7016|30/Белый",
                ColorPairComposition.compose("RAL7016|30", "Белый"));
    }

    @Test
    void rejectsNullCodes() {
        assertThrows(
                NullPointerException.class, () -> ColorPairComposition.compose(null, "Белый"));
        assertThrows(
                NullPointerException.class, () -> ColorPairComposition.compose("Белый", null));
    }
}