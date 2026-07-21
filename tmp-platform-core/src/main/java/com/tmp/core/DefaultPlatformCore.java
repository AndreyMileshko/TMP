package com.tmp.core;

import com.tmp.core.api.CapabilityRegistry;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformConfiguration;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.PlatformStatus;
import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.lifecycle.DefaultLifecycleManager;
import com.tmp.core.registry.DefaultPlatformRegistry;

public final class DefaultPlatformCore implements PlatformCore {

    private final DefaultPlatformRegistry platformRegistry;
    private final ServiceRegistry serviceRegistry;
    private final CapabilityRegistry capabilityRegistry;
    private final EventBus eventBus;
    private final PlatformConfiguration configuration;
    private final DefaultLifecycleManager lifecycleManager;
    private final String platformName;
    private final String platformVersion;
    private final Object registrationLock = new Object();

    public DefaultPlatformCore(
            DefaultPlatformRegistry platformRegistry,
            ServiceRegistry serviceRegistry,
            CapabilityRegistry capabilityRegistry,
            EventBus eventBus,
            PlatformConfiguration configuration,
            DefaultLifecycleManager lifecycleManager,
            String platformName,
            String platformVersion) {
        this.platformRegistry = platformRegistry;
        this.serviceRegistry = serviceRegistry;
        this.capabilityRegistry = capabilityRegistry;
        this.eventBus = eventBus;
        this.configuration = configuration;
        this.lifecycleManager = lifecycleManager;
        this.platformName = platformName;
        this.platformVersion = platformVersion;
        lifecycleManager.attachPlatformCore(this);
    }

    @Override
    public void registerComponent(PlatformComponent component) {
        String componentId = component.metadata().id();
        synchronized (registrationLock) {
            ComponentLifecycleState platformState = lifecycleManager.platformState();
            if (!isRegistrationAllowed(platformState)) {
                throw new IllegalStateException(
                        "Component registration is not allowed in platform state: " + platformState);
            }
            if (platformRegistry.isRegistered(componentId)) {
                throw new IllegalStateException("Component already registered: " + componentId);
            }
            if (lifecycleManager.isRegistered(componentId)) {
                throw new IllegalStateException("Component already registered for lifecycle: " + componentId);
            }
            platformRegistry.registerInternal(component);
            try {
                lifecycleManager.registerInternal(component);
            } catch (RuntimeException lifecycleRegistrationFailure) {
                platformRegistry.unregisterInternal(componentId);
                throw lifecycleRegistrationFailure;
            }
        }
    }

    private static boolean isRegistrationAllowed(ComponentLifecycleState platformState) {
        return platformState == ComponentLifecycleState.REGISTERED
                || platformState == ComponentLifecycleState.STOPPED;
    }

    @Override
    public PlatformRegistry platformRegistry() {
        return platformRegistry;
    }

    @Override
    public ServiceRegistry serviceRegistry() {
        return serviceRegistry;
    }

    @Override
    public CapabilityRegistry capabilityRegistry() {
        return capabilityRegistry;
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    public PlatformConfiguration configuration() {
        return configuration;
    }

    @Override
    public LifecycleManager lifecycleManager() {
        return lifecycleManager;
    }

    @Override
    public PlatformStatus status() {
        return new PlatformStatus(
                platformName,
                platformVersion,
                lifecycleManager.platformState(),
                platformRegistry.registeredComponents().size(),
                serviceRegistry.registeredServiceCount(),
                capabilityRegistry.findAll().size());
    }
}
