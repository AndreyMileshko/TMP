package com.tmp.warehouse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
import com.tmp.warehouse.api.WarehouseApiTestFixtures;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Public API contracts and DTO invariants (STAGE6-013).
 */
class WarehouseApiContractTest {

    @Test
    void stockViewRejectsNullFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        assertThrows(
                NullPointerException.class,
                () ->
                        new StockView(
                                null,
                                "Name",
                                "",
                                "",
                                "",
                                "WH",
                                "A-01",
                                BigDecimal.ONE,
                                StockStateView.AVAILABLE,
                                "MAT-1",
                                warehouseId,
                                cellId));
        assertThrows(
                NullPointerException.class,
                () ->
                        new StockView(
                                "MAT-1",
                                "Name",
                                "",
                                "",
                                "",
                                "WH",
                                "A-01",
                                BigDecimal.ONE,
                                null,
                                "MAT-1",
                                warehouseId,
                                cellId));
    }

    @Test
    void stockViewExposesExtendedMaterialFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        StockView view =
                WarehouseApiTestFixtures.stockView(
                        "VEKA-103.211",
                        "Профиль VEKA Softline",
                        "Белый",
                        "6000 мм",
                        "шт",
                        "WH-1 — Main",
                        "A-01",
                        BigDecimal.valueOf(100),
                        warehouseId,
                        cellId);
        assertEquals("VEKA-103.211", view.article());
        assertEquals("VEKA-103.211", view.materialCode());
        assertEquals("Профиль VEKA Softline", view.materialName());
        assertEquals("Белый", view.color());
        assertEquals("6000 мм", view.size());
        assertEquals("шт", view.unitOfMeasure());
        assertEquals("WH-1 — Main", view.warehouse());
        assertEquals("A-01", view.storageCell());
    }

    @Test
    void materialReferenceDisplayViewExposesFields() {
        MaterialReferenceDisplayView view =
                new MaterialReferenceDisplayView(
                        "VEKA-103.211",
                        "Профиль VEKA Softline",
                        "Белый",
                        "6000 мм",
                        "шт");
        assertEquals("VEKA-103.211", view.article());
        assertEquals("Профиль VEKA Softline", view.materialName());
        assertEquals("Белый", view.color());
        assertEquals("6000 мм", view.size());
        assertEquals("шт", view.unitOfMeasure());
    }

    @Test
    void availabilityResultExposesAvailableFlag() {
        AvailabilityResult available =
                new AvailabilityResult(
                        AvailabilityStatus.AVAILABLE,
                        "MAT-1",
                        BigDecimal.TEN,
                        BigDecimal.valueOf(20));
        assertTrue(available.isAvailable());

        AvailabilityResult insufficient =
                new AvailabilityResult(
                        AvailabilityStatus.INSUFFICIENT,
                        "MAT-1",
                        BigDecimal.TEN,
                        BigDecimal.valueOf(3));
        assertFalse(insufficient.isAvailable());
        assertEquals(AvailabilityStatus.INSUFFICIENT, insufficient.status());
    }

    @Test
    void reservationCommandAndViewRejectNulls() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new CreateReservationLinkCommand(
                                null,
                                ReservationTargetTypeView.ORDER,
                                "26096190",
                                BigDecimal.ONE));
        assertThrows(
                NullPointerException.class,
                () ->
                        new WarehouseApi.ReservationLinkView(
                                UUID.randomUUID(),
                                "MAT-1",
                                ReservationTargetTypeView.ORDER,
                                "26096190",
                                BigDecimal.ONE,
                                null));
    }

    @Test
    void executeOperationFactoriesSetKindAndRequiredFields() {
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        UUID destinationWarehouse = UUID.randomUUID();
        UUID destinationCell = UUID.randomUUID();

        ExecuteOperationCommand receipt =
                ExecuteOperationCommand.receipt("ALU-6060", BigDecimal.TEN, warehouseId, cellId);
        assertEquals(OperationKind.RECEIPT, receipt.kind());
        assertEquals(warehouseId, receipt.warehouseId());

        ExecuteOperationCommand move =
                ExecuteOperationCommand.move(
                        "ALU-6060",
                        BigDecimal.ONE,
                        warehouseId,
                        cellId,
                        destinationWarehouse,
                        destinationCell);
        assertEquals(OperationKind.MOVE, move.kind());
        assertEquals(destinationCell, move.destinationStorageCellId());

        ExecuteOperationCommand transferSend =
                ExecuteOperationCommand.transferSend(
                        "ALU-6060", BigDecimal.ONE, warehouseId, cellId, destinationWarehouse);
        assertEquals(OperationKind.TRANSFER_SEND, transferSend.kind());

        ExecuteOperationCommand transferReceive =
                ExecuteOperationCommand.transferReceive(
                        "ALU-6060",
                        BigDecimal.ONE,
                        warehouseId,
                        cellId,
                        destinationWarehouse,
                        destinationCell);
        assertEquals(OperationKind.TRANSFER_RECEIVE, transferReceive.kind());

        ExecuteOperationCommand consumption =
                ExecuteOperationCommand.consumption(
                        "ALU-6060", BigDecimal.ONE, warehouseId, cellId);
        assertEquals(OperationKind.CONSUMPTION, consumption.kind());

        ExecuteOperationCommand adjustment =
                ExecuteOperationCommand.adjustment(
                        "ALU-6060", BigDecimal.valueOf(-2), warehouseId, cellId);
        assertEquals(OperationKind.ADJUSTMENT, adjustment.kind());
        assertEquals(0, adjustment.quantity().compareTo(BigDecimal.valueOf(-2)));
    }

    @Test
    void operationResultHoldsPublicFieldsOnly() {
        UUID operationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        WarehouseApi.OperationResult result =
                new WarehouseApi.OperationResult(
                        operationId,
                        OperationKind.RECEIPT,
                        "COMPLETED",
                        "ALU-6060",
                        warehouseId,
                        cellId,
                        BigDecimal.TEN);
        assertEquals(operationId, result.operationId());
        assertEquals("COMPLETED", result.status());
        assertEquals(OperationKind.RECEIPT, result.kind());
    }
}
