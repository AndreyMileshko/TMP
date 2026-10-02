package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.MaterialReadinessQueryService.ItemReleaseQuantity;
import com.tmp.production.application.MaterialReadinessResult.MaterialReadinessLine;
import com.tmp.production.application.MaterialReadinessResult.MaterialReadinessReason;
import com.tmp.production.application.MaterialReadinessResult.MaterialReadinessStatus;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialReadinessQueryServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-02T06:00:00Z");
    private static final UUID PROD_WH = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID MAT_X = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MAT_Y = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private InMemoryItemStateRepository itemStates;
    private FakeWarehouseAvailability warehouse;
    private MaterialReadinessQueryService service;
    private SourceOrderId orderId;
    private SourceOrderItemId itemId;
    private SpecificationId specId;

    @BeforeEach
    void setUp() {
        orderId = SourceOrderId.generate();
        itemId = SourceOrderItemId.generate();
        specId = SpecificationId.generate();
        itemStates = new InMemoryItemStateRepository();
        warehouse = new FakeWarehouseAvailability();
        warehouse.catalog.add(entry(MAT_X, "101.208", "Профиль", "Белый", "м"));
        warehouse.catalog.add(entry(MAT_Y, "101.305", "Фурнитура", "—", "шт"));
        ProductionOrderViewService orderViewService =
                new ProductionOrderViewService(itemStates, sourceOrderId -> false);
        ProductionFoundationQueryService foundation =
                new ProductionFoundationQueryService(new FixedSpecPort());
        service =
                new MaterialReadinessQueryService(
                        orderViewService,
                        foundation,
                        warehouse,
                        new ProductionDestinationWarehouse(PROD_WH));
    }

    @Test
    void readyWhenAvailableExceedsRequired() {
        launchItem(10, bd("100"));
        warehouse.available.put(MAT_X, new BigDecimal("50"));

        MaterialReadinessResult result =
                service.evaluateForRelease(orderId, List.of(new ItemReleaseQuantity(itemId.value(), 4)));

        assertEquals(MaterialReadinessStatus.READY, result.status());
        assertEquals(0, result.deficientLineCount());
        MaterialReadinessLine line = result.lines().getFirst();
        assertEquals(0, new BigDecimal("40").compareTo(line.requiredQuantity()));
        assertEquals(0, new BigDecimal("50").compareTo(line.availableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.shortageQuantity()));
    }

    @Test
    void notReadyWhenAvailableBelowRequired() {
        launchItem(10, bd("100"));
        warehouse.available.put(MAT_X, new BigDecimal("25"));

        MaterialReadinessResult result =
                service.evaluateForRelease(orderId, List.of(new ItemReleaseQuantity(itemId.value(), 4)));

        assertEquals(MaterialReadinessStatus.NOT_READY, result.status());
        assertEquals(1, result.deficientLineCount());
        MaterialReadinessLine line = result.lines().getFirst();
        assertEquals(0, new BigDecimal("40").compareTo(line.requiredQuantity()));
        assertEquals(0, new BigDecimal("25").compareTo(line.availableQuantity()));
        assertEquals(0, new BigDecimal("15").compareTo(line.shortageQuantity()));
    }

    @Test
    void shortageEqualsRequiredWhenAvailableZero() {
        launchItem(10, bd("100"));
        warehouse.available.put(MAT_X, BigDecimal.ZERO);

        MaterialReadinessResult result =
                service.evaluateForRelease(orderId, List.of(new ItemReleaseQuantity(itemId.value(), 4)));

        assertEquals(MaterialReadinessStatus.NOT_READY, result.status());
        assertEquals(0, new BigDecimal("40").compareTo(result.lines().getFirst().shortageQuantity()));
    }

    @Test
    void overallNotReadyWhenAnyMaterialShort() {
        launchItem(10, bd("100"));
        warehouse.available.put(MAT_X, new BigDecimal("100"));
        warehouse.available.put(MAT_Y, new BigDecimal("6"));
        FixedSpecPort.lines =
                List.of(
                        new ResolvedMaterialLine("101.208", "Профиль", "Белый", null, bd("100"), "м"),
                        new ResolvedMaterialLine("101.305", "Фурнитура", "—", null, bd("20"), "шт"));

        MaterialReadinessResult result =
                service.evaluateForRelease(orderId, List.of(new ItemReleaseQuantity(itemId.value(), 4)));

        assertEquals(MaterialReadinessStatus.NOT_READY, result.status());
        assertEquals(1, result.deficientLineCount());
        assertEquals(2, result.lines().size());
    }

    @Test
    void availableAggregatesMultipleCellsAndIgnoresNonAvailable() {
        launchItem(10, bd("100"));
        warehouse.available.put(MAT_X, new BigDecimal("25")); // cells 10+15 AVAILABLE

        MaterialReadinessResult result =
                service.evaluateForRelease(orderId, List.of(new ItemReleaseQuantity(itemId.value(), 2)));

        assertEquals(MaterialReadinessStatus.READY, result.status());
        assertEquals(0, new BigDecimal("25").compareTo(result.lines().getFirst().availableQuantity()));
        assertTrue(
                result.lines().stream()
                        .noneMatch(line -> line.materialCode().contains(PROD_WH.toString())));
    }

    @Test
    void orderRemainderUsesActiveQuantityNotOrdered() {
        ProductionItemState launched = launchItem(10, bd("100"));
        ProductionItemState partial =
                launched.release(ProductionQuantity.positive(6), Instant.parse("2026-10-02T07:00:00Z"));
        itemStates.save(partial);
        warehouse.available.put(MAT_X, new BigDecimal("100"));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.READY, result.status());
        // Q=100 for N=10; releasedBefore=6, release=4 → plan = 40
        assertEquals(0, new BigDecimal("40").compareTo(result.lines().getFirst().requiredQuantity()));
    }

    @Test
    void partialFutureReleaseUsesRequestedQuantityNotFullRemainder() {
        ProductionItemState launched = launchItem(10, bd("100"));
        ProductionItemState partial =
                launched.release(ProductionQuantity.positive(6), Instant.parse("2026-10-02T07:00:00Z"));
        itemStates.save(partial);
        warehouse.available.put(MAT_X, new BigDecimal("100"));

        MaterialReadinessResult forTwo =
                service.evaluateForRelease(
                        orderId, List.of(new ItemReleaseQuantity(itemId.value(), 2)));
        MaterialReadinessResult forFour =
                service.evaluateForRelease(
                        orderId, List.of(new ItemReleaseQuantity(itemId.value(), 4)));

        assertEquals(0, new BigDecimal("20").compareTo(forTwo.lines().getFirst().requiredQuantity()));
        assertEquals(0, new BigDecimal("40").compareTo(forFour.lines().getFirst().requiredQuantity()));
    }

    @Test
    void notAcceptedIsNotApplicable() {
        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);
        assertEquals(MaterialReadinessStatus.NOT_APPLICABLE, result.status());
        assertEquals(MaterialReadinessReason.NOT_ACCEPTED, result.reason());
    }

    @Test
    void manufacturedIsNotApplicable() {
        ProductionItemState launched = launchItem(2, bd("10"));
        itemStates.save(launched.release(ProductionQuantity.positive(2), Instant.parse("2026-10-02T08:00:00Z")));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.NOT_APPLICABLE, result.status());
        assertEquals(MaterialReadinessReason.MANUFACTURED, result.reason());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void cancelledIsNotApplicable() {
        ProductionItemState launched = launchItem(2, bd("10"));
        itemStates.save(launched.cancel(Instant.parse("2026-10-02T08:00:00Z")));
        ProductionOrderViewService cancelledView =
                new ProductionOrderViewService(itemStates, sourceOrderId -> true);
        service =
                new MaterialReadinessQueryService(
                        cancelledView,
                        new ProductionFoundationQueryService(new FixedSpecPort()),
                        warehouse,
                        new ProductionDestinationWarehouse(PROD_WH));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.NOT_APPLICABLE, result.status());
        assertEquals(MaterialReadinessReason.CANCELLED, result.reason());
    }

    @Test
    void noProductionWarehouseReturnsControlledStatus() {
        launchItem(5, bd("10"));
        service =
                new MaterialReadinessQueryService(
                        new ProductionOrderViewService(itemStates, sourceOrderId -> false),
                        new ProductionFoundationQueryService(new FixedSpecPort()),
                        warehouse,
                        new ProductionDestinationWarehouse(Optional::empty));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.NO_PRODUCTION_WAREHOUSE, result.status());
    }

    @Test
    void readyWithoutMaterialRequirementWhenStockSufficient() {
        launchItem(5, bd("10"));
        warehouse.available.put(MAT_X, new BigDecimal("100"));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.READY, result.status());
    }

    @Test
    void notReadyEvenIfSubmittedMrWouldExistWhenStockInsufficient() {
        launchItem(5, bd("10"));
        warehouse.available.put(MAT_X, new BigDecimal("1"));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.NOT_READY, result.status());
    }

    @Test
    void readinessQueryDoesNotCallMutatingWarehouseApis() {
        launchItem(5, bd("10"));
        warehouse.available.put(MAT_X, new BigDecimal("100"));

        service.evaluateOrderRemaining(orderId);

        assertEquals(0, warehouse.mutationAttempts.get());
        assertTrue(warehouse.batchCalls.get() >= 1);
    }

    @Test
    void emptyMaterialSpecIsReady() {
        launchItem(3, bd("10"));
        FixedSpecPort.lines = List.of();

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.READY, result.status());
        assertTrue(result.lines().isEmpty());
    }

    private ProductionItemState launchItem(long ordered, BigDecimal lineQuantity) {
        ProductionItemState state =
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(ordered),
                        T0);
        itemStates.save(state);
        FixedSpecPort.itemId = itemId;
        FixedSpecPort.orderedQuantity = BigDecimal.valueOf(ordered);
        FixedSpecPort.lines =
                List.of(
                        new ResolvedMaterialLine(
                                "101.208", "Профиль", "Белый", null, lineQuantity, "м"));
        return state;
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static MaterialReferenceEntry entry(
            UUID id, String article, String name, String color, String uom) {
        return new MaterialReferenceEntry(id, article, name, color, "", uom);
    }

    private static final class FixedSpecPort implements OrderSpecificationQueryPort {
        static SourceOrderItemId itemId = SourceOrderItemId.generate();
        static BigDecimal orderedQuantity = BigDecimal.TEN;
        static List<ResolvedMaterialLine> lines =
                List.of(new ResolvedMaterialLine("101.208", "Профиль", "Белый", null, bd("10"), "м"));

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            return Optional.of(
                    new ResolvedSpecification(
                            specificationId,
                            itemId,
                            orderedQuantity,
                            List.copyOf(lines)));
        }

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            throw new UnsupportedOperationException("not used in readiness");
        }
    }

    private static final class FakeWarehouseAvailability implements WarehouseAvailabilityQueryPort {
        final List<MaterialReferenceEntry> catalog = new ArrayList<>();
        final Map<UUID, BigDecimal> available = new HashMap<>();
        final AtomicInteger batchCalls = new AtomicInteger();
        final AtomicInteger mutationAttempts = new AtomicInteger();

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            return List.of(new WarehouseCatalogEntry(PROD_WH, "PROD", "Production", true));
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            return List.copyOf(catalog);
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            return available.getOrDefault(materialReferenceId, BigDecimal.ZERO);
        }

        @Override
        public Map<UUID, BigDecimal> availableQuantities(
                UUID warehouseId, Collection<UUID> materialReferenceIds) {
            batchCalls.incrementAndGet();
            Map<UUID, BigDecimal> result = new HashMap<>();
            for (UUID id : materialReferenceIds) {
                BigDecimal qty = available.getOrDefault(id, BigDecimal.ZERO);
                if (qty.signum() > 0) {
                    result.put(id, qty);
                }
            }
            return Map.copyOf(result);
        }
    }

    private static final class InMemoryItemStateRepository implements ProductionItemStateRepository {
        private final Map<String, ProductionItemState> byKey = new HashMap<>();

        @Override
        public ProductionItemState save(ProductionItemState state) {
            byKey.put(key(state), state);
            return state;
        }

        @Override
        public Optional<ProductionItemState> findByIdentity(
                SourceOrderId sourceOrderId,
                SourceOrderItemId sourceOrderItemId,
                SpecificationId specificationId) {
            return Optional.ofNullable(
                    byKey.get(sourceOrderId + ":" + sourceOrderItemId + ":" + specificationId));
        }

        @Override
        public List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId) {
            return byKey.values().stream()
                    .filter(state -> state.sourceOrderId().equals(sourceOrderId))
                    .toList();
        }

        private static String key(ProductionItemState state) {
            return state.sourceOrderId()
                    + ":"
                    + state.sourceOrderItemId()
                    + ":"
                    + state.specificationId();
        }
    }
}
