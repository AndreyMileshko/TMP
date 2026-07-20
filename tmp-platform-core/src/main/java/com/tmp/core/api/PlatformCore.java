package com.tmp.core.api;

/**
 * Stable public entry point to Platform Core infrastructure services.
 */
public interface PlatformCore {

    PlatformRegistry platformRegistry();

    ServiceRegistry serviceRegistry();

    CapabilityRegistry capabilityRegistry();

    EventBus eventBus();

    PlatformConfiguration configuration();

    LifecycleManager lifecycleManager();

    PlatformStatus status();
}
