package com.tmp.bootstrap;

import com.tmp.capability.api.CapabilityDescriptor;
import com.tmp.capability.api.CapabilityEngine;
import com.tmp.capability.api.CapabilityEngineStatus;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.DocumentQuery;
import com.tmp.security.api.AuthenticationService;
import com.tmp.ui.shell.JavaFxShellLauncher;
import com.tmp.ui.shell.UiShellEntryPoint;
import java.util.List;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public final class DesktopBootstrap {

    private DesktopBootstrap() {
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext springContext = new SpringApplicationBuilder(TmpBootstrapApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        UiShellEntryPoint entryPoint = springContext.getBean(UiShellEntryPoint.class);
        AuthenticationService authenticationService = springContext.getBean(AuthenticationService.class);
        JavaFxShellLauncher.launch(springContext::close, entryPoint, authenticationService);
    }

    /**
     * Retained for smoke/lookup tests that assert Capability Engine status formatting.
     */
    static String formatCapabilityStatus(CapabilityEngine capabilityEngine) {
        CapabilityEngineStatus status = capabilityEngine.status();
        StringBuilder builder = new StringBuilder("Capability Engine\n");
        builder.append("discovered=")
                .append(status.discoveredCount())
                .append(" active=")
                .append(status.activeCount())
                .append('\n');
        for (CapabilityDescriptor descriptor : capabilityEngine.registeredCapabilities()) {
            builder.append(descriptor.id().value())
                    .append(" state=")
                    .append(capabilityEngine.stateOf(descriptor.id()))
                    .append('\n');
        }
        return builder.toString().trim();
    }

    /**
     * Retained for smoke/lookup tests that assert Document Engine panel formatting.
     */
    static String formatDocumentPanel(DocumentEngine documentEngine) {
        List<DocumentMetadata> documents = documentEngine.search(DocumentQuery.all(5));
        if (documents.isEmpty()) {
            return "Document Engine ready\nNo documents yet";
        }
        StringBuilder builder = new StringBuilder("Document Engine\n");
        for (DocumentMetadata document : documents) {
            builder.append(document.documentNumber())
                    .append(" | ")
                    .append(document.title())
                    .append(" | ")
                    .append(document.status())
                    .append('\n');
        }
        return builder.toString().trim();
    }
}
