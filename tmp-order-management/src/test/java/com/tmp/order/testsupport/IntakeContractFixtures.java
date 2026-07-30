package com.tmp.order.testsupport;

import com.tmp.order.testsupport.IntakeContractFixtures;

import com.tmp.order.application.payload.OrderItemRevisionPayloadLine;
import com.tmp.order.domain.SpecificationLine;
import java.math.BigDecimal;

/** Shared factories for Order Intake contract tests (STAGE5-051). */
public final class IntakeContractFixtures {

    private IntakeContractFixtures() {}

    public static SpecificationLine specLine(
            String materialCode, String materialName, BigDecimal lineQuantity, String unitOfMeasure) {
        return SpecificationLine.of(
                materialCode, materialName, null, null, lineQuantity, unitOfMeasure);
    }

    public static SpecificationLine specLine(
            String materialCode,
            String materialName,
            String color,
            BigDecimal lengthMm,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        return SpecificationLine.of(
                materialCode, materialName, color, lengthMm, lineQuantity, unitOfMeasure);
    }

    public static OrderItemRevisionPayloadLine payloadLine(
            int lineNumber,
            String materialCode,
            String materialName,
            BigDecimal lineQuantity,
            String unitOfMeasure) {
        return OrderItemRevisionPayloadLine.of(
                lineNumber, materialCode, materialName, null, null, lineQuantity, unitOfMeasure);
    }
}
