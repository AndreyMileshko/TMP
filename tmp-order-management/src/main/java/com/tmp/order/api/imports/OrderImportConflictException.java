package com.tmp.order.api.imports;

/**
 * Controlled conflict when an order with the same number already exists. Merge is forbidden.
 */
public final class OrderImportConflictException extends RuntimeException {

    public static final String USER_MESSAGE =
            "Заказ с таким номером уже существует. Импорт не выполнен.";

    public OrderImportConflictException() {
        super(USER_MESSAGE);
    }
}
