package com.tmp.capability.validation;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityVersion;
import com.tmp.capability.api.DependencyDescriptor;
import com.tmp.capability.api.DependencyValidationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates the dependency graph among discovered Capabilities and computes a
 * deterministic initialization order (dependencies before dependents). This class
 * performs no registration or contribution logic, and never mutates a registry — it is a
 * pure, stateless graph algorithm over the {@link Capability} list handed to it.
 *
 * <p>The specification rule that a Capability may only depend on another Capability's
 * public contract (never its internal implementation) is enforced structurally rather
 * than at runtime: {@link DependencyDescriptor} references only a {@link CapabilityId},
 * never a concrete class, so it is impossible to declare a dependency on internal
 * implementation details through this contract. No additional runtime check is needed or
 * possible here without reflection, which this module does not use.
 */
public final class DependencyGraphValidator {

    private DependencyGraphValidator() {
    }

    /**
     * Validates every dependency declared by the given capabilities against the full
     * discovered set, then returns them in deterministic topological order (a capability
     * always appears after every capability it depends on; ties are broken by
     * {@link CapabilityId} natural ordering).
     *
     * @throws DependencyValidationException if a dependency is missing, self-referential,
     *     duplicated, version-incompatible, or participates in a cycle (direct or indirect)
     */
    public static List<Capability> validate(List<Capability> discovered) {
        Objects.requireNonNull(discovered, "discovered");

        Map<CapabilityId, Capability> byId = new HashMap<>();
        for (Capability capability : discovered) {
            byId.put(capability.descriptor().id(), capability);
        }

        for (Capability capability : discovered) {
            validateDeclaredDependencies(capability, byId);
        }

        return topologicalOrder(discovered, byId);
    }

    /**
     * Returns the exact reverse of the given order, for use as the shutdown/deactivation
     * order (dependents stopped before their dependencies).
     */
    public static List<Capability> reverse(List<Capability> order) {
        Objects.requireNonNull(order, "order");
        List<Capability> reversed = new ArrayList<>(order);
        Collections.reverse(reversed);
        return List.copyOf(reversed);
    }

    private static void validateDeclaredDependencies(Capability capability, Map<CapabilityId, Capability> byId) {
        CapabilityId ownerId = capability.descriptor().id();
        Set<CapabilityId> seenTargets = new HashSet<>();

        for (DependencyDescriptor dependency : capability.descriptor().dependencies()) {
            CapabilityId targetId = dependency.dependencyId();

            if (targetId.equals(ownerId)) {
                throw DependencyValidationException.selfDependency(ownerId);
            }
            if (!seenTargets.add(targetId)) {
                throw DependencyValidationException.duplicateDependency(ownerId, targetId);
            }

            Capability target = byId.get(targetId);
            if (target == null) {
                throw DependencyValidationException.missingDependency(ownerId, targetId);
            }

            CapabilityVersion actualVersion = target.descriptor().version();
            if (!actualVersion.isCompatibleWith(dependency.minimumVersion())) {
                throw DependencyValidationException.incompatibleVersion(
                        ownerId, targetId, dependency.minimumVersion(), actualVersion);
            }
        }
    }

    private static List<Capability> topologicalOrder(List<Capability> discovered, Map<CapabilityId, Capability> byId) {
        Map<CapabilityId, Integer> remainingInDegree = new HashMap<>();
        Map<CapabilityId, List<CapabilityId>> dependents = new HashMap<>();

        for (Capability capability : discovered) {
            remainingInDegree.putIfAbsent(capability.descriptor().id(), 0);
        }
        for (Capability capability : discovered) {
            CapabilityId ownerId = capability.descriptor().id();
            for (DependencyDescriptor dependency : capability.descriptor().dependencies()) {
                remainingInDegree.merge(ownerId, 1, Integer::sum);
                dependents.computeIfAbsent(dependency.dependencyId(), key -> new ArrayList<>()).add(ownerId);
            }
        }

        TreeSet<CapabilityId> ready = new TreeSet<>(Comparator.comparing(CapabilityId::value));
        remainingInDegree.forEach((id, degree) -> {
            if (degree == 0) {
                ready.add(id);
            }
        });

        List<Capability> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            CapabilityId next = ready.pollFirst();
            order.add(byId.get(next));
            for (CapabilityId dependent : dependents.getOrDefault(next, List.of())) {
                int updated = remainingInDegree.merge(dependent, -1, Integer::sum);
                if (updated == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (order.size() != discovered.size()) {
            List<CapabilityId> cycleMembers = remainingInDegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.comparing(CapabilityId::value))
                    .toList();
            throw DependencyValidationException.cyclicDependency(cycleMembers);
        }

        return List.copyOf(order);
    }
}
