package com.tmp.reference.capability;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Reference Data Capability descriptor (ADR-039 / TMP-SPEC-015).
 *
 * <p>COL-010 registers the Capability without permissions, navigation or public services. Those
 * contributions are added in later COL tasks.
 */
public final class ReferenceDataCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("reference");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    private final CapabilityDescriptor descriptor;

    public ReferenceDataCapability() {
        this.descriptor =
                CapabilityDescriptor.builder()
                        .id(ID)
                        .name("Reference Data")
                        .version(VERSION)
                        .description(
                                "Reference data owned by TMP: TMP color catalog, SO7 color mappings"
                                        + " and automatic color-matching settings")
                        .build();
    }

    @Override
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification =
                    "CapabilityDescriptor is an immutable value type; returning it directly is safe.")
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public void onInitialize() {
        // no-op: contributions only
    }

    @Override
    public void onActivate() {
        // no-op: contributions only
    }

    @Override
    public void onDeactivate() {
        // no-op: contributions only
    }

    @Override
    public void onStop() {
        // no-op: contributions only
    }
}
