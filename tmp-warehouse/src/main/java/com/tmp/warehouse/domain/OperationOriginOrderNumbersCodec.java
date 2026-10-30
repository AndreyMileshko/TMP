package com.tmp.warehouse.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact JSON-array codec for persisted {@link OperationOrigin#orderNumbers()} ({@code TEXT}
 * column). Does not depend on Jackson.
 */
public final class OperationOriginOrderNumbersCodec {

    private OperationOriginOrderNumbersCodec() {}

    public static String encode(List<String> orderNumbers) {
        if (orderNumbers == null || orderNumbers.isEmpty()) {
            throw new IllegalArgumentException("orderNumbers must not be empty");
        }
        StringBuilder sb = new StringBuilder(orderNumbers.size() * 16);
        sb.append('[');
        for (int i = 0; i < orderNumbers.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(escape(orderNumbers.get(i))).append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    public static List<String> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        String text = encoded.trim();
        if (text.length() < 2 || text.charAt(0) != '[' || text.charAt(text.length() - 1) != ']') {
            throw new IllegalArgumentException("Invalid origin_order_numbers JSON array: " + encoded);
        }
        String body = text.substring(1, text.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        int i = 0;
        while (i < body.length()) {
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i >= body.length()) {
                break;
            }
            if (body.charAt(i) != '"') {
                throw new IllegalArgumentException(
                        "Invalid origin_order_numbers JSON array: " + encoded);
            }
            i++;
            StringBuilder value = new StringBuilder();
            while (i < body.length()) {
                char c = body.charAt(i++);
                if (c == '\\') {
                    if (i >= body.length()) {
                        throw new IllegalArgumentException(
                                "Invalid origin_order_numbers escape: " + encoded);
                    }
                    value.append(body.charAt(i++));
                } else if (c == '"') {
                    break;
                } else {
                    value.append(c);
                }
            }
            values.add(value.toString());
            while (i < body.length() && Character.isWhitespace(body.charAt(i))) {
                i++;
            }
            if (i < body.length()) {
                if (body.charAt(i) != ',') {
                    throw new IllegalArgumentException(
                            "Invalid origin_order_numbers JSON array: " + encoded);
                }
                i++;
            }
        }
        return List.copyOf(values);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
