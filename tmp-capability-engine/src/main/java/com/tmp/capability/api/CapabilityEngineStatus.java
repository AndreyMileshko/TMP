package com.tmp.capability.api;

/**
 * Minimal technical status snapshot of the Capability Engine for UI visibility and smoke
 * checks. Counts only — access decisions are never made from this type.
 */
public record CapabilityEngineStatus(
        int discoveredCount, int registeredCount, int activeCount, int failedCount) {
}
