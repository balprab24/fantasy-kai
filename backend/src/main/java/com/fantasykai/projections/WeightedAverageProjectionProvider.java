package com.fantasykai.projections;

import com.fantasykai.scoring.StatKey;
import java.util.List;
import java.util.Optional;

/**
 * The Fantasy-Kai Baseline: an exponentially weighted average of a player's games.
 *
 * <pre>
 * weight(age) = 0.5 ^ (age / 4)        age = games ago, the most recent game is age 0
 * projection  = Σ weight(ageᵢ) · statᵢ  /  Σ weight(ageᵢ)      for every stat separately
 * </pre>
 *
 * <p>So a game four appearances older counts exactly half as much as the newest, eight older a
 * quarter -- {@code 2^(-age/4)}, normalised. This is research baseline D with half-life 4,
 * frozen in {@code frozen-config.json} and measured in {@code docs/perf/projection-accuracy.md}
 * (PPR MAE 5.51 per player-week on 2025's ranked population). It is reproduced here operation
 * for operation, not reinterpreted: the same {@code Math.pow}, the same oldest-to-newest sums,
 * one division at the end -- which is why {@code ProviderParityTests} can demand bit-identical
 * output from the two.
 *
 * <ul>
 *   <li>Age counts appearances, not calendar weeks: a bye or a missed game adds no decay.</li>
 *   <li>History crosses seasons with no reset and no extra offseason decay, so week 1 is
 *       last season's games.</li>
 *   <li>A team change changes nothing; history follows the player.</li>
 *   <li>Every stat and every position decays the same way. No shrinkage, no priors, no
 *       clamping, no rounding.</li>
 *   <li>Games before 2020 are ignored -- the window the baseline was measured on, fixed here
 *       rather than read from the ingest's {@code first-season}, so a longer backfill can never
 *       change what {@code weighted-average-v1} means.</li>
 *   <li>No game, no projection. One to three games use exactly the same formula; a "limited
 *       history" label is a later layer's, from {@link RawProjection#gamesUsed()}.</li>
 * </ul>
 *
 * <p>Changing any of this is a new provider id, never an edit to this one.
 */
public final class WeightedAverageProjectionProvider implements ProjectionProvider {

    /**
     * What a member is told about the method, and how far off it has been. The errors are this
     * baseline's measured PPR MAE per player-week -- the one place the figures live, pinned by
     * {@code ProjectionSourceTests} to the committed reports. Both are research baseline D over the
     * research's population: each week's most-used skill players (top 32 QB, 64 RB, 96 WR and 32 TE
     * by opportunity, scaled by the teams playing). Neither is the board's population: over every
     * player with history who played the same baseline reads about 4.5 (4.509 in 2024, 4.470 in
     * 2025), so the population is part of each claim.
     *
     * <p>{@code IF_HE_PLAYS}, the research's P, the players among them who then played:
     * <ul>
     *   <li>2024, 5.480 over 3,808 player-weeks: the tuning season ({@code docs/perf/
     *       projection-backtest/validate-2024.md});</li>
     *   <li>2025, 5.512 over 3,807: the pre-registered test season
     *       ({@code docs/perf/projection-accuracy.md});</li>
     *   <li>2026 weeks 1-4, 5.688 over 896: the prospective check, on the corrected dataset that
     *       also counts zero-stat appearances ({@code docs/perf/projection-backtest/
     *       v2-prospective-2026.md}).</li>
     * </ul>
     *
     * <p>{@code BEFORE_KICKOFF}, the research's P0, the same players chosen before kickoff, each
     * who then did not play counted as a zero. It includes players the injury report lists as
     * Out, whom the board does not rank, so it is an upper bound for what a member reads:
     * <ul>
     *   <li>2024, 5.845 over 3,808 ({@code validate-2024.md}); 2025, 5.887 over 3,808
     *       ({@code projection-backtest/test-2025.md});</li>
     *   <li>2026 weeks 1-4, 5.934 over 896: the availability table of
     *       {@code v2-prospective-2026.md}, which defines "all projected" as P0.</li>
     * </ul>
     *
     * Each headline ("about 5.5", "about 5.9") is constructible only while all of its measurements
     * stay within 0.2 of it ({@link ProjectionSource.ErrorFigure}). Changing a figure here is a new
     * report first.
     */
    public static final ProjectionSource SOURCE = new ProjectionSource(
            "weighted-average-v1",
            "Fantasy-Kai Baseline",
            "A weighted average of the player's recent games: the latest counts most, "
                    + "and a game four games older counts half as much.",
            new ProjectionSource.TypicalError("PPR MAE", List.of(
                    new ProjectionSource.ErrorFigure(ProjectionSource.FigureKind.IF_HE_PLAYS, 5.5,
                            "each week's most-used skill players (top 32 QB, 64 RB, 96 WR, 32 TE by opportunity) "
                                    + "who played",
                            List.of(
                                    new ProjectionSource.Measurement(2024, null, 5.480, 3808),
                                    new ProjectionSource.Measurement(2025, null, 5.512, 3807),
                                    new ProjectionSource.Measurement(2026, "1-4", 5.688, 896))),
                    new ProjectionSource.ErrorFigure(ProjectionSource.FigureKind.BEFORE_KICKOFF, 5.9,
                            "the same players, chosen before kickoff (those who played their team's previous "
                                    + "game), counting each who then did not play as a zero",
                            List.of(
                                    new ProjectionSource.Measurement(2024, null, 5.845, 3808),
                                    new ProjectionSource.Measurement(2025, null, 5.887, 3808),
                                    new ProjectionSource.Measurement(2026, "1-4", 5.934, 896))))));

    /** Half-life in games. A double, as in the research, so the division is the same one. */
    static final double HALF_LIFE_GAMES = 4.0;

    /** The first season the baseline reads. */
    static final int FIRST_SEASON = 2020;

    @Override
    public ProjectionSource source() {
        return SOURCE;
    }

    /** {@code 0.5^(ageInGames / 4)}: 1 for the newest game, 0.5 four games back. */
    static double weight(int ageInGames) {
        return Math.pow(0.5, ageInGames / HALF_LIFE_GAMES);
    }

    @Override
    public Optional<RawProjection> project(PlayerHistory history) {
        List<HistoricalGame> games = history.games().stream()
                .filter(game -> game.when().season() >= FIRST_SEASON)
                .toList();
        int n = games.size();
        if (n == 0) {
            return Optional.empty();
        }
        double[] sum = new double[StatKey.COUNT];
        double passAttempts = 0;
        double carries = 0;
        double targets = 0;
        double total = 0;
        for (int i = 0; i < n; i++) {
            double w = weight(n - 1 - i);
            HistoricalGame game = games.get(i);
            for (int k = 0; k < StatKey.COUNT; k++) {
                sum[k] += w * game.stat(k);
            }
            passAttempts += w * game.passAttempts();
            carries += w * game.carries();
            targets += w * game.targets();
            total += w;
        }
        double[] stats = new double[StatKey.COUNT];
        for (int k = 0; k < StatKey.COUNT; k++) {
            stats[k] = sum[k] / total;
        }
        return Optional.of(new RawProjection(SOURCE, history.target(), stats, passAttempts / total,
                carries / total, targets / total, n, games.get(n - 1).when()));
    }
}
