package com.tmp.capability.api;

import java.util.Objects;

/**
 * Immutable, pure-data holder for a public service instance that a Capability contributes
 * through Platform Core's public {@code ServiceRegistry}. This type performs no
 * registration itself: it is a validated descriptor consumed by the registrar that talks
 * to Platform Core.
 *
 * @param <T> the public service contract type
 */
public final class PublicServiceContribution<T> {

    private final Class<T> serviceType;
    private final T serviceInstance;

    private PublicServiceContribution(Class<T> serviceType, T serviceInstance) {
        this.serviceType = serviceType;
        this.serviceInstance = serviceInstance;
    }

    public static <T> PublicServiceContribution<T> of(Class<T> serviceType, T serviceInstance) {
        Objects.requireNonNull(serviceType, "serviceType");
        Objects.requireNonNull(serviceInstance, "serviceInstance");
        if (!serviceType.isInstance(serviceInstance)) {
            throw new IllegalArgumentException(
                    "serviceInstance of type " + serviceInstance.getClass().getName()
                            + " is not assignable to declared serviceType " + serviceType.getName());
        }
        return new PublicServiceContribution<>(serviceType, serviceInstance);
    }

    public Class<T> serviceType() {
        return serviceType;
    }

    public T serviceInstance() {
        return serviceInstance;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PublicServiceContribution<?> that)) {
            return false;
        }
        return serviceType.equals(that.serviceType);
    }

    @Override
    public int hashCode() {
        return serviceType.hashCode();
    }

    @Override
    public String toString() {
        return serviceType.getName();
    }
}
