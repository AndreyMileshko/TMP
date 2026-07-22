package com.tmp.capability.sample;

/**
 * Trivial public service contract exposed by {@link SampleTechnicalCapability} through
 * Platform Core's {@code ServiceRegistry}. Used only as a technical fixture to prove
 * cross-capability resolution via public API (ADR-003), not as business functionality.
 */
public interface SampleTechnicalService {

    String marker();
}
