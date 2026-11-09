package com.tmp.reference.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColorTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T03:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Белый",
                "00",
                "RAL7016",
                "RAL7016|30",
                "RAL7016|МУАР",
                "KDB74-Z8",
                "KDB74-Z8|МАСС",
                "KDB74-Z8|ПРИТВОР"
            })
    void createAcceptsApprovedCodesAndPreservesSpelling(String code) {
        Color color = Color.create(code, ACTOR, CLOCK);

        assertEquals(code, color.code());
        assertEquals(ColorCodeKey.of(code), color.codeKey());
        assertTrue(color.active());
        assertEquals(0L, color.version());
        assertEquals(ACTOR, color.createdBy());
        assertEquals(ACTOR, color.updatedBy());
    }

    @Test
    void createTrimsEdgesButKeepsInternalSpacingAndCase() {
        Color color = Color.create("  RAL 7016  ", ACTOR, CLOCK);

        assertEquals("RAL 7016", color.code());
        assertEquals("RAL 7016", color.codeKey().value());
        assertNotEquals(ColorCodeKey.of("RAL7016"), color.codeKey());
    }

    @Test
    void createRejectsBlankSlashBackslashLengthAndPipeRules() {
        assertThrows(Color.InvalidCodeException.class, () -> Color.create("   ", ACTOR, CLOCK));
        assertThrows(Color.InvalidCodeException.class, () -> Color.create(null, ACTOR, CLOCK));
        assertThrows(
                Color.InvalidCodeException.class, () -> Color.create("Белый/RAL7016", ACTOR, CLOCK));
        assertThrows(
                Color.InvalidCodeException.class, () -> Color.create("RAL7016\\30", ACTOR, CLOCK));
        assertThrows(
                Color.InvalidCodeException.class,
                () -> Color.create("A".repeat(Color.MAX_CODE_LENGTH + 1), ACTOR, CLOCK));
        assertThrows(Color.InvalidCodeException.class, () -> Color.create("|30", ACTOR, CLOCK));
        assertThrows(Color.InvalidCodeException.class, () -> Color.create("RAL|", ACTOR, CLOCK));
        assertThrows(
                Color.InvalidCodeException.class, () -> Color.create("A|B|C", ACTOR, CLOCK));
    }

    @Test
    void archiveAndRestoreToggleActiveWithoutChangingCode() {
        Color created = Color.create("RAL7016|30", ACTOR, CLOCK);
        Color archived = created.archive(ACTOR, CLOCK);

        assertFalse(archived.active());
        assertEquals(created.code(), archived.code());
        assertEquals(created.id(), archived.id());
        assertEquals(created.version(), archived.version());

        Color restored = archived.restore(ACTOR, CLOCK);
        assertTrue(restored.active());
        assertEquals(created.code(), restored.code());

        assertThrows(IllegalStateException.class, () -> archived.archive(ACTOR, CLOCK));
        assertThrows(IllegalStateException.class, () -> created.restore(ACTOR, CLOCK));
    }
}
