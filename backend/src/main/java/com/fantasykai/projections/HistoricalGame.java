package com.fantasykai.projections;

import com.fantasykai.scoring.StatKey;
import java.util.Arrays;

/**
 * One regular-season appearance: the player was active and played, whatever he recorded.
 *
 * <p>An all-zero line is a real appearance -- a receiver who ran routes and caught nothing -- and
 * counts as a game like any other. It is not the same thing as no row at all (a bye, an inactive
 * week, an injury), which simply is not in a history. That distinction is the caller's to get
 * right; a projection provider treats every game it is given as played.
 *
 * <p>Raw stats only, never points. No team: a player's history follows him across teams.
 *
 * @param stats        the 13 scorable stats, indexed by {@link StatKey#index()}
 * @param passAttempts volume, never scored (see {@code query.Usage})
 * @param carries      volume, never scored
 * @param targets      volume, never scored
 */
public record HistoricalGame(SeasonWeek when, double[] stats, double passAttempts, double carries,
        double targets) {

    public HistoricalGame {
        if (stats.length != StatKey.COUNT) {
            throw new IllegalArgumentException("expected " + StatKey.COUNT + " stats, got " + stats.length);
        }
        // A stat that could not be read is not a zero, and a NaN would silently poison every
        // projection it entered. The caller decides what a missing value means; this refuses one.
        for (double value : stats) {
            requireFinite(value, when);
        }
        requireFinite(passAttempts, when);
        requireFinite(carries, when);
        requireFinite(targets, when);
        stats = stats.clone();
    }

    private static void requireFinite(double value, SeasonWeek when) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("a stat in " + when + " is " + value + ", not a number of plays");
        }
    }

    @Override
    public double[] stats() {
        return stats.clone();
    }

    double stat(int index) {
        return stats[index];
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof HistoricalGame g && when.equals(g.when) && Arrays.equals(stats, g.stats)
                && Double.compare(passAttempts, g.passAttempts) == 0
                && Double.compare(carries, g.carries) == 0 && Double.compare(targets, g.targets) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * when.hashCode() + Arrays.hashCode(stats);
    }
}
