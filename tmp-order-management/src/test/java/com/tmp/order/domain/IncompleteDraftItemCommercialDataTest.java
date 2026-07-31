package com.tmp.order.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncompleteDraftItemCommercialDataTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-31T02:00:00Z"), ZoneOffset.UTC);

    @Test
    void draftItemMayBeCreatedWithNullProductCodeAndName() {
        ItemCommercialData commercial = ItemCommercialData.of(null, null, null, "EXT-1");
        OrderItem item =
                OrderItem.create(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        commercial,
                        OrderedQuantity.of(2),
                        CLOCK);

        assertEquals(OrderItemStatus.DRAFT, item.status());
        assertNull(item.commercialData().productCode());
        assertNull(item.commercialData().name());
        assertEquals("EXT-1", item.commercialData().externalPositionNumber());
        assertTrue(item.commercialData().isProductCodeAbsent());
        assertTrue(item.commercialData().isNameAbsent());
    }

    @Test
    void blankNameAndProductCodeNormalizeToNull() {
        ItemCommercialData commercial = ItemCommercialData.ofRaw("  ", "   ", null, null);
        assertNull(commercial.productCode());
        assertNull(commercial.name());
        assertFalse(commercial.hasProductCode());
        assertFalse(commercial.hasName());
    }

    @Test
    void placeholdersAreRejectedForProductCodeAndName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemCommercialData.ofRaw("UNKNOWN", "Door", null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemCommercialData.of(ProductCode.of("P-1"), "N/A", null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemCommercialData.of(ProductCode.of("P-1"), "IMPORT", null, null));
    }

    @Test
    void completeItemCommercialDataWorksAsBefore() {
        ItemCommercialData commercial =
                ItemCommercialData.of(ProductCode.of("WIN-1"), "Window", "note", "EXT-9");
        assertEquals("WIN-1", commercial.productCode().value());
        assertEquals("Window", commercial.name());
        assertTrue(commercial.isCompleteForApproval());
        assertEquals(List.of(), commercial.missingMandatoryFieldsForApproval());
    }

    @Test
    void approvalWithoutProductCodeIsRejected() {
        OrderItem item = draftWithSpec(ItemCommercialData.of(null, "Door", null, null));
        InvalidOrderStateException ex =
                assertThrows(InvalidOrderStateException.class, () -> item.approveDraftRevision(CLOCK));
        assertTrue(ex.getMessage().contains("Код изделия"));
        assertFalse(ex.getMessage().contains("Наименование изделия"));
    }

    @Test
    void approvalWithoutNameIsRejected() {
        OrderItem item =
                draftWithSpec(ItemCommercialData.of(ProductCode.of("P-1"), null, null, null));
        InvalidOrderStateException ex =
                assertThrows(InvalidOrderStateException.class, () -> item.approveDraftRevision(CLOCK));
        assertTrue(ex.getMessage().contains("Наименование изделия"));
        assertFalse(ex.getMessage().contains("Код изделия"));
    }

    @Test
    void approvalWithoutBothFieldsListsBoth() {
        OrderItem item = draftWithSpec(ItemCommercialData.of(null, null, null, "EXT-2"));
        InvalidOrderStateException ex =
                assertThrows(InvalidOrderStateException.class, () -> item.approveDraftRevision(CLOCK));
        assertTrue(ex.getMessage().contains("Код изделия"));
        assertTrue(ex.getMessage().contains("Наименование изделия"));
    }

    @Test
    void approvalOfCompleteItemSucceeds() {
        OrderItem item =
                draftWithSpec(
                        ItemCommercialData.of(ProductCode.of("P-1"), "Door", null, null));
        OrderItem approved = item.approveDraftRevision(CLOCK);
        assertEquals(OrderItemStatus.ACTIVE, approved.status());
        assertEquals("P-1", approved.commercialData().productCode().value());
        assertEquals("Door", approved.commercialData().name());
    }

    @Test
    void specificationRemainsRequiredForApproval() {
        OrderItem item =
                OrderItem.create(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        ItemCommercialData.of(ProductCode.of("P-1"), "Door", null, null),
                        OrderedQuantity.of(1),
                        CLOCK);
        InvalidOrderStateException ex =
                assertThrows(InvalidOrderStateException.class, () -> item.approveDraftRevision(CLOCK));
        assertTrue(ex.getMessage().contains("non-empty specification"));
    }

    private static OrderItem draftWithSpec(ItemCommercialData commercial) {
        OrderItemId itemId = OrderItemId.generate();
        OrderItem item =
                OrderItem.create(
                        itemId,
                        OrderId.generate(),
                        commercial,
                        OrderedQuantity.of(1),
                        CLOCK);
        ItemSpecification spec =
                ItemSpecification.of(
                        itemId,
                        item.draftRevisionNumber().orElseThrow(),
                        List.of(
                                SpecificationLine.of(
                                        "M1",
                                        "Mat",
                                        null,
                                        null,
                                        BigDecimal.ONE,
                                        "шт")));
        return item.updateDraftSpecification(spec, CLOCK);
    }
}
