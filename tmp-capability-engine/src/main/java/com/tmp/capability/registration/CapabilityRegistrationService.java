package com.tmp.capability.registration;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.DocumentContribution;
import com.tmp.capability.api.PublicServiceContribution;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.contribution.CapabilityExternalContributionRegistry;
import com.tmp.capability.lifecycle.CapabilityEventSubscriptionRegistry;
import com.tmp.capability.registry.CapabilityRegistration;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.ServiceRegistration;
import com.tmp.core.api.component.ComponentType;
import com.tmp.core.api.component.PlatformComponentMetadata;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentProcessorRegistration;
import com.tmp.document.api.DocumentTypeDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Atomic registration entry point for a Capability. Each successful external step records a
 * compensation handle; failures unwind internal and external state in reverse order while
 * preserving the original exception and suppressing compensation failures.
 */
public final class CapabilityRegistrationService {

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityContributionCatalogs contributionCatalogs;
    private final CapabilityExternalContributionRegistry externalContributions;
    private final CapabilityEventSubscriptionRegistry eventSubscriptions;
    private final PlatformCore platformCore;
    private final DocumentEngine documentEngine;
    private final ReentrantLock registrationLock = new ReentrantLock();

    public CapabilityRegistrationService(
            CapabilityRegistry capabilityRegistry,
            CapabilityContributionCatalogs contributionCatalogs,
            CapabilityExternalContributionRegistry externalContributions,
            CapabilityEventSubscriptionRegistry eventSubscriptions,
            PlatformCore platformCore,
            DocumentEngine documentEngine) {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
        this.contributionCatalogs = Objects.requireNonNull(contributionCatalogs, "contributionCatalogs");
        this.externalContributions = Objects.requireNonNull(externalContributions, "externalContributions");
        this.eventSubscriptions = Objects.requireNonNull(eventSubscriptions, "eventSubscriptions");
        this.platformCore = Objects.requireNonNull(platformCore, "platformCore");
        this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
    }

    public void register(Capability capability) {
        Objects.requireNonNull(capability, "capability");
        CapabilityDescriptor descriptor = Objects.requireNonNull(capability.descriptor(), "descriptor");
        CapabilityId id = descriptor.id();

        registrationLock.lock();
        try {
            boolean reserved = false;
            boolean catalogsRegistered = false;
            List<Runnable> compensations = new ArrayList<>();
            try {
                capabilityRegistry.reserve(id);
                reserved = true;
                compensations.add(() -> capabilityRegistry.release(id));

                contributionCatalogs.registerInternalContributions(descriptor);
                catalogsRegistered = true;
                compensations.add(() -> contributionCatalogs.removeAllForOwner(id));

                registerPlatformCapabilityDescriptor(id, descriptor, compensations);
                registerDocumentContributions(id, descriptor, compensations);
                registerPublicServices(id, descriptor, compensations);

                capabilityRegistry.commit(
                        new CapabilityRegistration(descriptor, CapabilityLifecycleState.REGISTERED, capability));
            } catch (RuntimeException failure) {
                compensate(id, reserved, catalogsRegistered, compensations, failure);
                throw new CapabilityRegistrationException(
                        "Failed to register capability '" + id + "': " + failure.getMessage(), failure);
            }
        } finally {
            registrationLock.unlock();
        }
    }

    private void registerPlatformCapabilityDescriptor(
            CapabilityId id, CapabilityDescriptor descriptor, List<Runnable> compensations) {
        com.tmp.core.api.capability.CapabilityDescriptor platformDescriptor =
                new com.tmp.core.api.capability.CapabilityDescriptor(
                        descriptor.id().value(),
                        descriptor.name(),
                        descriptor.version().toString());
        platformCore.capabilityRegistry().register(platformDescriptor);
        externalContributions.recordPlatformCapabilityRegistration(id);
        compensations.add(() -> platformCore.capabilityRegistry().unregister(id.value()));
    }

    private void registerDocumentContributions(
            CapabilityId id, CapabilityDescriptor descriptor, List<Runnable> compensations) {
        for (DocumentContribution contribution : descriptor.documents()) {
            String typeId = contribution.documentTypeId();
            for (DocumentTypeDescriptor existing : documentEngine.registeredTypes()) {
                if (typeId.equals(existing.typeId())) {
                    throw new IllegalStateException(
                            "Document type already registered: " + typeId);
                }
            }
            DocumentProcessorRegistration registration = documentEngine.registerProcessor(contribution.processor());
            externalContributions.recordDocumentProcessor(id, registration);
            compensations.add(registration::unregister);
        }
    }

    private void registerPublicServices(
            CapabilityId id, CapabilityDescriptor descriptor, List<Runnable> compensations) {
        PlatformComponentMetadata owner = new PlatformComponentMetadata(
                descriptor.id().value(),
                descriptor.name(),
                descriptor.version().toString(),
                ComponentType.CAPABILITY);
        for (PublicServiceContribution<?> contribution : descriptor.publicServices()) {
            ServiceRegistration registration = registerPublicService(contribution, owner);
            externalContributions.recordServiceRegistration(id, registration);
            compensations.add(registration::unregister);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ServiceRegistration registerPublicService(
            PublicServiceContribution<T> contribution, PlatformComponentMetadata owner) {
        return platformCore
                .serviceRegistry()
                .register(contribution.serviceType(), contribution.serviceInstance(), owner);
    }

    private void compensate(
            CapabilityId id,
            boolean reserved,
            boolean catalogsRegistered,
            List<Runnable> compensations,
            RuntimeException originalFailure) {
        eventSubscriptions.unsubscribeAll(id);
        List<Runnable> ordered = new ArrayList<>(compensations);
        Collections.reverse(ordered);
        for (Runnable compensation : ordered) {
            try {
                compensation.run();
            } catch (RuntimeException compensationFailure) {
                originalFailure.addSuppressed(compensationFailure);
            }
        }
        try {
            externalContributions.clear(id);
        } catch (RuntimeException compensationFailure) {
            originalFailure.addSuppressed(compensationFailure);
        }
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
