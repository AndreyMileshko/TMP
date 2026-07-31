package com.tmp.order.api.imports;

/**
 * Controlled duplicate-import conflict. Same {@code sourceType + contentChecksum} was already
 * imported.
 */
public final class OrderImportDuplicateException extends RuntimeException {

    public static final String USER_MESSAGE =
            "Этот источник уже был импортирован. Повторный импорт не выполнен.";

    public OrderImportDuplicateException() {
        super(USER_MESSAGE);
    }
}
