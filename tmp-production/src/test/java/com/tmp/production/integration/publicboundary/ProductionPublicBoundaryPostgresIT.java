package com.tmp.production.integration.publicboundary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.DomainEvent;
import com.tmp.core.api.event.EventSubscription;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderDto;
import com.tmp.order.api.OrderItemDto;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderStatus;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.ProductionSpecificationDto;
import com.tmp.order.api.imports.OrderImportBatch;
import com.tmp.order.api.imports.OrderImportConfirmResult;
import com.tmp.order.api.imports.OrderImportPosition;
import com.tmp.order.api.imports.OrderImportPreview;
import com.tmp.order.api.imports.OrderImportService;
import com.tmp.order.api.imports.OrderImportSpecificationLine;
import com.tmp.production.ProductionAutoConfiguration;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionApplicationApi.CellAllocationView;
import com.tmp.production.api.ProductionApplicationApi.ItemReleaseView;
import com.tmp.production.api.ProductionApplicationApi.LogicalTransferView;
import com.tmp.production.api.ProductionApplicationApi.MaterialActualUsageView;
import com.tmp.production.api.ProductionApplicationApi.MaterialPlanningSourceView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptResultView;
import com.tmp.production.api.ProductionApplicationApi.ReceiptStatusView;
import com.tmp.production.api.ProductionApplicationApi.ReleasePreviewView;
import com.tmp.production.api.ProductionApplicationApi.TransferCellAllocation;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateLineView;
import com.tmp.production.api.ProductionApplicationApi.TransferTemplateView;
import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateStatus;
import com.tmp.production.api.ProductionQueryApi.ItemProductionStateView;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityLineStatus;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityOverallStatus;
import com.tmp.production.api.ProductionQueryApi.MaterialAvailabilityResultView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionView;
import com.tmp.production.api.ProductionQueryApi.OrderProductionViewStatus;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryEntryView;
import com.tmp.production.api.ProductionQueryApi.ProductionHistoryType;
import com.tmp.production.application.event.OrderAcceptedIntoProduction;
import com.tmp.production.application.event.ProductionReleased;
import com.tmp.production.domain.CancelOrderProductionException;
import com.tmp.production.domain.MaterialReceiptConfirmationException;
import com.tmp.production.domain.ProductionHistoryEntry;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.PermissionId;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import com.tmp.warehouse.WarehouseAutoConfiguration;
import com.tmp.warehouse.api.WarehouseApi.CreateStorageCellCommand;
import com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.ReceiptCommand;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
import com.tmp.warehouse.api.WarehouseApi.WarehouseView;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * STAGE7-018 final public-boundary integration suite: Production ↔ OM ↔ Warehouse through public
 * APIs only, real PostgreSQL, real Document Engine, real Production JDBC.
 */
@Testcontainers
@SpringBootTest(classes = ProductionPublicBoundaryPostgresIT.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value",
            "spring.autoconfigure.exclude=com.tmp.production.ProductionAutoConfiguration"
        })
class ProductionPublicBoundaryPostgresIT {

    private static final char[] OPERATOR_PASSWORD = "operator-secret-value".toCharArray();
    private static final String MATERIAL_CODE = "MAT-PB-018";
    private static final String MATERIAL_COLOR = "Silver";
    private static final String MATERIAL_UOM = "шт.";
    private static final String OTHER_CODE = "MAT-OTHER-018";
    private static final String OTHER_COLOR = "Blue";
    private static final AtomicInteger ORDER_SEQ = new AtomicInteger();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private OrderImportService orderImportService;
    @Autowired private OrderQueryService orderQueryService;
    @Autowired private WarehouseCommandApi warehouseCommandApi;
    @Autowired private WarehouseQueryApi warehouseQueryApi;
    @Autowired private DocumentEngine documentEngine;
    @Autowired
    @Qualifier("transactionalEventPublisher")
    private TransactionalEventPublisher eventPublisher;
    @Autowired private AuthenticationService authenticationService;
    @Autowired private com.tmp.security.api.AuthorizationService authorizationService;
    @Autowired private UserAdministrationService userAdministrationService;
    @Autowired private RoleAdministrationService roleAdministrationService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private EventBus eventBus;
    @Autowired private Clock clock;

    private final List<DomainEvent> deliveredEvents = new CopyOnWriteArrayList<>();
    private EventSubscription eventSubscription;

    private ProductionPublicBoundaryComposition production;
    private UUID mainWarehouseId;
    private UUID productionWarehouseId;
    private UUID cellMA;
    private UUID cellMB;
    private UUID cellPX;
    private UUID cellPY;
    private UUID primaryMaterialRefId;

    @BeforeEach
    void setUp() {
        authenticationService.logout();
        deliveredEvents.clear();
        if (eventSubscription != null) {
            eventSubscription.unsubscribe();
        }
        eventSubscription = eventBus.subscribeDomain(DomainEvent.class, deliveredEvents::add);

        ensureOperator();
        cleanupBusinessData();
        ensureWarehousesAndProductionRuntime();
        recreateCellsAndStock();
        production.itemStates().resetFailure();
        production.historyRepository().resetFailure();
        deliveredEvents.clear();
    }

    @Test
    void launchWholeOrderThroughPublicOmBoundary() {
        ImportedOrder order = importStandardOrder();

        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");

        OrderProductionView view =
                production.queryApi().getOrderProductionView(order.orderId());
        assertEquals(OrderProductionViewStatus.IN_PRODUCTION, view.status());
        assertEquals(2, view.itemCount());

        for (UUID itemId : List.of(order.itemAId(), order.itemBId())) {
            ItemProductionStateView state =
                    production.queryApi().getItemProductionState(itemId).orElseThrow();
            assertEquals(ItemProductionStateStatus.IN_PRODUCTION, state.status());
            assertEquals(0L, state.releasedQuantity());
            ProductionSpecificationDto omSpec =
                    orderQueryService
                            .getCurrentItemSpecification(
                                    com.tmp.order.api.OrderItemId.of(itemId))
                            .orElseThrow();
            assertEquals(omSpec.specificationId().value(), state.specificationId());
        }
        assertEquals(10L, itemState(order.itemAId()).orderedQuantity());
        assertEquals(5L, itemState(order.itemBId()).orderedQuantity());
        assertHistoryCount(order.orderId(), ProductionHistoryType.ORDER_ACCEPTED, 1);
        assertEquals(
                1,
                deliveredEvents.stream()
                        .filter(OrderAcceptedIntoProduction.class::isInstance)
                        .count());
        assertEquals(
                OrderStatus.ACTIVE,
                orderQueryService.getOrder(com.tmp.order.api.OrderId.of(order.orderId()))
                        .orElseThrow()
                        .status());
    }

    @Test
    void launchWholeOrderRollbackRemovesAllJdbcState() {
        ImportedOrder order = importStandardOrder();
        assertEquals(2, orderQueryService.getOrderItems(
                        com.tmp.order.api.OrderId.of(order.orderId()), PageRequest.firstPage())
                .content()
                .size());
        production.itemStates().failOnSaveCount(2);

        RuntimeException thrown =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                production
                                        .applicationApi()
                                        .acceptOrderIntoProduction(order.orderId(), "operator"));
        assertTrue(
                thrown.getMessage().contains("controlled production item state save failure")
                        || (thrown.getCause() != null
                                && thrown.getCause()
                                        .getMessage()
                                        .contains("controlled production item state save failure")),
                () -> "unexpected failure: " + thrown);

        assertTrue(
                production
                        .itemStates()
                        .findBySourceOrderId(SourceOrderId.of(order.orderId()))
                        .isEmpty());
        assertHistoryCount(order.orderId(), ProductionHistoryType.ORDER_ACCEPTED, 0);
        assertEquals(
                0,
                deliveredEvents.stream()
                        .filter(OrderAcceptedIntoProduction.class::isInstance)
                        .count());
        assertEquals(
                OrderStatus.ACTIVE,
                orderQueryService.getOrder(com.tmp.order.api.OrderId.of(order.orderId()))
                        .orElseThrow()
                        .status());
        assertEquals(
                OrderProductionViewStatus.NOT_ACCEPTED,
                production.queryApi().getOrderProductionView(order.orderId()).status());
    }

    @Test
    void materialAvailabilityAndExplicitCheckHistoryThroughPublicWarehouseBoundary() {
        ImportedOrder order = importStandardOrder();
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");

        production.applicationApi().checkMaterialAvailability(order.orderId());
        MaterialAvailabilityResultView availability =
                production.queryApi().getMaterialAvailabilityResult(order.orderId()).orElseThrow();
        assertEquals(MaterialAvailabilityOverallStatus.ALL_AVAILABLE, availability.overallStatus());
        var primary =
                availability.lines().stream()
                        .filter(line -> MATERIAL_CODE.equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, bd(10).compareTo(primary.requiredQuantity()));
        assertEquals(0, bd(10).compareTo(primary.mainWarehouseAvailable()));
        assertEquals(0, bd(0).compareTo(primary.productionWarehouseAvailable()));
        assertEquals(0, bd(10).compareTo(primary.totalAvailable()));
        assertEquals(0, bd(0).compareTo(primary.deficit()));
        assertEquals(MaterialAvailabilityLineStatus.AVAILABLE, primary.status());
        assertHistoryCount(order.orderId(), ProductionHistoryType.MATERIALS_CHECKED, 1);

        int historyBefore =
                production.queryApi().listProductionHistory(order.orderId()).size();
        production.queryApi().getMaterialAvailabilityResult(order.orderId());
        assertEquals(
                historyBefore,
                production.queryApi().listProductionHistory(order.orderId()).size(),
                "read-only query must not append MATERIALS_CHECKED");
    }

    @Test
    void transferMultiCellSendAndReceiptThroughPublicWarehouseBoundary() {
        ImportedOrder order = importStandardOrder();
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");

        TransferTemplateView template =
                production.applicationApi().prepareMaterialTransferTemplate(order.orderId());
        assertEquals(mainWarehouseId, template.sourceWarehouseId());
        assertEquals(productionWarehouseId, template.destinationWarehouseId());
        TransferTemplateLineView primaryLine =
                template.lines().stream()
                        .filter(line -> MATERIAL_CODE.equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        assertEquals(primaryMaterialRefId, primaryLine.materialReferenceId());
        assertEquals(MaterialPlanningSourceView.SPECIFICATION, primaryLine.planningSource());
        assertEquals(0, bd(10).compareTo(primaryLine.requestedQuantity()));

        BigDecimal mainBefore = availableInWarehouse(mainWarehouseId, primaryMaterialRefId);
        BigDecimal prodBefore = availableInWarehouse(productionWarehouseId, primaryMaterialRefId);

        LogicalTransferView logical =
                production
                        .applicationApi()
                        .confirmMaterialTransferCreate(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMA, cellPX, bd(6)),
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMB, cellPY, bd(4))));

        List<UUID> warehouseOperationIds =
                logical.warehouseOperations().stream()
                        .map(WarehouseTransferRefView::warehouseDraftOperationId)
                        .toList();
        assertEquals(2, warehouseOperationIds.size());
        List<UUID> listedDraftIds =
                warehouseQueryApi.listTransferDrafts().stream()
                        .map(TransferRequestView::operationId)
                        .toList();
        assertTrue(listedDraftIds.containsAll(warehouseOperationIds));

        ProductionMaterialTransfer durable =
                production
                        .materialTransfers()
                        .findById(
                                com.tmp.production.domain.ProductionMaterialTransferId.of(
                                        logical.id()))
                        .orElseThrow();
        assertEquals(2, durable.warehouseOperationRefs().size());
        for (WarehouseTransferOperationRef ref : durable.warehouseOperationRefs()) {
            assertEquals(
                    "DRAFT",
                    warehouseQueryApi.getTransferStatus(ref.warehouseDraftOperationId()).status());
            assertTrue(warehouseOperationIds.contains(ref.warehouseDraftOperationId()));
        }
        assertEquals(
                0,
                mainBefore.compareTo(availableInWarehouse(mainWarehouseId, primaryMaterialRefId)),
                "DRAFT must not mutate main AVAILABLE stock");
        assertEquals(
                0,
                prodBefore.compareTo(
                        availableInWarehouse(productionWarehouseId, primaryMaterialRefId)));

        for (WarehouseTransferOperationRef ref : durable.warehouseOperationRefs()) {
            UUID sameId = ref.warehouseDraftOperationId();
            warehouseCommandApi.sendTransfer(sameId);
            assertEquals("SENT", warehouseQueryApi.getTransferStatus(sameId).status());
            assertTrue(
                    warehouseQueryApi.listTransferDrafts().stream()
                            .noneMatch(draft -> draft.operationId().equals(sameId)));
        }
        assertEquals(0, bd(0).compareTo(availableInWarehouse(mainWarehouseId, primaryMaterialRefId)));
        assertEquals(
                0,
                bd(0).compareTo(availableInWarehouse(productionWarehouseId, primaryMaterialRefId)),
                "SENT must not make destination AVAILABLE yet");

        ReceiptResultView receipt =
                production.applicationApi().confirmMaterialReceipt(logical.id());
        assertEquals(ReceiptStatusView.RECEIVED, receipt.status());
        for (UUID operationId : warehouseOperationIds) {
            assertEquals("RECEIVED", warehouseQueryApi.getTransferStatus(operationId).status());
        }
        assertEquals(0, bd(6).compareTo(availableInCell(cellPX, primaryMaterialRefId)));
        assertEquals(0, bd(4).compareTo(availableInCell(cellPY, primaryMaterialRefId)));
        assertHistoryCount(order.orderId(), ProductionHistoryType.MATERIAL_RECEIPT_CONFIRMED, 1);

        ReceiptResultView retry =
                production.applicationApi().confirmMaterialReceipt(logical.id());
        assertEquals(ReceiptStatusView.ALREADY_RECEIVED, retry.status());
        assertHistoryCount(order.orderId(), ProductionHistoryType.MATERIAL_RECEIPT_CONFIRMED, 1);
    }

    @Test
    void releasePreviewNoMutationThenMultiCellConsumptionHappyPath() {
        ImportedOrder order = launchTransferReceiveReady(importStandardOrder());

        ReleasePreviewView preview =
                production
                        .applicationApi()
                        .prepareRelease(
                                order.orderId(),
                                List.of(new ItemReleaseView(order.itemAId(), 10)));
        assertEquals(1, preview.plannedMaterialLines().size());
        assertEquals(
                0,
                bd(10).compareTo(preview.plannedMaterialLines().getFirst().plannedQuantity()));
        assertEquals(
                0,
                preview
                        .defaultActuals()
                        .getFirst()
                        .plannedQuantity()
                        .compareTo(preview.defaultActuals().getFirst().actualQuantity()));
        assertEquals(
                ItemProductionStateStatus.IN_PRODUCTION, itemState(order.itemAId()).status());
        assertEquals(0, bd(6).compareTo(availableInCell(cellPX, primaryMaterialRefId)));
        assertEquals(0, bd(4).compareTo(availableInCell(cellPY, primaryMaterialRefId)));
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTS_RELEASED, 0);

        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 10)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd(10),
                                        List.of(
                                                new CellAllocationView(cellPX, bd(6)),
                                                new CellAllocationView(cellPY, bd(4))))));

        ItemProductionStateView released = itemState(order.itemAId());
        assertEquals(ItemProductionStateStatus.RELEASED, released.status());
        assertEquals(10L, released.releasedQuantity());
        assertEquals(0, bd(0).compareTo(availableInCell(cellPX, primaryMaterialRefId)));
        assertEquals(0, bd(0).compareTo(availableInCell(cellPY, primaryMaterialRefId)));
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTS_RELEASED, 1);
        assertHistoryCount(order.orderId(), ProductionHistoryType.PLAN_FACT_DEVIATION, 0);
        assertEquals(
                1,
                deliveredEvents.stream().filter(ProductionReleased.class::isInstance).count());
    }

    @Test
    void releaseFailureRollsBackPublicWarehouseConsumption() {
        ImportedOrder order = launchTransferReceiveReady(importStandardOrder());
        BigDecimal pxBefore = availableInCell(cellPX, primaryMaterialRefId);
        BigDecimal pyBefore = availableInCell(cellPY, primaryMaterialRefId);
        production
                .historyRepository()
                .failOnFirstAppendOf(ProductionHistoryEntry.ProductionHistoryType.PRODUCTS_RELEASED);
        deliveredEvents.clear();

        assertThrows(
                RuntimeException.class,
                () ->
                        production
                                .applicationApi()
                                .releaseProducts(
                                        order.orderId(),
                                        List.of(new ItemReleaseView(order.itemAId(), 10)),
                                        List.of(
                                                new MaterialActualUsageView(
                                                        order.itemAId(),
                                                        primaryMaterialRefId,
                                                        bd(10),
                                                        List.of(
                                                                new CellAllocationView(
                                                                        cellPX, bd(6)),
                                                                new CellAllocationView(
                                                                        cellPY, bd(4)))))));

        assertEquals(0, pxBefore.compareTo(availableInCell(cellPX, primaryMaterialRefId)));
        assertEquals(0, pyBefore.compareTo(availableInCell(cellPY, primaryMaterialRefId)));
        assertEquals(ItemProductionStateStatus.IN_PRODUCTION, itemState(order.itemAId()).status());
        assertEquals(0L, itemState(order.itemAId()).releasedQuantity());
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTS_RELEASED, 0);
        assertEquals(
                0,
                deliveredEvents.stream().filter(ProductionReleased.class::isInstance).count());
    }

    @Test
    void partialReleaseCumulativePlanningThroughPublicBoundary() {
        ImportedOrder order = importOrder(bd(17), 10, bd(1), 5);
        seedStockForOrderMaterial(bd(17));
        putOtherMaterialInProduction(bd(1));
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");
        TransferTemplateView template =
                production.applicationApi().prepareMaterialTransferTemplate(order.orderId());
        TransferTemplateLineView primaryLine =
                template.lines().stream()
                        .filter(line -> MATERIAL_CODE.equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        LogicalTransferView logical =
                production
                        .applicationApi()
                        .confirmMaterialTransferCreate(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new TransferCellAllocation(
                                                primaryLine.lineId(),
                                                cellMA,
                                                cellPX,
                                                primaryLine.requestedQuantity())));
        ProductionMaterialTransfer durable =
                production
                        .materialTransfers()
                        .findById(
                                com.tmp.production.domain.ProductionMaterialTransferId.of(
                                        logical.id()))
                        .orElseThrow();
        for (WarehouseTransferOperationRef ref : durable.warehouseOperationRefs()) {
            warehouseCommandApi.sendTransfer(ref.warehouseDraftOperationId());
        }
        production.applicationApi().confirmMaterialReceipt(logical.id());

        ReleasePreviewView firstPreview =
                production
                        .applicationApi()
                        .prepareRelease(
                                order.orderId(),
                                List.of(new ItemReleaseView(order.itemAId(), 3)));
        assertEquals(0, bd("5.100000").compareTo(firstPreview.plannedMaterialLines().getFirst().plannedQuantity()));

        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 3)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd("5.100000"),
                                        List.of(
                                                new CellAllocationView(
                                                        cellPX, bd("5.100000"))))));

        ReleasePreviewView secondPreview =
                production
                        .applicationApi()
                        .prepareRelease(
                                order.orderId(),
                                List.of(new ItemReleaseView(order.itemAId(), 4)));
        assertEquals(
                0,
                bd("6.800000")
                        .compareTo(secondPreview.plannedMaterialLines().getFirst().plannedQuantity()));
        assertEquals(
                ItemProductionStateStatus.PARTIALLY_RELEASED, itemState(order.itemAId()).status());
        assertEquals(3L, itemState(order.itemAId()).releasedQuantity());
    }

    @Test
    void cancellationPreservesReleasedAndCancelsUnfinishedWithoutStockReturn() {
        ImportedOrder order = launchTransferReceiveReady(importStandardOrder());
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 3)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd("3.000000"),
                                        List.of(
                                                new CellAllocationView(
                                                        cellPX, bd("3.000000"))))));

        BigDecimal pxBefore = availableInCell(cellPX, primaryMaterialRefId);
        BigDecimal pyBefore = availableInCell(cellPY, primaryMaterialRefId);

        production.applicationApi().cancelOrderProduction(order.orderId(), Optional.empty());

        ItemProductionStateView itemA = itemState(order.itemAId());
        assertEquals(ItemProductionStateStatus.CANCELLED, itemA.status());
        assertEquals(3L, itemA.releasedQuantity());
        assertEquals(0L, itemA.activeProductionQuantity());
        assertEquals(ItemProductionStateStatus.CANCELLED, itemState(order.itemBId()).status());
        assertEquals(
                OrderProductionViewStatus.CANCELLED,
                production.queryApi().getOrderProductionView(order.orderId()).status());
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTION_CANCELLED, 1);
        assertEquals(0, pxBefore.compareTo(availableInCell(cellPX, primaryMaterialRefId)));
        assertEquals(0, pyBefore.compareTo(availableInCell(cellPY, primaryMaterialRefId)));
    }

    @Test
    void manufacturedCancellationIsRejectedWithoutSideEffects() {
        ImportedOrder order = launchTransferReceiveReady(importStandardOrder());
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 10)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd(10),
                                        List.of(
                                                new CellAllocationView(cellPX, bd(6)),
                                                new CellAllocationView(cellPY, bd(4))))));
        // Item B uses OTHER already in production warehouse
        UUID otherRef =
                warehouseQueryApi.listMaterialReferences().stream()
                        .filter(ref -> OTHER_CODE.equals(ref.article()))
                        .findFirst()
                        .orElseThrow()
                        .materialReferenceId();
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemBId(), 5)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemBId(),
                                        otherRef,
                                        bd(1),
                                        List.of(new CellAllocationView(cellPX, bd(1))))));

        assertEquals(
                OrderProductionViewStatus.MANUFACTURED,
                production.queryApi().getOrderProductionView(order.orderId()).status());
        BigDecimal stockBefore = availableInWarehouse(productionWarehouseId, primaryMaterialRefId);
        assertThrows(
                CancelOrderProductionException.class,
                () ->
                        production
                                .applicationApi()
                                .cancelOrderProduction(order.orderId(), Optional.empty()));
        assertEquals(
                OrderProductionViewStatus.MANUFACTURED,
                production.queryApi().getOrderProductionView(order.orderId()).status());
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTION_CANCELLED, 0);
        assertEquals(
                0,
                stockBefore.compareTo(
                        availableInWarehouse(productionWarehouseId, primaryMaterialRefId)));
    }

    @Test
    void fullPartialReleaseThreeFourThreeClosesExactlyThroughPublicBoundary() {
        ImportedOrder order = importOrder(bd(1), 10, bd(1), 5);
        seedStockForOrderMaterial(bd(1));
        putOtherMaterialInProduction(bd(1));
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");
        transferSingleMaterialToProduction(order, bd(1));

        ReleasePreviewView firstPreview =
                production
                        .applicationApi()
                        .prepareRelease(
                                order.orderId(),
                                List.of(new ItemReleaseView(order.itemAId(), 3)));
        assertEquals(
                0,
                bd("0.300000")
                        .compareTo(firstPreview.plannedMaterialLines().getFirst().plannedQuantity()));
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 3)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd("0.300000"),
                                        List.of(
                                                new CellAllocationView(
                                                        cellPX, bd("0.300000"))))));

        ReleasePreviewView secondPreview =
                production
                        .applicationApi()
                        .prepareRelease(
                                order.orderId(),
                                List.of(new ItemReleaseView(order.itemAId(), 4)));
        assertEquals(
                0,
                bd("0.400000")
                        .compareTo(secondPreview.plannedMaterialLines().getFirst().plannedQuantity()));
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 4)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd("0.400000"),
                                        List.of(
                                                new CellAllocationView(
                                                        cellPX, bd("0.400000"))))));

        ReleasePreviewView thirdPreview =
                production
                        .applicationApi()
                        .prepareRelease(
                                order.orderId(),
                                List.of(new ItemReleaseView(order.itemAId(), 3)));
        assertEquals(
                0,
                bd("0.300000")
                        .compareTo(thirdPreview.plannedMaterialLines().getFirst().plannedQuantity()));
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 3)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd("0.300000"),
                                        List.of(
                                                new CellAllocationView(
                                                        cellPX, bd("0.300000"))))));

        ItemProductionStateView itemA = itemState(order.itemAId());
        assertEquals(ItemProductionStateStatus.RELEASED, itemA.status());
        assertEquals(10L, itemA.releasedQuantity());
        assertEquals(0L, itemA.activeProductionQuantity());

        UUID otherRef =
                warehouseQueryApi.listMaterialReferences().stream()
                        .filter(ref -> OTHER_CODE.equals(ref.article()))
                        .findFirst()
                        .orElseThrow()
                        .materialReferenceId();
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemBId(), 5)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemBId(),
                                        otherRef,
                                        bd(1),
                                        List.of(new CellAllocationView(cellPX, bd(1))))));

        assertEquals(
                OrderProductionViewStatus.MANUFACTURED,
                production.queryApi().getOrderProductionView(order.orderId()).status());
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTS_RELEASED, 4);
    }

    @Test
    void partialReleasePlanFactDeviationConsumesActualOnlyThroughPublicBoundary() {
        ImportedOrder order = importOrder(bd(1), 10, bd(1), 5);
        seedStockForOrderMaterial(bd(1));
        putOtherMaterialInProduction(bd(1));
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");
        transferMultiCellMaterialToProduction(order, bd("0.600000"), bd("0.400000"));

        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 3)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd("0.300000"),
                                        List.of(
                                                new CellAllocationView(
                                                        cellPX, bd("0.300000"))))));

        BigDecimal pxBeforeSecond = availableInCell(cellPX, primaryMaterialRefId);
        BigDecimal pyBeforeSecond = availableInCell(cellPY, primaryMaterialRefId);
        ReleasePreviewView secondPreview =
                production
                        .applicationApi()
                        .prepareRelease(
                                order.orderId(),
                                List.of(new ItemReleaseView(order.itemAId(), 4)));
        assertEquals(
                0,
                bd("0.400000")
                        .compareTo(secondPreview.plannedMaterialLines().getFirst().plannedQuantity()));

        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 4)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd("0.350000"),
                                        List.of(
                                                new CellAllocationView(cellPX, bd("0.200000")),
                                                new CellAllocationView(cellPY, bd("0.150000"))))));

        assertEquals(
                0,
                pxBeforeSecond
                        .subtract(bd("0.200000"))
                        .compareTo(availableInCell(cellPX, primaryMaterialRefId)));
        assertEquals(
                0,
                pyBeforeSecond
                        .subtract(bd("0.150000"))
                        .compareTo(availableInCell(cellPY, primaryMaterialRefId)));
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTS_RELEASED, 2);
        assertHistoryCount(order.orderId(), ProductionHistoryType.PLAN_FACT_DEVIATION, 1);
        assertEquals(7L, itemState(order.itemAId()).releasedQuantity());

        assertThrows(
                RuntimeException.class,
                () ->
                        production
                                .applicationApi()
                                .releaseProducts(
                                        order.orderId(),
                                        List.of(new ItemReleaseView(order.itemAId(), 1)),
                                        List.of(
                                                new MaterialActualUsageView(
                                                        order.itemAId(),
                                                        primaryMaterialRefId,
                                                        bd("0.100000"),
                                                        List.of(
                                                                new CellAllocationView(
                                                                        cellPX, bd("0.060000")),
                                                                new CellAllocationView(
                                                                        cellPY, bd("0.050000")))))));
    }

    @Test
    void zeroActualReleaseSkipsWarehouseConsumptionThroughPublicBoundary() {
        ImportedOrder order = importOrder(bd(1), 10, bd(1), 5);
        seedStockForOrderMaterial(bd(1));
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");
        transferSingleMaterialToProduction(order, bd(1));

        BigDecimal stockBefore = availableInCell(cellPX, primaryMaterialRefId);
        production
                .applicationApi()
                .releaseProducts(
                        order.orderId(),
                        List.of(new ItemReleaseView(order.itemAId(), 3)),
                        List.of(
                                new MaterialActualUsageView(
                                        order.itemAId(),
                                        primaryMaterialRefId,
                                        bd(0),
                                        List.of())));

        assertEquals(0, stockBefore.compareTo(availableInCell(cellPX, primaryMaterialRefId)));
        assertEquals(3L, itemState(order.itemAId()).releasedQuantity());
        assertHistoryCount(order.orderId(), ProductionHistoryType.PRODUCTS_RELEASED, 1);
        assertHistoryCount(order.orderId(), ProductionHistoryType.PLAN_FACT_DEVIATION, 1);
    }

    @Test
    void transferReceiptLifecycleFailClosedThroughPublicBoundary() {
        ImportedOrder order = importStandardOrder();
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");
        TransferTemplateView template =
                production.applicationApi().prepareMaterialTransferTemplate(order.orderId());
        TransferTemplateLineView primaryLine =
                template.lines().stream()
                        .filter(line -> MATERIAL_CODE.equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        LogicalTransferView logical =
                production
                        .applicationApi()
                        .confirmMaterialTransferCreate(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMA, cellPX, bd(6)),
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMB, cellPY, bd(4))));
        List<UUID> operationIds =
                logical.warehouseOperations().stream()
                        .map(WarehouseTransferRefView::warehouseDraftOperationId)
                        .toList();

        assertThrows(
                MaterialReceiptConfirmationException.class,
                () -> production.applicationApi().confirmMaterialReceipt(logical.id()));

        warehouseCommandApi.sendTransfer(operationIds.get(0));
        assertThrows(
                MaterialReceiptConfirmationException.class,
                () -> production.applicationApi().confirmMaterialReceipt(logical.id()));

        warehouseCommandApi.sendTransfer(operationIds.get(1));
        ReceiptResultView receipt =
                production.applicationApi().confirmMaterialReceipt(logical.id());
        assertEquals(ReceiptStatusView.RECEIVED, receipt.status());
        for (UUID operationId : operationIds) {
            assertEquals("RECEIVED", warehouseQueryApi.getTransferStatus(operationId).status());
        }

        ReceiptResultView retry =
                production.applicationApi().confirmMaterialReceipt(logical.id());
        assertEquals(ReceiptStatusView.ALREADY_RECEIVED, retry.status());
    }

    private void transferSingleMaterialToProduction(ImportedOrder order, BigDecimal quantity) {
        TransferTemplateView template =
                production.applicationApi().prepareMaterialTransferTemplate(order.orderId());
        TransferTemplateLineView primaryLine =
                template.lines().stream()
                        .filter(line -> MATERIAL_CODE.equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        LogicalTransferView logical =
                production
                        .applicationApi()
                        .confirmMaterialTransferCreate(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new TransferCellAllocation(
                                                primaryLine.lineId(),
                                                cellMA,
                                                cellPX,
                                                quantity)));
        for (WarehouseTransferRefView ref : logical.warehouseOperations()) {
            warehouseCommandApi.sendTransfer(ref.warehouseDraftOperationId());
        }
        production.applicationApi().confirmMaterialReceipt(logical.id());
    }

    private void transferMultiCellMaterialToProduction(
            ImportedOrder order, BigDecimal pxQuantity, BigDecimal pyQuantity) {
        TransferTemplateView template =
                production.applicationApi().prepareMaterialTransferTemplate(order.orderId());
        TransferTemplateLineView primaryLine =
                template.lines().stream()
                        .filter(line -> MATERIAL_CODE.equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        LogicalTransferView logical =
                production
                        .applicationApi()
                        .confirmMaterialTransferCreate(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMA, cellPX, pxQuantity),
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMB, cellPY, pyQuantity)));
        for (WarehouseTransferRefView ref : logical.warehouseOperations()) {
            warehouseCommandApi.sendTransfer(ref.warehouseDraftOperationId());
        }
        production.applicationApi().confirmMaterialReceipt(logical.id());
    }

    private ImportedOrder launchTransferReceiveReady(ImportedOrder order) {
        production.applicationApi().acceptOrderIntoProduction(order.orderId(), "operator");
        TransferTemplateView template =
                production.applicationApi().prepareMaterialTransferTemplate(order.orderId());
        TransferTemplateLineView primaryLine =
                template.lines().stream()
                        .filter(line -> MATERIAL_CODE.equals(line.materialCode()))
                        .findFirst()
                        .orElseThrow();
        LogicalTransferView logical =
                production
                        .applicationApi()
                        .confirmMaterialTransferCreate(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMA, cellPX, bd(6)),
                                        new TransferCellAllocation(
                                                primaryLine.lineId(), cellMB, cellPY, bd(4))));
        for (WarehouseTransferRefView ref : logical.warehouseOperations()) {
            warehouseCommandApi.sendTransfer(ref.warehouseDraftOperationId());
        }
        production.applicationApi().confirmMaterialReceipt(logical.id());
        return order;
    }

    private ImportedOrder importStandardOrder() {
        return importOrder(bd(10), 10, bd(1), 5);
    }

    private ImportedOrder importOrder(
            BigDecimal primaryLineQty, int itemAQty, BigDecimal otherLineQty, int itemBQty) {
        String orderNumber = "PB-" + ORDER_SEQ.incrementAndGet() + "-" + UUID.randomUUID();
        OrderImportBatch batch =
                OrderImportBatch.of(
                        "MANUAL",
                        orderNumber + ".fixture",
                        "checksum-" + orderNumber,
                        orderNumber,
                        LocalDate.of(2026, 8, 25),
                        null,
                        "Public Boundary Customer",
                        List.of(
                                OrderImportPosition.of(
                                        "1",
                                        "PROD-A",
                                        "Product A",
                                        itemAQty,
                                        List.of(
                                                OrderImportSpecificationLine.of(
                                                        MATERIAL_CODE,
                                                        "Primary material",
                                                        MATERIAL_COLOR,
                                                        new BigDecimal("1000"),
                                                        MATERIAL_UOM,
                                                        primaryLineQty))),
                                OrderImportPosition.of(
                                        "2",
                                        "PROD-B",
                                        "Product B",
                                        itemBQty,
                                        List.of(
                                                OrderImportSpecificationLine.of(
                                                        OTHER_CODE,
                                                        "Other material",
                                                        OTHER_COLOR,
                                                        new BigDecimal("500"),
                                                        MATERIAL_UOM,
                                                        otherLineQty)))));
        OrderImportPreview preview = orderImportService.preview(batch);
        assertTrue(preview.canConfirm(), () -> preview.errors().toString());
        OrderImportConfirmResult confirm =
                orderImportService.confirm(preview.preparedPlan().orElseThrow());
        OrderDto order =
                orderQueryService.getOrder(confirm.orderId()).orElseThrow();
        assertEquals(OrderStatus.ACTIVE, order.status());
        List<OrderItemDto> items =
                orderQueryService
                        .getOrderItems(confirm.orderId(), PageRequest.firstPage())
                        .content();
        UUID itemA =
                items.stream()
                        .filter(item -> "1".equals(item.externalPositionNumber()))
                        .findFirst()
                        .orElseThrow()
                        .orderItemId()
                        .value();
        UUID itemB =
                items.stream()
                        .filter(item -> "2".equals(item.externalPositionNumber()))
                        .findFirst()
                        .orElseThrow()
                        .orderItemId()
                        .value();
        return new ImportedOrder(confirm.orderId().value(), itemA, itemB);
    }

    private void ensureWarehousesAndProductionRuntime() {
        if (production != null) {
            return;
        }
        WarehouseView main =
                findOrCreateWarehouse("PB-MAIN", "Main Warehouse");
        WarehouseView prod =
                findOrCreateWarehouse("PB-PROD", "Production Warehouse");
        mainWarehouseId = main.warehouseId();
        productionWarehouseId = prod.warehouseId();
        production =
                ProductionPublicBoundaryComposition.wire(
                        jdbc,
                        transactionManager,
                        clock,
                        documentEngine,
                        eventPublisher,
                        authorizationService,
                        orderQueryService,
                        warehouseQueryApi,
                        warehouseCommandApi,
                        mainWarehouseId,
                        productionWarehouseId);
    }

    private WarehouseView findOrCreateWarehouse(String code, String name) {
        return warehouseQueryApi.listWarehouses().stream()
                .filter(w -> code.equals(w.code()))
                .findFirst()
                .orElseGet(
                        () ->
                                warehouseCommandApi.createWarehouse(
                                        new CreateWarehouseCommand(code, name, true)));
    }

    private void recreateCellsAndStock() {
        // Cells may already exist from prior tests; recreate fresh stock via receipt only after
        // ensuring cells.
        cellMA = findOrCreateCell(mainWarehouseId, "M-A");
        cellMB = findOrCreateCell(mainWarehouseId, "M-B");
        cellPX = findOrCreateCell(productionWarehouseId, "P-X");
        cellPY = findOrCreateCell(productionWarehouseId, "P-Y");
        seedStockForOrderMaterial(bd(10));
        putOtherMaterialInProduction(bd(1));
    }

    private UUID findOrCreateCell(UUID warehouseId, String code) {
        return warehouseQueryApi.listStorageCells(warehouseId).stream()
                .filter(cell -> code.equals(cell.code()))
                .map(StorageCellView::storageCellId)
                .findFirst()
                .orElseGet(
                        () ->
                                warehouseCommandApi
                                        .createStorageCell(
                                                new CreateStorageCellCommand(
                                                        warehouseId, code, true))
                                        .storageCellId());
    }

    private void seedStockForOrderMaterial(BigDecimal totalOnMain) {
        if (totalOnMain.compareTo(bd(10)) == 0) {
            OperationResult receiptA =
                    warehouseCommandApi.receive(
                            new ReceiptCommand(
                                    MATERIAL_CODE,
                                    "Primary material",
                                    MATERIAL_COLOR,
                                    "",
                                    MATERIAL_UOM,
                                    bd(6),
                                    mainWarehouseId,
                                    cellMA));
            primaryMaterialRefId = receiptA.materialReferenceId();
            warehouseCommandApi.receive(
                    new ReceiptCommand(
                            MATERIAL_CODE,
                            "Primary material",
                            MATERIAL_COLOR,
                            "",
                            MATERIAL_UOM,
                            bd(4),
                            mainWarehouseId,
                            cellMB));
            return;
        }
        OperationResult receipt =
                warehouseCommandApi.receive(
                        new ReceiptCommand(
                                MATERIAL_CODE,
                                "Primary material",
                                MATERIAL_COLOR,
                                "",
                                MATERIAL_UOM,
                                totalOnMain,
                                mainWarehouseId,
                                cellMA));
        primaryMaterialRefId = receipt.materialReferenceId();
    }

    private void putOtherMaterialInProduction(BigDecimal qty) {
        warehouseCommandApi.receive(
                new ReceiptCommand(
                        OTHER_CODE,
                        "Other material",
                        OTHER_COLOR,
                        "",
                        MATERIAL_UOM,
                        qty,
                        productionWarehouseId,
                        cellPX));
    }

    private void ensureOperator() {
        authenticationService.login(Login.of("admin"), "bootstrap-secret-value".toCharArray());
        UserSummary operator =
                userAdministrationService.listUsers(0, 100, null).stream()
                        .filter(user -> "pb-operator".equalsIgnoreCase(user.login().value()))
                        .findFirst()
                        .orElseGet(
                                () ->
                                        userAdministrationService.createUser(
                                                Login.of("pb-operator"),
                                                DisplayName.of("Public Boundary Operator"),
                                                OPERATOR_PASSWORD.clone()));
        for (PermissionId permission : PublicBoundaryPermissions.ORDER_IMPORT_AND_VIEW) {
            roleAdministrationService.grantIndividualPermission(operator.id(), permission);
        }
        for (PermissionId permission : PublicBoundaryPermissions.WAREHOUSE_COMMAND_AND_QUERY) {
            roleAdministrationService.grantIndividualPermission(operator.id(), permission);
        }
        for (PermissionId permission : PublicBoundaryPermissions.PRODUCTION_ALL) {
            roleAdministrationService.grantIndividualPermission(operator.id(), permission);
        }
        authenticationService.logout();
        authenticationService.login(Login.of("pb-operator"), OPERATOR_PASSWORD.clone());
    }

    private void cleanupBusinessData() {
        jdbc.update("DELETE FROM documents.document_lifecycle_journal");
        jdbc.update("DELETE FROM documents.document_versions");
        jdbc.update("DELETE FROM documents.documents");
        jdbc.update("TRUNCATE TABLE production.production_history");
        jdbc.update("DELETE FROM production.production_release_material_lines");
        jdbc.update("DELETE FROM production.production_release_item_lines");
        jdbc.update("DELETE FROM production.production_releases");
        jdbc.update("DELETE FROM production.material_transfer_operation_refs");
        jdbc.update("DELETE FROM production.material_transfers");
        jdbc.update("DELETE FROM production.material_transfer_template_line_source_items");
        jdbc.update("DELETE FROM production.material_transfer_template_line_cutting_refs");
        jdbc.update("DELETE FROM production.material_transfer_template_lines");
        jdbc.update("DELETE FROM production.material_transfer_templates");
        jdbc.update("DELETE FROM production.production_cancellation_item_lines");
        jdbc.update("DELETE FROM production.production_cancellations");
        jdbc.update("DELETE FROM production.production_item_cutting_plan_links");
        jdbc.update("DELETE FROM production.production_item_states");
        jdbc.update("DELETE FROM warehouse.transfer_operation_context");
        jdbc.update("DELETE FROM warehouse.warehouse_movements");
        jdbc.update("DELETE FROM warehouse.warehouse_operations");
        jdbc.update("DELETE FROM warehouse.stock_positions");
        jdbc.update("DELETE FROM warehouse.material_references");
        jdbc.update("DELETE FROM order_management.order_document_processing");
        jdbc.update("DELETE FROM order_management.order_document_payload");
        jdbc.update("DELETE FROM order_management.item_specification_lines");
        jdbc.update("DELETE FROM order_management.item_specifications");
        jdbc.update("DELETE FROM order_management.order_item_revisions");
        jdbc.update("DELETE FROM order_management.order_items");
        jdbc.update("DELETE FROM order_management.orders");
    }

    private ItemProductionStateView itemState(UUID itemId) {
        return production.queryApi().getItemProductionState(itemId).orElseThrow();
    }

    private void assertHistoryCount(UUID orderId, ProductionHistoryType type, int expected) {
        long count =
                production.queryApi().listProductionHistory(orderId).stream()
                        .map(ProductionHistoryEntryView::historyType)
                        .filter(type::equals)
                        .count();
        assertEquals(expected, count);
    }

    private BigDecimal availableInWarehouse(UUID warehouseId, UUID materialRefId) {
        return warehouseQueryApi.getStockByMaterialReferenceId(materialRefId).stream()
                .filter(stock -> warehouseId.equals(stock.warehouseId()))
                .filter(stock -> stock.stockState() == StockStateView.AVAILABLE)
                .map(StockView::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal availableInCell(UUID cellId, UUID materialRefId) {
        return warehouseQueryApi.getStockByMaterialReferenceId(materialRefId).stream()
                .filter(stock -> cellId.equals(stock.storageCellId()))
                .filter(stock -> stock.stockState() == StockStateView.AVAILABLE)
                .map(StockView::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record ImportedOrder(UUID orderId, UUID itemAId, UUID itemBId) {}

    @SpringBootApplication(exclude = {ProductionAutoConfiguration.class})
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        com.tmp.security.SecurityAutoConfiguration.class,
        OrderManagementAutoConfiguration.class,
        WarehouseAutoConfiguration.class
    })
    static class TestApplication {

        /**
         * Registers Production permission catalogue with Security at startup. Runtime Production
         * APIs used by tests are composed separately against real JDBC/Document Engine.
         */
        @org.springframework.context.annotation.Bean
        ProductionQueryApi productionQueryApiPlaceholder() {
            return new ProductionQueryApi() {
                @Override
                public OrderProductionView getOrderProductionView(UUID orderId) {
                    throw new UnsupportedOperationException("use composed ProductionQueryApi");
                }

                @Override
                public Optional<ItemProductionStateView> getItemProductionState(UUID orderItemId) {
                    throw new UnsupportedOperationException("use composed ProductionQueryApi");
                }

                @Override
                public Optional<MaterialAvailabilityResultView> getMaterialAvailabilityResult(
                        UUID orderId) {
                    throw new UnsupportedOperationException("use composed ProductionQueryApi");
                }

                @Override
                public List<ProductionHistoryEntryView> listProductionHistory(UUID orderId) {
                    throw new UnsupportedOperationException("use composed ProductionQueryApi");
                }
            };
        }

        @org.springframework.context.annotation.Bean
        com.tmp.production.security.ProductionCapability productionCapability(
                ProductionQueryApi productionQueryApiPlaceholder) {
            return new com.tmp.production.security.ProductionCapability(productionQueryApiPlaceholder);
        }
    }
}
