package com.tmp.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.document.api.CreateDocumentCommand;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.support.TestDocumentProcessor;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(classes = DefaultDocumentEngineRegistrationTransactionTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_document_engine_reg_tx;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DefaultDocumentEngineRegistrationTransactionTest {

    @Autowired
    private DocumentEngine documentEngine;

    @Autowired
    private DefaultDocumentProcessorRegistry processorRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void outerRollbackRemovesDbTypeAndInMemoryProcessorThenRetrySucceeds() {
        String typeId = uniqueType("rollback");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
            assertTrue(processorRegistry.isRegistered(typeId));
            assertEquals(1, countTypes(typeId));
            status.setRollbackOnly();
        });

        assertEquals(0, countTypes(typeId));
        assertFalse(processorRegistry.isRegistered(typeId));
        assertThrows(
                IllegalStateException.class,
                () -> documentEngine.createDocument(new CreateDocumentCommand(typeId, "orphan")));

        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        assertTrue(processorRegistry.isRegistered(typeId));
        assertEquals(1, countTypes(typeId));
        assertEquals(typeId, documentEngine.createDocument(new CreateDocumentCommand(typeId, "ok")).documentTypeId());
    }

    @Test
    void commitFailurePathUnregistersProcessor() {
        String typeId = uniqueType("commit.fail");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        assertThrows(IllegalStateException.class, () -> transactionTemplate.executeWithoutResult(status -> {
            documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void beforeCommit(boolean readOnly) {
                    throw new IllegalStateException("simulated commit failure");
                }
            });
        }));

        assertEquals(0, countTypes(typeId));
        assertFalse(processorRegistry.isRegistered(typeId));

        documentEngine.registerProcessor(new TestDocumentProcessor(typeId));
        assertTrue(processorRegistry.isRegistered(typeId));
    }

    @Test
    void createRejectedWhenProcessorPresentButTypeMissingFromDb() {
        String typeId = uniqueType("missing.db");
        processorRegistry.register(new TestDocumentProcessor(typeId));
        assertEquals(0, countTypes(typeId));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> documentEngine.createDocument(new CreateDocumentCommand(typeId, "blocked")));
        assertTrue(failure.getMessage().contains("document_types"));
    }

    @Test
    void foreignKeyRejectsDocumentRowWithoutRegisteredType() {
        UUID documentId = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO documents.documents (
                    id, document_type_id, document_number, title, status, version,
                    created_at, updated_at, posted_at, closed_at)
                VALUES (?, ?, ?, ?, 'DRAFT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL, NULL)
                """,
                documentId,
                "missing.type." + UUID.randomUUID(),
                "NUM-" + documentId,
                "orphan row"));
    }

    private int countTypes(String typeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents.document_types WHERE id = ?",
                Integer.class,
                typeId);
        return count == null ? 0 : count;
    }

    private static String uniqueType(String suffix) {
        return "platform.reg.tx." + suffix + "." + UUID.randomUUID();
    }

    @SpringBootApplication
    @Import({
            com.tmp.infra.db.DatabaseAutoConfiguration.class,
            com.tmp.core.PlatformCoreAutoConfiguration.class,
            DocumentEngineAutoConfiguration.class
    })
    static class TestApplication {
    }
}
