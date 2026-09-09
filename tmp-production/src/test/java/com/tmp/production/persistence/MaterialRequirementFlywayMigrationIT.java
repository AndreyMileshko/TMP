package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Stage 3.5.10: verifies production classpath migrations apply V43 Material Requirement tables
 * and V44 Submit lifecycle/traceability.
 *
 * <p>Production module migrations are V23–V31 + V43–V44 (warehouse owns V32–V42 in the full app).
 */
@Testcontainers
class MaterialRequirementFlywayMigrationIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void flywayHistoryIncludesVersion43And44() {
        Integer applied43 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '43' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied43);
        Integer applied44 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '44' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied44);

        String latest =
                jdbc.queryForObject(
                        """
                        SELECT version FROM flyway_schema_history
                        WHERE success = TRUE
                        ORDER BY installed_rank DESC
                        LIMIT 1
                        """,
                        String.class);
        assertEquals("44", latest);

        Integer applied42 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '42' AND success = TRUE
                        """,
                        Integer.class);
        // Warehouse-owned V42 is present when warehouse migrations are on the classpath;
        // production-only classpath may jump V31 → V43. Either way V43 must be the tip.
        if (applied42 != null && applied42 > 0) {
            Integer rank42 =
                    jdbc.queryForObject(
                            """
                            SELECT installed_rank FROM flyway_schema_history
                            WHERE version = '42' AND success = TRUE
                            """,
                            Integer.class);
            Integer rank43 =
                    jdbc.queryForObject(
                            """
                            SELECT installed_rank FROM flyway_schema_history
                            WHERE version = '43' AND success = TRUE
                            """,
                            Integer.class);
            assertTrue(rank43 != null && rank42 != null && rank43 > rank42);
        }
    }

    @Test
    void materialRequirementTablesExist() {
        List<String> tables =
                jdbc.queryForList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'production'
                          AND table_name LIKE 'material_requirement%'
                        ORDER BY table_name
                        """,
                        String.class);
        assertEquals(
                List.of(
                        "material_requirement_generated_documents",
                        "material_requirement_line_source_items",
                        "material_requirement_lines",
                        "material_requirement_routing_snapshot",
                        "material_requirements"),
                tables);
    }

    @Test
    void materialRequirementsHaveNoSourceWarehouseColumn() {
        Integer sourceWarehouseColumns =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'production'
                          AND table_name = 'material_requirements'
                          AND column_name ILIKE '%source_warehouse%'
                        """,
                        Integer.class);
        assertEquals(0, sourceWarehouseColumns);
    }

    @Test
    void materialRequirementLinesHaveSingleQuantityColumn() {
        Integer quantityColumns =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'production'
                          AND table_name = 'material_requirement_lines'
                          AND column_name IN (
                            'quantity',
                            'recommended_quantity',
                            'requested_quantity',
                            'required_quantity',
                            'main_warehouse_available',
                            'production_warehouse_available',
                            'uncovered_deficit'
                          )
                        """,
                        Integer.class);
        assertEquals(1, quantityColumns);

        Integer submittedAllowed =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.check_constraints
                        WHERE constraint_schema = 'production'
                          AND constraint_name = 'chk_material_requirements_status'
                          AND check_clause ILIKE '%SUBMITTED%'
                        """,
                        Integer.class);
        assertTrue(submittedAllowed >= 1);
    }
}
