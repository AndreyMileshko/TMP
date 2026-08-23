package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionReleaseTest {

    private static final Instant T0 = Instant.parse("2026-08-21T10:00:00Z");

    @Test
    void rejectsDuplicateItemLines() {
        SourceOrderItemId item = SourceOrderItemId.generate();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProductionRelease.draft(
                                UUID.randomUUID(),
                                SourceOrderId.generate(),
                                T0,
                                List.of(itemLine(item, 3), itemLine(item, 2)),
                                List.of()));
    }

    @Test
    void allowsPlanLessEqualGreaterThanFactAndZeroFact() {
        ProductionRelease.MaterialLine under =
                material(BigDecimal.TEN, BigDecimal.valueOf(8));
        ProductionRelease.MaterialLine equal =
                material(BigDecimal.TEN, BigDecimal.TEN);
        ProductionRelease.MaterialLine over =
                material(BigDecimal.TEN, BigDecimal.valueOf(12));
        ProductionRelease.MaterialLine zeroFact =
                material(BigDecimal.TEN, BigDecimal.ZERO);

        assertEquals(new BigDecimal("-2"), under.actualMinusPlanned());
        assertEquals(BigDecimal.ZERO, equal.actualMinusPlanned());
        assertEquals(new BigDecimal("2"), over.actualMinusPlanned());
        assertEquals(new BigDecimal("-10"), zeroFact.actualMinusPlanned());

        ProductionRelease release =
                ProductionRelease.draft(
                        UUID.randomUUID(),
                        SourceOrderId.generate(),
                        T0,
                        List.of(itemLine(SourceOrderItemId.generate(), 1)),
                        List.of(under, equal, over, zeroFact));
        assertEquals(4, release.materialLines().size());
    }

    @Test
    void postedReleaseIsImmutable() {
        ProductionRelease draft =
                ProductionRelease.draft(
                        UUID.randomUUID(),
                        SourceOrderId.generate(),
                        T0,
                        List.of(itemLine(SourceOrderItemId.generate(), 1)),
                        List.of());
        ProductionRelease posted = draft.markPosted();
        assertTrue(posted.posted());
        assertThrows(ProductionReleaseImmutableException.class, posted::markPosted);
        assertThrows(
                ProductionReleaseImmutableException.class,
                () ->
                        posted.replaceDraftContent(
                                T0,
                                List.of(itemLine(SourceOrderItemId.generate(), 2)),
                                List.of()));
    }

    @Test
    void actualMaterialUsagesProjectsFacts() {
        MaterialReferenceId material = MaterialReferenceId.generate();
        SourceOrderItemId item = SourceOrderItemId.generate();
        ProductionRelease release =
                ProductionRelease.draft(
                        UUID.randomUUID(),
                        SourceOrderId.generate(),
                        T0,
                        List.of(itemLine(item, 1)),
                        List.of(
                                new ProductionRelease.MaterialLine(
                                        material,
                                        BigDecimal.TEN,
                                        BigDecimal.valueOf(7),
                                        MaterialPlanningSource.SPECIFICATION,
                                        Optional.empty(),
                                        Optional.of(item),
                                        Optional.of("note"))));
        var usages = release.actualMaterialUsages();
        assertEquals(1, usages.size());
        assertEquals(material, usages.getFirst().materialReferenceId());
        assertEquals(BigDecimal.valueOf(7), usages.getFirst().actualQuantity());
        assertEquals(Optional.of(item), usages.getFirst().sourceOrderItemId());
    }

    @Test
    void rejectsNegativePlanOrFact() {
        assertThrows(
                IllegalArgumentException.class,
                () -> material(new BigDecimal("-1"), BigDecimal.ONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> material(BigDecimal.ONE, new BigDecimal("-1")));
    }

    @Test
    void draftStartsUnposted() {
        ProductionRelease release =
                ProductionRelease.draft(
                        UUID.randomUUID(),
                        SourceOrderId.generate(),
                        T0,
                        List.of(itemLine(SourceOrderItemId.generate(), 5)),
                        List.of());
        assertFalse(release.posted());
    }

    private static ProductionRelease.ItemLine itemLine(SourceOrderItemId itemId, long qty) {
        return new ProductionRelease.ItemLine(
                itemId, SpecificationId.generate(), ProductionQuantity.positive(qty));
    }

    private static ProductionRelease.MaterialLine material(BigDecimal plan, BigDecimal fact) {
        return new ProductionRelease.MaterialLine(
                MaterialReferenceId.generate(),
                plan,
                fact,
                MaterialPlanningSource.SPECIFICATION,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
    }
}
