package com.tmp.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagingSmokeIT {

    @Test
    @EnabledIfSystemProperty(named = "tmp.package.verify", matches = "true")
    void packageProfileProducesAppImage() {
        Path appImage = Path.of("..", "dist", "jpackage", "TMP");
        assertTrue(Files.exists(appImage), "jpackage app-image must exist under dist/jpackage/TMP");
    }
}
