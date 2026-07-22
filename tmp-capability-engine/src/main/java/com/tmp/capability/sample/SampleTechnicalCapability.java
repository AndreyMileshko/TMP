package com.tmp.capability.sample;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.CommandDescriptor;
import com.tmp.capability.api.DocumentContribution;
import com.tmp.capability.api.EventContribution;
import com.tmp.capability.api.NavigationContribution;
import com.tmp.capability.api.PermissionDescriptor;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.api.SettingsContribution;
import com.tmp.capability.api.ViewDescriptor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;

/**
 * Sample technical Capability with no dependencies, contributing every mandatory contribution
 * type (permission, command, view, navigation, setting, event, document, public service).
 * Contains no business logic — it exists only to prove end-to-end Capability Engine wiring.
 */
public final class SampleTechnicalCapability implements Capability {

    /** Stable capability id used by {@link SampleDependentTechnicalCapability}. */
    public static final CapabilityId ID = CapabilityId.of("sample.technical.capability");

    public static final CapabilityVersion VERSION = CapabilityVersion.of("1.0.0");

    private final CapabilityDescriptor descriptor;
    private final SampleTechnicalServiceImpl service = new SampleTechnicalServiceImpl();
    private final SampleTechnicalDocumentProcessor documentProcessor = new SampleTechnicalDocumentProcessor();

    public SampleTechnicalCapability() {
        this.descriptor = CapabilityDescriptor.builder()
                .id(ID)
                .name("Sample Technical Capability")
                .version(VERSION)
                .description("Technical fixture capability with no business logic")
                .permissions(List.of(PermissionDescriptor.of(
                        "sample.technical.view", "View sample technical data", "Technical fixture permission")))
                .commands(List.of(CommandDescriptor.of(
                        "sample.technical.run", "Run sample technical action", List.of("sample.technical.view"))))
                .views(List.of(ViewDescriptor.of(
                        "sample.technical.view", "Sample technical view", "sample.technical.nav")))
                .navigationContributions(List.of(NavigationContribution.of(
                        "sample.technical.nav", "Sample technical", "sample.technical.view", 0)))
                .settings(List.of(SettingsContribution.of(
                        "sample.technical.setting", "Sample setting", "Technical fixture setting", "0")))
                .events(List.of(EventContribution.of("sample.technical.event", "Sample technical event")))
                .documents(List.of(DocumentContribution.of(
                        SampleTechnicalDocumentProcessor.DOCUMENT_TYPE_ID,
                        "Sample technical document",
                        "Technical fixture document type",
                        documentProcessor)))
                .publicServices(List.of(PublicServiceContribution.of(SampleTechnicalService.class, service)))
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
