package com.tmp.production.persistence;

import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionRelease;
import com.tmp.production.domain.ProductionReleaseImmutableException;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.ProductionReleaseRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JDBC adapter for Production Release payload tables ({@code production.production_releases*}).
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcProductionReleaseRepository implements ProductionReleaseRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcProductionReleaseRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public ProductionRelease saveDraft(ProductionRelease release) {
        Objects.requireNonNull(release, "release");
        if (release.posted()) {
            throw new IllegalArgumentException("saveDraft rejects posted release payload");
        }
        Optional<HeaderRow> existing = findHeader(release.documentId());
        if (existing.isPresent() && existing.orElseThrow().posted()) {
            throw new ProductionReleaseImmutableException(release.documentId());
        }
        Instant now = clock.instant();
        if (existing.isEmpty()) {
            insertHeader(release, now);
        } else {
            updateHeaderDraft(release, now);
            deleteChildren(release.documentId());
        }
        insertItemLines(release);
        insertMaterialLines(release);
        return findByDocumentId(release.documentId()).orElseThrow();
    }

    @Override
    public ProductionRelease markPosted(ProductionRelease release) {
        Objects.requireNonNull(release, "release");
        if (!release.posted()) {
            throw new IllegalArgumentException("markPosted requires posted=true domain state");
        }
        Instant now = clock.instant();
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE production.production_releases
                           SET posted = TRUE, updated_at = ?
                         WHERE document_id = ? AND posted = FALSE
                        """,
                        Timestamp.from(now),
                        release.documentId());
        if (updated != 1) {
            Optional<HeaderRow> existing = findHeader(release.documentId());
            if (existing.isPresent() && existing.orElseThrow().posted()) {
                throw new ProductionReleaseImmutableException(release.documentId());
            }
            throw new IllegalStateException(
                    "Production Release not found for markPosted: " + release.documentId());
        }
        return findByDocumentId(release.documentId()).orElseThrow();
    }

    @Override
    public Optional<ProductionRelease> findByDocumentId(UUID documentId) {
        Objects.requireNonNull(documentId, "documentId");
        Optional<HeaderRow> header = findHeader(documentId);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        HeaderRow row = header.orElseThrow();
        List<ProductionRelease.ItemLine> itemLines = loadItemLines(documentId);
        List<ProductionRelease.MaterialLine> materialLines = loadMaterialLines(documentId);
        return Optional.of(
                ProductionRelease.restore(
                        row.documentId(),
                        SourceOrderId.of(row.sourceOrderId()),
                        row.releasedAt(),
                        itemLines,
                        materialLines,
                        row.posted()));
    }

    private Optional<HeaderRow> findHeader(UUID documentId) {
        try {
            return Optional.of(
                    jdbcTemplate.queryForObject(
                            """
                            SELECT document_id, source_order_id, released_at, posted
                              FROM production.production_releases
                             WHERE document_id = ?
                            """,
                            (rs, rowNum) ->
                                    new HeaderRow(
                                            rs.getObject("document_id", UUID.class),
                                            rs.getObject("source_order_id", UUID.class),
                                            rs.getTimestamp("released_at").toInstant(),
                                            rs.getBoolean("posted")),
                            documentId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private void insertHeader(ProductionRelease release, Instant now) {
        jdbcTemplate.update(
                """
                INSERT INTO production.production_releases
                    (document_id, source_order_id, released_at, posted, created_at, updated_at)
                VALUES (?, ?, ?, FALSE, ?, ?)
                """,
                release.documentId(),
                release.sourceOrderId().value(),
                Timestamp.from(release.releasedAt()),
                Timestamp.from(now),
                Timestamp.from(now));
    }

    private void updateHeaderDraft(ProductionRelease release, Instant now) {
        jdbcTemplate.update(
                """
                UPDATE production.production_releases
                   SET source_order_id = ?, released_at = ?, updated_at = ?
                 WHERE document_id = ? AND posted = FALSE
                """,
                release.sourceOrderId().value(),
                Timestamp.from(release.releasedAt()),
                Timestamp.from(now),
                release.documentId());
    }

    private void deleteChildren(UUID documentId) {
        jdbcTemplate.update(
                "DELETE FROM production.production_release_material_lines WHERE document_id = ?",
                documentId);
        jdbcTemplate.update(
                "DELETE FROM production.production_release_item_lines WHERE document_id = ?",
                documentId);
    }

    private void insertItemLines(ProductionRelease release) {
        int order = 0;
        for (ProductionRelease.ItemLine line : release.itemLines()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.production_release_item_lines
                        (id, document_id, source_order_item_id, specification_id,
                         release_quantity, line_order)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    release.documentId(),
                    line.sourceOrderItemId().value(),
                    line.specificationId().value(),
                    line.releaseQuantity().value(),
                    order++);
        }
    }

    private void insertMaterialLines(ProductionRelease release) {
        int order = 0;
        for (ProductionRelease.MaterialLine line : release.materialLines()) {
            jdbcTemplate.update(
                    """
                    INSERT INTO production.production_release_material_lines
                        (id, document_id, material_reference_id, planned_quantity, actual_quantity,
                         planning_source, cutting_plan_id, source_order_item_id, comment_text,
                         line_order)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    release.documentId(),
                    line.materialReferenceId().value(),
                    line.plannedQuantity(),
                    line.actualQuantity(),
                    line.planningSource().name(),
                    line.cuttingPlanId().map(CuttingPlanId::value).orElse(null),
                    line.sourceOrderItemId().map(SourceOrderItemId::value).orElse(null),
                    line.comment().orElse(null),
                    order++);
        }
    }

    private List<ProductionRelease.ItemLine> loadItemLines(UUID documentId) {
        return jdbcTemplate.query(
                """
                SELECT source_order_item_id, specification_id, release_quantity
                  FROM production.production_release_item_lines
                 WHERE document_id = ?
                 ORDER BY line_order
                """,
                (rs, rowNum) ->
                        new ProductionRelease.ItemLine(
                                SourceOrderItemId.of(
                                        rs.getObject("source_order_item_id", UUID.class)),
                                SpecificationId.of(rs.getObject("specification_id", UUID.class)),
                                ProductionQuantity.positive(rs.getBigDecimal("release_quantity"))),
                documentId);
    }

    private List<ProductionRelease.MaterialLine> loadMaterialLines(UUID documentId) {
        List<ProductionRelease.MaterialLine> lines = new ArrayList<>();
        jdbcTemplate.query(
                """
                SELECT material_reference_id, planned_quantity, actual_quantity, planning_source,
                       cutting_plan_id, source_order_item_id, comment_text
                  FROM production.production_release_material_lines
                 WHERE document_id = ?
                 ORDER BY line_order
                """,
                rs -> {
                    UUID cuttingPlanId = rs.getObject("cutting_plan_id", UUID.class);
                    UUID sourceOrderItemId = rs.getObject("source_order_item_id", UUID.class);
                    String comment = rs.getString("comment_text");
                    lines.add(
                            new ProductionRelease.MaterialLine(
                                    MaterialReferenceId.of(
                                            rs.getObject("material_reference_id", UUID.class)),
                                    rs.getBigDecimal("planned_quantity"),
                                    rs.getBigDecimal("actual_quantity"),
                                    MaterialPlanningSource.valueOf(rs.getString("planning_source")),
                                    Optional.ofNullable(cuttingPlanId).map(CuttingPlanId::of),
                                    Optional.ofNullable(sourceOrderItemId)
                                            .map(SourceOrderItemId::of),
                                    Optional.ofNullable(comment)));
                },
                documentId);
        return List.copyOf(lines);
    }

    private record HeaderRow(
            UUID documentId, UUID sourceOrderId, Instant releasedAt, boolean posted) {}
}
