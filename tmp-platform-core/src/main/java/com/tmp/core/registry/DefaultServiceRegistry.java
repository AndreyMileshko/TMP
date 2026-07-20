package com.tmp.core.registry;

import com.tmp.core.api.ServiceRegistry;
import com.tmp.core.api.component.PlatformComponentMetadata;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DefaultServiceRegistry implements ServiceRegistry {

    private final Map<Class<?>, CopyOnWriteArrayList<ServiceEntry<?>>> services = new ConcurrentHashMap<>();

    @Override
    public <T> void register(Class<T> serviceType, T instance, PlatformComponentMetadata owner) {
        services.computeIfAbsent(serviceType, ignored -> new CopyOnWriteArrayList<>())
                .add(new ServiceEntry<>(instance, owner));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> lookup(Class<T> serviceType) {
        CopyOnWriteArrayList<ServiceEntry<?>> entries = services.get(serviceType);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of((T) entries.getFirst().instance());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> lookupAll(Class<T> serviceType) {
        CopyOnWriteArrayList<ServiceEntry<?>> entries = services.get(serviceType);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<T> result = new ArrayList<>(entries.size());
        for (ServiceEntry<?> entry : entries) {
            result.add((T) entry.instance());
        }
        return List.copyOf(result);
    }

    @Override
    public List<PlatformComponentMetadata> registeredServices() {
        List<PlatformComponentMetadata> result = new ArrayList<>();
        for (CopyOnWriteArrayList<ServiceEntry<?>> entries : services.values()) {
            for (ServiceEntry<?> entry : entries) {
                result.add(entry.owner());
            }
        }
        result.sort(Comparator.comparing(PlatformComponentMetadata::id));
        return List.copyOf(result);
    }

    @Override
    public int registeredServiceCount() {
        int count = 0;
        for (CopyOnWriteArrayList<ServiceEntry<?>> entries : services.values()) {
            count += entries.size();
        }
        return count;
    }

    private record ServiceEntry<T>(T instance, PlatformComponentMetadata owner) {
    }
}
