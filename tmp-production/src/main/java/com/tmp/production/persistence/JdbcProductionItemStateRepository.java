package com.tmp.production.persistence;

import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for {@code production.production_item_states}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcProductionItemStateRepository implements ProductionItemStateRepository {

    private static final String SELECT_COLUMNS =
            """
            SELECT id, source_order_id, source_order_item_id, specification_id, status,
                   ordered_quantity, launched_quantity, active_production_quantity,
                   released_quantity, last_material_check_at, last_status_changed_at,
                   version, created_at, updated_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcProductionItemStateRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ProductionItemState save(ProductionItemState state) {
        Objects.requireNonNull(state, "state");
        ProductionItemStateEntity entity = ProductionItemStateMapper.toEntity(state);
        Optional<ProductionItemStateEntity> existing =
                findEntityByIdentity(
                        state.sourceOrderId(), state.sourceOrderItemId(), state.specificationId());
        if (existing.isPresent()) {
            ProductionItemStateEntity current = existing.orElseThrow();
            entity.setId(current.id());
            entity.setVersion(current.version());
            entity.setCreatedAt(current.createdAt());
            update(entity);
        } else {
            insert(entity);
        }
        return findByIdentity(
                        state.sourceOrderId(), state.sourceOrderItemId(), state.specificationId())
                .orElseThrow();
    }

    @Override
    public Optional<ProductionItemState> findByIdentity(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId) {
        return findEntityByIdentity(sourceOrderId, sourceOrderItemId, specificationId)
                .map(ProductionItemStateMapper::toDomain);
    }

    private Optional<ProductionItemStateEntity> findEntityByIdentity(
            SourceOrderId sourceOrderId,
            SourceOrderItemId sourceOrderItemId,
            SpecificationId specificationId) {
        try {
            ProductionItemStateEntity entity =
                    jdbcTemplate.queryForObject(
                            SELECT_COLUMNS
                                    + """
                                     FROM production.production_item_states
                                     WHERE source_order_id = ?
                                       AND source_order_item_id = ?
                                       AND specification_id = ?
                                    """,
                            (rs, rowNum) -> ProductionItemStateMapper.mapRow(rs),
                            sourceOrderId.value(),
                            sourceOrderItemId.value(),
                            specificationId.value());
            return Optional.of(entity);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private void insert(ProductionItemStateEntity entity) {
        Instant now = clock.instant();
        entity.setId(ProductionItemId.generate());
        entity.setVersion(0L);
        if (entity.createdAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        jdbcTemplate.update(
                """
                INSERT INTO production.production_item_states (
                    id, source_order_id, source_order_item_id, specification_id, status,
                    ordered_quantity, launched_quantity, active_production_quantity,
                    released_quantity, last_material_check_at, last_status_changed_at,
                    version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entity.id().value(),
                entity.sourceOrderId().value(),
                entity.sourceOrderItemId().value(),
                entity.specificationId().value(),
                entity.status().name(),
                entity.orderedQuantity().value().longValueExact(),
                entity.launchedQuantity().value().longValueExact(),
                entity.activeProductionQuantity().value().longValueExact(),
                entity.releasedQuantity().value().longValueExact(),
                toTimestamp(entity.lastMaterialCheckAt()),
                Timestamp.from(entity.lastStatusChangedAt()),
                entity.version(),
                Timestamp.from(entity.createdAt()),
                Timestamp.from(entity.updatedAt()));
    }

    private void update(ProductionItemStateEntity entity) {
        Instant now = clock.instant();
        long nextVersion = entity.version() + 1;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE production.production_item_states
                        SET status = ?,
                            ordered_quantity = ?,
                            launched_quantity = ?,
                            active_production_quantity = ?,
                            released_quantity = ?,
                            last_material_check_at = ?,
                            last_status_changed_at = ?,
                            version = ?,
                            updated_at = ?
                        WHERE id = ? AND version = ?
                        """,
                        entity.status().name(),
                        entity.orderedQuantity().value().longValueExact(),
                        entity.launchedQuantity().value().longValueExact(),
                        entity.activeProductionQuantity().value().longValueExact(),
                        entity.releasedQuantity().value().longValueExact(),
                        toTimestamp(entity.lastMaterialCheckAt()),
                        Timestamp.from(entity.lastStatusChangedAt()),
                        nextVersion,
                        Timestamp.from(now),
                        entity.id().value(),
                        entity.version());
        if (updated == 0) {
            throw new ProductionPersistenceException(
                    "Optimistic lock failure for production item state " + entity.id());
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
