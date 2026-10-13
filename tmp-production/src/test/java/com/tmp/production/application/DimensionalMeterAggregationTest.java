package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tmp.production.application.SpecificationMaterialRequirementCalculator.ScaledAggregate;
import com.tmp.production.application.SpecificationMaterialRequirementCalculator.ScaledMaterialInput;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Focused B3-MA-07 aggregation cases: multiple lengths of same material; meter/non-meter
 * regression; release default preliminary meters.
 */
class DimensionalMeterAggregationTest {

    private static final Instant T0 = Instant.parse("2026-10-13T08:00:00Z");

    private final SpecificationMaterialRequirementCalculator mrCalculator =
            new SpecificationMaterialRequirementCalculator();
    private final ReleaseMaterialPlanBuilder releaseBuilder = new ReleaseMaterialPlanBuilder();

    @Test
    void multipleLengthsOfSameMaterialAggregateMeters() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        // 1×1226×12/1000 + 2×500×12/1000 = 14.712 + 12.000 = 26.712
        List<ScaledAggregate> result =
                mrCalculator.aggregateScaled(
                        List.of(
                                new ScaledMaterialInput(
                                        orderId,
                                        itemId,
                                        12L,
                                        List.of(
                                                meterLine("PROF", "Белый", 1, 1226),
                                                meterLine("PROF", "Белый", 2, 500)))));

        assertEquals(1, result.size());
        assertEquals(0, new BigDecimal("26.712000").compareTo(result.getFirst().requiredQuantity()));
        assertNull(result.getFirst().lengthMm());
    }

    @Test
    void test004MaterialRequirementScaled() {
        List<ScaledAggregate> result =
                mrCalculator.aggregateScaled(
                        List.of(
                                new ScaledMaterialInput(
                                        SourceOrderId.generate(),
                                        SourceOrderItemId.generate(),
                                        12L,
                                        List.of(meterLine("PROF", "Белый", 1, 1226)))));

        assertEquals(0, new BigDecimal("14.712000").compareTo(result.getFirst().requiredQuantity()));
        assertEquals(0, new BigDecimal("1226").compareTo(result.getFirst().lengthMm()));
    }

    @Test
    void meterWithoutLengthAndNonMeterUnchangedInScaledAggregation() {
        SourceOrderId orderId = SourceOrderId.generate();
        List<ScaledAggregate> result =
                mrCalculator.aggregateScaled(
                        List.of(
                                new ScaledMaterialInput(
                                        orderId,
                                        SourceOrderItemId.generate(),
                                        5L,
                                        List.of(
                                                new ResolvedMaterialLine(
                                                        "M-METER",
                                                        "Tape",
                                                        "",
                                                        null,
                                                        new BigDecimal("2"),
                                                        "м."))),
                                new ScaledMaterialInput(
                                        orderId,
                                        SourceOrderItemId.generate(),
                                        5L,
                                        List.of(
                                                new ResolvedMaterialLine(
                                                        "M-PCS",
                                                        "Screw",
                                                        "",
                                                        new BigDecimal("50"),
                                                        new BigDecimal("3"),
                                                        "шт.")))));

        assertEquals(2, result.size());
        ScaledAggregate meters =
                result.stream()
                        .filter(a -> a.identity().materialCode().equals("M-METER"))
                        .findFirst()
                        .orElseThrow();
        ScaledAggregate pieces =
                result.stream()
                        .filter(a -> a.identity().materialCode().equals("M-PCS"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, new BigDecimal("10.000000").compareTo(meters.requiredQuantity()));
        assertEquals(0, new BigDecimal("15.000000").compareTo(pieces.requiredQuantity()));
    }

    @Test
    void releaseDefaultPreliminaryUsesMetersNotPieceCount() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        ProductionItemState state =
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(12),
                        T0);

        UUID materialRef = UUID.randomUUID();
        var planned =
                releaseBuilder
                        .buildPlannedLines(
                                state,
                                12,
                                List.of(meterLine("PROF", "Белый", 1, 1226)),
                                List.of(
                                        new MaterialReferenceEntry(
                                                materialRef, "PROF", "Profile", "Белый", "", "м.")))
                        .getFirst();

        assertEquals(0, new BigDecimal("14.712000").compareTo(planned.plannedQuantity()));
        assertEquals(0, new BigDecimal("1226").compareTo(planned.lengthMm()));
        assertEquals(0, new BigDecimal("12").compareTo(planned.technologicalPieceCount()));
    }

    @Test
    void releaseAggregatesMultipleLengthsIntoMeters() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        ProductionItemState state =
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(12),
                        T0);

        UUID materialRef = UUID.randomUUID();
        var planned =
                releaseBuilder
                        .buildPlannedLines(
                                state,
                                12,
                                List.of(
                                        meterLine("PROF", "Белый", 1, 1226),
                                        meterLine("PROF", "Белый", 2, 500)),
                                List.of(
                                        new MaterialReferenceEntry(
                                                materialRef, "PROF", "Profile", "Белый", "", "м.")))
                        .getFirst();

        assertEquals(0, new BigDecimal("26.712000").compareTo(planned.plannedQuantity()));
        assertNull(planned.lengthMm());
        assertEquals(0, new BigDecimal("36").compareTo(planned.technologicalPieceCount()));
    }

    private static ResolvedMaterialLine meterLine(
            String code, String color, long pieces, long lengthMm) {
        return new ResolvedMaterialLine(
                code,
                code,
                color,
                BigDecimal.valueOf(lengthMm),
                BigDecimal.valueOf(pieces),
                "м.");
    }
}
