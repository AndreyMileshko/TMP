package com.tmp.document.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.DocumentMetadata;
import com.tmp.document.api.port.DocumentFileReference;
import com.tmp.document.api.port.DocumentFileStoragePort;
import com.tmp.document.support.TestDocumentProcessor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = JdbcDocumentFileStorageAdapterTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_document_file_storage;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class JdbcDocumentFileStorageAdapterTest {

    private static final String TYPE_ID = "platform.files";

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private DocumentFileStoragePort documentFileStoragePort;

    @BeforeEach
    void registerProcessor() {
        try {
            documentEngine.registerProcessor(new TestDocumentProcessor(TYPE_ID));
        } catch (IllegalStateException alreadyRegistered) {
            // shared context
        }
    }

    @Test
    void registersAndFindsDocumentFileReference() {
        DocumentMetadata created = documentEngine.createDocument(new CreateDocumentCommand(TYPE_ID, "With file"));
        DocumentFileReference reference = new DocumentFileReference(
                UUID.randomUUID(),
                created.id(),
                "/storage/test.pdf",
                "application/pdf",
                1024L,
                "checksum-abc",
                Instant.now());

        documentFileStoragePort.registerReference(reference);

        assertFalse(documentFileStoragePort.findByDocumentId(created.id()).isEmpty());
        assertEquals("/storage/test.pdf", documentFileStoragePort.findByDocumentId(created.id()).getFirst().path());
    }

    @SpringBootApplication
    @Import({
            com.tmp.infra.db.DatabaseAutoConfiguration.class,
            com.tmp.core.PlatformCoreAutoConfiguration.class,
            com.tmp.document.DocumentEngineAutoConfiguration.class
    })
    static class TestApplication {
    }
}
