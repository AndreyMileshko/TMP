package com.tmp.ui.shell.screen.production;

import com.tmp.production.api.ProductionApplicationApi.MaterialReadinessLineView;
import java.math.BigDecimal;
import java.util.Objects;

/** Read-only row for Order Card material readiness details. Never exposes UUID. */
public final class MaterialReadinessLineRow {

    private final String materialCode;
    private final String materialName;
    private final String color;
    private final String requiredQuantity;
    private final String availableQuantity;
    private final String shortageQuantity;
    private final String unitOfMeasure;

    public MaterialReadinessLineRow(
            String materialCode,
            String materialName,
            String color,
            String requiredQuantity,
            String availableQuantity,
            String shortageQuantity,
            String unitOfMeasure) {
        this.materialCode = Objects.requireNonNull(materialCode, "materialCode");
        this.materialName = Objects.requireNonNull(materialName, "materialName");
        this.color = Objects.requireNonNull(color, "color");
        this.requiredQuantity = Objects.requireNonNull(requiredQuantity, "requiredQuantity");
        this.availableQuantity = Objects.requireNonNull(availableQuantity, "availableQuantity");
        this.shortageQuantity = Objects.requireNonNull(shortageQuantity, "shortageQuantity");
        this.unitOfMeasure = Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    }

    public static MaterialReadinessLineRow from(MaterialReadinessLineView line) {
        Objects.requireNonNull(line, "line");
        return new MaterialReadinessLineRow(
                line.materialCode(),
                line.materialName(),
                line.color(),
                format(line.requiredQuantity()),
                format(line.availableQuantity()),
                format(line.shortageQuantity()),
                line.unitOfMeasure());
    }

    public String materialCode() {
        return materialCode;
    }

    public String materialName() {
        return materialName;
    }

    public String color() {
        return color;
    }

    public String requiredQuantity() {
        return requiredQuantity;
    }

    public String availableQuantity() {
        return availableQuantity;
    }

    public String shortageQuantity() {
        return shortageQuantity;
    }

    public String unitOfMeasure() {
        return unitOfMeasure;
    }

    private static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
