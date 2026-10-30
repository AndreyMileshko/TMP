package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.production.ReleaseDialogSupport.Step2DialogSession;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused JavaFX checks for Production Release Step 2 article column and fact editing identity.
 */
class ReleaseStep2ArticleColumnFxTest {

    private static final UUID ORDER_A =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_B =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ITEM_A =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_B =
            UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb2");
    private static final UUID MAT_3012 =
            UUID.fromString("20efa49c-a309-41c2-92f5-04567cfcb334");
    private static final UUID MAT_3013 =
            UUID.fromString("7f0886ef-9eeb-409c-a62e-1ce639a20cf3");

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void step2ColumnsIncludeArticleBetweenOrderAndMaterial() throws Exception {
        AtomicReference<Step2DialogSession> sessionRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () ->
                        sessionRef.set(
                                ReleaseDialogSupport.createStep2Dialog(sampleRows(), true)));
        Step2DialogSession session = sessionRef.get();
        TableView<ReleaseMaterialRow> table = session.table();
        assertEquals(5, table.getColumns().size());
        List<String> headers =
                table.getColumns().stream().map(TableColumn::getText).toList();
        assertEquals(ReleaseDialogSupport.STEP2_COLUMN_HEADERS, headers);
        assertEquals(
                ReleaseDialogSupport.STEP2_DIALOG_PREF_WIDTH,
                session.dialog().getDialogPane().getPrefWidth(),
                0.1);
        assertTrue(
                session.dialog().getDialogPane().getMinWidth()
                        >= ReleaseDialogSupport.STEP2_DIALOG_MIN_WIDTH - 0.1);
    }

    @Test
    void step2RowsExposeAuthoritativeArticlesAndEditableFact() throws Exception {
        List<ReleaseMaterialRow> rows = sampleRows();
        AtomicReference<Step2DialogSession> sessionRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> sessionRef.set(ReleaseDialogSupport.createStep2Dialog(rows, true)));
        Step2DialogSession session = sessionRef.get();
        TableView<ReleaseMaterialRow> table = session.table();

        assertEquals("\u041C\u041F-3012", table.getItems().get(0).materialCode());
        assertEquals("\u041C\u041F-3013", table.getItems().get(1).materialCode());
        assertEquals(ORDER_A, table.getItems().get(0).sourceOrderId());
        assertEquals(ORDER_B, table.getItems().get(1).sourceOrderId());
        assertEquals(MAT_3012, table.getItems().get(0).materialReferenceId());
        assertEquals(MAT_3013, table.getItems().get(1).materialReferenceId());
        assertEquals("3.624", table.getItems().get(0).plannedQuantity());
        assertEquals("7.480", table.getItems().get(1).plannedQuantity());

        JavaFxTestSupport.runOnFxThread(
                () -> {
                    table.getItems().get(0).setActualQuantity("4.000");
                    table.getItems().get(1).setActualQuantity("8.000");
                });

        assertEquals("4.000", table.getItems().get(0).actualQuantity());
        assertEquals("8.000", table.getItems().get(1).actualQuantity());
        assertEquals("\u041C\u041F-3012", table.getItems().get(0).materialCode());
        assertEquals("\u041C\u041F-3013", table.getItems().get(1).materialCode());
        assertEquals(MAT_3012, table.getItems().get(0).materialReferenceId());
        assertEquals(MAT_3013, table.getItems().get(1).materialReferenceId());
        assertEquals("3.624", table.getItems().get(0).plannedQuantity());
        assertEquals("7.480", table.getItems().get(1).plannedQuantity());
        assertTrue(ReleaseDialogSupport.validatePlanFact(table.getItems()).isEmpty());
    }

    private static List<ReleaseMaterialRow> sampleRows() {
        return List.of(
                new ReleaseMaterialRow(
                        ORDER_A,
                        ITEM_A,
                        MAT_3012,
                        "26106082",
                        "\u041C\u041F-3012",
                        "Frame vent grille 24x21 mm",
                        "m.",
                        "3.624",
                        "3.624"),
                new ReleaseMaterialRow(
                        ORDER_B,
                        ITEM_B,
                        MAT_3013,
                        "26106082-B",
                        "\u041C\u041F-3013",
                        "Lamella grille 55x20.6 mm",
                        "m.",
                        "7.480",
                        "7.480"));
    }
}