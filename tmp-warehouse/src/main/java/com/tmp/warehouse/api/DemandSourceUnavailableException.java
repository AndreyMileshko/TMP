package com.tmp.warehouse.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Signals that at least one demand line has no source warehouse with positive AVAILABLE stock
 * (Stage 3.5.10 / ADR-037 §D). Raised by {@link WarehouseDemandCommandApi} before any Transfer
 * Document is created, so the caller can roll back the whole operation with zero side effects.
 *
 * <p>A source with positive AVAILABLE below the requested quantity does NOT trigger this exception;
 * such lines are routed to that source and the physical shortfall is handled later by Stage 3.5.7.
 */
public final class DemandSourceUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<UnavailableDemand> unavailableDemands;

    public DemandSourceUnavailableException(List<UnavailableDemand> unavailableDemands) {
        super(buildMessage(unavailableDemands));
        this.unavailableDemands = List.copyOf(Objects.requireNonNull(unavailableDemands, "unavailableDemands"));
    }

    public List<UnavailableDemand> unavailableDemands() {
        return unavailableDemands;
    }

    private static String buildMessage(List<UnavailableDemand> unavailableDemands) {
        return "No available source warehouse for demand line(s): "
                + Objects.requireNonNull(unavailableDemands, "unavailableDemands");
    }

    /** One demand line that could not be routed to any positive-AVAILABLE source. */
    public record UnavailableDemand(String demandKey, UUID materialReferenceId) {
        public UnavailableDemand {
            Objects.requireNonNull(demandKey, "demandKey");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
        }
    }
}
