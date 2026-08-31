package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionApplicationApi.WarehouseTransferRefView;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.TransferStatusView;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferReceiptEligibilityTest {

    @Test
    void receivableWhenSentNotWhenDraftOrAllReceived() {
        UUID draftId = UUID.randomUUID();
        UUID sentId = UUID.randomUUID();
        List<WarehouseTransferRefView> refs =
                List.of(
                        new WarehouseTransferRefView(draftId, UUID.randomUUID(), BigDecimal.ONE),
                        new WarehouseTransferRefView(sentId, UUID.randomUUID(), BigDecimal.ONE));
        StubQuery query = new StubQuery();
        query.statuses.put(draftId, "DRAFT");
        query.statuses.put(sentId, "SENT");
        assertFalse(TransferReceiptEligibility.isReceivable(refs, query));

        query.statuses.put(draftId, "SENT");
        assertTrue(TransferReceiptEligibility.isReceivable(refs, query));

        query.statuses.put(draftId, "RECEIVED");
        query.statuses.put(sentId, "RECEIVED");
        assertFalse(TransferReceiptEligibility.isReceivable(refs, query));
    }

    private static final class StubQuery implements WarehouseQueryApi {
        private final Map<UUID, String> statuses = new java.util.HashMap<>();

        @Override
        public TransferStatusView getTransferStatus(UUID operationId) {
            return new TransferStatusView(
                    operationId,
                    OperationKind.TRANSFER_SEND,
                    statuses.get(operationId),
                    UUID.randomUUID(),
                    BigDecimal.ONE,
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    null);
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

        @Override
        public List<com.tmp.warehouse.api.WarehouseApi.TransferRequestView> listTransferDrafts() {
            return List.of();
        }
    }
}
