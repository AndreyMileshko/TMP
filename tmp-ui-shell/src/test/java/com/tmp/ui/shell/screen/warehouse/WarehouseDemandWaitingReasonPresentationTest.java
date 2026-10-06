package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class WarehouseDemandWaitingReasonPresentationTest {

    @Test
    void mapsKnownReasonsWithoutEnumNames() {
        assertEquals(
                "Материал не сопоставлен со складскими данными",
                WarehouseDemandWaitingReasonPresentation.labelFor("MATERIAL_UNMATCHED"));
        assertEquals(
                "Материал определён неоднозначно",
                WarehouseDemandWaitingReasonPresentation.labelFor("MATERIAL_AMBIGUOUS"));
        assertEquals(
                "Нет доступного остатка",
                WarehouseDemandWaitingReasonPresentation.labelFor("NO_AVAILABLE_STOCK"));
        assertEquals(
                "Ожидает подготовки перемещения",
                WarehouseDemandWaitingReasonPresentation.labelFor("ROUTING_DEFERRED"));
        String unmatched = WarehouseDemandWaitingReasonPresentation.labelFor("MATERIAL_UNMATCHED");
        assertFalse(unmatched.contains("MATERIAL_UNMATCHED"));
        assertFalse(unmatched.contains("UNMATCHED"));
    }

    @Test
    void blankFallsBackToDeferred() {
        assertEquals(
                "Ожидает подготовки перемещения",
                WarehouseDemandWaitingReasonPresentation.labelFor(null));
        assertEquals(
                "Ожидает подготовки перемещения",
                WarehouseDemandWaitingReasonPresentation.labelFor("  "));
    }
}
