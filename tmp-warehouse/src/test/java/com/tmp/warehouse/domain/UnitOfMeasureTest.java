package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UnitOfMeasureTest {

    @Test
    void codesExposeFixedReferenceList() {
        assertEquals(
                java.util.List.of("шт.", "м.", "кв.м.", "компл.", "л.", "гр.", "кг."),
                UnitOfMeasure.codes());
    }

    @Test
    void canonicalCodeIsAccepted() {
        assertEquals("м.", UnitOfMeasure.requireCode("м."));
        assertEquals("шт.", UnitOfMeasure.requireCode("шт."));
    }

    @Test
    void commonAliasesAreNormalized() {
        assertEquals("м.", UnitOfMeasure.requireCode("м"));
        assertEquals("м.", UnitOfMeasure.requireCode("метры"));
        assertEquals("шт.", UnitOfMeasure.requireCode("шт"));
    }

    @Test
    void unsupportedUnitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> UnitOfMeasure.requireCode("футы"));
        assertThrows(IllegalArgumentException.class, () -> UnitOfMeasure.requireCode(""));
    }

    @Test
    void legacyEmptyIsAllowedOnlyForPersistedPath() {
        assertEquals("", UnitOfMeasure.requirePersistedOrLegacyEmpty(""));
        assertEquals("м.", UnitOfMeasure.requirePersistedOrLegacyEmpty("метр"));
    }
}
