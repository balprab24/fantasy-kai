package com.fantasykai.projection;

import com.fantasykai.projections.HistoricalGame;
import com.fantasykai.projections.PlayerHistory;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.WeightedAverageProjectionProvider;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * {@code scripts/backtest.sh uncertainty}: how far production's projection misses, by position
 * and by how much it projected -- the σ = a + b·μ that league plan S2's
 * {@code ProjectionUncertainty} carries and S3's win probability reads.
 *
 * <p>Method, fixed before running:
 * <ul>
 *   <li>production's {@link WeightedAverageProjectionProvider}, each player handed his whole
 *       career and cut by {@link PlayerHistory#before}, as {@code ProviderParity} does;</li>
 *   <li>population P (pinned in {@link Population}), 2021-2025 regular season, players who
 *       played -- availability is the league layer's, not the error's;</li>
 *   <li>μ and the actual scored with {@link ScoringEngine} under Half PPR, plan A7's basis, with
 *       PPR and 0 PPR fitted beside it as a robustness check;</li>
 *   <li>bins of 5 projected points; σ per bin is the RMSE of actual − μ; (a, b) by least
 *       squares weighted by each bin's count; bins under {@value #MIN_BIN} are left out.</li>
 * </ul>
 *
 * <p>Sanity gate, from A7's shape: RB, WR and TE need b &gt; 0, and QB the smallest b. A fit
 * that fails it is reported and the stage fails: no constant should be committed from it.
 */
final class UncertaintyFit {

    static final List<Integer> SEASONS = List.of(2021, 2022, 2023, 2024, 2025);
    static final double BIN = 5.0;
    static final int MIN_BIN = 30;
    private static final List<String> POSITIONS = List.of("QB", "RB", "WR", "TE");

    /** V3's presets (the test helper {@code Presets.BASE}), with the reception rate varied. */
    private static final Map<String, ResolvedRuleset> RULESETS = new LinkedHashMap<>();

    static {
        for (double rec : new double[] {0.5, 1.0, 0.0}) {
            Map<StatKey, Double> base = new EnumMap<>(StatKey.class);
            base.put(StatKey.PASS_YD, 0.04);
            base.put(StatKey.PASS_TD, 4.0);
            base.put(StatKey.PASS_INT, -2.0);
            base.put(StatKey.PASS_2PT, 2.0);
            base.put(StatKey.RUSH_YD, 0.1);
            base.put(StatKey.RUSH_TD, 6.0);
            base.put(StatKey.RUSH_2PT, 2.0);
            base.put(StatKey.REC, rec);
            base.put(StatKey.REC_YD, 0.1);
            base.put(StatKey.REC_TD, 6.0);
            base.put(StatKey.REC_2PT, 2.0);
            base.put(StatKey.FUM_LOST, -2.0);
            base.put(StatKey.RET_TD, 6.0);
            String name = rec == 0.5 ? "Half PPR" : rec == 1.0 ? "PPR" : "0 PPR";
            RULESETS.put(name, ResolvedRuleset.compile(new Ruleset(1, base, Map.of(), List.of())));
        }
    }

    record Fit(double a, double b, int n, double rmse) {}

    /** "Half PPR", "PPR" or "0 PPR": V3's presets, for other research stages to score with. */
    static ResolvedRuleset ruleset(String name) {
        return RULESETS.get(name);
    }

    private UncertaintyFit() {}

    static void run(Connection connection) throws Exception {
        BacktestData data = BacktestData.load(connection);
        WeightedAverageProjectionProvider production = new WeightedAverageProjectionProvider();

        int[] skipped = {0};
        Map<String, Map<String, List<double[]>>> pairs = pairs(data, production, SEASONS, skipped);

        StringBuilder md = new StringBuilder("# Projection uncertainty: σ against μ\n\n");
        md.append("Production `weighted-average-v1`, population P, ").append(SEASONS.getFirst()).append('–')
                .append(SEASONS.getLast()).append(" regular season, players who played. Bins of ")
                .append((int) BIN).append(" projected points; σ = RMSE of actual − μ per bin; (a, b) by "
                        + "count-weighted least squares over bins with n ≥ ").append(MIN_BIN)
                .append(". No projection (no history): ").append(skipped[0]).append(" player-weeks skipped.\n\n");

        Map<String, Map<String, Fit>> fits = new LinkedHashMap<>();
        for (var ruleset : pairs.entrySet()) {
            md.append("## ").append(ruleset.getKey()).append("\n\n");
            md.append("| Pos | n | RMSE | bins (μ from: n, σ) | a | b |\n|---|---:|---:|---|---:|---:|\n");
            Map<String, Fit> byPosition = new LinkedHashMap<>();
            for (String position : POSITIONS) {
                List<double[]> rows = ruleset.getValue().getOrDefault(position, List.of());
                Fit fit = fit(rows, md, position);
                byPosition.put(position, fit);
            }
            fits.put(ruleset.getKey(), byPosition);
            md.append('\n');
        }

        Map<String, Fit> half = fits.get("Half PPR");
        boolean sane = half.get("RB").b() > 0 && half.get("WR").b() > 0 && half.get("TE").b() > 0
                && half.get("QB").b() < Math.min(half.get("RB").b(), Math.min(half.get("WR").b(), half.get("TE").b()));
        md.append(sane ? "**Sanity gate passed** (Half PPR: RB, WR, TE b > 0; QB b the smallest).\n"
                : "**Sanity gate FAILED** -- do not commit constants from this fit.\n");

        Files.createDirectories(Backtest.OUT);
        Files.writeString(Backtest.OUT.resolve("uncertainty-results.md"), md.toString());
        System.out.println(md);
        if (!sane) {
            throw new IllegalStateException("uncertainty fit failed its sanity gate");
        }
    }

    /** ruleset -> position -> [mu, actual] pairs, for {@code seasons}. */
    static Map<String, Map<String, List<double[]>>> pairs(BacktestData data,
            WeightedAverageProjectionProvider production, List<Integer> seasons, int[] skipped) {
        Map<String, Map<String, List<double[]>>> pairs = new LinkedHashMap<>();
        RULESETS.keySet().forEach(r -> pairs.put(r, new TreeMap<>()));
        for (int season : seasons) {
            for (Case c : Population.build(data, season).cases()) {
                if (!c.inP || c.actual == null || !POSITIONS.contains(c.position)) {
                    continue;
                }
                List<HistoricalGame> career = new ArrayList<>();
                for (PlayerGame g : data.players().get(c.playerId).all()) {
                    career.add(game(g));
                }
                Optional<RawProjection> p = production.project(
                        PlayerHistory.before(new SeasonWeek(c.season, c.week), career));
                if (p.isEmpty()) {
                    skipped[0]++;
                    continue;
                }
                double[] actual = Arrays.copyOf(c.actual.line(), StatKey.COUNT);
                RULESETS.forEach((name, rules) -> pairs.get(name)
                        .computeIfAbsent(c.position, k -> new ArrayList<>())
                        .add(new double[] {
                                ScoringEngine.score(p.get().line(c.position), rules),
                                ScoringEngine.score(new StatLine(c.position, actual), rules)}));
            }
        }

        return pairs;
    }

    /** The Half PPR fit by position over {@code seasons} only -- calibration attempt 3's training fit. */
    static Map<String, Fit> fitHalfPpr(BacktestData data, List<Integer> seasons) {
        Map<String, List<double[]>> half = pairs(data, new WeightedAverageProjectionProvider(), seasons, new int[1])
                .get("Half PPR");
        Map<String, Fit> fits = new LinkedHashMap<>();
        for (String position : POSITIONS) {
            fits.put(position, fit(half.getOrDefault(position, List.of()), new StringBuilder(), position));
        }
        return fits;
    }

    /** Bins, σ per bin, then the count-weighted least-squares line through them. */
    static Fit fit(List<double[]> rows, StringBuilder md, String position) {
        Map<Integer, List<double[]>> bins = new TreeMap<>();
        double squares = 0;
        for (double[] row : rows) {
            bins.computeIfAbsent((int) Math.floor(Math.max(0, row[0]) / BIN), k -> new ArrayList<>()).add(row);
            squares += (row[1] - row[0]) * (row[1] - row[0]);
        }
        double sw = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
        StringBuilder cells = new StringBuilder();
        for (var bin : bins.entrySet()) {
            List<double[]> in = bin.getValue();
            if (in.size() < MIN_BIN) {
                continue;
            }
            double mu = in.stream().mapToDouble(r -> r[0]).average().orElse(0);
            double sigma = Math.sqrt(in.stream().mapToDouble(r -> (r[1] - r[0]) * (r[1] - r[0])).average().orElse(0));
            double w = in.size();
            sw += w;
            sx += w * mu;
            sy += w * sigma;
            sxx += w * mu * mu;
            sxy += w * mu * sigma;
            cells.append(String.format(Locale.ROOT, "%d: %d, %.2f; ", (int) (bin.getKey() * BIN), in.size(), sigma));
        }
        double b = (sw * sxy - sx * sy) / (sw * sxx - sx * sx);
        double a = (sy - b * sx) / sw;
        double rmse = rows.isEmpty() ? Double.NaN : Math.sqrt(squares / rows.size());
        md.append(String.format(Locale.ROOT, "| %s | %,d | %.3f | %s | %.3f | %.4f |%n", position, rows.size(), rmse,
                cells.toString().trim(), a, b));
        return new Fit(a, b, rows.size(), rmse);
    }

    private static HistoricalGame game(PlayerGame g) {
        return new HistoricalGame(new SeasonWeek(g.season(), g.week()),
                Arrays.copyOf(g.line(), StatKey.COUNT), g.line()[Quantity.PASS_ATT],
                g.line()[Quantity.RUSH_ATT], g.line()[Quantity.TARGETS]);
    }
}
