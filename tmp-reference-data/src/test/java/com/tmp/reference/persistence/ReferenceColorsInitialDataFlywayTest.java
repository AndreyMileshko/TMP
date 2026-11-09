package com.tmp.reference.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.reference.domain.ColorCodeKey;
import com.tmp.reference.domain.So7SourceKey;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * COL-011: verifies Flyway V59 seed (860 colors / 245 mappings / automatic matching OFF) against
 * the approved Excel-derived fixtures and Java key normalization.
 */
@Testcontainers
class ReferenceColorsInitialDataFlywayTest {

    private static final String BEZ_CVETA = "Без цвета";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcTemplate jdbc;
    private static Flyway flyway;
    private static List<String> expectedCodes;
    private static List<ExpectedMapping> expectedMappings;

    @BeforeAll
    static void migrateAndLoadFixtures() throws IOException {
        expectedCodes = loadExpectedCodes();
        expectedMappings = loadExpectedMappings();

        flyway =
                Flyway.configure()
                        .dataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword())
                        .locations("classpath:db/migration")
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
    void seedsExactApprovedCountsAndAllActiveWithMatchingOff() {
        assertEquals(860, expectedCodes.size());
        assertEquals(245, expectedMappings.size());

        assertEquals(860, count("SELECT COUNT(*) FROM reference.colors"));
        assertEquals(860, count("SELECT COUNT(*) FROM reference.colors WHERE active = TRUE"));
        assertEquals(245, count("SELECT COUNT(*) FROM reference.so7_color_mappings"));
        assertEquals(
                245, count("SELECT COUNT(*) FROM reference.so7_color_mappings WHERE active = TRUE"));

        Map<String, Object> setting =
                jdbc.queryForMap(
                        """
                        SELECT id, automatic_matching_enabled
                        FROM reference.color_matching_settings
                        """);
        assertEquals(1, ((Number) setting.get("id")).intValue());
        assertEquals(Boolean.FALSE, setting.get("automatic_matching_enabled"));
    }

    @Test
    void seedsUniqueNormalizedKeysAndNoOrphans() {
        assertEquals(
                860, count("SELECT COUNT(DISTINCT code_key) FROM reference.colors"));
        assertEquals(
                245,
                count("SELECT COUNT(DISTINCT source_key) FROM reference.so7_color_mappings"));

        Integer orphanInside =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM reference.so7_color_mappings m
                        LEFT JOIN reference.colors c ON c.id = m.inside_color_id
                        WHERE c.id IS NULL
                        """,
                        Integer.class);
        Integer orphanOutside =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM reference.so7_color_mappings m
                        LEFT JOIN reference.colors c ON c.id = m.outside_color_id
                        WHERE c.id IS NULL
                        """,
                        Integer.class);
        assertEquals(0, orphanInside);
        assertEquals(0, orphanOutside);
    }

    @Test
    void matchesFullApprovedExcelDatasetAndJavaKeys() {
        Map<String, Map<String, Object>> colorsByCode = new HashMap<>();
        jdbc.query(
                "SELECT id, code, code_key, active FROM reference.colors",
                rs -> {
                    colorsByCode.put(
                            rs.getString("code"),
                            Map.of(
                                    "id",
                                    rs.getObject("id", UUID.class),
                                    "code_key",
                                    rs.getString("code_key"),
                                    "active",
                                    rs.getBoolean("active")));
                });

        assertEquals(new HashSet<>(expectedCodes), colorsByCode.keySet());

        for (String code : expectedCodes) {
            Map<String, Object> row = colorsByCode.get(code);
            assertNotNull(row, () -> "missing color " + code);
            assertEquals(ColorCodeKey.of(code).value(), row.get("code_key"));
            assertEquals(Boolean.TRUE, row.get("active"));
            assertTrue(code.length() <= 31);
            assertFalse(code.contains("/"));
            assertFalse(code.contains("\\"));
        }

        assertTrue(colorsByCode.containsKey("Белый"));
        assertTrue(colorsByCode.containsKey("00"));
        assertTrue(colorsByCode.containsKey("RAL7016"));
        assertTrue(colorsByCode.containsKey("RAL7016|30"));
        assertTrue(colorsByCode.containsKey("RAL7016|МУАР"));
        assertTrue(colorsByCode.containsKey("KDB74-Z8|МАСС"));
        assertTrue(colorsByCode.containsKey("KDB74-Z8|ПРИТВОР"));
        assertNotEquals(
                colorsByCode.get("Белый").get("id"), colorsByCode.get("00").get("id"));

        List<Map<String, Object>> mappingRows =
                jdbc.queryForList(
                        """
                        SELECT m.source_text, m.source_key, m.active,
                               ci.code AS inside_code, co.code AS outside_code
                        FROM reference.so7_color_mappings m
                        JOIN reference.colors ci ON ci.id = m.inside_color_id
                        JOIN reference.colors co ON co.id = m.outside_color_id
                        ORDER BY m.source_text
                        """);

        assertEquals(245, mappingRows.size());

        Map<String, ExpectedMapping> expectedBySource = new HashMap<>();
        for (ExpectedMapping mapping : expectedMappings) {
            expectedBySource.put(mapping.sourceText(), mapping);
        }
        assertEquals(245, expectedBySource.size());

        Set<String> actualSources = new HashSet<>();
        int slashSources = 0;
        for (Map<String, Object> row : mappingRows) {
            String sourceText = (String) row.get("source_text");
            actualSources.add(sourceText);
            ExpectedMapping expected = expectedBySource.get(sourceText);
            assertNotNull(expected, () -> "unexpected mapping source " + sourceText);
            assertEquals(So7SourceKey.of(sourceText).value(), row.get("source_key"));
            assertEquals(expected.insideCode(), row.get("inside_code"));
            assertEquals(expected.outsideCode(), row.get("outside_code"));
            assertEquals(Boolean.TRUE, row.get("active"));
            if (sourceText.contains("/")) {
                slashSources++;
            }
        }
        assertEquals(expectedBySource.keySet(), actualSources);
        assertEquals(33, slashSources);

        Map<String, Object> example =
                mappingRows.stream()
                        .filter(row -> "RAL7016/30т".equals(row.get("source_text")))
                        .findFirst()
                        .orElseThrow();
        assertEquals("RAL7016|30", example.get("inside_code"));
        assertEquals("RAL7016|30", example.get("outside_code"));

        boolean hasBezCveta =
                mappingRows.stream().anyMatch(row -> BEZ_CVETA.equals(row.get("source_text")));
        assertFalse(hasBezCveta);
        assertEquals(
                0,
                count(
                        "SELECT COUNT(*) FROM reference.so7_color_mappings WHERE source_key = ?",
                        So7SourceKey.of(BEZ_CVETA).value()));
    }

    @Test
    void flywayRecordsV59AndSecondMigrateIsNoOp() {
        Integer applied =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '59' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied);

        var result = flyway.migrate();
        assertEquals(0, result.migrationsExecuted);
        assertEquals(860, count("SELECT COUNT(*) FROM reference.colors"));
        assertEquals(245, count("SELECT COUNT(*) FROM reference.so7_color_mappings"));
    }

    @Test
    void replayingV59InsertsDoesNotDuplicateOrOverwriteUserChanges() throws IOException {
        UUID whiteId =
                jdbc.queryForObject(
                        "SELECT id FROM reference.colors WHERE code = ?",
                        UUID.class,
                        "Белый");
        assertNotNull(whiteId);

        jdbc.update(
                """
                UPDATE reference.colors
                SET active = FALSE, version = version + 1, updated_at = now()
                WHERE id = ?
                """,
                whiteId);

        jdbc.update(
                """
                UPDATE reference.color_matching_settings
                SET automatic_matching_enabled = TRUE, version = version + 1, updated_at = now()
                WHERE id = 1
                """);

        for (String statement : loadV59Statements()) {
            jdbc.execute(statement);
        }

        assertEquals(860, count("SELECT COUNT(*) FROM reference.colors"));
        assertEquals(245, count("SELECT COUNT(*) FROM reference.so7_color_mappings"));
        assertEquals(
                1, count("SELECT COUNT(*) FROM reference.color_matching_settings"));

        Map<String, Object> white =
                jdbc.queryForMap("SELECT active, version FROM reference.colors WHERE id = ?", whiteId);
        assertEquals(Boolean.FALSE, white.get("active"));
        assertEquals(1L, ((Number) white.get("version")).longValue());

        Map<String, Object> setting =
                jdbc.queryForMap(
                        """
                        SELECT automatic_matching_enabled, version
                        FROM reference.color_matching_settings WHERE id = 1
                        """);
        assertEquals(Boolean.TRUE, setting.get("automatic_matching_enabled"));
        assertEquals(1L, ((Number) setting.get("version")).longValue());

        // Restore shared container state for sibling tests (JUnit method order is not guaranteed).
        jdbc.update(
                """
                UPDATE reference.colors
                SET active = TRUE, version = 0, updated_at = now()
                WHERE id = ?
                """,
                whiteId);
        jdbc.update(
                """
                UPDATE reference.color_matching_settings
                SET automatic_matching_enabled = FALSE, version = 0, updated_at = now()
                WHERE id = 1
                """);
    }

    private static int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? -1 : value;
    }

    private static List<String> loadExpectedCodes() throws IOException {
        List<String> codes = new ArrayList<>();
        try (BufferedReader reader = openResource("reference/seed/expected-colors.tsv")) {
            String header = reader.readLine();
            assertEquals("code", header);
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    codes.add(line);
                }
            }
        }
        return codes;
    }

    private static List<ExpectedMapping> loadExpectedMappings() throws IOException {
        List<ExpectedMapping> mappings = new ArrayList<>();
        try (BufferedReader reader = openResource("reference/seed/expected-mappings.tsv")) {
            String header = reader.readLine();
            assertEquals("source_text\tinside_code\toutside_code", header);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                String fixtureLine = line;
                assertEquals(3, parts.length, () -> "bad mapping fixture line: " + fixtureLine);
                mappings.add(new ExpectedMapping(parts[0], parts[1], parts[2]));
            }
        }
        return mappings;
    }

    private static List<String> loadV59Statements() throws IOException {
        String sql =
                new String(
                        new ClassPathResource("db/migration/V59__reference_colors_initial_data.sql")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8);

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new StringReader(sql))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (trimmed.endsWith(";")) {
                    statements.add(current.toString());
                    current.setLength(0);
                }
            }
        }
        assertFalse(statements.isEmpty());
        return statements;
    }

    private static BufferedReader openResource(String path) throws IOException {
        InputStream in =
                ReferenceColorsInitialDataFlywayTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, () -> "missing classpath resource " + path);
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    private record ExpectedMapping(String sourceText, String insideCode, String outsideCode) {}
}
