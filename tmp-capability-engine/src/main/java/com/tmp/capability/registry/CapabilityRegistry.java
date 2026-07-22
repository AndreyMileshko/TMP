package com.tmp.capability.registry;

import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityLifecycleState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, in-memory catalog of registered Capabilities, owned exclusively by the
 * Capability Engine. Distinct from {@code com.tmp.core.api.CapabilityRegistry} (Stage 1):
 * this registry stores the richer {@link CapabilityRegistration} snapshot (descriptor +
 * lifecycle state + owning instance) and is not part of the public API.
 *
 * <p>Registration is a two-phase protocol: {@link #reserve(CapabilityId)} atomically
 * claims an id before any potentially-failing contribution registration is attempted;
 * {@link #commit(CapabilityRegistration)} finalizes it, or {@link #release(CapabilityId)}
 * frees the id after a rollback so a retry can succeed. No partial state is ever visible
 * between {@code reserve} and {@code commit}: a reserved id has no entry in
 * {@link #findById(CapabilityId)} / {@link #findAll()} until it is committed.
 */
public final class CapabilityRegistry {

    private final Map<CapabilityId, CapabilityRegistration> registrations = new ConcurrentHashMap<>();
    private final Set<CapabilityId> reservedIds = ConcurrentHashMap.newKeySet();
    private final ReentrantLock lock = new ReentrantLock();

    public void reserve(CapabilityId id) {
        Objects.requireNonNull(id, "id");
        lock.lock();
        try {
            if (registrations.containsKey(id)) {
                throw new IllegalStateException("Capability id already registered: " + id);
            }
            if (!reservedIds.add(id)) {
                throw new IllegalStateException("Capability id already reserved: " + id);
            }
        } finally {
            lock.unlock();
        }
    }

    public void release(CapabilityId id) {
        Objects.requireNonNull(id, "id");
        lock.lock();
        try {
            reservedIds.remove(id);
        } finally {
            lock.unlock();
        }
    }

    public void commit(CapabilityRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        CapabilityId id = registration.descriptor().id();
        lock.lock();
        try {
            if (registrations.containsKey(id)) {
                throw new IllegalStateException("Capability id already registered: " + id);
            }
            registrations.put(id, registration);
            reservedIds.remove(id);
        } finally {
            lock.unlock();
        }
    }

    public void updateState(CapabilityId id, CapabilityLifecycleState newState) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(newState, "newState");
        lock.lock();
        try {
            CapabilityRegistration current = registrations.get(id);
            if (current == null) {
                throw new IllegalStateException("Capability id not registered: " + id);
            }
            registrations.put(id, current.withState(newState));
        } finally {
            lock.unlock();
        }
    }

    public Optional<CapabilityRegistration> findById(CapabilityId id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(registrations.get(id));
    }

    public List<CapabilityRegistration> findAll() {
        List<CapabilityRegistration> snapshot = new ArrayList<>(registrations.values());
        snapshot.sort(Comparator.comparing(registration -> registration.descriptor().id().value()));
        return List.copyOf(snapshot);
    }
}
