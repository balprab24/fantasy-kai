package com.fantasykai.projection;

import com.fantasykai.projections.HistoricalGame;
import com.fantasykai.projections.PlayerHistory;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.WeightedAverageProjectionProvider;
import com.fantasykai.scoring.StatKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code scripts/backtest.sh parity}: production's provider against the research baseline on
 * every 2024 and 2025 player-week the backtest projects, on real data, bit for bit.
 *
 * <p>Run on both research datasets: the database's stored rows (Slice 1's, where baseline D was
 * measured), and v2's corrected dataset, whose histories also carry active zero-stat
 * appearances -- so the provider is shown counting an all-zero game exactly as the research
 * did. The production side is handed each player's whole career and applies its own cutoff.
 * Any mismatch fails the run.
 */
final class ProviderParity {

    private static final List<Integer> SEASONS = List.of(2024, 2025);

    private ProviderParity() {}

    static void run(Connection connection) throws Exception {
        StringBuilder md = new StringBuilder("# Provider parity: production against research baseline D\n\n");
        md.append("`WeightedAverageProjectionProvider` (weighted-average-v1) against "
                + "`Baseline(EWMA, 4)`, every player-week the backtest projects in 2024 and 2025. "
                + "Bit-identical or it fails.\n\n");
        md.append("| Dataset | Season | Player-weeks compared | Of which zero-stat appearances in history | No history (both empty) | Mismatches |\n");
        md.append("|---|---:|---:|---:|---:|---:|\n");
        int mismatches = 0;
        mismatches += compare(md, "stored rows (Slice 1)", BacktestData.load(connection));
        String cache = System.getenv("NFLVERSE_CACHE");
        if (cache != null && !cache.isBlank()) {
            mismatches += compare(md, "v2 corrected (with zero-stat appearances)",
                    BacktestData.load(connection, NflverseExtras.read(Path.of(cache), 2020, 2025)));
        } else {
            md.append("| v2 corrected | -- | not run: NFLVERSE_CACHE unset | | | |\n");
        }
        md.append("\n").append(mismatches == 0 ? "**No mismatch.**" : "**" + mismatches + " mismatches.**")
                .append("\n");
        Files.createDirectories(Backtest.OUT);
        Files.writeString(Backtest.OUT.resolve("parity-results.md"), md.toString());
        System.out.println(md);
        if (mismatches > 0) {
            throw new IllegalStateException(mismatches + " parity mismatches");
        }
    }

    private static int compare(StringBuilder md, String label, BacktestData data) {
        Baseline research = new Baseline(Baseline.Kind.EWMA, 4.0);
        WeightedAverageProjectionProvider production = new WeightedAverageProjectionProvider();
        int total = 0;
        for (int season : SEASONS) {
            int compared = 0;
            int withZero = 0;
            int empty = 0;
            int mismatches = 0;
            for (Case c : Population.build(data, season).cases()) {
                List<HistoricalGame> career = new ArrayList<>();
                for (PlayerGame g : data.players().get(c.playerId).all()) {
                    career.add(new HistoricalGame(new SeasonWeek(g.season(), g.week()),
                            Arrays.copyOf(g.line(), StatKey.COUNT), g.line()[Quantity.PASS_ATT],
                            g.line()[Quantity.RUSH_ATT], g.line()[Quantity.TARGETS]));
                }
                Optional<RawProjection> p = production.project(
                        PlayerHistory.before(new SeasonWeek(c.season, c.week), career));
                if (c.history.isEmpty()) {
                    empty++;
                    mismatches += p.isPresent() ? 1 : 0;
                    continue;
                }
                compared++;
                withZero += c.history.stream().anyMatch(PlayerGame::snapOnly) ? 1 : 0;
                if (p.isEmpty() || !identical(research.project(c.history, c.season), p.get(), c.history.size())) {
                    mismatches++;
                }
            }
            md.append(String.format(Locale.ROOT, "| %s | %d | %,d | %,d | %,d | %d |%n", label, season,
                    compared, withZero, empty, mismatches));
            total += mismatches;
        }
        return total;
    }

    private static boolean identical(double[] research, RawProjection production, int games) {
        for (StatKey stat : StatKey.values()) {
            if (Double.doubleToRawLongBits(production.get(stat))
                    != Double.doubleToRawLongBits(research[stat.index()])) {
                return false;
            }
        }
        return Double.doubleToRawLongBits(production.passAttempts()) == Double.doubleToRawLongBits(research[Quantity.PASS_ATT])
                && Double.doubleToRawLongBits(production.carries()) == Double.doubleToRawLongBits(research[Quantity.RUSH_ATT])
                && Double.doubleToRawLongBits(production.targets()) == Double.doubleToRawLongBits(research[Quantity.TARGETS])
                && production.gamesUsed() == games;
    }
}
