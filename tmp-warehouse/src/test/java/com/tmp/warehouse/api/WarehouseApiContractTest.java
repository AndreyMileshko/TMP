package com.tmp.warehouse.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.AvailabilityResult;
import com.tmp.warehouse.api.WarehouseApi.AvailabilityStatus;
import com.tmp.warehouse.api.WarehouseApi.CreateReservationLinkCommand;
import com.tmp.warehouse.api.WarehouseApi.ExecuteOperationCommand;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceDisplayView;
import com.tmp.warehouse.api.WarehouseApi.MaterialReferenceView;
import com.tmp.warehouse.api.WarehouseApi.OperationKind;
import com.tmp.warehouse.api.WarehouseApi.ReservationTargetTypeView;
import com.tmp.warehouse.api.WarehouseApi.StockStateView;
import com.tmp.warehouse.api.WarehouseApi.StockView;
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
        UUID materialReferenceId = UUID.randomUUID();
        assertThrows(
                NullPointerException.class,
                () ->
                        new StockView(
                                materialReferenceId,
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
                                materialReferenceId,
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
                        "шт.",
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
        assertEquals("шт.", view.unitOfMeasure());
        assertEquals("WH-1 — Main", view.warehouse());
        assertEquals("A-01", view.storageCell());
        assertTrue(view.materialReferenceId() != null);
    }

    @Test
    void materialReferenceViewExposesFields() {
        UUID id = UUID.randomUUID();
        MaterialReferenceView view =
                new MaterialReferenceView(
                        id,
                        "VEKA-103.211",
                        "Профиль VEKA Softline",
                        "Белый",
                        "6000 мм",
                        "шт.");
        assertEquals(id, view.materialReferenceId());
        assertEquals("VEKA-103.211", view.article());
        assertEquals("Профиль VEKA Softline", view.name());
    }

    @Test
    void materialReferenceDisplayViewExposesFields() {
        MaterialReferenceDisplayView view =
                new MaterialReferenceDisplayView(
                        "VEKA-103.211",
                        "Профиль VEKA Softline",
                        "Белый",
                        "6000 мм",
                        "шт.");
        assertEquals("VEKA-103.211", view.article());
        assertEquals("Профиль VEKA Softline", view.materialName());
        assertEquals("Белый", view.color());
        assertEquals("6000 мм", view.size());
        assertEquals("шт.", view.unitOfMeasure());
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
        UUID materialReferenceId = UUID.randomUUID();

        ExecuteOperationCommand receipt =
                ExecuteOperationCommand.receipt(
                        "ALU-6060",
                        "ALU-6060",
                        "",
                        "",
                        "шт.",
                        BigDecimal.TEN,
                        warehouseId,
                        cellId);
        assertEquals(OperationKind.RECEIPT, receipt.kind());
        assertEquals(warehouseId, receipt.warehouseId());

        ExecuteOperationCommand move =
                ExecuteOperationCommand.move(
                        materialReferenceId,
                        BigDecimal.ONE,
                        warehouseId,
                        cellId,
                        destinationWarehouse,
                        destinationCell);
        assertEquals(OperationKind.MOVE, move.kind());
        assertEquals(destinationCell, move.destinationStorageCellId());

        ExecuteOperationCommand transferSend =
                ExecuteOperationCommand.transferSend(
                        materialReferenceId,
                        BigDecimal.ONE,
                        warehouseId,
                        cellId,
                        destinationWarehouse);
        assertEquals(OperationKind.TRANSFER_SEND, transferSend.kind());

        ExecuteOperationCommand transferReceive =
                ExecuteOperationCommand.transferReceive(
                        materialReferenceId,
                        BigDecimal.ONE,
                        warehouseId,
                        cellId,
                        destinationWarehouse,
                        destinationCell);
        assertEquals(OperationKind.TRANSFER_RECEIVE, transferReceive.kind());

        ExecuteOperationCommand consumption =
                ExecuteOperationCommand.consumption(
                        materialReferenceId, BigDecimal.ONE, warehouseId, cellId);
        assertEquals(OperationKind.CONSUMPTION, consumption.kind());

        ExecuteOperationCommand adjustment =
                ExecuteOperationCommand.adjustment(
                        materialReferenceId, BigDecimal.valueOf(-2), warehouseId, cellId);
        assertEquals(OperationKind.ADJUSTMENT, adjustment.kind());
        assertEquals(0, adjustment.quantity().compareTo(BigDecimal.valueOf(-2)));
    }

    @Test
    void operationResultHoldsPublicFieldsOnly() {
        UUID operationId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        UUID materialReferenceId = UUID.randomUUID();
        WarehouseApi.OperationResult result =
                new WarehouseApi.OperationResult(
                        operationId,
                        OperationKind.RECEIPT,
                        "COMPLETED",
                        materialReferenceId,
                        "ALU-6060",
                        warehouseId,
                        cellId,
                        BigDecimal.TEN);
        assertEquals(operationId, result.operationId());
        assertEquals("COMPLETED", result.status());
        assertEquals(OperationKind.RECEIPT, result.kind());
        assertEquals(materialReferenceId, result.materialReferenceId());
    }
}
