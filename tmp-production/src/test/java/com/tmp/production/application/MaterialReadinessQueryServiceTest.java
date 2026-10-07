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
    private static final UUID SOURCE_WH = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID MAT_X = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MAT_Y = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID MAT_A = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MAT_B = UUID.fromString("44444444-4444-4444-8444-444444444444");

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
        warehouse.catalog.add(entry(MAT_X, "MAT-001", "Профиль", "Белый", "шт"));
        warehouse.catalog.add(entry(MAT_Y, "101.305", "Фурнитура", "—", "шт"));
        warehouse.catalog.add(entry(MAT_A, "MAT-A", "Материал A", "—", "шт"));
        warehouse.catalog.add(entry(MAT_B, "MAT-B", "Материал B", "—", "шт"));
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
    void manualAcceptanceCaseTest002RequiredIsNormTimesRemaining() {
        // Order qty 115, released 0, MAT-001 norm 2, production AVAILABLE 6
        launchItem(115, bd("2"), "MAT-001", MAT_X);
        warehouse.available.put(MAT_X, new BigDecimal("6"));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.NOT_READY, result.status());
        MaterialReadinessLine line = result.lines().getFirst();
        assertEquals(0, new BigDecimal("230").compareTo(line.requiredQuantity()));
        assertEquals(0, new BigDecimal("6").compareTo(line.availableQuantity()));
        assertEquals(0, new BigDecimal("224").compareTo(line.shortageQuantity()));
    }

    @Test
    void partialReleaseRemainingUsesActiveQuantityOnly() {
        ProductionItemState launched = launchItem(10, bd("2"), "MAT-001", MAT_X);
        itemStates.save(
                launched.release(ProductionQuantity.positive(4), Instant.parse("2026-10-02T07:00:00Z")));
        warehouse.available.put(MAT_X, new BigDecimal("12"));

        MaterialReadinessResult ready = service.evaluateOrderRemaining(orderId);
        assertEquals(MaterialReadinessStatus.READY, ready.status());
        assertEquals(0, new BigDecimal("12").compareTo(ready.lines().getFirst().requiredQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(ready.lines().getFirst().shortageQuantity()));

        warehouse.available.put(MAT_X, new BigDecimal("11"));
        MaterialReadinessResult shortOne = service.evaluateOrderRemaining(orderId);
        assertEquals(MaterialReadinessStatus.NOT_READY, shortOne.status());
        assertEquals(0, new BigDecimal("1").compareTo(shortOne.lines().getFirst().shortageQuantity()));
    }

    @Test
    void releaseSelectedQuantityTimesNormNotFullRemainder() {
        launchItem(10, bd("2"), "MAT-001", MAT_X);
        warehouse.available.put(MAT_X, new BigDecimal("100"));

        MaterialReadinessResult forThree =
                service.evaluateForRelease(
                        orderId, List.of(new ItemReleaseQuantity(itemId.value(), 3)));

        assertEquals(0, new BigDecimal("6").compareTo(forThree.lines().getFirst().requiredQuantity()));
    }

    @Test
    void multipleMaterialsScaleIndependently() {
        launchItem(5, bd("2"), "MAT-A", MAT_A);
        FixedSpecPort.lines =
                List.of(
                        new ResolvedMaterialLine("MAT-A", "Материал A", "—", null, bd("2"), "шт"),
                        new ResolvedMaterialLine("MAT-B", "Материал B", "—", null, bd("3"), "шт"));
        warehouse.available.put(MAT_A, new BigDecimal("100"));
        warehouse.available.put(MAT_B, new BigDecimal("100"));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        Map<String, BigDecimal> byCode = new HashMap<>();
        for (MaterialReadinessLine line : result.lines()) {
            byCode.put(line.materialCode(), line.requiredQuantity());
        }
        assertEquals(0, new BigDecimal("10").compareTo(byCode.get("MAT-A")));
        assertEquals(0, new BigDecimal("15").compareTo(byCode.get("MAT-B")));
    }

    @Test
    void sameMaterialAcrossItemsAggregatesPerItemThenSums() {
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemA, specA, T0),
                        ProductionQuantity.positive(3),
                        T0));
        itemStates.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemB, specB, T0),
                        ProductionQuantity.positive(4),
                        T0));
        MultiItemSpecPort multi = new MultiItemSpecPort();
        multi.put(specA, itemA, 3, List.of(line("MAT-X", "MAT-X", bd("2"), "шт")));
        multi.put(specB, itemB, 4, List.of(line("MAT-X", "MAT-X", bd("5"), "шт")));
        warehouse.catalog.clear();
        warehouse.catalog.add(entry(MAT_X, "MAT-X", "Shared", "—", "шт"));
        warehouse.available.put(MAT_X, new BigDecimal("100"));
        service =
                new MaterialReadinessQueryService(
                        new ProductionOrderViewService(itemStates, sourceOrderId -> false),
                        new ProductionFoundationQueryService(multi),
                        warehouse,
                        new ProductionDestinationWarehouse(PROD_WH));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(1, result.lines().size());
        assertEquals(0, new BigDecimal("26").compareTo(result.lines().getFirst().requiredQuantity()));
    }

    @Test
    void zeroRemainingIsNotApplicableWithoutFakeShortage() {
        ProductionItemState launched = launchItem(10, bd("2"), "MAT-001", MAT_X);
        itemStates.save(
                launched.release(
                        ProductionQuantity.positive(10), Instant.parse("2026-10-02T08:00:00Z")));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.NOT_APPLICABLE, result.status());
        assertEquals(MaterialReadinessReason.MANUFACTURED, result.reason());
        assertTrue(result.lines().isEmpty());
    }

    @Test
    void availableUsesProductionWarehouseOnlyNotSourceStock() {
        launchItem(115, bd("2"), "MAT-001", MAT_X);
        warehouse.available.put(MAT_X, new BigDecimal("6"));
        warehouse.sourceWarehouseStock.put(MAT_X, new BigDecimal("1000"));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(0, new BigDecimal("6").compareTo(result.lines().getFirst().availableQuantity()));
        assertEquals(MaterialReadinessStatus.NOT_READY, result.status());
    }

    @Test
    void inTransitDoesNotCountAsAvailable() {
        launchItem(115, bd("2"), "MAT-001", MAT_X);
        warehouse.available.put(MAT_X, new BigDecimal("6"));
        warehouse.inTransit.put(MAT_X, new BigDecimal("100"));

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(0, new BigDecimal("6").compareTo(result.lines().getFirst().availableQuantity()));
        assertEquals(0, new BigDecimal("224").compareTo(result.lines().getFirst().shortageQuantity()));
    }

    @Test
    void readyWhenAvailableExceedsRequired() {
        launchItem(10, bd("10"), "MAT-001", MAT_X);
        warehouse.available.put(MAT_X, new BigDecimal("50"));

        MaterialReadinessResult result =
                service.evaluateForRelease(orderId, List.of(new ItemReleaseQuantity(itemId.value(), 4)));

        assertEquals(MaterialReadinessStatus.READY, result.status());
        MaterialReadinessLine line = result.lines().getFirst();
        assertEquals(0, new BigDecimal("40").compareTo(line.requiredQuantity()));
        assertEquals(0, new BigDecimal("50").compareTo(line.availableQuantity()));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.shortageQuantity()));
    }

    @Test
    void notReadyWhenAvailableBelowRequired() {
        launchItem(10, bd("10"), "MAT-001", MAT_X);
        warehouse.available.put(MAT_X, new BigDecimal("25"));

        MaterialReadinessResult result =
                service.evaluateForRelease(orderId, List.of(new ItemReleaseQuantity(itemId.value(), 4)));

        assertEquals(MaterialReadinessStatus.NOT_READY, result.status());
        MaterialReadinessLine line = result.lines().getFirst();
        assertEquals(0, new BigDecimal("40").compareTo(line.requiredQuantity()));
        assertEquals(0, new BigDecimal("15").compareTo(line.shortageQuantity()));
    }

    @Test
    void notAcceptedIsNotApplicable() {
        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);
        assertEquals(MaterialReadinessStatus.NOT_APPLICABLE, result.status());
        assertEquals(MaterialReadinessReason.NOT_ACCEPTED, result.reason());
    }

    @Test
    void noProductionWarehouseReturnsControlledStatus() {
        launchItem(5, bd("2"), "MAT-001", MAT_X);
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
    void readinessQueryDoesNotCallMutatingWarehouseApis() {
        launchItem(5, bd("2"), "MAT-001", MAT_X);
        warehouse.available.put(MAT_X, new BigDecimal("100"));

        service.evaluateOrderRemaining(orderId);

        assertEquals(0, warehouse.mutationAttempts.get());
        assertTrue(warehouse.batchCalls.get() >= 1);
    }

    @Test
    void emptyMaterialSpecIsReady() {
        launchItem(3, bd("2"), "MAT-001", MAT_X);
        FixedSpecPort.lines = List.of();

        MaterialReadinessResult result = service.evaluateOrderRemaining(orderId);

        assertEquals(MaterialReadinessStatus.READY, result.status());
        assertTrue(result.lines().isEmpty());
    }

    private ProductionItemState launchItem(
            long ordered, BigDecimal lineQuantity, String materialCode, UUID materialId) {
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
                                materialCode, materialCode, "Белый", null, lineQuantity, "шт"));
        if (warehouse.catalog.stream().noneMatch(e -> e.materialReferenceId().equals(materialId))) {
            warehouse.catalog.add(entry(materialId, materialCode, materialCode, "Белый", "шт"));
        }
        return state;
    }

    private static ResolvedMaterialLine line(
            String code, String name, BigDecimal qty, String uom) {
        return new ResolvedMaterialLine(code, name, "—", null, qty, uom);
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
                List.of(new ResolvedMaterialLine("MAT-001", "MAT-001", "Белый", null, bd("2"), "шт"));

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            return Optional.of(
                    new ResolvedSpecification(
                            specificationId, itemId, orderedQuantity, List.copyOf(lines)));
        }

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            throw new UnsupportedOperationException("not used in readiness");
        }
    }

    private static final class MultiItemSpecPort implements OrderSpecificationQueryPort {
        private final Map<SpecificationId, ResolvedSpecification> byId = new HashMap<>();

        void put(
                SpecificationId specId,
                SourceOrderItemId itemId,
                long ordered,
                List<ResolvedMaterialLine> lines) {
            byId.put(
                    specId,
                    new ResolvedSpecification(
                            specId, itemId, BigDecimal.valueOf(ordered), List.copyOf(lines)));
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            return Optional.ofNullable(byId.get(specificationId));
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
        final Map<UUID, BigDecimal> sourceWarehouseStock = new HashMap<>();
        final Map<UUID, BigDecimal> inTransit = new HashMap<>();
        final AtomicInteger batchCalls = new AtomicInteger();
        final AtomicInteger mutationAttempts = new AtomicInteger();

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            return List.of(
                    new WarehouseCatalogEntry(PROD_WH, "PROD", "Production", true),
                    new WarehouseCatalogEntry(SOURCE_WH, "MAIN", "Main", true));
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            return List.copyOf(catalog);
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            if (!PROD_WH.equals(warehouseId)) {
                return sourceWarehouseStock.getOrDefault(materialReferenceId, BigDecimal.ZERO);
            }
            return available.getOrDefault(materialReferenceId, BigDecimal.ZERO);
        }

        @Override
        public Map<UUID, BigDecimal> availableQuantities(
                UUID warehouseId, Collection<UUID> materialReferenceIds) {
            batchCalls.incrementAndGet();
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
            // inTransit intentionally ignored — readiness only sees AVAILABLE
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
