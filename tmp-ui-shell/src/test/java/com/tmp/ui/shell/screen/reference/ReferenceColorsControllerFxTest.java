package com.tmp.ui.shell.screen.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.reference.api.ColorCatalogCommandApi;
import com.tmp.reference.api.ColorCatalogQueryApi;
import com.tmp.reference.api.ColorDto;
import com.tmp.reference.api.ColorMatchingSettingApi;
import com.tmp.reference.api.ColorMatchingSettingApi.ColorMatchingSettingDto;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthorizationService;
import com.tmp.security.api.PermissionId;
import com.tmp.ui.shell.JavaFxTestSupport;
import com.tmp.ui.shell.UiShellScreens;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** COL-030A GUI acceptance fixes: loading geometry + matching checkbox confirmation. */
class ReferenceColorsControllerFxTest {

    @BeforeAll
    static void initJavaFx() {
        JavaFxTestSupport.ensureToolkit();
    }

    @Test
    void loadingOverlayNeverManagedAndDoesNotShiftTable() throws Exception {
        AtomicReference<Double> heightBefore = new AtomicReference<>();
        AtomicReference<Double> heightDuring = new AtomicReference<>();
        AtomicReference<Boolean> managedDuring = new AtomicReference<>();
        AtomicReference<Boolean> visibleDuring = new AtomicReference<>();

        JavaFxTestSupport.runOnFxThread(
                () -> {
                    Fixture fixture = loadFixture(false);
                    Parent root = fixture.root();
                    Stage stage = new Stage();
                    stage.setScene(new Scene(root, 960, 640));
                    stage.show();
                    root.applyCss();
                    root.layout();

                    TableView<?> table = fixture.controller().colorsTableForTests();
                    Label loading = fixture.controller().loadingLabelForTests();
                    heightBefore.set(table.getHeight());
                    assertTrue(heightBefore.get() > 100.0);

                    fixture.viewModel().loadingProperty().set(true);
                    root.layout();

                    managedDuring.set(loading.isManaged());
                    visibleDuring.set(loading.isVisible());
                    heightDuring.set(table.getHeight());
                    stage.close();
                });

        assertFalse(managedDuring.get(), "loadingLabel must stay unmanaged");
        assertTrue(visibleDuring.get());
        assertEquals(heightBefore.get(), heightDuring.get(), 0.5);
    }

    @Test
    void matchingCancelRestoresConfirmedStateBothDirections() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    Fixture fixture = loadFixture(false);
                    ReferenceColorsController controller = fixture.controller();
                    CheckBox check = controller.automaticMatchingCheckForTests();
                    AtomicInteger confirms = new AtomicInteger();
                    controller.setMatchingConfirmation(
                            () -> {
                                confirms.incrementAndGet();
                                return false;
                            });

                    assertFalse(check.isSelected());
                    check.setSelected(true);
                    assertFalse(check.isSelected(), "cancel OFF->ON must restore OFF");
                    assertFalse(fixture.viewModel().automaticMatchingEnabledProperty().get());
                    assertEquals(1, confirms.get());
                    assertEquals(0, fixture.setting().setCalls);

                    fixture.setting().enabled = true;
                    fixture.setting().version = 1L;
                    fixture.viewModel().automaticMatchingEnabledProperty().set(true);
                    assertTrue(check.isSelected());

                    check.setSelected(false);
                    assertTrue(check.isSelected(), "cancel ON->OFF must restore ON");
                    assertTrue(fixture.viewModel().automaticMatchingEnabledProperty().get());
                    assertEquals(2, confirms.get());
                    assertEquals(0, fixture.setting().setCalls);
                });
    }

    @Test
    void matchingConfirmPersistsBothDirectionsWithoutRecursion() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    Fixture fixture = loadFixture(false);
                    ReferenceColorsController controller = fixture.controller();
                    CheckBox check = controller.automaticMatchingCheckForTests();
                    AtomicInteger confirms = new AtomicInteger();
                    controller.setMatchingConfirmation(
                            () -> {
                                confirms.incrementAndGet();
                                return true;
                            });

                    check.setSelected(true);
                    assertTrue(check.isSelected());
                    assertTrue(fixture.viewModel().automaticMatchingEnabledProperty().get());
                    assertTrue(fixture.setting().enabled);
                    assertEquals(1, fixture.setting().setCalls);
                    assertEquals(1, confirms.get());

                    check.setSelected(false);
                    assertFalse(check.isSelected());
                    assertFalse(fixture.viewModel().automaticMatchingEnabledProperty().get());
                    assertFalse(fixture.setting().enabled);
                    assertEquals(2, fixture.setting().setCalls);
                    assertEquals(2, confirms.get());
                });
    }

    @Test
    void matchingPersistFailureRestoresConfirmedStateAndShowsError() throws Exception {
        JavaFxTestSupport.runOnFxThread(
                () -> {
                    Fixture fixture = loadFixture(false);
                    ReferenceColorsController controller = fixture.controller();
                    CheckBox check = controller.automaticMatchingCheckForTests();
                    AtomicBoolean confirmed = new AtomicBoolean(true);
                    controller.setMatchingConfirmation(confirmed::get);

                    fixture.setting().failNextSet = new RuntimeException("persist failed");
                    check.setSelected(true);

                    assertFalse(check.isSelected());
                    assertFalse(fixture.viewModel().automaticMatchingEnabledProperty().get());
                    assertTrue(fixture.viewModel().errorMessageProperty().get().length() > 0);
                    assertEquals(1, fixture.setting().setCalls);
                });
    }

    private static Fixture loadFixture(boolean matchingEnabled) {
        FakeSetting setting = new FakeSetting();
        setting.enabled = matchingEnabled;
        FakeAuth auth = new FakeAuth();
        auth.allow(
                UiShellScreens.REFERENCE_COLOR_VIEW_PERMISSION,
                UiShellScreens.REFERENCE_COLOR_SETTING_EDIT_PERMISSION);
        ReferenceColorsViewModel viewModel =
                new ReferenceColorsViewModel(
                        new EmptyQuery(),
                        new NoOpCommand(),
                        setting,
                        auth,
                        Runnable::run,
                        Runnable::run,
                        0L);

        FXMLLoader loader =
                new FXMLLoader(
                        Thread.currentThread()
                                .getContextClassLoader()
                                .getResource(UiShellScreens.REFERENCE_COLORS_FXML));
        loader.setControllerFactory(type -> new ReferenceColorsController());
        try {
            Parent root = loader.load();
            ReferenceColorsController controller = loader.getController();
            controller.setViewModel(viewModel);
            return new Fixture(root, controller, viewModel, setting);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Fixture(
            Parent root,
            ReferenceColorsController controller,
            ReferenceColorsViewModel viewModel,
            FakeSetting setting) {}

    private static final class FakeAuth implements AuthorizationService {
        private final Set<String> allowed = new HashSet<>();

        void allow(String... permissions) {
            allowed.clear();
            allowed.addAll(List.of(permissions));
        }

        @Override
        public boolean hasPermission(PermissionId permissionId) {
            return allowed.contains(permissionId.value());
        }

        @Override
        public void requirePermission(PermissionId permissionId) {
            if (!hasPermission(permissionId)) {
                throw new AccessDeniedException("denied");
            }
        }

        @Override
        public Set<PermissionId> effectivePermissions() {
            return Set.of();
        }
    }

    private static final class EmptyQuery implements ColorCatalogQueryApi {
        @Override
        public List<ColorDto> listColors(ColorDto.ActiveFilter filter, String search) {
            return List.of();
        }

        @Override
        public long getArchiveImpact(UUID colorId) {
            return 0L;
        }
    }

    private static final class NoOpCommand implements ColorCatalogCommandApi {
        @Override
        public ColorDto createColor(String code) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int archiveColor(UUID colorId, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ColorDto restoreColor(UUID colorId, long expectedVersion) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeSetting implements ColorMatchingSettingApi {
        private boolean enabled;
        private long version;
        private int setCalls;
        private RuntimeException failNextSet;

        @Override
        public ColorMatchingSettingDto getAutomaticMatchingSetting() {
            return new ColorMatchingSettingDto(enabled, version);
        }

        @Override
        public ColorMatchingSettingDto setAutomaticMatchingEnabled(
                boolean enabled, long expectedVersion) {
            setCalls++;
            if (failNextSet != null) {
                RuntimeException ex = failNextSet;
                failNextSet = null;
                throw ex;
            }
            if (expectedVersion != version) {
                throw new RuntimeException("Version conflict");
            }
            this.enabled = enabled;
            version++;
            return new ColorMatchingSettingDto(this.enabled, version);
        }
    }
}
