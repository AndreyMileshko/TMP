package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementNotAllowedException;
import com.tmp.production.domain.MaterialRequirementNotReadyException;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementSelectionException;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialRequirementServiceTest {

    private static final Instant T0 = Instant.parse("2026-09-09T10:00:00Z");
    private static final UUID PROD_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private InMemoryItemRepository itemRepository;
    private InMemoryRequirementRepository requirementRepository;
    private TrackingSpecificationQuery specificationQuery;
    private TrackingWarehouseQuery warehouseQuery;
    private MaterialRequirementService service;
    private SourceOrderId orderId;

    @BeforeEach
    void setUp() {
        itemRepository = new InMemoryItemRepository();
        requirementRepository = new InMemoryRequirementRepository();
        specificationQuery = new TrackingSpecificationQuery();
        warehouseQuery = new TrackingWarehouseQuery();
        warehouseQuery.warehouses =
                List.of(
                        new WarehouseCatalogEntry(PROD_WAREHOUSE, "PROD", "Production", true),
                        new WarehouseCatalogEntry(OTHER_WAREHOUSE, "OTHER", "Other", true));

        ProductionOrderViewService viewService = new ProductionOrderViewService(itemRepository);
        ProductionFoundationQueryService foundationQuery =
                new ProductionFoundationQueryService(specificationQuery);
        service =
                new MaterialRequirementService(
                        viewService,
                        foundationQuery,
                        new ProductionDestinationWarehouse(PROD_WAREHOUSE),
                        warehouseQuery,
                        requirementRepository,
                        Clock.fixed(T0, ZoneOffset.UTC));
        orderId = SourceOrderId.generate();
    }

    @Test
    void preparesRequirementForOneSelectedItem() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId);
        specificationQuery.byIdSpec =
                Optional.of(spec(specId, itemId, List.of(materialLine("MAT-A", "WHITE", "PCS", 10))));
        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-A", "Catalog A", "WHITE", "PCS"));

        MaterialRequirement requirement =
                service.prepareMaterialRequirement(orderId, List.of(itemId));

        assertEquals(PROD_WAREHOUSE, requirement.destinationWarehouseId());
        assertEquals(1, requirement.lines().size());
        MaterialRequirementLine line = requirement.lines().getFirst();
        assertEquals(0, line.quantity().compareTo(BigDecimal.TEN));
        assertEquals("Catalog A", line.materialName());
        assertTrue(line.sourceOrderItemIds().contains(itemId));
        assertEquals(0, warehouseQuery.availableQuantityCalls.get());
    }

    @Test
    void preparesRequirementForMultipleSelectedItems() {
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();
        launchItem(orderId, itemA, specA);
        launchItem(orderId, itemB, specB);
        specificationQuery.byIdResolver =
                id -> {
                    if (id.equals(specA)) {
                        return Optional.of(
                                spec(specA, itemA, List.of(materialLine("MAT-A", "WHITE", "PCS", 4))));
                    }
                    if (id.equals(specB)) {
                        return Optional.of(
                                spec(specB, itemB, List.of(materialLine("MAT-B", "BLACK", "PCS", 6))));
                    }
                    return Optional.empty();
                };
        warehouseQuery.materialReferences =
                List.of(
                        reference(UUID.randomUUID(), "MAT-A", "A", "WHITE", "PCS"),
                        reference(UUID.randomUUID(), "MAT-B", "B", "BLACK", "PCS"));

        MaterialRequirement requirement =
                service.prepareMaterialRequirement(orderId, List.of(itemA, itemB));

        assertEquals(2, requirement.lines().size());
        assertEquals(0, warehouseQuery.availableQuantityCalls.get());
    }

    @Test
    void aggregatesSameMaterialAcrossSelectedItems() {
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();
        launchItem(orderId, itemA, specA);
        launchItem(orderId, itemB, specB);
        specificationQuery.byIdResolver =
                id -> {
                    if (id.equals(specA)) {
                        return Optional.of(
                                spec(specA, itemA, List.of(materialLine("MAT-X", "WHITE", "PCS", 5))));
                    }
                    if (id.equals(specB)) {
                        return Optional.of(
                                spec(specB, itemB, List.of(materialLine("MAT-X", "WHITE", "PCS", 7))));
                    }
                    return Optional.empty();
                };
        warehouseQuery.materialReferences =
                List.of(reference(UUID.randomUUID(), "MAT-X", "X", "WHITE", "PCS"));

        MaterialRequirementLine line =
                service.prepareMaterialRequirement(orderId, List.of(itemA, itemB)).lines().getFirst();

        assertEquals(0, line.quantity().compareTo(BigDecimal.valueOf(12)));
        assertEquals(2, line.sourceOrderItemIds().size());
        assertTrue(line.sourceOrderItemIds().contains(itemA));
        assertTrue(line.sourceOrderItemIds().contains(itemB));
    }

    @Test
    void unselectedItemContributesNothing() {
        SourceOrderItemId selected = SourceOrderItemId.generate();
        SourceOrderItemId unselected = SourceOrderItemId.generate();
        SpecificationId selectedSpec = SpecificationId.generate();
        SpecificationId unselectedSpec = SpecificationId.generate();
        launchItem(orderId, selected, selectedSpec);
        launchItem(orderId, unselected, unselectedSpec);
        specificationQuery.byIdResolver =
                id -> {
                    if (id.equals(selectedSpec)) {
                        return Optional.of(
                                spec(
                                        selectedSpec,
                                        selected,
                                        List.of(materialLine("MAT-S", "WHITE", "PCS", 3))));
                    }
                    if (id.equals(unselectedSpec)) {
                        return Optional.of(
                                spec(
                                        unselectedSpec,
                                        unselected,
                                        List.of(materialLine("MAT-U", "BLACK", "PCS", 99))));
                    }
                    return Optional.empty();
                };
        warehouseQuery.materialReferences =
                List.of(
                        reference(UUID.randomUUID(), "MAT-S", "S", "WHITE", "PCS"),
                        reference(UUID.randomUUID(), "MAT-U", "U", "BLACK", "PCS"));

        MaterialRequirement requirement =
                service.prepareMaterialRequirement(orderId, List.of(selected));

        assertEquals(1, requirement.lines().size());
        assertEquals("MAT-S", requirement.lines().getFirst().materialCode());
        assertEquals(0, requirement.lines().getFirst().quantity().compareTo(BigDecimal.valueOf(3)));
    }

    @Test
    void rejectsEmptySelection() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, SpecificationId.generate());

        assertThrows(
                MaterialRequirementSelectionException.class,
                () -> service.prepareMaterialRequirement(orderId, List.of()));
        assertEquals(0, warehouseQuery.availableQuantityCalls.get());
    }

    @Test
    void rejectsItemFromAnotherOrder() {
        SourceOrderItemId localItem = SourceOrderItemId.generate();
        launchItem(orderId, localItem, SpecificationId.generate());
        SourceOrderId otherOrder = SourceOrderId.generate();
        SourceOrderItemId foreignItem = SourceOrderItemId.generate();
        launchItem(otherOrder, foreignItem, SpecificationId.generate());

        assertThrows(
                MaterialRequirementSelectionException.class,
                () -> service.prepareMaterialRequirement(orderId, List.of(foreignItem)));
    }

    @Test
    void rejectsReleasedAndCancelledItems() {
        SourceOrderItemId releasedItem = SourceOrderItemId.generate();
        SourceOrderItemId activeItem = SourceOrderItemId.generate();
        launchItem(orderId, releasedItem, SpecificationId.generate());
        launchItem(orderId, activeItem, SpecificationId.generate());
        ProductionItemState released =
                itemRepository.findBySourceOrderId(orderId).stream()
                        .filter(state -> state.sourceOrderItemId().equals(releasedItem))
                        .findFirst()
                        .orElseThrow();
        itemRepository.save(
                ProductionItemState.rehydrate(
                        released.foundation(),
                        ProductionStatus.RELEASED,
                        released.orderedQuantity(),
                        released.launchedQuantity(),
                        ProductionQuantity.zero(),
                        released.orderedQuantity(),
                        null,
                        T0));

        assertThrows(
                MaterialRequirementSelectionException.class,
                () -> service.prepareMaterialRequirement(orderId, List.of(releasedItem)));

        SourceOrderId mixedOrder = SourceOrderId.generate();
        SourceOrderItemId stillActive = SourceOrderItemId.generate();
        SourceOrderItemId cancelled = SourceOrderItemId.generate();
        launchItem(mixedOrder, stillActive, SpecificationId.generate());
        launchItem(mixedOrder, cancelled, SpecificationId.generate());
        itemRepository.save(
                itemRepository.findBySourceOrderId(mixedOrder).stream()
                        .filter(state -> state.sourceOrderItemId().equals(cancelled))
                        .findFirst()
                        .orElseThrow()
                        .cancel(T0));

        assertThrows(
                MaterialRequirementSelectionException.class,
                () -> service.prepareMaterialRequirement(mixedOrder, List.of(cancelled)));
    }

    @Test
    void changesQuantityFrom28To30() {
        MaterialRequirement requirement = prepareSimpleRequirement(BigDecimal.valueOf(28));
        MaterialRequirementLineId lineId = requirement.lines().getFirst().lineId();

        MaterialRequirement edited =
                service.changeQuantity(requirement.requirementId(), lineId, BigDecimal.valueOf(30));

        assertEquals(0, edited.lines().getFirst().quantity().compareTo(BigDecimal.valueOf(30)));
        assertEquals(1L, edited.version());
    }

    @Test
    void rejectsNonPositiveQuantityChange() {
        MaterialRequirement requirement = prepareSimpleRequirement(BigDecimal.TEN);
        MaterialRequirementLineId lineId = requirement.lines().getFirst().lineId();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.changeQuantity(
                                requirement.requirementId(), lineId, BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.changeQuantity(
                                requirement.requirementId(), lineId, BigDecimal.valueOf(-1)));
    }

    @Test
    void rejectsUnresolvedMaterial() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(specId, itemId, List.of(materialLine("UNKNOWN", "WHITE", "PCS", 1))));
        warehouseQuery.materialReferences = List.of();

        MaterialRequirementNotReadyException ex =
                assertThrows(
                        MaterialRequirementNotReadyException.class,
                        () -> service.prepareMaterialRequirement(orderId, List.of(itemId)));
        assertEquals(MaterialRequirementNotReadyException.Problem.UNRESOLVED, ex.problem());
        assertEquals(0, warehouseQuery.availableQuantityCalls.get());
    }

    @Test
    void rejectsAmbiguousMaterial() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId);
        specificationQuery.byIdSpec =
                Optional.of(spec(specId, itemId, List.of(materialLine("AMB", "WHITE", "PCS", 1))));
        warehouseQuery.materialReferences =
                List.of(
                        reference(UUID.randomUUID(), "AMB", "A1", "WHITE", "PCS"),
                        reference(UUID.randomUUID(), "AMB", "A2", "WHITE", "PCS"));

        MaterialRequirementNotReadyException ex =
                assertThrows(
                        MaterialRequirementNotReadyException.class,
                        () -> service.prepareMaterialRequirement(orderId, List.of(itemId)));
        assertEquals(MaterialRequirementNotReadyException.Problem.AMBIGUOUS, ex.problem());
        assertEquals(0, warehouseQuery.availableQuantityCalls.get());
    }

    @Test
    void prepareDoesNotCallAvailableQuantity() {
        prepareSimpleRequirement(BigDecimal.TEN);
        assertEquals(0, warehouseQuery.availableQuantityCalls.get());
        assertTrue(warehouseQuery.listMaterialReferencesCalls.get() >= 1);
        assertTrue(warehouseQuery.listWarehousesCalls.get() >= 1);
    }

    @Test
    void usesFrozenSpecificationIdNotCurrent() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId frozenSpecA = SpecificationId.generate();
        SpecificationId currentSpecB = SpecificationId.generate();
        ProductionItemState launched = launchItem(orderId, itemId, frozenSpecA, 1L);
        assertEquals(frozenSpecA, launched.foundation().specificationId());

        specificationQuery.byIdResolver =
                id -> {
                    if (id.equals(frozenSpecA)) {
                        return Optional.of(
                                spec(
                                        frozenSpecA,
                                        itemId,
                                        BigDecimal.valueOf(5),
                                        List.of(materialLine("MAT-A", "WHITE", "PCS", 10))));
                    }
                    return Optional.empty();
                };
        specificationQuery.currentForLaunchSpec =
                Optional.of(
                        spec(
                                currentSpecB,
                                itemId,
                                BigDecimal.valueOf(5),
                                List.of(materialLine("MAT-CURRENT", "BLACK", "PCS", 999))));
        warehouseQuery.materialReferences =
                List.of(
                        reference(UUID.randomUUID(), "MAT-A", "A", "WHITE", "PCS"),
                        reference(UUID.randomUUID(), "MAT-CURRENT", "C", "BLACK", "PCS"));

        MaterialRequirement requirement =
                service.prepareMaterialRequirement(orderId, List.of(itemId));

        assertEquals(0, specificationQuery.resolveCurrentCalls.get());
        assertTrue(specificationQuery.resolveByIdCalls.get() >= 1);
        assertTrue(specificationQuery.resolveByIdArgs.contains(frozenSpecA));
        assertFalse(specificationQuery.resolveByIdArgs.contains(currentSpecB));
        assertEquals(1, requirement.lines().size());
        assertEquals("MAT-A", requirement.lines().getFirst().materialCode());
        assertEquals(0, requirement.lines().getFirst().quantity().compareTo(BigDecimal.TEN));
    }

    @Test
    void doesNotMultiplyLineQuantityByOrderedQuantity() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId, 5L);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                BigDecimal.valueOf(5),
                                List.of(materialLine("MAT-Q", "WHITE", "PCS", 10))));
        warehouseQuery.materialReferences =
                List.of(reference(UUID.randomUUID(), "MAT-Q", "Q", "WHITE", "PCS"));

        MaterialRequirementLine line =
                service.prepareMaterialRequirement(orderId, List.of(itemId)).lines().getFirst();

        assertEquals(0, line.quantity().compareTo(BigDecimal.TEN));
        assertFalse(0 == line.quantity().compareTo(BigDecimal.valueOf(50)));
    }

    @Test
    void persistedModelHasOnlyOneQuantityField() {
        MaterialRequirement requirement = prepareSimpleRequirement(BigDecimal.TEN);
        MaterialRequirementLine line = requirement.lines().getFirst();

        Set<String> quantityRelatedFields =
                Arrays.stream(MaterialRequirementLine.class.getDeclaredFields())
                        .map(Field::getName)
                        .filter(
                                name -> {
                                    String lower = name.toLowerCase(Locale.ROOT);
                                    return lower.contains("quantity")
                                            || lower.contains("available")
                                            || lower.contains("recommended")
                                            || lower.contains("requested")
                                            || lower.contains("deficit");
                                })
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        assertEquals(Set.of("quantity"), quantityRelatedFields);
        assertEquals(0, line.quantity().compareTo(BigDecimal.TEN));
    }

    @Test
    void destinationOnlyNoMainWarehouse() {
        MaterialRequirement requirement = prepareSimpleRequirement(BigDecimal.TEN);

        assertEquals(PROD_WAREHOUSE, requirement.destinationWarehouseId());
        Set<String> methodNames =
                Arrays.stream(MaterialRequirement.class.getMethods())
                        .map(Method::getName)
                        .collect(Collectors.toSet());
        assertFalse(methodNames.contains("sourceWarehouseId"));
        assertFalse(methodNames.contains("mainWarehouseId"));
        Set<String> fieldNames =
                Arrays.stream(MaterialRequirement.class.getDeclaredFields())
                        .map(Field::getName)
                        .collect(Collectors.toSet());
        assertFalse(fieldNames.contains("sourceWarehouseId"));
        assertFalse(fieldNames.contains("mainWarehouseId"));
    }

    @Test
    void rejectsWhenOrderNotInProduction() {
        MaterialRequirementNotAllowedException ex =
                assertThrows(
                        MaterialRequirementNotAllowedException.class,
                        () ->
                                service.prepareMaterialRequirement(
                                        orderId, List.of(SourceOrderItemId.generate())));
        assertEquals(OrderProductionViewStatus.NOT_ACCEPTED, ex.viewStatus());
    }

    private MaterialRequirement prepareSimpleRequirement(BigDecimal quantity) {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId, 1L);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                BigDecimal.ONE,
                                List.of(
                                        materialLine(
                                                "MAT-Z",
                                                "WHITE",
                                                "PCS",
                                                quantity.longValue()))));
        warehouseQuery.materialReferences =
                List.of(reference(UUID.randomUUID(), "MAT-Z", "Z", "WHITE", "PCS"));
        return service.prepareMaterialRequirement(orderId, List.of(itemId));
    }

    private ProductionItemState launchItem(
            SourceOrderId order, SourceOrderItemId itemId, SpecificationId specId) {
        return launchItem(order, itemId, specId, 1L);
    }

    private ProductionItemState launchItem(
            SourceOrderId order,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long orderedQuantity) {
        ProductionFoundation foundation = ProductionFoundation.freeze(order, itemId, specId, T0);
        return itemRepository.save(
                ProductionItemState.launch(
                        foundation,
                        ProductionQuantity.positive(orderedQuantity),
                        T0,
                        CuttingPlanLinks.empty()));
    }

    private static ResolvedSpecification spec(
            SpecificationId specId, SourceOrderItemId itemId, List<ResolvedMaterialLine> lines) {
        return spec(specId, itemId, BigDecimal.TEN, lines);
    }

    private static ResolvedSpecification spec(
            SpecificationId specId,
            SourceOrderItemId itemId,
            BigDecimal orderedQuantity,
            List<ResolvedMaterialLine> lines) {
        return new ResolvedSpecification(specId, itemId, orderedQuantity, lines);
    }

    private static ResolvedMaterialLine materialLine(
            String code, String color, String unit, long quantity) {
        return new ResolvedMaterialLine(code, code, color, null, BigDecimal.valueOf(quantity), unit);
    }

    private static MaterialReferenceEntry reference(
            UUID id, String article, String name, String color, String unit) {
        return new MaterialReferenceEntry(id, article, name, color, "", unit);
    }

    private static final class InMemoryItemRepository implements ProductionItemStateRepository {
        private final Map<String, ProductionItemState> store = new ConcurrentHashMap<>();

        @Override
        public ProductionItemState save(ProductionItemState state) {
            store.put(
                    state.sourceOrderId()
                            + ":"
                            + state.sourceOrderItemId()
                            + ":"
                            + state.specificationId(),
                    state);
            return state;
        }

        @Override
        public Optional<ProductionItemState> findByIdentity(
                SourceOrderId sourceOrderId,
                SourceOrderItemId sourceOrderItemId,
                SpecificationId specificationId) {
            return Optional.ofNullable(
                    store.get(sourceOrderId + ":" + sourceOrderItemId + ":" + specificationId));
        }

        @Override
        public List<ProductionItemState> findBySourceOrderId(SourceOrderId sourceOrderId) {
            return store.values().stream()
                    .filter(state -> state.sourceOrderId().equals(sourceOrderId))
                    .toList();
        }
    }

    private static final class InMemoryRequirementRepository implements MaterialRequirementRepository {
        private final Map<MaterialRequirementId, MaterialRequirement> store =
                new ConcurrentHashMap<>();

        @Override
        public MaterialRequirement save(MaterialRequirement requirement) {
            MaterialRequirement existing = store.get(requirement.requirementId());
            if (existing != null && existing.version() != requirement.version()) {
                throw new MaterialRequirementOptimisticLockException(
                        requirement.requirementId(), requirement.version());
            }
            MaterialRequirement saved =
                    MaterialRequirement.rehydrate(
                            requirement.requirementId(),
                            requirement.sourceOrderId(),
                            requirement.destinationWarehouseId(),
                            requirement.createdAt(),
                            requirement.updatedAt(),
                            existing == null ? 0L : requirement.version() + 1,
                            requirement.status(),
                            requirement.lines());
            store.put(saved.requirementId(), saved);
            return saved;
        }

        @Override
        public Optional<MaterialRequirement> findById(MaterialRequirementId id) {
            return Optional.ofNullable(store.get(id));
        }
    }

    private static final class TrackingSpecificationQuery implements OrderSpecificationQueryPort {
        Optional<ResolvedSpecification> byIdSpec = Optional.empty();
        Optional<ResolvedSpecification> currentForLaunchSpec = Optional.empty();
        java.util.function.Function<SpecificationId, Optional<ResolvedSpecification>> byIdResolver;
        final AtomicInteger resolveByIdCalls = new AtomicInteger();
        final AtomicInteger resolveCurrentCalls = new AtomicInteger();
        final List<SpecificationId> resolveByIdArgs = new CopyOnWriteArrayList<>();

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            resolveCurrentCalls.incrementAndGet();
            return currentForLaunchSpec;
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            resolveByIdCalls.incrementAndGet();
            resolveByIdArgs.add(specificationId);
            if (byIdResolver != null) {
                return byIdResolver.apply(specificationId);
            }
            return byIdSpec;
        }
    }

    private static final class TrackingWarehouseQuery implements WarehouseAvailabilityQueryPort {
        List<WarehouseCatalogEntry> warehouses = List.of();
        List<MaterialReferenceEntry> materialReferences = List.of();
        final AtomicInteger listWarehousesCalls = new AtomicInteger();
        final AtomicInteger listMaterialReferencesCalls = new AtomicInteger();
        final AtomicInteger availableQuantityCalls = new AtomicInteger();

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            listWarehousesCalls.incrementAndGet();
            return warehouses;
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            listMaterialReferencesCalls.incrementAndGet();
            return materialReferences;
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            availableQuantityCalls.incrementAndGet();
            return BigDecimal.ZERO;
        }
    }
}
