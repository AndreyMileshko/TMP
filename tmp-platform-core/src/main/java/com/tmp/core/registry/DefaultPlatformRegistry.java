package com.tmp.core.registry;

import com.tmp.core.api.PlatformRegistry;
import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultPlatformRegistry implements PlatformRegistry {

    private final Map<String, PlatformComponent> components = new ConcurrentHashMap<>();

    @Override
    public void register(PlatformComponent component) {
        PlatformComponentMetadata metadata = component.metadata();
        PlatformComponent existing = components.putIfAbsent(metadata.id(), component);
        if (existing != null) {
            throw new IllegalStateException("Platform component already registered: " + metadata.id());
        }
    }

    @Override
    public Optional<PlatformComponent> findById(String componentId) {
        return Optional.ofNullable(components.get(componentId));
    }

    @Override
    public List<PlatformComponentMetadata> registeredComponents() {
        return components.values().stream()
                .map(PlatformComponent::metadata)
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .toList();
    }
}
