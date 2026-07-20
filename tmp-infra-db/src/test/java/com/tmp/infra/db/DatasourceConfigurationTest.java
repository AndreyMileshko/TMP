package com.tmp.infra.db;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = InfraDbTestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:tmp_infra_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
class DatasourceConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Environment environment;

    @Test
    void contextLoadsDatasource() throws Exception {
        assertNotNull(dataSource);
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.isValid(2));
        }
    }

    @Test
    void testProfileOverridesDatasourceProperties() {
        assertEquals("org.h2.Driver", environment.getProperty("spring.datasource.driver-class-name"));
        assertEquals("sa", environment.getProperty("spring.datasource.username"));
    }
}
