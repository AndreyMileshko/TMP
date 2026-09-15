package com.tmp.warehouse.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcTransferDocumentOrderReferenceQueryTest {

    @Test
    void emptyInputReturnsEmptyMapWithoutQuerying() {
        // DataSource is never used for empty input.
        DriverManagerDataSource unused = new DriverManagerDataSource();
        unused.setUrl("jdbc:postgresql://localhost:1/unused");
        JdbcTransferDocumentOrderReferenceQuery query =
                new JdbcTransferDocumentOrderReferenceQuery(new JdbcTemplate(unused));
        Map<UUID, String> result = query.findOrderNumbersByDocumentIds(List.of());
        assertTrue(result.isEmpty());
    }
}
