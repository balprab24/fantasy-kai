package com.fantasykai.api;

import com.fantasykai.projections.ProjectionSource;
import com.fantasykai.projections.SeasonWeek;
import java.time.Instant;

/**
 * The member's projection board.
 *
 * <p>{@code state}: {@code READY}; {@code STALE} (the rows are for an earlier week of the same
 * season than the one being played, or every game of theirs is at least 48 hours old);
 * {@code UNAVAILABLE} (a week to project, nothing stored for it); or {@code NO_UPCOMING_WEEK}
 * (the schedule has none left, and no rows are served).
 *
 * <p>Every field below the state says something the rows alone cannot, and each is absent
 * ({@code null}) rather than guessed when it is not known:
 *
 * @param season           the season of the week being projected, from the same rule the job
 *                         projects by; null with {@code NO_UPCOMING_WEEK}. When {@code state} is
 *                         {@code STALE} the rows are for an earlier week, and each row says which
 * @param week             that week
 * @param dataThrough      the newest game any served row was built from, whatever the member's
 *                         position or search; null when no row is served
 * @param generatedAt      when the newest served row was written, likewise; null when none is
 * @param availabilityAsOf the older of the last successful roster pull and the last successful
 *                         injury pull: how current "who is out" can be. Null when either feed has
 *                         never succeeded, because one feed's freshness says nothing about the
 *                         other's. It is when the pull ran, not when anything was published or which week it covered:
 *                         on a Tuesday after a successful pull, the week's injury report may not
 *                         exist yet (reports start Wednesday), and every player then reads
 *                         {@code UNKNOWN} under a recent time
 * @param excluded         who the board left out, and why
 * @param page             the ranked rows
 */
public record ProjectionBoard(String state, Integer season, Integer week, ProjectionSource source,
        SeasonWeek dataThrough, Instant generatedAt, Instant availabilityAsOf, Excluded excluded,
        PageResponse<ProjectionRow> page) {

    /**
     * Who is missing from the board, so that no player disappears without being counted. All three
     * respect the position filter and ignore the name search: the count is of the week's board,
     * not of what the member typed.
     *
     * @param unavailable players with a stored projection the board does not rank: Out, on a
     *                    reserve list, or off the roster. Their line is kept, and their own
     *                    page shows it
     * @param noHistory   rostered players the job would have projected but for having no
     *                    regular-season game before the week (decided by the job's own reader, so
     *                    a debut already played in this week does not remove him). Null unless
     *                    {@code state} is {@code READY}, and null when a stored history is
     *                    corrupt: the fault is logged by name and the count is not known
     * @param bye         rostered players whose team has no game in the week. Null unless
     *                    {@code state} is {@code READY}. Both are null otherwise because only the
     *                    week being played has a roster to count against, and zero would say "none"
     *                    where the truth is "not known"
     */
    public record Excluded(int unavailable, Integer noHistory, Integer bye) {}
}
