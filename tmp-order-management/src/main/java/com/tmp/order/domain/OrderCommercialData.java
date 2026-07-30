package com.tmp.order.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Commercial header fields of a customer order editable only while the order is {@code DRAFT}
 * (Specification §5.1 / §8). ADR-030: mandatory fields may be absent in DRAFT; approval requires
 * them to be filled without placeholder values.
 */
public final class OrderCommercialData {

    private final String customerRef;
    private final String customerName;
    private final String contractRef;
    private final String siteRef;
    private final String responsibleManager;
    private final OrderDirection direction;
    private final CurrencyCode currency;

    private OrderCommercialData(
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            OrderDirection direction,
            CurrencyCode currency) {
        this.customerRef = customerRef;
        this.customerName = customerName;
        this.contractRef = contractRef;
        this.siteRef = siteRef;
        this.responsibleManager = responsibleManager;
        this.direction = direction;
        this.currency = currency;
    }

    /**
     * Creates commercial data for a DRAFT order. Mandatory approval fields may be absent; any
     * provided value must not be a prohibited placeholder (ADR-030).
     */
    public static OrderCommercialData of(
            String customerRef,
            String customerName,
            String contractRef,
            String siteRef,
            String responsibleManager,
            OrderDirection direction,
            CurrencyCode currency) {
        String normalizedCustomerRef = CommercialPlaceholderValidator.normalizeOptional(customerRef);
        String normalizedCustomerName =
                CommercialPlaceholderValidator.normalizeOptional(customerName);
        String normalizedContractRef = CommercialPlaceholderValidator.normalizeOptional(contractRef);
        String normalizedSiteRef = CommercialPlaceholderValidator.normalizeOptional(siteRef);
        String normalizedManager =
                CommercialPlaceholderValidator.normalizeOptional(responsibleManager);

        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(normalizedCustomerRef, "customerRef");
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(
                normalizedCustomerName, "customerName");
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(
                normalizedContractRef, "contractRef");
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(normalizedSiteRef, "siteRef");
        CommercialPlaceholderValidator.rejectPlaceholderIfPresent(
                normalizedManager, "responsibleManager");

        return new OrderCommercialData(
                normalizedCustomerRef,
                normalizedCustomerName,
                normalizedContractRef,
                normalizedSiteRef,
                normalizedManager,
                direction,
                currency);
    }

    /**
     * Returns stable field names of mandatory commercial attributes missing for approval (ADR-030).
     */
    public List<String> missingMandatoryFieldsForApproval() {
        List<String> missing = new ArrayList<>();
        if (customerName == null) {
            missing.add("customerName");
        }
        if (direction == null) {
            missing.add("direction");
        }
        if (currency == null) {
            missing.add("currency");
        }
        if (contractRef == null) {
            missing.add("contractRef");
        }
        if (siteRef == null) {
            missing.add("siteRef");
        }
        return Collections.unmodifiableList(missing);
    }

    public boolean isCompleteForApproval() {
        return missingMandatoryFieldsForApproval().isEmpty();
    }

    public String customerRef() {
        return customerRef;
    }

    public String customerName() {
        return customerName;
    }

    public String contractRef() {
        return contractRef;
    }

    public String siteRef() {
        return siteRef;
    }

    public String responsibleManager() {
        return responsibleManager;
    }

    public OrderDirection direction() {
        return direction;
    }

    public CurrencyCode currency() {
        return currency;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof OrderCommercialData that)) {
            return false;
        }
        return Objects.equals(customerRef, that.customerRef)
                && Objects.equals(customerName, that.customerName)
                && Objects.equals(contractRef, that.contractRef)
                && Objects.equals(siteRef, that.siteRef)
                && Objects.equals(responsibleManager, that.responsibleManager)
                && direction == that.direction
                && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                customerRef,
                customerName,
                contractRef,
                siteRef,
                responsibleManager,
                direction,
                currency);
    }
}
