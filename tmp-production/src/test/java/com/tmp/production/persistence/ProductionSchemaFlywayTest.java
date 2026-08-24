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

/** Verifies production-owned Flyway migrations V23–V31. */
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
    void productionSchemaContainsItemStateCuttingLinkAndTransferTemplateTables() {
        List<String> tables =
                jdbc.queryForList(
                        """
                        SELECT table_name FROM information_schema.tables
                        WHERE table_schema = 'production'
                        ORDER BY table_name
                        """,
                        String.class);

        assertEquals(
                List.of(
                        "material_transfer_operation_refs",
                        "material_transfer_template_line_cutting_refs",
                        "material_transfer_template_line_source_items",
                        "material_transfer_template_lines",
                        "material_transfer_templates",
                        "material_transfers",
                        "production_cancellation_item_lines",
                        "production_cancellations",
                        "production_history",
                        "production_item_cutting_plan_links",
                        "production_item_states",
                        "production_release_item_lines",
                        "production_release_material_lines",
                        "production_releases"),
                tables);
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
    void flywayRecordsV23V26V27V28V29V30AndV31Migrations() {
        Integer applied23 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '23' AND success = TRUE
                        """,
                        Integer.class);
        Integer applied26 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '26' AND success = TRUE
                        """,
                        Integer.class);
        Integer applied27 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '27' AND success = TRUE
                        """,
                        Integer.class);
        Integer applied28 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '28' AND success = TRUE
                        """,
                        Integer.class);
        Integer applied29 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '29' AND success = TRUE
                        """,
                        Integer.class);
        Integer applied30 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '30' AND success = TRUE
                        """,
                        Integer.class);
        Integer applied31 =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM flyway_schema_history
                        WHERE version = '31' AND success = TRUE
                        """,
                        Integer.class);
        assertEquals(1, applied23);
        assertEquals(1, applied26);
        assertEquals(1, applied27);
        assertEquals(1, applied28);
        assertEquals(1, applied29);
        assertEquals(1, applied30);
        assertEquals(1, applied31);
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
    void cuttingPlanLinkUniqueConstraintIsItemPlusMaterial() {
        Integer unique =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = 'production'
                          AND table_name = 'production_item_cutting_plan_links'
                          AND constraint_name = 'uk_production_item_cutting_plan_links_item_material'
                          AND constraint_type = 'UNIQUE'
                        """,
                        Integer.class);
        assertEquals(1, unique);
    }

    @Test
    void cuttingPlanLinkForeignKeyTargetsOnlyProductionItemStates() {
        Integer productionFk =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.referential_constraints rc
                        JOIN information_schema.constraint_table_usage ctu
                          ON rc.unique_constraint_name = ctu.constraint_name
                         AND rc.unique_constraint_schema = ctu.constraint_schema
                        WHERE rc.constraint_schema = 'production'
                          AND rc.constraint_name = 'fk_production_item_cutting_plan_links_item'
                          AND ctu.table_schema = 'production'
                          AND ctu.table_name = 'production_item_states'
                        """,
                        Integer.class);
        assertEquals(1, productionFk);

        Integer crossCapabilityFk =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.referential_constraints rc
                        JOIN information_schema.constraint_table_usage ctu
                          ON rc.unique_constraint_name = ctu.constraint_name
                         AND rc.unique_constraint_schema = ctu.constraint_schema
                        WHERE rc.constraint_schema = 'production'
                          AND rc.constraint_name = 'fk_production_item_cutting_plan_links_item'
                          AND ctu.table_schema IN ('warehouse', 'cutting', 'order_management')
                        """,
                        Integer.class);
        assertEquals(0, crossCapabilityFk);
    }

    @Test
    void noCrossSchemaFkFromCuttingPlanLinks() {
        Integer foreignFks =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_name = ccu.constraint_name
                         AND tc.constraint_schema = ccu.constraint_schema
                        WHERE tc.table_schema = 'production'
                          AND tc.table_name = 'production_item_cutting_plan_links'
                          AND tc.constraint_type = 'FOREIGN KEY'
                          AND ccu.table_schema <> 'production'
                        """,
                        Integer.class);
        assertEquals(0, foreignFks);
    }

    @Test
    void cuttingPlanIdIndexExists() {
        Integer indexes =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM pg_indexes
                        WHERE schemaname = 'production'
                          AND tablename = 'production_item_cutting_plan_links'
                          AND indexname = 'idx_production_item_cutting_plan_links_cutting_plan_id'
                        """,
                        Integer.class);
        assertEquals(1, indexes);
    }

    @Test
    void noCrossSchemaFkFromMaterialTransferTemplates() {
        Integer foreignFks =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.constraint_column_usage ccu
                          ON tc.constraint_name = ccu.constraint_name
                         AND tc.constraint_schema = ccu.constraint_schema
                        WHERE tc.table_schema = 'production'
                          AND (
                            tc.table_name LIKE 'material_transfer_template%'
                            OR tc.table_name IN (
                                'material_transfers', 'material_transfer_operation_refs')
                            OR tc.table_name LIKE 'production_release%'
                          )
                          AND tc.constraint_type = 'FOREIGN KEY'
                          AND ccu.table_schema <> 'production'
                        """,
                        Integer.class);
        assertEquals(0, foreignFks);
    }

    @Test
    void materialTransferTemplateIdIsUniqueOnLogicalTransfer() {
        Integer unique =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.table_constraints
                        WHERE table_schema = 'production'
                          AND table_name = 'material_transfers'
                          AND constraint_name = 'uk_material_transfers_template_id'
                          AND constraint_type = 'UNIQUE'
                        """,
                        Integer.class);
        assertEquals(1, unique);
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

    @Test
    void cuttingPlanLinkTableHasNoRevisionColumns() {
        Integer revisionColumns =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'production'
                          AND table_name = 'production_item_cutting_plan_links'
                          AND column_name ILIKE '%revision%'
                        """,
                        Integer.class);
        assertEquals(0, revisionColumns);
    }
}
