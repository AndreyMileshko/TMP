package com.tmp.capability.sample;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.DependencyDescriptor;
import com.tmp.core.api.PlatformCore;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/**
 * Sample technical Capability that depends on {@link SampleTechnicalCapability} and resolves
 * {@link SampleTechnicalService} exclusively through Platform Core's public
 * {@code ServiceRegistry} during activation (ADR-003). Contains no business logic.
 */
public final class SampleDependentTechnicalCapability implements Capability {

    /** Stable capability id for the dependent sample. */
    public static final CapabilityId ID = CapabilityId.of("sample.dependent.technical.capability");

    private final PlatformCore platformCore;
    private final CapabilityDescriptor descriptor;

    public SampleDependentTechnicalCapability(PlatformCore platformCore) {
        this.platformCore = Objects.requireNonNull(platformCore, "platformCore");
        this.descriptor = CapabilityDescriptor.builder()
                .id(ID)
                .name("Sample Dependent Technical Capability")
                .version(CapabilityVersion.of("1.0.0"))
                .description("Technical fixture capability depending on sample.technical.capability")
                .dependencies(List.of(DependencyDescriptor.of(SampleTechnicalCapability.ID, CapabilityVersion.of("1.0.0"))))
                .build();
    }

    @Override
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "CapabilityDescriptor is an immutable value type; returning it directly is safe.")
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void onInitialize() {
        // technical fixture: no-op
    }

    @Override
    public void onActivate() {
        SampleTechnicalService resolved = platformCore
                .serviceRegistry()
                .lookup(SampleTechnicalService.class)
                .orElseThrow(() -> new IllegalStateException(
                        "SampleTechnicalService not found in Platform Core ServiceRegistry"));
        SampleLifecycleProbe.recordResolvedService(resolved);
        SampleLifecycleProbe.recordActivation(ID.value());
    }

    @Override
    public void onDeactivate() {
        // technical fixture: no-op
    }

    @Override
    public void onStop() {
        // technical fixture: no-op
    }
}
