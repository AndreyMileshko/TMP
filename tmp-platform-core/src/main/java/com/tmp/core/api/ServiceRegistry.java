package com.tmp.core.api;

import com.tmp.core.api.component.PlatformComponentMetadata;
import java.util.List;
import java.util.Optional;

/**
 * Registry of infrastructure services exposed by platform components.
 */
public interface ServiceRegistry {

    <T> void register(Class<T> serviceType, T instance, PlatformComponentMetadata owner);

    <T> Optional<T> lookup(Class<T> serviceType);

    <T> List<T> lookupAll(Class<T> serviceType);

    List<PlatformComponentMetadata> registeredServices();
}
