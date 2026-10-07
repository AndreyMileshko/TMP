package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.warehouse.api.WarehouseApi.PrepareDemandTransferLineResult;
import com.tmp.warehouse.api.WarehouseApi.PrepareProductionDemandTransfersResult;
import com.tmp.warehouse.api.WarehouseApi.PreparedTransferDocument;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseSupplyPrepareResultPresentationTest {

    @Test
    void allRoutedSingleTransfer() {
        PrepareProductionDemandTransfersResult result =
                new PrepareProductionDemandTransfersResult(
                        UUID.randomUUID(),
                        List.of(),
                        List.of(doc()),
                        1,
                        1,
                        0);
        assertEquals(
                WarehouseSupplyPrepareResultPresentation.SINGLE_TRANSFER,
                WarehouseSupplyPrepareResultPresentation.messageFor(result));
        assertTrue(WarehouseSupplyPrepareResultPresentation.isTerminalSuccess(result));
    }

    @Test
    void allRoutedMultipleTransfers() {
        PrepareProductionDemandTransfersResult result =
                new PrepareProductionDemandTransfersResult(
                        UUID.randomUUID(),
                        List.of(),
                        List.of(doc(), doc()),
                        2,
                        2,
                        0);
        assertEquals(
                "Создано перемещений: 2.",
                WarehouseSupplyPrepareResultPresentation.messageFor(result));
        assertTrue(WarehouseSupplyPrepareResultPresentation.isTerminalSuccess(result));
    }

    @Test
    void partialPrepare() {
        PrepareProductionDemandTransfersResult result =
                new PrepareProductionDemandTransfersResult(
                        UUID.randomUUID(),
                        List.<PrepareDemandTransferLineResult>of(),
                        List.of(doc()),
                        1,
                        1,
                        2);
        assertEquals(
                "Перемещение создано.\n"
                        + WarehouseSupplyPrepareResultPresentation.PARTIAL_SUFFIX,
                WarehouseSupplyPrepareResultPresentation.messageFor(result));
        assertTrue(WarehouseSupplyPrepareResultPresentation.isPartial(result));
    }

    @Test
    void nothingRoutable() {
        PrepareProductionDemandTransfersResult result =
                new PrepareProductionDemandTransfersResult(
                        UUID.randomUUID(), List.of(), List.of(), 0, 0, 3);
        assertEquals(
                WarehouseSupplyPrepareResultPresentation.NOTHING_ROUTABLE,
                WarehouseSupplyPrepareResultPresentation.messageFor(result));
        assertTrue(WarehouseSupplyPrepareResultPresentation.isNothingRoutable(result));
    }

    @Test
    void staleEmpty() {
        PrepareProductionDemandTransfersResult result =
                new PrepareProductionDemandTransfersResult(
                        UUID.randomUUID(), List.of(), List.of(), 0, 0, 0);
        assertEquals(
                WarehouseSupplyPrepareResultPresentation.STALE_TASK,
                WarehouseSupplyPrepareResultPresentation.messageFor(result));
        assertTrue(WarehouseSupplyPrepareResultPresentation.isStaleOrEmpty(result));
        assertFalse(WarehouseSupplyPrepareResultPresentation.isTerminalSuccess(result));
    }

    private static PreparedTransferDocument doc() {
        return new PreparedTransferDocument(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }
}
