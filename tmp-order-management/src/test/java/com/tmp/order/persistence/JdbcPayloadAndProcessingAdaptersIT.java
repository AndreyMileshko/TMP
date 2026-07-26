package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.application.payload.PayloadNotFoundException;
import com.tmp.order.application.payload.PayloadOptimisticLockException;
import com.tmp.order.application.processing.DuplicateProcessingRecordException;
import com.tmp.order.application.processing.ProcessingOperation;
import com.tmp.order.application.processing.ProcessingRecord;
import com.tmp.order.application.processing.ResultReference;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.ProductCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcPayloadAndProcessingAdaptersIT {

    private static final Instant NOW = Instant.parse("2026-07-25T17:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private JdbcOrderDocumentPayloadAdapter payloads;
    private JdbcProcessingRecordAdapter processing;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM order_management.order_document_processing");
        jdbc.update("DELETE FROM order_management.order_document_payload");
        payloads = new JdbcOrderDocumentPayloadAdapter(jdbc);
        processing = new JdbcProcessingRecordAdapter(jdbc);
    }

    static Stream<OrderDocumentPayload> allPayloadTypes() {
        DocumentId id = DocumentId.generate();
        OrderId orderId = OrderId.generate();
        OrderItemId itemId = OrderItemId.generate();
        return Stream.of(
                OrderCreatePayload.create(id, OrderNumber.of("PR-" + id), commercial(), NOW),
                OrderUpdatePayload.create(DocumentId.generate(), orderId, commercial(), NOW),
                OrderApprovePayload.create(DocumentId.generate(), orderId, NOW),
                OrderCancelPayload.create(DocumentId.generate(), orderId, NOW),
                OrderItemCreatePayload.create(
                        DocumentId.generate(),
                        orderId,
                        itemId,
                        itemCommercial(),
                        OrderedQuantity.of(2),
                        NOW),
                OrderItemUpdatePayload.create(
                        DocumentId.generate(), itemId, itemCommercial(), NOW),
                OrderItemCancelPayload.create(DocumentId.generate(), itemId, NOW),
                OrderItemRevisionCreatePayload.create(
                        DocumentId.generate(),
                        itemId,
                        RevisionNumber.of(2),
                        RevisionNumber.first(),
                        NOW),
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.generate(),
                        itemId,
                        RevisionNumber.first(),
                        OrderedQuantity.of(3),
                        List.of(
                                OrderItemRevisionPayloadLine.of(
                                        1,
                                        "M-1",
                                        "Mat",
                                        BigDecimal.ONE,
                                        "pcs",
                                        BigDecimal.ZERO),
                                OrderItemRevisionPayloadLine.of(
                                        2,
                                        "M-2",
                                        "Board",
                                        BigDecimal.TEN,
                                        "m2",
                                        BigDecimal.ONE)),
                        NOW),
                OrderItemRevisionApprovePayload.create(
                        DocumentId.generate(), itemId, RevisionNumber.first(), NOW));
    }

    @ParameterizedTest
    @MethodSource("allPayloadTypes")
    void roundTripEachPayloadType(OrderDocumentPayload original) {
        payloads.create(original);
        OrderDocumentPayload loaded = payloads.findByDocumentId(original.documentId()).orElseThrow();
        assertEquals(original.documentTypeCode(), loaded.documentTypeCode());
        assertEquals(original.identity().payloadRevision(), loaded.identity().payloadRevision());
        assertEquals(original.identity().schemaVersion(), loaded.identity().schemaVersion());
    }

    @Test
    void revisionLinesRoundTripInStableOrder() {
        OrderItemRevisionUpdatePayload original =
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.generate(),
                        OrderItemId.generate(),
                        RevisionNumber.first(),
                        OrderedQuantity.of(1),
                        List.of(
                                OrderItemRevisionPayloadLine.of(
                                        2, "B", "Second", BigDecimal.ONE, "pcs", BigDecimal.ZERO),
                                OrderItemRevisionPayloadLine.of(
                                        1, "A", "First", BigDecimal.TEN, "pcs", BigDecimal.ONE)),
                        NOW);
        payloads.create(original);
        OrderItemRevisionUpdatePayload loaded =
                (OrderItemRevisionUpdatePayload)
                        payloads.findByDocumentId(original.documentId()).orElseThrow();
        assertEquals(2, loaded.lines().size());
        assertEquals(1, loaded.lines().get(0).lineNumber());
        assertEquals(2, loaded.lines().get(1).lineNumber());
        assertEquals("A", loaded.lines().get(0).materialCode());
    }

    @Test
    void optimisticLockAndStaleUpdateRejection() {
        OrderCreatePayload created =
                OrderCreatePayload.create(
                        DocumentId.generate(), OrderNumber.of("PR-LOCK-1"), commercial(), NOW);
        payloads.create(created);
        OrderCreatePayload next = created.withCommercialData(commercial(), NOW.plusSeconds(1));
        payloads.update(next, PayloadRevision.initial());

        OrderCreatePayload stale = created.withCommercialData(commercial(), NOW.plusSeconds(2));
        assertThrows(
                PayloadOptimisticLockException.class,
                () -> payloads.update(stale, PayloadRevision.initial()));
    }

    @Test
    void draftPayloadCascadeDeleteRemovesTypedRows() {
        OrderItemRevisionUpdatePayload original =
                OrderItemRevisionUpdatePayload.create(
                        DocumentId.generate(),
                        OrderItemId.generate(),
                        RevisionNumber.first(),
                        OrderedQuantity.of(1),
                        List.of(
                                OrderItemRevisionPayloadLine.of(
                                        1, "M", "Mat", BigDecimal.ONE, "pcs", BigDecimal.ZERO)),
                        NOW);
        payloads.create(original);
        payloads.deleteDraft(original.documentId());
        assertTrue(payloads.findByDocumentId(original.documentId()).isEmpty());
        Integer lines =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.order_item_revision_payload_line WHERE document_id = ?",
                        Integer.class,
                        original.documentId().value());
        assertEquals(0, lines);
        assertThrows(PayloadNotFoundException.class, () -> payloads.deleteDraft(original.documentId()));
    }

    @Test
    void processingRecordUniquenessAndNoDuplicates() {
        DocumentId documentId = DocumentId.generate();
        ProcessingRecord record =
                ProcessingRecord.completedPost(
                        documentId,
                        DocumentTypeCode.ORDER_CREATE,
                        PayloadRevision.initial(),
                        NOW,
                        ResultReference.of("order:1"));
        processing.insert(record);
        assertTrue(processing.exists(documentId, ProcessingOperation.POST));
        assertThrows(DuplicateProcessingRecordException.class, () -> processing.insert(record));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM order_management.order_document_processing WHERE document_id = ?",
                        Integer.class,
                        documentId.value()));
    }

    @Test
    void noJsonColumnsInOrderManagementSchema() {
        Integer json =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'order_management'
                          AND (data_type IN ('json', 'jsonb', 'bytea') OR udt_name IN ('json', 'jsonb', 'bytea'))
                        """,
                        Integer.class);
        assertEquals(0, json);
        assertFalse(payloads.existsByDocumentId(DocumentId.generate()));
    }

    private static OrderCommercialData commercial() {
        return OrderCommercialData.of(
                "C-1", "Customer", null, null, null, OrderDirection.PRIVATE, CurrencyCode.of("USD"));
    }

    private static ItemCommercialData itemCommercial() {
        return ItemCommercialData.of(ProductCode.of("P-1"), "Item", null);
    }
}
