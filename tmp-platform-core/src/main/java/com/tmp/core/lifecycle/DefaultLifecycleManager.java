package com.tmp.core.lifecycle;

import com.tmp.core.api.LifecycleManager;
import com.tmp.core.api.PlatformCore;
import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.PlatformComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultLifecycleManager implements LifecycleManager {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLifecycleManager.class);

    private final Map<String, PlatformComponent> components = new LinkedHashMap<>();
    private final Map<String, ComponentLifecycleState> states = new LinkedHashMap<>();
    private PlatformCore platformCore;
    private ComponentLifecycleState platformState = ComponentLifecycleState.REGISTERED;

    public synchronized void attachPlatformCore(PlatformCore platformCore) {
        this.platformCore = platformCore;
    }

    public void registerInternal(PlatformComponent component) {
        String componentId = component.metadata().id();
        if (components.containsKey(componentId)) {
            throw new IllegalStateException("Component already registered for lifecycle: " + componentId);
        }
        components.put(componentId, component);
        states.put(componentId, ComponentLifecycleState.REGISTERED);
    }

    public void unregisterInternal(String componentId) {
        components.remove(componentId);
        states.remove(componentId);
    }

    public boolean isRegistered(String componentId) {
        return components.containsKey(componentId);
    }

    @Override
    public synchronized void startAll() {
        if (platformCore == null) {
            throw new IllegalStateException("PlatformCore is not attached to lifecycle manager");
        }
        if (platformState != ComponentLifecycleState.REGISTERED
                && platformState != ComponentLifecycleState.STOPPED) {
            throw new IllegalStateException("startAll() is not allowed in platform state: " + platformState);
        }

        platformState = ComponentLifecycleState.INITIALIZING;
        List<String> startedComponentIds = new ArrayList<>();

        try {
            for (PlatformComponent component : components.values()) {
                String componentId = component.metadata().id();
                states.put(componentId, ComponentLifecycleState.INITIALIZING);
                try {
                    component.initialize(platformCore);
                    component.start();
                    states.put(componentId, ComponentLifecycleState.STARTED);
                    startedComponentIds.add(componentId);
                } catch (RuntimeException startupFailure) {
                    states.put(componentId, ComponentLifecycleState.FAILED);
                    platformState = ComponentLifecycleState.FAILED;
                    rollbackStartedComponents(startedComponentIds, startupFailure);
                    throw startupFailure;
                }
            }
            platformState = ComponentLifecycleState.STARTED;
        } catch (RuntimeException ex) {
            if (platformState != ComponentLifecycleState.FAILED) {
                platformState = ComponentLifecycleState.FAILED;
            }
            throw ex;
        }
    }

    @Override
    public synchronized void stopAll() {
        if (platformState != ComponentLifecycleState.STARTED
                && platformState != ComponentLifecycleState.FAILED) {
            throw new IllegalStateException("stopAll() is not allowed in platform state: " + platformState);
        }

        platformState = ComponentLifecycleState.STOPPING;
        boolean stopFailure = false;
        RuntimeException firstStopFailure = null;

        for (Map.Entry<String, PlatformComponent> entry : reverseEntries().entrySet()) {
            String componentId = entry.getKey();
            ComponentLifecycleState currentState = states.getOrDefault(componentId, ComponentLifecycleState.REGISTERED);
            if (currentState != ComponentLifecycleState.STARTED
                    && currentState != ComponentLifecycleState.FAILED) {
                continue;
            }

            PlatformComponent component = entry.getValue();
            states.put(componentId, ComponentLifecycleState.STOPPING);
            try {
                component.stop();
                states.put(componentId, ComponentLifecycleState.STOPPED);
            } catch (RuntimeException stopFailureException) {
                states.put(componentId, ComponentLifecycleState.FAILED);
                stopFailure = true;
                if (firstStopFailure == null) {
                    firstStopFailure = stopFailureException;
                } else {
                    firstStopFailure.addSuppressed(stopFailureException);
                }
                LOG.warn("Failed to stop component {}", componentId, stopFailureException);
            }
        }

        if (stopFailure) {
            platformState = ComponentLifecycleState.FAILED;
            throw firstStopFailure;
        }
        platformState = ComponentLifecycleState.STOPPED;
    }

    @Override
    public ComponentLifecycleState stateOf(String componentId) {
        return states.getOrDefault(componentId, ComponentLifecycleState.REGISTERED);
    }

    @Override
    public ComponentLifecycleState platformState() {
        return platformState;
    }

    @Override
    public Map<String, ComponentLifecycleState> allStates() {
        return Map.copyOf(states);
    }

    private void rollbackStartedComponents(List<String> startedComponentIds, RuntimeException originalFailure) {
        for (int index = startedComponentIds.size() - 1; index >= 0; index--) {
            String componentId = startedComponentIds.get(index);
            PlatformComponent component = components.get(componentId);
            if (component == null) {
                continue;
            }
            try {
                states.put(componentId, ComponentLifecycleState.STOPPING);
                component.stop();
                states.put(componentId, ComponentLifecycleState.STOPPED);
            } catch (RuntimeException rollbackFailure) {
                states.put(componentId, ComponentLifecycleState.FAILED);
                originalFailure.addSuppressed(rollbackFailure);
                LOG.warn("Rollback stop failed for component {}", componentId, rollbackFailure);
            }
        }
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
