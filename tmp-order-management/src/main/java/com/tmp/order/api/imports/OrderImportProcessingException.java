package com.tmp.order.api.imports;

/**
 * Unexpected import processing failure with a safe Russian user message (no SQL leakage).
 */
public final class OrderImportProcessingException extends RuntimeException {

    public static final String USER_MESSAGE =
            "Не удалось выполнить импорт заказа. Повторите попытку или обратитесь к администратору.";

    public OrderImportProcessingException() {
        super(USER_MESSAGE);
    }

    public OrderImportProcessingException(Throwable cause) {
        super(USER_MESSAGE, cause);
    }
}
