package com.fantasykai.projection;

import com.fantasykai.projections.HistoricalGame;
import com.fantasykai.projections.JdbcProjectionInputs;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * {@code scripts/backtest.sh parity}: production's provider against the research baseline on
 * every 2024 and 2025 player-week the backtest projects, on real data, bit for bit.
 *
 * <p>Run on both research datasets: the database's stored rows (Slice 1's, where baseline D was
 * measured), and v2's corrected dataset, whose histories also carry active zero-stat
 * appearances -- so the provider is shown counting an all-zero game exactly as the research
 * did. The production side is handed each player's whole career and applies its own cutoff.
 *
 * <p>Slice 3 adds a third row: production's own <em>inputs</em>. {@code JdbcProjectionInputs}
 * reads this database -- stat rows plus the {@code player_game_appearances} the ingest now
 * stores -- and its history for every player-week must equal v2's, game by game, before the
 * provider runs on it. That row needs V7 applied and the appearances backfilled. Any mismatch
 * fails the run.
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
            BacktestData v2 = BacktestData.load(connection, NflverseExtras.read(Path.of(cache), 2020, 2025));
            mismatches += compare(md, "v2 corrected (with zero-stat appearances)", v2);
            mismatches += compareInputs(md, "production inputs (this database) against v2", v2, connection);
        } else {
            md.append("| v2 corrected | -- | not run: NFLVERSE_CACHE unset | | | |\n");
            md.append("| production inputs | -- | not run: NFLVERSE_CACHE unset | | | |\n");
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
                    career.add(game(g));
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

    /**
     * Production's history, read by {@code JdbcProjectionInputs} from this database, against the
     * research history of every player-week -- equal game by game, then bit-identical once
     * projected. Histories are read once per target week, as the job reads them.
     */
    private static int compareInputs(StringBuilder md, String label, BacktestData data, Connection connection) {
        JdbcProjectionInputs inputs = new JdbcProjectionInputs(
                new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
        Baseline research = new Baseline(Baseline.Kind.EWMA, 4.0);
        WeightedAverageProjectionProvider production = new WeightedAverageProjectionProvider();
        int total = 0;
        for (int season : SEASONS) {
            Map<SeasonWeek, Map<Long, PlayerHistory>> byWeek = new HashMap<>();
            int compared = 0;
            int withZero = 0;
            int empty = 0;
            int mismatches = 0;
            for (Case c : Population.build(data, season).cases()) {
                SeasonWeek target = new SeasonWeek(c.season, c.week);
                PlayerHistory stored = byWeek.computeIfAbsent(target, inputs::histories).get(c.playerId);
                if (c.history.isEmpty()) {
                    empty++;
                    mismatches += stored == null ? 0 : 1;
                    continue;
                }
                compared++;
                withZero += c.history.stream().anyMatch(PlayerGame::snapOnly) ? 1 : 0;
                List<HistoricalGame> expected = c.history.stream().map(ProviderParity::game).toList();
                Optional<RawProjection> p = stored == null ? Optional.empty() : production.project(stored);
                if (stored == null || !stored.games().equals(expected) || p.isEmpty()
                        || !identical(research.project(c.history, c.season), p.get(), c.history.size())) {
                    if (mismatches < 5) {
                        System.err.printf(Locale.ROOT, "inputs mismatch: player %d, %s: research %d games, stored %s%n",
                                c.playerId, target, expected.size(),
                                stored == null ? "none" : stored.games().size() + " games");
                    }
                    mismatches++;
                }
            }
            md.append(String.format(Locale.ROOT, "| %s | %d | %,d | %,d | %,d | %d |%n", label, season,
                    compared, withZero, empty, mismatches));
            total += mismatches;
        }
        return total;
    }

    private static HistoricalGame game(PlayerGame g) {
        return new HistoricalGame(new SeasonWeek(g.season(), g.week()),
                Arrays.copyOf(g.line(), StatKey.COUNT), g.line()[Quantity.PASS_ATT],
                g.line()[Quantity.RUSH_ATT], g.line()[Quantity.TARGETS]);
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
