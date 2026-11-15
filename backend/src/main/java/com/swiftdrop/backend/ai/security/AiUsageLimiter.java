package com.swiftdrop.backend.ai.security;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limits how much the AI assistant can be used, because every message costs Gemini quota.
 *
 * <ul>
 *   <li>Guests (counted by IP address): {@value #GUEST_PER_MINUTE} messages a minute, {@value #GUEST_PER_DAY} a day.</li>
 *   <li>Logged-in customers (counted by account): {@value #USER_PER_MINUTE} a minute, {@value #USER_PER_DAY} a day.</li>
 *   <li>The whole site: {@value #SITE_PER_DAY} messages a day (one message can make two Gemini calls), a safety net
 *       for the bill.</li>
 * </ul>
 *
 * Counts live in memory on this server (one backend pod), reset at the next minute / at midnight Sri Lanka time.
 */
public class AiUsageLimiter {

    static final int GUEST_PER_MINUTE = 5;
    static final int GUEST_PER_DAY = 30;
    static final int USER_PER_MINUTE = 10;
    static final int USER_PER_DAY = 100;
    static final int SITE_PER_DAY = 250;
    private static final ZoneId SRI_LANKA = ZoneId.of("Asia/Colombo");
    private static final int CLEANUP_ABOVE = 10_000;

    /** Why a message was refused; null when it is allowed. */
    public enum Refusal { TOO_FAST, DAILY_LIMIT, SITE_BUSY }

    private final Clock clock;
    private final Map<String, Window> minuteCounts = new ConcurrentHashMap<>();
    private final Map<String, Window> dayCounts = new ConcurrentHashMap<>();
    private Window siteDay = new Window(-1, 0);

    public AiUsageLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Counts one message for this caller if all limits allow it.
     *
     * @param key "user:&lt;id&gt;" for a logged-in customer, "ip:&lt;address&gt;" for a guest
     * @return null if allowed (and counted), otherwise the limit that was hit (nothing counted)
     */
    public synchronized Refusal tryUse(String key, boolean loggedIn) {
        long minute = clock.millis() / 60_000;
        long day = LocalDate.now(clock.withZone(SRI_LANKA)).toEpochDay();
        cleanUpIfLarge(minute, day);

        Window perMinute = current(minuteCounts, key, minute);
        Window perDay = current(dayCounts, key, day);
        if (siteDay.period != day) {
            siteDay = new Window(day, 0);
        }

        if (perMinute.count >= (loggedIn ? USER_PER_MINUTE : GUEST_PER_MINUTE)) {
            return Refusal.TOO_FAST;
        }
        if (perDay.count >= (loggedIn ? USER_PER_DAY : GUEST_PER_DAY)) {
            return Refusal.DAILY_LIMIT;
        }
        if (siteDay.count >= SITE_PER_DAY) {
            return Refusal.SITE_BUSY;
        }
        minuteCounts.put(key, perMinute.plusOne());
        dayCounts.put(key, perDay.plusOne());
        siteDay = siteDay.plusOne();
        return null;
    }

    private static Window current(Map<String, Window> counts, String key, long period) {
        Window window = counts.get(key);
        return window == null || window.period != period ? new Window(period, 0) : window;
    }

    // Old windows are useless once their minute/day is over; drop them so memory stays small
    private void cleanUpIfLarge(long minute, long day) {
        if (minuteCounts.size() > CLEANUP_ABOVE) {
            minuteCounts.values().removeIf(window -> window.period != minute);
        }
        if (dayCounts.size() > CLEANUP_ABOVE) {
            dayCounts.values().removeIf(window -> window.period != day);
        }
    }

    /** A count inside one period (a minute number or a day number). */
    private record Window(long period, int count) {
        Window plusOne() {
            return new Window(period, count + 1);
        }
    }
}
