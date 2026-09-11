package com.tmp.warehouse.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.MaterialDemand;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingOutcome;
import com.tmp.warehouse.api.WarehouseApi.MaterialSourceRoutingResult;
import com.tmp.warehouse.api.WarehouseApi.SourceCellSuggestion;
import com.tmp.warehouse.domain.repository.AvailableStockAggregationQuery;
import com.tmp.warehouse.domain.repository.AvailableStockAggregationQuery.AvailableCellStock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for automatic source routing + cell suggestion (Stage 3.5.4). */
class MaterialSourceRoutingServiceTest {

    private static final UUID DEST = UUID.fromString("00000000-0000-4000-8000-0000000000d0");
    private static final UUID WH_A = UUID.fromString("00000000-0000-4000-8000-0000000000a0");
    private static final UUID WH_B = UUID.fromString("00000000-0000-4000-8000-0000000000b0");
    private static final UUID WH_C = UUID.fromString("00000000-0000-4000-8000-0000000000c0");
    private static final UUID MAT = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID MAT_B = UUID.fromString("00000000-0000-4000-8000-000000000020");
    private static final UUID MAT_C = UUID.fromString("00000000-0000-4000-8000-000000000030");

    private InMemoryAvailableStock stock;
    private MaterialSourceRoutingService routing;

    @BeforeEach
    void setUp() {
        stock = new InMemoryAvailableStock();
        routing = new MaterialSourceRoutingService(stock);
    }

    @Test
    void selectsSingleCompleteSourceWithMaxCoverage() {
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "1-01", "120"));
        stock.add(cell(MAT, WH_B, "B", cellId("c2"), "1-01", "80"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("100"));

        assertEquals(MaterialSourceRoutingOutcome.SOURCE_SELECTED, result.outcome());
        assertEquals(WH_A, result.sourceWarehouseId());
        assertEquals(0, bd("100").compareTo(result.routedQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.uncoveredQuantity()));
    }

    @Test
    void amongCompleteSourcesSelectsMaxAvailable() {
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "1-01", "120"));
        stock.add(cell(MAT, WH_B, "B", cellId("c2"), "1-01", "300"));
        stock.add(cell(MAT, WH_C, "C", cellId("c3"), "1-01", "150"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("100"));

        assertEquals(WH_B, result.sourceWarehouseId());
    }

    @Test
    void whenNoCompleteSourceSelectsMaxPositiveWithoutCrossWarehouseSplit() {
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "1-01", "60"));
        stock.add(cell(MAT, WH_B, "B", cellId("c2"), "1-01", "40"));
        stock.add(cell(MAT, WH_C, "C", cellId("c3"), "1-01", "70"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("100"));

        assertEquals(WH_C, result.sourceWarehouseId());
        assertEquals(0, bd("70").compareTo(result.routedQuantity()));
        assertEquals(0, bd("30").compareTo(result.uncoveredQuantity()));
        assertEquals(1, result.sourceCellSuggestions().size());
    }

    @Test
    void excludesDestinationWarehouseEvenWithLargestStock() {
        stock.add(cell(MAT, DEST, "D", cellId("cd"), "1-01", "1000"));
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "1-01", "200"));
        stock.add(cell(MAT, WH_B, "B", cellId("c2"), "1-01", "50"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("100"));

        assertEquals(WH_A, result.sourceWarehouseId());
    }

    @Test
    void ignoresNonAvailableStatesBecauseQueryReturnsAvailableOnly() {
        // Query port already excludes IN_TRANSIT/BLOCKED; only AVAILABLE rows are present.
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "1-01", "20"));
        stock.add(cell(MAT, WH_B, "B", cellId("c2"), "1-01", "30"));

        assertEquals(WH_B, routing.routeMaterial(DEST, MAT, bd("25")).sourceWarehouseId());
    }

    @Test
    void noAvailableSourceReturnsNormalResult() {
        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("100"));

        assertEquals(MaterialSourceRoutingOutcome.NO_AVAILABLE_SOURCE, result.outcome());
        assertNull(result.sourceWarehouseId());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.routedQuantity()));
        assertEquals(0, bd("100").compareTo(result.uncoveredQuantity()));
        assertTrue(result.sourceCellSuggestions().isEmpty());
    }

    @Test
    void warehouseTieBreakIsDeterministicByCodeThenId() {
        UUID lowId = UUID.fromString("00000000-0000-4000-8000-000000000001");
        UUID highId = UUID.fromString("00000000-0000-4000-8000-000000000002");
        stock.add(cell(MAT, highId, "SAME", cellId("c1"), "Z", "100"));
        stock.add(cell(MAT, lowId, "SAME", cellId("c2"), "A", "100"));

        MaterialSourceRoutingResult first = routing.routeMaterial(DEST, MAT, bd("50"));
        MaterialSourceRoutingResult second = routing.routeMaterial(DEST, MAT, bd("50"));

        assertEquals(lowId, first.sourceWarehouseId());
        assertEquals(first.sourceWarehouseId(), second.sourceWarehouseId());
    }

    @Test
    void cellSuggestionGreedyFullCoverageUsesLargestCellOnly() {
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "1-01", "8"));
        stock.add(cell(MAT, WH_A, "A", cellId("c4"), "1-04", "20"));
        stock.add(cell(MAT, WH_A, "A", cellId("c7"), "1-07", "35"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("28"));

        assertEquals(1, result.sourceCellSuggestions().size());
        SourceCellSuggestion suggestion = result.sourceCellSuggestions().get(0);
        assertEquals(cellId("c7"), suggestion.storageCellId());
        assertEquals(0, bd("28").compareTo(suggestion.suggestedQuantity()));
    }

    @Test
    void cellSuggestionGreedySplitAcrossCells() {
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "C1", "25"));
        stock.add(cell(MAT, WH_A, "A", cellId("c2"), "C2", "10"));
        stock.add(cell(MAT, WH_A, "A", cellId("c3"), "C3", "8"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("40"));

        assertEquals(3, result.sourceCellSuggestions().size());
        assertEquals(cellId("c1"), result.sourceCellSuggestions().get(0).storageCellId());
        assertEquals(0, bd("25").compareTo(result.sourceCellSuggestions().get(0).suggestedQuantity()));
        assertEquals(cellId("c2"), result.sourceCellSuggestions().get(1).storageCellId());
        assertEquals(0, bd("10").compareTo(result.sourceCellSuggestions().get(1).suggestedQuantity()));
        assertEquals(cellId("c3"), result.sourceCellSuggestions().get(2).storageCellId());
        assertEquals(0, bd("5").compareTo(result.sourceCellSuggestions().get(2).suggestedQuantity()));
    }

    @Test
    void cellTieBreakIsDeterministicByCodeThenId() {
        UUID cellLow = UUID.fromString("00000000-0000-4000-8000-0000000000e1");
        UUID cellHigh = UUID.fromString("00000000-0000-4000-8000-0000000000e2");
        stock.add(cell(MAT, WH_A, "A", cellHigh, "SAME", "40"));
        stock.add(cell(MAT, WH_A, "A", cellLow, "SAME", "40"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("30"));

        assertEquals(1, result.sourceCellSuggestions().size());
        assertEquals(cellLow, result.sourceCellSuggestions().get(0).storageCellId());
    }

    @Test
    void suggestCellsForWarehouseUsesOnlyRequestedWarehouse() {
        stock.add(cell(MAT, WH_A, "A", cellId("c1"), "1-01", "40"));
        stock.add(cell(MAT, WH_A, "A", cellId("c2"), "1-02", "30"));
        stock.add(cell(MAT, WH_B, "B", cellId("c3"), "9-01", "1000"));

        List<SourceCellSuggestion> suggestions =
                routing.suggestCellsForWarehouse(WH_A, MAT, bd("50"));

        assertEquals(2, suggestions.size());
        assertEquals(cellId("c1"), suggestions.get(0).storageCellId());
        assertEquals(0, bd("40").compareTo(suggestions.get(0).suggestedQuantity()));
        assertEquals(cellId("c2"), suggestions.get(1).storageCellId());
        assertEquals(0, bd("10").compareTo(suggestions.get(1).suggestedQuantity()));
        assertTrue(suggestions.stream().noneMatch(s -> s.storageCellId().equals(cellId("c3"))));
    }

    @Test
    void insufficientSourceProducesUncoveredAndAllCellSuggestions() {
        stock.add(cell(MAT, WH_A, "A", cellId("ca"), "A", "40"));
        stock.add(cell(MAT, WH_A, "A", cellId("cb"), "B", "20"));

        MaterialSourceRoutingResult result = routing.routeMaterial(DEST, MAT, bd("100"));

        assertEquals(WH_A, result.sourceWarehouseId());
        assertEquals(0, bd("60").compareTo(result.routedQuantity()));
        assertEquals(0, bd("40").compareTo(result.uncoveredQuantity()));
        assertEquals(2, result.sourceCellSuggestions().size());
    }

    @Test
    void batchPreservesInputOrderAndCorrelationWithoutMergingDuplicates() {
        stock.add(cell(MAT, WH_A, "A", cellId("a1"), "1", "100"));
        stock.add(cell(MAT_B, WH_B, "B", cellId("b1"), "1", "50"));

        List<MaterialSourceRoutingResult> results =
                routing.routeMaterials(
                        DEST,
                        List.of(
                                new MaterialDemand("line-a", MAT, bd("10")),
                                new MaterialDemand("line-b", MAT_B, bd("10")),
                                new MaterialDemand("line-c", MAT_C, bd("10")),
                                new MaterialDemand("line-a2", MAT, bd("5"))));

        assertEquals(4, results.size());
        assertEquals("line-a", results.get(0).demandKey());
        assertEquals(WH_A, results.get(0).sourceWarehouseId());
        assertEquals("line-b", results.get(1).demandKey());
        assertEquals(WH_B, results.get(1).sourceWarehouseId());
        assertEquals("line-c", results.get(2).demandKey());
        assertEquals(MaterialSourceRoutingOutcome.NO_AVAILABLE_SOURCE, results.get(2).outcome());
        assertEquals("line-a2", results.get(3).demandKey());
        assertEquals(WH_A, results.get(3).sourceWarehouseId());
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> routing.routeMaterial(DEST, MAT, BigDecimal.ZERO));
    }

    private static AvailableCellStock cell(
            UUID materialId,
            UUID warehouseId,
            String warehouseCode,
            UUID cellId,
            String cellCode,
            String qty) {
        return new AvailableCellStock(
                materialId, warehouseId, warehouseCode, cellId, cellCode, bd(qty));
    }

    private static UUID cellId(String suffix) {
        // Stable deterministic cell ids for tests (hex-only suffix).
        return switch (suffix) {
            case "c1" -> UUID.fromString("00000000-0000-4000-8000-0000000000c1");
            case "c2" -> UUID.fromString("00000000-0000-4000-8000-0000000000c2");
            case "c3" -> UUID.fromString("00000000-0000-4000-8000-0000000000c3");
            case "c4" -> UUID.fromString("00000000-0000-4000-8000-0000000000c4");
            case "c7" -> UUID.fromString("00000000-0000-4000-8000-0000000000c7");
            case "ca" -> UUID.fromString("00000000-0000-4000-8000-0000000000ca");
            case "cb" -> UUID.fromString("00000000-0000-4000-8000-0000000000cb");
            case "cd" -> UUID.fromString("00000000-0000-4000-8000-0000000000cd");
            case "a1" -> UUID.fromString("00000000-0000-4000-8000-0000000000a1");
            case "b1" -> UUID.fromString("00000000-0000-4000-8000-0000000000b1");
            default -> throw new IllegalArgumentException("unknown cell suffix: " + suffix);
        };
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class InMemoryAvailableStock implements AvailableStockAggregationQuery {
        private final List<AvailableCellStock> rows = new ArrayList<>();

        void add(AvailableCellStock row) {
            rows.add(row);
        }

        @Override
        public List<AvailableCellStock> findAvailableByMaterials(
                Collection<UUID> materialReferenceIds) {
            return rows.stream()
                    .filter(row -> materialReferenceIds.contains(row.materialReferenceId()))
                    .toList();
        }
    }
}
