package com.tmp.ui.shell.order.error;

import java.util.Objects;

/** Safe user-facing message produced by {@link OrderUiErrorMapper}. */
public final class OrderUiUserMessage {

    private final OrderUiErrorCategory category;
    private final String text;

    private OrderUiUserMessage(OrderUiErrorCategory category, String text) {
        this.category = Objects.requireNonNull(category, "category");
        this.text = Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }

    public static OrderUiUserMessage of(OrderUiErrorCategory category, String text) {
        return new OrderUiUserMessage(category, text);
    }

    public OrderUiErrorCategory category() {
        return category;
    }

    public String text() {
        return text;
    }
}
