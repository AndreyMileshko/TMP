package com.tmp.order.domain;

import com.tmp.order.testsupport.IntakeContractFixtures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderItemRevisionTest {

    @Test
    void createDraftStartsAtDraftStatus() {
        OrderItemRevision revision = OrderItemRevision.createDraft(
                OrderItemId.generate(),
                RevisionNumber.first(),
                OrderedQuantity.of(2),
                null);
        assertTrue(revision.isDraft());
        assertEquals(RevisionStatus.DRAFT, revision.status());
        assertTrue(revision.previousRevisionNumber().isEmpty());
    }

    @Test
    void approveMakesImmutable() {
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber number = RevisionNumber.first();
        ItemSpecification spec = ItemSpecification.of(
                itemId,
                number,
                java.util.List.of(
                        IntakeContractFixtures.specLine("M1", "Material", BigDecimal.TEN, "pcs")));
        OrderItemRevision approved = OrderItemRevision.createDraft(
                        itemId, number, OrderedQuantity.of(1), null)
                .withSpecification(spec)
                .approved();
        assertTrue(approved.isApproved());
        assertTrue(approved.specification().orElseThrow().isImmutable());
        assertThrows(
                InvalidOrderStateException.class,
                () -> approved.withOrderedQuantity(OrderedQuantity.of(5)));
        assertThrows(InvalidOrderStateException.class, approved::approved);
    }

    @Test
    void draftRevisionAllowsOrderedQuantityChange() {
        OrderItemRevision revision =
                OrderItemRevision.createDraft(
                        OrderItemId.generate(), RevisionNumber.first(), OrderedQuantity.of(2), null);

        OrderItemRevision updated = revision.withOrderedQuantity(OrderedQuantity.of(5));

        assertTrue(updated.isDraft());
        assertEquals(OrderedQuantity.of(5), updated.orderedQuantity());
    }

    @Test
    void draftRevisionAllowsSpecificationAttachment() {
        OrderItemId itemId = OrderItemId.generate();
        RevisionNumber number = RevisionNumber.first();

        OrderItemRevision revision =
                OrderItemRevision.createDraft(
                        itemId, number, OrderedQuantity.of(2), null);

        ItemSpecification spec =
                ItemSpecification.of(
                        itemId,
                        number,
                        java.util.List.of(
                                IntakeContractFixtures.specLine("M1", "Material", BigDecimal.TEN, "pcs")));

        OrderItemRevision updated = revision.withSpecification(spec);

        assertTrue(updated.isDraft());
        assertTrue(updated.specification().isPresent());
        assertEquals("M1", updated.specification().orElseThrow().lines().get(0).materialCode());
    }

    @Test
    void orderedQuantityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> OrderedQuantity.of(0));
        assertThrows(IllegalArgumentException.class, () -> OrderedQuantity.of(-1));
    }
}
