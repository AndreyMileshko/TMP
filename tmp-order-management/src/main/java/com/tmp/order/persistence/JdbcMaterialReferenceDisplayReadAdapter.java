package com.tmp.order.persistence;

import com.tmp.order.api.MaterialReferenceDisplayDto;
import com.tmp.order.application.query.MaterialReferenceDisplayReadPort;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Resolves material display fields from ACTIVE Specification lines in Order Management.
 *
 * <p>Read-only. Does not create or mutate material master data.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "Stores Spring-managed JdbcTemplate injected by the container.")
public final class JdbcMaterialReferenceDisplayReadAdapter
        implements MaterialReferenceDisplayReadPort {

    private final JdbcTemplate jdbc;

    public JdbcMaterialReferenceDisplayReadAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbc = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
    }

    @Override
    public Optional<MaterialReferenceDisplayDto> findByMaterialCode(String materialCode) {
        Objects.requireNonNull(materialCode, "materialCode");
        String normalized = materialCode.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        List<MaterialReferenceDisplayDto> rows =
                jdbc.query(
                        """
                        SELECT l.material_code, l.material_name, l.color, l.length_mm, l.unit_of_measure
                        FROM order_management.item_specification_lines l
                        JOIN order_management.order_item_revisions r
                          ON r.order_item_id = l.order_item_id
                         AND r.revision_number = l.revision_number
                        WHERE l.material_code = ?
                          AND r.revision_status = 'ACTIVE'
                        ORDER BY l.line_number ASC
                        LIMIT 1
                        """,
                        (rs, rowNum) ->
                                MaterialReferenceDisplayDto.of(
                                        rs.getString("material_code"),
                                        rs.getString("material_name"),
                                        rs.getString("color"),
                                        formatSize(rs.getBigDecimal("length_mm")),
                                        rs.getString("unit_of_measure")),
                        normalized);
        return rows.stream().findFirst();
    }

    private static String formatSize(BigDecimal lengthMm) {
        if (lengthMm == null) {
            return "";
        }
        return lengthMm.stripTrailingZeros().toPlainString() + " мм";
    }
}
