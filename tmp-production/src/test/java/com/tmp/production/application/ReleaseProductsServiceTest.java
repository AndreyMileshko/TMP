package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentEngineStatus;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentQuery;
import com.tmp.document.api.DocumentStatus;
import com.tmp.document.api.DocumentTypeDescriptor;
import com.tmp.document.api.UpdateDocumentCommand;
import com.tmp.production.application.ReleaseProductsCommand.CellAllocation;
import com.tmp.production.application.ReleaseProductsCommand.ItemRelease;
import com.tmp.production.application.ReleaseProductsCommand.MaterialActualUsage;
import com.tmp.production.application.ReleaseProductsResult.PrepareReleasePreview;
import com.tmp.production.application.document.ProductionReleaseProcessor;
import com.tmp.production.application.internal.ProductionReleaseDocumentService;
import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.ProductionReleaseImmutableException;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.ReleaseProductsException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.domain.repository.ProductionReleaseRepository;
import com.tmp.warehouse.api.WarehouseApi.ConsumptionCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ReleaseProductsServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-23T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-23T11:00:00Z");
    private static final UUID MAIN = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000102");
    private static final UUID MAIN_CELL = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID PROD_CELL = UUID.fromString("00000000-0000-4000-8000-000000000203");
    private static final UUID PROD_CELL_B = UUID.fromString("00000000-0000-4000-8000-000000000204");

    private InMemoryRepository repository;
    private TrackingSpecificationQuery specificationQuery;
    private TrackingWarehouseQuery warehouseAvailabilityQuery;
    private RecordingWarehouseCommandApi warehouseCommands;
    private StubWarehouseQueryApi warehouseQueries;
    private InMemoryReleaseRepository releaseRepository;
    private ReleaseProductsService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        specificationQuery = new TrackingSpecificationQuery();
        warehouseAvailabilityQuery = new TrackingWarehouseQuery();
        warehouseAvailabilityQuery.warehouses =
                List.of(
                        new WarehouseCatalogEntry(MAIN, "MAIN", "Main", true),
                        new WarehouseCatalogEntry(PROD, "PROD", "Production", true));
        warehouseCommands = new RecordingWarehouseCommandApi();
        warehouseQueries = new StubWarehouseQueryApi();
        warehouseQueries.productionCells =
                List.of(
                        new StorageCellView(PROD_CELL, PROD, "P-01", true),
                        new StorageCellView(PROD_CELL_B, PROD, "P-02", true));
        releaseRepository = new InMemoryReleaseRepository();
        ProductionReleaseProcessor processor =
                new ProductionReleaseProcessor(releaseRepository, repository, event -> {});
        ProductionReleaseDocumentService releaseDocumentService =
                new ProductionReleaseDocumentService(
                        new StubDocumentEngine(processor),
                        releaseRepository,
                        Clock.fixed(T1, ZoneOffset.UTC));
        service =
                new ReleaseProductsService(
                        new ProductionOrderViewService(repository),
                        new ProductionFoundationQueryService(specificationQuery),
                        warehouseAvailabilityQuery,
                        warehouseCommands,
                        warehouseQueries,
                        new ProductionWarehouseScope(MAIN, PROD),
                        releaseDocumentService,
                        new PassthroughTransactionManager(),
                        Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void previewWithoutActualOrCellAllocations() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-PREV");

        PrepareReleasePreview preview =
                service.prepareRelease(
                        new PrepareReleaseCommand(
                                orderId.value(), List.of(new ItemRelease(itemId.value(), 3))));

        assertEquals(0, preview.plannedMaterialLines().getFirst().plannedQuantity().compareTo(bd("5.100000")));
        assertEquals(
                0,
                preview.defaultActuals().getFirst().actualQuantity().compareTo(bd("5.100000")));
        assertEquals(0, warehouseCommands.consumeCalls.size());
        assertTrue(releaseRepository.store.isEmpty());
        assertEquals(ProductionStatus.IN_PRODUCTION, state(orderId, itemId, specId).status());
    }

    @Test
    void confirmRecalculatesPlanAfterConcurrentStateChange() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-RECALC");
        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(100));

        PrepareReleasePreview stalePreview =
                service.prepareRelease(
                        new PrepareReleaseCommand(
                                orderId.value(), List.of(new ItemRelease(itemId.value(), 4))));
        assertEquals(
                0,
                stalePreview.plannedMaterialLines().getFirst().plannedQuantity().compareTo(bd("6.800000")));

        service.releaseProducts(
                releaseCommand(
                        orderId,
                        itemId,
                        3,
                        materialId,
                        bd("5.100000"),
                        List.of(new CellAllocation(PROD_CELL, bd("5.100000")))));

        ReleaseProductsResult confirm =
                service.releaseProducts(
                        releaseCommand(
                                orderId,
                                itemId,
                                4,
                                materialId,
                                stalePreview.defaultActuals().getFirst().actualQuantity(),
                                List.of(
                                        new CellAllocation(
                                                PROD_CELL,
                                                stalePreview
                                                        .defaultActuals()
                                                        .getFirst()
                                                        .actualQuantity()))));

        assertEquals(
                0, confirm.materialResults().getFirst().plannedQuantity().compareTo(bd("6.800000")));
        assertEquals(7L, state(orderId, itemId, specId).releasedQuantity().value().longValueExact());
    }

    @Test
    void normativePartialPlanTenSeventeenReleaseThreeThenFour() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-NORM");

        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(100));

        ReleaseProductsResult first =
                service.releaseProducts(
                        releaseCommand(
                                orderId,
                                itemId,
                                3,
                                materialId,
                                bd("5.100000"),
                                List.of(new CellAllocation(PROD_CELL, bd("5.100000")))));

        assertEquals(0, first.materialResults().getFirst().plannedQuantity().compareTo(bd("5.100000")));
        assertEquals(0, first.materialResults().getFirst().actualQuantity().compareTo(bd("5.100000")));
        assertEquals(ProductionStatus.PARTIALLY_RELEASED, state(orderId, itemId, specId).status());
        assertEquals(3L, state(orderId, itemId, specId).releasedQuantity().value().longValueExact());

        ReleaseProductsResult second =
                service.releaseProducts(
                        releaseCommand(
                                orderId,
                                itemId,
                                4,
                                materialId,
                                bd("6.800000"),
                                List.of(new CellAllocation(PROD_CELL, bd("6.800000")))));

        assertEquals(0, second.materialResults().getFirst().plannedQuantity().compareTo(bd("6.800000")));
        assertEquals(0, second.materialResults().getFirst().actualQuantity().compareTo(bd("6.800000")));
        assertEquals(7L, state(orderId, itemId, specId).releasedQuantity().value().longValueExact());
        assertEquals(2, warehouseCommands.consumeCalls.size());
    }

    @Test
    void warehouseConsumesActualEightTenTwelveNotPlan() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-FACT");

        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(100));

        BigDecimal actualFirst = bd(8);
        service.releaseProducts(
                releaseCommand(
                        orderId,
                        itemId,
                        1,
                        materialId,
                        actualFirst,
                        List.of(new CellAllocation(PROD_CELL, actualFirst))));
        assertEquals(0, warehouseCommands.consumeCalls.get(0).quantity().compareTo(actualFirst));
        assertTrue(
                warehouseCommands.consumeCalls.get(0).quantity().compareTo(bd("1.700000")) != 0);

        BigDecimal actualSecond = bd(10);
        service.releaseProducts(
                releaseCommand(
                        orderId,
                        itemId,
                        2,
                        materialId,
                        actualSecond,
                        List.of(new CellAllocation(PROD_CELL, actualSecond))));
        assertEquals(0, warehouseCommands.consumeCalls.get(1).quantity().compareTo(actualSecond));
        assertTrue(
                warehouseCommands.consumeCalls.get(1).quantity().compareTo(bd("3.400000")) != 0);

        BigDecimal actualThird = bd(12);
        service.releaseProducts(
                releaseCommand(
                        orderId,
                        itemId,
                        3,
                        materialId,
                        actualThird,
                        List.of(new CellAllocation(PROD_CELL, actualThird))));
        assertEquals(0, warehouseCommands.consumeCalls.get(2).quantity().compareTo(actualThird));
        assertTrue(
                warehouseCommands.consumeCalls.get(2).quantity().compareTo(bd("5.100000")) != 0);
        assertEquals(3, warehouseCommands.consumeCalls.size());
    }

    @Test
    void insufficientProductionStockFailsBeforeMutation() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-SHORT");

        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(2));

        assertThrows(
                ReleaseProductsException.class,
                () ->
                        service.releaseProducts(
                                releaseCommand(
                                        orderId,
                                        itemId,
                                        3,
                                        materialId,
                                        bd("5.100000"),
                                        List.of(new CellAllocation(PROD_CELL, bd("5.100000"))))));

        assertEquals(0, warehouseCommands.consumeCalls.size());
        assertTrue(releaseRepository.store.isEmpty());
        assertEquals(ProductionStatus.IN_PRODUCTION, state(orderId, itemId, specId).status());
    }

    @Test
    void mainWarehouseStockCannotMaskProductionShortage() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-MAIN");

        warehouseQueries.setStock(materialId, MAIN, MAIN_CELL, bd(100));
        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(1));

        assertThrows(
                ReleaseProductsException.class,
                () ->
                        service.releaseProducts(
                                releaseCommand(
                                        orderId,
                                        itemId,
                                        3,
                                        materialId,
                                        bd("5.100000"),
                                        List.of(new CellAllocation(PROD_CELL, bd("5.100000"))))));

        assertEquals(0, warehouseCommands.consumeCalls.size());
    }

    @Test
    void multiCellAllocationCreatesMultipleConsumeCalls() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-MULTI");

        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(4));
        warehouseQueries.setStock(materialId, PROD, PROD_CELL_B, bd(4));

        BigDecimal actual = bd(8);
        ReleaseProductsResult result =
                service.releaseProducts(
                        releaseCommand(
                                orderId,
                                itemId,
                                3,
                                materialId,
                                actual,
                                List.of(
                                        new CellAllocation(PROD_CELL, bd(4)),
                                        new CellAllocation(PROD_CELL_B, bd(4)))));

        assertEquals(2, result.consumptionReferences().size());
        assertEquals(2, warehouseCommands.consumeCalls.size());
        assertEquals(PROD_CELL, warehouseCommands.consumeCalls.get(0).storageCellId());
        assertEquals(PROD_CELL_B, warehouseCommands.consumeCalls.get(1).storageCellId());
    }

    @Test
    void allocationTotalMismatchFails() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-MISMATCH");

        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(100));

        assertThrows(
                ReleaseProductsException.class,
                () ->
                        service.releaseProducts(
                                releaseCommand(
                                        orderId,
                                        itemId,
                                        3,
                                        materialId,
                                        bd(10),
                                        List.of(new CellAllocation(PROD_CELL, bd(6))))));

        assertEquals(0, warehouseCommands.consumeCalls.size());
    }

    @Test
    void wrongWarehouseCellMainCellFails() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-CELL");

        warehouseQueries.setStock(materialId, MAIN, MAIN_CELL, bd(100));
        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(100));

        assertThrows(
                ReleaseProductsException.class,
                () ->
                        service.releaseProducts(
                                releaseCommand(
                                        orderId,
                                        itemId,
                                        3,
                                        materialId,
                                        bd("5.100000"),
                                        List.of(new CellAllocation(MAIN_CELL, bd("5.100000"))))));

        assertEquals(0, warehouseCommands.consumeCalls.size());
    }

    @Test
    void zeroActualCreatesNoConsumptionButReleaseSucceeds() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-ZERO");

        ReleaseProductsResult result =
                service.releaseProducts(
                        new ReleaseProductsCommand(
                                orderId.value(),
                                List.of(new ItemRelease(itemId.value(), 3)),
                                List.of(
                                        new MaterialActualUsage(
                                                itemId.value(),
                                                materialId,
                                                BigDecimal.ZERO,
                                                List.of()))));

        assertTrue(result.consumptionReferences().isEmpty());
        assertEquals(0, warehouseCommands.consumeCalls.size());
        assertEquals(ProductionStatus.PARTIALLY_RELEASED, state(orderId, itemId, specId).status());
    }

    @Test
    void extraMaterialRejected() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        UUID extraMaterialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-PLAN");
        warehouseAvailabilityQuery.materialReferences =
                List.of(
                        reference(materialId, "MAT-PLAN"),
                        reference(extraMaterialId, "MAT-EXTRA"));

        warehouseQueries.setStock(materialId, PROD, PROD_CELL, bd(100));

        assertThrows(
                ReleaseProductsException.class,
                () ->
                        service.releaseProducts(
                                new ReleaseProductsCommand(
                                        orderId.value(),
                                        List.of(new ItemRelease(itemId.value(), 3)),
                                        List.of(
                                                new MaterialActualUsage(
                                                        itemId.value(),
                                                        materialId,
                                                        bd("5.100000"),
                                                        List.of(
                                                                new CellAllocation(
                                                                        PROD_CELL, bd("5.100000")))),
                                                new MaterialActualUsage(
                                                        itemId.value(),
                                                        extraMaterialId,
                                                        bd(1),
                                                        List.of(
                                                                new CellAllocation(
                                                                        PROD_CELL, bd(1))))))));
    }

    @Test
    void duplicateItemRejected() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId specId = SpecificationId.generate();
        UUID materialId = UUID.randomUUID();
        launchItem(orderId, itemId, specId, 10);
        bindFrozenSpec(specId, itemId, bd(17), materialId, "MAT-DUP");

        assertThrows(
                ReleaseProductsException.class,
                () ->
                        service.releaseProducts(
                                new ReleaseProductsCommand(
                                        orderId.value(),
                                        List.of(
                                                new ItemRelease(itemId.value(), 3),
                                                new ItemRelease(itemId.value(), 2)),
                                        List.of(
                                                new MaterialActualUsage(
                                                        itemId.value(),
                                                        materialId,
                                                        bd("5.100000"),
                                                        List.of(
                                                                new CellAllocation(
                                                                        PROD_CELL, bd("5.100000"))))))));
    }

    @Test
    void frozenSpecOnlyByIdResolverNeverCurrent() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        SpecificationId frozenSpec = SpecificationId.generate();
        UUID frozenMaterialId = UUID.randomUUID();
        UUID currentMaterialId = UUID.randomUUID();
        launchItem(orderId, itemId, frozenSpec, 10);

        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                frozenSpec,
                                itemId,
                                List.of(materialLine("MAT-FROZEN", bd(17)))));
        specificationQuery.currentSpec =
                Optional.of(
                        spec(
                                SpecificationId.generate(),
                                itemId,
                                List.of(materialLine("MAT-CURRENT", bd(99)))));
        warehouseAvailabilityQuery.materialReferences =
                List.of(
                        reference(frozenMaterialId, "MAT-FROZEN"),
                        reference(currentMaterialId, "MAT-CURRENT"));
        warehouseQueries.setStock(frozenMaterialId, PROD, PROD_CELL, bd(100));

        ReleaseProductsResult result =
                service.releaseProducts(
                        releaseCommand(
                                orderId,
                                itemId,
                                3,
                                frozenMaterialId,
                                bd("5.100000"),
                                List.of(new CellAllocation(PROD_CELL, bd("5.100000")))));

        assertEquals(0, specificationQuery.resolveCurrentCalls);
        assertTrue(specificationQuery.resolveByIdCalls > 0);
        assertEquals(frozenMaterialId, result.materialResults().getFirst().materialReferenceId());
    }

    private ProductionItemState state(
            SourceOrderId orderId, SourceOrderItemId itemId, SpecificationId specId) {
        return repository.findByIdentity(orderId, itemId, specId).orElseThrow();
    }

    private void launchItem(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long orderedQuantity) {
        repository.save(
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, specId, T0),
                        ProductionQuantity.positive(orderedQuantity),
                        T0));
    }

    private void bindFrozenSpec(
            SpecificationId specId,
            SourceOrderItemId itemId,
            BigDecimal lineQuantity,
            UUID materialId,
            String materialCode) {
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine(materialCode, lineQuantity))));
        warehouseAvailabilityQuery.materialReferences = List.of(reference(materialId, materialCode));
    }

    private static ReleaseProductsCommand releaseCommand(
            SourceOrderId orderId,
            SourceOrderItemId itemId,
            long releaseQuantity,
            UUID materialId,
            BigDecimal actual,
            List<CellAllocation> allocations) {
        return new ReleaseProductsCommand(
                orderId.value(),
                List.of(new ItemRelease(itemId.value(), releaseQuantity)),
                List.of(new MaterialActualUsage(itemId.value(), materialId, actual, allocations)));
    }

    private static ResolvedSpecification spec(
            SpecificationId specId,
            SourceOrderItemId itemId,
            List<ResolvedMaterialLine> lines) {
        return new ResolvedSpecification(specId, itemId, BigDecimal.TEN, lines);
    }

    private static ResolvedMaterialLine materialLine(String code, BigDecimal quantity) {
        return new ResolvedMaterialLine(code, code, "WHITE", null, quantity, "PCS");
    }

    private static MaterialReferenceEntry reference(UUID id, String article) {
        return new MaterialReferenceEntry(id, article, article, "WHITE", "", "PCS");
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static final class InMemoryRepository implements ProductionItemStateRepository {

        private final Map<String, ProductionItemState> store = new ConcurrentHashMap<>();

        @Override
        public ProductionItemState save(ProductionItemState state) {
            store.put(key(state), state);
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

        private static String key(ProductionItemState state) {
            return state.sourceOrderId()
                    + ":"
                    + state.sourceOrderItemId()
                    + ":"
                    + state.specificationId();
        }
    }

    private static final class TrackingSpecificationQuery implements OrderSpecificationQueryPort {

        Optional<ResolvedSpecification> currentSpec = Optional.empty();
        Optional<ResolvedSpecification> byIdSpec = Optional.empty();
        int resolveCurrentCalls;
        int resolveByIdCalls;

        @Override
        public Optional<ResolvedSpecification> resolveCurrentForLaunch(
                SourceOrderItemId sourceOrderItemId) {
            resolveCurrentCalls++;
            return currentSpec;
        }

        @Override
        public Optional<ResolvedSpecification> resolveById(SpecificationId specificationId) {
            resolveByIdCalls++;
            return byIdSpec;
        }
    }

    private static final class TrackingWarehouseQuery implements WarehouseAvailabilityQueryPort {

        List<WarehouseCatalogEntry> warehouses = List.of();
        List<MaterialReferenceEntry> materialReferences = List.of();

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            return warehouses;
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            return materialReferences;
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            return BigDecimal.ZERO;
        }
    }

    private static final class RecordingWarehouseCommandApi implements WarehouseCommandApi {

        private final List<ConsumptionCommand> consumeCalls = new ArrayList<>();

        @Override
        public com.tmp.warehouse.api.WarehouseApi.WarehouseView createWarehouse(
                com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StorageCellView createStorageCell(
                com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.ReservationLinkView createReservationLink(
                com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult executeWarehouseOperation(
                com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult receive(com.tmp.warehouse.api.WarehouseApi.ReceiptCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult consume(ConsumptionCommand command) {
            consumeCalls.add(command);
            return new OperationResult(
                    UUID.randomUUID(),
                    OperationKind.CONSUMPTION,
                    "COMPLETED",
                    command.materialReferenceId(),
                    "MAT",
                    command.warehouseId(),
                    command.storageCellId(),
                    command.quantity());
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.TransferRequestView createTransferDraft(
                com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult sendTransfer(UUID transferDraftOperationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public OperationResult receiveTransfer(UUID sendOperationId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubWarehouseQueryApi implements WarehouseQueryApi {

        private List<StorageCellView> productionCells = List.of();
        private final List<StockView> stockViews = new ArrayList<>();

        void setStock(UUID materialId, UUID warehouseId, UUID cellId, BigDecimal quantity) {
            stockViews.removeIf(
                    view ->
                            materialId.equals(view.materialReferenceId())
                                    && warehouseId.equals(view.warehouseId())
                                    && cellId.equals(view.storageCellId()));
            stockViews.add(
                    new StockView(
                            materialId,
                            "MAT",
                            "Material",
                            "WHITE",
                            "",
                            "PCS",
                            warehouseId.equals(PROD) ? "PROD" : "MAIN",
                            "CELL",
                            quantity,
                            StockStateView.AVAILABLE,
                            "MAT",
                            warehouseId,
                            cellId));
        }

        @Override
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            if (PROD.equals(warehouseId)) {
                return productionCells;
            }
            return List.of();
        }

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.WarehouseView> listWarehouses() {
            return List.of();
        }

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView>
                listMaterialReferences() {
            return List.of();
        }

        @Override
        public List<String> listUnitOfMeasures() {
            return List.of();
        }

        @Override
        public List<StockView> getStock(String materialCode) {
            return List.of();
        }

        @Override
        public List<StockView> getStock(String materialCode, UUID warehouseId, UUID storageCellId) {
            return List.of();
        }

        @Override
        public List<StockView> getStockByWarehouse(UUID warehouseId) {
            return List.of();
        }

        @Override
        public List<StockView> getStockByMaterialReferenceId(UUID materialReferenceId) {
            return stockViews.stream()
                    .filter(view -> materialReferenceId.equals(view.materialReferenceId()))
                    .toList();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView
                getMaterialReferenceDisplay(String materialCode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.AvailabilityResult checkAvailability(
                com.tmp.warehouse.api.WarehouseApi.MaterialIdentityRequest identity,
                BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.AvailabilityResult checkAvailability(
                com.tmp.warehouse.api.WarehouseApi.MaterialIdentityRequest identity,
                UUID warehouseId,
                BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.AvailabilityResult checkAvailability(
                UUID materialReferenceId, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.AvailabilityResult checkAvailability(
                UUID materialReferenceId, UUID warehouseId, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.AvailabilityResult checkAvailabilityByLegacyArticle(
                String materialCode, BigDecimal quantity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.ReservationLinkView> listReservationLinks(
                String materialCode) {
            return List.of();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.TransferStatusView getTransferStatus(
                UUID operationId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubDocumentEngine implements DocumentEngine {

        private final DocumentProcessor processor;
        private final Map<UUID, DocumentMetadata> documents = new ConcurrentHashMap<>();

        StubDocumentEngine(DocumentProcessor processor) {
            this.processor = processor;
        }

        @Override
        public DocumentProcessorRegistration registerProcessor(DocumentProcessor processor) {
            return new DocumentProcessorRegistration() {
                @Override
                public String documentTypeId() {
                    return processor.documentTypeId();
                }

                @Override
                public void unregister() {}

                @Override
                public void deactivate() {}
            };
        }

        @Override
        public DocumentMetadata createDocument(CreateDocumentCommand command) {
            DocumentMetadata draft =
                    new DocumentMetadata(
                            UUID.randomUUID(),
                            command.documentTypeId(),
                            "PR-STUB",
                            command.title(),
                            DocumentStatus.DRAFT,
                            0L,
                            Instant.now(),
                            Instant.now(),
                            null,
                            null);
            documents.put(draft.id(), draft);
            return draft;
        }

        @Override
        public DocumentMetadata updateDocument(UpdateDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata postDocument(UUID documentId) {
            DocumentMetadata current = documents.get(documentId);
            processor.onPost(() -> current);
            DocumentMetadata posted =
                    new DocumentMetadata(
                            current.id(),
                            current.documentTypeId(),
                            current.documentNumber(),
                            current.title(),
                            DocumentStatus.POSTED,
                            current.version() + 1,
                            current.createdAt(),
                            Instant.now(),
                            Instant.now(),
                            current.closedAt());
            documents.put(documentId, posted);
            return posted;
        }

        @Override
        public DocumentMetadata unpostDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DocumentMetadata closeDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteDocument(UUID documentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DocumentMetadata> findById(UUID documentId) {
            return Optional.ofNullable(documents.get(documentId));
        }

        @Override
        public List<DocumentMetadata> search(DocumentQuery query) {
            return List.copyOf(documents.values());
        }

        @Override
        public List<DocumentTypeDescriptor> registeredTypes() {
            return List.of();
        }

        @Override
        public DocumentEngineStatus status() {
            return new DocumentEngineStatus(1, documents.size());
        }
    }

    private static final class InMemoryReleaseRepository implements ProductionReleaseRepository {

        private final Map<UUID, ProductionRelease> store = new ConcurrentHashMap<>();

        @Override
        public ProductionRelease saveDraft(ProductionRelease release) {
            ProductionRelease existing = store.get(release.documentId());
            if (existing != null && existing.posted()) {
                throw new ProductionReleaseImmutableException(release.documentId());
            }
            store.put(release.documentId(), release);
            return release;
        }

        @Override
        public ProductionRelease markPosted(ProductionRelease release) {
            store.put(release.documentId(), release);
            return release;
        }

        @Override
        public Optional<ProductionRelease> findByDocumentId(UUID documentId) {
            return Optional.ofNullable(store.get(documentId));
        }
    }

    private static final class PassthroughTransactionManager
            implements org.springframework.transaction.PlatformTransactionManager {

        @Override
        public org.springframework.transaction.TransactionStatus getTransaction(
                TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(org.springframework.transaction.TransactionStatus status)
                throws TransactionException {}

        @Override
        public void rollback(org.springframework.transaction.TransactionStatus status)
                throws TransactionException {}
    }
}
