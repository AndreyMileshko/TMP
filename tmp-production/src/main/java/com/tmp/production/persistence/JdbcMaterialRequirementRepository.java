package com.tmp.production.persistence;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementSourceItemKey;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC adapter for Production-owned Material Requirements ({@code production.*} only).
 *
 * <p>Header + source items + lines are persisted atomically in one local transaction (ADR-036 /
 * REQUIRED). Cross-order requirements store {@code source_order_id = NULL} on the header; covered
 * products live in {@code material_requirement_source_items}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "Stores Spring-managed JdbcTemplate, Clock and TransactionTemplate injected by"
                        + " the container.")
public final class JdbcMaterialRequirementRepository implements MaterialRequirementRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public JdbcMaterialRequirementRepository(
            JdbcTemplate jdbcTemplate, Clock clock, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public MaterialRequirement save(MaterialRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        MaterialRequirement saved =
                transactionTemplate.execute(
                        status -> {
                            Optional<HeaderRow> existing = findHeader(requirement.requirementId());
                            if (existing.isPresent()) {
                                update(requirement);
                            } else {
                                insert(requirement);
                            }
                            return findById(requirement.requirementId()).orElseThrow();
                        });
        if (saved == null) {
            throw new IllegalStateException(
                    "Material requirement save returned null: " + requirement.requirementId());
        }
        return saved;
    }

    @Override
    public Optional<MaterialRequirement> findById(MaterialRequirementId id) {
        Objects.requireNonNull(id, "id");
        return load(id, findHeader(id));
    }

    @Override
    public Optional<MaterialRequirement> findByIdForUpdate(MaterialRequirementId id) {
        Objects.requireNonNull(id, "id");
        return load(id, findHeaderForUpdate(id));
    }

    @Override
    public MaterialRequirement markSubmitted(MaterialRequirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        Instant now = clock.instant();
        long nextVersion = requirement.version() + 1;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE production.material_requirements
                        SET status = ?,
                            submitted_at = ?,
                            submitted_by = ?,
                            updated_at = ?,
                            version = ?
                        WHERE id = ? AND version = ?
                        """,
                        requirement.status().name(),
                        requirement.submittedAt().map(Timestamp::from).orElse(null),
                        requirement.submittedBy().orElse(null),
                        Timestamp.from(now),
                        nextVersion,
                        requirement.requirementId().value(),
                        requirement.version());
        if (updated == 0) {
            throw new MaterialRequirementOptimisticLockException(
                    requirement.requirementId(), requirement.version());
        }
        return findById(requirement.requirementId()).orElseThrow();
    }

    @Override
    public Map<MaterialRequirementSourceItemKey, Long> sumSubmittedProductQuantities(
            Collection<MaterialRequirementSourceItemKey> keys) {
        Objects.requireNonNull(keys, "keys");
        Map<MaterialRequirementSourceItemKey, Long> result = new LinkedHashMap<>();
        if (keys.isEmpty()) {
            return result;
        }
        for (MaterialRequirementSourceItemKey key : keys) {
            Objects.requireNonNull(key, "key");
            result.put(key, 0L);
        }
        List<MaterialRequirementSourceItemKey> keyList = List.copyOf(keys);
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT si.source_order_id, si.source_order_item_id,
                               COALESCE(SUM(si.requested_product_quantity), 0) AS submitted_qty
                        FROM production.material_requirement_source_items si
                        JOIN production.material_requirements mr ON mr.id = si.requirement_id
                        WHERE mr.status = 'SUBMITTED'
                          AND si.counts_toward_product_coverage = TRUE
                          AND (
                        """);
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < keyList.size(); i++) {
            if (i > 0) {
                sql.append(" OR ");
            }
            sql.append("(si.source_order_id = ? AND si.source_order_item_id = ?)");
            MaterialRequirementSourceItemKey key = keyList.get(i);
            args.add(key.sourceOrderId().value());
            args.add(key.sourceOrderItemId().value());
        }
        sql.append(
                """
                          )
                        GROUP BY si.source_order_id, si.source_order_item_id
                        """);
        jdbcTemplate.query(
                sql.toString(),
                (rs) -> {
                    MaterialRequirementSourceItemKey key =
                            MaterialRequirementSourceItemKey.of(
                                    SourceOrderId.of(rs.getObject("source_order_id", UUID.class)),
                                    SourceOrderItemId.of(
                                            rs.getObject("source_order_item_id", UUID.class)));
                    result.put(key, rs.getLong("submitted_qty"));
                },
                args.toArray());
        return Map.copyOf(result);
    }

    @Override
    public List<MaterialRequirement> findByIds(Collection<MaterialRequirementId> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.isEmpty()) {
            return List.of();
        }
        List<MaterialRequirement> loaded = new ArrayList<>();
        for (MaterialRequirementId id : ids) {
            findById(Objects.requireNonNull(id, "id")).ifPresent(loaded::add);
        }
        return List.copyOf(loaded);
    }

    @Override
    public List<MaterialRequirement> findBySourceOrderItemIds(
            Collection<SourceOrderItemId> itemIds) {
        Objects.requireNonNull(itemIds, "itemIds");
        if (itemIds.isEmpty()) {
            return List.of();
        }
        List<SourceOrderItemId> unique = itemIds.stream().distinct().toList();
        StringBuilder sql =
                new StringBuilder(
                        """
                        SELECT mr.id
                        FROM production.material_requirements mr
                        JOIN production.material_requirement_source_items si
                          ON si.requirement_id = mr.id
                        WHERE si.source_order_item_id IN (
                        """);
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < unique.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
            args.add(unique.get(i).value());
        }
        sql.append(
                """
                )
                GROUP BY mr.id, mr.created_at
                ORDER BY mr.created_at ASC, mr.id ASC
                """);
        List<UUID> requirementIds =
                jdbcTemplate.query(
                        sql.toString(),
                        (rs, rowNum) -> rs.getObject("id", UUID.class),
                        args.toArray());
        List<MaterialRequirement> loaded = new ArrayList<>(requirementIds.size());
        for (UUID id : requirementIds) {
            findById(MaterialRequirementId.of(id)).ifPresent(loaded::add);
        }
        return List.copyOf(loaded);
    }

    private Optional<MaterialRequirement> load(
            MaterialRequirementId id, Optional<HeaderRow> header) {
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<MaterialRequirementSourceItem> sourceItems = loadSourceItems(id);
        List<MaterialRequirementLine> lines = loadLines(id);
        HeaderRow row = header.orElseThrow();
        return Optional.of(
                MaterialRequirement.rehydrate(
                        id,
                        row.destinationWarehouseId(),
                        row.createdAt(),
                        row.updatedAt(),
                        row.version(),
                        row.status(),
                        row.submittedAt(),
                        row.submittedBy(),
                        sourceItems,
                        lines));
    }

    private void insert(MaterialRequirement requirement) {
        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                INSERT INTO production.material_requirements (
                    id, source_order_id, destination_warehouse_id,
                    created_at, updated_at, version, status, submitted_at, submitted_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                requirement.requirementId().value(),
                null,
                requirement.destinationWarehouseId(),
                Timestamp.from(requirement.createdAt()),
                Timestamp.from(now),
                0L,
                requirement.status().name(),
                requirement.submittedAt().map(Timestamp::from).orElse(null),
                requirement.submittedBy().orElse(null));
        insertSourceItems(requirement.requirementId(), requirement.sourceItems());
        insertLines(requirement.requirementId(), requirement.lines());
    }

    private void update(MaterialRequirement requirement) {
        Instant now = clock.instant();
        long nextVersion = requirement.version() + 1;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE production.material_requirements
                        SET destination_warehouse_id = ?,
                            updated_at = ?,
                            version = ?,
                            status = ?,
                            submitted_at = ?,
                            submitted_by = ?
                        WHERE id = ? AND version = ?
                        """,
                        requirement.destinationWarehouseId(),
                        Timestamp.from(now),
                        nextVersion,
                        requirement.status().name(),
                        requirement.submittedAt().map(Timestamp::from).orElse(null),
                        requirement.submittedBy().orElse(null),
                        requirement.requirementId().value(),
                        requirement.version());
        if (updated == 0) {
            throw new MaterialRequirementOptimisticLockException(
                    requirement.requirementId(), requirement.version());
        }
        deleteLines(requirement.requirementId());
        deleteSourceItems(requirement.requirementId());
        insertSourceItems(requirement.requirementId(), requirement.sourceItems());
        insertLines(requirement.requirementId(), requirement.lines());
    }

    private void deleteSourceItems(MaterialRequirementId requirementId) {
        jdbcTemplate.update(
                """
                DELETE FROM production.material_requirement_source_items
                WHERE requirement_id = ?
                """,
                requirementId.value());
    }

    private void insertSourceItems(
            MaterialRequirementId requirementId, List<MaterialRequirementSourceItem> sourceItems) {
        for (MaterialRequirementSourceItem item : sourceItems) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.material_requirement_source_items (
                        requirement_id, source_order_id, source_order_item_id,
                        requested_product_quantity, counts_toward_product_coverage)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    requirementId.value(),
                    item.sourceOrderId().value(),
                    item.sourceOrderItemId().value(),
                    item.requestedProductQuantity(),
                    item.countsTowardProductCoverage());
        }
    }

    private List<MaterialRequirementSourceItem> loadSourceItems(MaterialRequirementId requirementId) {
        return jdbcTemplate.query(
                """
                SELECT source_order_id, source_order_item_id, requested_product_quantity,
                       counts_toward_product_coverage
                FROM production.material_requirement_source_items
                WHERE requirement_id = ?
                ORDER BY source_order_id, source_order_item_id
                """,
                (rs, rowNum) ->
                        MaterialRequirementSourceItem.rehydrate(
                                SourceOrderId.of(rs.getObject("source_order_id", UUID.class)),
                                SourceOrderItemId.of(
                                        rs.getObject("source_order_item_id", UUID.class)),
                                rs.getLong("requested_product_quantity"),
                                rs.getBoolean("counts_toward_product_coverage")),
                requirementId.value());
    }

    private void deleteLines(MaterialRequirementId requirementId) {
        List<UUID> lineIds =
                jdbcTemplate.query(
                        """
                        SELECT id FROM production.material_requirement_lines
                        WHERE requirement_id = ?
                        """,
                        (rs, rowNum) -> rs.getObject("id", UUID.class),
                        requirementId.value());
        for (UUID lineId : lineIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM production.material_requirement_line_source_items
                    WHERE line_id = ?
                    """,
                    lineId);
        }
        jdbcTemplate.update(
                """
                DELETE FROM production.material_requirement_lines
                WHERE requirement_id = ?
                """,
                requirementId.value());
    }

    private void insertLines(
            MaterialRequirementId requirementId, List<MaterialRequirementLine> lines) {
        int order = 0;
        for (MaterialRequirementLine line : lines) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.material_requirement_lines (
                        id, requirement_id, material_reference_id, material_code, material_name,
                        color, unit_of_measure, quantity, line_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    line.lineId().value(),
                    requirementId.value(),
                    line.materialReferenceId().value(),
                    line.materialCode(),
                    line.materialName(),
                    line.color(),
                    line.unitOfMeasure(),
                    line.quantity(),
                    order++);
            for (MaterialRequirementLineContribution contribution : line.contributions()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO production.material_requirement_line_source_items (
                            line_id, source_order_item_id, source_order_id,
                            contributed_material_quantity)
                        VALUES (?, ?, ?, ?)
                        """,
                        line.lineId().value(),
                        contribution.sourceOrderItemId().value(),
                        contribution.sourceOrderId().value(),
                        contribution.contributedMaterialQuantity());
            }
        }
    }

    private List<MaterialRequirementLine> loadLines(MaterialRequirementId requirementId) {
        List<LineRow> rows =
                jdbcTemplate.query(
                        """
                        SELECT id, material_reference_id, material_code, material_name, color,
                               unit_of_measure, quantity
                        FROM production.material_requirement_lines
                        WHERE requirement_id = ?
                        ORDER BY line_order
                        """,
                        (rs, rowNum) ->
                                new LineRow(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("material_reference_id", UUID.class),
                                        rs.getString("material_code"),
                                        rs.getString("material_name"),
                                        rs.getString("color"),
                                        rs.getString("unit_of_measure"),
                                        rs.getBigDecimal("quantity")),
                        requirementId.value());

        List<MaterialRequirementLine> lines = new ArrayList<>(rows.size());
        for (LineRow row : rows) {
            List<MaterialRequirementLineContribution> contributions =
                    loadLineContributions(requirementId, row.id());
            lines.add(
                    MaterialRequirementLine.rehydrate(
                            MaterialRequirementLineId.of(row.id()),
                            MaterialReferenceId.of(row.materialReferenceId()),
                            row.materialCode(),
                            row.materialName(),
                            row.color(),
                            row.unitOfMeasure(),
                            row.quantity(),
                            contributions));
        }
        return lines;
    }

    private List<MaterialRequirementLineContribution> loadLineContributions(
            MaterialRequirementId requirementId, UUID lineId) {
        return jdbcTemplate.query(
                """
                SELECT COALESCE(lsi.source_order_id, si.source_order_id) AS source_order_id,
                       lsi.source_order_item_id,
                       COALESCE(lsi.contributed_material_quantity, mrl.quantity)
                           AS contributed_material_quantity
                FROM production.material_requirement_line_source_items lsi
                JOIN production.material_requirement_lines mrl ON mrl.id = lsi.line_id
                LEFT JOIN production.material_requirement_source_items si
                  ON si.requirement_id = mrl.requirement_id
                 AND si.source_order_item_id = lsi.source_order_item_id
                 AND (lsi.source_order_id IS NULL OR si.source_order_id = lsi.source_order_id)
                WHERE lsi.line_id = ?
                  AND mrl.requirement_id = ?
                ORDER BY source_order_id, lsi.source_order_item_id
                """,
                (rs, rowNum) ->
                        MaterialRequirementLineContribution.of(
                                SourceOrderId.of(rs.getObject("source_order_id", UUID.class)),
                                SourceOrderItemId.of(
                                        rs.getObject("source_order_item_id", UUID.class)),
                                rs.getBigDecimal("contributed_material_quantity")),
                lineId,
                requirementId.value());
    }

    private Optional<HeaderRow> findHeader(MaterialRequirementId requirementId) {
        return queryHeader(requirementId, false);
    }

    private Optional<HeaderRow> findHeaderForUpdate(MaterialRequirementId requirementId) {
        return queryHeader(requirementId, true);
    }

    private Optional<HeaderRow> queryHeader(MaterialRequirementId requirementId, boolean forUpdate) {
        String sql =
                """
                SELECT destination_warehouse_id,
                       created_at, updated_at, version, status, submitted_at, submitted_by
                FROM production.material_requirements
                WHERE id = ?
                """
                        + (forUpdate ? " FOR UPDATE" : "");
        try {
            HeaderRow row =
                    jdbcTemplate.queryForObject(
                            sql,
                            (rs, rowNum) ->
                                    new HeaderRow(
                                            rs.getObject("destination_warehouse_id", UUID.class),
                                            rs.getTimestamp("created_at").toInstant(),
                                            rs.getTimestamp("updated_at").toInstant(),
                                            rs.getLong("version"),
                                            MaterialRequirementStatus.valueOf(
                                                    rs.getString("status")),
                                            rs.getTimestamp("submitted_at") == null
                                                    ? null
                                                    : rs.getTimestamp("submitted_at").toInstant(),
                                            rs.getString("submitted_by")),
                            requirementId.value());
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private record HeaderRow(
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialRequirementStatus status,
            Instant submittedAt,
            String submittedBy) {}

    private record LineRow(
            UUID id,
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity) {}
}
