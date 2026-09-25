package com.tmp.warehouse.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Warehouse operation — sole write path for stock changes (Specification §10).
 *
 * <p>Lifecycle: {@link WarehouseOperationStatus#DRAFT} → {@code COMPLETED} on success, or {@code
 * FAILED} on error. Business flows (Receipt/Move/Transfer/…) are out of scope for the Operation
 * Engine; this type orchestrates generic stock mutation + movement recording.
 */
public final class WarehouseOperation {

    public static final int COMMENT_MAX_LENGTH = 1000;

    private final WarehouseOperationId id;
    private final WarehouseOperationType type;
    private final WarehouseOperationStatus status;
    private final MaterialReference material;
    private final WarehouseId warehouseId;
    private final StorageCellId storageCellId;
    private final StockState stockState;
    private final StockQuantity quantity;
    private final long version;
    private final UUID actorUserId;
    private final String actorLogin;
    private final String commentText;

    private WarehouseOperation(
            WarehouseOperationId id,
            WarehouseOperationType type,
            WarehouseOperationStatus status,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockState stockState,
            StockQuantity quantity,
            long version,
            UUID actorUserId,
            String actorLogin,
            String commentText) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.material = material;
        this.warehouseId = warehouseId;
        this.storageCellId = storageCellId;
        this.stockState = stockState;
        this.quantity = quantity;
        this.version = version;
        this.actorUserId = actorUserId;
        this.actorLogin = actorLogin;
        this.commentText = commentText;
    }

    /**
     * Creates a DRAFT operation ready for execution.
     */
    public static WarehouseOperation draft(
            WarehouseOperationId id,
            WarehouseOperationType type,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockState stockState,
            StockQuantity quantity) {
        return rehydrate(
                id,
                type,
                WarehouseOperationStatus.DRAFT,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                0L);
    }

    /**
     * Creates an operation description in DRAFT status without executing stock changes.
     *
     * <p>Defaults target stock state to {@link StockState#AVAILABLE}.
     */
    public static WarehouseOperation describe(
            WarehouseOperationId id,
            WarehouseOperationType type,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockQuantity quantity) {
        return draft(
                id, type, material, warehouseId, storageCellId, StockState.AVAILABLE, quantity);
    }

    /**
     * Rehydrates a persisted operation. Used by persistence adapters.
     */
    public static WarehouseOperation rehydrate(
            WarehouseOperationId id,
            WarehouseOperationType type,
            WarehouseOperationStatus status,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockState stockState,
            StockQuantity quantity,
            long version) {
        return rehydrate(
                id,
                type,
                status,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                version,
                null,
                null,
                null);
    }

    /**
     * Rehydrates a persisted operation including optional actor audit fields.
     */
    public static WarehouseOperation rehydrate(
            WarehouseOperationId id,
            WarehouseOperationType type,
            WarehouseOperationStatus status,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockState stockState,
            StockQuantity quantity,
            long version,
            UUID actorUserId,
            String actorLogin) {
        return rehydrate(
                id,
                type,
                status,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                version,
                actorUserId,
                actorLogin,
                null);
    }

    /**
     * Rehydrates a persisted operation including optional actor and comment audit fields.
     */
    public static WarehouseOperation rehydrate(
            WarehouseOperationId id,
            WarehouseOperationType type,
            WarehouseOperationStatus status,
            MaterialReference material,
            WarehouseId warehouseId,
            StorageCellId storageCellId,
            StockState stockState,
            StockQuantity quantity,
            long version,
            UUID actorUserId,
            String actorLogin,
            String commentText) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(storageCellId, "storageCellId");
        Objects.requireNonNull(stockState, "stockState");
        Objects.requireNonNull(quantity, "quantity");
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        return new WarehouseOperation(
                id,
                type,
                status,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                version,
                actorUserId,
                actorLogin,
                normalizeOptionalComment(commentText));
    }

    /**
     * Returns a copy with persisted actor identity for History audit.
     */
    public WarehouseOperation withActor(UUID actorUserId, String actorLogin) {
        return new WarehouseOperation(
                id,
                type,
                status,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                version,
                actorUserId,
                actorLogin,
                commentText);
    }

    /**
     * Returns a copy with optional immutable operation comment (blank → absent).
     */
    public WarehouseOperation withComment(String commentText) {
        return new WarehouseOperation(
                id,
                type,
                status,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                version,
                actorUserId,
                actorLogin,
                normalizeOptionalComment(commentText));
    }

    /**
     * Applies a stock quantity/state change through the operation write path.
     *
     * <p>Does not implement Receipt/Move/Transfer business flows; only enforces that stock mutation
     * goes via {@code WarehouseOperation}.
     */
    public StockPosition applyTo(
            StockPosition position, StockState newState, StockQuantity newQuantity) {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(newState, "newState");
        Objects.requireNonNull(newQuantity, "newQuantity");
        requireMatchingPosition(position);
        return position.applyChange(newState, newQuantity);
    }

    /**
     * Creates an initial stock position for this operation target.
     */
    public StockPosition createPosition(StockState state) {
        Objects.requireNonNull(state, "state");
        return StockPosition.of(
                StockPositionId.generate(), warehouseId, storageCellId, material, state, quantity);
    }

    /**
     * Ensures the operation is still executable ({@code DRAFT}).
     */
    public void ensureDraft() {
        if (status != WarehouseOperationStatus.DRAFT) {
            throw new InvalidWarehouseStateException(
                    "Warehouse operation is not executable: operationId="
                            + id
                            + ", status="
                            + status);
        }
    }

    public boolean isExecutable() {
        return status == WarehouseOperationStatus.DRAFT;
    }

    /**
     * Marks a successful execution result. Only allowed from {@code DRAFT}.
     */
    public WarehouseOperation complete() {
        ensureDraft();
        return new WarehouseOperation(
                id,
                type,
                WarehouseOperationStatus.COMPLETED,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                version,
                actorUserId,
                actorLogin,
                commentText);
    }

    /**
     * Marks a failed execution result. Only allowed from {@code DRAFT}.
     */
    public WarehouseOperation fail() {
        ensureDraft();
        return new WarehouseOperation(
                id,
                type,
                WarehouseOperationStatus.FAILED,
                material,
                warehouseId,
                storageCellId,
                stockState,
                quantity,
                version,
                actorUserId,
                actorLogin,
                commentText);
    }

    private void requireMatchingPosition(StockPosition position) {
        if (!warehouseId.equals(position.warehouseId())
                || !storageCellId.equals(position.storageCellId())
                || !material.equals(position.material())) {
            throw new InvalidWarehouseStateException(
                    "Warehouse operation target does not match stock position: operationId=" + id);
        }
    }

    static String normalizeOptionalComment(String commentText) {
        if (commentText == null) {
            return null;
        }
        String trimmed = commentText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > COMMENT_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Operation comment must be at most "
                            + COMMENT_MAX_LENGTH
                            + " characters");
        }
        return trimmed;
    }

    public WarehouseOperationId id() {
        return id;
    }

    public WarehouseOperationType type() {
        return type;
    }

    public WarehouseOperationStatus status() {
        return status;
    }

    public MaterialReference material() {
        return material;
    }

    public WarehouseId warehouseId() {
        return warehouseId;
    }

    public StorageCellId storageCellId() {
        return storageCellId;
    }

    public StockState stockState() {
        return stockState;
    }

    public StockQuantity quantity() {
        return quantity;
    }

    public long version() {
        return version;
    }

    public Optional<UUID> actorUserId() {
        return Optional.ofNullable(actorUserId);
    }

    public Optional<String> actorLogin() {
        return Optional.ofNullable(actorLogin);
    }

    public Optional<String> commentText() {
        return Optional.ofNullable(commentText);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WarehouseOperation that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "WarehouseOperation{id=" + id + ", type=" + type + ", status=" + status + '}';
    }
}
