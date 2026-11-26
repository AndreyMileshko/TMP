package com.tmp.order.application.imports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.order.api.imports.SpecificationColorConstraint.RejectionReason;
import com.tmp.order.api.imports.SpecificationColorConstraint.Result;
import com.tmp.order.domain.SpecificationLine;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DefaultSpecificationColorConstraintTest {

    private SpecificationColorConstraint constraint;

    @BeforeEach
    void setUp() {
        constraint = new DefaultSpecificationColorConstraint();
    }

    @Test
    void maxColorLengthMatchesSchemaVarchar64() {
        assertEquals(64, SpecificationColorConstraint.MAX_COLOR_LENGTH);
    }

    @Test
    void sixtyFourCharactersAreAllowedAndAcceptedBySpecificationLine() {
        String color64 = "a".repeat(64);
        Result result = constraint.check(color64);
        assertTrue(result.isAllowed());
        assertNull(result.rejectionReason());

        SpecificationLine line =
                SpecificationLine.of("ART-1", "Profile", color64, null, BigDecimal.ONE, "sht");
        assertEquals(color64, line.color());
    }

    @Test
    void sixtyFiveCharactersAreRejectedAsTooLong() {
        String color65 = "b".repeat(65);
        Result result = constraint.check(color65);
        assertFalse(result.isAllowed());
        assertEquals(RejectionReason.TOO_LONG, result.rejectionReason());
    }

    @ParameterizedTest
    @ValueSource(strings = {"N/A", "n/a", "-", "\u2014", "UNKNOWN", "unknown", "NA", "NONE", "TBD", "IMPORT"})
    void forbiddenPlaceholdersAreRejectedCaseInsensitively(String placeholder) {
        Result result = constraint.check(placeholder);
        assertFalse(result.isAllowed());
        assertEquals(RejectionReason.PLACEHOLDER, result.rejectionReason());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", " \n "})
    void blankAndWhitespaceAreRejected(String value) {
        Result result = constraint.check(value);
        assertFalse(result.isAllowed());
        assertEquals(RejectionReason.BLANK, result.rejectionReason());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "RAL7016/30\u0442",
                "\u0411\u0435\u043b\u044b\u0439/RAL7016|30",
                "\u0411\u0435\u0437 \u0446\u0432\u0435\u0442\u0430",
                " RAL7016 ",
                "\u0431\u0435\u043b\u044b\u0439"
            })
    void validFinalColorsAreAllowed(String color) {
        Result result = constraint.check(color);
        assertTrue(result.isAllowed());
        assertNull(result.rejectionReason());
    }

    @Test
    void trimsBeforeLengthCheckSoPaddedSixtyFourCoreIsAllowed() {
        String core64 = "c".repeat(64);
        Result result = constraint.check("  " + core64 + "  ");
        assertTrue(result.isAllowed());
    }

    @Test
    void trimsBeforeLengthCheckSoPaddedSixtyFiveCoreIsTooLong() {
        String core65 = "d".repeat(65);
        Result result = constraint.check("  " + core65 + "  ");
        assertFalse(result.isAllowed());
        assertEquals(RejectionReason.TOO_LONG, result.rejectionReason());
    }
}