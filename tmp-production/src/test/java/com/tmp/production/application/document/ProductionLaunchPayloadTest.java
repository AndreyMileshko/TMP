package com.tmp.production.application.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionCuttingPlanLink;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionLaunchPayloadTest {

    @Test
    void validPayload() {
        Instant now = Instant.now();
        SourceOrderId orderId = SourceOrderId.generate();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        orderId,
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        now);
        ProductionLaunchPayload payload =
                new ProductionLaunchPayload(
                        orderId,
                        List.of(new ProductionLaunchLine(foundation, ProductionQuantity.positive(5))),
                        "operator",
                        now);
        assertEquals("operator", payload.createdBy());
        assertEquals(1, payload.lines().size());
        assertEquals(orderId, payload.sourceOrderId());
        assertTrue(payload.lines().getFirst().cuttingPlanLinks().isEmpty());
    }

    @Test
    void launchLineCanCarryMultipleCuttingPlanLinks() {
        Instant now = Instant.now();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        now);
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()),
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()));

        ProductionLaunchLine line =
                new ProductionLaunchLine(foundation, ProductionQuantity.positive(3), links);

        assertEquals(2, line.cuttingPlanLinks().size());
    }

    @Test
    void emptyLinesRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProductionLaunchPayload(
                                SourceOrderId.generate(),
                                List.of(),
                                "op",
                                Instant.now()));
    }

    @Test
    void blankCreatedByRejected() {
        Instant now = Instant.now();
        SourceOrderId orderId = SourceOrderId.generate();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        orderId,
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        now);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ProductionLaunchPayload(
                                orderId,
                                List.of(
                                        new ProductionLaunchLine(
                                                foundation, ProductionQuantity.positive(1))),
                                "  ",
                                now));
    }
}
