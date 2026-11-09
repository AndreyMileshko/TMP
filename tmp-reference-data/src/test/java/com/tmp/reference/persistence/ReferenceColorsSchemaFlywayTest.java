package com.tmp.reference.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies Flyway V58 creates the Reference Data Colors schema without seed data.
 *
 * <p>Target is pinned to version 58 so COL-011 seed (V59) does not affect schema-only assertions.
 */
@Testcontainers
class ReferenceColorsSchemaFlywayTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static Flyway flyway;

    @BeforeAll
    static void migrate() {
        flyway =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .locations("classpath:db/migration")
                        .target("58")
                        .load();
        flyway.migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void v58CreatesReferenceSchemaAndThreeEmptyTables() {
        Integer schemaCount =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.schemata
                        WHERE schema_name = 'reference'
                        """,
                        Integer.class);
        assertEquals(1, schemaCount);

        List<String> tables =
                jdbc.queryForList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'reference'
                        ORDER BY table_name
                        """,
                        String.class);
        assertEquals(
                List.of("color_matching_settings", "colors", "so7_color_mappings"), tables);

        assertEquals(0, countRows("colors"));
        assertEquals(0, countRows("so7_color_mappings"));
        assertEquals(0, countRows("color_matching_settings"));
    }

    @Test
    void v58DefinesRequiredUniquenessForeignKeysAndChecks() {
        assertConstraintExists("colors", "uk_colors_code_key");
        assertConstraintExists("colors", "chk_colors_code_non_blank");
        assertConstraintExists("colors", "chk_colors_code_no_slash");
        assertConstraintExists("colors", "chk_colors_version");

        assertConstraintExists("so7_color_mappings", "uk_so7_color_mappings_source_key");
        assertConstraintExists("so7_color_mappings", "fk_so7_color_mappings_inside_color");
        assertConstraintExists("so7_color_mappings", "fk_so7_color_mappings_outside_color");
        assertConstraintExists("so7_color_mappings", "chk_so7_color_mappings_source_non_blank");
        assertConstraintExists("so7_color_mappings", "chk_so7_color_mappings_version");

        assertConstraintExists("color_matching_settings", "chk_color_matching_settings_singleton");
        assertConstraintExists("color_matching_settings", "chk_color_matching_settings_version");

        Integer insideIndex =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM pg_indexes
                        WHERE schemaname = 'reference'
                          AND indexname = 'idx_so7_color_mappings_inside_color_id'
                        """,
                        Integer.class);
        Integer outsideIndex =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM pg_indexes
                        WHERE schemaname = 'reference'
                          AND indexname = 'idx_so7_color_mappings_outside_color_id'
                        """,
                        Integer.class);
        assertEquals(1, insideIndex);
        assertEquals(1, outsideIndex);
    }

    @Test
    void colorAndMappingCodeColumnsUseApprovedLengths() {
        Map<String, Object> colorCode =
                jdbc.queryForMap(
                        """
                        SELECT character_maximum_length AS max_len
                        FROM information_schema.columns
                        WHERE table_schema = 'reference'
                          AND table_name = 'colors'
                          AND column_name = 'code'
                        """);
        Map<String, Object> colorKey =
                jdbc.queryForMap(
                        """
                        SELECT character_maximum_length AS max_len
                        FROM information_schema.columns
                        WHERE table_schema = 'reference'
                          AND table_name = 'colors'
                          AND column_name = 'code_key'
                        """);
        Map<String, Object> sourceText =
                jdbc.queryForMap(
                        """
                        SELECT character_maximum_length AS max_len
                        FROM information_schema.columns
                        WHERE table_schema = 'reference'
                          AND table_name = 'so7_color_mappings'
                          AND column_name = 'source_text'
                        """);
        Map<String, Object> sourceKey =
                jdbc.queryForMap(
                        """
                        SELECT character_maximum_length AS max_len
                        FROM information_schema.columns
                        WHERE table_schema = 'reference'
                          AND table_name = 'so7_color_mappings'
                          AND column_name = 'source_key'
                        """);

        assertEquals(31, ((Number) colorCode.get("max_len")).intValue());
        assertEquals(31, ((Number) colorKey.get("max_len")).intValue());
        assertEquals(64, ((Number) sourceText.get("max_len")).intValue());
        assertEquals(64, ((Number) sourceKey.get("max_len")).intValue());
    }

    @Test
    void flywayRecordsV58AndSecondMigrateIsNoOp() {
        Integer applied =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '58' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied);

        var result = flyway.migrate();
        assertEquals(0, result.migrationsExecuted);

        Integer stillOne =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '58' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, stillOne);
        assertEquals(0, countRows("colors"));
        assertEquals(0, countRows("so7_color_mappings"));
        assertEquals(0, countRows("color_matching_settings"));
    }

    private static int countRows(String tableName) {
        Integer count =
                jdbc.queryForObject("SELECT COUNT(*) FROM reference." + tableName, Integer.class);
        return count == null ? -1 : count;
    }

    private static void assertConstraintExists(String tableName, String constraintName) {
        Integer count =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = 'reference'
                          AND table_name = ?
                          AND constraint_name = ?
                        """,
                        Integer.class,
                        tableName,
                        constraintName);
        assertEquals(1, count, () -> "Missing constraint " + constraintName + " on " + tableName);
        assertTrue(count == 1);
    }
}
