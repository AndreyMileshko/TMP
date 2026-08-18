package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.MaterialReferenceDisplay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodeOnlyMaterialReferenceDisplayPortTest {

    private CodeOnlyMaterialReferenceDisplayPort port;

    @BeforeEach
    void setUp() {
        port = new CodeOnlyMaterialReferenceDisplayPort();
        port.resetWarnings();
    }

    @Test
    void resolvesArticleOnlyWhenExtendedFieldsUnavailable() {
        MaterialReferenceDisplay display = port.resolve("VEKA-TEST-001");

        assertEquals("VEKA-TEST-001", display.article());
        assertEquals("", display.materialName());
        assertEquals("", display.color());
        assertEquals("", display.size());
        assertEquals("", display.unitOfMeasure());
        assertFalse(display.hasExtendedFields());
        assertTrue(port.warnedFor("VEKA-TEST-001"));
    }

    @Test
    void warnsOncePerArticle() {
        port.resolve("MAT-1");
        port.resolve("MAT-1");
        assertTrue(port.warnedFor("MAT-1"));
    }
}
