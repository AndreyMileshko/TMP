package com.tmp.production.domain;

import java.util.List;
import java.util.Objects;

/**
 * Raised when a Material Transfer Template cannot be prepared because material identity is not
 * uniquely resolved for one or more requirement lines.
 */
public final class MaterialTransferTemplateNotReadyException extends RuntimeException {

    private final SourceOrderId sourceOrderId;
    private final List<MaterialAvailabilityLine> blockingLines;

    public MaterialTransferTemplateNotReadyException(
            SourceOrderId sourceOrderId, List<MaterialAvailabilityLine> blockingLines) {
        super(
                "Material transfer template is not ready: unresolved or ambiguous materials for order "
                        + sourceOrderId
                        + ", blockingLines="
                        + blockingLines.size());
        this.sourceOrderId = Objects.requireNonNull(sourceOrderId, "sourceOrderId");
        this.blockingLines = List.copyOf(Objects.requireNonNull(blockingLines, "blockingLines"));
        if (this.blockingLines.isEmpty()) {
            throw new IllegalArgumentException("blockingLines must not be empty");
        }
    }

    public SourceOrderId sourceOrderId() {
        return sourceOrderId;
    }

    public List<MaterialAvailabilityLine> blockingLines() {
        return blockingLines;
    }
}
