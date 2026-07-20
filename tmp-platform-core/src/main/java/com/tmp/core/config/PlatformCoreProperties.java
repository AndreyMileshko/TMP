package com.tmp.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tmp.platform")
public class PlatformCoreProperties {

    private String name = "TOP Manufacturing Platform";
    private String version = "0.1.0-SNAPSHOT";

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
