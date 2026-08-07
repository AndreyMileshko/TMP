package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MaterialReferenceTest {

    @Test
    void materialCodeIsRequired() {
        MaterialReference reference = MaterialReference.of(" VEKA-103.211 ");
        assertEquals("VEKA-103.211", reference.materialCode());
    }

    @Test
    void blankMaterialCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> MaterialReference.of(" "));
    }
}
