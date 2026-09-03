package com.tmp.order.api;

/**
 * Distinct customer option for the Orders worklist filter.
 *
 * <p>Both {@code customerRef} and {@code customerName} may be {@code null} for incomplete DRAFT
 * orders without a customer. {@code customerRef} is the stable selection key when present.
 */
public final class OrderCustomerOptionDto {

    private final String customerRef;
    private final String customerName;

    private OrderCustomerOptionDto(String customerRef, String customerName) {
        this.customerRef = customerRef;
        this.customerName = customerName;
    }

    public static OrderCustomerOptionDto of(String customerRef, String customerName) {
        return new OrderCustomerOptionDto(customerRef, customerName);
    }

    public static OrderCustomerOptionDto unassigned() {
        return new OrderCustomerOptionDto(null, null);
    }

    public String customerRef() {
        return customerRef;
    }

    public String customerName() {
        return customerName;
    }

    public boolean isUnassigned() {
        return customerRef == null;
    }
}
