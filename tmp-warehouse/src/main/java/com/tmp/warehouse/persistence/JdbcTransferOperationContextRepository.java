package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.TransferOperationContext;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.repository.TransferOperationContextRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcTransferOperationContextRepository
        implements TransferOperationContextRepository {

    private static final String SELECT_COLUMNS =
            """
            SELECT operation_id, destination_warehouse_id, destination_storage_cell_id,
                   receive_operation_id
            FROM warehouse.transfer_operation_context
            """;

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
                    operation_id, destination_warehouse_id, destination_storage_cell_id,
                    receive_operation_id)
                VALUES (?, ?, ?, ?)
                """,
                context.operationId().value(),
                context.destinationWarehouseId().value(),
                context.destinationStorageCellId().value(),
                context.receiveOperationId() == null ? null : context.receiveOperationId().value());
    }

    @Override
    public Optional<TransferOperationContext> findByOperationId(WarehouseOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return queryOne(SELECT_COLUMNS + " WHERE operation_id = ?", operationId.value());
    }

    @Override
    public Optional<TransferOperationContext> findByReceiveOperationId(
            WarehouseOperationId receiveOperationId) {
        Objects.requireNonNull(receiveOperationId, "receiveOperationId");
        return queryOne(
                SELECT_COLUMNS + " WHERE receive_operation_id = ?", receiveOperationId.value());
    }

    @Override
    public Optional<TransferOperationContext> lockByOperationId(WarehouseOperationId operationId) {
        Objects.requireNonNull(operationId, "operationId");
        return queryOne(
                SELECT_COLUMNS + " WHERE operation_id = ? FOR UPDATE", operationId.value());
    }

    @Override
    public boolean claimReceiveIfAbsent(
            WarehouseOperationId sendOperationId, WarehouseOperationId receiveOperationId) {
        Objects.requireNonNull(sendOperationId, "sendOperationId");
        Objects.requireNonNull(receiveOperationId, "receiveOperationId");
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE warehouse.transfer_operation_context
                        SET receive_operation_id = ?
                        WHERE operation_id = ?
                          AND receive_operation_id IS NULL
                        """,
                        receiveOperationId.value(),
                        sendOperationId.value());
        return updated == 1;
    }

    private Optional<TransferOperationContext> queryOne(String sql, UUID id) {
        try {
            return Optional.of(
                    jdbcTemplate.queryForObject(sql, (rs, rowNum) -> mapContext(rs), id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private static TransferOperationContext mapContext(ResultSet rs) throws SQLException {
        UUID receiveId = rs.getObject("receive_operation_id", UUID.class);
        return new TransferOperationContext(
                WarehouseOperationId.of(rs.getObject("operation_id", UUID.class)),
                WarehouseId.of(rs.getObject("destination_warehouse_id", UUID.class)),
                StorageCellId.of(rs.getObject("destination_storage_cell_id", UUID.class)),
                receiveId == null ? null : WarehouseOperationId.of(receiveId));
    }
}
