package com.tmp.order.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tmp.order.OrderManagementAutoConfiguration;
import com.tmp.order.api.OrderQueryService;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.Login;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * STAGE5-058 — Flyway clean-head and V11→head upgrade with application start verification.
 *
 * <p>Expected head for this module IT is the highest migration on the Order Management test
 * classpath (infra + document + security + order-management), currently {@code V34}. Warehouse /
 * Production migrations are intentionally absent here.
 *
 * <p>{@code order_import_metadata} is created in V12 and dropped in V13 (ADR-031); post-head schema
 * must not contain that table.
 */
@Testcontainers
class OrderIntakeFlywayBootstrapIT {

    /** Highest Flyway version on tmp-order-management Failsafe classpath (OM + Security). */
    private static final String CURRENT_CLASSPATH_HEAD = "34";

    private static final String ADMIN_PASSWORD = "bootstrap-secret-value";

    @Test
    void cleanDatabaseMigratesToCurrentHeadAndApplicationStarts() {
        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")) {
            container.start();
            Flyway.configure()
                    .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource(container));
            assertCurrentClasspathHeadSchema(jdbc);

            try (ConfigurableApplicationContext context = startApplication(container)) {
                assertNotNull(context.getBean(AuthenticationService.class));
                assertNotNull(context.getBean(OrderQueryService.class));
                AuthenticationService auth = context.getBean(AuthenticationService.class);
                auth.login(Login.of("admin"), ADMIN_PASSWORD.toCharArray());
                assertTrue(auth.isAuthenticated());
                auth.logout();
            }
        }
    }

    @Test
    void existingV11DatabaseUpgradesToCurrentHeadPreservingDataAndApplicationStarts() {
        try (PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")) {
            container.start();
            Flyway.configure()
                    .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                    .locations("classpath:db/migration")
                    .target("11")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource(container));
            UUID orderId = UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO order_management.orders (
                        order_id, order_number, customer_ref, customer_name, contract_ref, site_ref,
                        responsible_manager, direction, currency, status, version, created_at, updated_at
                    ) VALUES (?, 'V11-KEEP-APP', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', 0,
                              NOW(), NOW())
                    """,
                    orderId);

            try (ConfigurableApplicationContext context = startApplication(container)) {
                assertNotNull(context.getBean(OrderQueryService.class));
                JdbcTemplate after = new JdbcTemplate(dataSource(container));
                assertCurrentClasspathHeadSchema(after);
                assertEquals(
                        1,
                        after.queryForObject(
                                "SELECT COUNT(*) FROM order_management.orders"
                                        + " WHERE order_number = 'V11-KEEP-APP'",
                                Integer.class));
                AuthenticationService auth = context.getBean(AuthenticationService.class);
                auth.login(Login.of("admin"), ADMIN_PASSWORD.toCharArray());
                assertTrue(auth.isAuthenticated());
                auth.logout();
            }
        }
    }

    private static void assertCurrentClasspathHeadSchema(JdbcTemplate jdbc) {
        assertEquals(
                CURRENT_CLASSPATH_HEAD,
                jdbc.queryForObject(
                        "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1",
                        String.class));
        assertEquals(
                0,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'order_management'
                          AND table_name = 'order_import_metadata'
                        """,
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = 'security'
                          AND table_name = 'users'
                          AND column_name = 'password_setup_required'
                        """,
                        Integer.class));
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT COUNT(*) FROM information_schema.tables
                        WHERE table_schema = 'security'
                          AND table_name = 'user_ui_preferences'
                        """,
                        Integer.class));
    }

    private static ConfigurableApplicationContext startApplication(PostgreSQLContainer<?> container) {
        return new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .profiles("test")
                .run(
                        "--spring.datasource.url=" + container.getJdbcUrl(),
                        "--spring.datasource.username=" + container.getUsername(),
                        "--spring.datasource.password=" + container.getPassword(),
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--tmp.security.bootstrap.admin-login=admin",
                        "--tmp.security.bootstrap.admin-display-name=Administrator",
                        "--tmp.security.bootstrap.admin-password=" + ADMIN_PASSWORD);
    }

    private static DataSource dataSource(PostgreSQLContainer<?> container) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl(container.getJdbcUrl());
        dataSource.setUsername(container.getUsername());
        dataSource.setPassword(container.getPassword());
        return dataSource;
    }

    @SpringBootApplication
    @Import({
        com.tmp.infra.db.DatabaseAutoConfiguration.class,
        com.tmp.core.PlatformCoreAutoConfiguration.class,
        com.tmp.document.DocumentEngineAutoConfiguration.class,
        com.tmp.capability.CapabilityEngineAutoConfiguration.class,
        com.tmp.security.SecurityAutoConfiguration.class,
        OrderManagementAutoConfiguration.class
    })
    static class TestApplication {}
}
