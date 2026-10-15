package com.tmp.ui.shell.screen.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.ui.shell.screen.warehouse.WarehouseWorkspaceViewModel.TaskRow;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskKind;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskSource;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskState;
import com.tmp.warehouse.api.WarehouseApi.WarehouseTaskView;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** B3-MA-05 return route presentation + Warehouse Tasks visual style mapping. */
class WarehouseTaskPresentationTest {

    private static final UUID SRC = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID DST = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID DOC = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void returnTaskShowsInTransitSourceAndOriginalSourceDestination() {
        WarehouseTaskView view =
                transferTask(
                        WarehouseTaskKind.RETURN_MATERIALS,
                        WarehouseTaskState.NEW,
                        "SRC",
                        "Source",
                        "DST",
                        "Dest");
        TaskRow row = new TaskRow(view, null);

        assertEquals(WarehouseTaskVisualStyles.SYSTEM_IN_TRANSIT, row.sourceWarehouseLabel());
        assertEquals("SRC — Source", row.destinationWarehouseLabel());
        assertEquals("Возврат на: SRC — Source", row.routeLabel());
        assertEquals("Возврат материалов", row.kindLabel());
        assertTrue(row.sourceSystemLocation());
        assertFalse(row.destinationSystemLocation());
        assertNull(row.badgeSourceWarehouseId());
        assertEquals(SRC, row.badgeDestinationWarehouseId());

        // DTO routing unchanged — presentation must not swap domain fields.
        assertEquals(SRC, row.dtoSourceWarehouseId());
        assertEquals(DST, row.dtoDestinationWarehouseId());
        assertEquals(SRC, view.sourceWarehouseId());
        assertEquals(DST, view.destinationWarehouseId());
    }

    @Test
    void preparationRouteUnchanged() {
        WarehouseTaskView view =
                transferTask(
                        WarehouseTaskKind.TRANSFER_PREPARATION,
                        WarehouseTaskState.NEW,
                        "SRC",
                        "Source",
                        "DST",
                        "Dest");
        TaskRow row = new TaskRow(view, null);

        assertEquals("SRC — Source", row.sourceWarehouseLabel());
        assertEquals("DST — Dest", row.destinationWarehouseLabel());
        assertEquals("SRC — Source → DST — Dest", row.routeLabel());
        assertEquals(SRC, row.dtoSourceWarehouseId());
        assertEquals(DST, row.dtoDestinationWarehouseId());
        assertEquals(SRC, row.badgeSourceWarehouseId());
        assertEquals(DST, row.badgeDestinationWarehouseId());
    }

    @Test
    void taskKindRowStylesMapKnownKindsAndUnknownStaysNeutral() {
        assertEquals(
                WarehouseTaskVisualStyles.ROW_SUPPLY,
                WarehouseTaskVisualStyles.rowStyleClass(
                        WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY));
        assertEquals(
                WarehouseTaskVisualStyles.ROW_RETURN,
                WarehouseTaskVisualStyles.rowStyleClass(WarehouseTaskKind.RETURN_MATERIALS));
        assertEquals(
                WarehouseTaskVisualStyles.ROW_RECEIPT,
                WarehouseTaskVisualStyles.rowStyleClass(WarehouseTaskKind.TRANSFER_RECEIPT));
        assertEquals(
                WarehouseTaskVisualStyles.ROW_TRANSFER,
                WarehouseTaskVisualStyles.rowStyleClass(WarehouseTaskKind.TRANSFER_PREPARATION));
        assertEquals(
                WarehouseTaskVisualStyles.ROW_NEUTRAL, WarehouseTaskVisualStyles.rowStyleClass(null));
    }

    @Test
    void kindDotsMatchTaskKindStripesAndStayDistinctFromStatusDots() {
        assertEquals(
                WarehouseTaskVisualStyles.KIND_DOT_SUPPLY,
                WarehouseTaskVisualStyles.kindDotStyleClass(
                        WarehouseTaskKind.PRODUCTION_MATERIAL_SUPPLY));
        assertEquals(
                WarehouseTaskVisualStyles.KIND_DOT_RETURN,
                WarehouseTaskVisualStyles.kindDotStyleClass(WarehouseTaskKind.RETURN_MATERIALS));
        assertEquals(
                WarehouseTaskVisualStyles.KIND_DOT_RECEIPT,
                WarehouseTaskVisualStyles.kindDotStyleClass(WarehouseTaskKind.TRANSFER_RECEIPT));
        assertEquals(
                WarehouseTaskVisualStyles.KIND_DOT_TRANSFER,
                WarehouseTaskVisualStyles.kindDotStyleClass(WarehouseTaskKind.TRANSFER_PREPARATION));
        assertEquals(
                WarehouseTaskVisualStyles.KIND_DOT_NEUTRAL,
                WarehouseTaskVisualStyles.kindDotStyleClass(null));
        assertFalse(
                WarehouseTaskVisualStyles.ALL_KIND_DOT_STYLE_CLASSES.contains(
                        WarehouseTaskVisualStyles.STATUS_NEW));
        assertEquals("→", WarehouseTaskVisualStyles.ROUTE_ARROW_TEXT);
    }

    @Test
    void returnAndUndefinedSourcePreserveDocumentNumberOnTaskRow() {
        WarehouseTaskView ret =
                transferTask(
                        WarehouseTaskKind.RETURN_MATERIALS,
                        WarehouseTaskState.NEW,
                        "SRC",
                        "Source",
                        "DST",
                        "Dest");
        TaskRow row = new TaskRow(ret, null);
        assertEquals("TR-1", row.documentNumber());
        assertEquals(WarehouseTaskVisualStyles.SYSTEM_IN_TRANSIT, row.sourceWarehouseLabel());
        assertEquals("SRC — Source", row.destinationWarehouseLabel());
    }

    @Test
    void statusIndicatorsMapNewAndInWork() {
        assertEquals(
                WarehouseTaskVisualStyles.STATUS_NEW,
                WarehouseTaskVisualStyles.statusDotStyleClass(WarehouseTaskState.NEW));
        assertEquals(
                WarehouseTaskVisualStyles.STATUS_IN_WORK,
                WarehouseTaskVisualStyles.statusDotStyleClass(WarehouseTaskState.IN_WORK));
        assertEquals(
                WarehouseTaskVisualStyles.STATUS_NEUTRAL,
                WarehouseTaskVisualStyles.statusDotStyleClass(null));
    }

    @Test
    void warehouseBadgesFollowAuthoritativeProductionRole() {
        Set<UUID> production = Set.of(DST);
        assertEquals(
                WarehouseTaskVisualStyles.BADGE_SYSTEM,
                WarehouseTaskVisualStyles.warehouseBadgeStyleClass(null, true, production));
        assertEquals(
                WarehouseTaskVisualStyles.BADGE_PRODUCTION,
                WarehouseTaskVisualStyles.warehouseBadgeStyleClass(DST, false, production));
        assertEquals(
                WarehouseTaskVisualStyles.BADGE_STANDARD,
                WarehouseTaskVisualStyles.warehouseBadgeStyleClass(SRC, false, production));
        assertEquals(
                WarehouseTaskVisualStyles.BADGE_STANDARD,
                WarehouseTaskVisualStyles.warehouseBadgeStyleClass(SRC, false, Set.of()));
    }

    @Test
    void systemLocationLabelsDetectedWithoutNameGuessing() {
        assertTrue(WarehouseTaskVisualStyles.isSystemLocationLabel("В пути"));
        assertTrue(WarehouseTaskVisualStyles.isSystemLocationLabel("—"));
        assertTrue(WarehouseTaskVisualStyles.isSystemLocationLabel("Поставщик"));
        assertFalse(WarehouseTaskVisualStyles.isSystemLocationLabel("SRC — Source"));
        assertFalse(WarehouseTaskVisualStyles.isSystemLocationLabel("2000 — Production"));
    }

    private static WarehouseTaskView transferTask(
            WarehouseTaskKind kind,
            WarehouseTaskState state,
            String sourceCode,
            String sourceName,
            String destCode,
            String destName) {
        return new WarehouseTaskView(
                WarehouseTaskSource.TRANSFER_DOCUMENT,
                DOC,
                null,
                "TR-1",
                "ORD-1",
                kind,
                state,
                SRC,
                sourceCode,
                sourceName,
                DST,
                destCode,
                destName,
                1,
                null,
                null,
                Instant.EPOCH,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}