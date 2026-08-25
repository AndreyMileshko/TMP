package com.tmp.production.integration.publicboundary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * STAGE7-018 source-level import guard for the final public-boundary package. Forbids OM/Warehouse
 * application/domain/persistence imports in the suite and its support classes.
 */
class PublicBoundaryImportGuardTest {

    private static final Pattern IMPORT =
            Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)\\s*;", Pattern.MULTILINE);

    private static final List<String> FORBIDDEN_PREFIXES =
            List.of(
                    "com.tmp.order.application",
                    "com.tmp.order.domain",
                    "com.tmp.order.persistence",
                    "com.tmp.warehouse.application",
                    "com.tmp.warehouse.domain",
                    "com.tmp.warehouse.persistence");

    @Test
    void publicBoundaryPackageDoesNotImportOmOrWarehouseInternals() throws IOException {
        Path packageDir =
                Path.of("src/test/java/com/tmp/production/integration/publicboundary");
        assertTrue(Files.isDirectory(packageDir), "publicboundary package must exist");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(packageDir)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(
                            path -> {
                                try {
                                    String source = Files.readString(path);
                                    Matcher matcher = IMPORT.matcher(source);
                                    while (matcher.find()) {
                                        String imported = matcher.group(2);
                                        for (String forbidden : FORBIDDEN_PREFIXES) {
                                            if (imported.equals(forbidden)
                                                    || imported.startsWith(forbidden + ".")) {
                                                violations.add(path.getFileName() + " -> " + imported);
                                            }
                                        }
                                    }
                                } catch (IOException ex) {
                                    fail("Failed to read " + path + ": " + ex.getMessage());
                                }
                            });
        }

        if (!violations.isEmpty()) {
            fail("Forbidden OM/Warehouse internal imports:\n" + String.join("\n", violations));
        }

        String suite =
                Files.readString(packageDir.resolve("ProductionPublicBoundaryPostgresIT.java"));
        assertTrue(suite.contains("com.tmp.order.api"));
        assertTrue(suite.contains("com.tmp.warehouse.api"));
        assertFalse(suite.contains("com.tmp.order.application."));
        assertFalse(suite.contains("com.tmp.warehouse.application."));
        assertFalse(suite.contains("com.tmp.order.domain."));
        assertFalse(suite.contains("com.tmp.warehouse.domain."));
        assertFalse(suite.contains("com.tmp.order.persistence."));
        assertFalse(suite.contains("com.tmp.warehouse.persistence."));
    }
}
