package com.tmp.core.config;

import com.tmp.core.api.PlatformConfiguration;
import java.util.Optional;
import org.springframework.core.env.Environment;

public final class SpringPlatformConfiguration implements PlatformConfiguration {

    private final Environment environment;

    public SpringPlatformConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> getString(String key) {
        return Optional.ofNullable(environment.getProperty(key));
    }

    @Override
    public String getString(String key, String defaultValue) {
        return environment.getProperty(key, defaultValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        return environment.getProperty(key, Boolean.class, defaultValue);
    }
}
