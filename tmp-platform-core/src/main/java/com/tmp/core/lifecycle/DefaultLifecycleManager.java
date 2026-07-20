package com.tmp.core.lifecycle;

import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.PlatformComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultLifecycleManager implements LifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLifecycleManager.class);

    private final Map<String, PlatformComponent> components = new LinkedHashMap<>();
    private final Map<String, ComponentLifecycleState> states = new ConcurrentHashMap<>();
    private PlatformCore platformCore;
    private ComponentLifecycleState platformState = ComponentLifecycleState.REGISTERED;

    public void attachPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
    }

    @Override
    public void registerComponent(PlatformComponent component) {
        String componentId = component.metadata().id();
        if (components.containsKey(componentId)) {
            throw new IllegalStateException("Component already registered for lifecycle: " + componentId);
        }
        components.put(componentId, component);
        states.put(componentId, ComponentLifecycleState.REGISTERED);
    }

    @Override
    public void startAll() {
        if (platformCore == null) {
            throw new IllegalStateException("PlatformCore is not attached to lifecycle manager");
        }
        platformState = ComponentLifecycleState.INITIALIZING;
        for (PlatformComponent component : components.values()) {
            String componentId = component.metadata().id();
            try {
                states.put(componentId, ComponentLifecycleState.INITIALIZING);
                component.initialize(platformCore);
                component.start();
                states.put(componentId, ComponentLifecycleState.STARTED);
            } catch (RuntimeException ex) {
                states.put(componentId, ComponentLifecycleState.FAILED);
                LOG.error("Failed to start component {}", componentId, ex);
                throw ex;
            }
        }
        platformState = ComponentLifecycleState.STARTED;
    }

    @Override
    public void stopAll() {
        platformState = ComponentLifecycleState.STOPPING;
        for (Map.Entry<String, PlatformComponent> entry : reverseEntries().entrySet()) {
            String componentId = entry.getKey();
            PlatformComponent component = entry.getValue();
            try {
                states.put(componentId, ComponentLifecycleState.STOPPING);
                component.stop();
                states.put(componentId, ComponentLifecycleState.STOPPED);
            } catch (RuntimeException ex) {
                states.put(componentId, ComponentLifecycleState.FAILED);
                LOG.warn("Failed to stop component {}", componentId, ex);
            }
        }
        platformState = ComponentLifecycleState.STOPPED;
    }

    @Override
    public ComponentLifecycleState stateOf(String componentId) {
        return states.getOrDefault(componentId, ComponentLifecycleState.REGISTERED);
    }

    @Override
    public Map<String, ComponentLifecycleState> allStates() {
        return Map.copyOf(states);
    }

    public ComponentLifecycleState platformState() {
        return platformState;
    }

    private Map<String, PlatformComponent> reverseEntries() {
        LinkedHashMap<String, PlatformComponent> reversed = new LinkedHashMap<>();
        String[] keys = components.keySet().toArray(String[]::new);
        for (int index = keys.length - 1; index >= 0; index--) {
            String key = keys[index];
            reversed.put(key, components.get(key));
        }
        return reversed;
    }
}
