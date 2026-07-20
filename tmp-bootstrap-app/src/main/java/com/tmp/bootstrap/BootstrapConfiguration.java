package com.tmp.bootstrap;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }
}
