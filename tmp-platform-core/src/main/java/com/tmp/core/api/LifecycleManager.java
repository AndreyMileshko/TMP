package com.tmp.core.api;

import com.tmp.core.api.component.ComponentLifecycleState;
import com.tmp.core.api.component.PlatformComponent;
import java.util.Map;

/**
 * Manages initialization, startup and shutdown of registered platform components.
 */
public interface LifecycleManager {

    void startAll();

    void stopAll();

    ComponentLifecycleState stateOf(String componentId);

    ComponentLifecycleState platformState();

    Map<String, ComponentLifecycleState> allStates();
}
