package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.WarehouseDemandActiveTransferRules;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseId;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Read-side derivation support for Warehouse Demand fulfillment (B3B-3A).
 *
 * <p>Authoritative received quantity fact: {@code warehouse.transfer_receipt_settlement_item}
 * quantities for send allocations of Demand-linked Transfer lines, restricted to receipts whose
 * Transfer destination equals the Demand destination warehouse. Return settlement items are never
 * counted.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate.")
public final class JdbcWarehouseDemandFulfillmentReadQuery {

    private final JdbcTemplate jdbc;

    public JdbcWarehouseDemandFulfillmentReadQuery(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    /**
     * Sum of successful settled receipt quantities per Demand line at the Demand destination
     * warehouse.
     */
    public Map<WarehouseDemandLineId, BigDecimal> receivedQuantitiesByDemandLine(
            WarehouseDemandId demandId, WarehouseId destinationWarehouseId) {
        Objects.requireNonNull(demandId, "demandId");
        Objects.requireNonNull(destinationWarehouseId, "destinationWarehouseId");
        return receivedQuantitiesByDemandLines(List.of(demandId.value()));
    }

    /**
     * Batch received quantities for many Demands (B3B-3C1 inbox). Keys are Demand line ids.
     * Destination filter uses each Demand's own destination warehouse.
     */
    public Map<WarehouseDemandLineId, BigDecimal> receivedQuantitiesByDemandLines(
            Collection<UUID> demandIds) {
        Objects.requireNonNull(demandIds, "demandIds");
        if (demandIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = List.copyOf(demandIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                ("""
                SELECT dtl.demand_line_id AS demand_line_id,
                       COALESCE(SUM(ri.quantity), 0) AS received_quantity
                  FROM warehouse.warehouse_demand_transfer_links dtl
                  JOIN warehouse.warehouse_demand_lines dl
                    ON dl.id = dtl.demand_line_id
                  JOIN warehouse.warehouse_demands wd
                    ON wd.id = dl.demand_id
                  JOIN warehouse.transfer_document_payload tdp
                    ON tdp.document_id = dtl.transfer_document_id
                  JOIN warehouse.transfer_document_send_allocation sa
                    ON sa.line_id = dtl.transfer_line_id
                  JOIN warehouse.transfer_receipt_settlement_item ri
                    ON ri.send_allocation_id = sa.id
                 WHERE dl.demand_id IN (%s)
                   AND tdp.destination_warehouse_id = wd.destination_warehouse_id
                 GROUP BY dtl.demand_line_id
                """)
                        .formatted(placeholders);
        Map<WarehouseDemandLineId, BigDecimal> totals = new HashMap<>();
        jdbc.query(
                sql,
                (rs) -> {
                    while (rs.next()) {
                        totals.put(
                                WarehouseDemandLineId.of((UUID) rs.getObject("demand_line_id")),
                                rs.getBigDecimal("received_quantity"));
                    }
                    return null;
                },
                ids.toArray());
        return Map.copyOf(totals);
    }

    /** Demand lines that have at least one ACTIVE linked Transfer covering open obligation. */
    public Set<WarehouseDemandLineId> demandLinesWithActiveTransfer(WarehouseDemandId demandId) {
        Objects.requireNonNull(demandId, "demandId");
        return demandLinesWithActiveTransfer(List.of(demandId.value()));
    }

    /** Batch ACTIVE linked Transfer lines for many Demands (B3B-3C1 inbox). */
    public Set<WarehouseDemandLineId> demandLinesWithActiveTransfer(Collection<UUID> demandIds) {
        Objects.requireNonNull(demandIds, "demandIds");
        if (demandIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> ids = List.copyOf(demandIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql =
                ("""
                SELECT dtl.demand_line_id AS demand_line_id,
                       d.status AS document_status,
                       s.settlement_state AS settlement_state
                  FROM warehouse.warehouse_demand_transfer_links dtl
                  JOIN warehouse.warehouse_demand_lines dl
                    ON dl.id = dtl.demand_line_id
                  JOIN documents.documents d
                    ON d.id = dtl.transfer_document_id
                  LEFT JOIN warehouse.transfer_document_settlement s
                    ON s.document_id = dtl.transfer_document_id
                 WHERE dl.demand_id IN (%s)
                """)
                        .formatted(placeholders);
        Set<WarehouseDemandLineId> active = new HashSet<>();
        List<ActiveLinkRow> rows =
                jdbc.query(
                        sql,
                        (rs, rowNum) ->
                                new ActiveLinkRow(
                                        (UUID) rs.getObject("demand_line_id"),
                                        rs.getString("document_status"),
                                        rs.getString("settlement_state")),
                        ids.toArray());
        for (ActiveLinkRow row : rows) {
            if (WarehouseDemandActiveTransferRules.isActive(
                    row.documentStatus(), row.settlementState())) {
                active.add(WarehouseDemandLineId.of(row.demandLineId()));
            }
        }
        return Set.copyOf(active);
    }

    private record ActiveLinkRow(UUID demandLineId, String documentStatus, String settlementState) {}
}
