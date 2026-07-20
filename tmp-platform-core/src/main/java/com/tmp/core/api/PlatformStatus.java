package com.tmp.core.api;

import com.tmp.core.api.component.ComponentLifecycleState;

/**
 * Snapshot of platform runtime status for diagnostics and minimal UI visibility.
 */
public record PlatformStatus(
        String platformName,
        String platformVersion,
        ComponentLifecycleState lifecycleState,
        int registeredComponents,
        int registeredServices,
        int registeredCapabilities) {

    public String summary() {
        return platformName + " " + platformVersion + " | state=" + lifecycleState
                + " | components=" + registeredComponents
                + " | services=" + registeredServices
                + " | capabilities=" + registeredCapabilities;
    }
}
