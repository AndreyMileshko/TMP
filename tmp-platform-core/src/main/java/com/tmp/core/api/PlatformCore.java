package com.tmp.core.api;

import com.tmp.core.api.component.PlatformComponent;

/**
 * Stable public entry point to Platform Core infrastructure services.
 */
public interface PlatformCore {

    /**
     * Atomically registers a platform component for discovery and lifecycle management.
     */
    void registerComponent(PlatformComponent component);

    PlatformRegistry platformRegistry();

    ServiceRegistry serviceRegistry();

    CapabilityRegistry capabilityRegistry();

    EventBus eventBus();

    PlatformConfiguration configuration();

    LifecycleManager lifecycleManager();

    PlatformStatus status();
}
