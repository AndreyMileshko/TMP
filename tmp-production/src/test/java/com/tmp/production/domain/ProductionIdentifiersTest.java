package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionIdentifiersTest {

    @Test
    void sourceOrderIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> SourceOrderId.of(null));
    }

    @Test
    void sourceOrderItemIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> SourceOrderItemId.of(null));
    }

    @Test
    void specificationIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> SpecificationId.of(null));
    }

    @Test
    void sourceOrderIdValueEquality() {
        UUID value = UUID.randomUUID();
        assertEquals(SourceOrderId.of(value), SourceOrderId.of(value));
        assertEquals(value, SourceOrderId.of(value).value());
    }

    @Test
    void sourceOrderItemIdValueEquality() {
        UUID value = UUID.randomUUID();
        assertEquals(SourceOrderItemId.of(value), SourceOrderItemId.of(value));
    }

    @Test
    void specificationIdValueEquality() {
        UUID value = UUID.randomUUID();
        assertEquals(SpecificationId.of(value), SpecificationId.of(value));
    }

    @Test
    void differentSourceOrderIdsAreNotEqual() {
        assertNotEquals(SourceOrderId.generate(), SourceOrderId.generate());
    }

    @Test
    void differentSourceOrderItemIdsAreNotEqual() {
        assertNotEquals(SourceOrderItemId.generate(), SourceOrderItemId.generate());
    }

    @Test
    void differentSpecificationIdsAreNotEqual() {
        assertNotEquals(SpecificationId.generate(), SpecificationId.generate());
    }

    @Test
    void cuttingPlanIdRejectsNullAndUsesValueEquality() {
        assertThrows(NullPointerException.class, () -> CuttingPlanId.of(null));
        UUID value = UUID.randomUUID();
        assertEquals(CuttingPlanId.of(value), CuttingPlanId.of(value));
        assertNotEquals(CuttingPlanId.generate(), CuttingPlanId.generate());
    }

    @Test
    void materialReferenceIdRejectsNullAndUsesValueEquality() {
        assertThrows(NullPointerException.class, () -> MaterialReferenceId.of(null));
        UUID value = UUID.randomUUID();
        assertEquals(MaterialReferenceId.of(value), MaterialReferenceId.of(value));
        assertNotEquals(MaterialReferenceId.generate(), MaterialReferenceId.generate());
    }
}
