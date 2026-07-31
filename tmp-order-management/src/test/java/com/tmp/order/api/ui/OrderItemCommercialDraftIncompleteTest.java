package com.tmp.order.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OrderItemCommercialDraftIncompleteTest {

    @Test
    void incompleteDraftAllowsNullProductCodeAndName() {
        OrderItemCommercialDraft draft = OrderItemCommercialDraft.of(null, null, null, "EXT-1");
        assertNull(draft.productCode());
        assertNull(draft.name());
        assertEquals("EXT-1", draft.externalPositionNumber());
    }

    @Test
    void blankProductCodeAndNameNormalizeToNull() {
        OrderItemCommercialDraft draft = OrderItemCommercialDraft.of("  ", "\t", "c", "  ");
        assertNull(draft.productCode());
        assertNull(draft.name());
        assertNull(draft.externalPositionNumber());
        assertEquals("c", draft.comments());
    }

    @Test
    void placeholdersAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemCommercialDraft.of("UNKNOWN", "Door", null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemCommercialDraft.of("P-1", "N/A", null, null));
    }

    @Test
    void completeDraftStillWorks() {
        OrderItemCommercialDraft draft =
                OrderItemCommercialDraft.of("P-1", "Door", "note", "EXT-9");
        assertEquals("P-1", draft.productCode());
        assertEquals("Door", draft.name());
        assertEquals("note", draft.comments());
        assertEquals("EXT-9", draft.externalPositionNumber());
    }
}
