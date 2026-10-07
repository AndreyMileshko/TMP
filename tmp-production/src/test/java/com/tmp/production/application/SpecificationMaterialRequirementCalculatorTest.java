package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.production.application.SpecificationMaterialRequirementCalculator.ScaledAggregate;
import com.tmp.production.application.SpecificationMaterialRequirementCalculator.ScaledMaterialInput;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.domain.AggregatedMaterialRequirement;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpecificationMaterialRequirementCalculatorTest {

    private final SpecificationMaterialRequirementCalculator calculator =
            new SpecificationMaterialRequirementCalculator();

    @Test
    void lineQuantityIsNotMultipliedByOrderedQuantity() {
        List<ResolvedMaterialLine> lines =
                List.of(
                        new ResolvedMaterialLine(
                                "PROFILE-X",
                                "Profile",
                                "WHITE",
                                BigDecimal.valueOf(1500),
                                BigDecimal.valueOf(7),
                                "PCS"));

        List<AggregatedMaterialRequirement> requirements = calculator.aggregate(lines);

        assertEquals(1, requirements.size());
        assertEquals(0, requirements.getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(7)));
    }

    @Test
    void aggregatesSameMaterialIdentityAcrossLines() {
        List<ResolvedMaterialLine> lines =
                List.of(
                        materialLine("PROFILE-X", "WHITE", "PCS", 4),
                        materialLine("PROFILE-X", "WHITE", "PCS", 6));

        List<AggregatedMaterialRequirement> requirements = calculator.aggregate(lines);

        assertEquals(1, requirements.size());
        assertEquals(
                0, requirements.getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(10)));
    }

    @Test
    void doesNotAggregateDifferentColor() {
        List<ResolvedMaterialLine> lines =
                List.of(
                        materialLine("PROFILE-X", "WHITE", "PCS", 4),
                        materialLine("PROFILE-X", "BLACK", "PCS", 6));

        List<AggregatedMaterialRequirement> requirements = calculator.aggregate(lines);

        assertEquals(2, requirements.size());
    }

    @Test
    void doesNotAggregateDifferentUnitOfMeasure() {
        List<ResolvedMaterialLine> lines =
                List.of(
                        materialLine("PROFILE-X", "WHITE", "PCS", 4),
                        materialLine("PROFILE-X", "WHITE", "M", 6));

        List<AggregatedMaterialRequirement> requirements = calculator.aggregate(lines);

        assertEquals(2, requirements.size());
    }

    @Test
    void aggregationKeyIgnoresLengthMm() {
        List<ResolvedMaterialLine> lines =
                List.of(
                        new ResolvedMaterialLine(
                                "PROFILE-X",
                                "Profile",
                                "WHITE",
                                BigDecimal.valueOf(1500),
                                BigDecimal.valueOf(4),
                                "PCS"),
                        new ResolvedMaterialLine(
                                "PROFILE-X",
                                "Profile",
                                "WHITE",
                                BigDecimal.valueOf(3000),
                                BigDecimal.valueOf(6),
                                "PCS"));

        List<AggregatedMaterialRequirement> requirements = calculator.aggregate(lines);

        assertEquals(1, requirements.size());
        assertEquals(
                SpecificationMaterialIdentity.of("PROFILE-X", "WHITE", "PCS"),
                requirements.getFirst().identity());
        assertEquals(
                0, requirements.getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(10)));
    }

    @Test
    void aggregateScaledOneProductTimesFourMeters() {
        List<ScaledAggregate> result =
                calculator.aggregateScaled(
                        List.of(scaledInput(1L, List.of(materialLine("MAT", "WHITE", "M", 4)))));

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(4)));
        assertEquals(1, result.getFirst().contributions().size());
    }

    @Test
    void aggregateScaledTenProductsTimesFourMeters() {
        List<ScaledAggregate> result =
                calculator.aggregateScaled(
                        List.of(scaledInput(10L, List.of(materialLine("MAT", "WHITE", "M", 4)))));

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(40)));
    }

    @Test
    void aggregateScaledThreeProductsTimesNormTwoIsSixWithoutDoubleMultiply() {
        List<ScaledAggregate> result =
                calculator.aggregateScaled(
                        List.of(scaledInput(3L, List.of(materialLine("MAT", "WHITE", "PCS", 2)))));

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(6)));
    }

    @Test
    void aggregateScaledThreeProductsTimesPointSeventyFive() {
        List<ScaledAggregate> result =
                calculator.aggregateScaled(
                        List.of(
                                scaledInput(
                                        3L,
                                        List.of(
                                                new ResolvedMaterialLine(
                                                        "MAT",
                                                        "Material",
                                                        "WHITE",
                                                        null,
                                                        new BigDecimal("0.75"),
                                                        "PCS")))));

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().requiredQuantity().compareTo(new BigDecimal("2.25")));
    }

    @Test
    void aggregateScaledMergesSameMaterialFromTwoItems() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();

        List<ScaledAggregate> result =
                calculator.aggregateScaled(
                        List.of(
                                new ScaledMaterialInput(
                                        orderId,
                                        itemA,
                                        4L,
                                        List.of(materialLine("MAT", "WHITE", "PCS", 3))),
                                new ScaledMaterialInput(
                                        orderId,
                                        itemB,
                                        5L,
                                        List.of(materialLine("MAT", "WHITE", "PCS", 3)))));

        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(27)));
        assertEquals(2, result.getFirst().contributions().size());
    }

    @Test
    void aggregateScaledDoesNotMergeDifferentUnitOfMeasure() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();

        List<ScaledAggregate> result =
                calculator.aggregateScaled(
                        List.of(
                                new ScaledMaterialInput(
                                        orderId,
                                        itemA,
                                        1L,
                                        List.of(materialLine("MAT", "WHITE", "PCS", 4))),
                                new ScaledMaterialInput(
                                        orderId,
                                        itemB,
                                        1L,
                                        List.of(materialLine("MAT", "WHITE", "M", 6)))));

        assertEquals(2, result.size());
    }

    private static ScaledMaterialInput scaledInput(
            long requestedProductQuantity, List<ResolvedMaterialLine> materialLines) {
        return new ScaledMaterialInput(
                SourceOrderId.generate(),
                SourceOrderItemId.generate(),
                requestedProductQuantity,
                materialLines);
    }

    private static ResolvedMaterialLine materialLine(
            String code, String color, String unit, long quantity) {
        return new ResolvedMaterialLine(
                code, code, color, null, BigDecimal.valueOf(quantity), unit);
    }
}
