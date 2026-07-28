package com.tmp.order.domain;

import org.springframework.stereotype.Component;

/**
 * Test-only architecture violator for Stage 5.11A negative verification.
 *
 * <p>Intentionally depends on Spring from the Order Management domain package. Never used in
 * production.
 */
@Component
public final class DomainDependsOnSpringViolator {

    private DomainDependsOnSpringViolator() {}
}
