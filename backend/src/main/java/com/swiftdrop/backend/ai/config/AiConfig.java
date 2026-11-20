package com.swiftdrop.backend.ai.config;

import com.swiftdrop.backend.ai.security.AiUsageLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class AiConfig {

    /** One limiter for the whole app, so the counts are shared by every request. */
    @Bean
    AiUsageLimiter aiUsageLimiter(Clock clock) {
        return new AiUsageLimiter(clock);
    }
}
