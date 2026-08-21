package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.ConfirmMaterialTransferCommand.CellAllocation;
import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferConfirmationException;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.MaterialTransferTemplateNotEditableException;
import com.tmp.production.domain.MaterialTransferTemplateOptimisticLockException;
import com.tmp.production.domain.MaterialTransferTemplateStatus;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.warehouse.api.WarehouseApi.CreateTransferDraftCommand;
import com.tmp.warehouse.api.WarehouseApi.StorageCellView;
import com.tmp.warehouse.api.WarehouseApi.TransferRequestView;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ConfirmMaterialTransferServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-21T12:00:00Z");
    private static final UUID MAIN = UUID.fromString("00000000-0000-4000-8000-000000000101");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000102");
    private static final UUID MAIN_CELL_A = UUID.fromString("00000000-0000-4000-8000-000000000201");
    private static final UUID MAIN_CELL_B = UUID.fromString("00000000-0000-4000-8000-000000000202");
    private static final UUID PROD_CELL = UUID.fromString("00000000-0000-4000-8000-000000000203");
    private static final UUID OTHER_CELL = UUID.fromString("00000000-0000-4000-8000-000000000299");
    private static final UUID INACTIVE_CELL = UUID.fromString("00000000-0000-4000-8000-000000000298");

    private InMemoryTemplateRepository templates;
    private InMemoryTransferRepository transfers;
    private RecordingWarehouseCommandApi commands;
    private StubWarehouseQueryApi queries;
    private ConfirmMaterialTransferService service;

    @BeforeEach
    void setUp() {
        templates = new InMemoryTemplateRepository();
        transfers = new InMemoryTransferRepository();
        commands = new RecordingWarehouseCommandApi();
        queries = new StubWarehouseQueryApi();
        queries.sourceCells =
                List.of(
                        new StorageCellView(MAIN_CELL_A, MAIN, "M-A", true),
                        new StorageCellView(MAIN_CELL_B, MAIN, "M-B", true),
                        new StorageCellView(INACTIVE_CELL, MAIN, "M-OFF", false));
        queries.destinationCells =
                List.of(new StorageCellView(PROD_CELL, PROD, "P-01", true));
        service =
                new ConfirmMaterialTransferService(
                        templates,
                        transfers,
                        commands,
                        queries,
                        new PassthroughTransactionManager(),
                        Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void confirmCreatesOneLogicalTransferAndWarehouseDraftsFromAllocations() {
        MaterialTransferTemplate template = persistEditable(line("MAT-A", bd(10)));
        MaterialTransferTemplateLine line = template.lines().getFirst();

        ProductionMaterialTransfer result =
                service.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                template.templateId(),
                                template.version(),
                                List.of(
                                        new CellAllocation(
                                                line.lineId(), MAIN_CELL_A, PROD_CELL, bd(6)),
                                        new CellAllocation(
                                                line.lineId(), MAIN_CELL_B, PROD_CELL, bd(4)))));

        assertEquals(template.templateId(), result.templateId());
        assertEquals(2, result.warehouseOperationRefs().size());
        assertEquals(2, commands.createDraftCalls.size());
        assertEquals(0, commands.sendCalls);
        assertEquals(0, commands.receiveCalls);
        BigDecimal total =
                result.warehouseOperationRefs().stream()
                        .map(WarehouseTransferOperationRef::quantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, total.compareTo(bd(10)));
        assertEquals(
                MaterialTransferTemplateStatus.CONFIRMED,
                templates.findById(template.templateId()).orElseThrow().status());
    }

    @Test
    void confirmUsesRequestedQuantityNotRecommended() {
        MaterialTransferTemplate prepared = persistEditable(line("MAT-A", bd(10)));
        MaterialTransferTemplate edited =
                templates.save(
                        prepared.changeRequestedQuantity(
                                prepared.lines().getFirst().lineId(), bd(7), T0));
        MaterialTransferTemplateLine line = edited.lines().getFirst();

        ProductionMaterialTransfer result =
                service.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                edited.templateId(),
                                edited.version(),
                                List.of(
                                        new CellAllocation(
                                                line.lineId(), MAIN_CELL_A, PROD_CELL, bd(7)))));

        assertEquals(1, result.warehouseOperationRefs().size());
        assertEquals(0, result.warehouseOperationRefs().getFirst().quantity().compareTo(bd(7)));
        assertEquals(0, commands.createDraftCalls.getFirst().quantity().compareTo(bd(7)));
    }

    @Test
    void excludedLineDoesNotCreateWarehouseDraft() {
        MaterialTransferTemplateLine lineA = line("MAT-A", bd(5));
        MaterialTransferTemplateLine lineB = line("MAT-B", bd(5));
        MaterialTransferTemplateLine lineC = line("MAT-C", bd(5));
        MaterialTransferTemplate template =
                templates.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(),
                                MAIN,
                                PROD,
                                T0,
                                List.of(lineA, lineB, lineC)));
        MaterialTransferTemplate withExcluded =
                templates.save(template.excludeLine(lineB.lineId(), T0));

        ProductionMaterialTransfer result =
                service.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                withExcluded.templateId(),
                                withExcluded.version(),
                                List.of(
                                        new CellAllocation(
                                                lineA.lineId(), MAIN_CELL_A, PROD_CELL, bd(5)),
                                        new CellAllocation(
                                                lineC.lineId(), MAIN_CELL_A, PROD_CELL, bd(5)))));

        assertEquals(2, result.warehouseOperationRefs().size());
        assertEquals(2, commands.createDraftCalls.size());
        Set<UUID> materials =
                Set.copyOf(
                        commands.createDraftCalls.stream()
                                .map(CreateTransferDraftCommand::materialReferenceId)
                                .toList());
        assertEquals(
                Set.of(lineA.materialReferenceId().value(), lineC.materialReferenceId().value()),
                materials);
    }

    @Test
    void allocationForExcludedLineIsRejected() {
        MaterialTransferTemplateLine lineA = line("MAT-A", bd(5));
        MaterialTransferTemplateLine lineB = line("MAT-B", bd(5));
        MaterialTransferTemplate template =
                templates.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(), MAIN, PROD, T0, List.of(lineA, lineB)));
        MaterialTransferTemplate withExcluded =
                templates.save(template.excludeLine(lineB.lineId(), T0));

        assertThrows(
                MaterialTransferConfirmationException.class,
                () ->
                        service.confirmMaterialTransferCreate(
                                new ConfirmMaterialTransferCommand(
                                        withExcluded.templateId(),
                                        withExcluded.version(),
                                        List.of(
                                                new CellAllocation(
                                                        lineA.lineId(),
                                                        MAIN_CELL_A,
                                                        PROD_CELL,
                                                        bd(5)),
                                                new CellAllocation(
                                                        lineB.lineId(),
                                                        MAIN_CELL_A,
                                                        PROD_CELL,
                                                        bd(5))))));
        assertEquals(0, commands.createDraftCalls.size());
    }

    @Test
    void staleVersionRejectedWithoutWarehouseCalls() {
        MaterialTransferTemplate template = persistEditable(line("MAT-A", bd(5)));
        templates.save(
                template.changeRequestedQuantity(
                        template.lines().getFirst().lineId(), bd(4), T0));

        assertThrows(
                MaterialTransferConfirmationException.class,
                () ->
                        service.confirmMaterialTransferCreate(
                                new ConfirmMaterialTransferCommand(
                                        template.templateId(),
                                        0L,
                                        List.of(
                                                new CellAllocation(
                                                        template.lines().getFirst().lineId(),
                                                        MAIN_CELL_A,
                                                        PROD_CELL,
                                                        bd(4))))));
        assertEquals(0, commands.createDraftCalls.size());
    }

    @Test
    void sourceCellFromOtherWarehouseRejectedBeforeWarehouseCommand() {
        MaterialTransferTemplate template = persistEditable(line("MAT-A", bd(5)));
        MaterialTransferTemplateLine line = template.lines().getFirst();

        assertThrows(
                MaterialTransferConfirmationException.class,
                () ->
                        service.confirmMaterialTransferCreate(
                                new ConfirmMaterialTransferCommand(
                                        template.templateId(),
                                        template.version(),
                                        List.of(
                                                new CellAllocation(
                                                        line.lineId(),
                                                        OTHER_CELL,
                                                        PROD_CELL,
                                                        bd(5))))));
        assertEquals(0, commands.createDraftCalls.size());
    }

    @Test
    void inactiveSourceCellRejected() {
        MaterialTransferTemplate template = persistEditable(line("MAT-A", bd(5)));
        MaterialTransferTemplateLine line = template.lines().getFirst();

        assertThrows(
                MaterialTransferConfirmationException.class,
                () ->
                        service.confirmMaterialTransferCreate(
                                new ConfirmMaterialTransferCommand(
                                        template.templateId(),
                                        template.version(),
                                        List.of(
                                                new CellAllocation(
                                                        line.lineId(),
                                                        INACTIVE_CELL,
                                                        PROD_CELL,
                                                        bd(5))))));
        assertEquals(0, commands.createDraftCalls.size());
    }

    @Test
    void doubleConfirmReturnsSameLogicalTransferWithoutNewDrafts() {
        MaterialTransferTemplate template = persistEditable(line("MAT-A", bd(5)));
        MaterialTransferTemplateLine line = template.lines().getFirst();
        ConfirmMaterialTransferCommand command =
                new ConfirmMaterialTransferCommand(
                        template.templateId(),
                        template.version(),
                        List.of(new CellAllocation(line.lineId(), MAIN_CELL_A, PROD_CELL, bd(5))));

        ProductionMaterialTransfer first = service.confirmMaterialTransferCreate(command);
        ProductionMaterialTransfer second =
                service.confirmMaterialTransferCreate(
                        new ConfirmMaterialTransferCommand(
                                template.templateId(),
                                99L,
                                List.of(
                                        new CellAllocation(
                                                line.lineId(), MAIN_CELL_A, PROD_CELL, bd(5)))));

        assertEquals(first.logicalTransferId(), second.logicalTransferId());
        assertEquals(1, commands.createDraftCalls.size());
        assertEquals(1, transfers.store.size());
    }

    @Test
    void editAfterConfirmIsRejected() {
        MaterialTransferTemplate template = persistEditable(line("MAT-A", bd(5)));
        MaterialTransferTemplateLine line = template.lines().getFirst();
        service.confirmMaterialTransferCreate(
                new ConfirmMaterialTransferCommand(
                        template.templateId(),
                        template.version(),
                        List.of(new CellAllocation(line.lineId(), MAIN_CELL_A, PROD_CELL, bd(5)))));

        assertThrows(
                MaterialTransferTemplateNotEditableException.class,
                () ->
                        templates
                                .findById(template.templateId())
                                .orElseThrow()
                                .changeRequestedQuantity(line.lineId(), bd(3), T0));
        assertThrows(
                MaterialTransferTemplateNotEditableException.class,
                () ->
                        templates
                                .findById(template.templateId())
                                .orElseThrow()
                                .excludeLine(line.lineId(), T0));
        assertThrows(
                MaterialTransferTemplateNotEditableException.class,
                () ->
                        templates
                                .findById(template.templateId())
                                .orElseThrow()
                                .restoreLine(line.lineId(), T0));
    }

    @Test
    void partialWarehouseFailureLeavesNoLogicalTransferAndKeepsDraftTemplate() {
        MaterialTransferTemplateLine lineA = line("MAT-A", bd(5));
        MaterialTransferTemplateLine lineB = line("MAT-B", bd(5));
        MaterialTransferTemplate template =
                templates.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(), MAIN, PROD, T0, List.of(lineA, lineB)));
        commands.failOnCall = 2;

        assertThrows(
                RuntimeException.class,
                () ->
                        service.confirmMaterialTransferCreate(
                                new ConfirmMaterialTransferCommand(
                                        template.templateId(),
                                        template.version(),
                                        List.of(
                                                new CellAllocation(
                                                        lineA.lineId(),
                                                        MAIN_CELL_A,
                                                        PROD_CELL,
                                                        bd(5)),
                                                new CellAllocation(
                                                        lineB.lineId(),
                                                        MAIN_CELL_A,
                                                        PROD_CELL,
                                                        bd(5))))));

        assertTrue(transfers.store.isEmpty());
        assertEquals(
                MaterialTransferTemplateStatus.DRAFT,
                templates.findById(template.templateId()).orElseThrow().status());
        // In-memory TX is passthrough; production-side contract asserts no persisted logical
        // transfer / template remains DRAFT. Cross-capability Warehouse draft A rollback under
        // shared ACID is covered by ConfirmMaterialTransferPostgresIT (STAGE7-018 may deepen).
    }

    @Test
    void allocationTotalMismatchRejected() {
        MaterialTransferTemplate template = persistEditable(line("MAT-A", bd(10)));
        MaterialTransferTemplateLine line = template.lines().getFirst();

        assertThrows(
                MaterialTransferConfirmationException.class,
                () ->
                        service.confirmMaterialTransferCreate(
                                new ConfirmMaterialTransferCommand(
                                        template.templateId(),
                                        template.version(),
                                        List.of(
                                                new CellAllocation(
                                                        line.lineId(),
                                                        MAIN_CELL_A,
                                                        PROD_CELL,
                                                        bd(6))))));
        assertEquals(0, commands.createDraftCalls.size());
    }

    private MaterialTransferTemplate persistEditable(MaterialTransferTemplateLine line) {
        return templates.save(
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(), MAIN, PROD, T0, List.of(line)));
    }

    private static MaterialTransferTemplateLine line(String code, BigDecimal quantity) {
        return MaterialTransferTemplateLine.create(
                MaterialReferenceId.generate(),
                code,
                code,
                "WHITE",
                "PCS",
                quantity,
                MaterialPlanningSource.SPECIFICATION,
                null,
                CuttingLinkStatus.NONE,
                List.of(),
                Set.of(SourceOrderItemId.generate()),
                quantity,
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
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
                            existing == null ? 0L : template.version() + 1,
                            template.status(),
                            template.confirmedAt().orElse(null));
            store.put(saved.templateId(), saved);
            return saved;
        }

        @Override
        public Optional<MaterialTransferTemplate> findById(MaterialTransferTemplateId templateId) {
            return Optional.ofNullable(store.get(templateId));
        }
    }

    private static final class InMemoryTransferRepository
            implements ProductionMaterialTransferRepository {
        private final Map<ProductionMaterialTransferId, ProductionMaterialTransfer> store =
                new ConcurrentHashMap<>();

        @Override
        public ProductionMaterialTransfer save(ProductionMaterialTransfer transfer) {
            store.put(transfer.logicalTransferId(), transfer);
            return transfer;
        }

        @Override
        public Optional<ProductionMaterialTransfer> findById(
                ProductionMaterialTransferId logicalTransferId) {
            return Optional.ofNullable(store.get(logicalTransferId));
        }

        @Override
        public Optional<ProductionMaterialTransfer> findByTemplateId(
                MaterialTransferTemplateId templateId) {
            return store.values().stream()
                    .filter(transfer -> transfer.templateId().equals(templateId))
                    .findFirst();
        }

        @Override
        public List<ProductionMaterialTransfer> findBySourceOrderId(SourceOrderId sourceOrderId) {
            return store.values().stream()
                    .filter(transfer -> transfer.sourceOrderId().equals(sourceOrderId))
                    .toList();
        }
    }

    private static final class RecordingWarehouseCommandApi implements WarehouseCommandApi {
        private final List<CreateTransferDraftCommand> createDraftCalls = new ArrayList<>();
        private int sendCalls;
        private int receiveCalls;
        private int failOnCall = -1;
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public TransferRequestView createTransferDraft(CreateTransferDraftCommand command) {
            int n = callCount.incrementAndGet();
            if (failOnCall > 0 && n == failOnCall) {
                throw new RuntimeException("controlled warehouse draft failure");
            }
            createDraftCalls.add(command);
            return new TransferRequestView(
                    UUID.randomUUID(),
                    "DRAFT",
                    command.materialReferenceId(),
                    command.quantity(),
                    command.sourceWarehouseId(),
                    command.sourceStorageCellId(),
                    command.destinationWarehouseId(),
                    command.destinationStorageCellId());
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.OperationResult sendTransfer(
                UUID transferDraftOperationId) {
            sendCalls++;
            throw new UnsupportedOperationException("sendTransfer must not be called");
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.OperationResult receiveTransfer(
                UUID sendOperationId) {
            receiveCalls++;
            throw new UnsupportedOperationException("receiveTransfer must not be called");
        }

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
        public com.tmp.warehouse.api.WarehouseApi.OperationResult executeWarehouseOperation(
                com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.OperationResult receive(
                com.tmp.warehouse.api.WarehouseApi.ReceiptCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.OperationResult consume(
                com.tmp.warehouse.api.WarehouseApi.ConsumptionCommand command) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubWarehouseQueryApi implements WarehouseQueryApi {
        private List<StorageCellView> sourceCells = List.of();
        private List<StorageCellView> destinationCells = List.of();

        @Override
        public List<StorageCellView> listStorageCells(UUID warehouseId) {
            if (MAIN.equals(warehouseId)) {
                return sourceCells;
            }
            if (PROD.equals(warehouseId)) {
                return destinationCells;
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
        public List<com.tmp.warehouse.api.WarehouseApi.StockView> getStock(String materialCode) {
            return List.of();
        }

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.StockView> getStock(
                String materialCode, UUID warehouseId, UUID storageCellId) {
            return List.of();
        }

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.StockView> getStockByWarehouse(
                UUID warehouseId) {
            return List.of();
        }

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.StockView> getStockByMaterialReferenceId(
                UUID materialReferenceId) {
            return List.of();
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
