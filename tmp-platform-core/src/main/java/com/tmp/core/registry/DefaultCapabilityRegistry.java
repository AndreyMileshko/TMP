package com.tmp.core.registry;

import com.tmp.core.api.CapabilityRegistry;
import com.tmp.core.api.capability.CapabilityDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultCapabilityRegistry implements CapabilityRegistry {

    private final Map<String, CapabilityDescriptor> capabilities = new ConcurrentHashMap<>();

    @Override
    public void register(CapabilityDescriptor descriptor) {
        CapabilityDescriptor existing = capabilities.putIfAbsent(descriptor.id(), descriptor);
        if (existing != null) {
            throw new IllegalStateException("Capability already registered: " + descriptor.id());
        }
    }

    @Override
    public Optional<CapabilityDescriptor> findById(String capabilityId) {
        return Optional.ofNullable(capabilities.get(capabilityId));
    }

    @Override
    public List<CapabilityDescriptor> findAll() {
        List<CapabilityDescriptor> result = new ArrayList<>(capabilities.values());
        result.sort((left, right) -> left.id().compareTo(right.id()));
        return List.copyOf(result);
    }

    @Override
    public void unregister(String capabilityId) {
        if (capabilityId != null) {
            capabilities.remove(capabilityId);
        }
    }
}
