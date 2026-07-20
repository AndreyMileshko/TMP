package com.tmp.core;

import com.tmp.core.api.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.config.PlatformCoreProperties;
import com.tmp.core.config.SpringPlatformConfiguration;
import com.tmp.core.event.PlatformStartedEvent;
import com.tmp.core.event.PlatformStoppingEvent;
import com.tmp.core.event.SynchronousEventBus;
import com.tmp.core.lifecycle.DefaultLifecycleManager;
import com.tmp.core.registry.DefaultCapabilityRegistry;
import com.tmp.core.registry.DefaultPlatformRegistry;
import com.tmp.core.registry.DefaultServiceRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@AutoConfiguration
@EnableConfigurationProperties(PlatformCoreProperties.class)
public class PlatformCoreAutoConfiguration {

    @Bean
    DefaultPlatformRegistry platformRegistry() {
        return new DefaultPlatformRegistry();
    }

    @Bean
    DefaultServiceRegistry serviceRegistry() {
        return new DefaultServiceRegistry();
    }

    @Bean
    DefaultCapabilityRegistry capabilityRegistry() {
        return new DefaultCapabilityRegistry();
    }

    @Bean
    SynchronousEventBus eventBus() {
        return new SynchronousEventBus();
    }

    @Bean
    PlatformConfiguration platformConfiguration(Environment environment) {
        return new SpringPlatformConfiguration(environment);
    }

    @Bean
    DefaultLifecycleManager lifecycleManager() {
        return new DefaultLifecycleManager();
    }

    @Bean
    PlatformCore platformCore(
            PlatformRegistry platformRegistry,
            ServiceRegistry serviceRegistry,
            CapabilityRegistry capabilityRegistry,
            EventBus eventBus,
            PlatformConfiguration platformConfiguration,
            DefaultLifecycleManager lifecycleManager,
            PlatformCoreProperties properties) {
        return new DefaultPlatformCore(
                platformRegistry,
                serviceRegistry,
                capabilityRegistry,
                eventBus,
                platformConfiguration,
                lifecycleManager,
                properties.getName(),
                properties.getVersion());
    }

    @Bean
    PlatformCoreLifecycleListener platformCoreLifecycleListener(PlatformCore platformCore, EventBus eventBus) {
        return new PlatformCoreLifecycleListener(platformCore, eventBus);
    }

    static final class PlatformCoreLifecycleListener implements ApplicationListener<ApplicationReadyEvent> {

        private final PlatformCore platformCore;
        private final EventBus eventBus;

        PlatformCoreLifecycleListener(PlatformCore platformCore, EventBus eventBus) {
            this.platformCore = platformCore;
            this.eventBus = eventBus;
        }

        @Override
        public void onApplicationEvent(ApplicationReadyEvent event) {
            platformCore.lifecycleManager().startAll();
            eventBus.publish(new PlatformStartedEvent());
        }

        @EventListener
        public void onContextClosed(ContextClosedEvent event) {
            eventBus.publish(new PlatformStoppingEvent());
            platformCore.lifecycleManager().stopAll();
        }
    }
}
