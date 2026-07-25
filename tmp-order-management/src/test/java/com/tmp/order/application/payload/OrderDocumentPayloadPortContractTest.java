package com.tmp.order.application.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.PayloadSchemaVersion;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the single typed-payload persistence port (STAGE5-015).
 */
class OrderDocumentPayloadPortContractTest {

    private static final Instant NOW = Instant.parse("2026-07-25T14:00:00Z");

    private OrderDocumentPayloadPort port;

    @BeforeEach
    void setUp() {
        port = new InMemoryOrderDocumentPayloadPort();
    }

    @Test
    void portIsInfrastructureFreeInterface() {
        assertTrue(OrderDocumentPayloadPort.class.isInterface());
        for (Method method : OrderDocumentPayloadPort.class.getMethods()) {
            assertNoInfra(method.getReturnType());
            Arrays.stream(method.getParameterTypes()).forEach(this::assertNoInfra);
            assertFalse(
                    method.isDefault() && method.getName().toLowerCase().contains("jdbc"),
                    "No JDBC helpers on port");
        }
    }

    @Test
    void loadCreateUpdateDeleteAndExistsContract() {
        DocumentId documentId = DocumentId.generate();
        assertTrue(port.findByDocumentId(documentId).isEmpty());
        assertFalse(port.existsByDocumentId(documentId));

        OrderCreatePayload created =
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW);
        port.create(created);

        Optional<OrderDocumentPayload> loaded = port.findByDocumentId(documentId);
        assertTrue(loaded.isPresent());
        assertEquals(DocumentTypeCode.ORDER_CREATE, loaded.get().documentTypeCode());
        assertEquals(PayloadSchemaVersion.initial(), loaded.get().identity().schemaVersion());
        assertEquals(PayloadRevision.initial(), loaded.get().identity().payloadRevision());
        assertTrue(port.existsByDocumentId(documentId));

        OrderCreatePayload next =
                created.withCommercialData(
                        OrderCommercialData.of(
                                "X",
                                "Next",
                                null,
                                null,
                                null,
                                OrderDirection.DEALER,
                                CurrencyCode.of("EUR")),
                        NOW.plusSeconds(1));
        OrderDocumentPayload updated = port.update(next, PayloadRevision.initial());
        assertEquals(PayloadRevision.of(1L), updated.identity().payloadRevision());
        assertEquals(DocumentTypeCode.ORDER_CREATE, updated.documentTypeCode());
        assertEquals(PayloadSchemaVersion.initial(), updated.identity().schemaVersion());

        port.deleteDraft(documentId);
        assertTrue(port.findByDocumentId(documentId).isEmpty());
        assertFalse(port.existsByDocumentId(documentId));
    }

    @Test
    void absenceAndVersionConflictAreExplicit() {
        DocumentId missing = DocumentId.generate();
        assertEquals(Optional.empty(), port.findByDocumentId(missing));
        assertThrows(PayloadNotFoundException.class, () -> port.deleteDraft(missing));

        DocumentId documentId = DocumentId.generate();
        OrderCreatePayload created =
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW);
        port.create(created);

        OrderCreatePayload bumped = created.withCommercialData(commercialData(), NOW.plusSeconds(2));
        port.update(bumped, PayloadRevision.initial());

        OrderCreatePayload stale = created.withCommercialData(commercialData(), NOW.plusSeconds(3));
        PayloadOptimisticLockException conflict =
                assertThrows(
                        PayloadOptimisticLockException.class,
                        () -> port.update(stale, PayloadRevision.initial()));
        assertEquals(PayloadRevision.initial(), conflict.expected());
        assertEquals(PayloadRevision.of(1L), conflict.actual());
    }

    @Test
    void duplicateCreateRejectedAndPayloadTypePreserved() {
        DocumentId documentId = DocumentId.generate();
        OrderCreatePayload created =
                OrderCreatePayload.create(documentId, orderNumber(), commercialData(), NOW);
        port.create(created);
        assertThrows(PayloadAlreadyExistsException.class, () -> port.create(created));

        OrderDocumentPayload loaded = port.findByDocumentId(documentId).orElseThrow();
        assertTrue(loaded instanceof OrderCreatePayload);
        assertEquals(DocumentTypeCode.ORDER_CREATE, loaded.documentTypeCode());
    }

    @Test
    void requiredOperationsAreDeclaredOnPort() throws Exception {
        assertEquals(
                Optional.class,
                OrderDocumentPayloadPort.class
                        .getMethod("findByDocumentId", DocumentId.class)
                        .getReturnType());
        OrderDocumentPayloadPort.class.getMethod("create", OrderDocumentPayload.class);
        OrderDocumentPayloadPort.class.getMethod(
                "update", OrderDocumentPayload.class, PayloadRevision.class);
        OrderDocumentPayloadPort.class.getMethod("deleteDraft", DocumentId.class);
        assertEquals(
                boolean.class,
                OrderDocumentPayloadPort.class
                        .getMethod("existsByDocumentId", DocumentId.class)
                        .getReturnType());
    }

    private void assertNoInfra(Class<?> type) {
        String name = type.getName();
        assertTrue(
                !name.startsWith("org.springframework")
                        && !name.startsWith("jakarta.persistence")
                        && !name.startsWith("org.hibernate")
                        && !name.startsWith("java.sql")
                        && !name.startsWith("javax.sql")
                        && !name.contains("Jdbc")
                        && !name.contains("EntityManager")
                        && !name.equals("java.lang.Object"),
                () -> "Infrastructure / unchecked type on payload port: " + name);
    }

    private static OrderNumber orderNumber() {
        return OrderNumber.of("PR-2026-000200");
    }

    private static OrderCommercialData commercialData() {
        return OrderCommercialData.of(
                "C-1",
                "Customer",
                null,
                null,
                null,
                OrderDirection.PRIVATE,
                CurrencyCode.of("USD"));
    }
}
