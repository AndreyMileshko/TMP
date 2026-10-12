package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.MaterialRequirementProductionStockQueryService.LineProductionStock;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialRequirementProductionStockQueryServiceTest {

    private static final UUID PROD_WH = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MAIN_WH = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID MAT_001 = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final Instant T0 = Instant.parse("2026-10-07T00:00:00Z");

    private FakeWarehouseAvailability warehouse;
    private MaterialRequirementProductionStockQueryService service;

    @BeforeEach
    void setUp() {
        warehouse = new FakeWarehouseAvailability();
        service = new MaterialRequirementProductionStockQueryService(warehouse);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void test002Semantics_fullRequestNotReducedByProductionStock() {
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        warehouse.available.put(MAT_001, bd("20"));

        MaterialRequirement requirement = requirement(115L, line("MAT-001", "Белый", "шт", bd("230")));

        LineProductionStock stock = service.stockFor(requirement).getFirst();

        assertEquals(0, bd("20").compareTo(stock.onProductionAvailable()));
        assertEquals(0, bd("230").compareTo(requirement.lines().getFirst().quantity()));
        assertEquals(1, warehouse.batchCalls.get());
    }

    @Test
    void existingStockBeforeRequest_notSubtracted() {
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        warehouse.available.put(MAT_001, bd("6"));

        MaterialRequirement requirement = requirement(115L, line("MAT-001", "Белый", "шт", bd("230")));

        LineProductionStock stock = service.stockFor(requirement).getFirst();
        assertEquals(0, bd("6").compareTo(stock.onProductionAvailable()));
        assertEquals(0, bd("230").compareTo(requirement.lines().getFirst().quantity()));
    }

    @Test
    void zeroStockShowsZeroNotDash() {
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));

        LineProductionStock stock =
                service.stockFor(requirement(5L, line("MAT-001", "Белый", "шт", bd("10"))))
                        .getFirst();

        assertEquals(0, BigDecimal.ZERO.compareTo(stock.onProductionAvailable()));
    }

    @Test
    void unmatchedShowsNullDash() {
        LineProductionStock stock =
                service.stockFor(requirement(5L, line("UNKNOWN", "Белый", "шт", bd("10"))))
                        .getFirst();

        assertNull(stock.onProductionAvailable());
        assertEquals(0, warehouse.batchCalls.get());
    }

    @Test
    void ambiguousShowsNullDash() {
        UUID other = UUID.fromString("44444444-4444-4444-8444-444444444444");
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        warehouse.catalog.add(entry(other, "MAT-001", "Белый", "шт"));
        warehouse.available.put(MAT_001, bd("20"));
        warehouse.available.put(other, bd("5"));

        LineProductionStock stock =
                service.stockFor(requirement(115L, line("MAT-001", "Белый", "шт", bd("230"))))
                        .getFirst();

        assertNull(stock.onProductionAvailable());
        assertEquals(0, warehouse.batchCalls.get());
    }

    @Test
    void inTransitExcluded() {
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        warehouse.available.put(MAT_001, bd("5"));
        warehouse.inTransit.put(MAT_001, bd("100"));

        LineProductionStock stock =
                service.stockFor(requirement(115L, line("MAT-001", "Белый", "шт", bd("230"))))
                        .getFirst();

        assertEquals(0, bd("5").compareTo(stock.onProductionAvailable()));
    }

    @Test
    void otherWarehouseExcluded() {
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        warehouse.available.put(MAT_001, bd("20"));
        warehouse.mainAvailable.put(MAT_001, bd("500"));

        LineProductionStock stock =
                service.stockFor(requirement(115L, line("MAT-001", "Белый", "шт", bd("230"))))
                        .getFirst();

        assertEquals(0, bd("20").compareTo(stock.onProductionAvailable()));
    }

    @Test
    void multipleProductionCellsSummedViaBatch() {
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        // Adapter sums cells; fake exposes already-summed AVAILABLE
        warehouse.available.put(MAT_001, bd("20"));

        LineProductionStock stock =
                service.stockFor(requirement(115L, line("MAT-001", "Белый", "шт", bd("230"))))
                        .getFirst();

        assertEquals(0, bd("20").compareTo(stock.onProductionAvailable()));
        assertEquals(1, warehouse.batchCalls.get());
        assertEquals(0, warehouse.singleCalls.get());
    }

    @Test
    void flexibleRecalcQuantityIndependentOfStock() {
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        warehouse.available.put(MAT_001, bd("6"));

        MaterialRequirement requirement = requirement(10L, line("MAT-001", "Белый", "шт", bd("20")));

        LineProductionStock stock = service.stockFor(requirement).getFirst();
        assertEquals(0, bd("6").compareTo(stock.onProductionAvailable()));
        assertEquals(0, bd("20").compareTo(requirement.lines().getFirst().quantity()));
    }

    @Test
    void batchLookupOnceForMultipleResolvedLines() {
        UUID mat2 = UUID.fromString("55555555-5555-4555-8555-555555555555");
        warehouse.catalog.add(entry(MAT_001, "MAT-001", "Белый", "шт"));
        warehouse.catalog.add(entry(mat2, "MAT-002", "Серый", "м"));
        warehouse.available.put(MAT_001, new BigDecimal("20"));
        warehouse.available.put(mat2, new BigDecimal("3"));

        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        MaterialRequirementLine line1 =
                MaterialRequirementLine.create(
                        null,
                        "MAT-001",
                        "Material 1",
                        "Белый",
                        "шт",
                        null,
                        new BigDecimal("230"),
                        List.of(
                                MaterialRequirementLineContribution.of(
                                        orderId, itemId, new BigDecimal("230"))));
        MaterialRequirementLine line2 =
                MaterialRequirementLine.create(
                        null,
                        "MAT-002",
                        "Material 2",
                        "Серый",
                        "м",
                        null,
                        new BigDecimal("40"),
                        List.of(
                                MaterialRequirementLineContribution.of(
                                        orderId, itemId, new BigDecimal("40"))));
        MaterialRequirement requirement =
                MaterialRequirement.create(
                        PROD_WH,
                        T0,
                        List.of(MaterialRequirementSourceItem.of(orderId, itemId, 115L)),
                        List.of(line1, line2));

        List<LineProductionStock> stocks = service.stockFor(requirement);

        assertEquals(2, stocks.size());
        assertEquals(1, warehouse.batchCalls.get());
        assertTrue(warehouse.lastBatchSize.get() >= 2);
    }

    private static MaterialReferenceEntry entry(
            UUID id, String article, String color, String uom) {
        return new MaterialReferenceEntry(id, article, article, color, "", uom);
    }

    private static MaterialRequirementLine line(
            String code, String color, String uom, BigDecimal qty) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        return MaterialRequirementLine.create(
                null,
                code,
                code,
                color,
                uom,
                null,
                qty,
                List.of(MaterialRequirementLineContribution.of(orderId, itemId, qty)));
    }

    private static MaterialRequirement requirement(long products, MaterialRequirementLine line) {
        MaterialRequirementLineContribution contribution = line.contributions().getFirst();
        return MaterialRequirement.create(
                PROD_WH,
                T0,
                List.of(
                        MaterialRequirementSourceItem.of(
                                contribution.sourceOrderId(),
                                contribution.sourceOrderItemId(),
                                products)),
                List.of(line));
    }

    private static final class FakeWarehouseAvailability implements WarehouseAvailabilityQueryPort {
        final List<MaterialReferenceEntry> catalog = new ArrayList<>();
        final Map<UUID, BigDecimal> available = new HashMap<>();
        final Map<UUID, BigDecimal> mainAvailable = new HashMap<>();
        final Map<UUID, BigDecimal> inTransit = new HashMap<>();
        final AtomicInteger batchCalls = new AtomicInteger();
        final AtomicInteger singleCalls = new AtomicInteger();
        final AtomicInteger lastBatchSize = new AtomicInteger();

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            return List.of(
                    new WarehouseCatalogEntry(PROD_WH, "PROD", "Production", true),
                    new WarehouseCatalogEntry(MAIN_WH, "MAIN", "Main", true));
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            return List.copyOf(catalog);
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            singleCalls.incrementAndGet();
            if (MAIN_WH.equals(warehouseId)) {
                return mainAvailable.getOrDefault(materialReferenceId, BigDecimal.ZERO);
            }
            return available.getOrDefault(materialReferenceId, BigDecimal.ZERO);
        }

        @Override
        public Map<UUID, BigDecimal> availableQuantities(
                UUID warehouseId, Collection<UUID> materialReferenceIds) {
            batchCalls.incrementAndGet();
            lastBatchSize.set(materialReferenceIds.size());
            Map<UUID, BigDecimal> result = new HashMap<>();
            if (!PROD_WH.equals(warehouseId)) {
                return Map.copyOf(result);
            }
            for (UUID id : materialReferenceIds) {
                BigDecimal qty = available.getOrDefault(id, BigDecimal.ZERO);
                if (qty.signum() > 0) {
                    result.put(id, qty);
                }
            }
            // inTransit intentionally ignored
            return Map.copyOf(result);
        }
    }
}
