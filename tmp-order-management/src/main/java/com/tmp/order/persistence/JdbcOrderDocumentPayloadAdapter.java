package com.tmp.order.persistence;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderItemId;
import com.tmp.order.api.RevisionNumber;
import com.tmp.order.api.RevisionStatus;
import com.tmp.order.application.payload.DocumentId;
import com.tmp.order.application.payload.DocumentTypeCode;
import com.tmp.order.application.payload.OrderApprovePayload;
import com.tmp.order.application.payload.OrderCancelPayload;
import com.tmp.order.application.payload.OrderCreatePayload;
import com.tmp.order.application.payload.OrderDocumentPayload;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.payload.OrderItemCancelPayload;
import com.tmp.order.application.payload.OrderItemCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionApprovePayload;
import com.tmp.order.application.payload.OrderItemRevisionCreatePayload;
import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.application.payload.OrderItemRevisionUpdatePayload;
import com.tmp.order.application.payload.OrderItemUpdatePayload;
import com.tmp.order.application.payload.OrderUpdatePayload;
import com.tmp.order.application.payload.PayloadAlreadyExistsException;
import com.tmp.order.application.payload.PayloadIdentity;
import com.tmp.order.application.payload.PayloadNotFoundException;
import com.tmp.order.application.payload.PayloadOptimisticLockException;
import com.tmp.order.domain.CurrencyCode;
import com.tmp.order.domain.ItemCommercialData;
import com.tmp.order.domain.OrderCommercialData;
import com.tmp.order.domain.OrderDirection;
import com.tmp.order.domain.OrderNumber;
import com.tmp.order.domain.OrderedQuantity;
import com.tmp.order.domain.PayloadRevision;
import com.tmp.order.domain.PayloadSchemaVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@link OrderDocumentPayloadPort} over typed {@code order_management} payload
 * tables (Specification §11.5). No JSON / Object / Java serialization.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcOrderDocumentPayloadAdapter implements OrderDocumentPayloadPort {

    private final JdbcTemplate jdbc;

    public JdbcOrderDocumentPayloadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<OrderDocumentPayload> findByDocumentId(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        List<PayloadIdentity> identities =
                jdbc.query(
                        """
                        SELECT document_id, document_type_code, payload_schema_version,
                               payload_revision, created_at, updated_at
                        FROM order_management.order_document_payload
                        WHERE document_id = ?
                        """,
                        (rs, rowNum) -> mapIdentity(rs),
                        documentId.value());
        if (identities.isEmpty()) {
            return Optional.empty();
        }
        PayloadIdentity identity = identities.getFirst();
        return Optional.of(loadTyped(identity));
    }

    @Override
    public void create(OrderDocumentPayload payload) {
        Objects.requireNonNull(payload, "payload");
        PayloadIdentity identity = payload.identity();
        try {
            jdbc.update(
                    """
                    INSERT INTO order_management.order_document_payload
                      (document_id, document_type_code, payload_schema_version, payload_revision,
                       created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    identity.documentId().value(),
                    identity.documentTypeCode().name(),
                    identity.schemaVersion().value(),
                    identity.payloadRevision().value(),
                    Timestamp.from(identity.createdAt()),
                    Timestamp.from(identity.updatedAt()));
        } catch (DuplicateKeyException duplicate) {
            throw new PayloadAlreadyExistsException(identity.documentId());
        }
        insertTyped(payload);
    }

    @Override
    public OrderDocumentPayload update(OrderDocumentPayload payload, PayloadRevision expectedRevision) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(expectedRevision, "expectedRevision");
        DocumentId documentId = payload.documentId();
        PayloadIdentity identity = payload.identity();

        int updated =
                jdbc.update(
                        """
                        UPDATE order_management.order_document_payload
                        SET payload_schema_version = ?, payload_revision = ?, updated_at = ?
                        WHERE document_id = ? AND payload_revision = ?
                        """,
                        identity.schemaVersion().value(),
                        identity.payloadRevision().value(),
                        Timestamp.from(identity.updatedAt()),
                        documentId.value(),
                        expectedRevision.value());
        if (updated == 0) {
            Optional<Long> actual =
                    jdbc.query(
                                    """
                                    SELECT payload_revision FROM order_management.order_document_payload
                                    WHERE document_id = ?
                                    """,
                                    (rs, rowNum) -> rs.getLong(1),
                                    documentId.value())
                            .stream()
                            .findFirst();
            if (actual.isEmpty()) {
                throw new PayloadNotFoundException(documentId);
            }
            throw new PayloadOptimisticLockException(
                    documentId, expectedRevision, PayloadRevision.of(actual.get()));
        }
        if (updated != 1) {
            throw new IllegalStateException(
                    "Expected exactly one payload metadata row updated for " + documentId);
        }
        replaceTyped(payload);
        return payload;
    }

    @Override
    public void deleteDraft(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        int deleted =
                jdbc.update(
                        "DELETE FROM order_management.order_document_payload WHERE document_id = ?",
                        documentId.value());
        if (deleted == 0) {
            throw new PayloadNotFoundException(documentId);
        }
    }

    @Override
    public boolean existsByDocumentId(DocumentId documentId) {
        Objects.requireNonNull(documentId, "documentId");
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM order_management.order_document_payload
                        WHERE document_id = ?
                        """,
                        Integer.class,
                        documentId.value());
        return count != null && count > 0;
    }

    private void insertTyped(OrderDocumentPayload payload) {
        switch (payload) {
            case OrderCreatePayload p -> insertCreate(p);
            case OrderUpdatePayload p -> insertUpdate(p);
            case OrderApprovePayload p -> insertStatus(p.documentId(), p.orderId());
            case OrderCancelPayload p -> insertStatus(p.documentId(), p.orderId());
            case OrderItemCreatePayload p -> insertItemCreate(p);
            case OrderItemUpdatePayload p -> insertItemUpdate(p);
            case OrderItemCancelPayload p -> insertItemStatus(p.documentId(), p.orderItemId());
            case OrderItemRevisionCreatePayload p -> insertRevisionCreate(p);
            case OrderItemRevisionUpdatePayload p -> insertRevisionUpdate(p);
            case OrderItemRevisionApprovePayload p -> insertRevisionApprove(p);
        }
    }

    private void replaceTyped(OrderDocumentPayload payload) {
        UUID documentId = payload.documentId().value();
        switch (payload.documentTypeCode()) {
            case ORDER_CREATE -> jdbc.update(
                    "DELETE FROM order_management.order_create_payload WHERE document_id = ?",
                    documentId);
            case ORDER_UPDATE -> jdbc.update(
                    "DELETE FROM order_management.order_update_payload WHERE document_id = ?",
                    documentId);
            case ORDER_APPROVE, ORDER_CANCEL -> jdbc.update(
                    "DELETE FROM order_management.order_status_payload WHERE document_id = ?",
                    documentId);
            case ORDER_ITEM_CREATE -> jdbc.update(
                    "DELETE FROM order_management.order_item_create_payload WHERE document_id = ?",
                    documentId);
            case ORDER_ITEM_UPDATE -> jdbc.update(
                    "DELETE FROM order_management.order_item_update_payload WHERE document_id = ?",
                    documentId);
            case ORDER_ITEM_CANCEL -> jdbc.update(
                    "DELETE FROM order_management.order_item_status_payload WHERE document_id = ?",
                    documentId);
            case ORDER_ITEM_REVISION_CREATE -> jdbc.update(
                    "DELETE FROM order_management.order_item_revision_create_payload WHERE document_id = ?",
                    documentId);
            case ORDER_ITEM_REVISION_UPDATE -> {
                jdbc.update(
                        "DELETE FROM order_management.order_item_revision_payload_line WHERE document_id = ?",
                        documentId);
                jdbc.update(
                        "DELETE FROM order_management.order_item_revision_update_payload WHERE document_id = ?",
                        documentId);
            }
            case ORDER_ITEM_REVISION_APPROVE -> jdbc.update(
                    "DELETE FROM order_management.order_item_revision_approve_payload WHERE document_id = ?",
                    documentId);
        }
        insertTyped(payload);
    }

    private OrderDocumentPayload loadTyped(PayloadIdentity identity) {
        UUID documentId = identity.documentId().value();
        return switch (identity.documentTypeCode()) {
            case ORDER_CREATE -> loadCreate(identity, documentId);
            case ORDER_UPDATE -> loadUpdate(identity, documentId);
            case ORDER_APPROVE -> loadApprove(identity, documentId);
            case ORDER_CANCEL -> loadCancel(identity, documentId);
            case ORDER_ITEM_CREATE -> loadItemCreate(identity, documentId);
            case ORDER_ITEM_UPDATE -> loadItemUpdate(identity, documentId);
            case ORDER_ITEM_CANCEL -> loadItemCancel(identity, documentId);
            case ORDER_ITEM_REVISION_CREATE -> loadRevisionCreate(identity, documentId);
            case ORDER_ITEM_REVISION_UPDATE -> loadRevisionUpdate(identity, documentId);
            case ORDER_ITEM_REVISION_APPROVE -> loadRevisionApprove(identity, documentId);
        };
    }

    private void insertCreate(OrderCreatePayload payload) {
        OrderCommercialData commercial = payload.commercialData();
        jdbc.update(
                """
                INSERT INTO order_management.order_create_payload
                  (document_id, order_number, customer_ref, customer_name, contract_ref, site_ref,
                   responsible_manager, direction, currency_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                payload.documentId().value(),
                payload.orderNumber().value(),
                commercial.customerRef(),
                commercial.customerName(),
                commercial.contractRef(),
                commercial.siteRef(),
                commercial.responsibleManager(),
                commercial.direction() == null ? null : commercial.direction().name(),
                commercial.currency() == null ? null : commercial.currency().value());
    }

    private OrderCreatePayload loadCreate(PayloadIdentity identity, UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT order_number, customer_ref, customer_name, contract_ref, site_ref,
                       responsible_manager, direction, currency_code
                FROM order_management.order_create_payload WHERE document_id = ?
                """,
                (rs, rowNum) ->
                        OrderCreatePayload.rehydrate(
                                identity,
                                OrderNumber.of(rs.getString("order_number")),
                                mapOrderCommercial(rs)),
                documentId);
    }

    private void insertUpdate(OrderUpdatePayload payload) {
        OrderCommercialData commercial = payload.commercialData();
        jdbc.update(
                """
                INSERT INTO order_management.order_update_payload
                  (document_id, order_id, customer_ref, customer_name, contract_ref, site_ref,
                   responsible_manager, direction, currency_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                payload.documentId().value(),
                payload.orderId().value(),
                commercial.customerRef(),
                commercial.customerName(),
                commercial.contractRef(),
                commercial.siteRef(),
                commercial.responsibleManager(),
                commercial.direction() == null ? null : commercial.direction().name(),
                commercial.currency() == null ? null : commercial.currency().value());
    }

    private OrderUpdatePayload loadUpdate(PayloadIdentity identity, UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT order_id, customer_ref, customer_name, contract_ref, site_ref,
                       responsible_manager, direction, currency_code
                FROM order_management.order_update_payload WHERE document_id = ?
                """,
                (rs, rowNum) ->
                        OrderUpdatePayload.rehydrate(
                                identity,
                                OrderId.of(rs.getObject("order_id", UUID.class)),
                                mapOrderCommercial(rs)),
                documentId);
    }

    private void insertStatus(DocumentId documentId, OrderId orderId) {
        jdbc.update(
                """
                INSERT INTO order_management.order_status_payload (document_id, order_id)
                VALUES (?, ?)
                """,
                documentId.value(),
                orderId.value());
    }

    private OrderApprovePayload loadApprove(PayloadIdentity identity, UUID documentId) {
        UUID orderId =
                jdbc.queryForObject(
                        "SELECT order_id FROM order_management.order_status_payload WHERE document_id = ?",
                        UUID.class,
                        documentId);
        if (orderId == null) {
            throw new IllegalStateException("Missing order_id for status payload " + documentId);
        }
        return OrderApprovePayload.rehydrate(identity, OrderId.of(orderId));
    }

    private OrderCancelPayload loadCancel(PayloadIdentity identity, UUID documentId) {
        UUID orderId =
                jdbc.queryForObject(
                        "SELECT order_id FROM order_management.order_status_payload WHERE document_id = ?",
                        UUID.class,
                        documentId);
        if (orderId == null) {
            throw new IllegalStateException("Missing order_id for status payload " + documentId);
        }
        return OrderCancelPayload.rehydrate(identity, OrderId.of(orderId));
    }

    private void insertItemCreate(OrderItemCreatePayload payload) {
        ItemCommercialData commercial = payload.commercialData();
        jdbc.update(
                """
                INSERT INTO order_management.order_item_create_payload
                  (document_id, order_id, order_item_id, product_code, item_name, comments,
                   external_position_number, ordered_quantity)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                payload.documentId().value(),
                payload.orderId().value(),
                payload.orderItemId().value(),
                commercial.productCode() == null ? null : commercial.productCode().value(),
                commercial.name(),
                commercial.comments(),
                commercial.externalPositionNumber(),
                payload.orderedQuantity().value());
    }

    private OrderItemCreatePayload loadItemCreate(PayloadIdentity identity, UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT order_id, order_item_id, product_code, item_name, comments,
                       external_position_number, ordered_quantity
                FROM order_management.order_item_create_payload WHERE document_id = ?
                """,
                (rs, rowNum) ->
                        OrderItemCreatePayload.rehydrate(
                                identity,
                                OrderId.of(rs.getObject("order_id", UUID.class)),
                                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                                mapItemCommercial(rs),
                                OrderedQuantity.of(rs.getBigDecimal("ordered_quantity"))),
                documentId);
    }

    private void insertItemUpdate(OrderItemUpdatePayload payload) {
        ItemCommercialData commercial = payload.commercialData();
        jdbc.update(
                """
                INSERT INTO order_management.order_item_update_payload
                  (document_id, order_item_id, product_code, item_name, comments,
                   external_position_number)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                payload.documentId().value(),
                payload.orderItemId().value(),
                commercial.productCode() == null ? null : commercial.productCode().value(),
                commercial.name(),
                commercial.comments(),
                commercial.externalPositionNumber());
    }

    private OrderItemUpdatePayload loadItemUpdate(PayloadIdentity identity, UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT order_item_id, product_code, item_name, comments, external_position_number
                FROM order_management.order_item_update_payload WHERE document_id = ?
                """,
                (rs, rowNum) ->
                        OrderItemUpdatePayload.rehydrate(
                                identity,
                                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                                mapItemCommercial(rs)),
                documentId);
    }

    private void insertItemStatus(DocumentId documentId, OrderItemId orderItemId) {
        jdbc.update(
                """
                INSERT INTO order_management.order_item_status_payload (document_id, order_item_id)
                VALUES (?, ?)
                """,
                documentId.value(),
                orderItemId.value());
    }

    private OrderItemCancelPayload loadItemCancel(PayloadIdentity identity, UUID documentId) {
        UUID itemId =
                jdbc.queryForObject(
                        "SELECT order_item_id FROM order_management.order_item_status_payload WHERE document_id = ?",
                        UUID.class,
                        documentId);
        if (itemId == null) {
            throw new IllegalStateException("Missing order_item_id for status payload " + documentId);
        }
        return OrderItemCancelPayload.rehydrate(identity, OrderItemId.of(itemId));
    }

    private void insertRevisionCreate(OrderItemRevisionCreatePayload payload) {
        jdbc.update(
                """
                INSERT INTO order_management.order_item_revision_create_payload
                  (document_id, order_item_id, revision_number, copy_from_revision_number)
                VALUES (?, ?, ?, ?)
                """,
                payload.documentId().value(),
                payload.orderItemId().value(),
                payload.revisionNumber().value(),
                payload.copyFromRevisionNumber() == null
                        ? null
                        : payload.copyFromRevisionNumber().value());
    }

    private OrderItemRevisionCreatePayload loadRevisionCreate(
            PayloadIdentity identity, UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT order_item_id, revision_number, copy_from_revision_number
                FROM order_management.order_item_revision_create_payload WHERE document_id = ?
                """,
                (rs, rowNum) -> {
                    Integer copyFrom = (Integer) rs.getObject("copy_from_revision_number");
                    return OrderItemRevisionCreatePayload.rehydrate(
                            identity,
                            OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                            RevisionNumber.of(rs.getInt("revision_number")),
                            copyFrom == null ? null : RevisionNumber.of(copyFrom));
                },
                documentId);
    }

    private void insertRevisionUpdate(OrderItemRevisionUpdatePayload payload) {
        jdbc.update(
                """
                INSERT INTO order_management.order_item_revision_update_payload
                  (document_id, order_item_id, revision_number, ordered_quantity)
                VALUES (?, ?, ?, ?)
                """,
                payload.documentId().value(),
                payload.orderItemId().value(),
                payload.revisionNumber().value(),
                payload.orderedQuantity().value());
        for (OrderItemRevisionPayloadLine line : payload.lines()) {
            jdbc.update(
                    """
                    INSERT INTO order_management.order_item_revision_payload_line
                      (document_id, line_number, material_code, material_name, color, length_mm,
                       line_quantity, unit_of_measure)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    payload.documentId().value(),
                    line.lineNumber(),
                    line.materialCode(),
                    line.materialName(),
                    line.color(),
                    line.lengthMm(),
                    line.lineQuantity(),
                    line.unitOfMeasure());
        }
    }

    private OrderItemRevisionUpdatePayload loadRevisionUpdate(
            PayloadIdentity identity, UUID documentId) {
        OrderItemRevisionUpdatePayload withoutLines =
                jdbc.queryForObject(
                        """
                        SELECT order_item_id, revision_number, ordered_quantity
                        FROM order_management.order_item_revision_update_payload
                        WHERE document_id = ?
                        """,
                        (rs, rowNum) ->
                                OrderItemRevisionUpdatePayload.rehydrate(
                                        identity,
                                        OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                                        RevisionNumber.of(rs.getInt("revision_number")),
                                        RevisionStatus.DRAFT,
                                        OrderedQuantity.of(rs.getBigDecimal("ordered_quantity")),
                                        List.of()),
                        documentId);
        if (withoutLines == null) {
            throw new IllegalStateException(
                    "Missing order_item_revision_update_payload for " + documentId);
        }
        List<OrderItemRevisionPayloadLine> lines =
                jdbc.query(
                        """
                        SELECT line_number, material_code, material_name, color, length_mm,
                               line_quantity, unit_of_measure
                        FROM order_management.order_item_revision_payload_line
                        WHERE document_id = ?
                        ORDER BY line_number ASC
                        """,
                        (rs, rowNum) ->
                                OrderItemRevisionPayloadLine.of(
                                        rs.getInt("line_number"),
                                        rs.getString("material_code"),
                                        rs.getString("material_name"),
                                        rs.getString("color"),
                                        rs.getBigDecimal("length_mm"),
                                        rs.getBigDecimal("line_quantity"),
                                        rs.getString("unit_of_measure")),
                        documentId);
        return OrderItemRevisionUpdatePayload.rehydrate(
                identity,
                withoutLines.orderItemId(),
                withoutLines.revisionNumber(),
                RevisionStatus.DRAFT,
                withoutLines.orderedQuantity(),
                lines);
    }

    private void insertRevisionApprove(OrderItemRevisionApprovePayload payload) {
        jdbc.update(
                """
                INSERT INTO order_management.order_item_revision_approve_payload
                  (document_id, order_item_id, revision_number)
                VALUES (?, ?, ?)
                """,
                payload.documentId().value(),
                payload.orderItemId().value(),
                payload.revisionNumber().value());
    }

    private OrderItemRevisionApprovePayload loadRevisionApprove(
            PayloadIdentity identity, UUID documentId) {
        return jdbc.queryForObject(
                """
                SELECT order_item_id, revision_number
                FROM order_management.order_item_revision_approve_payload WHERE document_id = ?
                """,
                (rs, rowNum) ->
                        OrderItemRevisionApprovePayload.rehydrate(
                                identity,
                                OrderItemId.of(rs.getObject("order_item_id", UUID.class)),
                                RevisionNumber.of(rs.getInt("revision_number"))),
                documentId);
    }

    private static PayloadIdentity mapIdentity(ResultSet rs) throws SQLException {
        return PayloadIdentity.of(
                DocumentId.of(rs.getObject("document_id", UUID.class)),
                DocumentTypeCode.valueOf(rs.getString("document_type_code")),
                PayloadSchemaVersion.of(rs.getInt("payload_schema_version")),
                PayloadRevision.of(rs.getLong("payload_revision")),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static OrderCommercialData mapOrderCommercial(ResultSet rs) throws SQLException {
        String directionCode = rs.getString("direction");
        String currencyCode = rs.getString("currency_code");
        return OrderCommercialData.of(
                rs.getString("customer_ref"),
                rs.getString("customer_name"),
                rs.getString("contract_ref"),
                rs.getString("site_ref"),
                rs.getString("responsible_manager"),
                directionCode == null ? null : OrderDirection.valueOf(directionCode),
                currencyCode == null ? null : CurrencyCode.of(currencyCode));
    }

    private static ItemCommercialData mapItemCommercial(ResultSet rs) throws SQLException {
        return ItemCommercialData.ofRaw(
                rs.getString("product_code"),
                rs.getString("item_name"),
                rs.getString("comments"),
                rs.getString("external_position_number"));
    }
}
