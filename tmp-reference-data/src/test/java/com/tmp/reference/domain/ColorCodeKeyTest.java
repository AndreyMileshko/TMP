package com.tmp.reference.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ColorCodeKeyTest {

    @Test
    void uppercasesWithLocaleRootAndPreservesDelimiters() {
        assertEquals("RAL7016|30", ColorCodeKey.of("RAL7016|30").value());
        assertEquals("RAL7016|МУАР", ColorCodeKey.of("RAL7016|МУАР").value());
        assertEquals("KDB74-Z8|МАСС", ColorCodeKey.of("KDB74-Z8|МАСС").value());
        assertEquals("KDB74-Z8|ПРИТВОР", ColorCodeKey.of("KDB74-Z8|ПРИТВОР").value());
        assertEquals("БЕЛЫЙ", ColorCodeKey.of("Белый").value());
        assertEquals("00", ColorCodeKey.of("00").value());
        assertEquals("RAL7016", ColorCodeKey.of("ral7016").value());
    }

    @Test
    void doesNotCollapseOrRemoveSpaces() {
        assertEquals("RAL 7016", ColorCodeKey.of("RAL 7016").value());
        assertNotEquals(ColorCodeKey.of("RAL 7016").value(), ColorCodeKey.of("RAL7016").value());
    }

    @Test
    void doesNotReplaceYo() {
        assertEquals("Ё", ColorCodeKey.of("ё").value());
        assertNotEquals("Е", ColorCodeKey.of("ё").value());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> ColorCodeKey.of(null));
    }
}
