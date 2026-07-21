package com.tmp.document;

import com.tmp.core.api.PlatformCore;
import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.port.DocumentFileStoragePort;
import com.tmp.document.api.port.DocumentStoragePort;
import com.tmp.document.api.port.DocumentVersionPort;
import com.tmp.document.api.port.LifecycleJournalPort;
import com.tmp.document.persistence.JdbcDocumentFileStorageAdapter;
import com.tmp.document.persistence.JdbcDocumentStorageAdapter;
import com.tmp.document.persistence.JdbcDocumentVersionAdapter;
import com.tmp.document.persistence.JdbcLifecycleJournalAdapter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration.class)
@EnableTransactionManagement
public class DocumentEngineAutoConfiguration {

    @Bean
    DefaultDocumentProcessorRegistry documentProcessorRegistry() {
        return new DefaultDocumentProcessorRegistry();
    }

    @Bean
    DocumentStoragePort documentStoragePort(JdbcTemplate jdbcTemplate) {
        return new JdbcDocumentStorageAdapter(jdbcTemplate);
    }

    @Bean
    LifecycleJournalPort lifecycleJournalPort(JdbcTemplate jdbcTemplate) {
        return new JdbcLifecycleJournalAdapter(jdbcTemplate);
    }

    @Bean
    DocumentVersionPort documentVersionPort(JdbcTemplate jdbcTemplate) {
        return new JdbcDocumentVersionAdapter(jdbcTemplate);
    }

    @Bean
    DocumentFileStoragePort documentFileStoragePort(JdbcTemplate jdbcTemplate) {
        return new JdbcDocumentFileStorageAdapter(jdbcTemplate);
    }

    @Bean
    DefaultDocumentEngine documentEngine(
            DefaultDocumentProcessorRegistry processorRegistry,
            DocumentStoragePort documentStoragePort,
            LifecycleJournalPort lifecycleJournalPort,
            DocumentVersionPort documentVersionPort) {
        return new DefaultDocumentEngine(
                processorRegistry,
                documentStoragePort,
                lifecycleJournalPort,
                documentVersionPort);
    }

    @Bean
    DocumentEngine documentEngineFacade(DefaultDocumentEngine documentEngine) {
        return documentEngine;
    }

    @Bean
    DocumentEnginePlatformRegistrar documentEnginePlatformRegistrar(
            PlatformCore platformCore, DefaultDocumentEngine documentEngine) {
        return new DocumentEnginePlatformRegistrar(platformCore, documentEngine);
    }
}
