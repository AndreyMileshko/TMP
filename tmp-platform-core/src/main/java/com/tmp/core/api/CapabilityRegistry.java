package com.tmp.core.api;

import com.tmp.core.api.capability.CapabilityDescriptor;
import java.util.List;
import java.util.Optional;

/**
 * Registry of capability metadata. Does not load or manage capability internals.
 */
public interface CapabilityRegistry {

    void register(CapabilityDescriptor descriptor);

    /**
     * Removes capability metadata previously registered for rollback or deactivation.
     * Idempotent when the id is not registered.
     */
    void unregister(String capabilityId);

    Optional<CapabilityDescriptor> findById(String capabilityId);

    List<CapabilityDescriptor> findAll();
}
