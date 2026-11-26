package com.tmp.order.application.imports;

import com.tmp.order.api.imports.SpecificationColorConstraint;
import com.tmp.order.domain.CommercialPlaceholderValidator;

/**
 * Default OM implementation of {@link SpecificationColorConstraint}. Delegates placeholder rules to
 * {@link CommercialPlaceholderValidator} (single source of forbidden values) and enforces the &lt;=
 * {@link SpecificationColorConstraint#MAX_COLOR_LENGTH} length shared with {@code VARCHAR(64)}.
 */
public final class DefaultSpecificationColorConstraint implements SpecificationColorConstraint {

    @Override
    public Result check(String colorValue) {
        if (colorValue == null) {
            return Result.rejected(RejectionReason.BLANK);
        }
        String trimmed = colorValue.trim();
        if (trimmed.isEmpty()) {
            return Result.rejected(RejectionReason.BLANK);
        }
        if (trimmed.length() > MAX_COLOR_LENGTH) {
            return Result.rejected(RejectionReason.TOO_LONG);
        }
        if (CommercialPlaceholderValidator.isForbiddenPlaceholder(trimmed)) {
            return Result.rejected(RejectionReason.PLACEHOLDER);
        }
        return Result.allowed();
    }
}