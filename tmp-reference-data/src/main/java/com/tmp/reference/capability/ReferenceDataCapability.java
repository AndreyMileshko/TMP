package com.tmp.reference.capability;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.security.ReferenceDataPermissionCatalog;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/**
 * Reference Data Capability descriptor (ADR-039 / TMP-SPEC-015).
 *
 * <p>COL-023 registers the four {@code reference.*} permissions. COL-020B contributes public color
 * catalog query/command services. Navigation remains later COL tasks.
 */
public final class ReferenceDataCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("reference");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    private final CapabilityDescriptor descriptor;

    public ReferenceDataCapability(
            ColorCatalogQueryApi colorCatalogQueryApi, ColorCatalogCommandApi colorCatalogCommandApi) {
        Objects.requireNonNull(colorCatalogQueryApi, "colorCatalogQueryApi");
        Objects.requireNonNull(colorCatalogCommandApi, "colorCatalogCommandApi");
        this.descriptor =
                CapabilityDescriptor.builder()
                        .id(ID)
                        .name("Reference Data")
                        .version(VERSION)
                        .description(
                                "Reference data owned by TMP: TMP color catalog, SO7 color mappings"
                                        + " and automatic color-matching settings")
                        .permissions(ReferenceDataPermissionCatalog.all())
                        .publicServices(
                                List.of(
                                        PublicServiceContribution.of(
                                                ColorCatalogQueryApi.class, colorCatalogQueryApi),
                                        PublicServiceContribution.of(
                                                ColorCatalogCommandApi.class,
                                                colorCatalogCommandApi)))
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
