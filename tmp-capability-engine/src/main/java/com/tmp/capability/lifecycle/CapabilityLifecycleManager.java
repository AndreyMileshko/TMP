package com.tmp.capability.lifecycle;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import com.tmp.capability.api.DependencyDescriptor;
import com.tmp.capability.contribution.CapabilityContributionCatalogs;
import com.tmp.capability.registry.CapabilityRegistration;
import com.tmp.capability.registry.CapabilityRegistry;
import com.tmp.capability.validation.DependencyGraphValidator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Orchestrates Capability lifecycle transitions after registration: initialization in
 * topological dependency order, activation, reverse-order stop, and dependent-aware
 * deactivation. Every state mutation is gated by {@link CapabilityStateTransition#isAllowed}.
 *
 * <p>Failure isolation: an error in one Capability marks it {@code FAILED} and marks every
 * transitive dependent {@code FAILED} with a chained "dependency failed" cause; already
 * successful independent Capabilities are left untouched. Deactivation never cascades to
 * dependents automatically — it is rejected while any active dependent exists.
 */
public final class CapabilityLifecycleManager {

    private final CapabilityRegistry capabilityRegistry;
    private final CapabilityContributionCatalogs contributionCatalogs;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<CapabilityId, Throwable> failureCauses = new HashMap<>();

    public CapabilityLifecycleManager(
            CapabilityRegistry capabilityRegistry, CapabilityContributionCatalogs contributionCatalogs) {
        this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry");
        this.contributionCatalogs = Objects.requireNonNull(contributionCatalogs, "contributionCatalogs");
    }

    public void initializeAll() {
        lock.lock();
        try {
            List<Capability> order = topologicalOrderOfRegistered();
            Set<CapabilityId> failed = new HashSet<>();
            for (Capability capability : order) {
                CapabilityId id = capability.descriptor().id();
                CapabilityRegistration registration = requireRegistration(id);
                if (registration.state() != CapabilityLifecycleState.REGISTERED) {
                    continue;
                }
                if (dependsOnFailed(capability, failed)) {
                    markFailed(id, new IllegalStateException(
                            "Capability '" + id + "' dependency failed",
                            primaryDependencyFailure(capability, failed)));
                    failed.add(id);
                    continue;
                }
                try {
                    capability.onInitialize();
                    transition(id, CapabilityLifecycleState.INITIALIZED);
                } catch (RuntimeException failure) {
                    markFailed(id, failure);
                    failed.add(id);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void activateAll() {
        lock.lock();
        try {
            for (CapabilityRegistration registration : capabilityRegistry.findAll()) {
                if (registration.state() == CapabilityLifecycleState.ACTIVE) {
                    throw new IllegalStateException(
                            "Repeated activation rejected: capability '"
                                    + registration.descriptor().id()
                                    + "' is already ACTIVE");
                }
            }

            List<Capability> order = topologicalOrderOfRegistered();
            Set<CapabilityId> failed = new HashSet<>();
            for (Capability capability : order) {
                CapabilityId id = capability.descriptor().id();
                CapabilityRegistration registration = requireRegistration(id);
                if (registration.state() == CapabilityLifecycleState.FAILED) {
                    failed.add(id);
                    continue;
                }
                if (registration.state() != CapabilityLifecycleState.INITIALIZED) {
                    continue;
                }
                if (dependsOnFailed(capability, failed)) {
                    markFailed(id, new IllegalStateException(
                            "Capability '" + id + "' dependency failed",
                            primaryDependencyFailure(capability, failed)));
                    failed.add(id);
                    continue;
                }
                try {
                    capability.onActivate();
                    transition(id, CapabilityLifecycleState.ACTIVE);
                } catch (RuntimeException failure) {
                    markFailed(id, failure);
                    failed.add(id);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public void deactivate(CapabilityId id) {
        Objects.requireNonNull(id, "id");
        lock.lock();
        try {
            CapabilityRegistration registration = requireRegistration(id);
            if (registration.state() != CapabilityLifecycleState.ACTIVE) {
                throw new IllegalStateException(
                        "Capability '" + id + "' cannot be deactivated from state " + registration.state());
            }

            List<CapabilityId> activeDependents = findActiveDependents(id);
            if (!activeDependents.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot deactivate capability '" + id
                                + "': active dependents present: " + activeDependents);
            }

            Capability capability = registration.capability();
            transition(id, CapabilityLifecycleState.STOPPED);
            capability.onStop();
            transition(id, CapabilityLifecycleState.DEACTIVATED);
            capability.onDeactivate();
            contributionCatalogs.removeAllForOwner(id);
        } finally {
            lock.unlock();
        }
    }

    public void stopAll() {
        lock.lock();
        try {
            List<Capability> forward = topologicalOrderOfRegistered();
            List<Capability> reverse = DependencyGraphValidator.reverse(forward);
            for (Capability capability : reverse) {
                CapabilityId id = capability.descriptor().id();
                CapabilityRegistration registration = requireRegistration(id);
                if (registration.state() != CapabilityLifecycleState.ACTIVE) {
                    continue;
                }
                transition(id, CapabilityLifecycleState.STOPPED);
                capability.onStop();
            }
        } finally {
            lock.unlock();
        }
    }

    private List<Capability> topologicalOrderOfRegistered() {
        List<Capability> capabilities = capabilityRegistry.findAll().stream()
                .map(CapabilityRegistration::capability)
                .collect(Collectors.toCollection(ArrayList::new));
        return DependencyGraphValidator.validate(capabilities);
    }

    private CapabilityRegistration requireRegistration(CapabilityId id) {
        return capabilityRegistry
                .findById(id)
                .orElseThrow(() -> new IllegalStateException("Capability not registered: " + id));
    }

    private void transition(CapabilityId id, CapabilityLifecycleState to) {
        CapabilityLifecycleState from = requireRegistration(id).state();
        if (!CapabilityStateTransition.isAllowed(from, to)) {
            throw new IllegalStateException(
                    "Illegal capability lifecycle transition for '" + id + "': " + from + " -> " + to);
        }
        capabilityRegistry.updateState(id, to);
    }

    private void markFailed(CapabilityId id, Throwable cause) {
        CapabilityLifecycleState from = requireRegistration(id).state();
        if (CapabilityStateTransition.isAllowed(from, CapabilityLifecycleState.FAILED)) {
            capabilityRegistry.updateState(id, CapabilityLifecycleState.FAILED);
        }
        failureCauses.put(id, cause);
    }

    private boolean dependsOnFailed(Capability capability, Set<CapabilityId> failed) {
        for (DependencyDescriptor dependency : capability.descriptor().dependencies()) {
            if (failed.contains(dependency.dependencyId())) {
                return true;
            }
            CapabilityLifecycleState dependencyState = capabilityRegistry
                    .findById(dependency.dependencyId())
                    .map(CapabilityRegistration::state)
                    .orElse(CapabilityLifecycleState.FAILED);
            if (dependencyState == CapabilityLifecycleState.FAILED) {
                return true;
            }
        }
        return false;
    }

    private Throwable primaryDependencyFailure(Capability capability, Set<CapabilityId> failed) {
        for (DependencyDescriptor dependency : capability.descriptor().dependencies()) {
            CapabilityId dependencyId = dependency.dependencyId();
            if (failed.contains(dependencyId) || capabilityRegistry
                    .findById(dependencyId)
                    .map(CapabilityRegistration::state)
                    .orElse(CapabilityLifecycleState.FAILED)
                    == CapabilityLifecycleState.FAILED) {
                Throwable nested = failureCauses.get(dependencyId);
                return nested != null
                        ? nested
                        : new IllegalStateException("Dependency '" + dependencyId + "' is FAILED");
            }
        }
        return new IllegalStateException("Dependency failed");
    }

    private List<CapabilityId> findActiveDependents(CapabilityId targetId) {
        List<CapabilityId> dependents = new ArrayList<>();
        for (CapabilityRegistration registration : capabilityRegistry.findAll()) {
            if (registration.state() != CapabilityLifecycleState.ACTIVE) {
                continue;
            }
            CapabilityId candidateId = registration.descriptor().id();
            if (candidateId.equals(targetId)) {
                continue;
            }
            for (DependencyDescriptor dependency : registration.descriptor().dependencies()) {
                if (dependency.dependencyId().equals(targetId)) {
                    dependents.add(candidateId);
                }
            }
        }
        dependents.sort((left, right) -> left.value().compareTo(right.value()));
        return dependents;
    }
}
