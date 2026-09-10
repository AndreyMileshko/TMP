package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.capability.sample.SampleDependentTechnicalCapability;
import com.tmp.capability.sample.SampleTechnicalCapability;
import com.tmp.core.api.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.capability.CapabilityDescriptor;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.core.api.event.platform.PlatformStartedEvent;
import com.tmp.order.capability.OrderManagementCapability;
import com.tmp.production.security.ProductionCapability;
import com.tmp.security.capability.SecurityAdministrationCapability;
import com.tmp.warehouse.security.WarehouseCapability;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
        classes = TmpBootstrapApplication.class,
        properties = "tmp.capability.sample.diagnostic=true")
@Import(PlatformCoreIntegrationIT.IntegrationTestPlatformConfiguration.class)
@ActiveProfiles("test")
class PlatformCoreIntegrationIT extends AbstractBootstrapPostgresSpringTest {

    private static final String MANUAL_CAPABILITY_ID = "cap.integration";

    private static final Set<String> EXPECTED_CAPABILITY_IDS = Set.of(
            SampleTechnicalCapability.ID.value(),
            SampleDependentTechnicalCapability.ID.value(),
            SecurityAdministrationCapability.ID.value(),
            OrderManagementCapability.ID.value(),
            WarehouseCapability.ID.value(),
            ProductionCapability.ID.value(),
            MANUAL_CAPABILITY_ID);

    @Autowired
    private PlatformCore platformCore;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private CapabilityRegistry capabilityRegistry;

    @Test
    void registersServicesCapabilitiesAndDeliversEvents() {
        capabilityRegistry.register(new CapabilityDescriptor(MANUAL_CAPABILITY_ID, "Integration", "0.1.0"));

        AtomicBoolean eventReceived = new AtomicBoolean(false);
        eventBus.subscribePlatform(PlatformStartedEvent.class, event -> eventReceived.set(true));
        eventBus.publish(new PlatformStartedEvent());

        assertTrue(eventReceived.get(), "EventBus must deliver platform events synchronously");

        List<CapabilityDescriptor> actualCapabilities = capabilityRegistry.findAll();
        Set<String> actualCapabilityIds =
                actualCapabilities.stream().map(CapabilityDescriptor::id).collect(Collectors.toSet());
        assertEquals(
                EXPECTED_CAPABILITY_IDS,
                actualCapabilityIds,
                "expected auto-registered business + diagnostic sample capabilities plus manual fixture");
        assertEquals(EXPECTED_CAPABILITY_IDS.size(), actualCapabilities.size());

        assertEquals(ComponentLifecycleState.STARTED, platformCore.status().lifecycleState());
        assertEquals(
                4,
                platformCore.platformRegistry().registeredComponents().size(),
                "document-engine, capability-engine, security, integration test component");
        assertEquals(
                3,
                platformCore.status().registeredServices(),
                "integration test service, sample technical public service, production query API");
    }

    @Configuration
    static class IntegrationTestPlatformConfiguration {

        @Bean
        IntegrationTestRegistrar integrationTestRegistrar(PlatformCore platformCore) {
            SampleInfrastructureService service = () -> "ready";
            PlatformComponentMetadata owner = new PlatformComponentMetadata(
                    "bootstrap.integration", "Bootstrap Integration", "0.1.0", ComponentType.SERVICE);
            SamplePlatformComponent component = new SamplePlatformComponent();

            platformCore.serviceRegistry().register(SampleInfrastructureService.class, service, owner);
            platformCore.registerComponent(component);

            return new IntegrationTestRegistrar();
        }
    }

    record IntegrationTestRegistrar() {
    }

    interface SampleInfrastructureService {
        String status();
    }

    static final class SamplePlatformComponent implements PlatformComponent {

        @Override
        public PlatformComponentMetadata metadata() {
            return new PlatformComponentMetadata(
                    "component.integration", "Integration Component", "0.1.0", ComponentType.PLATFORM);
        }

        @Override
        public void initialize(PlatformCore platformCore) {
            // lifecycle hook verified through platform status
        }

        @Override
        public void start() {
            // no-op
        }

        @Override
        public void stop() {
            // no-op
        }
    }
}
