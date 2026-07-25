package com.tmp.order.application.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.ProductCode;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OrderItemPayloadModelsTest {

    private static final Instant NOW = Instant.parse("2026-07-25T11:00:00Z");

    @Test
    void createPayloadContainsOnlyCreationDataAndIdentity() {
        DocumentId documentId = DocumentId.generate();
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        OrderItemCreatePayload payload =
                OrderItemCreatePayload.create(
                        documentId, orderId, itemId, commercialData(), OrderedQuantity.of(3), NOW);

        assertEquals(documentId, payload.documentId());
        assertEquals(DocumentTypeCode.ORDER_ITEM_CREATE, payload.documentTypeCode());
        assertEquals(orderId, payload.orderId());
        assertEquals(itemId, payload.orderItemId());
        assertEquals(OrderedQuantity.of(3), payload.orderedQuantity());
        assertEquals(PayloadRevision.initial(), payload.identity().payloadRevision());
    }

    @Test
    void updatePayloadContainsOnlyCommercialFields() {
        OrderItemUpdatePayload payload =
                OrderItemUpdatePayload.create(
                        DocumentId.generate(), OrderItemId.generate(), commercialData(), NOW);

        assertEquals(DocumentTypeCode.ORDER_ITEM_UPDATE, payload.documentTypeCode());
        Set<String> fieldNames =
                Arrays.stream(OrderItemUpdatePayload.class.getDeclaredFields())
                        .map(Field::getName)
                        .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("identity", "orderItemId", "commercialData"), fieldNames);

        for (Field field : OrderItemUpdatePayload.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase(Locale.ROOT);
            assertFalse(name.contains("revision"), "Revision field forbidden: " + field.getName());
            assertFalse(
                    name.contains("specification") || name.contains("spec"),
                    "Specification field forbidden: " + field.getName());
            assertFalse(
                    field.getType().equals(RevisionNumber.class)
                            || field.getType().equals(OrderedQuantity.class),
                    "Revision/quantity type forbidden on update payload: " + field.getName());
        }
    }

    @Test
    void cancelPayloadContainsOnlyItemIdentity() {
        OrderItemId itemId = OrderItemId.generate();
        OrderItemCancelPayload payload =
                OrderItemCancelPayload.create(DocumentId.generate(), itemId, NOW);
        assertEquals(DocumentTypeCode.ORDER_ITEM_CANCEL, payload.documentTypeCode());
        assertEquals(itemId, payload.orderItemId());
        Set<String> fieldNames =
                Arrays.stream(OrderItemCancelPayload.class.getDeclaredFields())
                        .map(Field::getName)
                        .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("identity", "orderItemId"), fieldNames);
    }

    @Test
    void mismatchedDocumentTypeRejected() {
        PayloadIdentity wrong =
                PayloadIdentity.initialDraft(
                        DocumentId.generate(), DocumentTypeCode.ORDER_CREATE, NOW);
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemCreatePayload.rehydrate(
                        wrong,
                        OrderId.generate(),
                        OrderItemId.generate(),
                        commercialData(),
                        OrderedQuantity.of(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemUpdatePayload.rehydrate(
                        wrong, OrderItemId.generate(), commercialData()));
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderItemCancelPayload.rehydrate(wrong, OrderItemId.generate()));
    }

    @Test
    void itemPayloadsAreImmutableAndVersioned() {
        for (Class<?> type : new Class<?>[] {
            OrderItemCreatePayload.class, OrderItemUpdatePayload.class, OrderItemCancelPayload.class
        }) {
            assertTrue(Modifier.isFinal(type.getModifiers()));
            Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("set"))
                    .forEach(m -> {
                        throw new AssertionError("Setter not allowed: " + m.getName());
                    });
        }

        OrderItemUpdatePayload original =
                OrderItemUpdatePayload.create(
                        DocumentId.generate(), OrderItemId.generate(), commercialData(), NOW);
        OrderItemUpdatePayload next =
                original.withCommercialData(
                        ItemCommercialData.of(ProductCode.of("P-2"), "Renamed", null),
                        NOW.plusSeconds(10));
        assertEquals(PayloadRevision.initial(), original.identity().payloadRevision());
        assertEquals(PayloadRevision.of(1L), next.identity().payloadRevision());
    }

    @Test
    void nullRequiredFieldsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> OrderItemCreatePayload.create(
                        null,
                        OrderId.generate(),
                        OrderItemId.generate(),
                        commercialData(),
                        OrderedQuantity.of(1),
                        NOW));
        assertThrows(
                NullPointerException.class,
                () -> OrderItemUpdatePayload.create(
                        DocumentId.generate(), null, commercialData(), NOW));
        assertThrows(
                NullPointerException.class,
                () -> OrderItemCancelPayload.create(DocumentId.generate(), null, NOW));
    }

    private static ItemCommercialData commercialData() {
        return ItemCommercialData.of(ProductCode.of("P-1"), "Panel A", "note");
    }
}
