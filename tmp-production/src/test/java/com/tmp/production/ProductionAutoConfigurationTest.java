package com.tmp.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderQueryService;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.application.ProductionWarehouseScope;
import com.tmp.production.security.ProductionCapability;
import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class ProductionAutoConfigurationTest {

    private static final UUID MAIN = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROD = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID MAGIC_MAIN =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MAGIC_PROD =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ApplicationContextRunner baseRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ProductionAutoConfiguration.class))
                    .withBean(AuthorizationService.class, () -> Mockito.mock(AuthorizationService.class))
                    .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
                    .withBean(OrderQueryService.class, () -> Mockito.mock(OrderQueryService.class))
                    .withBean(WarehouseQueryApi.class, () -> Mockito.mock(WarehouseQueryApi.class))
                    .withBean(WarehouseCommandApi.class, () -> Mockito.mock(WarehouseCommandApi.class))
                    .withBean(DocumentEngine.class, () -> Mockito.mock(DocumentEngine.class))
                    .withBean(
                            TransactionalEventPublisher.class,
                            () -> Mockito.mock(TransactionalEventPublisher.class))
                    .withBean(
                            PlatformTransactionManager.class,
                            () -> Mockito.mock(PlatformTransactionManager.class));

    @Test
    void registersProductionBeansWhenWarehouseScopeConfiguredViaProperties() {
        baseRunner
                .withPropertyValues(
                        "tmp.production.warehouse.main-warehouse-id=" + MAIN,
                        "tmp.production.warehouse.production-warehouse-id=" + PROD)
                .run(
                        context -> {
                            assertNotNull(context.getBean(ProductionQueryApi.class));
                            assertNotNull(context.getBean(ProductionApplicationApi.class));
                            assertNotNull(context.getBean(ProductionCapability.class));
                            assertEquals(1, context.getBeansOfType(ProductionCapability.class).size());
                            ProductionWarehouseScope scope =
                                    context.getBean(ProductionWarehouseScope.class);
                            assertEquals(MAIN, scope.mainWarehouseId());
                            assertEquals(PROD, scope.productionWarehouseId());
                        });
    }

    @Test
    void acceptsExplicitProductionWarehouseScopeBeanWithoutProperties() {
        baseRunner
                .withBean(
                        ProductionWarehouseScope.class,
                        () -> new ProductionWarehouseScope(MAIN, PROD))
                .run(
                        context -> {
                            assertNotNull(context.getBean(ProductionQueryApi.class));
                            assertNotNull(context.getBean(ProductionApplicationApi.class));
                            ProductionWarehouseScope scope =
                                    context.getBean(ProductionWarehouseScope.class);
                            assertEquals(MAIN, scope.mainWarehouseId());
                            assertEquals(PROD, scope.productionWarehouseId());
                        });
    }

    @Test
    void failsFastWithoutWarehouseScopeConfiguration() {
        baseRunner.run(
                context -> {
                    assertTrue(context.getStartupFailure() != null);
                    Throwable root = rootCause(context.getStartupFailure());
                    assertTrue(
                            root.getMessage().contains("main warehouse ID")
                                    || root.getMessage().contains("main-warehouse-id"));
                    assertTrue(
                            root.getMessage().contains("production warehouse ID")
                                    || root.getMessage().contains("production-warehouse-id"));
                });
    }

    @Test
    void doesNotInventMagicWarehouseIds() {
        baseRunner
                .withPropertyValues(
                        "tmp.production.warehouse.main-warehouse-id=" + MAIN,
                        "tmp.production.warehouse.production-warehouse-id=" + PROD)
                .run(
                        context -> {
                            ProductionWarehouseScope scope =
                                    context.getBean(ProductionWarehouseScope.class);
                            assertTrue(!MAGIC_MAIN.equals(scope.mainWarehouseId()));
                            assertTrue(!MAGIC_PROD.equals(scope.productionWarehouseId()));
                        });
    }

    @Test
    void failsWhenOnlyOneWarehouseIdConfigured() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        baseRunner
                                .withPropertyValues(
                                        "tmp.production.warehouse.main-warehouse-id=" + MAIN)
                                .run(
                                        context -> {
                                            if (context.getStartupFailure() != null) {
                                                throw asIllegalState(context.getStartupFailure());
                                            }
                                        }));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static IllegalStateException asIllegalState(Throwable failure) {
        Throwable root = rootCause(failure);
        if (root instanceof IllegalStateException illegalState) {
            return illegalState;
        }
        return new IllegalStateException(root.getMessage(), root);
    }
}
