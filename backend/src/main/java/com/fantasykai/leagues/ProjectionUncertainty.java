package com.fantasykai.leagues;

import java.util.Map;

/**
 * How far a projection misses: σ = a + b·μ by position, in a league's points per week.
 *
 * <p>Fitted on production's own error, not assumed: {@code scripts/backtest.sh uncertainty}
 * (test-scoped {@code projection.UncertaintyFit}) ran {@code weighted-average-v1} over population
 * P, 2021-2025 regular season, players who played -- 19,025 player-weeks -- under Half PPR, and
 * fitted σ (the RMSE of actual − μ in 5-point bins of μ) by count-weighted least squares.
 * Source: {@code docs/perf/projection-accuracy.md}, "Projection uncertainty", run 2026-10-30.
 *
 * <p>One fit serves every league: refitted under PPR and 0 PPR, σ at μ = 10 moves by at most
 * 0.3 points (WR 6.69 Half, 6.94 PPR, 6.73 0 PPR). A league far from those -- six-point passing
 * TDs, big bonuses -- is a stated approximation, and S3's calibration gate is where it is tested.
 *
 * <p>Larger than plan A7's spreads, as it should be: A7 measured spread around a hindsight
 * season average; this is the error of a forecast.
 */
public final class ProjectionUncertainty {

    /** {a, b} per position. */
    static final Map<String, double[]> FIT = Map.of(
            "QB", new double[] {6.612, 0.0857},
            "RB", new double[] {4.027, 0.2732},
            "WR", new double[] {3.501, 0.3192},
            "TE", new double[] {3.138, 0.3310});

    /**
     * Same-team QB and pass-catcher (WR or TE) weekly deviations move together: r 0.37 for
     * QB-WR and QB-TE, plan A7 (half PPR, 2021-2025, players averaging 8+). S3's win
     * probability adds 2·r·σ_QB·σ_X for each such pair it starts. Unused before S3.
     */
    public static final double QB_PASS_CATCHER_CORRELATION = 0.37;

    private ProjectionUncertainty() {}

    /**
     * @throws IllegalArgumentException for a position with no fit -- K and DST are not
     *     modelled, and a guessed σ would be the kind of default that hides an absence
     */
    public static double sigma(String position, double mu) {
        double[] fit = FIT.get(position);
        if (fit == null) {
            throw new IllegalArgumentException("no uncertainty fit for position " + position);
        }
        return fit[0] + fit[1] * Math.max(0, mu);
    }

    public static boolean modeled(String position) {
        return FIT.containsKey(position);
    }
}
