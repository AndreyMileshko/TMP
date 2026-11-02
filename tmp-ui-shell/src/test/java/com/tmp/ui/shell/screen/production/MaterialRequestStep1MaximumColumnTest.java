package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Focused STEP 1 maximum column and invalid-quantity feedback. */
class MaterialRequestStep1MaximumColumnTest {

    private static final UUID ORDER_75 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa75");
    private static final UUID ITEM_75 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbb75");
    private static final UUID ORDER_82 = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa82");
    private static final UUID ITEM_82 = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbb82");

    @Test
    void columnTitleIsMaximum() {
        assertEquals("Максимум", MaterialRequestDialogSupport.COLUMN_MAXIMUM);
    }

    @Test
    void normalMaximumUsesRequestable() {
        MaterialRequestQuantityRow row = flexible(ORDER_82, ITEM_82, 2L, 5L, 2L);
        assertFalse(row.repeatedRequest());
        assertEquals(2L, row.maximumProductQuantity());
        assertEquals("2", MaterialRequestDialogSupport.maximumColumnDisplay(row));
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));
    }

    @Test
    void repeatedMaximumUsesOrdered() {
        MaterialRequestQuantityRow row = flexible(ORDER_82, ITEM_82, 2L, 5L, 2L);
        row.setRepeatedRequest(true);
        assertEquals(5L, row.maximumProductQuantity());
        assertEquals("5", MaterialRequestDialogSupport.maximumColumnDisplay(row));
        row.setRequestedProductQuantity(5L);
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));
    }

    @Test
    void maximumUpdatesImmediatelyOnCheckboxToggle_order26106082() {
        MaterialRequestQuantityRow row = flexible(ORDER_82, ITEM_82, 2L, 5L, 2L);
        assertEquals("2", MaterialRequestDialogSupport.maximumColumnDisplay(row));
        row.setRepeatedRequest(true);
        assertEquals("5", MaterialRequestDialogSupport.maximumColumnDisplay(row));
        row.setRepeatedRequest(false);
        assertEquals("2", MaterialRequestDialogSupport.maximumColumnDisplay(row));
    }

    @Test
    void zeroNormalRequestable_order26106075() {
        MaterialRequestQuantityRow row = standard(ORDER_75, ITEM_75, 0L, 1L, 0L);
        assertEquals("0", MaterialRequestDialogSupport.maximumColumnDisplay(row));
        Optional<String> error = MaterialRequestDialogSupport.validateStep1Quantities(List.of(row));
        assertEquals(
                MaterialRequestDialogSupport.NORMAL_ZERO_REQUESTABLE_MESSAGE, error.orElseThrow());
        assertFalse(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));

        row.setRepeatedRequest(true);
        assertEquals("1", MaterialRequestDialogSupport.maximumColumnDisplay(row));
        assertEquals(1L, row.requestedProductQuantity());
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));
    }

    @Test
    void standardAndFlexiblePreserveAuthoritativeMaximums() {
        MaterialRequestQuantityRow standard = standard(ORDER_82, ITEM_82, 2L, 5L, 2L);
        assertFalse(standard.quantityEditable());
        assertEquals(2L, standard.maximumProductQuantity());
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(standard)));

        standard.setRepeatedRequest(true);
        assertTrue(standard.quantityEditable());
        assertEquals(5L, standard.maximumProductQuantity());

        MaterialRequestQuantityRow flexible = flexible(ORDER_82, ITEM_82, 2L, 5L, 2L);
        assertTrue(flexible.quantityEditable());
        assertEquals(2L, flexible.maximumProductQuantity());
        flexible.setRequestedProductQuantity(1L);
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(flexible)));
        flexible.setRepeatedRequest(true);
        assertEquals(5L, flexible.maximumProductQuantity());
    }

    @Test
    void invalidQuantityShowsPermittedRangeMessage() {
        MaterialRequestQuantityRow row = flexible(ORDER_82, ITEM_82, 2L, 5L, 2L);
        row.setRequestedProductQuantity(3L);
        Optional<String> error = MaterialRequestDialogSupport.validateStep1Quantities(List.of(row));
        assertEquals("Допустимое количество: от 1 до 2.", error.orElseThrow());
        assertFalse(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));

        row.setRepeatedRequest(true);
        row.setRequestedProductQuantity(6L);
        error = MaterialRequestDialogSupport.validateStep1Quantities(List.of(row));
        assertEquals("Допустимое количество: от 1 до 5.", error.orElseThrow());
        assertFalse(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));

        row.setRequestedProductQuantity(5L);
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));
    }

    @Test
    void nextEnablementTracksValidityAcrossModeSwitch() {
        MaterialRequestQuantityRow row = flexible(ORDER_82, ITEM_82, 2L, 5L, 2L);
        row.setRequestedProductQuantity(4L);
        assertFalse(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));

        row.setRepeatedRequest(true);
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));

        row.setRepeatedRequest(false);
        // Preserved FLEXIBLE clamp restores requestable max when leaving repeated.
        assertEquals(2L, row.requestedProductQuantity());
        assertTrue(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));

        row.setRequestedProductQuantity(4L);
        assertFalse(MaterialRequestDialogSupport.step1QuantitiesValid(List.of(row)));
        assertEquals(
                MaterialRequestDialogSupport.permittedQuantityRangeMessage(2L),
                MaterialRequestDialogSupport.validateStep1Quantities(List.of(row)).orElseThrow());
    }

    @Test
    void rowsApplyMaximumIndependently() {
        MaterialRequestQuantityRow zeroNormal = standard(ORDER_75, ITEM_75, 0L, 1L, 0L);
        MaterialRequestQuantityRow partial = flexible(ORDER_82, ITEM_82, 2L, 5L, 2L);
        assertEquals("0", MaterialRequestDialogSupport.maximumColumnDisplay(zeroNormal));
        assertEquals("2", MaterialRequestDialogSupport.maximumColumnDisplay(partial));

        zeroNormal.setRepeatedRequest(true);
        assertEquals("1", MaterialRequestDialogSupport.maximumColumnDisplay(zeroNormal));
        assertEquals("2", MaterialRequestDialogSupport.maximumColumnDisplay(partial));

        partial.setRepeatedRequest(true);
        assertEquals("1", MaterialRequestDialogSupport.maximumColumnDisplay(zeroNormal));
        assertEquals("5", MaterialRequestDialogSupport.maximumColumnDisplay(partial));
    }

    private static MaterialRequestQuantityRow flexible(
            UUID orderId, UUID itemId, long requestable, long ordered, long requested) {
        return new MaterialRequestQuantityRow(
                orderId,
                itemId,
                "ORD",
                "Poz. 1",
                "Item",
                QuantityModeView.FLEXIBLE,
                requestable,
                ordered,
                requested);
    }

    private static MaterialRequestQuantityRow standard(
            UUID orderId, UUID itemId, long requestable, long ordered, long requested) {
        return new MaterialRequestQuantityRow(
                orderId,
                itemId,
                "ORD",
                "Poz. 1",
                "Item",
                QuantityModeView.STANDARD,
                requestable,
                ordered,
                requested);
    }
}