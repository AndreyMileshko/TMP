package com.tmp.production.application;

import com.tmp.production.application.port.WarehouseAvailabilityQueryPort.MaterialReferenceEntry;
import com.tmp.production.domain.SpecificationMaterialIdentity;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Resolves a frozen Specification material identity to a Warehouse {@code MaterialReferenceId}.
 *
 * <p>Uses the accepted rule: {@code article/materialCode + normalized color + unitOfMeasure}.
 * {@code lengthMm} is never mapped to Warehouse size. Zero candidates → unresolved; more than one
 * → ambiguous (never pick first).
 */
public final class MaterialReferenceResolver {

    public enum ResolutionStatus {
        RESOLVED,
        UNRESOLVED,
        AMBIGUOUS
    }

    public record Result(UUID materialReferenceId, ResolutionStatus status) {

        public Result {
            if (status == ResolutionStatus.RESOLVED) {
                Objects.requireNonNull(materialReferenceId, "materialReferenceId");
            }
        }

        public static Result resolved(UUID materialReferenceId) {
            return new Result(materialReferenceId, ResolutionStatus.RESOLVED);
        }

        public static Result unresolved() {
            return new Result(null, ResolutionStatus.UNRESOLVED);
        }

        public static Result ambiguous() {
            return new Result(null, ResolutionStatus.AMBIGUOUS);
        }

        public boolean isResolved() {
            return status == ResolutionStatus.RESOLVED;
        }
    }

    public Result resolve(SpecificationMaterialIdentity identity, List<MaterialReferenceEntry> catalog) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(catalog, "catalog");
        List<MaterialReferenceEntry> candidates =
                catalog.stream()
                        .filter(
                                entry ->
                                        entry.article().equals(identity.materialCode())
                                                && SpecificationMaterialIdentity.normalizeColor(
                                                                entry.color())
                                                        .equals(identity.color())
                                                && entry.unitOfMeasure()
                                                        .trim()
                                                        .equals(identity.unitOfMeasure()))
                        .toList();

        if (candidates.isEmpty()) {
            return Result.unresolved();
        }
        if (candidates.size() > 1) {
            return Result.ambiguous();
        }
        return Result.resolved(candidates.getFirst().materialReferenceId());
    }
}
