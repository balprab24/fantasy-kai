package com.fantasykai.projections;

import java.util.Comparator;

/**
 * A point in the regular-season calendar: the week a projection is for, or the week a game was
 * played. Ordered by season and then week.
 *
 * <p>Regular season only. Playoff weeks overlap the regular season's numbering -- 2020's wild
 * card is week 18 -- so a playoff game must never be represented as one of these.
 */
public record SeasonWeek(int season, int week) implements Comparable<SeasonWeek> {

    private static final Comparator<SeasonWeek> ORDER =
            Comparator.comparingInt(SeasonWeek::season).thenComparingInt(SeasonWeek::week);

    public SeasonWeek {
        if (week < 1 || week > 18) {
            throw new IllegalArgumentException("a regular-season week is 1-18, got " + week);
        }
    }

    @Override
    public int compareTo(SeasonWeek other) {
        return ORDER.compare(this, other);
    }

    public boolean isBefore(SeasonWeek other) {
        return compareTo(other) < 0;
    }

    @Override
    public String toString() {
        return season + " week " + week;
    }
}
