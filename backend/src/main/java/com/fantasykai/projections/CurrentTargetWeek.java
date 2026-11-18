package com.fantasykai.projections;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@link TargetWeekResolver} for a request path: the same rule the job uses, asked at most once
 * per time-to-live.
 *
 * <p>The resolver's query walks the primary-key index of {@code player_game_stats} once per
 * candidate game, because the table has no index on {@code game_id} and must not get one
 * before Phase 11 (architecture section 9). EXPLAIN ANALYZE on the 2026-11-18 local mirror
 * (122,755 stat rows, 123 candidate games, 108,270 buffer hits): 102 to 108 ms warm, 143 ms on the
 * next run and 196 ms on the first, which still read 11 pages from disk
 * ({@code docs/perf/target-week-resolver-2026-11-18.md}). Not measured on production. The cost
 * is per candidate game (about 0.85 ms each), so it is larger early in the season: roughly twice
 * as much in week 1, an extrapolation. That is right once
 * a day inside the job and wrong on every request, so requests go through here. One resolution
 * answers every member for {@code ttl}; a week rolls over at most one {@code ttl} after the
 * ingest lands its last stat row, which a board that changes once a day does not notice.
 *
 * <p>One slot, keyed by season. An entry is reused by any caller whose clock reads within
 * {@code ttl} of when it was computed, in either direction: concurrent requests read the clock
 * before they reach the lock, so one that arrives a few microseconds "early" must share the
 * answer rather than ask again. A clock that jumps by {@code ttl} or more either way recomputes.
 * {@code ttl} of zero asks the resolver every time, which is what a test that changes the
 * schedule between calls needs.
 */
@Component
public class CurrentTargetWeek {

    private record Entry(int season, Instant computedAt, Optional<TargetWeek> week) {}

    private final TargetWeekResolver resolver;
    private final Duration ttl;
    private final Object refresh = new Object();
    private volatile Entry entry;

    /** {@code ttlSeconds} has no default here: its one default is in {@code application.yml}. */
    public CurrentTargetWeek(TargetWeekResolver resolver,
            @Value("${fantasykai.projections.target-cache-seconds}") long ttlSeconds) {
        if (ttlSeconds < 0) {
            throw new IllegalArgumentException("fantasykai.projections.target-cache-seconds is "
                    + ttlSeconds + "; zero turns the cache off, it cannot be negative");
        }
        this.resolver = resolver;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /** The week to project in {@code season} as of {@code now}; empty when there is none. */
    public Optional<TargetWeek> resolve(int season, Instant now) {
        if (ttl.isZero()) {
            return resolver.resolve(season, now);
        }
        Entry current = entry;
        if (fresh(current, season, now)) {
            return current.week();
        }
        synchronized (refresh) {
            current = entry;
            if (fresh(current, season, now)) {
                return current.week();
            }
            Optional<TargetWeek> week = resolver.resolve(season, now);
            entry = new Entry(season, now, week);
            return week;
        }
    }

    private boolean fresh(Entry candidate, int season, Instant now) {
        return candidate != null && candidate.season() == season
                && Duration.between(candidate.computedAt(), now).abs().compareTo(ttl) < 0;
    }
}
