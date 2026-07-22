package com.tmp.capability.api;

/**
 * The fixed set of lifecycle states a Capability moves through, from discovery to
 * deactivation, as tracked by the Capability Engine. {@code FAILED} is a terminal state
 * reachable from any non-terminal state when an unrecoverable error occurs.
 */
public enum CapabilityLifecycleState {
    DISCOVERED,
    VALIDATED,
    REGISTERED,
    INITIALIZED,
    ACTIVE,
    STOPPED,
    DEACTIVATED,
    FAILED
}
