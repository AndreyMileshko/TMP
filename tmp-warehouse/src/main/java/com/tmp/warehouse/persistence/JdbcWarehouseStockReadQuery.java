package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.repository.WarehouseStockReadQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
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
 * PostgreSQL aggregation for modern Остатки: GROUP BY warehouse+material, JOIN material metadata,
 * search and pagination in SQL.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcWarehouseStockReadQuery implements WarehouseStockReadQuery {

    private static final RowMapper<StockSummaryRow> SUMMARY_MAPPER =
            JdbcWarehouseStockReadQuery::mapSummary;
    private static final RowMapper<StockCellRow> CELL_MAPPER = JdbcWarehouseStockReadQuery::mapCell;

    private final JdbcTemplate jdbcTemplate;

    public JdbcWarehouseStockReadQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<StockSummaryRow> findSummaries(
            Collection<UUID> warehouseIds, String search, int pageIndex, int pageSize) {
        Set<UUID> warehouses = distinctWarehouses(warehouseIds);
        if (warehouses.isEmpty()) {
            return List.of();
        }
        if (pageIndex < 0) {
            throw new IllegalArgumentException("pageIndex must be >= 0: " + pageIndex);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("pageSize must be >= 1: " + pageSize);
        }

        String searchPattern = toSearchPattern(search);
        String placeholders = String.join(",", Collections.nCopies(warehouses.size(), "?"));
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT sp.warehouse_id, ")
                .append("sp.material_reference_id, ")
                .append("mr.article, ")
                .append("mr.name, ")
                .append("mr.color, ")
                .append("mr.size, ")
                .append("mr.unit_of_measure, ")
                .append("SUM(sp.quantity) AS available_quantity ")
                .append("FROM warehouse.stock_positions sp ")
                .append("INNER JOIN warehouse.material_references mr ")
                .append("ON mr.id = sp.material_reference_id ")
                .append("INNER JOIN warehouse.warehouses w ")
                .append("ON w.id = sp.warehouse_id AND w.active = TRUE ")
                .append("INNER JOIN warehouse.storage_cells sc ")
                .append("ON sc.id = sp.storage_cell_id ")
                .append("AND sc.warehouse_id = sp.warehouse_id ")
                .append("AND sc.active = TRUE ")
                .append("WHERE sp.stock_state = ? ")
                .append("AND sp.quantity > 0 ")
                .append("AND sp.warehouse_id IN (")
                .append(placeholders)
                .append(") ");
        if (searchPattern != null) {
            sql.append("AND (LOWER(mr.article) LIKE ? ESCAPE '\\' ")
                    .append("OR LOWER(mr.name) LIKE ? ESCAPE '\\') ");
        }
        sql.append("GROUP BY sp.warehouse_id, ")
                .append("sp.material_reference_id, ")
                .append("mr.article, ")
                .append("mr.name, ")
                .append("mr.color, ")
                .append("mr.size, ")
                .append("mr.unit_of_measure ")
                .append("HAVING SUM(sp.quantity) > 0 ")
                .append("ORDER BY mr.article ASC, ")
                .append("sp.material_reference_id ASC, ")
                .append("sp.warehouse_id ASC ")
                .append("LIMIT ? OFFSET ?");

        List<Object> args = new ArrayList<>();
        args.add(StockState.AVAILABLE.name());
        args.addAll(warehouses);
        if (searchPattern != null) {
            args.add(searchPattern);
            args.add(searchPattern);
        }
        args.add(pageSize);
        args.add((long) pageIndex * (long) pageSize);
        return jdbcTemplate.query(sql.toString(), SUMMARY_MAPPER, args.toArray());
    }

    @Override
    public long countSummaries(Collection<UUID> warehouseIds, String search) {
        Set<UUID> warehouses = distinctWarehouses(warehouseIds);
        if (warehouses.isEmpty()) {
            return 0L;
        }
        String searchPattern = toSearchPattern(search);
        String placeholders = String.join(",", Collections.nCopies(warehouses.size(), "?"));
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT COUNT(*) FROM (")
                .append("SELECT sp.warehouse_id, sp.material_reference_id ")
                .append("FROM warehouse.stock_positions sp ")
                .append("INNER JOIN warehouse.material_references mr ")
                .append("ON mr.id = sp.material_reference_id ")
                .append("INNER JOIN warehouse.warehouses w ")
                .append("ON w.id = sp.warehouse_id AND w.active = TRUE ")
                .append("INNER JOIN warehouse.storage_cells sc ")
                .append("ON sc.id = sp.storage_cell_id ")
                .append("AND sc.warehouse_id = sp.warehouse_id ")
                .append("AND sc.active = TRUE ")
                .append("WHERE sp.stock_state = ? ")
                .append("AND sp.quantity > 0 ")
                .append("AND sp.warehouse_id IN (")
                .append(placeholders)
                .append(") ");
        if (searchPattern != null) {
            sql.append("AND (LOWER(mr.article) LIKE ? ESCAPE '\\' ")
                    .append("OR LOWER(mr.name) LIKE ? ESCAPE '\\') ");
        }
        sql.append("GROUP BY sp.warehouse_id, sp.material_reference_id ")
                .append("HAVING SUM(sp.quantity) > 0")
                .append(") counted");

        List<Object> args = new ArrayList<>();
        args.add(StockState.AVAILABLE.name());
        args.addAll(warehouses);
        if (searchPattern != null) {
            args.add(searchPattern);
            args.add(searchPattern);
        }
        Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    @Override
    public List<StockCellRow> findCellBreakdown(UUID warehouseId, UUID materialReferenceId) {
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        String sql =
                "SELECT sp.storage_cell_id, "
                        + "sc.code AS storage_cell_code, "
                        + "SUM(sp.quantity) AS available_quantity "
                        + "FROM warehouse.stock_positions sp "
                        + "INNER JOIN warehouse.storage_cells sc "
                        + "ON sc.id = sp.storage_cell_id "
                        + "AND sc.warehouse_id = sp.warehouse_id "
                        + "AND sc.active = TRUE "
                        + "INNER JOIN warehouse.warehouses w "
                        + "ON w.id = sp.warehouse_id AND w.active = TRUE "
                        + "WHERE sp.stock_state = ? "
                        + "AND sp.quantity > 0 "
                        + "AND sp.warehouse_id = ? "
                        + "AND sp.material_reference_id = ? "
                        + "GROUP BY sp.storage_cell_id, sc.code "
                        + "HAVING SUM(sp.quantity) > 0 "
                        + "ORDER BY sc.code ASC, sp.storage_cell_id ASC";
        return jdbcTemplate.query(
                sql,
                CELL_MAPPER,
                StockState.AVAILABLE.name(),
                warehouseId,
                materialReferenceId);
    }

    private static Set<UUID> distinctWarehouses(Collection<UUID> warehouseIds) {
        Objects.requireNonNull(warehouseIds, "warehouseIds");
        Set<UUID> distinct = new LinkedHashSet<>();
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
        return "%" + escapeLike(trimmed.toLowerCase(Locale.ROOT)) + "%";
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static StockSummaryRow mapSummary(ResultSet rs, int rowNum) throws SQLException {
        return new StockSummaryRow(
                rs.getObject("warehouse_id", UUID.class),
                rs.getObject("material_reference_id", UUID.class),
                rs.getString("article"),
                rs.getString("name"),
                nullToEmpty(rs.getString("color")),
                nullToEmpty(rs.getString("size")),
                nullToEmpty(rs.getString("unit_of_measure")),
                rs.getBigDecimal("available_quantity"));
    }

    private static StockCellRow mapCell(ResultSet rs, int rowNum) throws SQLException {
        return new StockCellRow(
                rs.getObject("storage_cell_id", UUID.class),
                rs.getString("storage_cell_code"),
                rs.getBigDecimal("available_quantity"));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
