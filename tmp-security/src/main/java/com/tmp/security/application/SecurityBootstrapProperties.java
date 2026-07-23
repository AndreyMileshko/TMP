package com.tmp.security.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External bootstrap administrator credentials. Bound from {@code TMP_SECURITY_BOOTSTRAP_*}
 * environment variables via Spring relaxed binding. No defaults — missing values fail fast.
 */
@ConfigurationProperties(prefix = "tmp.security.bootstrap")
public class SecurityBootstrapProperties {

    private String adminLogin;
    private String adminDisplayName;
    private String adminPassword;

    public String getAdminLogin() {
        return adminLogin;
    }

    public void setAdminLogin(String adminLogin) {
        this.adminLogin = adminLogin;
    }

    public String getAdminDisplayName() {
        return adminDisplayName;
    }

    public void setAdminDisplayName(String adminDisplayName) {
        this.adminDisplayName = adminDisplayName;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }

    public boolean isComplete() {
        return isPresent(adminLogin) && isPresent(adminDisplayName) && isPresent(adminPassword);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
