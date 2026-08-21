package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.CuttingLinkStatus;
import com.tmp.production.domain.CuttingPlanId;
import com.tmp.production.domain.MaterialPlanningSource;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialTransferTemplate;
import com.tmp.production.domain.MaterialTransferTemplateLine;
import com.tmp.production.domain.MaterialTransferTemplateOptimisticLockException;
import com.tmp.production.domain.MaterialTransferTemplateStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialTransferTemplateRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JdbcMaterialTransferTemplateRepositoryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-21T06:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-08-21T06:00:00Z");
    private static final UUID MAIN = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000012");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private MaterialTransferTemplateRepository repository;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUsername(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        dataSource = ds;
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM production.material_transfer_operation_refs");
        jdbc.update("DELETE FROM production.material_transfers");
        jdbc.update("DELETE FROM production.material_transfer_template_line_cutting_refs");
        jdbc.update("DELETE FROM production.material_transfer_template_line_source_items");
        jdbc.update("DELETE FROM production.material_transfer_template_lines");
        jdbc.update("DELETE FROM production.material_transfer_templates");
        jdbc.execute("DROP TRIGGER IF EXISTS trg_fail_template_line_insert ON production.material_transfer_template_lines");
        jdbc.execute("DROP FUNCTION IF EXISTS production.fail_template_line_insert()");
        repository =
                new JdbcMaterialTransferTemplateRepository(
                        jdbc, CLOCK, new DataSourceTransactionManager(dataSource));
    }

    @Test
    void saveAndLoadRoundTripPreservesTemplate() {
        CuttingPlanId plan = CuttingPlanId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        MaterialTransferTemplateLine line =
                MaterialTransferTemplateLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-1",
                        "Material One",
                        "WHITE",
                        "PCS",
                        BigDecimal.valueOf(6),
                        MaterialPlanningSource.SPECIFICATION,
                        plan,
                        CuttingLinkStatus.SINGLE,
                        List.of(plan),
                        Set.of(itemA, itemB),
                        BigDecimal.TEN,
                        BigDecimal.valueOf(20),
                        BigDecimal.valueOf(4),
                        BigDecimal.ZERO);
        MaterialTransferTemplate created =
                MaterialTransferTemplate.create(
                        SourceOrderId.generate(), MAIN, PROD, T0, List.of(line));

        MaterialTransferTemplate saved = repository.save(created);
        MaterialTransferTemplate loaded =
                repository.findById(saved.templateId()).orElseThrow();

        assertEquals(saved.templateId(), loaded.templateId());
        assertEquals(saved.sourceOrderId(), loaded.sourceOrderId());
        assertEquals(MAIN, loaded.sourceWarehouseId());
        assertEquals(PROD, loaded.destinationWarehouseId());
        assertEquals(0L, loaded.version());
        assertEquals(MaterialTransferTemplateStatus.DRAFT, loaded.status());
        assertTrue(loaded.confirmedAt().isEmpty());
        assertEquals(1, loaded.lines().size());
        MaterialTransferTemplateLine loadedLine = loaded.lines().getFirst();
        assertEquals(0, loadedLine.recommendedQuantity().compareTo(BigDecimal.valueOf(6)));
        assertEquals(0, loadedLine.requestedQuantity().compareTo(BigDecimal.valueOf(6)));
        assertEquals(CuttingLinkStatus.SINGLE, loadedLine.cuttingLinkStatus());
        assertEquals(Optional.of(plan), loadedLine.cuttingPlanId());
        assertEquals(2, loadedLine.sourceOrderItemIds().size());
        assertTrue(loadedLine.sourceOrderItemIds().contains(itemA));
        assertTrue(loadedLine.sourceOrderItemIds().contains(itemB));
    }

    @Test
    void editRoundTripKeepsRecommendedAndUpdatesRequested() {
        MaterialTransferTemplate created =
                repository.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(),
                                MAIN,
                                PROD,
                                T0,
                                List.of(lineWithoutCutting(BigDecimal.TEN))));
        MaterialTransferTemplate edited =
                created.changeRequestedQuantity(
                        created.lines().getFirst().lineId(), BigDecimal.valueOf(8), T0);

        MaterialTransferTemplate saved = repository.save(edited);
        MaterialTransferTemplate loaded =
                repository.findById(saved.templateId()).orElseThrow();

        assertEquals(1L, loaded.version());
        assertEquals(0, loaded.lines().getFirst().recommendedQuantity().compareTo(BigDecimal.TEN));
        assertEquals(
                0, loaded.lines().getFirst().requestedQuantity().compareTo(BigDecimal.valueOf(8)));
    }

    @Test
    void optimisticLockRejectsStaleUpdate() {
        MaterialTransferTemplate created =
                repository.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(),
                                MAIN,
                                PROD,
                                T0,
                                List.of(lineWithoutCutting(BigDecimal.TEN))));
        MaterialTransferTemplate firstEdit =
                created.changeRequestedQuantity(
                        created.lines().getFirst().lineId(), BigDecimal.valueOf(7), T0);
        repository.save(firstEdit);

        MaterialTransferTemplate stale =
                created.changeRequestedQuantity(
                        created.lines().getFirst().lineId(), BigDecimal.valueOf(3), T0);
        assertThrows(
                MaterialTransferTemplateOptimisticLockException.class,
                () -> repository.save(stale));
    }

    @Test
    void multipleCuttingRefsRoundTrip() {
        CuttingPlanId plan1 = CuttingPlanId.generate();
        CuttingPlanId plan2 = CuttingPlanId.generate();
        MaterialTransferTemplateLine line =
                MaterialTransferTemplateLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-2",
                        "Material Two",
                        "BLACK",
                        "PCS",
                        BigDecimal.valueOf(5),
                        MaterialPlanningSource.SPECIFICATION,
                        null,
                        CuttingLinkStatus.MULTIPLE_REFERENCES,
                        List.of(plan1, plan2),
                        Set.of(SourceOrderItemId.generate()),
                        BigDecimal.valueOf(5),
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO);
        MaterialTransferTemplate saved =
                repository.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(), MAIN, PROD, T0, List.of(line)));

        MaterialTransferTemplateLine loaded =
                repository.findById(saved.templateId()).orElseThrow().lines().getFirst();
        assertEquals(CuttingLinkStatus.MULTIPLE_REFERENCES, loaded.cuttingLinkStatus());
        assertTrue(loaded.cuttingPlanId().isEmpty());
        assertEquals(2, loaded.cuttingPlanReferences().size());
    }

    @Test
    void childRowFailureRollsBackHeaderVersionAndChildren() {
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        CuttingPlanId plan = CuttingPlanId.generate();
        MaterialTransferTemplateLine originalLine =
                MaterialTransferTemplateLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-OK",
                        "Material OK",
                        "WHITE",
                        "PCS",
                        BigDecimal.TEN,
                        MaterialPlanningSource.SPECIFICATION,
                        plan,
                        CuttingLinkStatus.SINGLE,
                        List.of(plan),
                        Set.of(itemId),
                        BigDecimal.TEN,
                        BigDecimal.valueOf(20),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO);
        MaterialTransferTemplate v0 =
                repository.save(
                        MaterialTransferTemplate.create(
                                SourceOrderId.generate(), MAIN, PROD, T0, List.of(originalLine)));

        Instant updatedAtBefore = v0.updatedAt();
        Long versionBefore =
                jdbc.queryForObject(
                        """
                        SELECT version FROM production.material_transfer_templates WHERE id = ?
                        """,
                        Long.class,
                        v0.templateId().value());
        Instant headerUpdatedAtBefore =
                jdbc.queryForObject(
                                """
                                SELECT updated_at FROM production.material_transfer_templates WHERE id = ?
                                """,
                                java.sql.Timestamp.class,
                                v0.templateId().value())
                        .toInstant();
        assertEquals(0L, versionBefore);
        assertEquals(updatedAtBefore, headerUpdatedAtBefore);

        jdbc.execute(
                """
                CREATE OR REPLACE FUNCTION production.fail_template_line_insert()
                RETURNS trigger AS $$
                BEGIN
                  IF NEW.material_code = 'FAIL-CHILD' THEN
                    RAISE EXCEPTION 'controlled child insert failure';
                  END IF;
                  RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute(
                """
                CREATE TRIGGER trg_fail_template_line_insert
                BEFORE INSERT ON production.material_transfer_template_lines
                FOR EACH ROW EXECUTE FUNCTION production.fail_template_line_insert()
                """);

        MaterialTransferTemplateLine failingLine =
                MaterialTransferTemplateLine.rehydrate(
                        originalLine.lineId(),
                        originalLine.materialReferenceId(),
                        "FAIL-CHILD",
                        originalLine.materialName(),
                        originalLine.color(),
                        originalLine.unitOfMeasure(),
                        originalLine.recommendedQuantity(),
                        BigDecimal.valueOf(7),
                        true,
                        originalLine.planningSource(),
                        plan,
                        CuttingLinkStatus.SINGLE,
                        List.of(plan),
                        Set.of(itemId),
                        originalLine.requiredQuantity(),
                        originalLine.mainWarehouseAvailable(),
                        originalLine.productionWarehouseAvailable(),
                        originalLine.uncoveredDeficit());
        MaterialTransferTemplate edited =
                MaterialTransferTemplate.rehydrate(
                        v0.templateId(),
                        v0.sourceOrderId(),
                        v0.sourceWarehouseId(),
                        v0.destinationWarehouseId(),
                        v0.createdAt(),
                        T0,
                        List.of(failingLine),
                        v0.version(),
                        MaterialTransferTemplateStatus.DRAFT,
                        null);

        assertThrows(RuntimeException.class, () -> repository.save(edited));

        MaterialTransferTemplate afterFailure =
                repository.findById(v0.templateId()).orElseThrow();
        assertEquals(0L, afterFailure.version());
        assertEquals(headerUpdatedAtBefore, afterFailure.updatedAt());
        assertEquals(1, afterFailure.lines().size());
        MaterialTransferTemplateLine persistedLine = afterFailure.lines().getFirst();
        assertEquals("MAT-OK", persistedLine.materialCode());
        assertEquals(0, persistedLine.requestedQuantity().compareTo(BigDecimal.TEN));
        assertEquals(Set.of(itemId), persistedLine.sourceOrderItemIds());
        assertEquals(List.of(plan), persistedLine.cuttingPlanReferences());
        assertEquals(MaterialTransferTemplateStatus.DRAFT, afterFailure.status());
    }

    private static MaterialTransferTemplateLine lineWithoutCutting(BigDecimal quantity) {
        return MaterialTransferTemplateLine.create(
                MaterialReferenceId.generate(),
                "MAT-Z",
                "Material Z",
                "WHITE",
                "PCS",
                quantity,
                MaterialPlanningSource.SPECIFICATION,
                null,
                CuttingLinkStatus.NONE,
                List.of(),
                Set.of(SourceOrderItemId.generate()),
                quantity,
                BigDecimal.valueOf(20),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }
}
