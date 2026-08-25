package com.tmp.production.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MaterialAvailabilityLineInvariantTest {

    @Test
    void resolvedLineRequiresTotalEqualToMainPlusProduction() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MaterialAvailabilityLine(
                                "CODE",
                                "Name",
                                "WHITE",
                                "PCS",
                                UUID.randomUUID(),
                                BigDecimal.TEN,
                                BigDecimal.valueOf(3),
                                BigDecimal.valueOf(2),
                                BigDecimal.valueOf(9),
                                BigDecimal.ONE,
                                MaterialAvailabilityLineStatus.INSUFFICIENT,
                                MaterialPlanningSource.SPECIFICATION));
    }

    @Test
    void unresolvedLineRequiresZeroAvailabilityAndFullDeficit() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MaterialAvailabilityLine(
                                "CODE",
                                "Name",
                                "WHITE",
                                "PCS",
                                null,
                                BigDecimal.valueOf(5),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.valueOf(5),
                                BigDecimal.valueOf(5),
                                MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED,
                                MaterialPlanningSource.SPECIFICATION));

        assertDoesNotThrow(
                () ->
                        new MaterialAvailabilityLine(
                                "CODE",
                                "Name",
                                "WHITE",
                                "PCS",
                                null,
                                BigDecimal.valueOf(5),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.valueOf(5),
                                MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED,
                                MaterialPlanningSource.SPECIFICATION));
    }

    @Test
    void ambiguousLineRequiresZeroAvailabilityAndFullDeficit() {
        assertDoesNotThrow(
                () ->
                        new MaterialAvailabilityLine(
                                "CODE",
                                "Name",
                                "WHITE",
                                "PCS",
                                null,
                                BigDecimal.valueOf(4),
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.ZERO,
                                BigDecimal.valueOf(4),
                                MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS,
                                MaterialPlanningSource.SPECIFICATION));
    }
}
