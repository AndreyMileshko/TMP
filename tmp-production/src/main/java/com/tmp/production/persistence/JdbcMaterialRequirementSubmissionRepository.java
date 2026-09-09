package com.tmp.production.persistence;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.repository.MaterialRequirementSubmissionRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for Material Requirement Submit traceability ({@code production.*} only).
 *
 * <p>No own transaction: every method participates in the caller's Submit transaction (ADR-036 /
 * REQUIRED) so persistence rolls back atomically with the requirement + generated documents.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcMaterialRequirementSubmissionRepository
        implements MaterialRequirementSubmissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMaterialRequirementSubmissionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void saveGeneratedDocuments(
            MaterialRequirementId requirementId, List<GeneratedDocumentLink> documents) {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(documents, "documents");
        for (GeneratedDocumentLink document : documents) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.material_requirement_generated_documents (
                        requirement_id, warehouse_document_id, source_warehouse_id,
                        destination_warehouse_id, document_order, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    requirementId.value(),
                    document.warehouseDocumentId(),
                    document.sourceWarehouseId(),
                    document.destinationWarehouseId(),
                    document.documentOrder(),
                    Timestamp.from(document.createdAt()));
        }
    }

    @Override
    public void saveRoutingSnapshot(
            MaterialRequirementId requirementId, List<RoutingSnapshotRow> snapshot) {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(snapshot, "snapshot");
        for (RoutingSnapshotRow row : snapshot) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.material_requirement_routing_snapshot (
                        requirement_id, requirement_line_id, material_reference_id,
                        source_warehouse_id, source_warehouse_code, warehouse_document_id,
                        warehouse_transfer_line_id, available_at_routing, routed_quantity,
                        uncovered_quantity)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    requirementId.value(),
                    row.requirementLineId().value(),
                    row.materialReferenceId().value(),
                    row.sourceWarehouseId(),
                    row.sourceWarehouseCode(),
                    row.warehouseDocumentId(),
                    row.warehouseTransferLineId(),
                    row.availableAtRouting(),
                    row.routedQuantity(),
                    row.uncoveredQuantity());
        }
    }

    @Override
    public List<GeneratedDocumentLink> findGeneratedDocuments(MaterialRequirementId requirementId) {
        Objects.requireNonNull(requirementId, "requirementId");
        return jdbcTemplate.query(
                """
                SELECT warehouse_document_id, source_warehouse_id, destination_warehouse_id,
                       document_order, created_at
                FROM production.material_requirement_generated_documents
                WHERE requirement_id = ?
                ORDER BY document_order
                """,
                (rs, rowNum) ->
                        new GeneratedDocumentLink(
                                rs.getObject("warehouse_document_id", UUID.class),
                                rs.getObject("source_warehouse_id", UUID.class),
                                rs.getObject("destination_warehouse_id", UUID.class),
                                rs.getInt("document_order"),
                                rs.getTimestamp("created_at").toInstant()),
                requirementId.value());
    }

    @Override
    public List<RoutingSnapshotRow> findRoutingSnapshot(MaterialRequirementId requirementId) {
        Objects.requireNonNull(requirementId, "requirementId");
        return jdbcTemplate.query(
                """
                SELECT requirement_line_id, material_reference_id, source_warehouse_id,
                       source_warehouse_code, warehouse_document_id, warehouse_transfer_line_id,
                       available_at_routing, routed_quantity, uncovered_quantity
                FROM production.material_requirement_routing_snapshot
                WHERE requirement_id = ?
                ORDER BY requirement_line_id
                """,
                (rs, rowNum) ->
                        new RoutingSnapshotRow(
                                MaterialRequirementLineId.of(
                                        rs.getObject("requirement_line_id", UUID.class)),
                                MaterialReferenceId.of(
                                        rs.getObject("material_reference_id", UUID.class)),
                                rs.getObject("source_warehouse_id", UUID.class),
                                rs.getString("source_warehouse_code"),
                                rs.getObject("warehouse_document_id", UUID.class),
                                rs.getObject("warehouse_transfer_line_id", UUID.class),
                                rs.getBigDecimal("available_at_routing"),
                                rs.getBigDecimal("routed_quantity"),
                                rs.getBigDecimal("uncovered_quantity")),
                requirementId.value());
    }
}
