package com.tmp.warehouse.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MaterialReservationLinkTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void createLinkStoresMaterialQuantityAndOrderTarget() {
        MaterialReservationLink link =
                MaterialReservationLink.create(
                        MaterialReservationLinkId.generate(),
                        MaterialReference.of("VEKA 103.211 WHITE"),
                        ReservationTargetReference.order("26096190"),
                        StockQuantity.of(200L),
                        CREATED_AT);

        assertEquals("VEKA 103.211 WHITE", link.material().materialCode());
        assertEquals(ReservationTargetType.ORDER, link.target().type());
        assertEquals("26096190", link.target().reference());
        assertEquals(StockQuantity.of(200L), link.quantity());
        assertEquals(CREATED_AT, link.createdAt());
    }

    @Test
    void createRejectsNonPositiveQuantity() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MaterialReservationLink.create(
                                MaterialReservationLinkId.generate(),
                                MaterialReference.of("ALU-6060"),
                                ReservationTargetReference.order("1"),
                                StockQuantity.of(0),
                                CREATED_AT));
    }

    @Test
    void orderTargetRejectsBlankReference() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ReservationTargetReference.order("  "));
    }
}
