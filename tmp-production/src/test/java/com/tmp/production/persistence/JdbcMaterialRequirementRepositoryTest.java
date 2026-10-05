package com.tmp.production.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementOptimisticLockException;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.MaterialRequirementStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
class JdbcMaterialRequirementRepositoryTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-09T06:00:00Z"), ZoneOffset.UTC);
    private static final Instant T0 = Instant.parse("2026-09-09T06:00:00Z");
    private static final UUID PROD = UUID.fromString("00000000-0000-4000-8000-000000000012");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private MaterialRequirementRepository repository;

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
        jdbc.update("DELETE FROM production.material_requirement_routing_snapshot");
        jdbc.update("DELETE FROM production.material_requirement_generated_documents");
        jdbc.update("DELETE FROM production.material_requirement_line_source_items");
        jdbc.update("DELETE FROM production.material_requirement_lines");
        jdbc.update("DELETE FROM production.material_requirement_source_items");
        jdbc.update("DELETE FROM production.material_requirements");
        repository =
                new JdbcMaterialRequirementRepository(
                        jdbc, CLOCK, new DataSourceTransactionManager(dataSource));
    }

    @Test
    void saveAndLoadRoundTripPreservesRequirement() {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemA = SourceOrderItemId.generate();
        SourceOrderItemId itemB = SourceOrderItemId.generate();
        BigDecimal qtyA = BigDecimal.valueOf(5);
        BigDecimal qtyB = BigDecimal.valueOf(7);
        MaterialRequirementLine line =
                MaterialRequirementLine.create(
                        MaterialReferenceId.generate(),
                        "MAT-1",
                        "Material One",
                        "WHITE",
                        "PCS",
                        null,
                        BigDecimal.valueOf(12),
                        List.of(
                                MaterialRequirementLineContribution.of(orderId, itemA, qtyA),
                                MaterialRequirementLineContribution.of(orderId, itemB, qtyB)));
        MaterialRequirement created =
                MaterialRequirement.create(
                        PROD,
                        T0,
                        List.of(
                                MaterialRequirementSourceItem.of(orderId, itemA, 1L),
                                MaterialRequirementSourceItem.of(orderId, itemB, 1L)),
                        List.of(line));

        MaterialRequirement saved = repository.save(created);
        MaterialRequirement loaded = repository.findById(saved.requirementId()).orElseThrow();

        assertEquals(saved.requirementId(), loaded.requirementId());
        assertEquals(2, loaded.sourceItems().size());
        assertEquals(orderId, loaded.sourceItems().getFirst().sourceOrderId());
        assertEquals(PROD, loaded.destinationWarehouseId());
        assertEquals(0L, loaded.version());
        assertEquals(MaterialRequirementStatus.DRAFT, loaded.status());
        assertEquals(1, loaded.lines().size());
        MaterialRequirementLine loadedLine = loaded.lines().getFirst();
        assertEquals(0, loadedLine.quantity().compareTo(BigDecimal.valueOf(12)));
        assertEquals(2, loadedLine.sourceOrderItemIds().size());
        assertTrue(loadedLine.sourceOrderItemIds().contains(itemA));
        assertTrue(loadedLine.sourceOrderItemIds().contains(itemB));
        assertEquals(2, loadedLine.contributions().size());
    }

    @Test
    void editRoundTripUpdatesQuantityAndVersion() {
        MaterialRequirement created =
                repository.save(sampleRequirement(BigDecimal.TEN));
        MaterialRequirement edited =
                created.changeLineQuantity(
                        created.lines().getFirst().lineId(), BigDecimal.valueOf(30), T0);

        MaterialRequirement saved = repository.save(edited);
        MaterialRequirement loaded = repository.findById(saved.requirementId()).orElseThrow();

        assertEquals(1L, loaded.version());
        assertEquals(0, loaded.lines().getFirst().quantity().compareTo(BigDecimal.valueOf(30)));
    }

    @Test
    void optimisticLockRejectsStaleUpdate() {
        MaterialRequirement created =
                repository.save(sampleRequirement(BigDecimal.TEN));
        MaterialRequirement firstEdit =
                created.changeLineQuantity(
                        created.lines().getFirst().lineId(), BigDecimal.valueOf(7), T0);
        repository.save(firstEdit);

        MaterialRequirement stale =
                created.changeLineQuantity(
                        created.lines().getFirst().lineId(), BigDecimal.valueOf(3), T0);
        assertThrows(
                MaterialRequirementOptimisticLockException.class, () -> repository.save(stale));
    }

    @Test
    void markSubmittedIncrementsVersionAndPersistsMetadata() {
        MaterialRequirement created =
                repository.save(sampleRequirement(BigDecimal.TEN));
        MaterialRequirement submitted = created.submit("user-1", T0);
        MaterialRequirement saved = repository.markSubmitted(submitted);
        MaterialRequirement loaded = repository.findById(saved.requirementId()).orElseThrow();

        assertEquals(MaterialRequirementStatus.SUBMITTED, loaded.status());
        assertEquals(1L, loaded.version());
        assertEquals(Optional.of("user-1"), loaded.submittedBy());
        assertTrue(repository.findByIdForUpdate(loaded.requirementId()).isPresent());
    }

    private static MaterialRequirement sampleRequirement(BigDecimal quantity) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        return MaterialRequirement.create(
                PROD,
                T0,
                List.of(MaterialRequirementSourceItem.of(orderId, itemId, 1L)),
                List.of(sampleLine(orderId, itemId, quantity)));
    }

    private static MaterialRequirementLine sampleLine(
            SourceOrderId orderId, SourceOrderItemId itemId, BigDecimal quantity) {
        return MaterialRequirementLine.create(
                MaterialReferenceId.generate(),
                "MAT-Z",
                "Material Z",
                "WHITE",
                "PCS",
                null,
                quantity,
                List.of(MaterialRequirementLineContribution.of(orderId, itemId, quantity)));
    }
}
