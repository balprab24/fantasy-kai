package com.fantasykai.projections;

import java.time.Clock;
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
 * (architecture section 9). Measured on the 2026-11-18 local mirror (122,755 stat rows, 123
 * candidate games): 196 ms and 108,259 buffer hits per call. That is right once a day inside the
 * job and wrong on every request, so requests go through here. One resolution answers every
 * member for {@code ttl}; a week rolls over at most one {@code ttl} after the ingest lands its
 * last stat row, which a board that changes once a day does not notice.
 *
 * <p>Keyed by season, and by the caller's clock: an entry from the future, or older than
 * {@code ttl}, is recomputed. {@code ttl} of zero asks the resolver every time, which is what a
 * test that changes the schedule between calls needs.
 */
@Component
public class CurrentTargetWeek {

    private record Entry(int season, Instant computedAt, Optional<TargetWeek> week) {}

    private final TargetWeekResolver resolver;
    private final Duration ttl;
    private volatile Entry entry;

    public CurrentTargetWeek(TargetWeekResolver resolver,
            @Value("${fantasykai.projections.target-cache-seconds:60}") long ttlSeconds) {
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
        synchronized (this) {
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
                && !now.isBefore(candidate.computedAt())
                && now.isBefore(candidate.computedAt().plus(ttl));
    }
}
