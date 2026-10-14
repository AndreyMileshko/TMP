package com.tmp.ui.shell.screen.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.production.api.ProductionApplicationApi.QuantityModeView;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.screen.production.ReleaseConfirmPresentation.ConfirmDialogSession;
import com.tmp.ui.shell.screen.production.ReleaseConfirmPresentation.ConfirmMaterialAggregate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Focused JavaFX UI tests for Release confirmation expand/collapse auto-resize (B3-MA-09-FIX1).
 */
class ReleaseConfirmDialogSizingFxTest {

    private static final UUID ORDER_A =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_B =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ITEM_1 =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1");
    private static final UUID ITEM_2 =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa2");
    private static final UUID MAT_1 =
            UUID.fromString("77777777-7777-4777-8777-777777777771");
    private static final UUID MAT_2 =
            UUID.fromString("88888888-8888-4888-8888-888888888888");

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void clampToScreenVisualHeightRespectsFraction() {
        double visual = 1000;
        double max =
                visual * ReleaseConfirmPresentation.CONFIRM_DIALOG_MAX_SCREEN_HEIGHT_FRACTION;
        assertEquals(max, ReleaseConfirmPresentation.clampToScreenVisualHeight(2000, visual));
        assertEquals(400, ReleaseConfirmPresentation.clampToScreenVisualHeight(400, visual));
        assertEquals(0, ReleaseConfirmPresentation.clampToScreenVisualHeight(0, visual));
    }

    @Test
    void collapsedDialogUsesCompactSizeAndKeepsActionButtons() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(sampleRows());
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    assertFalse(session.materialsExpanded());
                    assertFalse(session.materialsScroll().isVisible());
                    assertFalse(session.searchField().isVisible());
                    assertTrue(session.toggleButton().getText().contains("Показать"));

                    double height = window(session).getHeight();
                    assertTrue(height > 0, "collapsed dialog must have positive height");
                    assertTrue(
                            height
                                    < ReleaseConfirmPresentation.MATERIALS_VIEWPORT_MAX_HEIGHT
                                            + 220,
                            "collapsed height should stay compact, was " + height);

                    assertTrue(lookupReleaseButton(session).isVisible());
                    assertTrue(lookupCancelButton(session).isVisible());
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    @Test
    void expandingMaterialsIncreasesUsableDetailAreaAndKeepsButtonsVisible() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(sampleRows());
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    double collapsed = window(session).getHeight();
                    session.expandMaterials();
                    forceLayout(session);

                    assertTrue(session.materialsExpanded());
                    assertTrue(session.materialsScroll().isVisible());
                    assertTrue(session.searchField().isVisible());
                    assertEquals(
                            ReleaseConfirmPresentation.HIDE_MATERIALS,
                            session.toggleButton().getText());

                    double expanded = window(session).getHeight();
                    assertTrue(
                            expanded > collapsed + 80,
                            "expanded height " + expanded + " should exceed collapsed " + collapsed);
                    assertTrue(session.materialsScroll().getHeight() > 0);
                    assertTrue(
                            session.materialsScroll().getMaxHeight()
                                    <= ReleaseConfirmPresentation.MATERIALS_VIEWPORT_MAX_HEIGHT
                                            + 0.1);

                    Button release = lookupReleaseButton(session);
                    Button cancel = lookupCancelButton(session);
                    assertTrue(release.isVisible());
                    assertTrue(cancel.isVisible());
                    // Action buttons stay in the dialog button bar below the expandable content.
                    double releaseY = release.localToScene(0, 0).getY();
                    double scrollY = session.materialsScroll().localToScene(0, 0).getY();
                    assertTrue(
                            releaseY > scrollY,
                            "release button must remain visible below materials detail");
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    @Test
    void collapsingRestoresCompactWhenNotManuallyResized() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(sampleRows());
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    double collapsed = window(session).getHeight();
                    session.expandMaterials();
                    forceLayout(session);
                    double expanded = window(session).getHeight();
                    assertTrue(expanded > collapsed);

                    session.collapseMaterials();
                    forceLayout(session);

                    assertFalse(session.materialsExpanded());
                    double restored = window(session).getHeight();
                    assertTrue(
                            Math.abs(restored - collapsed) < 8,
                            "restored " + restored + " vs compact " + collapsed);
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    @Test
    void manualResizeIsRespectedOnCollapse() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(sampleRows());
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    session.expandMaterials();
                    forceLayout(session);
                    Window win = window(session);
                    double manual = win.getHeight() + 40;
                    win.setHeight(manual);
                    assertTrue(session.wasManuallyResized());

                    session.collapseMaterials();
                    forceLayout(session);

                    assertFalse(session.materialsExpanded());
                    assertTrue(
                            Math.abs(win.getHeight() - manual) < 8,
                            "manual height must be kept, was " + win.getHeight());
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    @Test
    void largeMaterialListsScrollInternallyWithinMaxViewport() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(manyMaterials(40));
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    session.expandMaterials();
                    forceLayout(session);

                    ScrollPane scroll = session.materialsScroll();
                    assertEquals(
                            ReleaseConfirmPresentation.MATERIALS_VIEWPORT_MAX_HEIGHT,
                            scroll.getMaxHeight());
                    assertTrue(scroll.getHeight() <= scroll.getMaxHeight() + 1);

                    TitledPane groupPane = session.orderGroups().getFirst().pane();
                    groupPane.setExpanded(true);
                    forceLayout(session);
                    assertTrue(groupPane.getContent() instanceof TableView<?>);
                    @SuppressWarnings("unchecked")
                    TableView<ConfirmMaterialAggregate> table =
                            (TableView<ConfirmMaterialAggregate>) groupPane.getContent();
                    assertEquals(40, table.getItems().size());
                    assertEquals(160, table.getPrefHeight());
                    assertTrue(
                            table.getHeight() <= table.getPrefHeight() + 1,
                            "large lists must keep a bounded table height and scroll inside");
                    assertTrue(
                            window(session).getHeight()
                                    <= Screen.getPrimary().getVisualBounds().getHeight()
                                                    * ReleaseConfirmPresentation
                                                            .CONFIRM_DIALOG_MAX_SCREEN_HEIGHT_FRACTION
                                            + 1);
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    @Test
    void expandedHeightRespectsScreenVisualBounds() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(manyMaterials(80));
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    session.expandMaterials();
                    forceLayout(session);

                    Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
                    double max =
                            bounds.getHeight()
                                    * ReleaseConfirmPresentation
                                            .CONFIRM_DIALOG_MAX_SCREEN_HEIGHT_FRACTION;
                    assertTrue(
                            window(session).getHeight() <= max + 1,
                            "height "
                                    + window(session).getHeight()
                                    + " exceeds screen clamp "
                                    + max);
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    @Test
    void searchStillFiltersWithoutBreakingExpandState() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(sampleRows());
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    session.expandMaterials();
                    forceLayout(session);
                    double before = window(session).getHeight();

                    session.searchField().setText("ART-1");
                    forceLayout(session);

                    ConfirmMaterialAggregate only =
                            ReleaseConfirmPresentation.filterAggregates(
                                            ReleaseConfirmPresentation.aggregateMaterialsForDisplay(
                                                    sampleRows().materials()),
                                            "ART-1")
                                    .getFirst();
                    assertEquals("ART-1", only.materialCode());
                    assertTrue(session.materialsExpanded());
                    assertTrue(
                            Math.abs(window(session).getHeight() - before) < 12,
                            "search must not force unstable resize");
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    @Test
    void multiOrderGroupExpansionStaysInsideScrollBounds() throws Exception {
        AtomicReference<ConfirmDialogSession> sessionRef = new AtomicReference<>();
        AtomicReference<Stage> ownerRef = new AtomicReference<>();
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    ConfirmDialogSession session = createSession(multiOrderRows());
                    Stage owner = show(session);
                    sessionRef.set(session);
                    ownerRef.set(owner);

                    session.expandMaterials();
                    forceLayout(session);
                    double before = window(session).getHeight();

                    for (var group : session.orderGroups()) {
                        TitledPane pane = group.pane();
                        pane.setExpanded(true);
                    }
                    forceLayout(session);

                    assertTrue(
                            Math.abs(window(session).getHeight() - before) < 16,
                            "order-group expand must not grow the window unboundedly");
                    assertTrue(
                            session.materialsScroll().getHeight()
                                    <= ReleaseConfirmPresentation.MATERIALS_VIEWPORT_MAX_HEIGHT
                                            + 1);
                });
        closeQuietly(sessionRef.get(), ownerRef.get());
    }

    private static ConfirmDialogSession createSession(Fixture fixture) {
        return ReleaseConfirmPresentation.createConfirmDialog(fixture.quantities(), fixture.materials());
    }

    private static Stage show(ConfirmDialogSession session) {
        Stage owner = new Stage();
        owner.setScene(new Scene(new Region(), 40, 40));
        owner.show();
        session.dialog().initOwner(owner);
        session.dialog().show();
        forceLayout(session);
        return owner;
    }

    private static void forceLayout(ConfirmDialogSession session) {
        Region pane = session.dialog().getDialogPane();
        pane.applyCss();
        pane.layout();
        session.root().applyCss();
        session.root().layout();
        if (session.materialsScroll().getContent() != null) {
            session.materialsScroll().getContent().applyCss();
            if (session.materialsScroll().getContent() instanceof Region region) {
                region.layout();
            }
        }
    }

    private static Window window(ConfirmDialogSession session) {
        return session.dialog().getDialogPane().getScene().getWindow();
    }

    private static Button lookupReleaseButton(ConfirmDialogSession session) {
        return (Button) session.dialog().getDialogPane().lookupButton(session.releaseType());
    }

    private static Button lookupCancelButton(ConfirmDialogSession session) {
        return (Button) session.dialog().getDialogPane().lookupButton(session.cancelType());
    }

    private static void closeQuietly(ConfirmDialogSession session, Stage owner) throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    if (session != null) {
                        session.dialog().close();
                    }
                    if (owner != null) {
                        owner.close();
                    }
                });
    }

    private static Fixture sampleRows() {
        ReleaseQuantityRow qty = quantity(ORDER_A, ITEM_1, "Заказ №1", 2L);
        ReleaseMaterialRow glass =
                material(ORDER_A, ITEM_1, MAT_1, "Заказ №1", "ART-1", "Стекло", "м2", "10", "10");
        ReleaseMaterialRow seal =
                material(ORDER_A, ITEM_1, MAT_2, "Заказ №1", "ART-SEAL", "Уплотнитель", "м", "2", "2");
        return new Fixture(List.of(qty), List.of(glass, seal));
    }

    private static Fixture manyMaterials(int count) {
        List<ReleaseMaterialRow> materials = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            UUID mat = UUID.fromString(String.format("77777777-7777-4777-8777-%012d", i));
            materials.add(
                    material(
                            ORDER_A,
                            ITEM_1,
                            mat,
                            "Заказ №1",
                            "ART-" + i,
                            "Материал " + i,
                            "м",
                            "1",
                            "1"));
        }
        return new Fixture(List.of(quantity(ORDER_A, ITEM_1, "Заказ №1", 1L)), materials);
    }

    private static Fixture multiOrderRows() {
        List<ReleaseQuantityRow> quantities =
                List.of(
                        quantity(ORDER_A, ITEM_1, "Заказ №1", 3L),
                        quantity(ORDER_B, ITEM_2, "Заказ №2", 5L));
        List<ReleaseMaterialRow> materials =
                List.of(
                        material(ORDER_A, ITEM_1, MAT_1, "Заказ №1", "A1", "Стекло", "м2", "1", "1"),
                        material(ORDER_B, ITEM_2, MAT_2, "Заказ №2", "B1", "Профиль", "м", "2", "3"));
        return new Fixture(quantities, materials);
    }

    private static ReleaseQuantityRow quantity(
            UUID orderId, UUID itemId, String orderLabel, long releaseQty) {
        return new ReleaseQuantityRow(
                orderId,
                itemId,
                orderLabel,
                "1",
                "Изделие",
                QuantityModeView.FLEXIBLE,
                10L,
                0L,
                10L,
                releaseQty);
    }

    private static ReleaseMaterialRow material(
            UUID orderId,
            UUID itemId,
            UUID materialId,
            String orderLabel,
            String code,
            String name,
            String uom,
            String planned,
            String actual) {
        return new ReleaseMaterialRow(
                orderId, itemId, materialId, orderLabel, code, name, uom, planned, actual);
    }

    private record Fixture(List<ReleaseQuantityRow> quantities, List<ReleaseMaterialRow> materials) {}
}
