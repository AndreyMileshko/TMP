package com.tmp.ui.shell.screen.orderimport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tmp.order.api.imports.OrderImportProblem;
import org.junit.jupiter.api.Test;

class OrderImportProblemRowTest {

    @Test
    void mapsKnownFieldToRussianAndBuildsWhere() {
        OrderImportProblem problem =
                OrderImportProblem.error(
                        "QTY", "loc", 3, 12, "quantity", "x", "Количество должно быть больше 0");
        OrderImportProblemRow row = OrderImportProblemRow.from(problem);
        assertEquals("● Ошибка", row.typeLabel());
        assertEquals("Позиция 3 · строка 12 · Количество", row.whereLabel());
    }

    @Test
    void unknownFieldFallsBackToOriginal() {
        OrderImportProblem problem =
                OrderImportProblem.warning(
                        "W", null, null, null, "customUnknownField", null, "warn");
        OrderImportProblemRow row = OrderImportProblemRow.from(problem);
        assertEquals("● Предупреждение", row.typeLabel());
        assertEquals("customUnknownField", row.whereLabel());
    }
}
