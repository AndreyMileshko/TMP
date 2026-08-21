package com.tmp.production.persistence;

import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateId;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateLineId;
import com.tmp.production.domain.MaterialTransferTemplateOptimisticLockException;
import com.tmp.production.domain.MaterialTransferTemplateStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
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
 * JDBC adapter for Production-owned Material Transfer Templates ({@code production.*} only).
 *
 * <p>Header + child rows are persisted atomically in one local transaction (ADR-036 / REQUIRED).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification =
                "Stores Spring-managed JdbcTemplate, Clock and TransactionTemplate injected by"
                        + " the container.")
public final class JdbcMaterialTransferTemplateRepository
        implements MaterialTransferTemplateRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public JdbcMaterialTransferTemplateRepository(
            JdbcTemplate jdbcTemplate, Clock clock, PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transactionTemplate =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public MaterialTransferTemplate save(MaterialTransferTemplate template) {
        Objects.requireNonNull(template, "template");
        MaterialTransferTemplate saved =
                transactionTemplate.execute(
                        status -> {
                            Optional<HeaderRow> existing = findHeader(template.templateId());
                            if (existing.isPresent()) {
                                update(template);
                            } else {
                                insert(template);
                            }
                            return findById(template.templateId()).orElseThrow();
                        });
        if (saved == null) {
            throw new IllegalStateException(
                    "Material transfer template save returned null: " + template.templateId());
        }
        return saved;
    }

    @Override
    public Optional<MaterialTransferTemplate> findById(MaterialTransferTemplateId templateId) {
        Objects.requireNonNull(templateId, "templateId");
        Optional<HeaderRow> header = findHeader(templateId);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        List<MaterialTransferTemplateLine> lines = loadLines(templateId);
        HeaderRow row = header.orElseThrow();
        return Optional.of(
                MaterialTransferTemplate.rehydrate(
                        templateId,
                        SourceOrderId.of(row.sourceOrderId()),
                        row.sourceWarehouseId(),
                        row.destinationWarehouseId(),
                        row.createdAt(),
                        row.updatedAt(),
                        lines,
                        row.version(),
                        row.status(),
                        row.confirmedAt()));
    }

    private void insert(MaterialTransferTemplate template) {
        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                INSERT INTO production.material_transfer_templates (
                    id, source_order_id, source_warehouse_id, destination_warehouse_id,
                    created_at, updated_at, version, status, confirmed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                template.templateId().value(),
                template.sourceOrderId().value(),
                template.sourceWarehouseId(),
                template.destinationWarehouseId(),
                Timestamp.from(template.createdAt()),
                Timestamp.from(now),
                0L,
                template.status().name(),
                template.confirmedAt().map(Timestamp::from).orElse(null));
        insertLines(template.templateId(), template.lines());
    }

    private void update(MaterialTransferTemplate template) {
        Instant now = clock.instant();
        long nextVersion = template.version() + 1;
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE production.material_transfer_templates
                        SET source_order_id = ?,
                            source_warehouse_id = ?,
                            destination_warehouse_id = ?,
                            updated_at = ?,
                            version = ?,
                            status = ?,
                            confirmed_at = ?
                        WHERE id = ? AND version = ?
                        """,
                        template.sourceOrderId().value(),
                        template.sourceWarehouseId(),
                        template.destinationWarehouseId(),
                        Timestamp.from(now),
                        nextVersion,
                        template.status().name(),
                        template.confirmedAt().map(Timestamp::from).orElse(null),
                        template.templateId().value(),
                        template.version());
        if (updated == 0) {
            throw new MaterialTransferTemplateOptimisticLockException(
                    template.templateId(), template.version());
        }
        deleteLines(template.templateId());
        insertLines(template.templateId(), template.lines());
    }

    private void deleteLines(MaterialTransferTemplateId templateId) {
        List<UUID> lineIds =
                jdbcTemplate.query(
                        """
                        SELECT id FROM production.material_transfer_template_lines
                        WHERE template_id = ?
                        """,
                        (rs, rowNum) -> rs.getObject("id", UUID.class),
                        templateId.value());
        for (UUID lineId : lineIds) {
            jdbcTemplate.update(
                    """
                    DELETE FROM production.material_transfer_template_line_source_items
                    WHERE line_id = ?
                    """,
                    lineId);
            jdbcTemplate.update(
                    """
                    DELETE FROM production.material_transfer_template_line_cutting_refs
                    WHERE line_id = ?
                    """,
                    lineId);
        }
        jdbcTemplate.update(
                """
                DELETE FROM production.material_transfer_template_lines
                WHERE template_id = ?
                """,
                templateId.value());
    }

    private void insertLines(
            MaterialTransferTemplateId templateId, List<MaterialTransferTemplateLine> lines) {
        int order = 0;
        for (MaterialTransferTemplateLine line : lines) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.material_transfer_template_lines (
                        id, template_id, material_reference_id, material_code, material_name,
                        color, unit_of_measure, recommended_quantity, requested_quantity,
                        included, planning_source, cutting_plan_id, cutting_link_status,
                        required_quantity, main_warehouse_available,
                        production_warehouse_available, uncovered_deficit, line_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    line.lineId().value(),
                    templateId.value(),
                    line.materialReferenceId().value(),
                    line.materialCode(),
                    line.materialName(),
                    line.color(),
                    line.unitOfMeasure(),
                    line.recommendedQuantity(),
                    line.requestedQuantity(),
                    line.included(),
                    line.planningSource().name(),
                    line.cuttingPlanId().map(CuttingPlanId::value).orElse(null),
                    line.cuttingLinkStatus().name(),
                    line.requiredQuantity(),
                    line.mainWarehouseAvailable(),
                    line.productionWarehouseAvailable(),
                    line.uncoveredDeficit(),
                    order++);
            for (SourceOrderItemId itemId : line.sourceOrderItemIds()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO production.material_transfer_template_line_source_items (
                            line_id, source_order_item_id)
                        VALUES (?, ?)
                        """,
                        line.lineId().value(),
                        itemId.value());
            }
            for (CuttingPlanId cuttingPlanId : line.cuttingPlanReferences()) {
                jdbcTemplate.update(
                        """
                        INSERT INTO production.material_transfer_template_line_cutting_refs (
                            line_id, cutting_plan_id)
                        VALUES (?, ?)
                        """,
                        line.lineId().value(),
                        cuttingPlanId.value());
            }
        }
    }

    private List<MaterialTransferTemplateLine> loadLines(MaterialTransferTemplateId templateId) {
        List<LineRow> rows =
                jdbcTemplate.query(
                        """
                        SELECT id, material_reference_id, material_code, material_name, color,
                               unit_of_measure, recommended_quantity, requested_quantity, included,
                               planning_source, cutting_plan_id, cutting_link_status,
                               required_quantity, main_warehouse_available,
                               production_warehouse_available, uncovered_deficit
                        FROM production.material_transfer_template_lines
                        WHERE template_id = ?
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
                                        rs.getBigDecimal("recommended_quantity"),
                                        rs.getBigDecimal("requested_quantity"),
                                        rs.getBoolean("included"),
                                        MaterialPlanningSource.valueOf(
                                                rs.getString("planning_source")),
                                        rs.getObject("cutting_plan_id", UUID.class),
                                        CuttingLinkStatus.valueOf(
                                                rs.getString("cutting_link_status")),
                                        rs.getBigDecimal("required_quantity"),
                                        rs.getBigDecimal("main_warehouse_available"),
                                        rs.getBigDecimal("production_warehouse_available"),
                                        rs.getBigDecimal("uncovered_deficit")),
                        templateId.value());

        List<MaterialTransferTemplateLine> lines = new ArrayList<>(rows.size());
        for (LineRow row : rows) {
            Set<SourceOrderItemId> sourceItems = loadSourceItems(row.id());
            List<CuttingPlanId> cuttingRefs = loadCuttingRefs(row.id());
            CuttingPlanId cuttingPlanId =
                    row.cuttingPlanId() == null ? null : CuttingPlanId.of(row.cuttingPlanId());
            lines.add(
                    MaterialTransferTemplateLine.rehydrate(
                            MaterialTransferTemplateLineId.of(row.id()),
                            MaterialReferenceId.of(row.materialReferenceId()),
                            row.materialCode(),
                            row.materialName(),
                            row.color(),
                            row.unitOfMeasure(),
                            row.recommendedQuantity(),
                            row.requestedQuantity(),
                            row.included(),
                            row.planningSource(),
                            cuttingPlanId,
                            row.cuttingLinkStatus(),
                            cuttingRefs,
                            sourceItems,
                            row.requiredQuantity(),
                            row.mainWarehouseAvailable(),
                            row.productionWarehouseAvailable(),
                            row.uncoveredDeficit()));
        }
        return lines;
    }

    private Set<SourceOrderItemId> loadSourceItems(UUID lineId) {
        List<SourceOrderItemId> items =
                jdbcTemplate.query(
                        """
                        SELECT source_order_item_id
                        FROM production.material_transfer_template_line_source_items
                        WHERE line_id = ?
                        ORDER BY source_order_item_id
                        """,
                        (rs, rowNum) ->
                                SourceOrderItemId.of(
                                        rs.getObject("source_order_item_id", UUID.class)),
                        lineId);
        return new LinkedHashSet<>(items);
    }

    private List<CuttingPlanId> loadCuttingRefs(UUID lineId) {
        return jdbcTemplate.query(
                """
                SELECT cutting_plan_id
                FROM production.material_transfer_template_line_cutting_refs
                WHERE line_id = ?
                ORDER BY cutting_plan_id
                """,
                (rs, rowNum) -> CuttingPlanId.of(rs.getObject("cutting_plan_id", UUID.class)),
                lineId);
    }

    private Optional<HeaderRow> findHeader(MaterialTransferTemplateId templateId) {
        try {
            HeaderRow row =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id, source_order_id, source_warehouse_id, destination_warehouse_id,
                                   created_at, updated_at, version, status, confirmed_at
                            FROM production.material_transfer_templates
                            WHERE id = ?
                            """,
                            (rs, rowNum) ->
                                    new HeaderRow(
                                            rs.getObject("source_order_id", UUID.class),
                                            rs.getObject("source_warehouse_id", UUID.class),
                                            rs.getObject("destination_warehouse_id", UUID.class),
                                            rs.getTimestamp("created_at").toInstant(),
                                            rs.getTimestamp("updated_at").toInstant(),
                                            rs.getLong("version"),
                                            MaterialTransferTemplateStatus.valueOf(
                                                    rs.getString("status")),
                                            rs.getTimestamp("confirmed_at") == null
                                                    ? null
                                                    : rs.getTimestamp("confirmed_at").toInstant()),
                            templateId.value());
            return Optional.ofNullable(row);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private record HeaderRow(
            UUID sourceOrderId,
            UUID sourceWarehouseId,
            UUID destinationWarehouseId,
            Instant createdAt,
            Instant updatedAt,
            long version,
            MaterialTransferTemplateStatus status,
            Instant confirmedAt) {}

    private record LineRow(
            UUID id,
            UUID materialReferenceId,
            String materialCode,
            String materialName,
            String color,
            String unitOfMeasure,
            BigDecimal recommendedQuantity,
            BigDecimal requestedQuantity,
            boolean included,
            MaterialPlanningSource planningSource,
            UUID cuttingPlanId,
            CuttingLinkStatus cuttingLinkStatus,
            BigDecimal requiredQuantity,
            BigDecimal mainWarehouseAvailable,
            BigDecimal productionWarehouseAvailable,
            BigDecimal uncoveredDeficit) {}
}
