package com.swiftdrop.backend.ai.security;

import com.swiftdrop.backend.ai.security.AiUsageLimiter.Refusal;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AiUsageLimiterTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-11-15T04:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }

    private final TestClock clock = new TestClock();
    private final AiUsageLimiter limiter = new AiUsageLimiter(clock);

    private Refusal useTimes(String key, boolean loggedIn, int times) {
        Refusal last = null;
        for (int i = 0; i < times; i++) {
            last = limiter.tryUse(key, loggedIn);
        }
        return last;
    }

    @Test
    void guestGetsFiveMessagesAMinuteThenMustWait() {
        assertThat(useTimes("ip:1.2.3.4", false, AiUsageLimiter.GUEST_PER_MINUTE)).isNull();
        assertThat(limiter.tryUse("ip:1.2.3.4", false)).isEqualTo(Refusal.TOO_FAST);

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryUse("ip:1.2.3.4", false)).isNull();
    }

    @Test
    void oneGuestHittingTheLimitDoesNotBlockAnother() {
        useTimes("ip:1.2.3.4", false, AiUsageLimiter.GUEST_PER_MINUTE + 1);

        assertThat(limiter.tryUse("ip:5.6.7.8", false)).isNull();
    }

    @Test
    void guestDailyLimitHoldsUntilTheNextDay() {
        for (int i = 0; i < AiUsageLimiter.GUEST_PER_DAY; i++) {
            assertThat(limiter.tryUse("ip:1.2.3.4", false)).isNull();
            clock.advance(Duration.ofMinutes(1));
        }
        assertThat(limiter.tryUse("ip:1.2.3.4", false)).isEqualTo(Refusal.DAILY_LIMIT);

        clock.advance(Duration.ofDays(1));
        assertThat(limiter.tryUse("ip:1.2.3.4", false)).isNull();
    }

    @Test
    void loggedInCustomerGetsTheHigherLimit() {
        assertThat(useTimes("user:abc", true, AiUsageLimiter.USER_PER_MINUTE)).isNull();
        assertThat(limiter.tryUse("user:abc", true)).isEqualTo(Refusal.TOO_FAST);
    }

    @Test
    void wholeSiteStopsAtTheDailySafetyNet() {
        // Many different guests, each well under their own limit
        for (int i = 0; i < AiUsageLimiter.SITE_PER_DAY; i++) {
            assertThat(limiter.tryUse("ip:10.0." + (i / 200) + "." + (i % 200), false)).isNull();
        }
        assertThat(limiter.tryUse("ip:99.99.99.99", false)).isEqualTo(Refusal.SITE_BUSY);
    }
}
