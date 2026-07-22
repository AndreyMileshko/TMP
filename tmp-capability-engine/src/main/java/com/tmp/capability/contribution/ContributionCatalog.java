package com.tmp.capability.contribution;

import com.tmp.capability.api.CapabilityId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Thread-safe, owner-tracked catalog of a single contribution type (permission, command,
 * view, navigation, settings, or event descriptor). Identity of each entry is extracted
 * via the supplied {@code idExtractor}; duplicate ids are rejected across <em>all</em>
 * owners (cross-capability id conflict).
 *
 * <p>This is a pure in-memory catalog: it never calls Document Engine or Platform Core,
 * and it performs no authorization checks. Rollback and deactivation both use
 * {@link #removeAllForOwner(CapabilityId)}.
 *
 * @param <T> contribution descriptor type
 */
public final class ContributionCatalog<T> {

    private final Function<T, String> idExtractor;
    private final String contributionKind;
    private final Map<String, OwnedEntry<T>> byId = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();

    public ContributionCatalog(String contributionKind, Function<T, String> idExtractor) {
        this.contributionKind = Objects.requireNonNull(contributionKind, "contributionKind");
        this.idExtractor = Objects.requireNonNull(idExtractor, "idExtractor");
    }

    public void add(CapabilityId owner, T descriptor) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(descriptor, "descriptor");
        String id = idExtractor.apply(descriptor);
        Objects.requireNonNull(id, "descriptor id");

        lock.lock();
        try {
            OwnedEntry<T> existing = byId.get(id);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate " + contributionKind + " id '" + id + "': already owned by "
                                + existing.owner() + ", rejected for owner " + owner);
            }
            byId.put(id, new OwnedEntry<>(owner, descriptor));
        } finally {
            lock.unlock();
        }
    }

    public void removeAllForOwner(CapabilityId owner) {
        Objects.requireNonNull(owner, "owner");
        lock.lock();
        try {
            byId.entrySet().removeIf(entry -> entry.getValue().owner().equals(owner));
        } finally {
            lock.unlock();
        }
    }

    public List<T> activeEntries() {
        List<T> snapshot = new ArrayList<>();
        for (OwnedEntry<T> entry : byId.values()) {
            snapshot.add(entry.descriptor());
        }
        return List.copyOf(snapshot);
    }

    public Optional<CapabilityId> ownerOf(String descriptorId) {
        Objects.requireNonNull(descriptorId, "descriptorId");
        OwnedEntry<T> entry = byId.get(descriptorId);
        return entry == null ? Optional.empty() : Optional.of(entry.owner());
    }

    private record OwnedEntry<T>(CapabilityId owner, T descriptor) {
        private OwnedEntry {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(descriptor, "descriptor");
        }
    }
}
