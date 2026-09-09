package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferDocumentSettlementRejectReturnTest {

    private static final Instant T0 = Instant.parse("2026-09-09T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-09-09T10:01:00Z");
    private static final Instant T2 = Instant.parse("2026-09-09T10:02:00Z");

    @Test
    void markRejectedAndReturnPendingStoresMetadata() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TransferDocumentSettlement awaiting =
                TransferDocumentSettlement.awaitingReceipt(documentId, T0);

        TransferDocumentSettlement rejected =
                awaiting.markRejectedAndReturnPending(0L, "  Повреждение  ", T1, userId);

        assertEquals(TransferSettlementState.RETURN_PENDING, rejected.settlementState());
        assertEquals(TransferSettlementDecision.REJECTED, rejected.decision().orElseThrow());
        assertEquals(1L, rejected.operationalRevision());
        assertEquals("Повреждение", rejected.rejectionReason().orElseThrow());
        assertEquals(T1, rejected.rejectedAt().orElseThrow());
        assertEquals(userId, rejected.rejectedBy().orElseThrow());
    }

    @Test
    void blankRejectReasonRejected() {
        TransferDocumentSettlement awaiting =
                TransferDocumentSettlement.awaitingReceipt(UUID.randomUUID(), T0);
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> awaiting.markRejectedAndReturnPending(0L, "   ", T1, UUID.randomUUID()));
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> awaiting.markRejectedAndReturnPending(0L, null, T1, UUID.randomUUID()));
    }

    @Test
    void markReturnedAndSettledPreservesRejectionMetadata() {
        UUID documentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TransferDocumentSettlement rejected =
                TransferDocumentSettlement.awaitingReceipt(documentId, T0)
                        .markRejectedAndReturnPending(0L, "reason", T1, userId);

        TransferDocumentSettlement settled = rejected.markReturnedAndSettled(1L, T2);

        assertEquals(TransferSettlementState.SETTLED, settled.settlementState());
        assertEquals(TransferSettlementDecision.REJECTED, settled.decision().orElseThrow());
        assertEquals(2L, settled.operationalRevision());
        assertEquals("reason", settled.rejectionReason().orElseThrow());
        assertEquals(T1, settled.rejectedAt().orElseThrow());
        assertEquals(userId, settled.rejectedBy().orElseThrow());
    }

    @Test
    void markReturnedAndSettledPreservesAcceptedDecision() {
        TransferDocumentSettlement pending =
                TransferDocumentSettlement.awaitingReceipt(UUID.randomUUID(), T0)
                        .markAcceptedAndReturnPending(0L, T1);

        TransferDocumentSettlement settled = pending.markReturnedAndSettled(1L, T2);

        assertEquals(TransferSettlementState.SETTLED, settled.settlementState());
        assertEquals(TransferSettlementDecision.ACCEPTED, settled.decision().orElseThrow());
        assertTrue(settled.rejectionReason().isEmpty());
        assertEquals(2L, settled.operationalRevision());
    }

    @Test
    void rejectRequiresAwaitingReceipt() {
        TransferDocumentSettlement pending =
                TransferDocumentSettlement.awaitingReceipt(UUID.randomUUID(), T0)
                        .markAcceptedAndReturnPending(0L, T1);
        assertThrows(
                InvalidWarehouseStateException.class,
                () -> pending.markRejectedAndReturnPending(1L, "x", T2, UUID.randomUUID()));
    }
}
