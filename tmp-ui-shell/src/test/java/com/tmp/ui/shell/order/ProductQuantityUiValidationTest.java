package com.tmp.ui.shell.order;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductQuantityUiValidationTest {

    @ParameterizedTest
    @ValueSource(strings = {"8", "8.0", "8.000000", "8.000", " 8.000000 "})
    void acceptsMathematicallyWholePositiveQuantities(String raw) {
        assertDoesNotThrow(() -> ProductQuantityUiValidation.requireValidProductQuantity(raw));
    }

    @Test
    void acceptsCommaDecimalWholeQuantity() {
        assertDoesNotThrow(() -> ProductQuantityUiValidation.requireValidProductQuantity("8,000000"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.5", "0.5", "-1.2", "0", "-1", "0.0", "-8.000000"})
    void rejectsNonWholeOrNonPositiveQuantities(String raw) {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProductQuantityUiValidation.requireValidProductQuantity(raw));
        assertEquals(
                "Количество изделий должно быть целым числом больше нуля.", ex.getMessage());
    }

    @Test
    void rejectsNullAndBlank() {
        IllegalArgumentException nullEx =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProductQuantityUiValidation.requireValidProductQuantity(null));
        assertEquals("Количество изделий обязательно для заполнения.", nullEx.getMessage());

        IllegalArgumentException blankEx =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProductQuantityUiValidation.requireValidProductQuantity("   "));
        assertEquals("Количество изделий обязательно для заполнения.", blankEx.getMessage());
    }

    @Test
    void rejectsNonNumeric() {
        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ProductQuantityUiValidation.requireValidProductQuantity("abc"));
        assertEquals("Количество изделий должно быть числом.", ex.getMessage());
    }

    @Test
    void isWholeNumberUsesFractionalPartNotScale() {
        assertTrue(ProductQuantityUiValidation.isWholeNumber(new BigDecimal("8")));
        assertTrue(ProductQuantityUiValidation.isWholeNumber(new BigDecimal("8.000000")));
        assertTrue(ProductQuantityUiValidation.isWholeNumber(new BigDecimal("8.0")));
        assertFalse(ProductQuantityUiValidation.isWholeNumber(new BigDecimal("8.5")));
        assertFalse(ProductQuantityUiValidation.isWholeNumber(new BigDecimal("0.5")));
    }
}
