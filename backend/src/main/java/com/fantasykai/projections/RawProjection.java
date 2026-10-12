package com.fantasykai.projections;

import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;

/**
 * A projected raw stat line for one player's target week -- never points.
 *
 * <p>Points depend on a league this type knows nothing about. {@link #line} hands the stats to
 * {@code ScoringEngine} under whatever position and ruleset the caller has, so one projection
 * scores under every league without being regenerated. Values are fractional and unrounded: an
 * expected 0.43 touchdowns is the projection; rounding is for display, later.
 *
 * @param source       the method that made it
 * @param target       the week projected
 * @param stats        the 13 scorable stats, indexed by {@link StatKey#index()}
 * @param passAttempts projected volume, never scored
 * @param carries      projected volume, never scored
 * @param targets      projected volume, never scored
 * @param gamesUsed    how many games the projection drew on -- neutral metadata; "limited
 *                     history" is a label a later layer attaches, not this one
 * @param lastGame     the most recent game it used
 */
public record RawProjection(ProjectionSource source, SeasonWeek target, double[] stats,
        double passAttempts, double carries, double targets, int gamesUsed, SeasonWeek lastGame) {

    public RawProjection {
        if (stats.length != StatKey.COUNT) {
            throw new IllegalArgumentException("expected " + StatKey.COUNT + " stats, got " + stats.length);
        }
        if (gamesUsed < 1) {
            throw new IllegalArgumentException("a projection draws on at least one game");
        }
        stats = stats.clone();
    }

    @Override
    public double[] stats() {
        return stats.clone();
    }

    public double get(StatKey stat) {
        return stats[stat.index()];
    }

    /** The projection as {@code ScoringEngine} reads it, for a player at {@code position}. */
    public StatLine line(String position) {
        return new StatLine(position, stats.clone());
    }
}
