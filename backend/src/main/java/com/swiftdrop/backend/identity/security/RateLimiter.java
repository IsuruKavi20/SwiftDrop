package com.swiftdrop.backend.identity.security;

import java.time.Clock;

/**
 * A small token bucket: at most {@code perSecond} requests per second on average, short bursts up to the same number.
 * Per server instance (each pod has its own), which is enough to stop a flood of junk at the event endpoint.
 */
public final class RateLimiter {

    private final int perSecond;
    private final Clock clock;
    private double tokens;
    private long lastRefillMillis;

    public RateLimiter(int perSecond, Clock clock) {
        this.perSecond = perSecond;
        this.clock = clock;
        this.tokens = perSecond;
        this.lastRefillMillis = clock.millis();
    }

    public synchronized boolean tryAcquire() {
        long now = clock.millis();
        tokens = Math.min(perSecond, tokens + (now - lastRefillMillis) * perSecond / 1000.0);
        lastRefillMillis = now;
        if (tokens < 1) {
            return false;
        }
        tokens -= 1;
        return true;
    }
}
