package com.tmp.bootstrap.warehouse;

import com.tmp.warehouse.api.TransferDocumentOrderReferenceQuery;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Composition-boundary read: Warehouse Transfer / Demand → Material Requirement → Order number(s).
 *
 * <p>Cross-schema joins are allowed here; Warehouse persistence must not query Order Management
 * tables directly.
 *
 * <p>Paths:
 *
 * <ul>
 *   <li>Transfer document via historical {@code material_requirement_generated_documents}
 *   <li>Transfer document via Demand transfer links ({@code warehouse_demand_transfer_links})
 *   <li>Demand supply task via {@code warehouse_demands.source_material_requirement_id}
 * </ul>
 *
 * <p>Order numbers come from {@code material_requirement_source_items} (supports cross-order MR).
 */
public final class CompositionTransferDocumentOrderReferenceQuery
        implements TransferDocumentOrderReferenceQuery {

    private final JdbcTemplate jdbcTemplate;

    public CompositionTransferDocumentOrderReferenceQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Map<UUID, String> findOrderNumbersByDocumentIds(Collection<UUID> documentIds) {
        Objects.requireNonNull(documentIds, "documentIds");
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(documentIds);
        Map<UUID, Set<String>> collected = new LinkedHashMap<>();
        for (UUID documentId : ids) {
            Objects.requireNonNull(documentId, "documentId");
            collected.put(documentId, new TreeSet<>());
        }
        collectFromGeneratedDocuments(ids, collected);
        collectFromDemandTransferLinks(ids, collected);
        return compose(collected);
    }

    @Override
    public Map<UUID, String> findOrderNumbersByDemandIds(Collection<UUID> demandIds) {
        Objects.requireNonNull(demandIds, "demandIds");
        if (demandIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(demandIds);
        Map<UUID, Set<String>> collected = new LinkedHashMap<>();
        for (UUID demandId : ids) {
            Objects.requireNonNull(demandId, "demandId");
            collected.put(demandId, new TreeSet<>());
        }
        String sql =
                "SELECT d.id AS demand_id, o.order_number"
                        + " FROM warehouse.warehouse_demands d"
                        + " JOIN production.material_requirement_source_items si"
                        + " ON si.requirement_id = d.source_material_requirement_id"
                        + " JOIN order_management.orders o"
                        + " ON o.order_id = si.source_order_id"
                        + " WHERE d.id IN ("
                        + placeholders(ids.size())
                        + ")";
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID demandId = rs.getObject("demand_id", UUID.class);
                    String orderNumber = rs.getString("order_number");
                    addOrderNumber(collected, demandId, orderNumber);
                },
                ids.toArray());
        return compose(collected);
    }

    private void collectFromGeneratedDocuments(
            List<UUID> documentIds, Map<UUID, Set<String>> collected) {
        String sql =
                "SELECT gd.warehouse_document_id, o.order_number"
                        + " FROM production.material_requirement_generated_documents gd"
                        + " JOIN production.material_requirement_source_items si"
                        + " ON si.requirement_id = gd.requirement_id"
                        + " JOIN order_management.orders o"
                        + " ON o.order_id = si.source_order_id"
                        + " WHERE gd.warehouse_document_id IN ("
                        + placeholders(documentIds.size())
                        + ")";
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID documentId = rs.getObject("warehouse_document_id", UUID.class);
                    String orderNumber = rs.getString("order_number");
                    addOrderNumber(collected, documentId, orderNumber);
                },
                documentIds.toArray());
    }

    private void collectFromDemandTransferLinks(
            List<UUID> documentIds, Map<UUID, Set<String>> collected) {
        String sql =
                "SELECT link.transfer_document_id, o.order_number"
                        + " FROM warehouse.warehouse_demand_transfer_links link"
                        + " JOIN warehouse.warehouse_demand_lines dl"
                        + " ON dl.id = link.demand_line_id"
                        + " JOIN warehouse.warehouse_demands d"
                        + " ON d.id = dl.demand_id"
                        + " JOIN production.material_requirement_source_items si"
                        + " ON si.requirement_id = d.source_material_requirement_id"
                        + " JOIN order_management.orders o"
                        + " ON o.order_id = si.source_order_id"
                        + " WHERE link.transfer_document_id IN ("
                        + placeholders(documentIds.size())
                        + ")";
        jdbcTemplate.query(
                sql,
                rs -> {
                    UUID documentId = rs.getObject("transfer_document_id", UUID.class);
                    String orderNumber = rs.getString("order_number");
                    addOrderNumber(collected, documentId, orderNumber);
                },
                documentIds.toArray());
    }

    private static void addOrderNumber(
            Map<UUID, Set<String>> collected, UUID key, String orderNumber) {
        if (key == null || orderNumber == null) {
            return;
        }
        String trimmed = orderNumber.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        Set<String> numbers = collected.get(key);
        if (numbers != null) {
            numbers.add(trimmed);
        }
    }

    private static Map<UUID, String> compose(Map<UUID, Set<String>> collected) {
        Map<UUID, String> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : collected.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            result.put(entry.getKey(), String.join(", ", entry.getValue()));
        }
        return Map.copyOf(result);
    }

    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('?');
        }
        return builder.toString();
    }
}
