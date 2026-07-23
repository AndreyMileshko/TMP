package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.tmp.capability.api.CapabilityEngine;
import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.DocumentEngine;
import com.tmp.ui.shell.UiShellEntryPoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DesktopBootstrapLookupSmokeTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void desktopBootstrapResolvesDocumentEngineWithoutAmbiguity() {
        ConfigurableApplicationContext context = new SpringApplicationBuilder(TmpBootstrapApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run(
                        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "--spring.datasource.username=" + POSTGRES.getUsername(),
                        "--spring.datasource.password=" + POSTGRES.getPassword(),
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--tmp.security.bootstrap.admin-login=admin",
                        "--tmp.security.bootstrap.admin-display-name=Administrator",
                        "--tmp.security.bootstrap.admin-password=test-admin-password");

        try {
            PlatformCore platformCore = context.getBean(PlatformCore.class);
            DocumentEngine documentEngine = context.getBean(DocumentEngine.class);
            CapabilityEngine capabilityEngine = context.getBean(CapabilityEngine.class);
            UiShellEntryPoint entryPoint = context.getBean(UiShellEntryPoint.class);
            assertNotNull(platformCore);
            assertNotNull(documentEngine);
            assertNotNull(capabilityEngine);
            assertNotNull(entryPoint);
            assertNotNull(DesktopBootstrap.formatDocumentPanel(documentEngine));
            assertNotNull(DesktopBootstrap.formatCapabilityStatus(capabilityEngine));
        } finally {
            context.close();
        }
    }
}
