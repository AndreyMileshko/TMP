package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies production-owned Flyway migration V23. */
@Testcontainers
class ProductionSchemaFlywayTest {

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
    void v23CreatesProductionItemStateTable() {
        List<String> tables =
                jdbc.queryForList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'production'
                        ORDER BY table_name
                        """,
                        String.class);

        assertEquals(List.of("production_item_states"), tables);
    }

    @Test
    void specificationIdColumnIsNotNull() {
        Integer nullable =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'production'
                          AND table_name = 'production_item_states'
                          AND column_name = 'specification_id'
                          AND is_nullable = 'NO'
                        """,
                        Integer.class);
        assertEquals(1, nullable);
    }

    @Test
    void forbiddenProductionOrderTablesDoNotExist() {
        Integer forbidden =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'production'
                          AND (
                            table_name ILIKE '%production_order%'
                            OR table_name ILIKE '%revision%'
                            OR table_name ILIKE '%batch%'
                          )
                        """,
                        Integer.class);
        assertEquals(0, forbidden);
    }

    @Test
    void flywayRecordsV23Migration() {
        Integer applied =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '23' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied);
    }

    @Test
    void businessIdentityUniqueConstraintExists() {
        Integer unique =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = 'production'
                          AND table_name = 'production_item_states'
                          AND constraint_name = 'uk_production_item_states_identity'
                        """,
                        Integer.class);
        assertTrue(unique >= 1);
    }

    @Test
    void sourceOrderIdIndexExists() {
        Integer indexes =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM pg_indexes
                        WHERE schemaname = 'production'
                          AND tablename = 'production_item_states'
                          AND indexname = 'idx_production_item_states_source_order_id'
                        """,
                        Integer.class);
        assertEquals(1, indexes);
    }

    @Test
    void noStoredOrderLevelProductionStatus() {
        Integer orderStatusColumns =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'production'
                          AND (
                            column_name ILIKE '%order_production_status%'
                            OR column_name ILIKE 'order_cancelled'
                          )
                        """,
                        Integer.class);
        assertEquals(0, orderStatusColumns);

        Integer orderLevelTables =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'production'
                          AND (
                            table_name ILIKE '%order_production%'
                            OR table_name ILIKE '%production_order%'
                          )
                        """,
                        Integer.class);
        assertEquals(0, orderLevelTables);
    }
}
