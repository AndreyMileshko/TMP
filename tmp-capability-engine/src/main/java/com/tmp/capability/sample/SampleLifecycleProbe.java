package com.tmp.capability.sample;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Package-private probe used by sample capabilities and their integration test to observe
 * activation order and cross-capability service resolution without coupling production
 * wiring to test utilities.
 */
final class SampleLifecycleProbe {

    private static final List<String> ACTIVATION_ORDER = new CopyOnWriteArrayList<>();
    private static volatile SampleTechnicalService resolvedService;

    private SampleLifecycleProbe() {
    }

    static void recordActivation(String capabilityId) {
        ACTIVATION_ORDER.add(capabilityId);
    }

    static void recordResolvedService(SampleTechnicalService service) {
        resolvedService = service;
    }

    static List<String> activationOrder() {
        return List.copyOf(ACTIVATION_ORDER);
    }

    static SampleTechnicalService resolvedService() {
        return resolvedService;
    }

    static void reset() {
        ACTIVATION_ORDER.clear();
        resolvedService = null;
    }
}
