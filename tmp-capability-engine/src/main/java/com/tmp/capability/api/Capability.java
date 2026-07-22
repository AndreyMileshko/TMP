package com.tmp.capability.api;

/**
 * Service-provider contract that a technical Capability implements. The Capability Engine
 * calls these lifecycle hooks; a {@code Capability} implementation never calls back into
 * the engine's internals. No {@code PlatformCore} or {@code DocumentEngine} reference is
 * injected into this SPI — a Capability obtains public services exclusively through the
 * same public registries any other module would use.
 */
public interface Capability {

    CapabilityDescriptor descriptor();

    void onInitialize();

    void onActivate();

    void onDeactivate();

    void onStop();
}
