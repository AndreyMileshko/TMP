package com.tmp.ui.shell.order.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tmp.security.api.AccessDeniedException;
import org.junit.jupiter.api.Test;

class OrderUiErrorMapperTest {

    @Test
    void mapsAccessDenied() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new AccessDeniedException("secret"), OrderUiOperation.LOAD);
        assertEquals(OrderUiErrorCategory.ACCESS_DENIED, message.category());
        assertEquals(OrderUiErrorMapper.ACCESS_DENIED, message.text());
        assertFalse(message.text().contains("secret"));
    }

    @Test
    void mapsOptimisticLockByClassNameInCauseChain() {
        RuntimeException root =
                new RuntimeException("wrapper", new PayloadOptimisticLockExceptionStub());
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(root, OrderUiOperation.SAVE_DRAFT);
        assertEquals(OrderUiErrorCategory.OPTIMISTIC_LOCK, message.category());
        assertEquals(OrderUiErrorMapper.OPTIMISTIC_LOCK, message.text());
        assertFalse(message.text().toLowerCase().contains("payload"));
    }

    @Test
    void mapsValidation() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new IllegalArgumentException("quantity must be > 0"),
                        OrderUiOperation.VALIDATE);
        assertEquals(OrderUiErrorCategory.VALIDATION, message.category());
        assertEquals(OrderUiErrorMapper.VALIDATION, message.text());
    }

    @Test
    void mapsNotFound() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new OrderItemNotFoundExceptionStub(), OrderUiOperation.LOAD);
        assertEquals(OrderUiErrorCategory.NOT_FOUND, message.category());
        assertEquals(OrderUiErrorMapper.NOT_FOUND, message.text());
    }

    @Test
    void mapsForbiddenTransition() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new InvalidOrderStateExceptionStub(), OrderUiOperation.APPROVE);
        assertEquals(OrderUiErrorCategory.FORBIDDEN_TRANSITION, message.category());
        assertEquals(OrderUiErrorMapper.FORBIDDEN_TRANSITION, message.text());
    }

    @Test
    void mapsAlreadyPosted() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new IllegalStateException(
                                "Operation requires DRAFT status: 11111111-1111-1111-1111-111111111111"),
                        OrderUiOperation.POST_DOCUMENT);
        assertEquals(OrderUiErrorCategory.ALREADY_POSTED, message.category());
        assertEquals(OrderUiErrorMapper.ALREADY_POSTED, message.text());
        assertFalse(message.text().contains("11111111"));
        assertFalse(message.text().contains("DRAFT"));
    }

    @Test
    void mapsUnpostNotSupported() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new UnsupportedOperationException("some unsupported operation"),
                        OrderUiOperation.UNPOST);
        assertEquals(OrderUiErrorCategory.UNPOST_NOT_SUPPORTED, message.category());
        assertEquals(OrderUiErrorMapper.UNPOST_NOT_SUPPORTED, message.text());
        assertFalse(message.text().contains("UNPOST"));
        assertFalse(message.text().contains("some unsupported operation"));
    }

    @Test
    void doesNotMapUnpostNotSupportedWhenUnsupportedOperationOnSaveDraft() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new UnsupportedOperationException("some unsupported operation"),
                        OrderUiOperation.SAVE_DRAFT);
        assertEquals(OrderUiErrorCategory.TECHNICAL_FAILURE, message.category());
        assertEquals(OrderUiErrorMapper.TECHNICAL_FAILURE, message.text());
        assertFalse(message.text().contains("some unsupported operation"));
    }

    @Test
    void mapsUnpostNotSupportedWhenCauseChainContainsUnpostNotSupportedMessage() {
        RuntimeException root =
                new RuntimeException(
                        "wrapper",
                        new RuntimeException(
                                "UNPOST IS NOT SUPPORTED; SQLException: duplicate key"));

        OrderUiUserMessage message = OrderUiErrorMapper.map(root, OrderUiOperation.LOAD);
        assertEquals(OrderUiErrorCategory.UNPOST_NOT_SUPPORTED, message.category());
        assertEquals(OrderUiErrorMapper.UNPOST_NOT_SUPPORTED, message.text());
        // technical details must not reach the UI
        assertFalse(message.text().contains("SQLException"));
        assertFalse(message.text().contains("duplicate key"));
        assertFalse(message.text().contains("UNPOST"));
    }

    @Test
    void mapsUnknownTechnicalErrorWithoutLeakingDetails() {
        OrderUiUserMessage message =
                OrderUiErrorMapper.map(
                        new RuntimeException(
                                "SQLException in com.tmp.order.persistence.JdbcOrderRepository"),
                        OrderUiOperation.LOAD);
        assertEquals(OrderUiErrorCategory.TECHNICAL_FAILURE, message.category());
        assertEquals(OrderUiErrorMapper.TECHNICAL_FAILURE, message.text());
        assertFalse(message.text().contains("SQL"));
        assertFalse(message.text().contains("persistence"));
        assertFalse(message.text().contains("Jdbc"));
        assertFalse(message.text().contains("com.tmp"));
    }

    /** Stub whose simple name matches production optimistic-lock exception naming. */
    private static final class PayloadOptimisticLockExceptionStub extends RuntimeException {
        private PayloadOptimisticLockExceptionStub() {
            super("Payload revision conflict for document");
        }
    }

    private static final class OrderItemNotFoundExceptionStub extends RuntimeException {
        private OrderItemNotFoundExceptionStub() {
            super("Order item not found");
        }
    }

    private static final class InvalidOrderStateExceptionStub extends RuntimeException {
        private InvalidOrderStateExceptionStub() {
            super("Cannot re-approve revision");
        }
    }
}
