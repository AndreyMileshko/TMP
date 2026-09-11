package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.WarehouseOperationStatus;
import com.tmp.warehouse.domain.WarehouseOperationType;
import com.tmp.warehouse.domain.repository.WarehouseHistoryReadQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * PostgreSQL history projection over completed Warehouse operations + physical movement deltas.
 *
 * <p>One user-facing row per completed operation (not per raw movement leg). MOVE destination and
 * RECEIPT/CONSUMPTION/ADJUSTMENT signed quantities are resolved via correlated movement lookups in
 * the same SQL statement (no N+1).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcWarehouseHistoryReadQuery implements WarehouseHistoryReadQuery {

    private static final RowMapper<HistoryRow> ROW_MAPPER = JdbcWarehouseHistoryReadQuery::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcWarehouseHistoryReadQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<HistoryRow> findHistory(
            Collection<UUID> warehouseIds,
            Instant fromInclusive,
            Instant toExclusive,
            String materialSearch,
            String operationType,
            int pageIndex,
            int pageSize) {
        Set<UUID> warehouses = distinctWarehouses(warehouseIds);
        if (warehouses.isEmpty()) {
            return List.of();
        }
        validatePage(pageIndex, pageSize);
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "fromInclusive must be before toExclusive: "
                            + fromInclusive
                            + " / "
                            + toExclusive);
        }

        String searchPattern = toSearchPattern(materialSearch);
        String type = normalizeOperationType(operationType);
        String placeholders = String.join(",", Collections.nCopies(warehouses.size(), "?"));

        StringBuilder sql = new StringBuilder();
        sql.append(selectProjection())
                .append(fromJoins())
                .append("WHERE wo.status = ? ")
                .append("AND wo.operation_type <> ? ")
                .append("AND wo.warehouse_id IN (")
                .append(placeholders)
                .append(") ")
                .append("AND wo.updated_at >= ? ")
                .append("AND wo.updated_at < ? ");
        if (searchPattern != null) {
            sql.append("AND (LOWER(mr.article) LIKE ? ESCAPE '\\' ")
                    .append("OR LOWER(mr.name) LIKE ? ESCAPE '\\') ");
        }
        if (type != null) {
            sql.append("AND wo.operation_type = ? ");
        }
        sql.append("ORDER BY wo.updated_at DESC, wo.id DESC ")
                .append("LIMIT ? OFFSET ?");

        List<Object> args = baseArgs(warehouses, fromInclusive, toExclusive, searchPattern, type);
        args.add(pageSize);
        args.add((long) pageIndex * (long) pageSize);
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    @Override
    public long countHistory(
            Collection<UUID> warehouseIds,
            Instant fromInclusive,
            Instant toExclusive,
            String materialSearch,
            String operationType) {
        Set<UUID> warehouses = distinctWarehouses(warehouseIds);
        if (warehouses.isEmpty()) {
            return 0L;
        }
        Objects.requireNonNull(fromInclusive, "fromInclusive");
        Objects.requireNonNull(toExclusive, "toExclusive");
        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "fromInclusive must be before toExclusive: "
                            + fromInclusive
                            + " / "
                            + toExclusive);
        }

        String searchPattern = toSearchPattern(materialSearch);
        String type = normalizeOperationType(operationType);
        String placeholders = String.join(",", Collections.nCopies(warehouses.size(), "?"));

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) ")
                .append("FROM warehouse.warehouse_operations wo ")
                .append("INNER JOIN warehouse.material_references mr ")
                .append("ON mr.id = wo.material_reference_id ")
                .append("WHERE wo.status = ? ")
                .append("AND wo.operation_type <> ? ")
                .append("AND wo.warehouse_id IN (")
                .append(placeholders)
                .append(") ")
                .append("AND wo.updated_at >= ? ")
                .append("AND wo.updated_at < ? ");
        if (searchPattern != null) {
            sql.append("AND (LOWER(mr.article) LIKE ? ESCAPE '\\' ")
                    .append("OR LOWER(mr.name) LIKE ? ESCAPE '\\') ");
        }
        if (type != null) {
            sql.append("AND wo.operation_type = ? ");
        }

        List<Object> args = baseArgs(warehouses, fromInclusive, toExclusive, searchPattern, type);
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private static String selectProjection() {
        return """
                SELECT wo.id AS entry_id,
                       wo.updated_at AS occurred_at,
                       wo.operation_type,
                       wo.material_reference_id,
                       mr.article,
                       mr.name,
                       mr.unit_of_measure,
                       CASE wo.operation_type
                         WHEN 'TRANSFER_SEND' THEN -wo.quantity
                         WHEN 'TRANSFER_RECEIVE' THEN wo.quantity
                         WHEN 'TRANSFER_RETURN' THEN wo.quantity
                         WHEN 'MOVE' THEN wo.quantity
                         ELSE COALESCE(phys.quantity_delta, wo.quantity)
                       END AS quantity,
                       CASE wo.operation_type
                         WHEN 'RECEIPT' THEN NULL
                         WHEN 'TRANSFER_RECEIVE' THEN payload.source_warehouse_id
                         ELSE wo.warehouse_id
                       END AS source_warehouse_id,
                       CASE wo.operation_type
                         WHEN 'RECEIPT' THEN NULL
                         WHEN 'TRANSFER_RECEIVE' THEN src_wh.name
                         ELSE op_wh.name
                       END AS source_warehouse_name,
                       CASE wo.operation_type
                         WHEN 'RECEIPT' THEN NULL
                         WHEN 'TRANSFER_RECEIVE' THEN NULL
                         WHEN 'TRANSFER_RETURN' THEN ret_send.source_storage_cell_id
                         ELSE wo.storage_cell_id
                       END AS source_cell_id,
                       CASE wo.operation_type
                         WHEN 'RECEIPT' THEN NULL
                         WHEN 'TRANSFER_RECEIVE' THEN NULL
                         WHEN 'TRANSFER_RETURN' THEN ret_src_cell.code
                         ELSE op_cell.code
                       END AS source_cell_code,
                       CASE wo.operation_type
                         WHEN 'CONSUMPTION' THEN NULL
                         WHEN 'ADJUSTMENT' THEN NULL
                         WHEN 'TRANSFER_SEND' THEN payload.destination_warehouse_id
                         WHEN 'MOVE' THEN wo.warehouse_id
                         ELSE wo.warehouse_id
                       END AS destination_warehouse_id,
                       CASE wo.operation_type
                         WHEN 'CONSUMPTION' THEN NULL
                         WHEN 'ADJUSTMENT' THEN NULL
                         WHEN 'TRANSFER_SEND' THEN dst_wh.name
                         WHEN 'MOVE' THEN op_wh.name
                         ELSE op_wh.name
                       END AS destination_warehouse_name,
                       CASE wo.operation_type
                         WHEN 'CONSUMPTION' THEN NULL
                         WHEN 'ADJUSTMENT' THEN NULL
                         WHEN 'TRANSFER_SEND' THEN NULL
                         WHEN 'MOVE' THEN move_dest.storage_cell_id
                         WHEN 'RECEIPT' THEN wo.storage_cell_id
                         WHEN 'TRANSFER_RECEIVE' THEN wo.storage_cell_id
                         WHEN 'TRANSFER_RETURN' THEN wo.storage_cell_id
                         ELSE wo.storage_cell_id
                       END AS destination_cell_id,
                       CASE wo.operation_type
                         WHEN 'CONSUMPTION' THEN NULL
                         WHEN 'ADJUSTMENT' THEN NULL
                         WHEN 'TRANSFER_SEND' THEN NULL
                         WHEN 'MOVE' THEN move_dest.cell_code
                         WHEN 'RECEIPT' THEN op_cell.code
                         WHEN 'TRANSFER_RECEIVE' THEN op_cell.code
                         WHEN 'TRANSFER_RETURN' THEN op_cell.code
                         ELSE op_cell.code
                       END AS destination_cell_code,
                       COALESCE(send_alloc.document_id, recv_item.document_id, ret_item.document_id)
                         AS document_id
                """;
    }

    private static String fromJoins() {
        return """
                FROM warehouse.warehouse_operations wo
                INNER JOIN warehouse.material_references mr
                  ON mr.id = wo.material_reference_id
                INNER JOIN warehouse.warehouses op_wh
                  ON op_wh.id = wo.warehouse_id
                INNER JOIN warehouse.storage_cells op_cell
                  ON op_cell.id = wo.storage_cell_id
                 AND op_cell.warehouse_id = wo.warehouse_id
                LEFT JOIN warehouse.transfer_document_send_allocation send_alloc
                  ON send_alloc.send_operation_id = wo.id
                LEFT JOIN warehouse.transfer_receipt_settlement_item recv_item
                  ON recv_item.receive_operation_id = wo.id
                LEFT JOIN warehouse.transfer_return_settlement_item ret_item
                  ON ret_item.return_operation_id = wo.id
                LEFT JOIN warehouse.transfer_document_send_allocation ret_send
                  ON ret_send.id = ret_item.send_allocation_id
                 AND ret_send.document_id = ret_item.document_id
                LEFT JOIN warehouse.storage_cells ret_src_cell
                  ON ret_src_cell.id = ret_send.source_storage_cell_id
                LEFT JOIN warehouse.transfer_document_payload payload
                  ON payload.document_id = COALESCE(
                       send_alloc.document_id, recv_item.document_id, ret_item.document_id)
                LEFT JOIN warehouse.warehouses src_wh
                  ON src_wh.id = payload.source_warehouse_id
                LEFT JOIN warehouse.warehouses dst_wh
                  ON dst_wh.id = payload.destination_warehouse_id
                LEFT JOIN LATERAL (
                  SELECT wm.quantity_delta
                  FROM warehouse.warehouse_movements wm
                  INNER JOIN warehouse.stock_positions sp ON sp.id = wm.stock_position_id
                  WHERE sp.warehouse_id = wo.warehouse_id
                    AND sp.storage_cell_id = wo.storage_cell_id
                    AND sp.material_reference_id = wo.material_reference_id
                    AND wm.operation_type = wo.operation_type
                    AND wm.created_at >= wo.created_at
                    AND wm.created_at <= wo.updated_at + INTERVAL '5 seconds'
                  ORDER BY wm.id
                  LIMIT 1
                ) phys ON TRUE
                LEFT JOIN LATERAL (
                  SELECT sp.storage_cell_id, sc.code AS cell_code
                  FROM warehouse.warehouse_movements wm
                  INNER JOIN warehouse.stock_positions sp ON sp.id = wm.stock_position_id
                  INNER JOIN warehouse.storage_cells sc
                    ON sc.id = sp.storage_cell_id AND sc.warehouse_id = sp.warehouse_id
                  WHERE wo.operation_type = 'MOVE'
                    AND sp.warehouse_id = wo.warehouse_id
                    AND sp.material_reference_id = wo.material_reference_id
                    AND sp.storage_cell_id <> wo.storage_cell_id
                    AND wm.operation_type = 'MOVE'
                    AND wm.quantity_delta > 0
                    AND wm.created_at >= wo.created_at
                    AND wm.created_at <= wo.updated_at + INTERVAL '5 seconds'
                  ORDER BY wm.id
                  LIMIT 1
                ) move_dest ON TRUE
                """;
    }

    private static List<Object> baseArgs(
            Set<UUID> warehouses,
            Instant fromInclusive,
            Instant toExclusive,
            String searchPattern,
            String type) {
        List<Object> args = new ArrayList<>();
        args.add(WarehouseOperationStatus.COMPLETED.name());
        args.add(WarehouseOperationType.RESERVATION.name());
        args.addAll(warehouses);
        args.add(Timestamp.from(fromInclusive));
        args.add(Timestamp.from(toExclusive));
        if (searchPattern != null) {
            args.add(searchPattern);
            args.add(searchPattern);
        }
        if (type != null) {
            args.add(type);
        }
        return args;
    }

    private static void validatePage(int pageIndex, int pageSize) {
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be >= 0: " + pageIndex);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1: " + pageSize);
        }
    }

    private static Set<UUID> distinctWarehouses(Collection<UUID> warehouseIds) {
        if (warehouseIds == null || warehouseIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : warehouseIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        return distinct;
    }

    private static String toSearchPattern(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String escaped =
                trimmed.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String normalizeOperationType(String operationType) {
        if (operationType == null) {
            return null;
        }
        String trimmed = operationType.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        WarehouseOperationType.valueOf(trimmed);
        if (WarehouseOperationType.RESERVATION.name().equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static HistoryRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp occurred = rs.getTimestamp("occurred_at");
        return new HistoryRow(
                (UUID) rs.getObject("entry_id"),
                occurred.toInstant(),
                rs.getString("operation_type"),
                (UUID) rs.getObject("material_reference_id"),
                rs.getString("article"),
                rs.getString("name"),
                rs.getString("unit_of_measure"),
                rs.getBigDecimal("quantity"),
                (UUID) rs.getObject("source_warehouse_id"),
                rs.getString("source_warehouse_name"),
                (UUID) rs.getObject("source_cell_id"),
                rs.getString("source_cell_code"),
                (UUID) rs.getObject("destination_warehouse_id"),
                rs.getString("destination_warehouse_name"),
                (UUID) rs.getObject("destination_cell_id"),
                rs.getString("destination_cell_code"),
                (UUID) rs.getObject("document_id"));
    }
}
