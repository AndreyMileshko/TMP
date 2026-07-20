package com.tmp.core.api;

import com.tmp.core.api.component.PlatformComponent;
import com.tmp.core.api.component.PlatformComponentMetadata;
import java.util.List;
import java.util.Optional;

/**
 * Registry of platform components.
 */
public interface PlatformRegistry {

    void register(PlatformComponent component);

    Optional<PlatformComponent> findById(String componentId);

    List<PlatformComponentMetadata> registeredComponents();
}
