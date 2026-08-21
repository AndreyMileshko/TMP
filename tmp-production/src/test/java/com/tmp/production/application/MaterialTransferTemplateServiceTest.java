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
import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferNotAllowedException;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.MaterialTransferTemplateNotReadyException;
import com.tmp.production.domain.MaterialTransferTemplateOptimisticLockException;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionCuttingPlanLink;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaterialTransferTemplateServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-21T10:00:00Z");
    private static final UUID MAIN_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PROD_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private InMemoryItemRepository itemRepository;
    private InMemoryTemplateRepository templateRepository;
    private TrackingSpecificationQuery specificationQuery;
    private TrackingWarehouseQuery warehouseQuery;
    private MaterialTransferTemplateService service;
    private SourceOrderId orderId;

    @BeforeEach
    void setUp() {
        itemRepository = new InMemoryItemRepository();
        templateRepository = new InMemoryTemplateRepository();
        specificationQuery = new TrackingSpecificationQuery();
        warehouseQuery = new TrackingWarehouseQuery();
        warehouseQuery.warehouses =
                List.of(
                        new WarehouseCatalogEntry(MAIN_WAREHOUSE, "MAIN", "Main", true),
                        new WarehouseCatalogEntry(PROD_WAREHOUSE, "PROD", "Production", true),
                        new WarehouseCatalogEntry(OTHER_WAREHOUSE, "OTHER", "Other", true));

        ProductionOrderViewService viewService = new ProductionOrderViewService(itemRepository);
        ProductionFoundationQueryService foundationQuery =
                new ProductionFoundationQueryService(specificationQuery);
        CheckMaterialAvailabilityService availabilityService =
                new CheckMaterialAvailabilityService(
                        viewService,
                        foundationQuery,
                        warehouseQuery,
                        new ProductionWarehouseScope(MAIN_WAREHOUSE, PROD_WAREHOUSE),
                        Clock.fixed(T0, ZoneOffset.UTC));
        service =
                new MaterialTransferTemplateService(
                        availabilityService,
                        viewService,
                        foundationQuery,
                        new ProductionWarehouseScope(MAIN_WAREHOUSE, PROD_WAREHOUSE),
                        templateRepository,
                        Clock.fixed(T0, ZoneOffset.UTC));
        orderId = SourceOrderId.generate();
    }

    @Test
    void rejectsNotAcceptedWithoutWarehouseQueries() {
        MaterialTransferNotAllowedException ex =
                assertThrows(
                        MaterialTransferNotAllowedException.class,
                        () -> service.prepareMaterialTransferTemplate(orderId));
        assertEquals(OrderProductionViewStatus.NOT_ACCEPTED, ex.viewStatus());
        assertEquals(0, warehouseQuery.listMaterialReferencesCalls);
    }

    @Test
    void rejectsManufacturedAndCancelled() {
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, SourceOrderItemId.generate(), specId, 1, CuttingPlanLinks.empty());
        ProductionItemState state = itemRepository.findBySourceOrderId(orderId).getFirst();
        itemRepository.save(
                ProductionItemState.rehydrate(
                        state.foundation(),
                        ProductionStatus.RELEASED,
                        state.orderedQuantity(),
                        state.launchedQuantity(),
                        ProductionQuantity.zero(),
                        state.orderedQuantity(),
                        null,
                        T0));

        assertThrows(
                MaterialTransferNotAllowedException.class,
                () -> service.prepareMaterialTransferTemplate(orderId));

        SourceOrderId cancelledOrder = SourceOrderId.generate();
        launchItem(cancelledOrder, SourceOrderItemId.generate(), SpecificationId.generate(), 1, CuttingPlanLinks.empty());
        itemRepository.save(itemRepository.findBySourceOrderId(cancelledOrder).getFirst().cancel(T0));
        assertThrows(
                MaterialTransferNotAllowedException.class,
                () -> service.prepareMaterialTransferTemplate(cancelledOrder));
    }

    @Test
    void createsTemplateWithWarehouseScopeAndRecommendationCases() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId, 1, CuttingPlanLinks.empty());
        specificationQuery.byIdSpec =
                Optional.of(spec(specId, itemId, List.of(materialLine("MAT-A", "WHITE", "PCS", 10))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-A", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(20));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.valueOf(4));
        warehouseQuery.stock.put(stockKey(materialId, OTHER_WAREHOUSE), BigDecimal.valueOf(100));

        MaterialTransferTemplate template = service.prepareMaterialTransferTemplate(orderId);

        assertEquals(MAIN_WAREHOUSE, template.sourceWarehouseId());
        assertEquals(PROD_WAREHOUSE, template.destinationWarehouseId());
        assertEquals(1, template.lines().size());
        MaterialTransferTemplateLine line = template.lines().getFirst();
        assertEquals(0, line.recommendedQuantity().compareTo(BigDecimal.valueOf(6)));
        assertEquals(0, line.requestedQuantity().compareTo(BigDecimal.valueOf(6)));
        assertEquals(MaterialPlanningSource.SPECIFICATION, line.planningSource());
        assertEquals(0, line.uncoveredDeficit().compareTo(BigDecimal.ZERO));
        assertTrue(line.sourceOrderItemIds().contains(itemId));
        assertTrue(templateRepository.findById(template.templateId()).isPresent());
    }

    @Test
    void shortageCapsAtMainAndExposesUncoveredDeficit() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId, 1, CuttingPlanLinks.empty());
        specificationQuery.byIdSpec =
                Optional.of(spec(specId, itemId, List.of(materialLine("MAT-B", "WHITE", "PCS", 10))));
        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-B", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(5));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.valueOf(2));

        MaterialTransferTemplateLine line =
                service.prepareMaterialTransferTemplate(orderId).lines().getFirst();

        assertEquals(0, line.recommendedQuantity().compareTo(BigDecimal.valueOf(5)));
        assertEquals(0, line.uncoveredDeficit().compareTo(BigDecimal.valueOf(3)));
    }

    @Test
    void alreadyOnProductionOmitsTransferLine() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId, 1, CuttingPlanLinks.empty());
        specificationQuery.byIdSpec =
                Optional.of(spec(specId, itemId, List.of(materialLine("MAT-C", "WHITE", "PCS", 10))));
        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-C", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(20));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.valueOf(12));

        MaterialTransferTemplate template = service.prepareMaterialTransferTemplate(orderId);

        assertTrue(template.lines().isEmpty());
    }

    @Test
    void rejectsUnresolvedAndAmbiguousMaterials() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId, 1, CuttingPlanLinks.empty());
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(specId, itemId, List.of(materialLine("UNKNOWN", "WHITE", "PCS", 1))));
        warehouseQuery.materialReferences = List.of();

        assertThrows(
                MaterialTransferTemplateNotReadyException.class,
                () -> service.prepareMaterialTransferTemplate(orderId));

        SourceOrderId ambiguousOrder = SourceOrderId.generate();
        SourceOrderItemId ambiguousItem = SourceOrderItemId.generate();
        SpecificationId ambiguousSpec = SpecificationId.generate();
        launchItem(ambiguousOrder, ambiguousItem, ambiguousSpec, 1, CuttingPlanLinks.empty());
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                ambiguousSpec,
                                ambiguousItem,
                                List.of(materialLine("AMB", "WHITE", "PCS", 1))));
        warehouseQuery.materialReferences =
                List.of(
                        reference(UUID.randomUUID(), "AMB", "WHITE", "S1", "PCS"),
                        reference(UUID.randomUUID(), "AMB", "WHITE", "S2", "PCS"));

        assertThrows(
                MaterialTransferTemplateNotReadyException.class,
                () -> service.prepareMaterialTransferTemplate(ambiguousOrder));
    }

    @Test
    void editAndExcludePreserveRecommendedQuantity() {
        MaterialTransferTemplate template = prepareSimpleTemplate(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
        MaterialTransferTemplateLineId lineId = template.lines().getFirst().lineId();

        MaterialTransferTemplate edited =
                service.changeRequestedQuantity(template.templateId(), lineId, BigDecimal.valueOf(8));
        assertEquals(0, edited.lines().getFirst().recommendedQuantity().compareTo(BigDecimal.TEN));
        assertEquals(0, edited.lines().getFirst().requestedQuantity().compareTo(BigDecimal.valueOf(8)));

        MaterialTransferTemplate excluded = service.excludeLine(template.templateId(), lineId);
        assertFalse(excluded.lines().getFirst().included());
        assertEquals(0, excluded.lines().getFirst().recommendedQuantity().compareTo(BigDecimal.TEN));

        MaterialTransferTemplate restored = service.restoreLine(template.templateId(), lineId);
        assertTrue(restored.lines().getFirst().included());
    }

    @Test
    void sameCuttingPlanIsInformationalSingleLinkWithSpecificationSource() {
        UUID materialId = UUID.randomUUID();
        CuttingPlanId plan = CuttingPlanId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(MaterialReferenceId.of(materialId), plan));

        launchItem(orderId, itemA, specA, 1, links);
        launchItem(orderId, itemB, specB, 1, links);
        specificationQuery.byIdResolver =
                id -> {
                    if (id.equals(specA)) {
                        return Optional.of(spec(specA, itemA, List.of(materialLine("MAT-X", "WHITE", "PCS", 5))));
                    }
                    if (id.equals(specB)) {
                        return Optional.of(spec(specB, itemB, List.of(materialLine("MAT-X", "WHITE", "PCS", 5))));
                    }
                    return Optional.empty();
                };
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(20));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialTransferTemplateLine line =
                service.prepareMaterialTransferTemplate(orderId).lines().getFirst();

        assertEquals(CuttingLinkStatus.SINGLE, line.cuttingLinkStatus());
        assertEquals(Optional.of(plan), line.cuttingPlanId());
        assertEquals(MaterialPlanningSource.SPECIFICATION, line.planningSource());
        assertEquals(0, line.recommendedQuantity().compareTo(BigDecimal.TEN));
        assertEquals(2, line.sourceOrderItemIds().size());
    }

    @Test
    void differentCuttingPlansDoNotPickFirstAndKeepSpecificationQuantity() {
        UUID materialId = UUID.randomUUID();
        CuttingPlanId plan1 = CuttingPlanId.generate();
        CuttingPlanId plan2 = CuttingPlanId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();

        launchItem(
                orderId,
                itemA,
                specA,
                1,
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(MaterialReferenceId.of(materialId), plan1)));
        launchItem(
                orderId,
                itemB,
                specB,
                1,
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(MaterialReferenceId.of(materialId), plan2)));
        specificationQuery.byIdResolver =
                id -> {
                    if (id.equals(specA)) {
                        return Optional.of(spec(specA, itemA, List.of(materialLine("MAT-Y", "WHITE", "PCS", 4))));
                    }
                    if (id.equals(specB)) {
                        return Optional.of(spec(specB, itemB, List.of(materialLine("MAT-Y", "WHITE", "PCS", 6))));
                    }
                    return Optional.empty();
                };
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-Y", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(20));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialTransferTemplateLine line =
                service.prepareMaterialTransferTemplate(orderId).lines().getFirst();

        assertEquals(CuttingLinkStatus.MULTIPLE_REFERENCES, line.cuttingLinkStatus());
        assertTrue(line.cuttingPlanId().isEmpty());
        assertEquals(2, line.cuttingPlanReferences().size());
        assertTrue(line.cuttingPlanReferences().contains(plan1));
        assertTrue(line.cuttingPlanReferences().contains(plan2));
        assertEquals(MaterialPlanningSource.SPECIFICATION, line.planningSource());
        assertEquals(0, line.recommendedQuantity().compareTo(BigDecimal.TEN));
    }

    @Test
    void absentCuttingPlanStillCreatesTemplate() {
        MaterialTransferTemplate template =
                prepareSimpleTemplate(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
        MaterialTransferTemplateLine line = template.lines().getFirst();
        assertEquals(CuttingLinkStatus.NONE, line.cuttingLinkStatus());
        assertTrue(line.cuttingPlanId().isEmpty());
        assertEquals(MaterialPlanningSource.SPECIFICATION, line.planningSource());
    }

    @Test
    void findTemplateByIdReturnsPersistedTemplate() {
        MaterialTransferTemplate created =
                prepareSimpleTemplate(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
        Optional<MaterialTransferTemplate> loaded = service.findTemplateById(created.templateId());
        assertTrue(loaded.isPresent());
        assertEquals(created.templateId(), loaded.orElseThrow().templateId());
    }

    @Test
    void optimisticLockRejectsStaleVersion() {
        MaterialTransferTemplate created =
                prepareSimpleTemplate(BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN);
        MaterialTransferTemplate stale =
                MaterialTransferTemplate.rehydrate(
                        created.templateId(),
                        created.sourceOrderId(),
                        created.sourceWarehouseId(),
                        created.destinationWarehouseId(),
                        created.createdAt(),
                        created.updatedAt(),
                        created.lines(),
                        0L);
        templateRepository.save(
                created.changeRequestedQuantity(
                        created.lines().getFirst().lineId(), BigDecimal.valueOf(7), T0));

        assertThrows(
                MaterialTransferTemplateOptimisticLockException.class,
                () ->
                        templateRepository.save(
                                stale.changeRequestedQuantity(
                                        stale.lines().getFirst().lineId(),
                                        BigDecimal.valueOf(3),
                                        T0)));
    }

    private MaterialTransferTemplate prepareSimpleTemplate(
            BigDecimal required, BigDecimal production, BigDecimal main) {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        launchItem(orderId, itemId, specId, 1, CuttingPlanLinks.empty());
        specificationQuery.byIdSpec =
                Optional.of(spec(specId, itemId, List.of(materialLine("MAT-Z", "WHITE", "PCS", required.longValue()))));
        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-Z", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), main);
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), production);
        return service.prepareMaterialTransferTemplate(orderId);
    }

    private void launchItem(
            SourceOrderId order,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long quantity,
            CuttingPlanLinks links) {
        ProductionFoundation foundation = ProductionFoundation.freeze(order, itemId, specId, T0);
        itemRepository.save(
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(quantity), T0, links));
    }

    private static ResolvedSpecification spec(
            SpecificationId specId, SourceOrderItemId itemId, List<ResolvedMaterialLine> lines) {
        return new ResolvedSpecification(specId, itemId, BigDecimal.TEN, lines);
    }

    private static ResolvedMaterialLine materialLine(
            String code, String color, String unit, long quantity) {
        return new ResolvedMaterialLine(code, code, color, null, BigDecimal.valueOf(quantity), unit);
    }

    private static MaterialReferenceEntry reference(
            UUID id, String article, String color, String size, String unit) {
        return new MaterialReferenceEntry(id, article, article, color, size, unit);
    }

    private static String stockKey(UUID materialId, UUID warehouseId) {
        return materialId + ":" + warehouseId;
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

    private static final class InMemoryTemplateRepository implements MaterialTransferTemplateRepository {
        private final Map<MaterialTransferTemplateId, MaterialTransferTemplate> store =
                new ConcurrentHashMap<>();

        @Override
        public MaterialTransferTemplate save(MaterialTransferTemplate template) {
            MaterialTransferTemplate existing = store.get(template.templateId());
            if (existing != null && existing.version() != template.version()) {
                throw new MaterialTransferTemplateOptimisticLockException(
                        template.templateId(), template.version());
            }
            MaterialTransferTemplate saved =
                    MaterialTransferTemplate.rehydrate(
                            template.templateId(),
                            template.sourceOrderId(),
                            template.sourceWarehouseId(),
                            template.destinationWarehouseId(),
                            template.createdAt(),
                            template.updatedAt(),
                            template.lines(),
                            existing == null ? 0L : template.version() + 1);
            store.put(saved.templateId(), saved);
            return saved;
        }

        @Override
        public Optional<MaterialTransferTemplate> findById(MaterialTransferTemplateId templateId) {
            return Optional.ofNullable(store.get(templateId));
        }
    }

    private static final class TrackingSpecificationQuery implements OrderSpecificationQueryPort {
        Optional<ResolvedSpecification> byIdSpec = Optional.empty();
        java.util.function.Function<SpecificationId, Optional<ResolvedSpecification>> byIdResolver;

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(SourceOrderItemId sourceOrderItemId) {
            throw new UnsupportedOperationException("not used");
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            if (byIdResolver != null) {
                return byIdResolver.apply(specificationId);
            }
            return byIdSpec;
        }
    }

    private static final class TrackingWarehouseQuery implements WarehouseAvailabilityQueryPort {
        List<WarehouseCatalogEntry> warehouses = List.of();
        List<MaterialReferenceEntry> materialReferences = List.of();
        final Map<String, BigDecimal> stock = new ConcurrentHashMap<>();
        int listMaterialReferencesCalls;

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            return warehouses;
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            listMaterialReferencesCalls++;
            return materialReferences;
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            return stock.getOrDefault(stockKey(materialReferenceId, warehouseId), BigDecimal.ZERO);
        }
    }
}
