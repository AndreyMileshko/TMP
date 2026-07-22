package com.tmp.capability.api;

import com.tmp.core.api.EventBus;
import com.tmp.core.api.PlatformCore;

/**
 * Scoped access to capability lifecycle resources. During {@code onInitialize},
 * {@code onActivate}, {@code onStop}, and {@code onDeactivate} the Capability Engine
 * installs a tracking {@link EventBus} so subscriptions can be released automatically.
 */
public final class CapabilityRuntimeAccess {

    private static final ThreadLocal<EventBus> eventBus = new ThreadLocal<>();

    private CapabilityRuntimeAccess() {
    }

    /**
     * Returns the lifecycle-scoped event bus when invoked inside a capability callback;
     * otherwise falls back to the platform bus.
     */
    public static EventBus eventBus(PlatformCore platformCore) {
        EventBus scoped = eventBus.get();
        if (scoped != null) {
            return scoped;
        }
        return platformCore.eventBus();
    }

    /**
     * Runs an action with the lifecycle-scoped event bus installed for capability callbacks.
     */
    public static void runWithEventBus(EventBus scopedEventBus, Runnable action) {
        setEventBus(scopedEventBus);
        try {
            action.run();
        } finally {
            clear();
        }
    }

    static void setEventBus(EventBus scopedEventBus) {
        if (scopedEventBus == null) {
            eventBus.remove();
        } else {
            eventBus.set(scopedEventBus);
        }
    }

    static void clear() {
        eventBus.remove();
    }
}
