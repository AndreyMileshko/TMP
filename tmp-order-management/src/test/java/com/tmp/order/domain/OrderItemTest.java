package com.tmp.order.domain;

import com.tmp.order.testsupport.IntakeContractFixtures;

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
    void commercialUpdateDoesNotChangeDraftRevisionOrSpecification() {
        OrderItem draft = sampleItem();
        ItemSpecification spec =
                ItemSpecification.of(draft.id(), RevisionNumber.first(), java.util.List.of(sampleLine()));
        draft = draft.updateDraftSpecification(spec, CLOCK);

        OrderItemRevision beforeRevision = draft.draftRevision().orElseThrow();
        ItemSpecification beforeSpec = beforeRevision.specification().orElseThrow();

        OrderItem updated = draft.updateCommercialData(sampleCommercialData(), CLOCK);
        assertTrue(updated.activeRevisionNumber().isEmpty());
        assertEquals(
                beforeRevision.revisionNumber(),
                updated.draftRevision().orElseThrow().revisionNumber());
        assertEquals(beforeRevision.orderedQuantity(), updated.draftRevision().orElseThrow().orderedQuantity());

        ItemSpecification afterSpec = updated.draftRevision().orElseThrow().specification().orElseThrow();
        assertEquals(beforeSpec.lines().size(), afterSpec.lines().size());
        assertEquals(
                beforeSpec.lines().get(0).materialCode(),
                afterSpec.lines().get(0).materialCode());
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
        OrderItem active = approvedItem();
        assertThrows(
                InvalidOrderStateException.class,
                () -> active.updateCommercialData(sampleCommercialData(), CLOCK));
    }

    @Test
    void activateDraftRevisionForImportWorksWithoutCommercialFields() {
        OrderItem item = sampleItem();
        ItemSpecification spec = ItemSpecification.of(
                item.id(), RevisionNumber.first(), java.util.List.of(sampleLine()));
        OrderItem withSpec = item.updateDraftSpecification(spec, CLOCK);
        OrderItem active = withSpec.activateDraftRevisionForImport(CLOCK);
        assertEquals(OrderItemStatus.ACTIVE, active.status());
        assertTrue(active.activeRevision().orElseThrow().isActive());
    }

    @Test
    void approveDraftRevisionRequiresCommercialCompleteness() {
        OrderItem incomplete =
                OrderItem.create(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        ItemCommercialData.of(null, null, null, "EXT-1"),
                        OrderedQuantity.of(1),
                        CLOCK);
        ItemSpecification spec = ItemSpecification.of(
                incomplete.id(), RevisionNumber.first(), java.util.List.of(sampleLine()));
        OrderItem withSpec = incomplete.updateDraftSpecification(spec, CLOCK);
        assertThrows(
                InvalidOrderStateException.class, () -> withSpec.approveDraftRevision(CLOCK));
    }

    @Test
    void approveDraftActivatesItemAndClearsDraft() {
        OrderItem approved = approvedItem();
        assertEquals(OrderItemStatus.ACTIVE, approved.status());
        assertEquals(1, approved.activeRevisionNumber().orElseThrow().value());
        assertTrue(approved.draftRevisionNumber().isEmpty());
        assertTrue(approved.activeRevision().orElseThrow().isActive());
    }

    @Test
    void createNextDraftDoesNotChangeActive() {
        OrderItem withDraft = approvedItem()
                .createNextDraftRevision(OrderedQuantity.of(3), CLOCK);
        assertEquals(1, withDraft.activeRevisionNumber().orElseThrow().value());
        assertEquals(2, withDraft.draftRevisionNumber().orElseThrow().value());
        assertEquals(
                RevisionStatus.ACTIVE,
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
        assertTrue(approved.revision(RevisionNumber.first()).orElseThrow().isActive());
        assertTrue(approved.revision(RevisionNumber.of(2)).orElseThrow().isActive());
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
        return IntakeContractFixtures.specLine("MAT-1", "Glass", BigDecimal.ONE, "m2");
    }
}
