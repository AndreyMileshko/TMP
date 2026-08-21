package com.tmp.production.persistence;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.ProductionMaterialTransfer;
import com.tmp.production.domain.ProductionMaterialTransferId;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.WarehouseTransferOperationRef;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC adapter for Production-owned logical material transfers ({@code production.*} only).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and TransactionTemplate.")
public final class JdbcProductionMaterialTransferRepository
        implements ProductionMaterialTransferRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public JdbcProductionMaterialTransferRepository(
            JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public ProductionMaterialTransfer save(ProductionMaterialTransfer transfer) {
        Objects.requireNonNull(transfer, "transfer");
        return transactionTemplate.execute(
                status -> {
                    if (findHeader(transfer.logicalTransferId()).isPresent()) {
                        throw new IllegalStateException(
                                "Production material transfer is immutable after create: "
                                        + transfer.logicalTransferId());
                    }
                    insert(transfer);
                    return findById(transfer.logicalTransferId()).orElseThrow();
                });
    }

    @Override
    public Optional<ProductionMaterialTransfer> findById(
            ProductionMaterialTransferId logicalTransferId) {
        Objects.requireNonNull(logicalTransferId, "logicalTransferId");
        Optional<HeaderRow> header = findHeader(logicalTransferId);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        HeaderRow row = header.orElseThrow();
        return Optional.of(
                ProductionMaterialTransfer.rehydrate(
                        logicalTransferId,
                        MaterialTransferTemplateId.of(row.templateId()),
                        SourceOrderId.of(row.sourceOrderId()),
                        row.createdAt(),
                        loadRefs(logicalTransferId)));
    }

    @Override
    public Optional<ProductionMaterialTransfer> findByTemplateId(
            MaterialTransferTemplateId templateId) {
        Objects.requireNonNull(templateId, "templateId");
        try {
            UUID id =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id FROM production.material_transfers
                            WHERE template_id = ?
                            """,
                            UUID.class,
                            templateId.value());
            if (id == null) {
                return Optional.empty();
            }
            return findById(ProductionMaterialTransferId.of(id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<ProductionMaterialTransfer> findBySourceOrderId(SourceOrderId sourceOrderId) {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        List<UUID> ids =
                jdbcTemplate.query(
                        """
                        SELECT id FROM production.material_transfers
                        WHERE source_order_id = ?
                        ORDER BY created_at, id
                        """,
                        (rs, rowNum) -> rs.getObject("id", UUID.class),
                        sourceOrderId.value());
        List<ProductionMaterialTransfer> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            findById(ProductionMaterialTransferId.of(id)).ifPresent(result::add);
        }
        return result;
    }

    private void insert(ProductionMaterialTransfer transfer) {
        jdbcTemplate.update(
                """
                INSERT INTO production.material_transfers (
                    id, template_id, source_order_id, created_at)
                VALUES (?, ?, ?, ?)
                """,
                transfer.logicalTransferId().value(),
                transfer.templateId().value(),
                transfer.sourceOrderId().value(),
                Timestamp.from(transfer.createdAt()));
        int order = 0;
        for (WarehouseTransferOperationRef ref : transfer.warehouseOperationRefs()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.material_transfer_operation_refs (
                        id, material_transfer_id, template_line_id, warehouse_draft_operation_id,
                        material_reference_id, quantity, source_storage_cell_id,
                        destination_storage_cell_id, ref_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    transfer.logicalTransferId().value(),
                    ref.templateLineId().value(),
                    ref.warehouseDraftOperationId(),
                    ref.materialReferenceId().value(),
                    ref.quantity(),
                    ref.sourceStorageCellId(),
                    ref.destinationStorageCellId(),
                    order++);
        }
    }

    private List<WarehouseTransferOperationRef> loadRefs(
            ProductionMaterialTransferId logicalTransferId) {
        return jdbcTemplate.query(
                """
                SELECT template_line_id, warehouse_draft_operation_id, material_reference_id,
                       quantity, source_storage_cell_id, destination_storage_cell_id
                FROM production.material_transfer_operation_refs
                WHERE material_transfer_id = ?
                ORDER BY ref_order
                """,
                (rs, rowNum) ->
                        new WarehouseTransferOperationRef(
                                MaterialTransferTemplateLineId.of(
                                        rs.getObject("template_line_id", UUID.class)),
                                rs.getObject("warehouse_draft_operation_id", UUID.class),
                                MaterialReferenceId.of(
                                        rs.getObject("material_reference_id", UUID.class)),
                                rs.getBigDecimal("quantity"),
                                rs.getObject("source_storage_cell_id", UUID.class),
                                rs.getObject("destination_storage_cell_id", UUID.class)),
                logicalTransferId.value());
    }

    private Optional<HeaderRow> findHeader(ProductionMaterialTransferId logicalTransferId) {
        try {
            HeaderRow row =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT template_id, source_order_id, created_at
                            FROM production.material_transfers
                            WHERE id = ?
                            """,
                            (rs, rowNum) ->
                                    new HeaderRow(
                                            rs.getObject("template_id", UUID.class),
                                            rs.getObject("source_order_id", UUID.class),
                                            rs.getTimestamp("created_at").toInstant()),
                            logicalTransferId.value());
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private record HeaderRow(UUID templateId, UUID sourceOrderId, java.time.Instant createdAt) {}
}
