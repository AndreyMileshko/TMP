package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandResupplyWaiver;
import com.tmp.warehouse.domain.WarehouseDemandResupplyWaiverId;
import com.tmp.warehouse.domain.repository.WarehouseDemandResupplyWaiverRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC adapter for {@code warehouse.warehouse_demand_resupply_waivers}. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate.")
public final class JdbcWarehouseDemandResupplyWaiverRepository
        implements WarehouseDemandResupplyWaiverRepository {

    private final JdbcTemplate jdbc;

    public JdbcWarehouseDemandResupplyWaiverRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insert(WarehouseDemandResupplyWaiver waiver) {
        Objects.requireNonNull(waiver, "waiver");
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_demand_resupply_waivers (
                    id, demand_line_id, transfer_document_id, waived_quantity, waived_at, waived_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                waiver.id().value(),
                waiver.demandLineId().value(),
                waiver.transferDocumentId(),
                waiver.waivedQuantity().value(),
                Timestamp.from(waiver.waivedAt()),
                waiver.waivedBy());
    }

    @Override
    public boolean insertIfAbsent(WarehouseDemandResupplyWaiver waiver) {
        Objects.requireNonNull(waiver, "waiver");
        try {
            insert(waiver);
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public Map<WarehouseDemandLineId, BigDecimal> waivedQuantitiesByDemandLine(
            WarehouseDemandId demandId) {
        Objects.requireNonNull(demandId, "demandId");
        return waivedQuantitiesByDemandLines(List.of(demandId.value()));
    }

    @Override
    public Map<WarehouseDemandLineId, BigDecimal> waivedQuantitiesByDemandLines(
            Collection<UUID> demandIds) {
        Objects.requireNonNull(demandIds, "demandIds");
        if (demandIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(demandIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                """
                SELECT w.demand_line_id AS demand_line_id,
                       COALESCE(SUM(w.waived_quantity), 0) AS waived_quantity
                  FROM warehouse.warehouse_demand_resupply_waivers w
                  JOIN warehouse.warehouse_demand_lines dl
                    ON dl.id = w.demand_line_id
                """
                        + ("WHERE dl.demand_id IN (%s) GROUP BY w.demand_line_id")
                                .formatted(placeholders);
        Map<WarehouseDemandLineId, BigDecimal> totals = new HashMap<>();
        jdbc.query(
                sql,
                (rs) -> {
                    while (rs.next()) {
                        totals.put(
                                WarehouseDemandLineId.of((UUID) rs.getObject("demand_line_id")),
                                rs.getBigDecimal("waived_quantity"));
                    }
                    return null;
                },
                ids.toArray());
        return Map.copyOf(totals);
    }

    @Override
    public List<WarehouseDemandResupplyWaiver> findByTransferDocumentId(UUID transferDocumentId) {
        Objects.requireNonNull(transferDocumentId, "transferDocumentId");
        return jdbc.query(
                """
                SELECT id, demand_line_id, transfer_document_id, waived_quantity, waived_at, waived_by
                  FROM warehouse.warehouse_demand_resupply_waivers
                 WHERE transfer_document_id = ?
                 ORDER BY waived_at, id
                """,
                (rs, rowNum) -> mapRow(rs),
                transferDocumentId);
    }

    @Override
    public List<WarehouseDemandResupplyWaiver> findByDemandLineId(WarehouseDemandLineId demandLineId) {
        Objects.requireNonNull(demandLineId, "demandLineId");
        return jdbc.query(
                """
                SELECT id, demand_line_id, transfer_document_id, waived_quantity, waived_at, waived_by
                  FROM warehouse.warehouse_demand_resupply_waivers
                 WHERE demand_line_id = ?
                 ORDER BY waived_at, id
                """,
                (rs, rowNum) -> mapRow(rs),
                demandLineId.value());
    }

    private static WarehouseDemandResupplyWaiver mapRow(ResultSet rs) throws SQLException {
        return WarehouseDemandResupplyWaiver.of(
                WarehouseDemandResupplyWaiverId.of((UUID) rs.getObject("id")),
                WarehouseDemandLineId.of((UUID) rs.getObject("demand_line_id")),
                (UUID) rs.getObject("transfer_document_id"),
                StockQuantity.of(rs.getBigDecimal("waived_quantity")),
                rs.getTimestamp("waived_at").toInstant(),
                (UUID) rs.getObject("waived_by"));
    }
}