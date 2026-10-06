package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.WarehouseDemand;
import com.tmp.warehouse.domain.WarehouseDemandId;
import com.tmp.warehouse.domain.WarehouseDemandLine;
import com.tmp.warehouse.domain.WarehouseDemandLineId;
import com.tmp.warehouse.domain.WarehouseDemandTransferLink;
import com.tmp.warehouse.domain.WarehouseDemandTransferLinkId;
import com.tmp.warehouse.domain.WarehouseDemandWaitingReason;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseTransferLineId;
import com.tmp.warehouse.domain.repository.WarehouseDemandRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC adapter for {@code warehouse.warehouse_demands} and related tables. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate.")
public final class JdbcWarehouseDemandRepository implements WarehouseDemandRepository {

    private final JdbcTemplate jdbc;

    public JdbcWarehouseDemandRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public void insert(WarehouseDemand demand) {
        Objects.requireNonNull(demand, "demand");
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_demands (
                    id, source_material_requirement_id, destination_warehouse_id,
                    accepted_at, accepted_by, cancelled_at, cancelled_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                demand.id().value(),
                demand.sourceMaterialRequirementId(),
                demand.destinationWarehouseId().value(),
                Timestamp.from(demand.acceptedAt()),
                demand.acceptedBy().orElse(null),
                demand.cancelledAt().map(Timestamp::from).orElse(null),
                demand.cancelledBy().orElse(null),
                demand.version());
        for (WarehouseDemandLine line : demand.lines()) {
            jdbc.update(
                    """
                    INSERT INTO warehouse.warehouse_demand_lines (
                        id, demand_id, source_material_requirement_line_id,
                        material_code, material_name, color, unit_of_measure, length_mm,
                        required_quantity, material_reference_id, waiting_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    line.id().value(),
                    demand.id().value(),
                    line.sourceMaterialRequirementLineId(),
                    line.materialCode(),
                    line.materialName(),
                    line.color(),
                    line.unitOfMeasure(),
                    line.lengthMm().orElse(null),
                    line.requiredQuantity().value(),
                    line.materialReferenceId().map(MaterialReferenceId::value).orElse(null),
                    line.waitingReason().map(Enum::name).orElse(null));
        }
    }

    @Override
    public Optional<WarehouseDemand> findById(WarehouseDemandId demandId) {
        Objects.requireNonNull(demandId, "demandId");
        return loadHeader(
                        """
                        SELECT id, source_material_requirement_id, destination_warehouse_id,
                               accepted_at, accepted_by, cancelled_at, cancelled_by, version
                          FROM warehouse.warehouse_demands
                         WHERE id = ?
                        """,
                        demandId.value())
                .map(this::attachLines);
    }

    @Override
    public Optional<WarehouseDemand> findBySourceMaterialRequirementId(
            UUID sourceMaterialRequirementId) {
        Objects.requireNonNull(sourceMaterialRequirementId, "sourceMaterialRequirementId");
        return loadHeader(
                        """
                        SELECT id, source_material_requirement_id, destination_warehouse_id,
                               accepted_at, accepted_by, cancelled_at, cancelled_by, version
                          FROM warehouse.warehouse_demands
                         WHERE source_material_requirement_id = ?
                        """,
                        sourceMaterialRequirementId)
                .map(this::attachLines);
    }

    @Override
    public Optional<WarehouseDemand> lockById(WarehouseDemandId demandId) {
        Objects.requireNonNull(demandId, "demandId");
        return loadHeader(
                        """
                        SELECT id, source_material_requirement_id, destination_warehouse_id,
                               accepted_at, accepted_by, cancelled_at, cancelled_by, version
                          FROM warehouse.warehouse_demands
                         WHERE id = ?
                         FOR UPDATE
                        """,
                        demandId.value())
                .map(this::attachLines);
    }

    @Override
    public void insertTransferLink(WarehouseDemandTransferLink link) {
        Objects.requireNonNull(link, "link");
        jdbc.update(
                """
                INSERT INTO warehouse.warehouse_demand_transfer_links (
                    id, demand_line_id, transfer_document_id, transfer_line_id, linked_quantity)
                VALUES (?, ?, ?, ?, ?)
                """,
                link.id().value(),
                link.demandLineId().value(),
                link.transferDocumentId(),
                link.transferLineId().value(),
                link.linkedQuantity().value());
    }

    @Override
    public boolean insertTransferLinkIfAbsent(WarehouseDemandTransferLink link) {
        Objects.requireNonNull(link, "link");
        if (findTransferLinkByTransferLineId(link.transferLineId()).isPresent()) {
            return false;
        }
        int inserted =
                jdbc.update(
                        """
                        INSERT INTO warehouse.warehouse_demand_transfer_links (
                            id, demand_line_id, transfer_document_id, transfer_line_id, linked_quantity)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (transfer_line_id) DO NOTHING
                        """,
                        link.id().value(),
                        link.demandLineId().value(),
                        link.transferDocumentId(),
                        link.transferLineId().value(),
                        link.linkedQuantity().value());
        return inserted > 0;
    }

    @Override
    public List<WarehouseDemandTransferLink> findTransferLinksByDemandLineId(
            WarehouseDemandLineId demandLineId) {
        Objects.requireNonNull(demandLineId, "demandLineId");
        return jdbc.query(
                """
                SELECT id, demand_line_id, transfer_document_id, transfer_line_id, linked_quantity
                  FROM warehouse.warehouse_demand_transfer_links
                 WHERE demand_line_id = ?
                 ORDER BY id
                """,
                (rs, rowNum) -> mapLink(rs),
                demandLineId.value());
    }

    @Override
    public List<WarehouseDemandTransferLink> findTransferLinksByDemandId(WarehouseDemandId demandId) {
        Objects.requireNonNull(demandId, "demandId");
        return jdbc.query(
                """
                SELECT l.id, l.demand_line_id, l.transfer_document_id, l.transfer_line_id,
                       l.linked_quantity
                  FROM warehouse.warehouse_demand_transfer_links l
                  JOIN warehouse.warehouse_demand_lines dl ON dl.id = l.demand_line_id
                 WHERE dl.demand_id = ?
                 ORDER BY l.id
                """,
                (rs, rowNum) -> mapLink(rs),
                demandId.value());
    }

    @Override
    public Optional<WarehouseDemandTransferLink> findTransferLinkByTransferLineId(
            WarehouseTransferLineId transferLineId) {
        Objects.requireNonNull(transferLineId, "transferLineId");
        List<WarehouseDemandTransferLink> rows =
                jdbc.query(
                        """
                        SELECT id, demand_line_id, transfer_document_id, transfer_line_id,
                               linked_quantity
                          FROM warehouse.warehouse_demand_transfer_links
                         WHERE transfer_line_id = ?
                        """,
                        (rs, rowNum) -> mapLink(rs),
                        transferLineId.value());
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private Optional<HeaderRow> loadHeader(String sql, Object id) {
        List<HeaderRow> rows = jdbc.query(sql, (rs, rowNum) -> mapHeader(rs), id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.get(0));
    }

    private WarehouseDemand attachLines(HeaderRow header) {
        List<WarehouseDemandLine> lines =
                jdbc.query(
                        """
                        SELECT id, source_material_requirement_line_id,
                               material_code, material_name, color, unit_of_measure, length_mm,
                               required_quantity, material_reference_id, waiting_reason
                          FROM warehouse.warehouse_demand_lines
                         WHERE demand_id = ?
                         ORDER BY id
                        """,
                        (rs, rowNum) -> mapLine(rs),
                        header.id());
        return WarehouseDemand.of(
                WarehouseDemandId.of(header.id()),
                header.sourceMaterialRequirementId(),
                WarehouseId.of(header.destinationWarehouseId()),
                header.acceptedAt(),
                header.acceptedBy(),
                header.cancelledAt(),
                header.cancelledBy(),
                header.version(),
                lines);
    }

    private static HeaderRow mapHeader(ResultSet rs) throws SQLException {
        Timestamp cancelledTs = rs.getTimestamp("cancelled_at");
        return new HeaderRow(
                (UUID) rs.getObject("id"),
                (UUID) rs.getObject("source_material_requirement_id"),
                (UUID) rs.getObject("destination_warehouse_id"),
                rs.getTimestamp("accepted_at").toInstant(),
                rs.getString("accepted_by"),
                cancelledTs == null ? null : cancelledTs.toInstant(),
                rs.getString("cancelled_by"),
                rs.getLong("version"));
    }

    private static WarehouseDemandLine mapLine(ResultSet rs) throws SQLException {
        UUID materialRef = (UUID) rs.getObject("material_reference_id");
        String waiting = rs.getString("waiting_reason");
        BigDecimal lengthMm = rs.getBigDecimal("length_mm");
        return WarehouseDemandLine.of(
                WarehouseDemandLineId.of((UUID) rs.getObject("id")),
                (UUID) rs.getObject("source_material_requirement_line_id"),
                rs.getString("material_code"),
                rs.getString("material_name"),
                rs.getString("color"),
                rs.getString("unit_of_measure"),
                lengthMm,
                StockQuantity.of(rs.getBigDecimal("required_quantity")),
                materialRef == null ? null : MaterialReferenceId.of(materialRef),
                waiting == null ? null : WarehouseDemandWaitingReason.valueOf(waiting));
    }

    private static WarehouseDemandTransferLink mapLink(ResultSet rs) throws SQLException {
        return WarehouseDemandTransferLink.of(
                WarehouseDemandTransferLinkId.of((UUID) rs.getObject("id")),
                WarehouseDemandLineId.of((UUID) rs.getObject("demand_line_id")),
                (UUID) rs.getObject("transfer_document_id"),
                WarehouseTransferLineId.of((UUID) rs.getObject("transfer_line_id")),
                StockQuantity.of(rs.getBigDecimal("linked_quantity")));
    }

    private record HeaderRow(
            UUID id,
            UUID sourceMaterialRequirementId,
            UUID destinationWarehouseId,
            Instant acceptedAt,
            String acceptedBy,
            Instant cancelledAt,
            String cancelledBy,
            long version) {}
}
