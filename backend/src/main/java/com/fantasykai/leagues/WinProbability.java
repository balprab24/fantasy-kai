package com.fantasykai.leagues;

import java.util.List;

/**
 * The chance one fantasy lineup outscores another in a week (plan S3).
 *
 * <p>A team's week is normal: its mean is the sum of its starters' expected points; its
 * variance is the sum of their σ² ({@link ProjectionUncertainty}), plus 2·r·σ_QB·σ_X for each
 * quarterback and receiver or tight end from the same NFL team that it starts -- a stack moves
 * together (r 0.37, plan A7). Then
 *
 * <pre>
 * P(A beats B) = Φ((μ_A − μ_B) / √(Var_A + Var_B))
 * </pre>
 *
 * <p>Stated approximations: opponents are independent (plan A7 measured QB-QB across a game at
 * 0.19 and other cross-game pairs near 0); a starter expected to score 0 -- a bye, Out -- adds
 * no variance, because he is known not to play; K and DST are not modelled, so their variance is
 * absent and odds in leagues that start them are a little too confident. Whether the whole thing
 * predicts real results is the pre-registered calibration gate
 * ({@code docs/perf/trade-calibration.md}), not this class's claim.
 */
public final class WinProbability {

    /**
     * One starter.
     *
     * @param nflTeamId his NFL team that week, for stacks; null when unknown
     * @param mu        expected points, unrounded
     */
    public record Starter(String position, Integer nflTeamId, double mu) {}

    /** A team's week as a normal distribution. */
    public record TeamWeek(double mean, double variance) {}

    /** How far a modelled starter's week misses: production's is {@link ProjectionUncertainty#sigma}. */
    @FunctionalInterface
    public interface Sigma {
        double of(String position, double mu);
    }

    /**
     * The spread multiplier k: P = Φ(gap / (k·spread)). 1.0 until a value passes the
     * pre-registered calibration gate (docs/perf/trade-calibration.md, attempt 3); then that one.
     */
    public static final double SPREAD_MULTIPLIER = 1.0;

    private static final Sigma PRODUCTION = (position, mu) ->
            ProjectionUncertainty.modeled(position) ? ProjectionUncertainty.sigma(position, mu) : 0;

    /** Production's σ, for callers that pass one explicitly. */
    public static Sigma productionSigma() {
        return PRODUCTION;
    }

    private WinProbability() {}

    public static TeamWeek team(List<Starter> starters) {
        return team(starters, 0);
    }

    /**
     * @param unmodeledVariance variance from starters with no projection -- K and DST, whose
     *     points are a constant mean for every team (so it cancels) and a measured spread
     */
    public static TeamWeek team(List<Starter> starters, double unmodeledVariance) {
        return team(starters, unmodeledVariance, PRODUCTION);
    }

    /** As {@link #team(List, double)}, with a σ other than production's -- for research fits. */
    public static TeamWeek team(List<Starter> starters, double unmodeledVariance, Sigma sigma) {
        double mean = 0;
        double variance = unmodeledVariance;
        for (Starter s : starters) {
            mean += s.mu();
            variance += square(sigma(s, sigma));
        }
        for (Starter qb : starters) {
            if (!"QB".equals(qb.position()) || qb.nflTeamId() == null) {
                continue;
            }
            for (Starter other : starters) {
                if (("WR".equals(other.position()) || "TE".equals(other.position()))
                        && qb.nflTeamId().equals(other.nflTeamId())) {
                    variance += 2 * ProjectionUncertainty.QB_PASS_CATCHER_CORRELATION * sigma(qb, sigma) * sigma(other, sigma);
                }
            }
        }
        return new TeamWeek(mean, variance);
    }

    /** P(a outscores b); a tie in expectation with no variance at all is a coin toss. */
    public static double beats(TeamWeek a, TeamWeek b) {
        return beats(a, b, SPREAD_MULTIPLIER);
    }

    /** With an explicit spread multiplier; {@code k = 1} is the plain normal model, bit for bit. */
    public static double beats(TeamWeek a, TeamWeek b, double k) {
        double spread = k * Math.sqrt(a.variance() + b.variance());
        double gap = a.mean() - b.mean();
        if (spread == 0) {
            return gap > 0 ? 1 : gap < 0 ? 0 : 0.5;
        }
        return phi(gap / spread);
    }

    /** A starter's σ: 0 when he is known not to play, and for anyone not modelled. */
    private static double sigma(Starter s, Sigma sigma) {
        return s.mu() == 0 ? 0 : sigma.of(s.position(), s.mu());
    }

    /**
     * The standard normal CDF, via erfc (Numerical Recipes' Chebyshev fit, fractional error
     * below 1.2e-7 everywhere) -- far finer than any probability shown, and no dependency.
     */
    static double phi(double x) {
        return 0.5 * erfc(-x / Math.sqrt(2));
    }

    private static double erfc(double x) {
        double z = Math.abs(x);
        double t = 1 / (1 + 0.5 * z);
        double r = t * Math.exp(-z * z - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418
                + t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587
                + t * (-0.82215223 + t * 0.17087277)))))))));
        return x >= 0 ? r : 2 - r;
    }

    private static double square(double x) {
        return x * x;
    }
}
