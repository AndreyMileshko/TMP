package com.tmp.warehouse.persistence;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.MaterialReferenceId;
import com.tmp.warehouse.domain.MaterialReservationLink;
import com.tmp.warehouse.domain.MaterialReservationLinkId;
import com.tmp.warehouse.domain.ReservationTargetReference;
import com.tmp.warehouse.domain.ReservationTargetType;
import com.tmp.warehouse.domain.StockQuantity;
import com.tmp.warehouse.domain.repository.MaterialReservationLinkRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC adapter for informational {@link MaterialReservationLink} persistence.
 *
 * <p>Does not touch {@code stock_positions} or {@code warehouse_movements}.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcMaterialReservationLinkRepository
        implements MaterialReservationLinkRepository {

    private static final RowMapper<MaterialReservationLink> LINK_MAPPER =
            JdbcMaterialReservationLinkRepository::mapLink;

    private final JdbcTemplate jdbcTemplate;

    public JdbcMaterialReservationLinkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public MaterialReservationLink create(MaterialReservationLink link) {
        Objects.requireNonNull(link, "link");
        jdbcTemplate.update(
                """
                INSERT INTO warehouse.material_reservation_links (
                    id, material_reference_id, target_type, target_reference, quantity, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                link.id().value(),
                link.material().id().value(),
                link.target().type().name(),
                link.target().reference(),
                link.quantity().value(),
                Timestamp.from(link.createdAt()));
        return link;
    }

    @Override
    public Optional<MaterialReservationLink> findById(MaterialReservationLinkId id) {
        Objects.requireNonNull(id, "id");
        List<MaterialReservationLink> found =
                jdbcTemplate.query(
                        """
                        SELECT mrl.id, mrl.target_type, mrl.target_reference, mrl.quantity, mrl.created_at,
                               mr.id AS material_id, mr.article, mr.name, mr.color, mr.size,
                               mr.unit_of_measure
                        FROM warehouse.material_reservation_links mrl
                        JOIN warehouse.material_references mr ON mrl.material_reference_id = mr.id
                        WHERE mrl.id = ?
                        """,
                        LINK_MAPPER,
                        id.value());
        return found.stream().findFirst();
    }

    @Override
    public List<MaterialReservationLink> findByMaterial(MaterialReference material) {
        Objects.requireNonNull(material, "material");
        return jdbcTemplate.query(
                """
                SELECT mrl.id, mrl.target_type, mrl.target_reference, mrl.quantity, mrl.created_at,
                       mr.id AS material_id, mr.article, mr.name, mr.color, mr.size,
                       mr.unit_of_measure
                FROM warehouse.material_reservation_links mrl
                JOIN warehouse.material_references mr ON mrl.material_reference_id = mr.id
                WHERE mrl.material_reference_id = ?
                ORDER BY mrl.created_at, mrl.id
                """,
                LINK_MAPPER,
                material.id().value());
    }

    @Override
    public List<MaterialReservationLink> findByTarget(ReservationTargetReference target) {
        Objects.requireNonNull(target, "target");
        return jdbcTemplate.query(
                """
                SELECT mrl.id, mrl.target_type, mrl.target_reference, mrl.quantity, mrl.created_at,
                       mr.id AS material_id, mr.article, mr.name, mr.color, mr.size,
                       mr.unit_of_measure
                FROM warehouse.material_reservation_links mrl
                JOIN warehouse.material_references mr ON mrl.material_reference_id = mr.id
                WHERE mrl.target_type = ? AND mrl.target_reference = ?
                ORDER BY mrl.created_at, mrl.id
                """,
                LINK_MAPPER,
                target.type().name(),
                target.reference());
    }

    private static MaterialReservationLink mapLink(ResultSet rs, int rowNum) throws SQLException {
        return MaterialReservationLink.rehydrate(
                MaterialReservationLinkId.of(rs.getObject("id", UUID.class)),
                MaterialReference.rehydrate(
                        MaterialReferenceId.of(rs.getObject("material_id", UUID.class)),
                        rs.getString("article"),
                        rs.getString("name"),
                        rs.getString("color"),
                        rs.getString("size"),
                        rs.getString("unit_of_measure")),
                ReservationTargetReference.of(
                        ReservationTargetType.valueOf(rs.getString("target_type")),
                        rs.getString("target_reference")),
                StockQuantity.of(rs.getBigDecimal("quantity")),
                rs.getTimestamp("created_at").toInstant());
    }
}
