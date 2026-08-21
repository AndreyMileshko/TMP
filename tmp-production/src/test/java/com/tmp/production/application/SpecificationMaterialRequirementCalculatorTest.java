package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.domain.AggregatedMaterialRequirement;
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

    private static ResolvedMaterialLine materialLine(
            String code, String color, String unit, long quantity) {
        return new ResolvedMaterialLine(
                code, code, color, null, BigDecimal.valueOf(quantity), unit);
    }
}
