package com.tmp.order.application.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.PayloadSchemaVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPayloadModelsTest {

    private static final Instant NOW = Instant.parse("2026-07-25T10:00:00Z");

    @Test
    void createPayloadRequiresIdentityFieldsAndBindsDocumentId() {
        DocumentId documentId = DocumentId.of(UUID.randomUUID());
        OrderCreatePayload payload =
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW);

        assertEquals(documentId, payload.documentId());
        assertEquals(DocumentTypeCode.ORDER_CREATE, payload.documentTypeCode());
        assertEquals(PayloadSchemaVersion.initial(), payload.identity().schemaVersion());
        assertEquals(PayloadRevision.initial(), payload.identity().payloadRevision());
        assertEquals(NOW, payload.identity().createdAt());
        assertEquals(NOW, payload.identity().updatedAt());
        assertEquals(orderNumber(), payload.orderNumber());
    }

    @Test
    void updateApproveCancelPayloadsBindMatchingDocumentTypes() {
        DocumentId documentId = DocumentId.generate();
        OrderId orderId = OrderId.generate();

        OrderUpdatePayload update =
                OrderUpdatePayload.create(documentId, orderId, commercialData(), NOW);
        OrderApprovePayload approve = OrderApprovePayload.create(documentId, orderId, NOW);
        OrderActivatePayload activate = OrderActivatePayload.create(documentId, orderId, NOW);
        OrderCancelPayload cancel = OrderCancelPayload.create(documentId, orderId, NOW);

        assertEquals(DocumentTypeCode.ORDER_UPDATE, update.documentTypeCode());
        assertEquals(DocumentTypeCode.ORDER_APPROVE, approve.documentTypeCode());
        assertEquals(DocumentTypeCode.ORDER_ACTIVATE, activate.documentTypeCode());
        assertEquals(DocumentTypeCode.ORDER_CANCEL, cancel.documentTypeCode());
        assertEquals(orderId, update.orderId());
        assertEquals(orderId, approve.orderId());
        assertEquals(orderId, activate.orderId());
        assertEquals(orderId, cancel.orderId());
    }

    @Test
    void mismatchedDocumentTypeIsRejectedOnRehydrate() {
        PayloadIdentity wrongType =
                PayloadIdentity.initialDraft(
                        DocumentId.generate(), DocumentTypeCode.ORDER_CANCEL, NOW);

        assertThrows(
                IllegalArgumentException.class,
                () -> OrderCreatePayload.rehydrate(wrongType, orderNumber(), commercialData()));
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderUpdatePayload.rehydrate(wrongType, OrderId.generate(), commercialData()));
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderApprovePayload.rehydrate(wrongType, OrderId.generate()));
        assertThrows(
                IllegalArgumentException.class,
                () -> OrderActivatePayload.rehydrate(wrongType, OrderId.generate()));
    }

    @Test
    void nullRequiredFieldsAreRejected() {
        DocumentId documentId = DocumentId.generate();
        assertThrows(
                NullPointerException.class,
                () -> OrderCreatePayload.create(null, orderNumber(), commercialData(), NOW));
        assertThrows(
                NullPointerException.class,
                () -> OrderCreatePayload.create(documentId, null, commercialData(), NOW));
        assertThrows(
                NullPointerException.class,
                () -> OrderCreatePayload.create(documentId, orderNumber(), null, NOW));
        assertThrows(
                NullPointerException.class,
                () -> OrderCreatePayload.create(documentId, orderNumber(), commercialData(), null));
        assertThrows(NullPointerException.class, () -> DocumentId.of(null));
        assertThrows(
                NullPointerException.class,
                () -> PayloadIdentity.of(null, DocumentTypeCode.ORDER_CREATE,
                        PayloadSchemaVersion.initial(), PayloadRevision.initial(), NOW, NOW));
    }

    @Test
    void payloadsAreImmutableAndHaveNoSetters() {
        for (Class<?> type : new Class<?>[] {
            OrderCreatePayload.class,
            OrderUpdatePayload.class,
            OrderApprovePayload.class,
            OrderActivatePayload.class,
            OrderCancelPayload.class,
            PayloadIdentity.class,
            DocumentId.class
        }) {
            assertTrue(Modifier.isFinal(type.getModifiers()), type.getSimpleName() + " must be final");
            Arrays.stream(type.getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("set"))
                    .forEach(m -> {
                        throw new AssertionError("Setter not allowed: " + type.getSimpleName() + "." + m.getName());
                    });
            for (Field field : type.getDeclaredFields()) {
                assertTrue(
                        Modifier.isFinal(field.getModifiers()),
                        type.getSimpleName() + "." + field.getName() + " must be final");
            }
        }
    }

    @Test
    void commercialUpdateIncrementsPayloadRevisionWithoutMutatingOriginal() {
        OrderCreatePayload original =
                OrderCreatePayload.create(DocumentId.generate(), orderNumber(), commercialData(), NOW);
        OrderCommercialData updated =
                OrderCommercialData.of(
                        "C-2",
                        "Updated Customer",
                        null,
                        null,
                        null,
                        OrderDirection.DEALER,
                        CurrencyCode.of("EUR"));
        Instant later = NOW.plusSeconds(60);

        OrderCreatePayload next = original.withCommercialData(updated, later);

        assertEquals(PayloadRevision.initial(), original.identity().payloadRevision());
        assertEquals(PayloadRevision.of(1L), next.identity().payloadRevision());
        assertNotEquals(original.commercialData().customerName(), next.commercialData().customerName());
        assertEquals(later, next.identity().updatedAt());
        assertEquals(original.identity().createdAt(), next.identity().createdAt());
    }

    @Test
    void payloadsAreTypedSealedHierarchyWithoutGenericJson() {
        OrderDocumentPayload payload =
                OrderCreatePayload.create(DocumentId.generate(), orderNumber(), commercialData(), NOW);
        assertInstanceOf(OrderCreatePayload.class, payload);
        assertTrue(OrderDocumentPayload.class.isSealed());

        for (Class<?> type : new Class<?>[] {
            OrderCreatePayload.class,
            OrderUpdatePayload.class,
            OrderApprovePayload.class,
            OrderActivatePayload.class,
            OrderCancelPayload.class
        }) {
            for (Field field : type.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                assertNotEquals(Map.class, fieldType, "Map/JSON field forbidden on " + type.getSimpleName());
                assertNotEquals(Object.class, fieldType, "raw Object field forbidden on " + type.getSimpleName());
                assertTrue(
                        !(String.class.equals(fieldType)
                                && field.getName().toLowerCase().contains("json")),
                        "JSON string field forbidden on " + type.getSimpleName());
            }
        }
    }

    private static OrderNumber orderNumber() {
        return OrderNumber.of("PR-2026-000001");
    }

    private static OrderCommercialData commercialData() {
        return OrderCommercialData.of(
                "C-1",
                "Acme Corp",
                "CTR-1",
                "SITE-1",
                "Manager",
                OrderDirection.CORPORATE,
                CurrencyCode.of("USD"));
    }
}
