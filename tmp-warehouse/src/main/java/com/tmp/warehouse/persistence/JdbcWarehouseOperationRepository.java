package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.WarehouseOperation;
import com.tmp.warehouse.domain.WarehouseOperationId;
import com.tmp.warehouse.domain.repository.WarehouseOperationRepository;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.WarehouseOperationRow;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter mapping domain {@link WarehouseOperation} to {@code warehouse.warehouse_operations}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores JdbcWarehouseStockRepository and Clock injected by the container.")
public final class JdbcWarehouseOperationRepository implements WarehouseOperationRepository {

    private final JdbcWarehouseStockRepository stock;
    private final Clock clock;

    public JdbcWarehouseOperationRepository(JdbcWarehouseStockRepository stock, Clock clock) {
        this.stock = Objects.requireNonNull(stock, "stock");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public WarehouseOperation create(WarehouseOperation operation) {
        Objects.requireNonNull(operation, "operation");
        Instant now = clock.instant();
        return stock.insertOperation(WarehouseOperationRow.fromDomain(operation, now, now))
                .toDomain();
    }

    @Override
    public Optional<WarehouseOperation> findById(WarehouseOperationId id) {
        Objects.requireNonNull(id, "id");
        return stock.findOperationById(id).map(WarehouseOperationRow::toDomain);
    }

    @Override
    public WarehouseOperation update(WarehouseOperation operation) {
        Objects.requireNonNull(operation, "operation");
        Instant now = clock.instant();
        WarehouseOperationRow current =
                stock.findOperationById(operation.id())
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Warehouse operation not found: " + operation.id()));
        return stock.updateOperation(
                        WarehouseOperationRow.fromDomain(operation, current.createdAt(), now),
                        operation.version())
                .toDomain();
    }
}
