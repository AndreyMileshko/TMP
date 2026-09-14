package com.tmp.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderQueryService;
import com.tmp.production.api.ProductionApplicationApi;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.production.application.ProductionDestinationWarehouse;
import com.tmp.production.security.ProductionCapability;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.warehouse.api.WarehouseCommandApi;
import com.tmp.warehouse.api.WarehouseDemandCommandApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import com.tmp.warehouse.api.WarehouseReferenceQueryApi;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class ProductionAutoConfigurationTest {

    private static final UUID PROD = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final ApplicationContextRunner baseRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ProductionAutoConfiguration.class))
                    .withBean(AuthorizationService.class, () -> Mockito.mock(AuthorizationService.class))
                    .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
                    .withBean(OrderQueryService.class, () -> Mockito.mock(OrderQueryService.class))
                    .withBean(WarehouseQueryApi.class, () -> Mockito.mock(WarehouseQueryApi.class))
                    .withBean(WarehouseCommandApi.class, () -> Mockito.mock(WarehouseCommandApi.class))
                    .withBean(
                            WarehouseDemandCommandApi.class,
                            () -> Mockito.mock(WarehouseDemandCommandApi.class))
                    .withBean(
                            "warehouseReferenceQueryApi",
                            WarehouseReferenceQueryApi.class,
                            () -> {
                                WarehouseReferenceQueryApi api =
                                        Mockito.mock(WarehouseReferenceQueryApi.class);
                                Mockito.when(api.findProductionWarehouse())
                                        .thenReturn(Optional.empty());
                                return api;
                            })
                    .withBean(
                            AuthenticationService.class,
                            () -> Mockito.mock(AuthenticationService.class))
                    .withBean(DocumentEngine.class, () -> Mockito.mock(DocumentEngine.class))
                    .withBean(
                            TransactionalEventPublisher.class,
                            () -> Mockito.mock(TransactionalEventPublisher.class))
                    .withBean(
                            PlatformTransactionManager.class,
                            () -> Mockito.mock(PlatformTransactionManager.class));

    @Test
    void registersProductionBeansWithoutConfiguredWarehouseUuid() {
        baseRunner.run(
                context -> {
                    assertNotNull(context.getBean(ProductionQueryApi.class));
                    assertNotNull(context.getBean(ProductionApplicationApi.class));
                    assertNotNull(context.getBean(ProductionCapability.class));
                    assertEquals(1, context.getBeansOfType(ProductionCapability.class).size());
                    ProductionDestinationWarehouse destination =
                            context.getBean(ProductionDestinationWarehouse.class);
                    assertTrue(destination.findProductionWarehouseId().isEmpty());
                });
    }

    @Test
    void acceptsExplicitProductionDestinationWarehouseBean() {
        baseRunner
                .withBean(
                        ProductionDestinationWarehouse.class,
                        () -> new ProductionDestinationWarehouse(PROD))
                .run(
                        context -> {
                            assertNotNull(context.getBean(ProductionQueryApi.class));
                            assertNotNull(context.getBean(ProductionApplicationApi.class));
                            ProductionDestinationWarehouse destination =
                                    context.getBean(ProductionDestinationWarehouse.class);
                            assertEquals(PROD, destination.productionWarehouseId());
                        });
    }

    @Test
    void resolvesDestinationFromWarehouseReferenceApi() {
        WarehouseReferenceQueryApi api = Mockito.mock(WarehouseReferenceQueryApi.class);
        Mockito.when(api.findProductionWarehouse())
                .thenReturn(
                        Optional.of(
                                new WarehouseReferenceQueryApi.WarehouseReferenceView(
                                        PROD, "SECOND", "Второй склад", true)));
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ProductionAutoConfiguration.class))
                .withBean(AuthorizationService.class, () -> Mockito.mock(AuthorizationService.class))
                .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
                .withBean(OrderQueryService.class, () -> Mockito.mock(OrderQueryService.class))
                .withBean(WarehouseQueryApi.class, () -> Mockito.mock(WarehouseQueryApi.class))
                .withBean(WarehouseCommandApi.class, () -> Mockito.mock(WarehouseCommandApi.class))
                .withBean(
                        WarehouseDemandCommandApi.class,
                        () -> Mockito.mock(WarehouseDemandCommandApi.class))
                .withBean("warehouseReferenceQueryApi", WarehouseReferenceQueryApi.class, () -> api)
                .withBean(
                        AuthenticationService.class,
                        () -> Mockito.mock(AuthenticationService.class))
                .withBean(DocumentEngine.class, () -> Mockito.mock(DocumentEngine.class))
                .withBean(
                        TransactionalEventPublisher.class,
                        () -> Mockito.mock(TransactionalEventPublisher.class))
                .withBean(
                        PlatformTransactionManager.class,
                        () -> Mockito.mock(PlatformTransactionManager.class))
                .run(
                        context -> {
                            ProductionDestinationWarehouse destination =
                                    context.getBean(ProductionDestinationWarehouse.class);
                            assertEquals(PROD, destination.productionWarehouseId());
                        });
    }
}
