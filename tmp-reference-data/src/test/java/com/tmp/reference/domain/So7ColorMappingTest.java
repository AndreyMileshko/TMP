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

class So7ColorMappingTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-09T04:00:00Z"), ZoneOffset.UTC);
    private static final UUID ACTOR = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final ColorId INSIDE = ColorId.generate();
    private static final ColorId OUTSIDE = ColorId.generate();

    @Test
    void createTrimsSourcePreservesMeaningfulCharsAndBuildsKey() {
        So7ColorMapping mapping =
                So7ColorMapping.create("  RAL7016/30т  ", INSIDE, OUTSIDE, ACTOR, CLOCK);

        assertEquals("RAL7016/30т", mapping.sourceText());
        assertEquals(So7SourceKey.of("RAL7016/30т"), mapping.sourceKey());
        assertEquals("RAL7016/30Т", mapping.sourceKey().value());
        assertEquals(INSIDE, mapping.insideColorId());
        assertEquals(OUTSIDE, mapping.outsideColorId());
        assertTrue(mapping.active());
        assertEquals(0L, mapping.version());
        assertEquals(ACTOR, mapping.createdBy());
        assertNotEquals(null, mapping.id());
    }

    @Test
    void createAllowsSameInsideAndOutside() {
        So7ColorMapping mapping =
                So7ColorMapping.create("Белый", INSIDE, INSIDE, ACTOR, CLOCK);
        assertEquals(INSIDE, mapping.insideColorId());
        assertEquals(INSIDE, mapping.outsideColorId());
    }

    @Test
    void createPreservesInternalSpacesAndSlashPipe() {
        So7ColorMapping mapping =
                So7ColorMapping.create("RAL  7016|мат", INSIDE, OUTSIDE, ACTOR, CLOCK);
        assertEquals("RAL  7016|мат", mapping.sourceText());
        assertEquals("RAL 7016|МАТ", mapping.sourceKey().value());
    }

    @Test
    void createRejectsBlankNullAndTooLongSource() {
        assertThrows(
                So7ColorMapping.InvalidSourceException.class,
                () -> So7ColorMapping.create("   ", INSIDE, OUTSIDE, ACTOR, CLOCK));
        assertThrows(
                So7ColorMapping.InvalidSourceException.class,
                () -> So7ColorMapping.create(null, INSIDE, OUTSIDE, ACTOR, CLOCK));
        assertThrows(
                So7ColorMapping.InvalidSourceException.class,
                () -> So7ColorMapping.create(
                        "x".repeat(So7ColorMapping.MAX_SOURCE_TEXT_LENGTH + 1),
                        INSIDE,
                        OUTSIDE,
                        ACTOR,
                        CLOCK));
    }

    @Test
    void updatePairAndActiveToggleDoNotChangeSource() {
        So7ColorMapping created =
                So7ColorMapping.create("Белый", INSIDE, OUTSIDE, ACTOR, CLOCK);
        ColorId other = ColorId.generate();

        So7ColorMapping updated = created.updatePair(other, INSIDE, ACTOR, CLOCK);
        assertEquals("Белый", updated.sourceText());
        assertEquals(created.sourceKey(), updated.sourceKey());
        assertEquals(other, updated.insideColorId());
        assertEquals(INSIDE, updated.outsideColorId());
        assertEquals(created.version(), updated.version());

        So7ColorMapping inactive = updated.deactivate(ACTOR, CLOCK);
        assertFalse(inactive.active());
        assertEquals("Белый", inactive.sourceText());

        So7ColorMapping activeAgain = inactive.activate(ACTOR, CLOCK);
        assertTrue(activeAgain.active());

        assertThrows(
                IllegalStateException.class, () -> activeAgain.activate(ACTOR, CLOCK));
        assertThrows(
                IllegalStateException.class,
                () -> inactive.deactivate(ACTOR, CLOCK));
    }

    @Test
    void rehydrateRejectsMismatchedSourceKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> So7ColorMapping.rehydrate(
                        UUID.randomUUID(),
                        "Белый",
                        So7SourceKey.of("Чёрный"),
                        INSIDE,
                        OUTSIDE,
                        true,
                        0L,
                        CLOCK.instant(),
                        CLOCK.instant(),
                        ACTOR,
                        ACTOR));
    }
}