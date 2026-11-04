package com.fantasykai.projection;

import com.fantasykai.leagues.LineupOptimizer;
import com.fantasykai.leagues.LineupOptimizer.Candidate;
import com.fantasykai.leagues.LineupOptimizer.Lineup;
import com.fantasykai.leagues.LineupOptimizer.Spot;
import com.fantasykai.leagues.RosterSlot;
import com.fantasykai.leagues.WinProbability;
import com.fantasykai.leagues.WinProbability.Starter;
import com.fantasykai.leagues.WinProbability.TeamWeek;
import com.fantasykai.projections.HistoricalGame;
import com.fantasykai.projections.PlayerHistory;
import com.fantasykai.projections.RawProjection;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.projections.WeightedAverageProjectionProvider;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * {@code scripts/backtest.sh calibration}: league plan S3's pre-registered gate. Does
 * {@link WinProbability} -- production's projection, {@code ProjectionUncertainty}'s σ and the
 * stack correlation -- predict who wins a fantasy matchup as often as it says?
 *
 * <p><strong>Pass bar, fixed in the plan before any run</strong> (plan S3, committed with the
 * plan): a Brier score at least 10% below the constant-0.5 baseline (0.25), so at most 0.225,
 * <em>and</em> a reliability slope within [0.8, 1.2] across 10 bins. Fail either and Trades is
 * held; the owner ruled out a public beta (2026-10-29).
 *
 * <p>Every other choice is a constant below, set before the first run:
 * <ul>
 *   <li>2021-2025; {@value #LEAGUES} synthetic {@value #TEAMS}-team leagues a season, regular
 *       weeks 1-{@value #WEEKS}, Half PPR;</li>
 *   <li>the pool: skill players with at least {@value #MIN_PRIOR_GAMES} games the season
 *       before and a game this season -- a stated mild leak: it drops retirees, as a real
 *       draft would, and also anyone hurt all year, which a real draft would not know;</li>
 *   <li>a {@value #ROUNDS}-round snake draft on last season's points per game, times
 *       1 + N(0, {@value #DRAFT_NOISE}) per league so leagues differ; caps of 2 QB, 5 RB,
 *       6 WR, 2 TE, and every team fills 1 QB, 2 RB, 2 WR, 1 TE;</li>
 *   <li>lineups 1 QB, 2 RB, 2 WR, 1 TE, 1 flex, chosen each week by {@link LineupOptimizer}
 *       on production's projection, cut at the week through {@link PlayerHistory#before};</li>
 *   <li>known before kickoff and used: the schedule (a bye projects 0). Not used: injury
 *       designations, which this dataset does not carry -- so a starter who sits scores 0 and
 *       the model is tested without the availability rules production applies;</li>
 *   <li>random pairings each week; a game is one (p, outcome) row, a tie scoring 0.5.</li>
 * </ul>
 *
 * <p>Known in-sample term: σ (two numbers a position) was fitted on these same seasons
 * ({@code UncertaintyFit}). The projections themselves are out of sample.
 */
final class WinCalibration {

    static final List<Integer> SEASONS = List.of(2021, 2022, 2023, 2024, 2025);
    static final int LEAGUES = 20;
    static final int TEAMS = 12;
    static final int ROUNDS = 13;
    static final int WEEKS = 14;
    static final int MIN_PRIOR_GAMES = 4;
    static final double DRAFT_NOISE = 0.15;
    static final long SEED = 20261030L;

    static final double BASELINE_BRIER = 0.25;
    static final double MAX_BRIER = 0.225;
    static final double MIN_SLOPE = 0.8;
    static final double MAX_SLOPE = 1.2;

    private static final Map<String, Integer> CAPS = Map.of("QB", 2, "RB", 5, "WR", 6, "TE", 2);
    private static final Map<String, Integer> NEEDS = Map.of("QB", 1, "RB", 2, "WR", 2, "TE", 1);
    private static final Map<RosterSlot, Integer> LINEUP = Map.of(RosterSlot.QB, 1, RosterSlot.RB, 2,
            RosterSlot.WR, 2, RosterSlot.TE, 1, RosterSlot.FLEX_RWT, 1);

    private record Drafted(long id, String position, double value) {}

    /** One matchup: both teams' modelled weeks, so the spread multiplier is applied when scored. */
    private record Row(int season, TeamWeek a, TeamWeek b, double outcome) {
        double p(double k) {
            return WinProbability.beats(a, b, k);
        }
    }

    /** Attempt 3: fitted on these, tested on those. */
    static final List<Integer> TRAIN = List.of(2021, 2022, 2023);
    static final List<Integer> TEST = List.of(2024, 2025);

    private WinCalibration() {}

    /** Attempt 1, exactly as pre-registered and run: no availability, no K or DST. */
    static void run(Connection connection) throws Exception {
        run(connection, null);
    }

    /**
     * Attempt 2 (pre-registered in docs/perf/trade-calibration.md): attempt 1 plus production's
     * availability rules and K/DST as production treats them. Skill drafts and pairings use the
     * same random streams as attempt 1; K/DST draws come from their own.
     */
    static void runV2(Connection connection, Path cache) throws Exception {
        run(connection, Attempt2.load(connection, cache));
    }

    private static void run(Connection connection, Attempt2 v2) throws Exception {
        BacktestData data = BacktestData.load(connection);
        List<Row> rows = simulate(data, v2, SEASONS, WinProbability.productionSigma(),
                v2 == null ? 0 : v2.unmodeledVariance());
        report(rows, v2, 1.0, null);
    }

    /**
     * Attempt 3 (pre-registered in docs/perf/trade-calibration.md): attempt 2's harness with the
     * skill σ, σ_K, σ_DST and the spread multiplier k all fitted on {@link #TRAIN}, and the gate
     * read on {@link #TEST} alone.
     */
    static void runV3(Connection connection, Path cache) throws Exception {
        BacktestData data = BacktestData.load(connection);
        Attempt2 v2 = Attempt2.load(connection, cache);
        Map<String, UncertaintyFit.Fit> fit = UncertaintyFit.fitHalfPpr(data, TRAIN);
        WinProbability.Sigma sigma = (position, mu) -> {
            UncertaintyFit.Fit f = fit.get(position);
            return f == null ? 0 : f.a() + f.b() * Math.max(0, mu);
        };
        double variance = v2.unmodeledVariance(TRAIN);

        List<Row> train = simulate(data, v2, TRAIN, sigma, variance);
        double k = 1.0;
        double best = Double.POSITIVE_INFINITY;
        StringBuilder grid = new StringBuilder();
        for (int step = 0; step <= 20; step++) {
            double candidate = 1.0 + step * 0.05;
            double brier = brier(train, candidate);
            grid.append(String.format(Locale.ROOT, "%.2f: %.5f; ", candidate, brier));
            if (brier < best) { // strictly less: ties keep the smaller k
                best = brier;
                k = candidate;
            }
        }
        List<Row> test = simulate(data, v2, TEST, sigma, variance);

        StringBuilder fitted = new StringBuilder(String.format(Locale.ROOT,
                "Fitted on %s, %,d training matchups. Skill σ (Half PPR): ", TRAIN, train.size()));
        fit.forEach((position, f) -> fitted.append(String.format(Locale.ROOT, "%s %.3f + %.4fμ; ", position, f.a(), f.b())));
        fitted.append(String.format(Locale.ROOT, "σ_K %.3f, σ_DST %.3f (training seasons). "
                + "k grid (Brier on training): %s→ **k = %.2f** (training Brier %.4f).",
                v2.sigmaK(TRAIN), v2.sigmaDst(TRAIN), grid, k, best));
        report(test, v2, k, fitted.toString());
    }

    private static List<Row> simulate(BacktestData data, Attempt2 v2, List<Integer> seasons,
            WinProbability.Sigma sigma, double unmodeledVariance) {
        ResolvedRuleset half = UncertaintyFit.ruleset("Half PPR");
        WeightedAverageProjectionProvider production = new WeightedAverageProjectionProvider();
        Map<Long, List<HistoricalGame>> careers = new HashMap<>();
        Map<String, Double> projected = new HashMap<>();
        List<Row> rows = new ArrayList<>();

        for (int season : seasons) {
            List<Drafted> pool = pool(data, season, half);
            for (int league = 0; league < LEAGUES; league++) {
                Random random = new Random(SEED + season * 1000L + league);
                List<List<Drafted>> rosters = draft(pool, random);
                Random kdst = new Random(SEED + season * 1000L + league + 777_000L);
                long[] kickers = v2 == null ? null : v2.assignKickers(season, kdst);
                int[] defenses = v2 == null ? null : v2.assignDefenses(kdst);
                for (int week = 1; week <= WEEKS; week++) {
                    TeamWeek[] model = new TeamWeek[TEAMS];
                    double[] actual = new double[TEAMS];
                    for (int t = 0; t < TEAMS; t++) {
                        List<Candidate> candidates = new ArrayList<>();
                        Map<Long, Integer> nflTeam = new HashMap<>();
                        for (Drafted d : rosters.get(t)) {
                            Integer team = teamBefore(data, d.id(), season, week);
                            nflTeam.put(d.id(), team);
                            boolean bye = team == null
                                    || data.gamesIn(season, week).stream().noneMatch(g -> g.involves(team));
                            boolean sits = v2 != null && v2.sits(d.id(), season, week);
                            double mu = bye || sits ? 0 : mu(data, production, half, careers, projected, d, season, week);
                            candidates.add(new Candidate(d.id(), d.position(), mu));
                        }
                        Lineup lineup = LineupOptimizer.solve(LINEUP, candidates);
                        List<Starter> starters = new ArrayList<>();
                        for (Spot spot : lineup.spots()) {
                            if (spot.playerId() == null) {
                                continue;
                            }
                            String position = position(rosters.get(t), spot.playerId());
                            starters.add(new Starter(position, nflTeam.get(spot.playerId()), spot.points()));
                            actual[t] += actualPoints(data, half, spot.playerId(), position, season, week);
                        }
                        if (v2 == null) {
                            model[t] = WinProbability.team(starters, 0, sigma);
                        } else {
                            model[t] = WinProbability.team(starters, unmodeledVariance, sigma);
                            actual[t] += v2.kickerPoints(kickers[t], season, week, kdst)
                                    + v2.defensePoints(defenses[t], season, week, kdst);
                        }
                    }
                    List<Integer> order = new ArrayList<>();
                    for (int t = 0; t < TEAMS; t++) {
                        order.add(t);
                    }
                    Collections.shuffle(order, random);
                    for (int i = 0; i < TEAMS; i += 2) {
                        int a = order.get(i);
                        int b = order.get(i + 1);
                        double outcome = actual[a] > actual[b] ? 1 : actual[a] < actual[b] ? 0 : 0.5;
                        rows.add(new Row(season, model[a], model[b], outcome));
                    }
                }
            }
        }
        return rows;
    }

    /** Skill players with enough games last season and a game this one, valued by last season's PPG. */
    static List<Drafted> pool(BacktestData data, int season, ResolvedRuleset rules) {
        List<Drafted> pool = new ArrayList<>();
        data.players().forEach((id, timeline) -> {
            List<PlayerGame> last = timeline.all().stream().filter(g -> g.season() == season - 1).toList();
            boolean playsThisSeason = timeline.all().stream().anyMatch(g -> g.season() == season);
            if (last.size() < MIN_PRIOR_GAMES || !playsThisSeason) {
                return;
            }
            String position = last.getLast().position();
            if (!CAPS.containsKey(position)) {
                return;
            }
            double total = 0;
            for (PlayerGame g : last) {
                total += ScoringEngine.score(new StatLine(position, Arrays.copyOf(g.line(), StatKey.COUNT)), rules);
            }
            pool.add(new Drafted(id, position, total / last.size()));
        });
        pool.sort((a, b) -> a.id() < b.id() ? -1 : a.id() > b.id() ? 1 : 0); // stable before noise
        return pool;
    }

    /** A snake draft on noisy value, within caps, every team ending with its starters' positions. */
    static List<List<Drafted>> draft(List<Drafted> pool, Random random) {
        Map<Long, Double> board = new HashMap<>();
        for (Drafted d : pool) {
            board.put(d.id(), d.value() * (1 + DRAFT_NOISE * random.nextGaussian()));
        }
        List<Drafted> available = new ArrayList<>(pool);
        available.sort((a, b) -> Double.compare(board.get(b.id()), board.get(a.id())));
        List<List<Drafted>> rosters = new ArrayList<>();
        for (int t = 0; t < TEAMS; t++) {
            rosters.add(new ArrayList<>());
        }
        for (int round = 0; round < ROUNDS; round++) {
            for (int i = 0; i < TEAMS; i++) {
                int t = round % 2 == 0 ? i : TEAMS - 1 - i;
                List<Drafted> roster = rosters.get(t);
                Map<String, Integer> have = new HashMap<>();
                roster.forEach(d -> have.merge(d.position(), 1, Integer::sum));
                int unmet = NEEDS.entrySet().stream()
                        .mapToInt(e -> Math.max(0, e.getValue() - have.getOrDefault(e.getKey(), 0))).sum();
                boolean mustFill = ROUNDS - roster.size() <= unmet;
                Drafted pick = null;
                for (Drafted d : available) {
                    int count = have.getOrDefault(d.position(), 0);
                    boolean needed = count < NEEDS.get(d.position());
                    if (count < CAPS.get(d.position()) && (!mustFill || needed)) {
                        pick = d;
                        break;
                    }
                }
                if (pick == null) {
                    throw new IllegalStateException("draft ran out of eligible players");
                }
                available.remove(pick);
                roster.add(pick);
            }
        }
        return rosters;
    }

    /** His NFL team as of his last game before the week: what a manager knows. */
    private static Integer teamBefore(BacktestData data, long id, int season, int week) {
        List<PlayerGame> before = data.players().get(id).asOf(season, week);
        return before.isEmpty() ? null : before.getLast().teamId();
    }

    private static double mu(BacktestData data, WeightedAverageProjectionProvider production, ResolvedRuleset rules,
            Map<Long, List<HistoricalGame>> careers, Map<String, Double> projected, Drafted d, int season, int week) {
        return projected.computeIfAbsent(d.id() + ":" + season + ":" + week, key -> {
            List<HistoricalGame> career = careers.computeIfAbsent(d.id(), id -> data.players().get(id).all().stream()
                    .map(g -> new HistoricalGame(new SeasonWeek(g.season(), g.week()),
                            Arrays.copyOf(g.line(), StatKey.COUNT), g.line()[Quantity.PASS_ATT],
                            g.line()[Quantity.RUSH_ATT], g.line()[Quantity.TARGETS]))
                    .toList());
            Optional<RawProjection> p = production.project(PlayerHistory.before(new SeasonWeek(season, week), career));
            return p.map(r -> ScoringEngine.score(r.line(d.position()), rules)).orElse(0.0);
        });
    }

    private static double actualPoints(BacktestData data, ResolvedRuleset rules, long id, String position,
            int season, int week) {
        return data.players().get(id).at(season, week)
                .map(g -> ScoringEngine.score(new StatLine(position, Arrays.copyOf(g.line(), StatKey.COUNT)), rules))
                .orElse(0.0);
    }

    private static String position(List<Drafted> roster, long id) {
        return roster.stream().filter(d -> d.id() == id).findFirst().orElseThrow().position();
    }

    /**
     * @param k      the spread multiplier the rows are scored with; 1 for attempts 1 and 2
     * @param fitted attempt 3's training record; null for attempts 1 and 2
     */
    private static void report(List<Row> rows, Attempt2 v2, double k, String fitted) throws Exception {
        boolean v3 = fitted != null;
        double brier = brier(rows, k);
        double[][] bins = new double[10][3]; // sum p, sum outcome, n
        for (Row r : rows) {
            double p = r.p(k);
            int b = Math.min(9, (int) (p * 10));
            bins[b][0] += p;
            bins[b][1] += r.outcome();
            bins[b][2]++;
        }
        double sw = 0, sx = 0, sy = 0, sxx = 0, sxy = 0;
        StringBuilder table = new StringBuilder("| Bin | Games | Mean predicted | Observed |\n|---|---:|---:|---:|\n");
        for (int b = 0; b < 10; b++) {
            double n = bins[b][2];
            if (n == 0) {
                table.append(String.format(Locale.ROOT, "| %.1f–%.1f | 0 | – | – |%n", b / 10.0, (b + 1) / 10.0));
                continue;
            }
            double x = bins[b][0] / n;
            double y = bins[b][1] / n;
            sw += n;
            sx += n * x;
            sy += n * y;
            sxx += n * x * x;
            sxy += n * x * y;
            table.append(String.format(Locale.ROOT, "| %.1f–%.1f | %,d | %.3f | %.3f |%n", b / 10.0, (b + 1) / 10.0,
                    (int) n, x, y));
        }
        double slope = (sw * sxy - sx * sy) / (sw * sxx - sx * sx);
        double intercept = (sy - slope * sx) / sw;
        boolean brierPass = brier <= MAX_BRIER;
        boolean slopePass = slope >= MIN_SLOPE && slope <= MAX_SLOPE;

        StringBuilder md = new StringBuilder(v3
                ? "# Trade model calibration, attempt 3: fitted on 2021-2023, tested on 2024-2025\n\n"
                : v2 == null
                ? "# Trade model calibration: weekly win probability\n\n"
                : "# Trade model calibration, attempt 2: with availability, K and DST\n\n");
        if (v2 != null) {
            md.append(v2.summary()).append("\n\n");
        }
        if (v3) {
            md.append(fitted).append("\n\nEverything below is the **test** seasons only, scored with that k.\n\n");
        }
        md.append(String.format(Locale.ROOT, "Head: %s. Sources (sha256): %s.%n%n",
                System.getenv().getOrDefault("BACKTEST_HEAD", "unknown"), hashes()));
        List<Integer> seasons = rows.stream().map(Row::season).distinct().sorted().toList();
        md.append(String.format(Locale.ROOT, "%,d matchups: %d seasons × %d leagues × %d weeks × %d games.%n%n",
                rows.size(), seasons.size(), LEAGUES, WEEKS, TEAMS / 2));
        md.append("| Season | Games | Brier |\n|---|---:|---:|\n");
        for (int season : seasons) {
            List<Row> in = rows.stream().filter(r -> r.season() == season).toList();
            md.append(String.format(Locale.ROOT, "| %d | %,d | %.4f |%n", season, in.size(), brier(in, k)));
        }
        md.append(String.format(Locale.ROOT, "| **All** | %,d | **%.4f** |%n%n", rows.size(), brier));
        md.append(String.format(Locale.ROOT, "Baseline (always 0.5): %.4f. Skill: %.1f%% better. Bar: at most %.3f.%n%n",
                BASELINE_BRIER, 100 * (1 - brier / BASELINE_BRIER), MAX_BRIER));
        md.append(table).append('\n');
        md.append(String.format(Locale.ROOT, "Reliability slope %.3f (intercept %.3f), count-weighted over non-empty bins. "
                + "Bar: %.1f to %.1f.%n%n", slope, intercept, MIN_SLOPE, MAX_SLOPE));
        md.append(String.format(Locale.ROOT, "Brier: **%s**. Slope: **%s**. Gate: **%s**.%n",
                brierPass ? "pass" : "FAIL", slopePass ? "pass" : "FAIL",
                brierPass && slopePass ? "PASSED" : "FAILED -- hold Trades"));

        Files.createDirectories(Backtest.OUT);
        Files.writeString(Backtest.OUT.resolve(v3 ? "calibration-v3-results.md"
                : v2 == null ? "calibration-results.md" : "calibration-v2-results.md"), md.toString());
        System.out.println(md);
    }

    static double brier(List<Row> rows, double k) {
        return rows.stream().mapToDouble(r -> (r.p(k) - r.outcome()) * (r.p(k) - r.outcome())).average().orElse(Double.NaN);
    }

    /** What exactly produced the numbers: the harness and the model it tests. */
    private static String hashes() throws Exception {
        List<String> files = List.of(
                "src/test/java/com/fantasykai/projection/WinCalibration.java",
                "src/main/java/com/fantasykai/leagues/WinProbability.java",
                "src/main/java/com/fantasykai/leagues/ProjectionUncertainty.java",
                "src/main/java/com/fantasykai/leagues/LineupOptimizer.java",
                "src/test/java/com/fantasykai/projection/Backtest.java",
                "src/test/java/com/fantasykai/projection/UncertaintyFit.java",
                "../scripts/backtest.sh");
        List<String> out = new ArrayList<>();
        for (String file : files) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(file)));
            out.add(Path.of(file).getFileName() + " " + HexFormat.of().formatHex(digest).substring(0, 12));
        }
        return String.join(", ", out);
    }

    /**
     * Attempt 2's additions, all loaded up front: who could not play (production's
     * {@link AvailabilityRules}), and K and DST points scored with the captured Sleeper league's
     * defaults. See docs/perf/trade-calibration.md, attempt 2, for every rule here.
     */
    static final class Attempt2 {

        private record KickerWeek(int teamId, double points) {}

        private final Map<Long, String> gsis;
        private final Map<String, String> roster;      // gsis:season:week -> status (final snapshot)
        private final Map<String, String> report;      // gsis:season:week -> most restrictive status
        private final Map<String, KickerWeek> kickerWeeks = new HashMap<>(); // id:season:week
        private final Map<Long, List<int[]>> kickerGames = new HashMap<>();  // id -> [season, week, team]
        private final Map<String, Double> defenseWeeks = new HashMap<>();    // team:season:week
        private final Map<String, java.util.Set<Integer>> playing = new HashMap<>(); // season:week -> teams
        private final List<String> files = new ArrayList<>();
        private final double sigmaK;
        private final double sigmaDst;
        private final Map<Integer, List<Double>> kBySeason = new HashMap<>();
        private final Map<Integer, List<Double>> dBySeason = new HashMap<>();
        private int sat;

        private Attempt2(Connection connection, Path cache) throws Exception {
            gsis = new HashMap<>();
            try (var st = connection.createStatement();
                    var rs = st.executeQuery("SELECT id, gsis_id FROM players WHERE gsis_id IS NOT NULL")) {
                while (rs.next()) {
                    gsis.put(rs.getLong(1), rs.getString(2));
                }
            }
            roster = new HashMap<>();
            report = new HashMap<>();
            for (int season : SEASONS) {
                for (var r : csv(cache.resolve("roster_weekly_" + season + ".csv"))) {
                    if ("REG".equals(r.get("game_type")) && !r.get("gsis_id").isBlank()) {
                        roster.put(r.get("gsis_id") + ":" + season + ":" + r.get("week"), r.get("status"));
                    }
                }
                for (var r : csv(cache.resolve("injuries_" + season + ".csv"))) {
                    if ("REG".equals(r.get("game_type")) && !r.get("report_status").isBlank()) {
                        report.merge(r.get("gsis_id") + ":" + season + ":" + r.get("week"), r.get("report_status"),
                                (a, b) -> com.fantasykai.projections.AvailabilityRules.restrictiveness(a)
                                        >= com.fantasykai.projections.AvailabilityRules.restrictiveness(b) ? a : b);
                    }
                }
            }

            List<Double> kPoints = new ArrayList<>();
            try (var st = connection.createStatement(); var rs = st.executeQuery("""
                    SELECT s.player_id, s.season, s.week, s.team_id,
                           s.fg_made_0_19 + s.fg_made_20_29 + s.fg_made_30_39 AS short, s.fg_made_40_49 AS mid,
                           s.fg_made_50_59 + s.fg_made_60_plus AS long, s.fg_missed, s.pat_made, s.pat_missed
                      FROM player_game_stats s JOIN players p ON p.id = s.player_id JOIN games g ON g.id = s.game_id
                     WHERE g.season_type = 'REG' AND p.position = 'K' AND s.season BETWEEN 2020 AND 2025
                     ORDER BY s.season, s.week
                    """)) {
                while (rs.next()) {
                    double points = 3 * rs.getInt("short") + 4 * rs.getInt("mid") + 5 * rs.getInt("long")
                            - rs.getInt("fg_missed") + rs.getInt("pat_made") - rs.getInt("pat_missed");
                    long id = rs.getLong("player_id");
                    int season = rs.getInt("season");
                    int week = rs.getInt("week");
                    kickerWeeks.put(id + ":" + season + ":" + week, new KickerWeek(rs.getInt("team_id"), points));
                    kickerGames.computeIfAbsent(id, k -> new ArrayList<>()).add(new int[] {season, week, rs.getInt("team_id")});
                    if (season >= SEASONS.getFirst()) {
                        kPoints.add(points);
                        kBySeason.computeIfAbsent(season, x -> new ArrayList<>()).add(points);
                    }
                }
            }

            Map<String, double[]> defense = new HashMap<>(); // team:season:week -> summed columns
            try (var st = connection.createStatement(); var rs = st.executeQuery("""
                    SELECT s.team_id, s.season, s.week, sum(s.def_sacks) AS sacks, sum(s.def_int) AS ints,
                           sum(s.def_fumble_rec) AS fum_rec, sum(s.def_safety) AS safeties, sum(s.def_td) AS tds,
                           sum(s.def_fumbles_forced) AS ff, sum(s.def_blocked_kicks) AS blocks
                      FROM player_game_stats s JOIN games g ON g.id = s.game_id
                     WHERE g.season_type = 'REG' AND s.season BETWEEN 2021 AND 2025
                     GROUP BY s.team_id, s.season, s.week
                    """)) {
                while (rs.next()) {
                    defense.put(rs.getInt("team_id") + ":" + rs.getInt("season") + ":" + rs.getInt("week"),
                            new double[] {rs.getDouble("sacks"), rs.getDouble("ints"), rs.getDouble("fum_rec"),
                                    rs.getDouble("safeties"), rs.getDouble("tds"), rs.getDouble("ff"), rs.getDouble("blocks")});
                }
            }
            List<Double> dPoints = new ArrayList<>();
            try (var st = connection.createStatement(); var rs = st.executeQuery("""
                    SELECT season, week, home_team_id, away_team_id, home_score, away_score FROM games
                     WHERE season_type = 'REG' AND season BETWEEN 2021 AND 2025 AND home_score IS NOT NULL
                    """)) {
                while (rs.next()) {
                    int season = rs.getInt("season");
                    int week = rs.getInt("week");
                    int home = rs.getInt("home_team_id");
                    int away = rs.getInt("away_team_id");
                    var teams = playing.computeIfAbsent(season + ":" + week, k -> new java.util.HashSet<>());
                    teams.add(home);
                    teams.add(away);
                    for (int[] side : new int[][] {{home, rs.getInt("away_score")}, {away, rs.getInt("home_score")}}) {
                        double[] d = defense.getOrDefault(side[0] + ":" + season + ":" + week, new double[7]);
                        double points = d[0] + 2 * d[1] + 2 * d[2] + 2 * d[3] + 6 * d[4] + d[5] + 2 * d[6]
                                + pointsAllowed(side[1]);
                        defenseWeeks.put(side[0] + ":" + season + ":" + week, points);
                        dPoints.add(points);
                        dBySeason.computeIfAbsent(season, x -> new ArrayList<>()).add(points);
                    }
                }
            }
            sigmaK = sd(kPoints);
            sigmaDst = sd(dPoints);
            this.kPointsCount = kPoints.size();
            this.dPointsCount = dPoints.size();
        }

        private final int kPointsCount;
        private final int dPointsCount;

        static Attempt2 load(Connection connection, Path cache) throws Exception {
            return new Attempt2(connection, cache);
        }

        /** Points allowed, Sleeper's default tiers. */
        static double pointsAllowed(int points) {
            if (points == 0) {
                return 10;
            }
            if (points <= 6) {
                return 7;
            }
            if (points <= 13) {
                return 4;
            }
            if (points <= 20) {
                return 1;
            }
            if (points <= 27) {
                return 0;
            }
            return points <= 34 ? -1 : -4;
        }

        double unmodeledVariance() {
            return sigmaK * sigmaK + sigmaDst * sigmaDst;
        }

        /** Attempt 3: σ_K and σ_DST measured on {@code seasons} only. */
        double unmodeledVariance(List<Integer> seasons) {
            return sigmaK(seasons) * sigmaK(seasons) + sigmaDst(seasons) * sigmaDst(seasons);
        }

        double sigmaK(List<Integer> seasons) {
            return sd(seasons.stream().flatMap(s -> kBySeason.getOrDefault(s, List.of()).stream()).toList());
        }

        double sigmaDst(List<Integer> seasons) {
            return sd(seasons.stream().flatMap(s -> dBySeason.getOrDefault(s, List.of()).stream()).toList());
        }

        /** Out this week, or off a roster last week: production's rule, pregame data only. */
        boolean sits(long id, int season, int week) {
            String g = gsis.get(id);
            if (g == null) {
                return false;
            }
            String previous = week > 1 ? roster.get(g + ":" + season + ":" + (week - 1)) : null;
            var status = com.fantasykai.projections.AvailabilityRules.classify(previous,
                    report.get(g + ":" + season + ":" + week));
            boolean sits = status == com.fantasykai.projections.AvailabilityStatus.OUT
                    || status == com.fantasykai.projections.AvailabilityStatus.RESERVE
                    || status == com.fantasykai.projections.AvailabilityStatus.NOT_ON_ROSTER;
            if (sits) {
                sat++;
            }
            return sits;
        }

        /** Kickers with 4+ games last season and a game this one, {@value #TEAMS} drawn at random. */
        long[] assignKickers(int season, Random random) {
            List<Long> pool = new ArrayList<>();
            kickerGames.forEach((id, games) -> {
                long last = games.stream().filter(g -> g[0] == season - 1).count();
                boolean now = games.stream().anyMatch(g -> g[0] == season);
                if (last >= MIN_PRIOR_GAMES && now) {
                    pool.add(id);
                }
            });
            pool.sort(Long::compare);
            Collections.shuffle(pool, random);
            long[] out = new long[TEAMS];
            for (int t = 0; t < TEAMS; t++) {
                out[t] = pool.get(t);
            }
            return out;
        }

        /** {@value #TEAMS} of the NFL's teams, drawn at random. */
        int[] assignDefenses(Random random) {
            List<Integer> teams = new ArrayList<>(new java.util.TreeSet<>(playing.get(SEASONS.getFirst() + ":1")));
            Collections.shuffle(teams, random);
            int[] out = new int[TEAMS];
            for (int t = 0; t < TEAMS; t++) {
                out[t] = teams.get(t);
            }
            return out;
        }

        /** His points, 0 if he did not kick; on his team's bye, a random kicker who did -- streaming. */
        double kickerPoints(long kicker, int season, int week, Random random) {
            Integer team = null;
            for (int[] g : kickerGames.getOrDefault(kicker, List.of())) {
                if (g[0] < season || g[0] == season && g[1] < week) {
                    team = g[2];
                }
            }
            var teams = playing.getOrDefault(season + ":" + week, java.util.Set.of());
            if (team != null && teams.contains(team)) {
                KickerWeek k = kickerWeeks.get(kicker + ":" + season + ":" + week);
                return k == null ? 0 : k.points();
            }
            List<KickerWeek> streamable = kickerWeeks.entrySet().stream()
                    .filter(e -> e.getKey().endsWith(":" + season + ":" + week))
                    .sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
            return streamable.isEmpty() ? 0 : streamable.get(random.nextInt(streamable.size())).points();
        }

        /** The defense's points; on its bye, a random defense that played -- streaming. */
        double defensePoints(int team, int season, int week, Random random) {
            var teams = playing.getOrDefault(season + ":" + week, java.util.Set.of());
            if (teams.contains(team)) {
                return defenseWeeks.getOrDefault(team + ":" + season + ":" + week, 0.0);
            }
            List<Integer> others = new ArrayList<>(new java.util.TreeSet<>(teams));
            return others.isEmpty() ? 0
                    : defenseWeeks.getOrDefault(others.get(random.nextInt(others.size())) + ":" + season + ":" + week, 0.0);
        }

        String summary() {
            return String.format(Locale.ROOT, "Availability: %,d skill starter-candidate weeks projected 0 (Out, reserve "
                    + "or off a roster, from %,d roster and %,d report rows). K: %,d kicker-weeks 2021-2025, σ_K %.3f. "
                    + "DST: %,d team-weeks, σ_DST %.3f. nflverse files (sha256): %s.",
                    sat, roster.size(), report.size(), kPointsCount, sigmaK, dPointsCount, sigmaDst, String.join(", ", files));
        }

        private List<org.apache.commons.csv.CSVRecord> csv(Path file) throws Exception {
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException(file + " is not in the cache; run scripts/backtest.sh, which fills it");
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            files.add(file.getFileName() + " " + HexFormat.of().formatHex(digest).substring(0, 12));
            try (var reader = Files.newBufferedReader(file);
                    var parser = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader()
                            .setSkipHeaderRecord(true).get().parse(reader)) {
                return parser.getRecords();
            }
        }

        private static double sd(List<Double> values) {
            double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            return Math.sqrt(values.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0));
        }
    }
}
