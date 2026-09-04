package com.fantasykai.scoring;

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

    /** Round once, at the boundary. Callers rendering a number use this and nothing else. */
    public static double roundForDisplay(double points) {
        return Math.round(points * 100.0) / 100.0;
    }
}
