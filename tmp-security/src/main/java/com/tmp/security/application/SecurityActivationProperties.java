package com.tmp.security.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Activation code TTL configuration. Bound from {@code tmp.security.activation.*}.
 */
@ConfigurationProperties(prefix = "tmp.security.activation")
public class SecurityActivationProperties {

    /** Default activation code lifetime. */
    private Duration ttl = Duration.ofHours(24);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl == null ? Duration.ofHours(24) : ttl;
    }
}
