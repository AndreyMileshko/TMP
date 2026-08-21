package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionCuttingPlanLink;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProductionItemStateMapperTest {

    private static final Instant T0 = Instant.parse("2026-08-19T06:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-19T07:00:00Z");

    @Test
    void domainToEntityAndBackPreservesAllFields() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specificationId = SpecificationId.generate();
        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, specificationId, T0);

        ProductionItemState launched =
                ProductionItemState.launch(foundation, ProductionQuantity.positive(12), T0);
        ProductionItemState partial =
                launched.release(ProductionQuantity.positive(5), T1).recordMaterialCheck(T1);

        ProductionItemStateEntity entity = ProductionItemStateMapper.toEntity(partial);
        ProductionItemState restored = ProductionItemStateMapper.toDomain(entity);

        assertFieldEquals(partial, restored);
        assertEquals(foundation, restored.foundation());
        assertEquals(specificationId, restored.specificationId());
        assertEquals(ProductionStatus.PARTIALLY_RELEASED, restored.status());
        assertEquals(ProductionQuantity.positive(12), restored.orderedQuantity());
        assertEquals(ProductionQuantity.nonNegative(7), restored.activeProductionQuantity());
        assertEquals(ProductionQuantity.nonNegative(5), restored.releasedQuantity());
        assertEquals(T1, restored.lastMaterialCheckAt());
        assertEquals(T1, restored.lastStatusChangedAt());
    }

    @Test
    void mapperPreservesNullMaterialCheckTimestamp() {
        ProductionItemState launched = sampleLaunched();
        ProductionItemStateEntity entity = ProductionItemStateMapper.toEntity(launched);
        ProductionItemState restored = ProductionItemStateMapper.toDomain(entity);

        assertNull(restored.lastMaterialCheckAt());
        assertNotNull(restored.foundation());
        assertNotNull(restored.specificationId());
    }

    @Test
    void specificationIdIsNeverNullInEntityMapping() {
        ProductionItemState launched = sampleLaunched();
        ProductionItemStateEntity entity = ProductionItemStateMapper.toEntity(launched);
        assertNotNull(entity.specificationId());
        assertTrue(entity.specificationId().value().toString().length() > 0);
    }

    @Test
    void mapperRoundTripPreservesMultipleCuttingPlanLinks() {
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()),
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.generate(), CuttingPlanId.generate()));
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        T0);
        ProductionItemState state =
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(2), T0, links);

        ProductionItemState restored =
                ProductionItemStateMapper.toDomain(ProductionItemStateMapper.toEntity(state));

        assertEquals(links, restored.cuttingPlanLinks());
        assertEquals(2, restored.cuttingPlanLinks().size());
    }

    private static ProductionItemState sampleLaunched() {
        ProductionFoundation foundation =
                ProductionFoundation.freeze(
                        SourceOrderId.generate(),
                        SourceOrderItemId.generate(),
                        SpecificationId.generate(),
                        T0);
        return ProductionItemState.launch(foundation, ProductionQuantity.positive(3), T0);
    }

    private static void assertFieldEquals(ProductionItemState expected, ProductionItemState actual) {
        assertEquals(expected.foundation(), actual.foundation());
        assertEquals(expected.sourceOrderId(), actual.sourceOrderId());
        assertEquals(expected.sourceOrderItemId(), actual.sourceOrderItemId());
        assertEquals(expected.specificationId(), actual.specificationId());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.orderedQuantity(), actual.orderedQuantity());
        assertEquals(expected.launchedQuantity(), actual.launchedQuantity());
        assertEquals(expected.activeProductionQuantity(), actual.activeProductionQuantity());
        assertEquals(expected.releasedQuantity(), actual.releasedQuantity());
        assertEquals(expected.lastMaterialCheckAt(), actual.lastMaterialCheckAt());
        assertEquals(expected.lastStatusChangedAt(), actual.lastStatusChangedAt());
        assertEquals(expected.cuttingPlanLinks(), actual.cuttingPlanLinks());
    }
}
