package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.repository.AvailableStockAggregationQuery;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Single-query AVAILABLE aggregation for source routing (Stage 3.5.4).
 *
 * <p>Joins active warehouses and active cells; sums AVAILABLE only.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcAvailableStockAggregationQuery implements AvailableStockAggregationQuery {

    private static final RowMapper<AvailableCellStock> ROW_MAPPER =
            JdbcAvailableStockAggregationQuery::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAvailableStockAggregationQuery(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public List<AvailableCellStock> findAvailableByMaterials(
            Collection<UUID> materialReferenceIds) {
        Objects.requireNonNull(materialReferenceIds, "materialReferenceIds");
        Set<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : materialReferenceIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(distinct.size(), "?"));
        String sql =
                """
                SELECT sp.material_reference_id,
                       sp.warehouse_id,
                       w.code AS warehouse_code,
                       sp.storage_cell_id,
                       sc.code AS storage_cell_code,
                       SUM(sp.quantity) AS available_quantity
                FROM warehouse.stock_positions sp
                INNER JOIN warehouse.warehouses w
                        ON w.id = sp.warehouse_id AND w.active = TRUE
                INNER JOIN warehouse.storage_cells sc
                        ON sc.id = sp.storage_cell_id
                       AND sc.warehouse_id = sp.warehouse_id
                       AND sc.active = TRUE
                WHERE sp.stock_state = ?
                  AND sp.quantity > 0
                  AND sp.material_reference_id IN (%s)
                GROUP BY sp.material_reference_id,
                         sp.warehouse_id,
                         w.code,
                         sp.storage_cell_id,
                         sc.code
                HAVING SUM(sp.quantity) > 0
                """
                        .formatted(placeholders);

        List<Object> args = new ArrayList<>(distinct.size() + 1);
        args.add(StockState.AVAILABLE.name());
        args.addAll(distinct);
        return jdbcTemplate.query(sql, ROW_MAPPER, args.toArray());
    }

    private static AvailableCellStock mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new AvailableCellStock(
                rs.getObject("material_reference_id", UUID.class),
                rs.getObject("warehouse_id", UUID.class),
                rs.getString("warehouse_code"),
                rs.getObject("storage_cell_id", UUID.class),
                rs.getString("storage_cell_code"),
                rs.getBigDecimal("available_quantity"));
    }
}
