package com.tmp.production.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Whole-order Production Cancellation command (Production Spec §16 / §18.2). */
public record CancelOrderProductionCommand(UUID sourceOrderId, Optional<String> reason) {

    public CancelOrderProductionCommand {
        Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        Objects.requireNonNull(reason, "reason");
        reason = reason.map(String::trim).filter(value -> !value.isEmpty());
    }

    public CancelOrderProductionCommand(UUID sourceOrderId) {
        this(sourceOrderId, Optional.empty());
    }
}
