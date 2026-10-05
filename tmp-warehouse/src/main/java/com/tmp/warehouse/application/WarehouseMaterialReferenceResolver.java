package com.tmp.warehouse.application;

import com.tmp.warehouse.domain.MaterialReference;
import com.tmp.warehouse.domain.UnitOfMeasure;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Warehouse-owned operational MaterialReference resolution from immutable Demand snapshot identity.
 *
 * <p>Matching key: article/materialCode + normalized color + canonical UoM. {@code lengthMm} is
 * never part of the matching key. Zero → unmatched; more than one → ambiguous (never pick first).
 */
public final class WarehouseMaterialReferenceResolver {

    public enum ResolutionStatus {
        RESOLVED,
        UNMATCHED,
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

        public static Result unmatched() {
            return new Result(null, ResolutionStatus.UNMATCHED);
        }

        public static Result ambiguous() {
            return new Result(null, ResolutionStatus.AMBIGUOUS);
        }
    }

    public Result resolve(
            String materialCode, String color, String unitOfMeasure, List<MaterialReference> catalog) {
        Objects.requireNonNull(materialCode, "materialCode");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        Objects.requireNonNull(catalog, "catalog");
        String articleKey = materialCode.trim();
        String colorKey = color == null ? "" : color.trim();
        List<MaterialReference> candidates =
                catalog.stream()
                        .filter(
                                material ->
                                        material.article().equals(articleKey)
                                                && normalizeColor(material.color()).equals(colorKey)
                                                && UnitOfMeasure.equalForKey(
                                                        material.unitOfMeasure(), unitOfMeasure))
                        .toList();
        if (candidates.isEmpty()) {
            return Result.unmatched();
        }
        if (candidates.size() > 1) {
            return Result.ambiguous();
        }
        return Result.resolved(candidates.getFirst().id().value());
    }

    private static String normalizeColor(String color) {
        return color == null ? "" : color.trim();
    }
}
