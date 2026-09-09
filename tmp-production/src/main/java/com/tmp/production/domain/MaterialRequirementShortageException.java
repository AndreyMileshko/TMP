package com.tmp.production.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Raised when a Material Requirement cannot be submitted because at least one line has no source
 * warehouse with positive AVAILABLE stock (Stage 3.5.10 §9). Submit fails as a whole with zero side
 * effects (no generated documents, no tasks, no snapshot, requirement stays DRAFT).
 */
public final class MaterialRequirementShortageException extends RuntimeException {

    private final transient MaterialRequirementId requirementId;
    private final transient List<ShortageLine> shortages;

    public MaterialRequirementShortageException(
            MaterialRequirementId requirementId, List<ShortageLine> shortages) {
        super(buildMessage(requirementId, shortages));
        this.requirementId = Objects.requireNonNull(requirementId, "requirementId");
        this.shortages = List.copyOf(Objects.requireNonNull(shortages, "shortages"));
    }

    public MaterialRequirementId requirementId() {
        return requirementId;
    }

    public List<ShortageLine> shortages() {
        return shortages;
    }

    private static String buildMessage(
            MaterialRequirementId requirementId, List<ShortageLine> shortages) {
        Objects.requireNonNull(requirementId, "requirementId");
        Objects.requireNonNull(shortages, "shortages");
        String materials =
                shortages.stream()
                        .map(ShortageLine::displayName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("");
        return "Сейчас нет ни одного склада-источника с доступным остатком для: " + materials;
    }

    /** One requirement line (and its material) with no positive-AVAILABLE source. */
    public record ShortageLine(
            MaterialRequirementLineId lineId, UUID materialReferenceId, String materialCode) {
        public ShortageLine {
            Objects.requireNonNull(lineId, "lineId");
            Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            materialCode = materialCode == null ? "" : materialCode;
        }

        String displayName() {
            return materialCode.isBlank() ? materialReferenceId.toString() : materialCode;
        }
    }
}
