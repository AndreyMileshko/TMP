package com.tmp.capability.sample;

/**
 * Default implementation of {@link SampleTechnicalService} registered as a public service
 * contribution by {@link SampleTechnicalCapability}.
 */
public final class SampleTechnicalServiceImpl implements SampleTechnicalService {

    @Override
    public String marker() {
        return "sample-technical-service";
    }
}
