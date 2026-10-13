package com.tmp.production.application;

import com.tmp.warehouse.api.UnitOfMeasureKeys;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Shared conversion from frozen Specification line fields to preliminary warehouse demand.
 *
 * <p>Rules (canonical UoM via {@link UnitOfMeasureKeys#equalForKey}):
 *
 * <ul>
 *   <li><b>A</b> — {@code unitOfMeasure} is meters and {@code lengthMm} present: {@code
 *       lineQuantity} is cut-piece count per product; warehouse demand meters = {@code
 *       lineQuantity × lengthMm × productQuantity / 1000}.
 *   <li><b>B</b> — meters without {@code lengthMm}: {@code lineQuantity} is already meters per
 *       product; demand = {@code lineQuantity × productQuantity}.
 *   <li><b>C</b> — any other UoM: unchanged; demand = {@code lineQuantity × productQuantity}.
 * </ul>
 *
 * <p>Does not mutate imported specification fields. Material names/categories are never used.
 */
public final class SpecificationWarehouseQuantityCalculator {

    public static final int SCALE = PartialReleaseMaterialPlanCalculator.SCALE;
    public static final RoundingMode ROUNDING = PartialReleaseMaterialPlanCalculator.ROUNDING;

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final String METERS = "м.";

    private SpecificationWarehouseQuantityCalculator() {}

    /** True when UoM compares equal to canonical warehouse meters. */
    public static boolean isMeters(String unitOfMeasure) {
        return UnitOfMeasureKeys.equalForKey(unitOfMeasure, METERS);
    }

    /**
     * True when Rule A applies: meters UoM with a present positive {@code lengthMm}.
     */
    public static boolean isDimensionalMeter(String unitOfMeasure, BigDecimal lengthMm) {
        return isMeters(unitOfMeasure) && lengthMm != null && lengthMm.signum() > 0;
    }

    /**
     * Warehouse quantity for <strong>one product</strong> from one specification line.
     *
     * <p>Rule A returns meters; Rules B/C return {@code lineQuantity} in the line's warehouse UoM.
     */
    public static BigDecimal warehouseQuantityPerProduct(
            BigDecimal lineQuantity, BigDecimal lengthMm, String unitOfMeasure) {
        Objects.requireNonNull(lineQuantity, "lineQuantity");
        Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
        if (lineQuantity.signum() <= 0) {
            throw new IllegalArgumentException("lineQuantity must be > 0: " + lineQuantity);
        }
        if (isDimensionalMeter(unitOfMeasure, lengthMm)) {
            return normalize(lineQuantity.multiply(lengthMm).divide(THOUSAND, SCALE, ROUNDING));
        }
        return normalize(lineQuantity);
    }

    /**
     * Preliminary warehouse demand for {@code productQuantity} products from one specification
     * line.
     */
    public static BigDecimal warehouseDemand(
            BigDecimal lineQuantity,
            BigDecimal lengthMm,
            String unitOfMeasure,
            long productQuantity) {
        if (productQuantity <= 0L) {
            throw new IllegalArgumentException("productQuantity must be > 0: " + productQuantity);
        }
        return normalize(
                warehouseQuantityPerProduct(lineQuantity, lengthMm, unitOfMeasure)
                        .multiply(BigDecimal.valueOf(productQuantity)));
    }

    /**
     * Technological piece count for Rule A rows with unambiguous length: {@code requiredMeters ×
     * 1000 / lengthMm}. Empty when not applicable.
     */
    public static BigDecimal technologicalPieceCount(
            BigDecimal requiredWarehouseQuantity, BigDecimal lengthMm, String unitOfMeasure) {
        Objects.requireNonNull(requiredWarehouseQuantity, "requiredWarehouseQuantity");
        if (!isDimensionalMeter(unitOfMeasure, lengthMm)) {
            return null;
        }
        return requiredWarehouseQuantity
                .multiply(THOUSAND)
                .divide(lengthMm, 0, RoundingMode.HALF_UP);
    }

    public static BigDecimal normalize(BigDecimal value) {
        Objects.requireNonNull(value, "value");
        return value.setScale(SCALE, ROUNDING);
    }
}
