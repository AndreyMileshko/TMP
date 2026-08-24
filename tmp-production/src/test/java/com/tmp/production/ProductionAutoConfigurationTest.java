package com.tmp.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.production.security.ProductionCapability;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ProductionAutoConfiguration.class));

    @Test
    void registersSingleProductionCapabilityBean() {
        contextRunner.run(
                context -> {
                    assertNotNull(context.getBean(ProductionCapability.class));
                    assertEquals(1, context.getBeansOfType(ProductionCapability.class).size());
                });
    }
}
