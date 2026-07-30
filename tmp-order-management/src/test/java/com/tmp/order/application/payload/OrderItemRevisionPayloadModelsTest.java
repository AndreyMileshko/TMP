package com.tmp.order.application.payload;

import com.tmp.order.testsupport.IntakeContractFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.PayloadRevision;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderItemRevisionPayloadModelsTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    @Test
    void createPayloadCarriesOrderItemIdAndRequiredRevisionNumber() {
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.of(2);
        OrderItemRevisionCreatePayload payload =
                OrderItemRevisionCreatePayload.create(
                        DocumentId.generate(),
                        itemId,
                        revision,
                        RevisionNumber.first(),
                        NOW);

        assertEquals(DocumentTypeCode.ORDER_ITEM_REVISION_CREATE, payload.documentTypeCode());
        assertEquals(itemId, payload.orderItemId());
        assertEquals(revision, payload.revisionNumber());
        assertEquals(RevisionNumber.first(), payload.copyFromRevisionNumber());
    }

    @Test
    void updatePayloadRejectsApprovedRevisionTarget() {
        PayloadIdentity identity =
                PayloadIdentity.initialDraft(
                        DocumentId.generate(), DocumentTypeCode.ORDER_ITEM_REVISION_UPDATE, NOW);
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemRevisionUpdatePayload.rehydrate(
                        identity,
                        OrderItemId.generate(),
                        RevisionNumber.first(),
                        RevisionStatus.APPROVED,
                        OrderedQuantity.of(1),
                        List.of()));
    }

    @Test
    void updatePayloadProtectsLinesCollectionAndValidatesQuantities() {
        OrderItemRevisionPayloadLine line =
                IntakeContractFixtures.payloadLine(1, "M-1", "Material", BigDecimal.ONE, "pcs");
        List<OrderItemRevisionPayloadLine> mutable = new ArrayList<>();
        mutable.add(line);

        OrderItemRevisionUpdatePayload payload =
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.generate(),
                        OrderItemId.generate(),
                        RevisionNumber.first(),
                        OrderedQuantity.of(5),
                        mutable,
                        NOW);

        mutable.clear();
        assertEquals(1, payload.lines().size());
        assertThrows(UnsupportedOperationException.class, () -> payload.lines().clear());
        assertEquals(RevisionStatus.DRAFT, payload.targetRevisionStatus());

        assertThrows(
                IllegalArgumentException.class,
                () -> IntakeContractFixtures.payloadLine(1, "M-1", "Material", BigDecimal.ZERO, "pcs"));
        assertThrows(
                IllegalArgumentException.class,
                () -> IntakeContractFixtures.payloadLine(1, "M-1", "Material", BigDecimal.ONE, " "));
    }

    @Test
    void approvePayloadCarriesOrderItemIdAndRevisionNumber() {
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber revision = RevisionNumber.first();
        OrderItemRevisionApprovePayload payload =
                OrderItemRevisionApprovePayload.create(
                        DocumentId.generate(), itemId, revision, NOW);
        assertEquals(DocumentTypeCode.ORDER_ITEM_REVISION_APPROVE, payload.documentTypeCode());
        assertEquals(itemId, payload.orderItemId());
        assertEquals(revision, payload.revisionNumber());
    }

    @Test
    void contentUpdateIncrementsPayloadRevision() {
        OrderItemRevisionUpdatePayload original =
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.generate(),
                        OrderItemId.generate(),
                        RevisionNumber.first(),
                        OrderedQuantity.of(1),
                        List.of(),
                        NOW);
        OrderItemRevisionUpdatePayload next =
                original.withContent(
                        OrderedQuantity.of(2),
                        List.of(
                                IntakeContractFixtures.payloadLine(1, "M-2", "Board", BigDecimal.TEN, "m2")),
                        NOW.plusSeconds(30));
        assertEquals(PayloadRevision.initial(), original.identity().payloadRevision());
        assertEquals(PayloadRevision.of(1L), next.identity().payloadRevision());
        assertEquals(1, next.lines().size());
        assertTrue(original.lines().isEmpty());
    }

    @Test
    void createRejectsNonIncreasingCopyFromRevision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemRevisionCreatePayload.create(
                        DocumentId.generate(),
                        OrderItemId.generate(),
                        RevisionNumber.first(),
                        RevisionNumber.of(2),
                        NOW));
    }
}
