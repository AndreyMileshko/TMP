package com.tmp.reference.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class So7SourceKeyTest {

    @Test
    void trimsAndCollapsesWhitespaceIncludingNbsp() {
        assertEquals("RAL 9006 МЕТАЛЛИК", So7SourceKey.of("  RAL 9006 металлик  ").value());
        assertEquals(
                "RAL 9006 МЕТАЛЛИК",
                So7SourceKey.of("RAL\u00A0\u00A09006\tметаллик").value());
        assertEquals("A B", So7SourceKey.of("a   b").value());
    }

    @Test
    void preservesSlashPipeAndMeaningfulSpaces() {
        assertEquals("RAL7016/30Т", So7SourceKey.of("RAL7016/30т").value());
        assertEquals("RAL7016|30", So7SourceKey.of("RAL7016|30").value());
        assertNotEquals(So7SourceKey.of("RAL 7016").value(), So7SourceKey.of("RAL7016").value());
    }

    @Test
    void doesNotReplaceYo() {
        assertEquals("Ё", So7SourceKey.of("ё").value());
        assertNotEquals("Е", So7SourceKey.of("ё").value());
    }

    @Test
    void uppercasesCyrillicWithLocaleRoot() {
        assertEquals("БЕЛЫЙ", So7SourceKey.of("Белый").value());
        assertEquals("БЕЗ ЦВЕТА", So7SourceKey.of("Без цвета").value());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> So7SourceKey.of(null));
    }
}
