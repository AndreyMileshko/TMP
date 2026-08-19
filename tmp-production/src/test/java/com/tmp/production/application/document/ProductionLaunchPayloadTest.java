package com.tmp.production.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductionLaunchPayloadTest {

    @Test
    void validPayload() {
        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        ProductionQuantity.positive(5),
                        Instant.now(),
                        "operator");
        assertEquals("operator", payload.createdBy());
    }

    @Test
    void nullSourceOrderIdRejected() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new ProductionLaunchPayload(
                                null,
                                SourceOrderItemId.generate(),
                                SpecificationId.generate(),
                                ProductionQuantity.positive(1),
                                Instant.now(),
                                "op"));
    }

    @Test
    void blankCreatedByRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProductionLaunchPayload(
                                SourceOrderId.generate(),
                                SourceOrderItemId.generate(),
                                SpecificationId.generate(),
                                ProductionQuantity.positive(1),
                                Instant.now(),
                                "  "));
    }
}
