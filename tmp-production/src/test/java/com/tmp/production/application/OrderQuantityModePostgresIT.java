package com.tmp.production.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.tmp.production.api.ProductionApplicationApi.OrderQuantityModeView;
import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.production.domain.MaterialReferenceId;
import com.tmp.production.domain.MaterialRequirement;
import com.tmp.production.domain.MaterialRequirementLine;
import com.tmp.production.domain.MaterialRequirementLineContribution;
import com.tmp.production.domain.MaterialRequirementSourceItem;
import com.tmp.production.domain.ProductionFoundation;
import com.tmp.production.domain.ProductionItemState;
import com.tmp.production.domain.ProductionQuantity;
import com.tmp.production.domain.ProductionStatus;
import com.tmp.production.domain.SourceOrderId;
import com.tmp.production.domain.SourceOrderItemId;
import com.tmp.production.domain.SpecificationId;
import com.tmp.production.domain.repository.MaterialRequirementRepository;
import com.tmp.production.domain.repository.ProductionItemStateRepository;
import com.tmp.production.domain.repository.ProductionMaterialTransferRepository;
import com.tmp.production.persistence.JdbcMaterialRequirementRepository;
import com.tmp.production.persistence.JdbcOrderQuantityModeRepository;
import com.tmp.production.persistence.JdbcProductionItemStateRepository;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
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

/**
 * Order quantity mode is a Production setting independent of Production state: it can be changed
 * in every state and never alters item states or material requirements.
 */
@Testcontainers
class OrderQuantityModePostgresIT {

    private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0, ZoneOffset.UTC);
    private static final UUID PROD_WH = UUID.fromString("00000000-0000-4000-8000-000000000048");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    private ProductionItemStateRepository itemStates;
    private MaterialRequirementRepository requirements;
    private DefaultProductionApplicationApi api;

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
        itemStates = new JdbcProductionItemStateRepository(jdbc, CLOCK);
        requirements =
                new JdbcMaterialRequirementRepository(
                        jdbc, CLOCK, new DataSourceTransactionManager(dataSource));
        api =
                new DefaultProductionApplicationApi(
                        mock(AuthorizationService.class),
                        mock(AuthenticationService.class),
                        new ProductionDestinationWarehouse(PROD_WH),
                        mock(ProductionLaunchService.class),
                        mock(CheckMaterialAvailabilityService.class),
                        mock(MaterialRequirementService.class),
                        mock(MaterialRequirementCoverageService.class),
                        mock(SubmitMaterialRequirementService.class),
                        mock(ConfirmMaterialReceiptService.class),
                        mock(ReleaseProductsService.class),
                        mock(CancelOrderProductionService.class),
                        mock(MaterialReadinessQueryService.class),
                        mock(ProductionMaterialTransferRepository.class),
                        new JdbcOrderQuantityModeRepository(jdbc, CLOCK));
    }

    @Test
    void newOrderWithoutProductionStateIsStandardAndModeCanChange() {
        UUID orderId = UUID.randomUUID();

        OrderQuantityModeView initial = api.getOrderQuantityMode(orderId);
        assertEquals(new OrderQuantityModeView(orderId, QuantityModeView.STANDARD, 0L), initial);

        OrderQuantityModeView flexible =
                api.changeOrderQuantityMode(orderId, QuantityModeView.FLEXIBLE, initial.version());

        assertEquals(new OrderQuantityModeView(orderId, QuantityModeView.FLEXIBLE, 1L), flexible);
        assertEquals(flexible, api.getOrderQuantityMode(orderId));
        assertTrue(
                itemStates.findBySourceOrderId(SourceOrderId.of(orderId)).isEmpty(),
                "Changing the mode must not create Production state for the order");
    }

    @Test
    void modeChangesInProductionWithoutAlteringStateOrRequirement() {
        assertModeChangeKeepsState(state -> state, ProductionStatus.IN_PRODUCTION);
    }

    @Test
    void modeChangesWhenPartiallyReleasedWithoutAlteringState() {
        assertModeChangeKeepsState(
                state -> state.release(ProductionQuantity.positive(4), T0),
                ProductionStatus.PARTIALLY_RELEASED);
    }

    @Test
    void modeChangesWhenReleasedWithoutAlteringState() {
        assertModeChangeKeepsState(
                state -> state.release(ProductionQuantity.positive(10), T0),
                ProductionStatus.RELEASED);
    }

    @Test
    void modeChangesWhenCancelledWithoutAlteringState() {
        assertModeChangeKeepsState(
                state -> state.release(ProductionQuantity.positive(4), T0).cancel(T0),
                ProductionStatus.CANCELLED);
    }

    @Test
    void persistedTableHasNoRevisionOrProductionOrderShape() {
        Integer revisionColumns =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'production'
                          AND table_name = 'order_quantity_modes'
                          AND (column_name ILIKE '%revision%' OR column_name ILIKE '%status%')
                        """,
                        Integer.class);
        assertEquals(0, revisionColumns);

        Integer productionOrderTables =
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'production'
                          AND (table_name ILIKE '%production_order%'
                               OR table_name ILIKE '%order_production%')
                        """,
                        Integer.class);
        assertEquals(0, productionOrderTables);
    }

    private void assertModeChangeKeepsState(
            Function<ProductionItemState, ProductionItemState> transition,
            ProductionStatus expectedStatus) {
        SourceOrderId orderId = SourceOrderId.generate();
        SourceOrderItemId itemId = SourceOrderItemId.generate();
        ProductionItemState launched =
                ProductionItemState.launch(
                        ProductionFoundation.freeze(orderId, itemId, SpecificationId.generate(), T0),
                        ProductionQuantity.positive(10),
                        T0);
        itemStates.save(launched);
        ProductionItemState current = transition.apply(launched);
        itemStates.save(current);
        assertEquals(expectedStatus, current.status());
        MaterialRequirement draft =
                requirements.save(
                        MaterialRequirement.create(
                                PROD_WH,
                                T0,
                                List.of(MaterialRequirementSourceItem.of(orderId, itemId, 1L)),
                                List.of(
                                        MaterialRequirementLine.create(
                                                MaterialReferenceId.generate(),
                                                "MAT-1",
                                                "Material 1",
                                                "WHITE",
                                                "PCS",
                                                null,
                                                BigDecimal.valueOf(7),
                                                List.of(
                                                        MaterialRequirementLineContribution.of(
                                                                orderId,
                                                                itemId,
                                                                BigDecimal.valueOf(7)))))));
        Map<String, Object> stateBefore = itemStateRow(orderId);

        OrderQuantityModeView flexible =
                api.changeOrderQuantityMode(orderId.value(), QuantityModeView.FLEXIBLE, 0L);
        OrderQuantityModeView standard =
                api.changeOrderQuantityMode(
                        orderId.value(), QuantityModeView.STANDARD, flexible.version());

        assertEquals(QuantityModeView.FLEXIBLE, flexible.quantityMode());
        assertEquals(
                new OrderQuantityModeView(orderId.value(), QuantityModeView.STANDARD, 2L),
                standard);
        assertEquals(standard, api.getOrderQuantityMode(orderId.value()));
        assertEquals(stateBefore, itemStateRow(orderId));
        MaterialRequirement draftAfter =
                requirements.findById(draft.requirementId()).orElseThrow();
        assertEquals(draft.version(), draftAfter.version());
        assertEquals(
                0,
                draft.lines().getFirst().quantity()
                        .compareTo(draftAfter.lines().getFirst().quantity()));
    }

    private Map<String, Object> itemStateRow(SourceOrderId orderId) {
        return jdbc.queryForMap(
                """
                SELECT status, ordered_quantity, active_production_quantity, released_quantity,
                       version, updated_at
                FROM production.production_item_states
                WHERE source_order_id = ?
                """,
                orderId.value());
    }
}
