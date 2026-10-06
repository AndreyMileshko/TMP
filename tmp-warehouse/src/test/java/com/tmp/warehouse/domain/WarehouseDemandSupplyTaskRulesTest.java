package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseDemandDerivedStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseDemandSupplyTaskRulesTest {

    @Test
    void waitingLineWithoutActiveTransferQualifies() {
        WarehouseDemandLine line =
                WarehouseDemandLine.create(
                        UUID.randomUUID(),
                        "ART",
                        "Name",
                        "",
                        "шт.",
                        null,
                        StockQuantity.of(new BigDecimal("10")),
                        null,
                        WarehouseDemandWaitingReason.NO_AVAILABLE_STOCK);
        WarehouseDemand demand =
                WarehouseDemand.accept(
                        UUID.randomUUID(),
                        WarehouseId.generate(),
                        Instant.parse("2026-10-06T10:00:00Z"),
                        "system",
                        List.of(line));

        List<WarehouseDemandSupplyTaskRules.WaitingLine> waiting =
                WarehouseDemandSupplyTaskRules.waitingLines(
                        demand, Map.of(), Set.of());
        assertEquals(1, waiting.size());
        assertEquals(new BigDecimal("10"), waiting.getFirst().remainingQuantity());
        assertEquals(
                WarehouseDemandWaitingReason.NO_AVAILABLE_STOCK,
                waiting.getFirst().effectiveWaitingReason());
        assertTrue(
                WarehouseDemandSupplyTaskRules.qualifiesForSupplyTask(
                        demand, Map.of(), Set.of()));
    }

    @Test
    void activeTransferLineDoesNotQualifyAsWaiting() {
        WarehouseDemandLine line =
                WarehouseDemandLine.create(
                        UUID.randomUUID(),
                        "ART",
                        "Name",
                        "",
                        "шт.",
                        null,
                        StockQuantity.of(new BigDecimal("10")),
                        MaterialReferenceId.generate(),
                        null);
        WarehouseDemand demand =
                WarehouseDemand.accept(
                        UUID.randomUUID(),
                        WarehouseId.generate(),
                        Instant.parse("2026-10-06T10:00:00Z"),
                        "system",
                        List.of(line));

        assertEquals(
                WarehouseDemandDerivedStatus.IN_FULFILLMENT,
                WarehouseDemandStatusDeriver.deriveLineStatus(
                        false, new BigDecimal("10"), BigDecimal.ZERO, true));
        assertFalse(
                WarehouseDemandSupplyTaskRules.qualifiesForSupplyTask(
                        demand, Map.of(), Set.of(line.id())));
    }

    @Test
    void cancelledDemandNeverQualifies() {
        WarehouseDemandLine line =
                WarehouseDemandLine.create(
                        UUID.randomUUID(),
                        "ART",
                        "Name",
                        "",
                        "шт.",
                        null,
                        StockQuantity.of(new BigDecimal("10")),
                        null,
                        WarehouseDemandWaitingReason.MATERIAL_UNMATCHED);
        WarehouseDemand demand =
                WarehouseDemand.of(
                        WarehouseDemandId.generate(),
                        UUID.randomUUID(),
                        WarehouseId.generate(),
                        Instant.parse("2026-10-06T10:00:00Z"),
                        "system",
                        Instant.parse("2026-10-06T11:00:00Z"),
                        "system",
                        1L,
                        List.of(line));
        assertFalse(
                WarehouseDemandSupplyTaskRules.qualifiesForSupplyTask(
                        demand, Map.of(), Set.of()));
    }
}
