package com.tmp.production.domain;

/** Aggregated outcome of a material availability check for the whole order. */
public enum MaterialAvailabilityOverallStatus {
    ALL_AVAILABLE,
    HAS_DEFICIT,
    HAS_UNRESOLVED_MATERIALS
}
