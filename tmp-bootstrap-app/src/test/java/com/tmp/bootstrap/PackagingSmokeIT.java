package com.tmp.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingSmokeIT {

    private static final Path APP_IMAGE = Path.of("..", "dist", "jpackage", "TMP");

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
}
