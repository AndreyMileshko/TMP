package com.tmp.warehouse.testsupport;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.persistence.JdbcMaterialReferenceRepository;
import java.time.Clock;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC integration test helpers for warehouse-owned materials. */
public final class WarehouseJdbcTestSupport {

    private WarehouseJdbcTestSupport() {}

    public static MaterialReference persistMaterial(
            JdbcTemplate jdbc, Clock clock, MaterialReference material) {
        return new JdbcMaterialReferenceRepository(jdbc, clock).create(material);
    }

    public static MaterialReference persistLegacyArticle(JdbcTemplate jdbc, Clock clock, String article) {
        return persistMaterial(jdbc, clock, MaterialReference.legacyArticle(article));
    }

    public static void clearMaterialReferences(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM warehouse.material_references");
    }
}
