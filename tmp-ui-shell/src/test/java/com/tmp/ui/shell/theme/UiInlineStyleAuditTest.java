package com.tmp.ui.shell.theme;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Lightweight audit for inline permanent CSS violations in UI resources.
 * Known legacy violations are allow-listed; new violations fail the build.
 */
class UiInlineStyleAuditTest {

    private static final Pattern INLINE_FXML_STYLE = Pattern.compile("\\bstyle\\s*=");
    private static final Pattern INLINE_JAVA_SET_STYLE = Pattern.compile("\\.setStyle\\s*\\(");

    /**
     * Legacy inline styles documented in Stage 3.0 audit; to be removed in Stage 3.6 Production.
     */
    private static final Set<String> ALLOWED_FXML_INLINE_STYLE = Set.of(
            "com/tmp/ui/shell/screen/production/ProductionWorkbenchScreen.fxml");

    @Test
    void fxmlMustNotIntroduceNewInlineStyleAttributes() throws IOException {
        Path resourcesRoot = resolveResourcesRoot();
        List<String> violations = new ArrayList<>();
        final Path scanRoot = resourcesRoot;

        try (Stream<Path> paths = Files.walk(scanRoot)) {
            paths.filter(path -> path.toString().replace('\\', '/').endsWith(".fxml"))
                    .forEach(path -> {
                        String relative = scanRoot.relativize(path).toString().replace('\\', '/');
                        if (ALLOWED_FXML_INLINE_STYLE.contains(relative)) {
                            return;
                        }
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            if (INLINE_FXML_STYLE.matcher(content).find()) {
                                violations.add(relative);
                            }
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        }

        if (!violations.isEmpty()) {
            fail("Inline style=\"...\" found in FXML (not allow-listed): " + violations);
        }
    }

    @Test
    void productionUiJavaMustNotUseSetStyle() throws IOException {
        Path javaRoot = resolveJavaRoot();
        List<String> violations = new ArrayList<>();
        final Path scanRoot = javaRoot;

        try (Stream<Path> paths = Files.walk(scanRoot)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        String relative = scanRoot.relativize(path).toString().replace('\\', '/');
                        try {
                            String content = Files.readString(path, StandardCharsets.UTF_8);
                            if (INLINE_JAVA_SET_STYLE.matcher(content).find()) {
                                violations.add(relative);
                            }
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    });
        }

        if (!violations.isEmpty()) {
            fail("setStyle(...) found in production UI Java: " + violations);
        }
    }

    private static Path resolveResourcesRoot() {
        Path local = Path.of("src/main/resources");
        if (Files.isDirectory(local)) {
            return local;
        }
        return Path.of("tmp-ui-shell/src/main/resources");
    }

    private static Path resolveJavaRoot() {
        Path local = Path.of("src/main/java");
        if (Files.isDirectory(local)) {
            return local;
        }
        return Path.of("tmp-ui-shell/src/main/java");
    }
}
