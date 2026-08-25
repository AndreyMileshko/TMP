package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.port.OrderSpecificationQueryPort;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedMaterialLine;
import com.tmp.production.application.port.OrderSpecificationQueryPort.ResolvedSpecification;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.WarehouseCatalogEntry;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.CuttingPlanLinks;
import com.tmp.production.domain.MaterialAvailabilityCheckResult;
import com.tmp.production.domain.MaterialAvailabilityLineStatus;
import com.tmp.production.domain.MaterialAvailabilityOverallStatus;
import com.tmp.production.domain.MaterialCheckNotAllowedException;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.OrderProductionViewStatus;
import com.tmp.production.domain.ProductionCuttingPlanLink;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.testsupport.InMemoryProductionHistoryRepository;
import com.tmp.production.testsupport.ProductionHistoryTestSupport;
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

class CheckMaterialAvailabilityServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-20T10:00:00Z");
    private static final UUID MAIN_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID PROD_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final UUID OTHER_WAREHOUSE = UUID.fromString("00000000-0000-4000-8000-000000000003");

    private InMemoryRepository repository;
    private TrackingSpecificationQuery specificationQuery;
    private TrackingWarehouseQuery warehouseQuery;
    private CheckMaterialAvailabilityService service;
    private InMemoryProductionHistoryRepository historyRepository;
    private SourceOrderId orderId;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRepository();
        specificationQuery = new TrackingSpecificationQuery();
        warehouseQuery = new TrackingWarehouseQuery();
        warehouseQuery.warehouses =
                List.of(
                        new WarehouseCatalogEntry(MAIN_WAREHOUSE, "MAIN", "Main", true),
                        new WarehouseCatalogEntry(PROD_WAREHOUSE, "PROD", "Production", true),
                        new WarehouseCatalogEntry(OTHER_WAREHOUSE, "OTHER", "Other", true));

        ProductionOrderViewService viewService = new ProductionOrderViewService(repository);
        ProductionFoundationQueryService foundationQuery =
                new ProductionFoundationQueryService(specificationQuery);
        historyRepository = new InMemoryProductionHistoryRepository();
        ProductionHistoryService historyService =
                ProductionHistoryTestSupport.historyService(historyRepository);
        service =
                new CheckMaterialAvailabilityService(
                        viewService,
                        foundationQuery,
                        warehouseQuery,
                        new ProductionWarehouseScope(MAIN_WAREHOUSE, PROD_WAREHOUSE),
                        historyService,
                        ProductionHistoryTestSupport.noOpTransactionManager(),
                        Clock.fixed(T0, ZoneOffset.UTC));

        orderId = SourceOrderId.generate();
    }

    @Test
    void rejectsNotAcceptedOrderWithoutWarehouseQueries() {
        MaterialCheckNotAllowedException ex =
                assertThrows(
                        MaterialCheckNotAllowedException.class, () -> service.check(orderId));

        assertEquals(OrderProductionViewStatus.NOT_ACCEPTED, ex.viewStatus());
        assertEquals(0, warehouseQuery.listMaterialReferencesCalls);
        assertEquals(0, warehouseQuery.availableQuantityCalls);
    }

    @Test
    void rejectsManufacturedOrder() {
        launchSingleItem(orderId, SpecificationId.generate(), 1);
        repository.save(
                manufactured(
                        orderId,
                        repository.findBySourceOrderId(orderId).getFirst().foundation(),
                        1));

        MaterialCheckNotAllowedException ex =
                assertThrows(
                        MaterialCheckNotAllowedException.class, () -> service.check(orderId));

        assertEquals(OrderProductionViewStatus.MANUFACTURED, ex.viewStatus());
        assertEquals(0, warehouseQuery.listMaterialReferencesCalls);
    }

    @Test
    void rejectsCancelledOrder() {
        launchSingleItem(orderId, SpecificationId.generate(), 1);
        ProductionItemState state = repository.findBySourceOrderId(orderId).getFirst();
        repository.save(state.cancel(Instant.parse("2026-08-20T11:00:00Z")));

        MaterialCheckNotAllowedException ex =
                assertThrows(
                        MaterialCheckNotAllowedException.class, () -> service.check(orderId));

        assertEquals(OrderProductionViewStatus.CANCELLED, ex.viewStatus());
        assertEquals(0, warehouseQuery.listMaterialReferencesCalls);
    }

    @Test
    void usesFrozenSpecificationNotCurrent() {
        SpecificationId frozenSpec = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, frozenSpec, 10);

        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                frozenSpec,
                                itemId,
                                List.of(materialLine("MAT-FROZEN", "WHITE", "PCS", 5))));
        specificationQuery.currentSpec =
                Optional.of(
                        spec(
                                SpecificationId.generate(),
                                itemId,
                                List.of(materialLine("MAT-NEW", "WHITE", "PCS", 99))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-FROZEN", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.TEN);
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(0, specificationQuery.resolveCurrentCalls);
        assertEquals(1, result.lines().size());
        assertEquals("MAT-FROZEN", result.lines().getFirst().materialCode());
        assertEquals(0, result.lines().getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(5)));
    }

    @Test
    void mereCuttingPlanLinkDoesNotSwitchPlanningSourceFromSpecification() {
        SpecificationId frozenSpec = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        UUID materialId = UUID.randomUUID();
        CuttingPlanLinks links =
                CuttingPlanLinks.of(
                        ProductionCuttingPlanLink.of(
                                MaterialReferenceId.of(materialId), CuttingPlanId.generate()));

        ProductionFoundation foundation =
                ProductionFoundation.freeze(orderId, itemId, frozenSpec, T0);
        repository.save(
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(10), T0, links));

        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                frozenSpec,
                                itemId,
                                List.of(materialLine("MAT-LINKED", "WHITE", "PCS", 4))));
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "MAT-LINKED", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.TEN);
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(1, result.lines().size());
        assertEquals(MaterialPlanningSource.SPECIFICATION, result.lines().getFirst().planningSource());
        assertTrue(repository.findBySourceOrderId(orderId).getFirst().cuttingPlanLinks().size() == 1);
        assertEquals(0, result.lines().getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(4)));
    }

    @Test
    void lineQuantityIsNotMultipliedByOrderedQuantity() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 10);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "PROFILE-X",
                                                "Profile",
                                                "WHITE",
                                                BigDecimal.valueOf(1500),
                                                BigDecimal.valueOf(7),
                                                "PCS"))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.TEN);
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(0, result.lines().getFirst().requiredQuantity().compareTo(BigDecimal.valueOf(7)));
    }

    @Test
    void aggregatesRequirementsAcrossItems() {
        SpecificationId specA = SpecificationId.generate();
        SpecificationId specB = SpecificationId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        launchItem(orderId, itemA, specA, 1);
        launchItem(orderId, itemB, specB, 1);

        specificationQuery.byIdResolver =
                specId -> {
                    if (specId.equals(specA)) {
                        return Optional.of(
                                spec(
                                        specA,
                                        itemA,
                                        List.of(materialLine("PROFILE-X", "WHITE", "PCS", 4))));
                    }
                    if (specId.equals(specB)) {
                        return Optional.of(
                                spec(
                                        specB,
                                        itemB,
                                        List.of(materialLine("PROFILE-X", "WHITE", "PCS", 6))));
                    }
                    return Optional.empty();
                };

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.TEN);
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(1, result.lines().size());
        assertEquals(0, result.lines().getFirst().requiredQuantity().compareTo(BigDecimal.TEN));
    }

    @Test
    void lengthMmIsNotMappedToWarehouseSize_andEmptySizeResolves() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "PROFILE-X",
                                                "Profile",
                                                "WHITE",
                                                BigDecimal.valueOf(1500),
                                                BigDecimal.ONE,
                                                "PCS"))));

        UUID emptySizeMaterial = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(emptySizeMaterial, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(emptySizeMaterial, MAIN_WAREHOUSE), BigDecimal.ONE);
        warehouseQuery.stock.put(stockKey(emptySizeMaterial, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(MaterialAvailabilityLineStatus.AVAILABLE, result.lines().getFirst().status());
        assertEquals(emptySizeMaterial, result.lines().getFirst().materialReferenceId());
    }

    @Test
    void lengthMmIsNotMappedToWarehouseSize_doesNotPreferSize1500() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(
                                        new ResolvedMaterialLine(
                                                "PROFILE-X",
                                                "Profile",
                                                "WHITE",
                                                BigDecimal.valueOf(1500),
                                                BigDecimal.ONE,
                                                "PCS"))));

        UUID sizedMaterial = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(
                        reference(sizedMaterial, "PROFILE-X", "WHITE", "1500", "PCS"),
                        reference(UUID.randomUUID(), "PROFILE-X", "WHITE", "", "PCS"));

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS, result.lines().getFirst().status());
        assertEquals(0, warehouseQuery.availableQuantityCalls);
    }

    @Test
    void ambiguousMaterialVariantsAreReportedExplicitly() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 3))));

        warehouseQuery.materialReferences =
                List.of(
                        reference(UUID.randomUUID(), "PROFILE-X", "WHITE", "6000", "PCS"),
                        reference(UUID.randomUUID(), "PROFILE-X", "WHITE", "3000", "PCS"));

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS, result.lines().getFirst().status());
        assertEquals(
                MaterialAvailabilityOverallStatus.HAS_UNRESOLVED_MATERIALS, result.overallStatus());
    }

    @Test
    void unresolvedMaterialIsNotReportedAsZeroStock() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("UNKNOWN", "WHITE", "PCS", 3))));
        warehouseQuery.materialReferences = List.of();

        MaterialAvailabilityCheckResult result = service.check(orderId);

        var line = result.lines().getFirst();
        assertEquals(MaterialAvailabilityLineStatus.MATERIAL_UNRESOLVED, line.status());
        assertEquals(0, line.mainWarehouseAvailable().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.productionWarehouseAvailable().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.totalAvailable().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.deficit().compareTo(BigDecimal.valueOf(3)));
        assertEquals(0, warehouseQuery.availableQuantityCalls);
    }

    @Test
    void ambiguousMaterialReportsZeroTotalAvailableAndFullDeficit() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 5))));
        warehouseQuery.materialReferences =
                List.of(
                        reference(UUID.randomUUID(), "PROFILE-X", "WHITE", "6000", "PCS"),
                        reference(UUID.randomUUID(), "PROFILE-X", "WHITE", "3000", "PCS"));

        MaterialAvailabilityCheckResult result = service.check(orderId);

        var line = result.lines().getFirst();
        assertEquals(MaterialAvailabilityLineStatus.MATERIAL_AMBIGUOUS, line.status());
        assertEquals(0, line.mainWarehouseAvailable().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.productionWarehouseAvailable().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.totalAvailable().compareTo(BigDecimal.ZERO));
        assertEquals(0, line.deficit().compareTo(BigDecimal.valueOf(5)));
    }

    @Test
    void resolvedLineTotalAvailableEqualsMainPlusProduction() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 10))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(6));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.valueOf(2));

        MaterialAvailabilityCheckResult result = service.check(orderId);

        var line = result.lines().getFirst();
        assertEquals(
                0,
                line.totalAvailable()
                        .compareTo(
                                line.mainWarehouseAvailable().add(line.productionWarehouseAvailable())));
    }

    @Test
    void calculatesMainAndProductionStockWithDeficit() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 10))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(6));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.valueOf(2));

        MaterialAvailabilityCheckResult result = service.check(orderId);

        var line = result.lines().getFirst();
        assertEquals(0, line.mainWarehouseAvailable().compareTo(BigDecimal.valueOf(6)));
        assertEquals(0, line.productionWarehouseAvailable().compareTo(BigDecimal.valueOf(2)));
        assertEquals(0, line.totalAvailable().compareTo(BigDecimal.valueOf(8)));
        assertEquals(0, line.deficit().compareTo(BigDecimal.valueOf(2)));
        assertEquals(MaterialAvailabilityLineStatus.INSUFFICIENT, line.status());
        assertEquals(MaterialAvailabilityOverallStatus.HAS_DEFICIT, result.overallStatus());
    }

    @Test
    void reportsAvailableWhenMainAndProductionCoverRequirement() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 10))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(4));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.valueOf(6));

        MaterialAvailabilityCheckResult result = service.check(orderId);

        var line = result.lines().getFirst();
        assertEquals(MaterialAvailabilityLineStatus.AVAILABLE, line.status());
        assertEquals(0, line.deficit().compareTo(BigDecimal.ZERO));
        assertEquals(MaterialAvailabilityOverallStatus.ALL_AVAILABLE, result.overallStatus());
    }

    @Test
    void successfulCheckAppendsMaterialsCheckedHistoryAndRepeatedChecksAppendAgain() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 10))));
        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(10));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        service.check(orderId);
        service.check(orderId);

        assertEquals(2, historyRepository.size());
        assertEquals(
                2,
                historyRepository
                        .ofType(
                                com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType
                                        .MATERIALS_CHECKED)
                        .size());
        assertEquals(
                List.of(
                        com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType
                                .MATERIALS_CHECKED,
                        com.tmp.production.domain.ProductionHistoryEntry.ProductionHistoryType
                                .MATERIALS_CHECKED),
                historyRepository.listByOrder(orderId).stream()
                        .map(com.tmp.production.domain.ProductionHistoryEntry::historyType)
                        .toList());
    }

    @Test
    void warehouseFailureDoesNotAppendMaterialsCheckedHistory() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 10))));
        warehouseQuery.failOnListMaterials = true;

        assertThrows(RuntimeException.class, () -> service.check(orderId));
        assertEquals(0, historyRepository.size());
    }

    @Test
    void otherWarehouseStockDoesNotMaskDeficit() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 10))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.valueOf(2));
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.valueOf(1));
        warehouseQuery.stock.put(stockKey(materialId, OTHER_WAREHOUSE), BigDecimal.valueOf(100));

        MaterialAvailabilityCheckResult result = service.check(orderId);

        var line = result.lines().getFirst();
        assertEquals(0, line.totalAvailable().compareTo(BigDecimal.valueOf(3)));
        assertEquals(0, line.deficit().compareTo(BigDecimal.valueOf(7)));
    }

    @Test
    void usesScopedWarehouseAvailabilityQueryOnly() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 1))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.ONE);
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ONE);

        service.check(orderId);

        assertTrue(warehouseQuery.availableQuantityCalls >= 2);
        assertEquals(MAIN_WAREHOUSE, warehouseQuery.lastWarehouseIds.get(0));
        assertEquals(PROD_WAREHOUSE, warehouseQuery.lastWarehouseIds.get(1));
    }

    @Test
    void resultIsSnapshotAtInjectedClock() {
        SpecificationId specId = SpecificationId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        launchItem(orderId, itemId, specId, 1);
        specificationQuery.byIdSpec =
                Optional.of(
                        spec(
                                specId,
                                itemId,
                                List.of(materialLine("PROFILE-X", "WHITE", "PCS", 1))));

        UUID materialId = UUID.randomUUID();
        warehouseQuery.materialReferences =
                List.of(reference(materialId, "PROFILE-X", "WHITE", "", "PCS"));
        warehouseQuery.stock.put(stockKey(materialId, MAIN_WAREHOUSE), BigDecimal.ONE);
        warehouseQuery.stock.put(stockKey(materialId, PROD_WAREHOUSE), BigDecimal.ZERO);

        MaterialAvailabilityCheckResult result = service.check(orderId);

        assertEquals(T0, result.checkedAt());
        assertEquals(orderId, result.sourceOrderId());
    }

    private void launchSingleItem(SourceOrderId order, SpecificationId specId, long quantity) {
        launchItem(order, SourceOrderItemId.generate(), specId, quantity);
    }

    private void launchItem(
            SourceOrderId order,
            SourceOrderItemId itemId,
            SpecificationId specId,
            long quantity) {
        ProductionFoundation foundation =
                ProductionFoundation.freeze(order, itemId, specId, T0);
        repository.save(
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(quantity), T0));
    }

    private static ProductionItemState manufactured(
            SourceOrderId orderId, ProductionFoundation foundation, long quantity) {
        ProductionItemState launched =
                ProductionItemState.launch(
                        foundation, ProductionQuantity.positive(quantity), foundation.frozenAt());
        return ProductionItemState.rehydrate(
                foundation,
                ProductionStatus.RELEASED,
                ProductionQuantity.positive(quantity),
                ProductionQuantity.positive(quantity),
                ProductionQuantity.zero(),
                ProductionQuantity.positive(quantity),
                null,
                Instant.parse("2026-08-20T11:00:00Z"));
    }

    private static ResolvedSpecification spec(
            SpecificationId specId,
            SourceOrderItemId itemId,
            List<ResolvedMaterialLine> lines) {
        return new ResolvedSpecification(specId, itemId, BigDecimal.TEN, lines);
    }

    private static ResolvedMaterialLine materialLine(
            String code, String color, String unit, long quantity) {
        return new ResolvedMaterialLine(
                code, code, color, null, BigDecimal.valueOf(quantity), unit);
    }

    private static MaterialReferenceEntry reference(
            UUID id, String article, String color, String size, String unit) {
        return new MaterialReferenceEntry(id, article, article, color, size, unit);
    }

    private static String stockKey(UUID materialId, UUID warehouseId) {
        return materialId + ":" + warehouseId;
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
        java.util.function.Function<SpecificationId, Optional<ResolvedSpecification>> byIdResolver;
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
        int availableQuantityCalls;
        boolean failOnListMaterials;
        final List<UUID> lastWarehouseIds = new ArrayList<>();

        @Override
        public List<WarehouseCatalogEntry> listWarehouses() {
            return warehouses;
        }

        @Override
        public List<MaterialReferenceEntry> listMaterialReferences() {
            listMaterialReferencesCalls++;
            if (failOnListMaterials) {
                throw new RuntimeException("controlled warehouse catalog failure");
            }
            return materialReferences;
        }

        @Override
        public BigDecimal availableQuantity(UUID materialReferenceId, UUID warehouseId) {
            availableQuantityCalls++;
            lastWarehouseIds.add(warehouseId);
            return stock.getOrDefault(stockKey(materialReferenceId, warehouseId), BigDecimal.ZERO);
        }
    }
}
