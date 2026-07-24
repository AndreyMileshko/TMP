package com.tmp.order.domain;

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
                        SpecificationLine.of(
                                "M1", "Material", BigDecimal.TEN, "pcs", BigDecimal.ONE)));
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
    void orderedQuantityMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> OrderedQuantity.of(0));
        assertThrows(IllegalArgumentException.class, () -> OrderedQuantity.of(-1));
    }
}
