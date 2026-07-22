package com.tmp.core.api;

import com.tmp.core.api.component.PlatformComponentMetadata;

/**
 * Reversible handle returned when a service instance is registered in the
 * {@link ServiceRegistry}. Unregister removes the instance from lookup without
 * affecting unrelated registrations.
 */
public interface ServiceRegistration {

    Class<?> serviceType();

    PlatformComponentMetadata owner();

    void unregister();
}
