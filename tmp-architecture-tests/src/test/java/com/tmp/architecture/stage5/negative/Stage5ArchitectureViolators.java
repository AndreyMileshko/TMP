package com.tmp.architecture.stage5.negative;

import com.tmp.core.api.EventBus;
import com.tmp.document.api.DocumentProcessor;
import com.tmp.order.api.ui.OrderDocumentUiService;
import com.tmp.order.persistence.JdbcCustomerOrderRepository;

/**
 * Test-only architecture violators for Stage 5 negative-rule verification. Never used in production.
 */
public final class Stage5ArchitectureViolators {

    private Stage5ArchitectureViolators() {}

    /** Violates: other capabilities must not depend on {@code com.tmp.order.api.ui}. */
    public static final class OtherCapabilityUsesOrderUiApi {
        private OrderDocumentUiService service;
    }

    /** Violates: processors must not depend on {@link EventBus} directly. */
    public static final class ProcessorUsesEventBus implements DocumentProcessor {
        private final EventBus eventBus;

        public ProcessorUsesEventBus(EventBus eventBus) {
            this.eventBus = eventBus;
        }

        @Override
        public String documentTypeId() {
            return "negative.eventbus";
        }

        @Override
        public void validateCreate(
                com.tmp.document.api.DocumentOperationContext context) {}

        @Override
        public void validateUpdate(
                com.tmp.document.api.DocumentOperationContext context) {}

        @Override
        public void onPost(com.tmp.document.api.DocumentOperationContext context) {
            // Would use eventBus here — field dependency is enough for ArchUnit.
        }

        @Override
        public void onUnpost(com.tmp.document.api.DocumentOperationContext context) {}

        @Override
        public void onClose(com.tmp.document.api.DocumentOperationContext context) {}

        @Override
        public void onDelete(com.tmp.document.api.DocumentOperationContext context) {}
    }

    /** Violates: Order must not import internal Document Engine classes. */
    public static final class OrderUsesInternalDocumentEngine {
        private com.tmp.document.DefaultDocumentEngine engine;
    }

    /** Violates: UI must not depend on Order persistence adapters. */
    public static final class UiUsesOrderPersistence {
        private JdbcCustomerOrderRepository repository;
    }

    /** Violates: Order must not use Jackson for payload. */
    public static final class OrderUsesJackson {
        private com.fasterxml.jackson.databind.ObjectMapper mapper;
    }

    /**
     * Standalone class (not {@link DocumentProcessor}) with non-void {@code onPost} for rule
     * evaluation only.
     */
    public static final class MethodOnPostReturnsString {
        public String onPost(Object context) {
            return "forbidden";
        }
    }
}
