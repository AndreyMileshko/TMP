package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.core.api.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.capability.CapabilityDescriptor;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.core.event.PlatformStartedEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = TmpBootstrapApplication.class)
@Import(PlatformCoreIntegrationIT.IntegrationTestPlatformConfiguration.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_platform_core_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class PlatformCoreIntegrationIT {

    @Autowired
    private PlatformCore platformCore;

    @Autowired
    private EventBus eventBus;

    @Autowired
    private CapabilityRegistry capabilityRegistry;

    @Test
    void registersServicesCapabilitiesAndDeliversEvents() {
        capabilityRegistry.register(new CapabilityDescriptor("cap.integration", "Integration", "0.1.0"));

        AtomicBoolean eventReceived = new AtomicBoolean(false);
        eventBus.subscribePlatform(PlatformStartedEvent.class, event -> eventReceived.set(true));
        eventBus.publish(new PlatformStartedEvent());

        assertTrue(eventReceived.get(), "EventBus must deliver platform events synchronously");
        assertEquals(1, capabilityRegistry.findAll().size());
        assertEquals(ComponentLifecycleState.STARTED, platformCore.status().lifecycleState());
        assertEquals(1, platformCore.platformRegistry().registeredComponents().size());
        assertEquals(1, platformCore.status().registeredServices());
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
