package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductionFoundationTest {

    private static final Instant T0 = Instant.parse("2026-08-19T06:00:00Z");

    @Test
    void freezeCapturesImmutableReference() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();

        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, specId, T0);

        assertEquals(orderId, foundation.sourceOrderId());
        assertEquals(itemId, foundation.sourceOrderItemId());
        assertEquals(specId, foundation.specificationId());
        assertEquals(T0, foundation.frozenAt());
    }

    @Test
    void restoreReconstructsPersistedFoundation() {
        ProductionFoundation original = sampleFoundation();
        ProductionFoundation restored =
                ProductionFoundation.restore(
                        original.sourceOrderId(),
                        original.sourceOrderItemId(),
                        original.specificationId(),
                        original.frozenAt());

        assertEquals(original, restored);
    }

    @Test
    void foundationIsImmutableAcrossEquality() {
        ProductionFoundation first = sampleFoundation();
        ProductionFoundation second =
                ProductionFoundation.freeze(
                        first.sourceOrderId(),
                        first.sourceOrderItemId(),
                        first.specificationId(),
                        first.frozenAt());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void differentSpecificationProducesDifferentFoundation() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();

        ProductionFoundation first =
                ProductionFoundation.freeze(
                        orderId, itemId, SpecificationId.generate(), T0);
        ProductionFoundation second =
                ProductionFoundation.freeze(
                        orderId, itemId, SpecificationId.generate(), T0);

        assertNotEquals(first, second);
    }

    @Test
    void cuttingPlanLinksExtensionPointIsEmpty() {
        assertTrue(sampleFoundation().cuttingPlanLinks().links().isEmpty());
    }

    @Test
    void freezeRejectsNullFields() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();

        assertThrows(
                NullPointerException.class,
                () -> ProductionFoundation.freeze(null, itemId, specId, T0));
        assertThrows(
                NullPointerException.class,
                () -> ProductionFoundation.freeze(orderId, null, specId, T0));
        assertThrows(
                NullPointerException.class,
                () -> ProductionFoundation.freeze(orderId, itemId, null, T0));
        assertThrows(
                NullPointerException.class,
                () -> ProductionFoundation.freeze(orderId, itemId, specId, null));
    }

    private static ProductionFoundation sampleFoundation() {
        return ProductionFoundation.freeze(
                SourceOrderId.generate(),
                SourceOrderItemId.generate(),
                SpecificationId.generate(),
                T0);
    }
}
