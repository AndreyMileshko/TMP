package com.tmp.capability.discovery;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Discovers the set of technical Capabilities available at startup through explicit
 * Spring/Java composition: the caller (a Spring configuration) supplies the full list of
 * {@link Capability} beans, and this class only orders and duplicate-checks it. There is
 * no classloading, hot deployment, plugin marketplace, network discovery, custom
 * classloader, or microservice discovery of any kind — this class itself carries no
 * framework annotation and performs no I/O, keeping it framework-agnostic and
 * unit-testable.
 */
public final class CapabilityDiscovery {

    private final List<Capability> discoveredCapabilities;

    public CapabilityDiscovery(List<Capability> discoveredCapabilities) {
        this.discoveredCapabilities = List.copyOf(Objects.requireNonNull(discoveredCapabilities, "discoveredCapabilities"));
    }

    /**
     * Returns the discovered capabilities sorted deterministically by {@link CapabilityId},
     * regardless of the injection order supplied to the constructor.
     *
     * @throws IllegalStateException if two distinct {@link Capability} instances declare
     *     the same {@link CapabilityId}
     */
    public List<Capability> discover() {
        List<Capability> sorted = new ArrayList<>(discoveredCapabilities);
        sorted.sort(Comparator.comparing(capability -> capability.descriptor().id().value()));

        for (int index = 1; index < sorted.size(); index++) {
            Capability previous = sorted.get(index - 1);
            Capability current = sorted.get(index);
            CapabilityId previousId = previous.descriptor().id();
            if (previousId.equals(current.descriptor().id())) {
                throw new IllegalStateException(
                        "Duplicate capability id '" + previousId + "' discovered in both "
                                + previous.getClass().getName() + " and " + current.getClass().getName());
            }
        }

        return List.copyOf(sorted);
    }
}
