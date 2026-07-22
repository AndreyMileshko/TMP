package com.tmp.capability.lifecycle;

import com.tmp.capability.api.CapabilityId;
import com.tmp.capability.api.CapabilityRuntimeAccess;
import com.tmp.core.api.EventBus;
import com.tmp.core.api.event.EventSubscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks event subscriptions created while a capability lifecycle callback is running.
 */
public final class CapabilityEventSubscriptionRegistry {

    private final ThreadLocal<CapabilityId> currentCapability = new ThreadLocal<>();
    private final Map<CapabilityId, List<EventSubscription>> subscriptions = new ConcurrentHashMap<>();

    public void runWithCapability(CapabilityId capabilityId, Runnable action) {
        Objects.requireNonNull(capabilityId, "capabilityId");
        Objects.requireNonNull(action, "action");
        CapabilityId previous = currentCapability.get();
        currentCapability.set(capabilityId);
        try {
            action.run();
        } finally {
            if (previous == null) {
                currentCapability.remove();
            } else {
                currentCapability.set(previous);
            }
        }
    }

    public void runWithCapability(CapabilityId capabilityId, EventBus scopedEventBus, Runnable action) {
        runWithCapability(capabilityId, () -> CapabilityRuntimeAccess.runWithEventBus(scopedEventBus, action));
    }

    public void recordSubscription(EventSubscription subscription) {
        CapabilityId capabilityId = currentCapability.get();
        if (capabilityId == null || subscription == null) {
            return;
        }
        subscriptions.computeIfAbsent(capabilityId, ignored -> new ArrayList<>()).add(subscription);
    }

    public void unsubscribeAll(CapabilityId capabilityId) {
        List<EventSubscription> owned = subscriptions.remove(capabilityId);
        if (owned == null) {
            return;
        }
        for (EventSubscription subscription : owned) {
            try {
                subscription.unsubscribe();
            } catch (RuntimeException ignored) {
                // best-effort cleanup during rollback or shutdown
            }
        }
    }

    CapabilityId currentCapability() {
        return currentCapability.get();
    }
}
