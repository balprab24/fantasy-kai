package com.fantasykai.projections;

import java.time.Instant;

/**
 * One player's known availability for one week, with the source values it was read from.
 *
 * <p>The status is derived ({@link AvailabilityRules#classify}); the rest is what the feeds said
 * and when, so a screen or a log can say why -- "on injured reserve (R01) as of the week 5
 * roster" -- without asking again.
 *
 * @param week          the week this answers for
 * @param status        the classification
 * @param rosterStatus  the roster {@code status} read, or null when no roster row applied
 * @param rosterDetail  its {@code status_description_abbr}, or null
 * @param rosterWeek    the roster week it came from: {@code week.week()} when that week is
 *                      published for his team, an earlier week until it is, null for none
 * @param teamId        the team that roster row lists him on, or null
 * @param reportStatus  the injury {@code report_status} for exactly this week, or null
 * @param rosterAsOf    when the roster row was read, or null
 * @param reportAsOf    when the injury row was read, or null
 */
public record Availability(SeasonWeek week, AvailabilityStatus status, String rosterStatus,
        String rosterDetail, Integer rosterWeek, Integer teamId, String reportStatus,
        Instant rosterAsOf, Instant reportAsOf) {

    /** Nothing known about this player for this week. */
    public static Availability unknown(SeasonWeek week) {
        return new Availability(week, AvailabilityStatus.UNKNOWN, null, null, null, null, null, null, null);
    }

    /** Whether the projection board ranks him this week. */
    public boolean ranked() {
        return status.ranked();
    }

    /** The roster status is from this week's roster, not carried from an earlier one. */
    public boolean rosterIsCurrent() {
        return rosterWeek != null && rosterWeek == week.week();
    }
}
