package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseDemandTest {

    private static final Instant ACCEPTED_AT = Instant.parse("2026-10-05T09:00:00Z");

    @Test
    void demandWithUnknownMaterialReferenceIsValid() {
        WarehouseDemandLine line =
                line(
                        UUID.randomUUID(),
                        "ART-1",
                        "Profile",
                        "White",
                        "m",
                        null,
                        BigDecimal.TEN,
                        null,
                        null);
        WarehouseDemand demand = accept(List.of(line));
        assertTrue(demand.lines().get(0).materialReferenceId().isEmpty());
    }

    @Test
    void demandWithMultipleLinesIsValid() {
        WarehouseDemand demand =
                accept(
                        List.of(
                                line(
                                        UUID.randomUUID(),
                                        "ART-1",
                                        "A",
                                        "",
                                        "m",
                                        null,
                                        BigDecimal.ONE,
                                        null,
                                        null),
                                line(
                                        UUID.randomUUID(),
                                        "ART-2",
                                        "B",
                                        "Black",
                                        "pcs",
                                        BigDecimal.valueOf(1200),
                                        BigDecimal.TEN,
                                        MaterialReferenceId.generate(),
                                        WarehouseDemandWaitingReason.NO_AVAILABLE_STOCK)));
        assertEquals(2, demand.lines().size());
    }

    @Test
    void requiredQuantityZeroOrNegativeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        line(
                                UUID.randomUUID(),
                                "ART-1",
                                "A",
                                "",
                                "m",
                                null,
                                BigDecimal.ZERO,
                                null,
                                null));
        assertThrows(
                IllegalArgumentException.class,
                () -> StockQuantity.of(BigDecimal.valueOf(-1)));
    }

    @Test
    void duplicateSourceLineIdRejected() {
        UUID sourceLineId = UUID.randomUUID();
        WarehouseDemandLine first =
                line(sourceLineId, "ART-1", "A", "", "m", null, BigDecimal.ONE, null, null);
        WarehouseDemandLine second =
                line(sourceLineId, "ART-2", "B", "", "pcs", null, BigDecimal.TEN, null, null);
        assertThrows(IllegalArgumentException.class, () -> accept(List.of(first, second)));
    }

    @Test
    void lengthMmNullableAndPreserved() {
        WarehouseDemandLine withoutLength =
                line(UUID.randomUUID(), "ART-1", "A", "", "m", null, BigDecimal.ONE, null, null);
        assertTrue(withoutLength.lengthMm().isEmpty());

        BigDecimal length = new BigDecimal("2500.500000");
        WarehouseDemandLine withLength =
                line(UUID.randomUUID(), "ART-1", "A", "", "m", length, BigDecimal.ONE, null, null);
        assertEquals(0, length.compareTo(withLength.lengthMm().orElseThrow()));
    }

    @Test
    void materialReferenceIdAndWaitingReasonNullable() {
        WarehouseDemandLine line =
                line(UUID.randomUUID(), "ART-1", null, null, "m", null, BigDecimal.ONE, null, null);
        assertNull(line.materialName());
        assertEquals("", line.color());
        assertTrue(line.materialReferenceId().isEmpty());
        assertTrue(line.waitingReason().isEmpty());
    }

    @Test
    void waitingReasonEnumValuesAccepted() {
        for (WarehouseDemandWaitingReason reason : WarehouseDemandWaitingReason.values()) {
            WarehouseDemandLine line =
                    line(
                            UUID.randomUUID(),
                            "ART-1",
                            "A",
                            "",
                            "m",
                            null,
                            BigDecimal.ONE,
                            null,
                            reason);
            assertEquals(reason, line.waitingReason().orElseThrow());
        }
    }

    @Test
    void blankMaterialCodeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        line(
                                UUID.randomUUID(),
                                "  ",
                                "A",
                                "",
                                "m",
                                null,
                                BigDecimal.ONE,
                                null,
                                null));
    }

    @Test
    void emptyLinesRejected() {
        assertThrows(IllegalArgumentException.class, () -> accept(List.of()));
    }

    private static WarehouseDemand accept(List<WarehouseDemandLine> lines) {
        return WarehouseDemand.accept(
                UUID.randomUUID(), WarehouseId.generate(), ACCEPTED_AT, "tester", lines);
    }

    private static WarehouseDemandLine line(
            UUID sourceLineId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal lengthMm,
            BigDecimal quantity,
            MaterialReferenceId materialReferenceId,
            WarehouseDemandWaitingReason waitingReason) {
        return WarehouseDemandLine.create(
                sourceLineId,
                materialCode,
                materialName,
                color,
                unitOfMeasure,
                lengthMm,
                StockQuantity.of(quantity),
                materialReferenceId,
                waitingReason);
    }
}
