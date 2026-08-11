package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.repository.MaterialReferenceRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** JDBC adapter for {@code warehouse.material_references}. */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate and Clock injected by the container.")
public final class JdbcMaterialReferenceRepository implements MaterialReferenceRepository {

    private static final RowMapper<MaterialReference> MATERIAL_MAPPER =
            JdbcMaterialReferenceRepository::mapMaterial;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public JdbcMaterialReferenceRepository(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public MaterialReference create(MaterialReference material) {
        Objects.requireNonNull(material, "material");
        Instant now = clock.instant();
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.material_references (
                    id, article, name, color, size, unit_of_measure, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                material.id().value(),
                material.article(),
                material.name(),
                material.color(),
                material.size(),
                material.unitOfMeasure(),
                Timestamp.from(now),
                Timestamp.from(now));
        return material;
    }

    @Override
    public Optional<MaterialReference> findById(MaterialReferenceId id) {
        Objects.requireNonNull(id, "id");
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id, article, name, color, size, unit_of_measure
                            FROM warehouse.material_references
                            WHERE id = ?
                            """,
                            MATERIAL_MAPPER,
                            id.value()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<MaterialReference> findByNaturalKey(
            String article, String color, String size, String unitOfMeasure) {
        MaterialReference probe =
                MaterialReference.legacyArticle(article);
        String normalizedColor = probe.color();
        String normalizedSize = probe.size();
        String normalizedUnit = probe.unitOfMeasure();
        if (color != null) {
            normalizedColor = color.trim();
        }
        if (size != null) {
            normalizedSize = size.trim();
        }
        if (unitOfMeasure != null) {
            normalizedUnit = unitOfMeasure.trim();
        }
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject(
                            """
                            SELECT id, article, name, color, size, unit_of_measure
                            FROM warehouse.material_references
                            WHERE article = ?
                              AND color = ?
                              AND size = ?
                              AND unit_of_measure = ?
                            """,
                            MATERIAL_MAPPER,
                            probe.article(),
                            normalizedColor,
                            normalizedSize,
                            normalizedUnit));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<MaterialReference> findAll() {
        return jdbcTemplate.query(
                """
                SELECT id, article, name, color, size, unit_of_measure
                FROM warehouse.material_references
                ORDER BY article, color, size, unit_of_measure
                """,
                MATERIAL_MAPPER);
    }

    private static MaterialReference mapMaterial(ResultSet rs, int rowNum) throws SQLException {
        return MaterialReference.rehydrate(
                MaterialReferenceId.of(rs.getObject("id", UUID.class)),
                rs.getString("article"),
                rs.getString("name"),
                rs.getString("color"),
                rs.getString("size"),
                rs.getString("unit_of_measure"));
    }
}
