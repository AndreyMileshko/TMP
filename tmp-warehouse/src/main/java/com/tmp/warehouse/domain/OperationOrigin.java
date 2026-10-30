package com.tmp.warehouse.domain;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Opaque business-document origin metadata persisted with a Warehouse operation.
 *
 * <p>Warehouse stores and projects this data; it does not validate the referenced document or
 * compute order attribution. Origin never participates in stock accounting.
 */
public final class OperationOrigin {

    public static final int DOCUMENT_TYPE_MAX_LENGTH = 64;
    public static final int DOCUMENT_NUMBER_MAX_LENGTH = 128;
    public static final int ORDER_NUMBER_MAX_LENGTH = 128;
    public static final int MAX_ORDER_NUMBERS = 32;

    private final String documentType;
    private final UUID documentId;
    private final String documentNumber;
    private final List<String> orderNumbers;

    private OperationOrigin(
            String documentType, UUID documentId, String documentNumber, List<String> orderNumbers) {
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentNumber = documentNumber;
        this.orderNumbers = orderNumbers;
    }

    public static OperationOrigin of(
            String documentType,
            UUID documentId,
            String documentNumber,
            List<String> orderNumbers) {
        Objects.requireNonNull(documentType, "documentType");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(documentNumber, "documentNumber");
        Objects.requireNonNull(orderNumbers, "orderNumbers");
        String type = documentType.trim();
        String number = documentNumber.trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("documentType must not be blank");
        }
        if (type.length() > DOCUMENT_TYPE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "documentType must be at most " + DOCUMENT_TYPE_MAX_LENGTH + " characters");
        }
        if (number.isEmpty()) {
            throw new IllegalArgumentException("documentNumber must not be blank");
        }
        if (number.length() > DOCUMENT_NUMBER_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "documentNumber must be at most "
                            + DOCUMENT_NUMBER_MAX_LENGTH
                            + " characters");
        }
        if (orderNumbers.isEmpty()) {
            throw new IllegalArgumentException("orderNumbers must not be empty");
        }
        if (orderNumbers.size() > MAX_ORDER_NUMBERS) {
            throw new IllegalArgumentException(
                    "orderNumbers must contain at most " + MAX_ORDER_NUMBERS + " entries");
        }
        List<String> normalized = new ArrayList<>(orderNumbers.size());
        for (String raw : orderNumbers) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("orderNumbers entries must not be blank");
            }
            String trimmed = raw.trim();
            if (trimmed.length() > ORDER_NUMBER_MAX_LENGTH) {
                throw new IllegalArgumentException(
                        "order number must be at most "
                                + ORDER_NUMBER_MAX_LENGTH
                                + " characters");
            }
            if (!normalized.contains(trimmed)) {
                normalized.add(trimmed);
            }
        }
        return new OperationOrigin(type, documentId, number, List.copyOf(normalized));
    }

    public String documentType() {
        return documentType;
    }

    public UUID documentId() {
        return documentId;
    }

    public String documentNumber() {
        return documentNumber;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "orderNumbers is an unmodifiable List.copyOf snapshot.")
    public List<String> orderNumbers() {
        return orderNumbers;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OperationOrigin that)) {
            return false;
        }
        return documentType.equals(that.documentType)
                && documentId.equals(that.documentId)
                && documentNumber.equals(that.documentNumber)
                && orderNumbers.equals(that.orderNumbers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentType, documentId, documentNumber, orderNumbers);
    }

    @Override
    public String toString() {
        return "OperationOrigin{type="
                + documentType
                + ", id="
                + documentId
                + ", number="
                + documentNumber
                + ", orders="
                + orderNumbers
                + '}';
    }
}