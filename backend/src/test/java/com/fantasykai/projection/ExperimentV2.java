package com.fantasykai.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fantasykai.scoring.StatKey;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Experiment v2: can opportunity data nflverse already publishes (air yards), and a league
 * scoring-environment term, push the raw-stat model past the pre-registered 3% on 2026
 * weeks no choice has seen?
 *
 * <pre>
 * ./scripts/backtest.sh v2-validate      # 2021-23 train, 2024 picks λ and the candidate, freezes
 * ./scripts/backtest.sh v2-diagnostic    # frozen; refit 2021-24; 2025 shown, never chosen on
 * ./scripts/backtest.sh v2-prospective   # frozen; refit 2021-25; 2026's finished weeks: the test
 * </pre>
 *
 * <p>Held constant from v1, on purpose, so a change can be attributed to information rather
 * than to a new search: the G3 structure per position, the λ grid and procedure, the
 * shrinkage grids, the half-life, the baseline, and the CI-gated rule that decides whether
 * added columns earn their place. What changes is the data (v2's corrected dataset) and the
 * columns (v2a, v2b). 2025 is spent: no stage here selects on it, and v2-validate never
 * builds its population or reads its nflverse files.
 */
final class ExperimentV2 {

    static final Path FROZEN = Path.of("src", "test", "resources", "projection", "frozen-config-v2.json");

    static final List<Integer> TRAIN_FOR_VALIDATION = List.of(2021, 2022, 2023);
    static final int VALIDATION = 2024;
    static final List<Integer> TRAIN_FOR_DIAGNOSTIC = List.of(2021, 2022, 2023, 2024);
    static final int DIAGNOSTIC = 2025;
    static final List<Integer> TRAIN_FOR_PROSPECTIVE = List.of(2021, 2022, 2023, 2024, 2025);
    static final int PROSPECTIVE = 2026;

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Predicate<Case> P = c -> c.inP;

    enum Variant {
        V1_FROZEN("v1 (frozen λ)", false, false),
        V1_PRIME("v1′", false, false),
        V2A("v2a: + air yards", true, false),
        V2B("v2b: + air yards + environment", true, true);

        final String label;
        final boolean opportunity;
        final boolean environment;

        Variant(String label, boolean opportunity, boolean environment) {
            this.label = label;
            this.opportunity = opportunity;
            this.environment = environment;
        }

        ProjectionModel.Config config(Map<String, Double> lambdas) {
            return new ProjectionModel.Config(ProjectionModel.Group.G3, false, false, opportunity,
                    environment, lambdas);
        }
    }

    /** What v2-validate froze. {@code candidate} is a {@link Variant} name. */
    record Frozen(double halfLife, Map<String, Map<String, Double>> lambdas, String candidate,
            List<List<String>> gate) {}

    private ExperimentV2() {}

    static void run(String stage, Connection connection, String frozenCommit) throws Exception {
        String cacheDir = System.getenv("NFLVERSE_CACHE");
        if (cacheDir == null || cacheDir.isBlank()) {
            throw new IllegalStateException("NFLVERSE_CACHE is not set; run through scripts/backtest.sh");
        }
        Path cache = Path.of(cacheDir);
        Backtest.Frozen v1 = JSON.readValue(Backtest.FROZEN.toFile(), Backtest.Frozen.class);
        if (v1.group() != ProjectionModel.Group.G3 || v1.pooledReceiving() || v1.pooledRushing()) {
            throw new IllegalStateException("v2 is built on v1's frozen G3 per-position structure");
        }
        List<League> leagues = League.load(connection);
        switch (stage) {
            case "v2-validate" -> validate(connection, cache, v1, leagues);
            case "v2-diagnostic" -> confirm(connection, cache, v1, leagues, frozenCommit,
                    TRAIN_FOR_DIAGNOSTIC, DIAGNOSTIC, stage);
            case "v2-prospective" -> confirm(connection, cache, v1, leagues, frozenCommit,
                    TRAIN_FOR_PROSPECTIVE, PROSPECTIVE, stage);
            default -> throw new IllegalArgumentException("unknown stage " + stage);
        }
    }

    // ---- stages ----------------------------------------------------------------------

    private static void validate(Connection connection, Path cache, Backtest.Frozen v1,
            List<League> leagues) throws Exception {
        // Only the files up to the validation season are even read.
        BacktestData data = BacktestData.load(connection, NflverseExtras.read(cache, 2020, VALIDATION));
        Map<Integer, Population.Season> seasons = new TreeMap<>();
        for (int season = TRAIN_FOR_VALIDATION.get(0); season <= VALIDATION; season++) {
            seasons.put(season, Population.build(data, season));
        }
        Priors priors = Priors.from(data, TRAIN_FOR_VALIDATION);
        List<Case> train = trainingRows(seasons, TRAIN_FOR_VALIDATION);
        Population.Season val = seasons.get(VALIDATION);
        Backtest.featurize(train, priors, v1.ewmaHalfLife());
        Backtest.featurize(val.cases(), priors, v1.ewmaHalfLife());
        List<Case> valP = val.where(P);

        // λ per ridge model, per variant: v1's procedure, unchanged -- the grid included.
        Map<String, Map<String, Double>> lambdas = new LinkedHashMap<>();
        for (Variant v : List.of(Variant.V1_PRIME, Variant.V2A, Variant.V2B)) {
            ProjectionModel.Config probe = v.config(Map.of());
            Map<String, Double> chosen = new LinkedHashMap<>();
            for (ProjectionModel.Key key : ProjectionModel.keys(false, false)) {
                int[][] design = ProjectionModel.design(key, probe);
                double best = Backtest.LAMBDAS[0];
                double bestRmse = Double.MAX_VALUE;
                for (double lambda : Backtest.LAMBDAS) {
                    Ridge ridge = ProjectionModel.fitKey(key, probe, train, lambda);
                    double sq = 0;
                    int n = 0;
                    for (Case c : valP) {
                        if (ProjectionModel.covers(key, c)) {
                            double e = Math.max(0, ridge.predict(ProjectionModel.row(c.features, design)))
                                    - c.actualLine()[key.q()];
                            sq += e * e;
                            n++;
                        }
                    }
                    if (Math.sqrt(sq / n) < bestRmse) {
                        bestRmse = Math.sqrt(sq / n);
                        best = lambda;
                    }
                }
                chosen.put(key.toString(), best);
            }
            lambdas.put(v.name(), chosen);
        }

        Run run = evaluate(v1, lambdas, leagues, train, val, priors);
        int ppr = run.eval().league(League.PPR);

        // The candidate: v1's CI-gated rule, as it decided G3 -- columns are adopted only when
        // their paired PPR error against the current choice has a CI entirely below 0.
        List<List<String>> gate = new ArrayList<>();
        Variant candidate = Variant.V1_PRIME;
        for (Variant challenger : List.of(Variant.V2A, Variant.V2B)) {
            Evaluation.Interval i = run.eval().paired(challenger.label, candidate.label, ppr, P,
                    Backtest.REPS, Backtest.SEED);
            boolean adopt = i.hi() < 0;
            gate.add(List.of(challenger.label, candidate.label, Backtest.f3(i.diff()),
                    "[" + Backtest.f3(i.lo()) + ", " + Backtest.f3(i.hi()) + "]", adopt ? "yes" : "no"));
            if (adopt) {
                candidate = challenger;
            }
        }
        Frozen frozen = new Frozen(v1.ewmaHalfLife(), lambdas, candidate.name(), gate);
        Files.createDirectories(FROZEN.getParent());
        Files.writeString(FROZEN, JSON.writeValueAsString(frozen) + "\n");

        StringBuilder md = new StringBuilder();
        md.append("# Experiment v2 -- validation (").append(VALIDATION).append(")\n\n");
        md.append("λ per variant and the candidate are chosen here; optimistic by construction. "
                + "2025 and 2026 are not read by this stage.\n\n");
        Backtest.header(md, data, VALIDATION);
        md.append("## The candidate (v1's CI-gated rule)\n\n");
        List<String[]> rows = new ArrayList<>();
        gate.forEach(g -> rows.add(g.toArray(String[]::new)));
        Backtest.table(md, new String[] {"Challenger", "Against", "Diff PPR abs. error", "95% CI",
            "Adopted"}, rows);
        md.append("\n**Candidate: ").append(candidate.label).append("**\n\n");
        md.append("## λ chosen per ridge model (2024 RMSE of its own stat)\n\n");
        rows.clear();
        for (ProjectionModel.Key key : ProjectionModel.keys(false, false)) {
            List<String> row = new ArrayList<>(List.of(key.toString(),
                    Backtest.g(v1.lambdas().get("G3").get(key.toString()))));
            for (Variant v : List.of(Variant.V1_PRIME, Variant.V2A, Variant.V2B)) {
                row.add(Backtest.g(lambdas.get(v.name()).get(key.toString())));
            }
            rows.add(row.toArray(String[]::new));
        }
        Backtest.table(md, new String[] {"Model", "v1 frozen", "v1′", "v2a", "v2b"}, rows);
        md.append('\n');
        report(md, run, seasons, candidate, false);
        write(md, "v2-validate");
        System.out.println("wrote " + FROZEN);
    }

    private static void confirm(Connection connection, Path cache, Backtest.Frozen v1,
            List<League> leagues, String frozenCommit, List<Integer> trainSeasons, int evalSeason,
            String stage) throws Exception {
        Frozen frozen = JSON.readValue(FROZEN.toFile(), Frozen.class);
        BacktestData data = BacktestData.load(connection, NflverseExtras.read(cache, 2020, evalSeason));
        Map<Integer, Population.Season> seasons = new TreeMap<>();
        for (int season : trainSeasons) {
            seasons.put(season, Population.build(data, season));
        }
        seasons.put(evalSeason, Population.build(data, evalSeason));
        Priors priors = Priors.from(data, trainSeasons);
        List<Case> train = trainingRows(seasons, trainSeasons);
        Population.Season eval = seasons.get(evalSeason);
        Backtest.featurize(train, priors, frozen.halfLife());
        Backtest.featurize(eval.cases(), priors, frozen.halfLife());
        Run run = evaluate(v1, frozen.lambdas(), leagues, train, eval, priors);

        StringBuilder md = new StringBuilder();
        md.append("# Experiment v2 -- ").append(stage.substring(3)).append(" (").append(evalSeason)
                .append(")\n\n");
        md.append("Choices frozen in `frozen-config-v2.json`")
                .append(frozenCommit.isBlank() ? "" : " at commit `" + frozenCommit + "`")
                .append("; refit on ").append(trainSeasons.get(0)).append("–")
                .append(trainSeasons.get(trainSeasons.size() - 1)).append(". ")
                .append("prospective".equals(stage.substring(3))
                        ? "**This is the acceptance test.**"
                        : "**Diagnostic only: nothing is chosen on this season.**")
                .append("\n\n");
        Backtest.header(md, data, evalSeason);
        report(md, run, seasons, Variant.valueOf(frozen.candidate()), "v2-prospective".equals(stage));
        write(md, stage);
    }

    // ---- one evaluation --------------------------------------------------------------

    record Run(Evaluation eval, String baseline, int season) {}

    private static List<Case> trainingRows(Map<Integer, Population.Season> seasons, List<Integer> train) {
        List<Case> rows = new ArrayList<>();
        for (int season : train) {
            rows.addAll(seasons.get(season).where(P));
        }
        return rows;
    }

    private static Run evaluate(Backtest.Frozen v1, Map<String, Map<String, Double>> lambdas,
            List<League> leagues, List<Case> train, Population.Season eval, Priors priors) {
        Evaluation e = new Evaluation(eval.cases(), leagues);
        Baseline baseline = v1.best();
        e.add(baseline.label(), Backtest.project(eval.cases(), baseline), null);
        for (Variant v : Variant.values()) {
            Map<String, Double> l = v == Variant.V1_FROZEN ? v1.lambdas().get("G3") : lambdas.get(v.name());
            e.add(v.label, Backtest.predict(ProjectionModel.fit(train, v.config(l), priors), eval.cases()), null);
        }
        return new Run(e, baseline.label(), eval.cases().get(0).season);
    }

    // ---- the report ------------------------------------------------------------------

    private static void report(StringBuilder md, Run run, Map<Integer, Population.Season> seasons,
            Variant candidate, boolean binding) {
        Evaluation e = run.eval();
        int ppr = e.league(League.PPR);
        String base = run.baseline();
        String model = candidate.label;
        List<String> methods = new ArrayList<>(List.of(base));
        for (Variant v : Variant.values()) {
            methods.add(v.label);
        }
        List<String[]> rows = new ArrayList<>();

        md.append("## Population (v2 dataset)\n\n");
        seasons.forEach((season, s) -> {
            Population.Counts c = s.counts();
            long zero = s.cases().stream().filter(x -> x.inP && x.actual.snapOnly()).count();
            rows.add(new String[] {Integer.toString(season), Integer.toString(c.weeks()), Backtest.n(c.p()),
                Backtest.n((int) zero), Backtest.n(c.p0()) + " (" + Backtest.n(c.p0DidNotPlay()) + " inactive)",
                Backtest.n(c.pAll()), Backtest.n(c.debuts())});
        });
        Backtest.table(md, new String[] {"Season", "Weeks", "P (official)", "of which zero-stat appearances",
            "P0", "P-all", "Debuts excluded"}, rows);
        md.append("\nP counts a player who appeared -- a stat row or offensive snaps. Inactive means "
                + "neither.\n\n");

        md.append("## Every method (").append(run.season()).append(", official population P)\n\n");
        rows.clear();
        List<String> leagues = List.of("0 PPR", "Half PPR", "PPR", "My league");
        for (String m : methods) {
            List<String> row = new ArrayList<>(List.of(m));
            for (String league : leagues) {
                row.add(Backtest.f3(e.fantasy(m, e.league(league), P).mae()));
            }
            Evaluation.Summary s = e.fantasy(m, ppr, P);
            row.add(Backtest.f3(s.rmse()));
            row.add(Backtest.f3(s.bias()));
            row.add(Backtest.f3(e.spearman(m, ppr, P)));
            rows.add(row.toArray(String[]::new));
        }
        Backtest.table(md, new String[] {"Method", "MAE 0 PPR", "MAE Half", "MAE PPR", "MAE My league",
            "RMSE PPR", "Bias PPR", "Spearman PPR"}, rows);
        md.append('\n');

        md.append("## Each model against ").append(base).append(" (PPR, P, week-resampled 95% CI)\n\n");
        rows.clear();
        for (Variant v : Variant.values()) {
            rows.add(Backtest.intervalRow(v.label, e.paired(v.label, base, ppr, P, Backtest.REPS, Backtest.SEED)));
        }
        Backtest.table(md, new String[] {"Model", "n", "Baseline MAE", "Model MAE", "Diff", "95% CI",
            "Improvement"}, rows);
        md.append("\nCandidate: **").append(model).append("**.\n\n");

        md.append("### By position and by time -- improvement over the baseline, PPR\n\n");
        List<Object[]> slices = new ArrayList<>();
        for (String position : Priors.POSITIONS) {
            slices.add(new Object[] {position, P.and(c -> position.equals(c.position))});
        }
        TreeSet<Integer> weeks = new TreeSet<>();
        e.cases.forEach(c -> weeks.add(c.week));
        if (weeks.size() <= 6) {
            for (int w : weeks) {
                slices.add(new Object[] {"Week " + w, P.and(c -> c.week == w)});
            }
        } else {
            for (int[] b : new int[][] {{1, 3}, {4, 9}, {10, 22}}) {
                slices.add(new Object[] {"Weeks " + b[0] + "–" + Math.min(b[1], 18),
                    P.and(c -> c.week >= b[0] && c.week <= b[1])});
            }
        }
        rows.clear();
        for (Object[] slice : slices) {
            @SuppressWarnings("unchecked")
            Predicate<Case> f = (Predicate<Case>) slice[1];
            List<String> row = new ArrayList<>(List.of((String) slice[0]));
            Evaluation.Interval ci = e.paired(model, base, ppr, f, Backtest.REPS, Backtest.SEED);
            row.add(Backtest.n(ci.n()));
            row.add(Backtest.f3(ci.baseMae()));
            for (Variant v : Variant.values()) {
                Evaluation.Interval i = e.paired(v.label, base, ppr, f, Backtest.REPS, Backtest.SEED);
                row.add(Backtest.pct(i.relative()));
            }
            row.add("[" + Backtest.f3(ci.lo()) + ", " + Backtest.f3(ci.hi()) + "]");
            rows.add(row.toArray(String[]::new));
        }
        Backtest.table(md, new String[] {"Slice", "n", "Baseline MAE", "v1 frozen", "v1′", "v2a", "v2b",
            "Candidate CI"}, rows);
        md.append('\n');

        md.append("### The candidate under every league\n\n");
        rows.clear();
        for (String league : leagues) {
            rows.add(Backtest.intervalRow(league, e.paired(model, base, e.league(league), P,
                    Backtest.REPS, Backtest.SEED)));
        }
        Backtest.table(md, new String[] {"League", "n", "Baseline MAE", "Model MAE", "Diff", "95% CI",
            "Improvement"}, rows);
        md.append('\n');

        Evaluation.Interval overall = e.paired(model, base, ppr, P, Backtest.REPS, Backtest.SEED);
        boolean c1 = overall.hi() < 0;
        boolean c2 = overall.relative() >= Backtest.MIN_RELATIVE_IMPROVEMENT;
        boolean c3 = true;
        StringBuilder positions = new StringBuilder();
        for (String position : Priors.POSITIONS) {
            Evaluation.Interval i = e.paired(model, base, ppr, P.and(c -> position.equals(c.position)),
                    Backtest.REPS, Backtest.SEED);
            c3 &= i.lo() <= 0;
            positions.append(positions.isEmpty() ? "" : ", ").append(position).append(' ')
                    .append(Backtest.f3(i.lo()));
        }
        double bias = e.fantasy(model, ppr, P).bias();
        boolean c4 = Math.abs(bias) <= Backtest.MAX_ABS_BIAS;
        md.append("### The pre-registered ship rule, on the candidate")
                .append(binding ? " -- binding" : " (informative only here)").append("\n\n");
        rows.clear();
        rows.add(new String[] {"1", "PPR MAE CI entirely below 0",
            "[" + Backtest.f3(overall.lo()) + ", " + Backtest.f3(overall.hi()) + "]", Backtest.pass(c1)});
        rows.add(new String[] {"2", "Improvement ≥ " + Backtest.pct(Backtest.MIN_RELATIVE_IMPROVEMENT),
            Backtest.pct(overall.relative()), Backtest.pass(c2)});
        rows.add(new String[] {"3", "No position's CI entirely above 0", positions.toString(), Backtest.pass(c3)});
        rows.add(new String[] {"4", "Absolute bias ≤ " + Backtest.MAX_ABS_BIAS + " PPR pts",
            Backtest.f3(bias), Backtest.pass(c4)});
        Backtest.table(md, new String[] {"#", "Criterion", "Measured", "Result"}, rows);
        md.append("\n**").append(c1 && c2 && c3 && c4 ? "All four pass." : "Not all four pass.")
                .append("** Weekly clusters in this season: ").append(weeks.size()).append(".\n\n");

        md.append("## Receiving stats -- where air yards should act (RMSE, P)\n\n");
        rows.clear();
        int[] receiving = {Quantity.TARGETS, Quantity.of(StatKey.REC), Quantity.of(StatKey.REC_YD),
            Quantity.of(StatKey.REC_TD)};
        for (String position : List.of("RB", "WR", "TE")) {
            Predicate<Case> f = P.and(c -> position.equals(c.position));
            for (int q : receiving) {
                double b = e.stat(base, q, f).rmse();
                double v1p = e.stat(Variant.V1_PRIME.label, q, f).rmse();
                double a = e.stat(Variant.V2A.label, q, f).rmse();
                double bb = e.stat(Variant.V2B.label, q, f).rmse();
                rows.add(new String[] {position, Quantity.name(q), Backtest.f3(b), Backtest.f3(v1p),
                    Backtest.f3(a), Backtest.f3(bb), Backtest.pct((v1p - a) / v1p)});
            }
        }
        Backtest.table(md, new String[] {"Pos", "Stat", "Baseline", "v1′", "v2a", "v2b",
            "v2a better than v1′ by"}, rows);
        md.append('\n');

        md.append("## Availability -- diagnostic, never the acceptance population\n\n");
        rows.clear();
        Predicate<Case> all = c -> c.inP0;
        Predicate<Case> appeared = c -> c.inP0 && c.actual != null;
        Predicate<Case> inactive = c -> c.inP0 && c.actual == null;
        for (String m : new java.util.LinkedHashSet<>(List.of(base, Variant.V1_PRIME.label, model))) {
            Evaluation.Summary s0 = e.fantasy(m, ppr, all);
            Evaluation.Summary s1 = e.fantasy(m, ppr, appeared);
            Evaluation.Summary s2 = e.fantasy(m, ppr, inactive);
            double share = s2.n() == 0 ? 0 : s2.mae() * s2.n() / (s0.mae() * s0.n());
            rows.add(new String[] {m, Backtest.n(s0.n()) + " / " + Backtest.n(s1.n()) + " / " + Backtest.n(s2.n()),
                Backtest.f3(s0.mae()), Backtest.f3(s1.mae()), Backtest.f3(s2.mae()), Backtest.share(share),
                Backtest.f3(s2.mae() * s2.n() / Math.max(1, s0.n()))});
        }
        Backtest.table(md, new String[] {"Method", "n all / appeared / inactive", "MAE all projected",
            "MAE appeared", "MAE inactive", "Inactive share of error", "Knowing who is active would remove"},
            rows);
        md.append("\nAll projected = P0: the top players by trailing opportunity who played their team's "
                + "previous game, before kickoff. An inactive player scores 0, so all of his projection is "
                + "error.\n\n");

        md.append("## Bias -- does the environment term remove the seasonal swing?\n\n");
        rows.clear();
        for (String m : methods) {
            List<String> row = new ArrayList<>(List.of(m, Backtest.f3(e.fantasy(m, ppr, P).bias()),
                    Backtest.f3(e.medianError(m, ppr, P))));
            for (String position : Priors.POSITIONS) {
                row.add(Backtest.f3(e.fantasy(m, ppr, P.and(c -> position.equals(c.position))).bias()));
            }
            row.add(Backtest.f3(e.stat(m, Quantity.of(StatKey.PASS_YD), P.and(c -> "QB".equals(c.position))).bias()));
            row.add(Backtest.f3(e.stat(m, Quantity.of(StatKey.REC_YD), P.and(c -> "WR".equals(c.position))).bias()));
            rows.add(row.toArray(String[]::new));
        }
        Backtest.table(md, new String[] {"Method", "PPR bias", "PPR median error", "QB", "RB", "WR",
            "TE", "QB pass_yd", "WR rec_yd"}, rows);
        md.append("\nBias is the mean of projected minus actual; the median error is its median. "
                + "Position columns are PPR bias; the last two are raw-stat bias in yards.\n");
        md.append('\n');
    }

    private static void write(StringBuilder md, String stage) throws Exception {
        Files.createDirectories(Backtest.OUT);
        Files.writeString(Backtest.OUT.resolve(stage + "-results.md"), md.toString());
        System.out.println(md);
        System.out.println("wrote " + Backtest.OUT.resolve(stage + "-results.md"));
    }
}
