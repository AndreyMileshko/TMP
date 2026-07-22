package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.DocumentEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

class DesktopBootstrapLookupSmokeTest {

    @Test
    void desktopBootstrapResolvesDocumentEngineWithoutAmbiguity() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TmpBootstrapApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run(
                        "--spring.datasource.url=jdbc:h2:mem:tmp_desktop_bootstrap_lookup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                        "--spring.datasource.driver-class-name=org.h2.Driver",
                        "--spring.datasource.username=sa",
                        "--spring.datasource.password=");

        try {
            PlatformCore platformCore = context.getBean(PlatformCore.class);
            DocumentEngine documentEngine = context.getBean(DocumentEngine.class);
            assertNotNull(platformCore);
            assertNotNull(documentEngine);
            assertNotNull(DesktopBootstrap.formatDocumentPanel(documentEngine));
        } finally {
            context.close();
        }
    }
}
