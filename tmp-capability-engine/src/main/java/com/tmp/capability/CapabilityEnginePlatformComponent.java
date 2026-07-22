package com.tmp.capability;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;

/**
 * Adapts {@link CapabilityEngine} to the Platform Core {@link PlatformComponent} contract.
 * The infrastructural Capability Engine registers with {@link ComponentType#SERVICE} — it is
 * platform infrastructure, not a business capability instance ({@link ComponentType#CAPABILITY}
 * is reserved for future use and is intentionally not used here).
 */
final class CapabilityEnginePlatformComponent implements PlatformComponent {

    private static final PlatformComponentMetadata METADATA = new PlatformComponentMetadata(
            "capability-engine", "Capability Engine", "0.1.0-SNAPSHOT", ComponentType.SERVICE);

    private final CapabilityEngine capabilityEngine;

    CapabilityEnginePlatformComponent(CapabilityEngine capabilityEngine) {
        this.capabilityEngine = capabilityEngine;
    }

    @Override
    public PlatformComponentMetadata metadata() {
        return METADATA;
    }

    @Override
    public void initialize(PlatformCore platformCore) {
        capabilityEngine.discoverAndRegisterAll();
    }

    @Override
    public void start() {
        capabilityEngine.activateAll();
    }

    @Override
    public void stop() {
        capabilityEngine.stopAll();
    }
}
