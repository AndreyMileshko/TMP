package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MaterialReferenceTest {

    @Test
    void articleIsRequiredAndTrimmed() {
        MaterialReference reference = MaterialReference.legacyArticle(" VEKA-103.211 ");
        assertEquals("VEKA-103.211", reference.article());
        assertEquals("VEKA-103.211", reference.materialCode());
    }

    @Test
    void blankArticleIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MaterialReference.legacyArticle(" "));
    }

    @Test
    void naturalKeyUsesArticleColorSizeUnitNotName() {
        MaterialReference white =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль A", "Белый", "6000", "шт");
        MaterialReference anthracite =
                MaterialReference.create(
                        "VEKA-103.211", "Профиль B", "Антрацит", "6000", "шт");
        assertEquals("VEKA-103.211", white.article());
        assertEquals("Профиль A", white.name());
        assertEquals("Профиль B", anthracite.name());
        assertEquals(false, white.equals(anthracite));
    }
}
