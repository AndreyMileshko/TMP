package com.tmp.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.order.api.OrderQueryService;
import com.tmp.security.api.AuthorizationService;
import com.tmp.production.security.ProductionCapability;
import com.tmp.production.api.ProductionQueryApi;
import com.tmp.warehouse.api.WarehouseQueryApi;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.mockito.Mockito;

class ProductionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ProductionAutoConfiguration.class))
                    .withBean(AuthorizationService.class, () -> Mockito.mock(AuthorizationService.class))
                    .withBean(JdbcTemplate.class, () -> Mockito.mock(JdbcTemplate.class))
                    .withBean(OrderQueryService.class, () -> Mockito.mock(OrderQueryService.class))
                    .withBean(WarehouseQueryApi.class, () -> Mockito.mock(WarehouseQueryApi.class));

    @Test
    void registersSingleProductionCapabilityBean() {
        contextRunner.run(
                context -> {
                    // Required by production query API wiring.
                    AuthorizationService authorization = context.getBean(AuthorizationService.class);
                    assertNotNull(authorization);
                    assertNotNull(context.getBean(ProductionQueryApi.class));
                    assertNotNull(context.getBean(ProductionCapability.class));
                    assertEquals(1, context.getBeansOfType(ProductionCapability.class).size());
                });
    }

}
