package com.agentjars.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {

    /** Injected rather than called statically so that packaging runs are reproducible in tests. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
