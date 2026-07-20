package com.tmp.core.api.component;

/**
 * Lifecycle state of a registered platform component.
 */
public enum ComponentLifecycleState {
    REGISTERED,
    INITIALIZING,
    STARTED,
    STOPPING,
    STOPPED,
    FAILED
}
