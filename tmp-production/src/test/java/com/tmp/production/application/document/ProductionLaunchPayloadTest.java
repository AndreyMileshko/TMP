package com.tmp.production.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductionLaunchPayloadTest {

    @Test
    void validPayload() {
        Instant now = Instant.now();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        now);
        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(foundation, ProductionQuantity.positive(5), "operator");
        assertEquals("operator", payload.createdBy());
        assertEquals(foundation, payload.foundation());
    }

    @Test
    void nullFoundationRejected() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ProductionLaunchPayload(
                                null, ProductionQuantity.positive(1), "op"));
    }

    @Test
    void blankCreatedByRejected() {
        Instant now = Instant.now();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        now);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProductionLaunchPayload(
                                foundation, ProductionQuantity.positive(1), "  "));
    }
}
