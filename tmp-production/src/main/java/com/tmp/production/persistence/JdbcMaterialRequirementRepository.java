package com.tmp.production.persistence;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementId;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineId;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JDBC adapter for Production-owned Material Requirements ({@code production.*} only).
 *
 * <p>Header + child rows are persisted atomically in one local transaction (ADR-036 / REQUIRED).
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
        Optional<HeaderRow> header = findHeader(id);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<MaterialRequirementLine> lines = loadLines(id);
        HeaderRow row = header.orElseThrow();
        return Optional.of(
                MaterialRequirement.rehydrate(
                        id,
                        SourceOrderId.of(row.sourceOrderId()),
                        row.destinationWarehouseId(),
                        row.createdAt(),
                        row.updatedAt(),
                        row.version(),
                        row.status(),
                        lines));
    }

    private void insert(MaterialRequirement requirement) {
        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                INSERT INTO production.material_requirements (
                    id, source_order_id, destination_warehouse_id,
                    created_at, updated_at, version, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                requirement.requirementId().value(),
                requirement.sourceOrderId().value(),
                requirement.destinationWarehouseId(),
                Timestamp.from(requirement.createdAt()),
                Timestamp.from(now),
                0L,
                requirement.status().name());
        insertLines(requirement.requirementId(), requirement.lines());
    }

    private void update(MaterialRequirement requirement) {
        Instant now = clock.instant();
        long nextVersion = requirement.version() + 1;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE production.material_requirements
                        SET source_order_id = ?,
                            destination_warehouse_id = ?,
                            updated_at = ?,
                            version = ?,
                            status = ?
                        WHERE id = ? AND version = ?
                        """,
                        requirement.sourceOrderId().value(),
                        requirement.destinationWarehouseId(),
                        Timestamp.from(now),
                        nextVersion,
                        requirement.status().name(),
                        requirement.requirementId().value(),
                        requirement.version());
        if (updated == 0) {
            throw new MaterialRequirementOptimisticLockException(
                    requirement.requirementId(), requirement.version());
        }
        deleteLines(requirement.requirementId());
        insertLines(requirement.requirementId(), requirement.lines());
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
            for (SourceOrderItemId itemId : line.sourceOrderItemIds()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO production.material_requirement_line_source_items (
                            line_id, source_order_item_id)
                        VALUES (?, ?)
                        """,
                        line.lineId().value(),
                        itemId.value());
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
            Set<SourceOrderItemId> sourceItems = loadSourceItems(row.id());
            lines.add(
                    MaterialRequirementLine.rehydrate(
                            MaterialRequirementLineId.of(row.id()),
                            MaterialReferenceId.of(row.materialReferenceId()),
                            row.materialCode(),
                            row.materialName(),
                            row.color(),
                            row.unitOfMeasure(),
                            row.quantity(),
                            sourceItems));
        }
        return lines;
    }

    private Set<SourceOrderItemId> loadSourceItems(UUID lineId) {
        List<SourceOrderItemId> items =
                jdbcTemplate.query(
                        """
                        SELECT source_order_item_id
                        FROM production.material_requirement_line_source_items
                        WHERE line_id = ?
                        ORDER BY source_order_item_id
                        """,
                        (rs, rowNum) ->
                                SourceOrderItemId.of(
                                        rs.getObject("source_order_item_id", UUID.class)),
                        lineId);
        return new LinkedHashSet<>(items);
    }

    private Optional<HeaderRow> findHeader(MaterialRequirementId requirementId) {
        try {
            HeaderRow row =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id, source_order_id, destination_warehouse_id,
                                   created_at, updated_at, version, status
                            FROM production.material_requirements
                            WHERE id = ?
                            """,
                            (rs, rowNum) ->
                                    new HeaderRow(
                                            rs.getObject("source_order_id", UUID.class),
                                            rs.getObject("destination_warehouse_id", UUID.class),
                                            rs.getTimestamp("created_at").toInstant(),
                                            rs.getTimestamp("updated_at").toInstant(),
                                            rs.getLong("version"),
                                            MaterialRequirementStatus.valueOf(
                                                    rs.getString("status"))),
                            requirementId.value());
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private record HeaderRow(
            UUID sourceOrderId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialRequirementStatus status) {}

    private record LineRow(
            UUID id,
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal quantity) {}
}
