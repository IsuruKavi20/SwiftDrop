package com.swiftdrop.backend.customer.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class CustomerModuleConfig {

    /** The current time, as a bean so tests can replace it with a fixed clock. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
