package com.tmp.order.api.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.OrderItemStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderItemEditorSnapshotTest {

    @Test
    void snapshotContainsExternalPositionNumber() {
        OrderItemEditorSnapshot snapshot =
                OrderItemEditorSnapshot.of(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        "P-1",
                        "Panel",
                        "comment",
                        "EXT-42",
                        OrderItemStatus.DRAFT,
                        null,
                        null,
                        BigDecimal.ONE);

        assertEquals("EXT-42", snapshot.externalPositionNumber());
    }

    @Test
    void snapshotAllowsNullExternalPositionNumber() {
        OrderItemEditorSnapshot snapshot =
                OrderItemEditorSnapshot.of(
                        OrderItemId.generate(),
                        OrderId.generate(),
                        "P-1",
                        "Panel",
                        null,
                        null,
                        OrderItemStatus.DRAFT,
                        null,
                        null,
                        BigDecimal.ONE);

        assertNull(snapshot.externalPositionNumber());
    }
}
