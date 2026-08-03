package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import com.tmp.security.api.AuthenticationService;
import com.tmp.ui.shell.JavaFxShellApplication;
import com.tmp.ui.shell.UiShellScreens;
import com.tmp.ui.shell.screen.login.LoginViewModel;
import com.tmp.ui.shell.screen.orderimport.OrderImportController;
import com.tmp.ui.shell.screen.orderimport.OrderImportViewModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * STAGE5-056 packaging smoke: app-image artifacts, TMP.exe launch + login window, admin login and
 * Import GUI open without import/data mutation.
 */
class PackagingSmokeIT {

    private static final Path APP_IMAGE = Path.of("..", "dist", "jpackage", "TMP");
    private static final String ADMIN_PASSWORD = "package-smoke-admin-password";
    private static final Duration WINDOW_WAIT = Duration.ofSeconds(90);

    @Test
    @EnabledIfSystemProperty(named = "tmp.package.verify", matches = "true")
    void packageProfileProducesExecutableAppImage() throws IOException {
        Path executable = APP_IMAGE.resolve("TMP.exe");
        Path runtime = APP_IMAGE.resolve("runtime");
        Path appDirectory = APP_IMAGE.resolve("app");
        Path launcherConfig = appDirectory.resolve("TMP.cfg");

        assertTrue(Files.isDirectory(APP_IMAGE), "jpackage app-image root must exist");
        assertTrue(Files.isRegularFile(executable), "jpackage must produce TMP.exe");
        assertTrue(Files.isDirectory(runtime), "jpackage must include bundled runtime");
        assertTrue(Files.isDirectory(appDirectory), "jpackage must include app directory");

        assertTrue(Files.isRegularFile(runtime.resolve("release")), "Bundled runtime must include release metadata");
        assertTrue(
                Files.isRegularFile(runtime.resolve("lib").resolve("jvm.cfg"))
                        || Files.isRegularFile(runtime.resolve("bin").resolve("java.dll")),
                "Bundled runtime must include JVM binaries");

        Path applicationJar = findApplicationJar(appDirectory);
        assertNotNull(applicationJar, "App directory must contain bootstrap fat jar");
        assertTrue(Files.isRegularFile(applicationJar), "Bootstrap fat jar must be present");

        assertTrue(Files.isRegularFile(launcherConfig), "App directory must include launcher configuration");
        String launcherConfiguration = Files.readString(launcherConfig);
        assertTrue(
                launcherConfiguration.contains("spring.profiles.active=package"),
                "Launcher config must activate package profile");
        assertTrue(
                launcherConfiguration.contains("org.springframework.boot.loader.launch.JarLauncher"),
                "Launcher config must use Spring Boot jar launcher");

        assertTrue(
                jarContainsResource(applicationJar, "application-package.yml"),
                "Bootstrap fat jar must include package profile configuration");
    }

    @Test
    @EnabledIfSystemProperty(named = "tmp.package.verify", matches = "true")
    void packagedTmpStartsLoginWindowAdminLoginOpensImportGuiWithoutImport() throws Exception {
        Path executable = APP_IMAGE.resolve("TMP.exe");
        assertTrue(Files.isRegularFile(executable), "TMP.exe must exist before runtime smoke");

        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();

            Process process = startPackagedTmp(executable, postgres);
            try {
                assertTrue(
                        waitForWindowTitle(JavaFxShellApplication.WINDOW_TITLE, WINDOW_WAIT),
                        "Login window title must appear after TMP starts");
                assertTrue(process.isAlive(), "TMP process must stay alive while login window is shown");
            } finally {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
            }

            try (ConfigurableApplicationContext context = startPackageProfileContext(postgres)) {
                AuthenticationService authenticationService =
                        context.getBean(AuthenticationService.class);
                LoginViewModel loginViewModel = context.getBean(LoginViewModel.class);
                OrderImportViewModel orderImportViewModel = context.getBean(OrderImportViewModel.class);
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

                int ordersBefore =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM order_management.orders", Integer.class);

                ensureToolkit();
                AtomicBoolean loginNavigated = new AtomicBoolean(false);
                loginViewModel.setOnLoginSuccess(() -> loginNavigated.set(true));
                loginViewModel.loginProperty().set("admin");
                assertTrue(
                        loginViewModel.submit(ADMIN_PASSWORD.toCharArray()),
                        "admin must authenticate successfully");
                assertTrue(loginNavigated.get());
                assertTrue(authenticationService.isAuthenticated());

                LoadedImportScreen importScreen = loadImportScreen(orderImportViewModel);
                assertNotNull(importScreen.root.lookup("#selectFileButton"));
                assertNotNull(importScreen.root.lookup("#fileNameField"));
                assertNotNull(importScreen.root.lookup("#importButton"));
                assertEquals(
                        "Выбрать файл",
                        ((Button) importScreen.root.lookup("#selectFileButton")).getText());
                assertTrue(((TextField) importScreen.root.lookup("#fileNameField")).getText().isBlank());
                assertFalse(
                        orderImportViewModel.canImportProperty().get(),
                        "Import must stay disabled without file/preview");

                int ordersAfter =
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM order_management.orders", Integer.class);
                assertEquals(ordersBefore, ordersAfter, "Packaging smoke must not import or mutate orders");
                authenticationService.logout();
            }
        }
    }

    private static Process startPackagedTmp(Path executable, PostgreSQLContainer<?> postgres)
            throws IOException {
        ProcessBuilder builder = new ProcessBuilder(executable.toAbsolutePath().toString());
        builder.directory(APP_IMAGE.toAbsolutePath().toFile());
        Map<String, String> env = builder.environment();
        env.put("TMP_DB_URL", postgres.getJdbcUrl());
        env.put("TMP_DB_USERNAME", postgres.getUsername());
        env.put("TMP_DB_PASSWORD", postgres.getPassword());
        env.put("TMP_SECURITY_BOOTSTRAP_ADMIN_LOGIN", "admin");
        env.put("TMP_SECURITY_BOOTSTRAP_ADMIN_DISPLAY_NAME", "Administrator");
        env.put("TMP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD", ADMIN_PASSWORD);
        builder.redirectErrorStream(true);
        return builder.start();
    }

    private static ConfigurableApplicationContext startPackageProfileContext(
            PostgreSQLContainer<?> postgres) {
        return new SpringApplicationBuilder(TmpBootstrapApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("package")
                .run(
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--tmp.security.bootstrap.admin-login=admin",
                        "--tmp.security.bootstrap.admin-display-name=Administrator",
                        "--tmp.security.bootstrap.admin-password=" + ADMIN_PASSWORD);
    }

    private static LoadedImportScreen loadImportScreen(OrderImportViewModel viewModel)
            throws Exception {
        AtomicReference<Parent> root = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(
                () -> {
                    try {
                        FXMLLoader loader =
                                new FXMLLoader(
                                        PackagingSmokeIT.class
                                                .getClassLoader()
                                                .getResource(UiShellScreens.ORDER_IMPORT_FXML));
                        Parent loaded = loader.load();
                        OrderImportController controller = loader.getController();
                        controller.setViewModel(viewModel);
                        root.set(loaded);
                    } catch (Throwable throwable) {
                        error.set(throwable);
                    } finally {
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "Import FXML load timed out");
        if (error.get() != null) {
            throw new AssertionError("Import GUI failed to open", error.get());
        }
        return new LoadedImportScreen(root.get());
    }

    private static void ensureToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // Toolkit already started.
        }
    }

    private static boolean waitForWindowTitle(String title, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (windowTitleExists(title)) {
                return true;
            }
            Thread.sleep(500L);
        }
        return windowTitleExists(title);
    }

    private static boolean windowTitleExists(String title) {
        AtomicBoolean found = new AtomicBoolean(false);
        char[] expected = title.toCharArray();
        User32.INSTANCE.EnumWindows(
                (hWnd, data) -> {
                    if (!User32.INSTANCE.IsWindowVisible(hWnd)) {
                        return true;
                    }
                    char[] buffer = new char[512];
                    int length = User32.INSTANCE.GetWindowTextW(hWnd, buffer, buffer.length);
                    if (length > 0 && length == expected.length) {
                        boolean match = true;
                        for (int i = 0; i < length; i++) {
                            if (buffer[i] != expected[i]) {
                                match = false;
                                break;
                            }
                        }
                        if (match) {
                            found.set(true);
                            return false;
                        }
                    }
                    return true;
                },
                Pointer.NULL);
        return found.get();
    }

    private static Path findApplicationJar(Path appDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(appDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().startsWith("tmp-bootstrap-app")
                            && path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElse(null);
        }
    }

    private static boolean jarContainsResource(Path jarPath, String resourceName) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            return jarFile.getEntry(resourceName) != null
                    || jarFile.getEntry("BOOT-INF/classes/" + resourceName) != null;
        }
    }

    private record LoadedImportScreen(Parent root) {
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class, W32APIOptions.DEFAULT_OPTIONS);

        interface WNDENUMPROC extends StdCallLibrary.StdCallCallback {
            boolean callback(Pointer hWnd, Pointer data);
        }

        boolean EnumWindows(WNDENUMPROC lpEnumFunc, Pointer data);

        int GetWindowTextW(Pointer hWnd, char[] lpString, int nMaxCount);

        boolean IsWindowVisible(Pointer hWnd);
    }
}
