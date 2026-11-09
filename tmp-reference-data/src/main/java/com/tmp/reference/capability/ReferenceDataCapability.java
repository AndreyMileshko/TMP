package com.tmp.reference.capability;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.api.ViewDescriptor;
import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorImportResolutionApi;
import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.api.So7ColorMappingCommandApi;
import com.tmp.reference.api.So7ColorMappingQueryApi;
import com.tmp.reference.security.ReferenceDataPermissionCatalog;
import com.tmp.reference.security.ReferenceDataPermissions;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Objects;

/**
 * Reference Data Capability descriptor (ADR-039 / TMP-SPEC-015).
 *
 * <p>COL-023 registers the four {@code reference.*} permissions. COL-020B contributes color catalog
 * public services. COL-021B contributes SO7 mapping public services. COL-022A contributes the
 * automatic matching setting API. COL-022B contributes the import resolution API. COL-030A
 * contributes navigation «Справочники» → Colors screen.
 */
public final class ReferenceDataCapability implements Capability {

    public static final CapabilityId ID = CapabilityId.of("reference");
    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    public static final String NAV_COLORS = "reference.nav.colors";
    public static final String VIEW_COLORS = "reference.view.colors";

    private final CapabilityDescriptor descriptor;

    public ReferenceDataCapability(
            ColorCatalogQueryApi colorCatalogQueryApi,
            ColorCatalogCommandApi colorCatalogCommandApi,
            So7ColorMappingQueryApi so7ColorMappingQueryApi,
            So7ColorMappingCommandApi so7ColorMappingCommandApi,
            ColorMatchingSettingApi colorMatchingSettingApi,
            ColorImportResolutionApi colorImportResolutionApi) {
        Objects.requireNonNull(colorCatalogQueryApi, "colorCatalogQueryApi");
        Objects.requireNonNull(colorCatalogCommandApi, "colorCatalogCommandApi");
        Objects.requireNonNull(so7ColorMappingQueryApi, "so7ColorMappingQueryApi");
        Objects.requireNonNull(so7ColorMappingCommandApi, "so7ColorMappingCommandApi");
        Objects.requireNonNull(colorMatchingSettingApi, "colorMatchingSettingApi");
        Objects.requireNonNull(colorImportResolutionApi, "colorImportResolutionApi");
        this.descriptor =
                CapabilityDescriptor.builder()
                        .id(ID)
                        .name("Reference Data")
                        .version(VERSION)
                        .description(
                                "Reference data owned by TMP: TMP color catalog, SO7 color mappings"
                                        + " and automatic color-matching settings")
                        .permissions(ReferenceDataPermissionCatalog.all())
                        .commands(
                                List.of(
                                        CommandDescriptor.of(
                                                NAV_COLORS,
                                                "Reference Colors",
                                                List.of(
                                                        ReferenceDataPermissions.COLOR_VIEW
                                                                .value()))))
                        .views(
                                List.of(
                                        ViewDescriptor.of(
                                                VIEW_COLORS, "Reference Colors", NAV_COLORS)))
                        .navigationContributions(
                                List.of(
                                        NavigationContribution.of(
                                                NAV_COLORS, "Справочники", VIEW_COLORS, 70)))
                        .publicServices(
                                List.of(
                                        PublicServiceContribution.of(
                                                ColorCatalogQueryApi.class, colorCatalogQueryApi),
                                        PublicServiceContribution.of(
                                                ColorCatalogCommandApi.class,
                                                colorCatalogCommandApi),
                                        PublicServiceContribution.of(
                                                So7ColorMappingQueryApi.class,
                                                so7ColorMappingQueryApi),
                                        PublicServiceContribution.of(
                                                So7ColorMappingCommandApi.class,
                                                so7ColorMappingCommandApi),
                                        PublicServiceContribution.of(
                                                ColorMatchingSettingApi.class,
                                                colorMatchingSettingApi),
                                        PublicServiceContribution.of(
                                                ColorImportResolutionApi.class,
                                                colorImportResolutionApi)))
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