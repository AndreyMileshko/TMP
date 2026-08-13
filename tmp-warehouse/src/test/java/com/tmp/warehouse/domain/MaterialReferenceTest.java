package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MaterialReferenceTest {

    @Test
    void articleIsRequiredAndTrimmed() {
        MaterialReference reference = MaterialReference.legacyArticle(" VEKA-103.211 ");
        assertEquals("VEKA-103.211", reference.article());
        assertEquals("VEKA-103.211", reference.materialCode());
        assertEquals("", reference.unitOfMeasure());
    }

    @Test
    void blankArticleIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MaterialReference.legacyArticle(" "));
    }

    @Test
    void createNormalizesUnitAliasToCanonicalCode() {
        MaterialReference reference =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль VEKA Softline", "Белый", "6000 мм", "м.");
        assertEquals("м.", reference.unitOfMeasure());
        assertTrue(reference.matchesNaturalKey("VEKA-103.211", "Белый", "6000 мм", "метры"));
    }

    @Test
    void createRejectsUnsupportedUnit() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MaterialReference.create(
                                "VEKA-103.211",
                                "Профиль VEKA Softline",
                                "Белый",
                                "6000 мм",
                                "метры-неизвестные"));
    }

    @Test
    void metersAliasIsNormalizedToCanonicalMeters() {
        MaterialReference reference =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль", "Белый", "6000 мм", "метры");
        assertEquals("м.", reference.unitOfMeasure());
    }

    @Test
    void differentUnitCreatesDifferentNaturalKey() {
        MaterialReference meters =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль", "Белый", "6000 мм", "м.");
        MaterialReference pieces =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль", "Белый", "6000 мм", "шт.");
        assertFalse(meters.matchesNaturalKey("VEKA-103.211", "Белый", "6000 мм", "шт."));
        assertTrue(pieces.matchesNaturalKey("VEKA-103.211", "Белый", "6000 мм", "шт"));
        assertFalse(meters.id().equals(pieces.id()));
    }

    @Test
    void naturalKeyUsesArticleColorSizeUnitNotName() {
        MaterialReference white =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль A", "Белый", "6000", "шт.");
        MaterialReference anthracite =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль B", "Антрацит", "6000", "шт.");
        assertEquals("VEKA-103.211", white.article());
        assertEquals("Профиль A", white.name());
        assertEquals("Профиль B", anthracite.name());
        assertEquals("шт.", white.unitOfMeasure());
        assertFalse(white.equals(anthracite));
    }
}
