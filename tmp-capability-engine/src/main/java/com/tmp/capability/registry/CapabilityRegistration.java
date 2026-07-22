package com.tmp.capability.registry;

import com.tmp.capability.api.Capability;
import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityLifecycleState;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Objects;

/**
 * Immutable snapshot of a single Capability's registration state: its descriptor, its
 * current {@link CapabilityLifecycleState}, and the {@link Capability} instance that owns
 * that descriptor. Consumers of {@link CapabilityRegistry#findAll()} /
 * {@link CapabilityRegistry#findById(com.tmp.capability.api.CapabilityId)} observe one of
 * these per registered Capability; a state change produces a new instance rather than
 * mutating this one.
 */
@SuppressFBWarnings(
        value = "EI_EXPOSE_REP2",
        justification = "CapabilityDescriptor and Capability are themselves immutable/behavioral contracts; "
                + "storing the given references directly is safe and intentional.")
public final class CapabilityRegistration {

    private final CapabilityDescriptor descriptor;
    private final CapabilityLifecycleState state;
    private final Capability capability;

    public CapabilityRegistration(CapabilityDescriptor descriptor, CapabilityLifecycleState state, Capability capability) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.state = Objects.requireNonNull(state, "state");
        this.capability = Objects.requireNonNull(capability, "capability");
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "CapabilityDescriptor is an immutable value type; returning it directly is safe.")
    public CapabilityDescriptor descriptor() {
        return descriptor;
    }

    public CapabilityLifecycleState state() {
        return state;
    }

    public Capability capability() {
        return capability;
    }

    CapabilityRegistration withState(CapabilityLifecycleState newState) {
        Objects.requireNonNull(newState, "newState");
        return new CapabilityRegistration(descriptor, newState, capability);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilityRegistration that)) {
            return false;
        }
        return descriptor.equals(that.descriptor) && state == that.state && capability.equals(that.capability);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor, state, capability);
    }

    @Override
    public String toString() {
        return descriptor.id() + " [" + state + "]";
    }
}
