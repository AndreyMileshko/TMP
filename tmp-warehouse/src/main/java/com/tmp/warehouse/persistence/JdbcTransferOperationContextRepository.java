package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcTransferOperationContextRepository
        implements TransferOperationContextRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcTransferOperationContextRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public void save(TransferOperationContext context) {
        Objects.requireNonNull(context, "context");
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.transfer_operation_context (
                    operation_id, destination_warehouse_id, destination_storage_cell_id)
                VALUES (?, ?, ?)
                """,
                context.operationId().value(),
                context.destinationWarehouseId().value(),
                context.destinationStorageCellId().value());
    }

    @Override
    public Optional<TransferOperationContext> findByOperationId(WarehouseOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        try {
            return Optional.of(
                    jdbcTemplate.queryForObject(
                            """
                            SELECT operation_id, destination_warehouse_id, destination_storage_cell_id
                            FROM warehouse.transfer_operation_context
                            WHERE operation_id = ?
                            """,
                            (rs, rowNum) ->
                                    new TransferOperationContext(
                                            WarehouseOperationId.of(
                                                    rs.getObject("operation_id", java.util.UUID.class)),
                                            WarehouseId.of(
                                                    rs.getObject(
                                                            "destination_warehouse_id",
                                                            java.util.UUID.class)),
                                            StorageCellId.of(
                                                    rs.getObject(
                                                            "destination_storage_cell_id",
                                                            java.util.UUID.class))),
                            operationId.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }
}
