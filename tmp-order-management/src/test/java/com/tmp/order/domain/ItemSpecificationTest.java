package com.tmp.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ItemSpecificationTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void emptySpecificationBelongsToRevision() {
        OrderItemId itemId = OrderItemId.generate();
        ItemSpecification spec = ItemSpecification.empty(itemId, RevisionNumber.first());
        assertEquals(itemId, spec.orderItemId());
        assertEquals(1, spec.revisionNumber().value());
        assertTrue(spec.isEmpty());
        assertFalse(spec.isImmutable());
    }

    @Test
    void lineValidationRejectsNonPositiveQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecificationLine.of("M1", "Mat", BigDecimal.ZERO, "pcs", BigDecimal.ONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecificationLine.of(
                        "M1", "Mat", BigDecimal.valueOf(-1), "pcs", BigDecimal.ONE));
    }

    @Test
    void lineValidationRejectsNegativeNormAndBlankFields() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecificationLine.of(
                        "M1", "Mat", BigDecimal.ONE, "pcs", BigDecimal.valueOf(-0.1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecificationLine.of(" ", "Mat", BigDecimal.ONE, "pcs", BigDecimal.ONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecificationLine.of("M1", " ", BigDecimal.ONE, "pcs", BigDecimal.ONE));
        assertThrows(
                IllegalArgumentException.class,
                () -> SpecificationLine.of("M1", "Mat", BigDecimal.ONE, " ", BigDecimal.ONE));
    }

    @Test
    void draftSpecificationCanBeEdited() {
        OrderItemId itemId = OrderItemId.generate();
        ItemSpecification draft = ItemSpecification.empty(itemId, RevisionNumber.first())
                .addLine(sampleLine("M1"))
                .addLine(sampleLine("M2"));
        assertEquals(2, draft.lines().size());
        ItemSpecification cleared = draft.clearLines();
        assertTrue(cleared.isEmpty());
        ItemSpecification replaced = cleared.withLines(List.of(sampleLine("M3")));
        assertEquals(1, replaced.lines().size());
        assertEquals("M3", replaced.lines().get(0).materialCode());
    }

    @Test
    void approvedSpecificationIsImmutable() {
        OrderItem item = OrderItem.create(
                OrderItemId.generate(),
                OrderId.generate(),
                ItemCommercialData.of(ProductCode.of("P-1"), "Door", null),
                OrderedQuantity.of(1),
                CLOCK);
        ItemSpecification draft = ItemSpecification.of(
                item.id(), RevisionNumber.first(), List.of(sampleLine("M1")));
        OrderItem approved = item.updateDraftSpecification(draft, CLOCK).approveDraftRevision(CLOCK);
        ItemSpecification frozen = approved.activeRevision().orElseThrow().specification().orElseThrow();
        assertTrue(frozen.isImmutable());
        assertThrows(InvalidOrderStateException.class, () -> frozen.addLine(sampleLine("M2")));
        assertThrows(InvalidOrderStateException.class, frozen::clearLines);
        assertThrows(
                InvalidOrderStateException.class,
                () -> frozen.withLines(List.of(sampleLine("M3"))));
    }

    @Test
    void changeAfterApprovalRequiresNewDraftRevision() {
        OrderItem approved = approvedItem();
        OrderItem withDraft = approved.createNextDraftRevision(OrderedQuantity.of(2), CLOCK);
        ItemSpecification newSpec = ItemSpecification.of(
                withDraft.id(), RevisionNumber.of(2), List.of(sampleLine("M-NEW")));
        OrderItem updated = withDraft.updateDraftSpecification(newSpec, CLOCK);
        assertEquals(
                "M-NEW",
                updated.draftRevision()
                        .orElseThrow()
                        .specification()
                        .orElseThrow()
                        .lines()
                        .get(0)
                        .materialCode());
        assertEquals(
                "M1",
                updated.activeRevision()
                        .orElseThrow()
                        .specification()
                        .orElseThrow()
                        .lines()
                        .get(0)
                        .materialCode());
    }

    @Test
    void linesCollectionIsUnmodifiableFromOutside() {
        ItemSpecification spec = ItemSpecification.of(
                OrderItemId.generate(),
                RevisionNumber.first(),
                List.of(sampleLine("M1")));
        List<SpecificationLine> view = spec.lines();
        assertThrows(UnsupportedOperationException.class, () -> view.add(sampleLine("M2")));
        assertThrows(UnsupportedOperationException.class, view::clear);
        List<SpecificationLine> mutableInput = new ArrayList<>();
        mutableInput.add(sampleLine("M1"));
        ItemSpecification fromMutable =
                ItemSpecification.of(OrderItemId.generate(), RevisionNumber.first(), mutableInput);
        mutableInput.add(sampleLine("M2"));
        assertEquals(1, fromMutable.lines().size());
    }

    private static OrderItem approvedItem() {
        OrderItem item = OrderItem.create(
                OrderItemId.generate(),
                OrderId.generate(),
                ItemCommercialData.of(ProductCode.of("P-1"), "Door", null),
                OrderedQuantity.of(1),
                CLOCK);
        ItemSpecification draft = ItemSpecification.of(
                item.id(), RevisionNumber.first(), List.of(sampleLine("M1")));
        return item.updateDraftSpecification(draft, CLOCK).approveDraftRevision(CLOCK);
    }

    private static SpecificationLine sampleLine(String code) {
        return SpecificationLine.of(code, "Material " + code, BigDecimal.ONE, "pcs", BigDecimal.ONE);
    }
}
