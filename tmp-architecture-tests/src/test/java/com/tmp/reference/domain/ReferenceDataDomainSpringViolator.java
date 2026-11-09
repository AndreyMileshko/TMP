package com.tmp.reference.domain;

import org.springframework.stereotype.Component;

/**
 * Test-only architecture violator for COL-010 negative verification.
 *
 * <p>Intentionally depends on Spring from the Reference Data domain package. Never used in
 * production.
 */
@Component
public final class ReferenceDataDomainSpringViolator {

    private ReferenceDataDomainSpringViolator() {}
}
