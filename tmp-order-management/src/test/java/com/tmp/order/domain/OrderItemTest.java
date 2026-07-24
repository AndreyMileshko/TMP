package com.tmp.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class OrderItemTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T09:00:00Z"), ZoneOffset.UTC);

    @Test
    void createStartsInDraftWithRevisionOne() {
        OrderItem item = sampleItem();
        assertEquals(OrderItemStatus.DRAFT, item.status());
        assertEquals(1, item.draftRevisionNumber().orElseThrow().value());
        assertTrue(item.activeRevisionNumber().isEmpty());
        assertEquals(RevisionStatus.DRAFT, item.draftRevision().orElseThrow().status());
    }

    @Test
    void cancelFromDraftSucceeds() {
        assertEquals(OrderItemStatus.CANCELLED, sampleItem().cancel(CLOCK).status());
    }

    @Test
    void updateCommercialDataAllowedInDraft() {
        ItemCommercialData updated =
                ItemCommercialData.of(ProductCode.of("P-2"), "Door B", "note");
        OrderItem result = sampleItem().updateCommercialData(updated, CLOCK);
        assertEquals("Door B", result.commercialData().name());
    }

    @Test
    void activeCannotBeCancelled() {
        OrderItem active = approvedItem();
        InvalidOrderStateException ex =
                assertThrows(InvalidOrderStateException.class, () -> active.cancel(CLOCK));
        assertTrue(ex.getMessage().contains("cannot be cancelled"));
    }

    @Test
    void cancelledCannotBeCancelledAgain() {
        OrderItem cancelled = sampleItem().cancel(CLOCK);
        assertThrows(InvalidOrderStateException.class, () -> cancelled.cancel(CLOCK));
    }

    @Test
    void commercialUpdateForbiddenWhenActive() {
        assertThrows(
                InvalidOrderStateException.class,
                () -> approvedItem().updateCommercialData(sampleCommercialData(), CLOCK));
    }

    @Test
    void approveDraftActivatesItemAndClearsDraft() {
        OrderItem approved = approvedItem();
        assertEquals(OrderItemStatus.ACTIVE, approved.status());
        assertEquals(1, approved.activeRevisionNumber().orElseThrow().value());
        assertTrue(approved.draftRevisionNumber().isEmpty());
        assertTrue(approved.activeRevision().orElseThrow().isApproved());
    }

    @Test
    void createNextDraftDoesNotChangeActive() {
        OrderItem withDraft = approvedItem()
                .createNextDraftRevision(OrderedQuantity.of(3), CLOCK);
        assertEquals(1, withDraft.activeRevisionNumber().orElseThrow().value());
        assertEquals(2, withDraft.draftRevisionNumber().orElseThrow().value());
        assertEquals(
                RevisionStatus.APPROVED,
                withDraft.revision(RevisionNumber.first()).orElseThrow().status());
    }

    @Test
    void secondDraftRejected() {
        OrderItem withDraft = approvedItem()
                .createNextDraftRevision(OrderedQuantity.of(3), CLOCK);
        assertThrows(
                InvalidOrderStateException.class,
                () -> withDraft.createNextDraftRevision(OrderedQuantity.of(4), CLOCK));
    }

    @Test
    void approveSecondDraftSwitchesActiveAndPreservesPrevious() {
        OrderItem withDraft = approvedItem()
                .createNextDraftRevision(OrderedQuantity.of(3), CLOCK);
        ItemSpecification spec = ItemSpecification.of(
                withDraft.id(),
                RevisionNumber.of(2),
                java.util.List.of(sampleLine()));
        OrderItem approved = withDraft
                .updateDraftSpecification(spec, CLOCK)
                .approveDraftRevision(CLOCK);
        assertEquals(2, approved.activeRevisionNumber().orElseThrow().value());
        assertTrue(approved.draftRevisionNumber().isEmpty());
        assertTrue(approved.revision(RevisionNumber.first()).orElseThrow().isApproved());
        assertTrue(approved.revision(RevisionNumber.of(2)).orElseThrow().isApproved());
    }

    @Test
    void approveWithoutDraftFails() {
        OrderItem active = approvedItem();
        assertThrows(InvalidOrderStateException.class, () -> active.approveDraftRevision(CLOCK));
    }

    @Test
    void approveWithoutSpecificationFails() {
        assertThrows(
                InvalidOrderStateException.class,
                () -> sampleItem().approveDraftRevision(CLOCK));
    }

    @Test
    void approvedRevisionQuantityChangeForbidden() {
        OrderItem active = approvedItem();
        OrderItemRevision approvedRev = active.activeRevision().orElseThrow();
        assertThrows(
                InvalidOrderStateException.class,
                () -> approvedRev.withOrderedQuantity(OrderedQuantity.of(99)));
    }

    private static OrderItem sampleItem() {
        return OrderItem.create(
                OrderItemId.generate(),
                OrderId.generate(),
                sampleCommercialData(),
                OrderedQuantity.of(1),
                CLOCK);
    }

    private static OrderItem approvedItem() {
        OrderItem item = sampleItem();
        ItemSpecification spec = ItemSpecification.of(
                item.id(), RevisionNumber.first(), java.util.List.of(sampleLine()));
        return item.updateDraftSpecification(spec, CLOCK).approveDraftRevision(CLOCK);
    }

    private static ItemCommercialData sampleCommercialData() {
        return ItemCommercialData.of(ProductCode.of("P-1"), "Door A", null);
    }

    private static SpecificationLine sampleLine() {
        return SpecificationLine.of(
                "MAT-1",
                "Glass",
                BigDecimal.ONE,
                "m2",
                BigDecimal.valueOf(1.2));
    }
}
