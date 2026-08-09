package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.StockPosition;
import com.tmp.warehouse.domain.StockPositionId;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.StockState;
import com.tmp.warehouse.domain.StorageCellId;
import com.tmp.warehouse.domain.WarehouseId;
import com.tmp.warehouse.domain.repository.StockPositionRepository;
import com.tmp.warehouse.persistence.WarehousePersistenceModels.StockPositionRow;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter mapping domain {@link StockPosition} to {@code warehouse.stock_positions}.
 *
 * <p>Does not execute Warehouse Operation / Movement / Reservation flows.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores JdbcWarehouseStockRepository injected by the container.")
public final class JdbcStockPositionRepository implements StockPositionRepository {

    private final JdbcWarehouseStockRepository stock;

    public JdbcStockPositionRepository(JdbcWarehouseStockRepository stock) {
        this.stock = Objects.requireNonNull(stock, "stock");
    }

    @Override
    public StockPosition create(StockPosition position) {
        Objects.requireNonNull(position, "position");
        StockPositionRow inserted = stock.insertPosition(
                position.id().value(),
                position.warehouseId(),
                position.storageCellId(),
                position.material(),
                position.quantity(),
                position.stockState());
        return inserted.toDomain();
    }

    @Override
    public Optional<StockPosition> findById(StockPositionId id) {
        Objects.requireNonNull(id, "id");
        return stock.findPositionById(id.value()).map(StockPositionRow::toDomain);
    }

    @Override
    public Optional<StockPosition> findByNaturalKey(
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            MaterialReference material,
            StockState stockState) {
        return stock.findPositionByNaturalKey(warehouseId, storageCellId, material, stockState)
                .map(StockPositionRow::toDomain);
    }

    @Override
    public List<StockPosition> findByMaterial(MaterialReference material) {
        Objects.requireNonNull(material, "material");
        return stock.findPositionsByMaterial(material).stream()
                .map(StockPositionRow::toDomain)
                .toList();
    }

    @Override
    public StockPosition updateQuantity(
            StockPositionId id, StockQuantity quantity, long expectedVersion) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(quantity, "quantity");
        StockPositionRow current = requireRow(id);
        return stock.updatePosition(id.value(), quantity, current.stockState(), expectedVersion)
                .toDomain();
    }

    @Override
    public StockPosition updateState(
            StockPositionId id, StockState stockState, long expectedVersion) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(stockState, "stockState");
        StockPositionRow current = requireRow(id);
        return stock.updatePosition(id.value(), current.quantity(), stockState, expectedVersion)
                .toDomain();
    }

    @Override
    public StockPosition updateQuantityAndState(
            StockPositionId id,
            StockQuantity quantity,
            StockState stockState,
            long expectedVersion) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(stockState, "stockState");
        return stock.updatePosition(id.value(), quantity, stockState, expectedVersion).toDomain();
    }

    private StockPositionRow requireRow(StockPositionId id) {
        return stock.findPositionById(id.value())
                .orElseThrow(() -> new NoSuchElementException("Stock position not found: " + id));
    }
}
