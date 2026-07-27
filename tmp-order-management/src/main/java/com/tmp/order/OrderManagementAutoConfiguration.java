package com.tmp.order;

import com.tmp.document.api.DocumentEngine;
import com.tmp.document.api.TransactionalEventPublisher;
import com.tmp.order.api.OrderQueryService;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.application.document.OrderApproveDocumentProcessor;
import com.tmp.order.application.document.OrderCancelDocumentProcessor;
import com.tmp.order.application.document.OrderCreateDocumentProcessor;
import com.tmp.order.application.document.OrderUpdateDocumentProcessor;
import com.tmp.order.application.order.ApproveOrderUseCase;
import com.tmp.order.application.order.CancelOrderUseCase;
import com.tmp.order.application.order.CreateOrderUseCase;
import com.tmp.order.application.order.UpdateOrderUseCase;
import com.tmp.order.application.payload.DraftPayloadApplicationService;
import com.tmp.order.application.payload.OrderDocumentPayloadPort;
import com.tmp.order.application.processing.ProcessingRecordPort;
import com.tmp.order.application.query.DefaultOrderQueryService;
import com.tmp.order.application.query.OrderQueryReadPort;
import com.tmp.order.application.ui.DefaultOrderDocumentUiService;
import com.tmp.order.capability.OrderManagementCapability;
import com.tmp.order.domain.repository.CustomerOrderRepository;
import com.tmp.order.domain.repository.OrderItemRepository;
import com.tmp.order.persistence.JdbcCustomerOrderRepository;
import com.tmp.order.persistence.JdbcOrderDocumentPayloadAdapter;
import com.tmp.order.persistence.JdbcOrderItemRepository;
import com.tmp.order.persistence.JdbcOrderQueryReadAdapter;
import com.tmp.order.persistence.JdbcProcessingRecordAdapter;
import com.tmp.security.api.AuthorizationService;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registers Order Management Public Query API beans and order-level document UI flow wiring.
 * Depends on JDBC, Document Engine, and the public Security {@link AuthorizationService}.
 *
 * <p>Item / revision document processors are intentionally not registered here (STAGE5-039).
 */
@AutoConfiguration
@AutoConfigureAfter(
        name = {
            "org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration",
            "com.tmp.document.DocumentEngineAutoConfiguration",
            "com.tmp.security.SecurityAutoConfiguration"
        })
public class OrderManagementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.systemUTC();
    }

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

    @Bean
    OrderDocumentPayloadPort orderDocumentPayloadPort(JdbcTemplate jdbcTemplate) {
        return new JdbcOrderDocumentPayloadAdapter(jdbcTemplate);
    }

    @Bean
    ProcessingRecordPort processingRecordPort(JdbcTemplate jdbcTemplate) {
        return new JdbcProcessingRecordAdapter(jdbcTemplate);
    }

    @Bean
    CustomerOrderRepository customerOrderRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcCustomerOrderRepository(jdbcTemplate);
    }

    @Bean
    OrderItemRepository orderItemRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcOrderItemRepository(jdbcTemplate);
    }

    @Bean
    DraftPayloadApplicationService draftPayloadApplicationService(
            DocumentEngine documentEngine, OrderDocumentPayloadPort orderDocumentPayloadPort) {
        return new DraftPayloadApplicationService(documentEngine, orderDocumentPayloadPort);
    }

    @Bean
    CreateOrderUseCase createOrderUseCase(
            CustomerOrderRepository customerOrderRepository, Clock clock) {
        return new CreateOrderUseCase(customerOrderRepository, clock);
    }

    @Bean
    UpdateOrderUseCase updateOrderUseCase(
            CustomerOrderRepository customerOrderRepository, Clock clock) {
        return new UpdateOrderUseCase(customerOrderRepository, clock);
    }

    @Bean
    ApproveOrderUseCase approveOrderUseCase(
            CustomerOrderRepository customerOrderRepository,
            OrderItemRepository orderItemRepository,
            Clock clock) {
        return new ApproveOrderUseCase(customerOrderRepository, orderItemRepository, clock);
    }

    @Bean
    CancelOrderUseCase cancelOrderUseCase(
            CustomerOrderRepository customerOrderRepository, Clock clock) {
        return new CancelOrderUseCase(customerOrderRepository, clock);
    }

    @Bean
    OrderCreateDocumentProcessor orderCreateDocumentProcessor(
            OrderDocumentPayloadPort orderDocumentPayloadPort,
            ProcessingRecordPort processingRecordPort,
            @Qualifier("transactionalEventPublisher")
                    TransactionalEventPublisher transactionalEventPublisher,
            CreateOrderUseCase createOrderUseCase,
            Clock clock) {
        return new OrderCreateDocumentProcessor(
                orderDocumentPayloadPort,
                processingRecordPort,
                transactionalEventPublisher,
                createOrderUseCase,
                clock);
    }

    @Bean
    OrderUpdateDocumentProcessor orderUpdateDocumentProcessor(
            OrderDocumentPayloadPort orderDocumentPayloadPort,
            ProcessingRecordPort processingRecordPort,
            @Qualifier("transactionalEventPublisher")
                    TransactionalEventPublisher transactionalEventPublisher,
            UpdateOrderUseCase updateOrderUseCase,
            Clock clock) {
        return new OrderUpdateDocumentProcessor(
                orderDocumentPayloadPort,
                processingRecordPort,
                transactionalEventPublisher,
                updateOrderUseCase,
                clock);
    }

    @Bean
    OrderApproveDocumentProcessor orderApproveDocumentProcessor(
            OrderDocumentPayloadPort orderDocumentPayloadPort,
            ProcessingRecordPort processingRecordPort,
            @Qualifier("transactionalEventPublisher")
                    TransactionalEventPublisher transactionalEventPublisher,
            ApproveOrderUseCase approveOrderUseCase,
            Clock clock) {
        return new OrderApproveDocumentProcessor(
                orderDocumentPayloadPort,
                processingRecordPort,
                transactionalEventPublisher,
                approveOrderUseCase,
                clock);
    }

    @Bean
    OrderCancelDocumentProcessor orderCancelDocumentProcessor(
            OrderDocumentPayloadPort orderDocumentPayloadPort,
            ProcessingRecordPort processingRecordPort,
            @Qualifier("transactionalEventPublisher")
                    TransactionalEventPublisher transactionalEventPublisher,
            CancelOrderUseCase cancelOrderUseCase,
            Clock clock) {
        return new OrderCancelDocumentProcessor(
                orderDocumentPayloadPort,
                processingRecordPort,
                transactionalEventPublisher,
                cancelOrderUseCase,
                clock);
    }

    @Bean
    OrderDocumentUiService defaultOrderDocumentUiService(
            DocumentEngine documentEngine,
            DraftPayloadApplicationService draftPayloadApplicationService,
            OrderQueryService orderQueryService,
            ProcessingRecordPort processingRecordPort,
            AuthorizationService authorizationService,
            Clock clock) {
        return new DefaultOrderDocumentUiService(
                documentEngine,
                draftPayloadApplicationService,
                orderQueryService,
                processingRecordPort,
                authorizationService,
                clock);
    }

    @Bean
    OrderDocumentProcessorRegistrar orderDocumentProcessorRegistrar(
            DocumentEngine documentEngine,
            OrderCreateDocumentProcessor orderCreateDocumentProcessor,
            OrderUpdateDocumentProcessor orderUpdateDocumentProcessor,
            OrderApproveDocumentProcessor orderApproveDocumentProcessor,
            OrderCancelDocumentProcessor orderCancelDocumentProcessor) {
        return new OrderDocumentProcessorRegistrar(
                documentEngine,
                orderCreateDocumentProcessor,
                orderUpdateDocumentProcessor,
                orderApproveDocumentProcessor,
                orderCancelDocumentProcessor);
    }

    /**
     * Registers the four order-level document processors on the Document Engine at startup.
     */
    static final class OrderDocumentProcessorRegistrar {

        private final DocumentEngine documentEngine;
        private final OrderCreateDocumentProcessor createProcessor;
        private final OrderUpdateDocumentProcessor updateProcessor;
        private final OrderApproveDocumentProcessor approveProcessor;
        private final OrderCancelDocumentProcessor cancelProcessor;

        OrderDocumentProcessorRegistrar(
                DocumentEngine documentEngine,
                OrderCreateDocumentProcessor createProcessor,
                OrderUpdateDocumentProcessor updateProcessor,
                OrderApproveDocumentProcessor approveProcessor,
                OrderCancelDocumentProcessor cancelProcessor) {
            this.documentEngine = Objects.requireNonNull(documentEngine, "documentEngine");
            this.createProcessor = Objects.requireNonNull(createProcessor, "createProcessor");
            this.updateProcessor = Objects.requireNonNull(updateProcessor, "updateProcessor");
            this.approveProcessor = Objects.requireNonNull(approveProcessor, "approveProcessor");
            this.cancelProcessor = Objects.requireNonNull(cancelProcessor, "cancelProcessor");
        }

        @PostConstruct
        void register() {
            createProcessor.register(documentEngine);
            updateProcessor.register(documentEngine);
            approveProcessor.register(documentEngine);
            cancelProcessor.register(documentEngine);
        }
    }
}
