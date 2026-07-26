package com.tmp.order;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tmp.order.api.OrderId;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.OrderSearchCriteria;
import com.tmp.order.api.OrderSummaryDto;
import com.tmp.order.api.PageRequest;
import com.tmp.order.api.PageResult;
import com.tmp.order.application.query.OrderQueryReadPort;
import com.tmp.order.capability.OrderManagementCapability;
import com.tmp.order.capability.OrderManagementPermissions;
import com.tmp.security.api.AccessDeniedException;
import com.tmp.security.api.AuthenticationService;
import com.tmp.security.api.DisplayName;
import com.tmp.security.api.Login;
import com.tmp.security.api.RoleAdministrationService;
import com.tmp.security.api.UserAdministrationService;
import com.tmp.security.api.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = OrderManagementAutoConfigurationTest.TestApplication.class)
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tmp.security.bootstrap.admin-login=admin",
            "tmp.security.bootstrap.admin-display-name=Administrator",
            "tmp.security.bootstrap.admin-password=bootstrap-secret-value"
        })
class OrderManagementAutoConfigurationTest {

    private static final char[] VIEWER_PASSWORD = "viewer-secret-value".toCharArray();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private OrderQueryService orderQueryService;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private UserAdministrationService userAdministrationService;

    @Autowired
    private RoleAdministrationService roleAdministrationService;

    @BeforeEach
    void clearSession() {
        authenticationService.logout();
    }

    @Test
    void queryApiBeansAreCreatedExactlyOnce() {
        assertEquals(1, applicationContext.getBeansOfType(OrderQueryService.class).size());
        assertEquals(1, applicationContext.getBeansOfType(OrderQueryReadPort.class).size());
        assertEquals(1, applicationContext.getBeansOfType(OrderManagementCapability.class).size());
        assertNotNull(orderQueryService);
    }

    @Test
    void queryWithoutPermissionIsRejectedEvenAfterAdminLogin() {
        authenticationService.login(Login.of("admin"), "bootstrap-secret-value".toCharArray());
        assertThrows(
                AccessDeniedException.class,
                () ->
                        orderQueryService.searchOrders(
                                OrderSearchCriteria.empty(), PageRequest.firstPage()));
        assertThrows(
                AccessDeniedException.class, () -> orderQueryService.getOrder(OrderId.generate()));
    }

    @Test
    void searchOrdersSucceedsAfterOrderViewPermissionGrantedViaPublicSecurityApi() {
        authenticationService.login(Login.of("admin"), "bootstrap-secret-value".toCharArray());
        UserSummary viewer = userAdministrationService.createUser(
                Login.of("orderviewer"), DisplayName.of("Order Viewer"), VIEWER_PASSWORD.clone());
        roleAdministrationService.grantIndividualPermission(
                viewer.id(), OrderManagementPermissions.ORDER_VIEW);
        authenticationService.logout();

        authenticationService.login(Login.of("orderviewer"), VIEWER_PASSWORD.clone());
        OrderQueryService fromContext = applicationContext.getBean(OrderQueryService.class);
        PageResult<OrderSummaryDto> page =
                assertDoesNotThrow(
                        () ->
                                fromContext.searchOrders(
                                        OrderSearchCriteria.empty(), PageRequest.firstPage()));
        assertNotNull(page);
        assertEquals(0, page.pageIndex());
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
