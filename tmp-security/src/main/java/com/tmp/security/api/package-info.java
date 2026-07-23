/**
 * Public API of the TMP Security module.
 *
 * <p>External modules may depend only on types in this package: identity value objects,
 * summary DTOs, service façades ({@link AuthenticationService}, {@link AuthorizationService},
 * {@link UserAdministrationService}, {@link RoleAdministrationService}, {@link AuditQueryService}),
 * and public exceptions. Internal domain, persistence, and application packages must not be
 * referenced from outside {@code tmp-security}.
 *
 * <p>No type in this package exposes password plaintext or password hash.
 */
package com.tmp.security.api;
