package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.repository.TransferDocumentOrderReferenceQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cross-schema read of Material Requirement generated-document links → Order number.
 * No schema migration; Production / Order Management own their tables.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring JdbcTemplate collaborator")
public final class JdbcTransferDocumentOrderReferenceQuery
        implements TransferDocumentOrderReferenceQuery {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTransferDocumentOrderReferenceQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Map<UUID, String> findOrderNumbersByDocumentIds(Collection<UUID> documentIds) {
        Objects.requireNonNull(documentIds, "documentIds");
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        StringBuilder placeholders = new StringBuilder();
        Object[] args = new Object[documentIds.size()];
        int i = 0;
        for (UUID documentId : documentIds) {
            Objects.requireNonNull(documentId, "documentId");
            if (i > 0) {
                placeholders.append(',');
            }
            placeholders.append('?');
            args[i++] = documentId;
        }
        String sql =
                "SELECT gd.warehouse_document_id, o.order_number"
                        + " FROM production.material_requirement_generated_documents gd"
                        + " JOIN production.material_requirements mr"
                        + " ON mr.id = gd.requirement_id"
                        + " JOIN order_management.orders o"
                        + " ON o.order_id = mr.source_order_id"
                        + " WHERE gd.warehouse_document_id IN ("
                        + placeholders
                        + ")";
        Map<UUID, String> result = new HashMap<>();
        jdbcTemplate.query(
                sql,
                rs -> {
                    result.put(
                            rs.getObject("warehouse_document_id", UUID.class),
                            rs.getString("order_number"));
                },
                args);
        return Map.copyOf(result);
    }
}
