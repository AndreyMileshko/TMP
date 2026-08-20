package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecificationIdDerivationTest {

    @Test
    void sameInputProducesSameId() {
        OrderItemId itemId = OrderItemId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        RevisionNumber rev = RevisionNumber.of(1);

        UUID first = OrderItemAggregateJdbcSupport.deriveSpecificationId(itemId, rev);
        UUID second = OrderItemAggregateJdbcSupport.deriveSpecificationId(itemId, rev);

        assertEquals(first, second);
    }

    @Test
    void differentRevisionProducesDifferentId() {
        OrderItemId itemId = OrderItemId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        UUID rev1 = OrderItemAggregateJdbcSupport.deriveSpecificationId(itemId, RevisionNumber.of(1));
        UUID rev2 = OrderItemAggregateJdbcSupport.deriveSpecificationId(itemId, RevisionNumber.of(2));

        assertNotEquals(rev1, rev2);
    }

    @Test
    void differentItemProducesDifferentId() {
        OrderItemId item1 = OrderItemId.of(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        OrderItemId item2 = OrderItemId.of(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        UUID id1 = OrderItemAggregateJdbcSupport.deriveSpecificationId(item1, RevisionNumber.of(1));
        UUID id2 = OrderItemAggregateJdbcSupport.deriveSpecificationId(item2, RevisionNumber.of(1));

        assertNotEquals(id1, id2);
    }
}
