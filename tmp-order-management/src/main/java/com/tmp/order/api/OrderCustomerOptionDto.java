package com.tmp.order.api;

/**
 * Distinct customer option for the Orders worklist filter.
 *
 * <p>{@link IdentityKind#REF} is a stable {@code customerRef}. {@link IdentityKind#NAME} is a
 * legacy Order whose {@code customer_ref} is absent but {@code customer_name} is meaningful.
 * {@link IdentityKind#UNASSIGNED} is only for Orders with neither ref nor meaningful name.
 */
public final class OrderCustomerOptionDto {

    public enum IdentityKind {
        REF,
        NAME,
        UNASSIGNED
    }

    private final IdentityKind identityKind;
    private final String customerRef;
    private final String customerName;

    private OrderCustomerOptionDto(IdentityKind identityKind, String customerRef, String customerName) {
        this.identityKind = identityKind;
        this.customerRef = customerRef;
        this.customerName = customerName;
    }

    public static OrderCustomerOptionDto of(String customerRef, String customerName) {
        if (customerRef != null && !customerRef.isBlank()) {
            return new OrderCustomerOptionDto(IdentityKind.REF, customerRef.trim(), customerName);
        }
        if (customerName != null && !customerName.isBlank()) {
            return new OrderCustomerOptionDto(IdentityKind.NAME, null, customerName.trim());
        }
        return unassigned();
    }

    public static OrderCustomerOptionDto legacyName(String customerName) {
        return of(null, customerName);
    }

    public static OrderCustomerOptionDto unassigned() {
        return new OrderCustomerOptionDto(IdentityKind.UNASSIGNED, null, null);
    }

    public IdentityKind identityKind() {
        return identityKind;
    }

    public String customerRef() {
        return customerRef;
    }

    public String customerName() {
        return customerName;
    }

    public boolean isUnassigned() {
        return identityKind == IdentityKind.UNASSIGNED;
    }

    public boolean isLegacyName() {
        return identityKind == IdentityKind.NAME;
    }
}
