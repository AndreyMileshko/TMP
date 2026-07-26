package com.tmp.order;

import com.tmp.order.api.OrderQueryService;
import com.tmp.order.application.query.DefaultOrderQueryService;
import com.tmp.order.application.query.OrderQueryReadPort;
import com.tmp.order.capability.OrderManagementCapability;
import com.tmp.order.persistence.JdbcOrderQueryReadAdapter;
import com.tmp.security.api.AuthorizationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers Order Management Public Query API beans. Depends on JDBC and the public Security
 * {@link AuthorizationService}.
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "com.tmp.security.SecurityAutoConfiguration"
        },
        value = JdbcTemplateAutoConfiguration.class)
public class OrderManagementAutoConfiguration {

    @Bean
    OrderQueryReadPort orderQueryReadPort(JdbcTemplate jdbcTemplate) {
        return new JdbcOrderQueryReadAdapter(jdbcTemplate);
    }

    @Bean
    OrderQueryService orderQueryService(
            OrderQueryReadPort orderQueryReadPort, AuthorizationService authorizationService) {
        return new DefaultOrderQueryService(orderQueryReadPort, authorizationService);
    }

    @Bean
    OrderManagementCapability orderManagementCapability() {
        return new OrderManagementCapability();
    }
}
