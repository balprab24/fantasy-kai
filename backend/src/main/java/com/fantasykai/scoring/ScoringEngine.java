package com.fantasykai.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Evaluates a stat line against a compiled ruleset.
 *
 * <pre>
 * points = Σ stat × effectiveRate(stat, position)
 *        + Σ bonuses where stat ≥ threshold
 * </pre>
 *
 * <p>Pure: no Spring, no database, no rounding. §6 is specific that rounding
 * happens once at the API boundary and never mid-calculation -- rounding here
 * would make a season total the sum of thirteen rounded weeks rather than the
 * rounded sum of thirteen weeks, which are not the same number.
 *
 * <p>Computed in Java rather than SQL on purpose. Position overrides and
 * threshold bonuses get ugly fast in a query, and this version is trivially
 * unit-testable against a real box score.
 */
public final class ScoringEngine {

    private ScoringEngine() {}

    public static double score(StatLine line, ResolvedRuleset rules) {
        double[] rates = rules.ratesFor(line.position());
        double[] values = line.values();

        double points = 0;
        for (int i = 0; i < StatKey.COUNT; i++) {
            points += values[i] * rates[i];
        }
        for (Bonus bonus : rules.bonuses()) {
            if (values[bonus.stat().index()] >= bonus.gte()) {
                points += bonus.points();
            }
        }
        return points;
    }

    /**
     * Round once, at the boundary. Callers rendering a number use this and
     * nothing else.
     *
     * <p>Decimal rounding, not {@code Math.round(x * 100) / 100.0}, which
     * disagrees with what a league scoreboard shows in two separate ways.
     * {@code 0.145 * 100} is {@code 14.499999999999998} in binary, so it
     * rounded down to {@code 0.14}; and {@code Math.round} breaks ties toward
     * positive infinity, so {@code -0.125} went to {@code -0.12} while
     * {@code +0.125} went to {@code +0.13}. The asymmetry is not academic --
     * {@code pass_int} and {@code fum_lost} carry negative rates, so half the
     * penalties round the wrong way.
     *
     * <p>{@code BigDecimal.valueOf} routes through {@code Double.toString}, so
     * it sees the {@code 0.145} the user wrote rather than the binary expansion
     * underneath it.
     */
    public static double roundForDisplay(double points) {
        return BigDecimal.valueOf(points).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
