package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.application.MaterialReceiptConfirmationResult.MaterialReceiptConfirmationStatus;
import com.tmp.production.domain.MaterialReceiptConfirmationException;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.ProductionMaterialTransferNotFoundException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.OperationResult;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.SimpleTransactionStatus;

class ConfirmMaterialReceiptServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-21T15:00:00Z");
    private static final UUID SOURCE_CELL = UUID.fromString("00000000-0000-4000-8000-000000000301");
    private static final UUID DEST_CELL = UUID.fromString("00000000-0000-4000-8000-000000000302");
    private static final UUID DEST_WAREHOUSE =
            UUID.fromString("00000000-0000-4000-8000-000000000102");

    private InMemoryTransferRepository transfers;
    private RecordingWarehouse warehouse;
    private ConfirmMaterialReceiptService service;

    @BeforeEach
    void setUp() {
        transfers = new InMemoryTransferRepository();
        warehouse = new RecordingWarehouse();
        service =
                new ConfirmMaterialReceiptService(
                        transfers,
                        warehouse,
                        warehouse,
                        new PassthroughTransactionManager(),
                        Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void notFoundDoesNotCallWarehouse() {
        ProductionMaterialTransferId missing = ProductionMaterialTransferId.generate();
        assertThrows(
                ProductionMaterialTransferNotFoundException.class,
                () ->
                        service.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(missing)));
        assertEquals(0, warehouse.statusCalls.size());
        assertEquals(0, warehouse.receiveCalls.size());
    }

    @Test
    void draftBlocksConfirmationBeforeAnyReceive() {
        ProductionMaterialTransfer transfer = persistTransfer(ref("DRAFT"));
        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        service.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));
        assertEquals(1, warehouse.statusCalls.size());
        assertEquals(0, warehouse.receiveCalls.size());
    }

    @Test
    void sentReceivesAndReturnsReceived() {
        WarehouseTransferOperationRef sentRef = ref("SENT");
        ProductionMaterialTransfer transfer = persistTransfer(sentRef);

        MaterialReceiptConfirmationResult result =
                service.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(MaterialReceiptConfirmationStatus.RECEIVED, result.status());
        assertEquals(T0, result.confirmedAt());
        assertEquals(1, warehouse.receiveCalls.size());
        assertEquals(sentRef.warehouseDraftOperationId(), warehouse.receiveCalls.getFirst());
        assertEquals(STATUS_RECEIVED, result.references().getFirst().resultingStatus());
        assertEquals(
                warehouse.lastReceiveOperationId,
                result.references().getFirst().receiveOperationId());
    }

    @Test
    void allReceivedIsIdempotentWithoutReceiveCalls() {
        WarehouseTransferOperationRef a = ref("RECEIVED");
        WarehouseTransferOperationRef b = ref("RECEIVED");
        ProductionMaterialTransfer transfer = persistTransfer(a, b);

        MaterialReceiptConfirmationResult result =
                service.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(MaterialReceiptConfirmationStatus.ALREADY_RECEIVED, result.status());
        assertEquals(0, warehouse.receiveCalls.size());
        assertEquals(2, result.references().size());
        assertEquals(T0, result.confirmedAt());
    }

    @Test
    void mixedSentAndReceivedReceivesOnlySent() {
        WarehouseTransferOperationRef received = ref("RECEIVED");
        WarehouseTransferOperationRef sent = ref("SENT");
        ProductionMaterialTransfer transfer = persistTransfer(received, sent);

        MaterialReceiptConfirmationResult result =
                service.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(MaterialReceiptConfirmationStatus.RECEIVED, result.status());
        assertEquals(1, warehouse.receiveCalls.size());
        assertEquals(sent.warehouseDraftOperationId(), warehouse.receiveCalls.getFirst());
        assertEquals(2, result.references().size());
        assertTrue(
                result.references().stream()
                        .allMatch(r -> STATUS_RECEIVED.equals(r.resultingStatus())));
    }

    @Test
    void multiRefReceivesAllSent() {
        ProductionMaterialTransfer transfer =
                persistTransfer(ref("SENT"), ref("SENT"), ref("SENT"));

        MaterialReceiptConfirmationResult result =
                service.confirmMaterialReceipt(
                        new ConfirmMaterialReceiptCommand(transfer.logicalTransferId()));

        assertEquals(3, warehouse.receiveCalls.size());
        assertEquals(MaterialReceiptConfirmationStatus.RECEIVED, result.status());
        assertEquals(3, result.references().size());
    }

    @Test
    void prevalidationFailsBeforeAnyReceiveWhenAnyDraftPresent() {
        ProductionMaterialTransfer transfer =
                persistTransfer(ref("SENT"), ref("DRAFT"), ref("SENT"));

        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        service.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));
        assertEquals(3, warehouse.statusCalls.size());
        assertEquals(0, warehouse.receiveCalls.size());
    }

    @Test
    void invalidStatusRejectedBeforeReceive() {
        ProductionMaterialTransfer transfer = persistTransfer(ref("FAILED"));
        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        service.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));
        assertEquals(0, warehouse.receiveCalls.size());
    }

    @Test
    void materialMismatchFailsBeforeReceive() {
        WarehouseTransferOperationRef stored = ref("SENT");
        warehouse.overrideMaterial.put(
                stored.warehouseDraftOperationId(), MaterialReferenceId.generate().value());
        ProductionMaterialTransfer transfer = persistTransfer(stored);

        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        service.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));
        assertEquals(0, warehouse.receiveCalls.size());
    }

    @Test
    void quantityMismatchFailsBeforeReceive() {
        WarehouseTransferOperationRef stored = ref("SENT");
        warehouse.overrideQuantity.put(stored.warehouseDraftOperationId(), BigDecimal.valueOf(6));
        ProductionMaterialTransfer transfer = persistTransfer(stored);

        assertThrows(
                MaterialReceiptConfirmationException.class,
                () ->
                        service.confirmMaterialReceipt(
                                new ConfirmMaterialReceiptCommand(transfer.logicalTransferId())));
        assertEquals(0, warehouse.receiveCalls.size());
    }

    @Test
    void commandAcceptsOnlyLogicalTransferId() {
        ConfirmMaterialReceiptCommand command =
                new ConfirmMaterialReceiptCommand(ProductionMaterialTransferId.generate());
        assertEquals(command.logicalTransferId(), command.logicalTransferId());
    }

    private ProductionMaterialTransfer persistTransfer(WarehouseTransferOperationRef... refs) {
        ProductionMaterialTransfer transfer =
                ProductionMaterialTransfer.create(
                        MaterialTransferTemplateId.generate(),
                        SourceOrderId.generate(),
                        T0,
                        List.of(refs));
        return transfers.save(transfer);
    }

    private WarehouseTransferOperationRef ref(String status) {
        UUID operationId = UUID.randomUUID();
        MaterialReferenceId material = MaterialReferenceId.generate();
        BigDecimal quantity = BigDecimal.valueOf(5);
        warehouse.seed(operationId, status, material.value(), quantity, SOURCE_CELL, DEST_CELL);
        return new WarehouseTransferOperationRef(
                MaterialTransferTemplateLineId.generate(),
                operationId,
                material,
                quantity,
                SOURCE_CELL,
                DEST_CELL);
    }

    private static final String STATUS_RECEIVED = "RECEIVED";

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

    private static final class RecordingWarehouse implements WarehouseCommandApi, WarehouseQueryApi {
        private final Map<UUID, TransferStatusView> statuses = new HashMap<>();
        private final Map<UUID, UUID> receiveIds = new HashMap<>();
        private final Map<UUID, UUID> overrideMaterial = new HashMap<>();
        private final Map<UUID, BigDecimal> overrideQuantity = new HashMap<>();
        private final List<UUID> statusCalls = new ArrayList<>();
        private final List<UUID> receiveCalls = new ArrayList<>();
        private UUID lastReceiveOperationId;
        private final AtomicInteger receiveSeq = new AtomicInteger();

        void seed(
                UUID operationId,
                String status,
                UUID materialId,
                BigDecimal quantity,
                UUID sourceCell,
                UUID destCell) {
            UUID receiveId =
                    "RECEIVED".equals(status) ? UUID.randomUUID() : null;
            if (receiveId != null) {
                receiveIds.put(operationId, receiveId);
            }
            statuses.put(
                    operationId,
                    new TransferStatusView(
                            operationId,
                            OperationKind.TRANSFER_SEND,
                            status,
                            materialId,
                            quantity,
                            UUID.randomUUID(),
                            sourceCell,
                            DEST_WAREHOUSE,
                            destCell,
                            receiveId));
        }

        @Override
        public TransferStatusView getTransferStatus(UUID operationId) {
            statusCalls.add(operationId);
            TransferStatusView base = statuses.get(operationId);
            if (base == null) {
                throw new IllegalArgumentException("unknown op " + operationId);
            }
            UUID material =
                    overrideMaterial.getOrDefault(operationId, base.materialReferenceId());
            BigDecimal quantity = overrideQuantity.getOrDefault(operationId, base.quantity());
            return new TransferStatusView(
                    base.operationId(),
                    base.kind(),
                    base.status(),
                    material,
                    quantity,
                    base.warehouseId(),
                    base.storageCellId(),
                    base.destinationWarehouseId(),
                    base.destinationStorageCellId(),
                    base.receiveOperationId());
        }

        @Override
        public OperationResult receiveTransfer(UUID sendOperationId) {
            receiveCalls.add(sendOperationId);
            TransferStatusView before = statuses.get(sendOperationId);
            if (before == null || !"SENT".equals(before.status())) {
                throw new IllegalStateException("not SENT: " + sendOperationId);
            }
            UUID receiveId = UUID.randomUUID();
            receiveSeq.incrementAndGet();
            lastReceiveOperationId = receiveId;
            receiveIds.put(sendOperationId, receiveId);
            statuses.put(
                    sendOperationId,
                    new TransferStatusView(
                            before.operationId(),
                            before.kind(),
                            "RECEIVED",
                            before.materialReferenceId(),
                            before.quantity(),
                            before.warehouseId(),
                            before.storageCellId(),
                            before.destinationWarehouseId(),
                            before.destinationStorageCellId(),
                            receiveId));
            return new OperationResult(
                    receiveId,
                    OperationKind.TRANSFER_RECEIVE,
                    "COMPLETED",
                    before.materialReferenceId(),
                    "MAT",
                    before.destinationWarehouseId(),
                    before.destinationStorageCellId(),
                    before.quantity());
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.WarehouseView createWarehouse(
                com.tmp.warehouse.api.WarehouseApi.CreateWarehouseCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.tmp.warehouse.api.WarehouseApi.StorageCellView createStorageCell(
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
        public OperationResult consume(
                com.tmp.warehouse.api.WarehouseApi.ConsumptionCommand command) {
            throw new UnsupportedOperationException();
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
        public List<com.tmp.warehouse.api.WarehouseApi.WarehouseView> listWarehouses() {
            return List.of();
        }

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.StorageCellView> listStorageCells(
                UUID warehouseId) {
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
