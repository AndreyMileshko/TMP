package com.tmp.capability.registration;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.DocumentContribution;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.registry.CapabilityRegistration;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentTypeDescriptor;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Single atomic registration entry point for a Capability. Executes Capability-Engine-owned
 * catalog mutations first (always reversible), then external Platform Core / Document Engine
 * calls in the fixed order defined by Stage 3 design decisions §6:
 *
 * <ol>
 *   <li>reserve id in the Capability-Engine {@link CapabilityRegistry};</li>
 *   <li>register internal contribution catalogs (permissions, commands, views, navigation,
 *       settings, event descriptors);</li>
 *   <li>register basic descriptor into Platform Core {@code capabilityRegistry()};</li>
 *   <li>pre-check and register each {@link DocumentContribution} via
 *       {@link DocumentEngine#registerProcessor};</li>
 *   <li>register each {@link PublicServiceContribution} via
 *       {@code platformCore.serviceRegistry().register(...)}</li>
 *   <li>event contributions: cataloging only (no EventBus subscription intermediation —
 *       capabilities subscribe themselves in {@code onActivate}/{@code onInitialize} and
 *       unsubscribe in {@code onStop}/{@code onDeactivate});</li>
 *   <li>on success: {@code commit} the Capability-Engine registration as
 *       {@link CapabilityLifecycleState#REGISTERED}.</li>
 * </ol>
 *
 * <p><b>Known residual limitation</b> (design decisions §6, documented verbatim intent):
 * no compensation is possible for {@code ServiceRegistry} / Document Engine calls that
 * already succeeded before a later step fails. This is mitigated by ordering +
 * pre-validation under a single registration lock, not eliminated by contract. A future
 * Platform Core contract change that made {@code ServiceRegistry.register} fail after
 * Document Engine registration already succeeded would leave those external registrations
 * in place; Capability-Engine-owned state (catalogs + reservation) is still fully unwound.
 */
public final class CapabilityRegistrationService {

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityContributionCatalogs contributionCatalogs;
    private final PlatformCore platformCore;
    private final DocumentEngine documentEngine;
    private final ReentrantLock registrationLock = new ReentrantLock();

    public CapabilityRegistrationService(
            CapabilityRegistry capabilityRegistry,
            CapabilityContributionCatalogs contributionCatalogs,
            PlatformCore platformCore,
            DocumentEngine documentEngine) {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
        this.contributionCatalogs = Objects.requireNonNull(contributionCatalogs, "contributionCatalogs");
        this.platformCore = Objects.requireNonNull(platformCore, "platformCore");
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
    }

    public void register(Capability capability) {
        Objects.requireNonNull(capability, "capability");
        CapabilityDescriptor descriptor = Objects.requireNonNull(capability.descriptor(), "descriptor");
        CapabilityId id = descriptor.id();

        registrationLock.lock();
        boolean reserved = false;
        boolean catalogsRegistered = false;
        try {
            capabilityRegistry.reserve(id);
            reserved = true;

            contributionCatalogs.registerInternalContributions(descriptor);
            catalogsRegistered = true;

            registerPlatformCapabilityDescriptor(descriptor);
            registerDocumentContributions(descriptor);
            registerPublicServices(descriptor);

            capabilityRegistry.commit(
                    new CapabilityRegistration(descriptor, CapabilityLifecycleState.REGISTERED, capability));
            reserved = false;
            catalogsRegistered = false;
        } catch (RuntimeException failure) {
            compensate(id, reserved, catalogsRegistered, failure);
            throw new CapabilityRegistrationException(
                    "Failed to register capability '" + id + "': " + failure.getMessage(), failure);
        } finally {
            registrationLock.unlock();
        }
    }

    private void registerPlatformCapabilityDescriptor(CapabilityDescriptor descriptor) {
        com.tmp.core.api.capability.CapabilityDescriptor platformDescriptor =
                new com.tmp.core.api.capability.CapabilityDescriptor(
                        descriptor.id().value(),
                        descriptor.name(),
                        descriptor.version().toString());
        platformCore.capabilityRegistry().register(platformDescriptor);
    }

    private void registerDocumentContributions(CapabilityDescriptor descriptor) {
        for (DocumentContribution contribution : descriptor.documents()) {
            String typeId = contribution.documentTypeId();
            for (DocumentTypeDescriptor existing : documentEngine.registeredTypes()) {
                if (typeId.equals(existing.typeId())) {
                    throw new IllegalStateException(
                            "Document type already registered: " + typeId);
                }
            }
            documentEngine.registerProcessor(contribution.processor());
        }
    }

    private void registerPublicServices(CapabilityDescriptor descriptor) {
        PlatformComponentMetadata owner = new PlatformComponentMetadata(
                descriptor.id().value(),
                descriptor.name(),
                descriptor.version().toString(),
                ComponentType.CAPABILITY);
        for (PublicServiceContribution<?> contribution : descriptor.publicServices()) {
            registerPublicService(contribution, owner);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void registerPublicService(PublicServiceContribution<T> contribution, PlatformComponentMetadata owner) {
        platformCore.serviceRegistry().register(
                contribution.serviceType(), contribution.serviceInstance(), owner);
    }

    private void compensate(
            CapabilityId id, boolean reserved, boolean catalogsRegistered, RuntimeException originalFailure) {
        if (catalogsRegistered) {
            try {
                contributionCatalogs.removeAllForOwner(id);
            } catch (RuntimeException compensationFailure) {
                originalFailure.addSuppressed(compensationFailure);
            }
        }
        if (reserved) {
            try {
                capabilityRegistry.release(id);
            } catch (RuntimeException compensationFailure) {
                originalFailure.addSuppressed(compensationFailure);
            }
        }
    }
}
