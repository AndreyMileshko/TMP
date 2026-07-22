package com.tmp.capability.contribution;

import com.tmp.capability.api.CapabilityId;
import com.tmp.core.api.ServiceRegistration;
import com.tmp.document.api.DocumentProcessorRegistration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks reversible external contribution handles registered for each capability so
 * registration rollback and deactivation can compensate Platform Core / Document Engine state.
 */
public final class CapabilityExternalContributionRegistry {

    private final Map<CapabilityId, ExternalContributions> contributions = new ConcurrentHashMap<>();

    public void recordDocumentProcessor(CapabilityId owner, DocumentProcessorRegistration registration) {
        externalContributions(owner).documentProcessors.add(registration);
    }

    public void recordServiceRegistration(CapabilityId owner, ServiceRegistration registration) {
        externalContributions(owner).serviceRegistrations.add(registration);
    }

    public void recordPlatformCapabilityRegistration(CapabilityId owner) {
        externalContributions(owner).platformCapabilityRegistered = true;
    }

    public List<DocumentProcessorRegistration> documentProcessors(CapabilityId owner) {
        return List.copyOf(externalContributions(owner).documentProcessors);
    }

    public List<ServiceRegistration> serviceRegistrations(CapabilityId owner) {
        return List.copyOf(externalContributions(owner).serviceRegistrations);
    }

    public boolean hasPlatformCapabilityRegistration(CapabilityId owner) {
        return externalContributions(owner).platformCapabilityRegistered;
    }

    public void compensateAll(CapabilityId owner, Runnable platformCapabilityUnregister) {
        ExternalContributions external = contributions.remove(owner);
        if (external == null) {
            return;
        }
        RuntimeException firstFailure = null;
        for (int index = external.serviceRegistrations.size() - 1; index >= 0; index--) {
            ServiceRegistration registration = external.serviceRegistrations.get(index);
            firstFailure = runCompensation(firstFailure, registration::unregister);
        }
        for (int index = external.documentProcessors.size() - 1; index >= 0; index--) {
            DocumentProcessorRegistration registration = external.documentProcessors.get(index);
            firstFailure = runCompensation(firstFailure, registration::unregister);
        }
        if (external.platformCapabilityRegistered) {
            firstFailure = runCompensation(firstFailure, platformCapabilityUnregister);
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public void deactivateAll(CapabilityId owner, Runnable platformCapabilityUnregister) {
        ExternalContributions external = contributions.get(owner);
        if (external == null) {
            return;
        }
        for (ServiceRegistration registration : external.serviceRegistrations) {
            registration.unregister();
        }
        external.serviceRegistrations.clear();
        for (DocumentProcessorRegistration registration : external.documentProcessors) {
            registration.deactivate();
        }
        if (external.platformCapabilityRegistered) {
            platformCapabilityUnregister.run();
            external.platformCapabilityRegistered = false;
        }
    }

    public void clear(CapabilityId owner) {
        contributions.remove(owner);
    }

    private ExternalContributions externalContributions(CapabilityId owner) {
        return contributions.computeIfAbsent(owner, ignored -> new ExternalContributions());
    }

    private static RuntimeException runCompensation(RuntimeException firstFailure, Runnable compensation) {
        try {
            compensation.run();
            return firstFailure;
        } catch (RuntimeException failure) {
            if (firstFailure == null) {
                return failure;
            }
            firstFailure.addSuppressed(failure);
            return firstFailure;
        }
    }

    private static final class ExternalContributions {
        private final List<DocumentProcessorRegistration> documentProcessors = new ArrayList<>();
        private final List<ServiceRegistration> serviceRegistrations = new ArrayList<>();
        private boolean platformCapabilityRegistered;
    }
}
