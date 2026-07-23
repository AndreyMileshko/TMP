package com.tmp.bootstrap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.tmp.document.api.DocumentEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DocumentEngineBeanLookupTest extends AbstractBootstrapPostgresSpringTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void applicationContextResolvesSingleDocumentEngineBean() {
        DocumentEngine documentEngine = applicationContext.getBean(DocumentEngine.class);
        assertNotNull(documentEngine);
    }

    @Test
    void documentEngineBeanLookupIsUnambiguous() {
        String[] beanNames = applicationContext.getBeanNamesForType(DocumentEngine.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, beanNames.length);
        DocumentEngine byType = applicationContext.getBean(DocumentEngine.class);
        DocumentEngine byName = applicationContext.getBean(beanNames[0], DocumentEngine.class);
        assertSame(byType, byName);
    }
}
