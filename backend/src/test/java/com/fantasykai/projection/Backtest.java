package com.fantasykai.projection;

import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Phase 6 Slice 1: can fantasy-kai project next week's raw stat lines better than simple
 * baselines, and are the points those lines score accurate under real rulesets?
 *
 * <pre>
 * ./scripts/backtest.sh validate     # 2021-23 train, 2024 chooses everything, writes frozen-config.json
 * ./scripts/backtest.sh test         # frozen choices, refit on 2021-24, 2025 read once
 * ./scripts/backtest.sh prospective  # frozen choices, refit on 2021-25, 2026 so far
 * </pre>
 *
 * <p>The split is the guarantee. Every choice -- windows, half-life, λ, feature groups,
 * pooling, which baseline is "best" -- is made on 2024 and written to
 * {@code frozen-config.json}; {@code backtest.sh} refuses the test stage until that file is
 * committed and unmodified, so the commit history shows the choices predate the test numbers.
 *
 * <p>Research code, test-scoped on purpose: nothing here ships in the jar. It moves to
 * {@code src/main} in 6c only if the verdict is to ship.
 */
public final class Backtest {

    static final List<Integer> TRAIN_FOR_VALIDATION = List.of(2021, 2022, 2023);
    static final int VALIDATION = 2024;
    static final List<Integer> TRAIN_FOR_TEST = List.of(2021, 2022, 2023, 2024);
    static final int TEST = 2025;
    static final List<Integer> TRAIN_FOR_PROSPECTIVE = List.of(2021, 2022, 2023, 2024, 2025);
    static final int PROSPECTIVE = 2026;

    static final int[] ROLLING_WINDOWS = {3, 4, 5};
    static final double[] HALF_LIVES = {1, 2, 3, 4, 6, 8};
    static final double[] LAMBDAS = {0.001, 0.01, 0.1, 1, 10};

    /** The pre-registered ship rule (plan §9), fixed before the test season is read. */
    static final double MIN_RELATIVE_IMPROVEMENT = 0.03;
    static final double MAX_ABS_BIAS = 0.5;
    static final int REPS = 2000;
    static final long SEED = 20261009L;

    static final Path OUT = Path.of("target", "backtest");
    static final Path FROZEN = Path.of("src", "test", "resources", "projection", "frozen-config.json");

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Predicate<Case> P = c -> c.inP;

    private Backtest() {}

    public static void main(String[] args) throws Exception {
        String stage = args.length > 0 ? args[0] : "validate";
        String frozenCommit = args.length > 1 ? args[1] : "";
        Files.createDirectories(OUT);
        try (Connection connection = DriverManager.getConnection(
                env("DB_URL"), env("DB_USERNAME"), env("DB_PASSWORD"))) {
            if (stage.startsWith("v2-")) {
                ExperimentV2.run(stage, connection, frozenCommit);
                return;
            }
            BacktestData data = BacktestData.load(connection);
            List<League> leagues = League.load(connection);
            switch (stage) {
                case "validate" -> validate(data, leagues);
                case "test" -> confirm(data, leagues, frozenCommit, TRAIN_FOR_TEST, TEST, "test");
                case "prospective" -> confirm(data, leagues, frozenCommit,
                        TRAIN_FOR_PROSPECTIVE, PROSPECTIVE, "prospective");
                default -> throw new IllegalArgumentException("unknown stage " + stage);
            }
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set; run through scripts/backtest.sh");
        }
        return value;
    }

    // ---- the frozen choices ----------------------------------------------------------

    record Frozen(Baseline.Kind bestKind, double bestParameter, int rollingK, double ewmaHalfLife,
            ProjectionModel.Group group, boolean pooledReceiving, boolean pooledRushing,
            Map<String, Map<String, Double>> lambdas) {

        Baseline best() {
            return new Baseline(bestKind, bestParameter);
        }

        List<Baseline> baselines() {
            return List.of(new Baseline(Baseline.Kind.PREVIOUS_GAME, 0),
                    new Baseline(Baseline.Kind.ROLLING, rollingK),
                    new Baseline(Baseline.Kind.SEASON_TO_DATE, 0),
                    new Baseline(Baseline.Kind.EWMA, ewmaHalfLife));
        }

        ProjectionModel.Config config(ProjectionModel.Group g) {
            return new ProjectionModel.Config(g, pooledReceiving, pooledRushing, lambdas.get(g.name()));
        }
    }

    // ---- stages ----------------------------------------------------------------------

    private static void validate(BacktestData data, List<League> leagues) throws IOException {
        Map<Integer, Population.Season> seasons = new TreeMap<>();
        for (int season = TRAIN_FOR_VALIDATION.get(0); season <= VALIDATION; season++) {
            seasons.put(season, Population.build(data, season));
        }
        Population.Season val = seasons.get(VALIDATION);
        int ppr = 2;

        // 1. Baselines: each window and half-life, then the best of the four kinds.
        Evaluation grid = new Evaluation(val.cases(), leagues);
        List<Baseline> candidates = new ArrayList<>();
        candidates.add(new Baseline(Baseline.Kind.PREVIOUS_GAME, 0));
        for (int k : ROLLING_WINDOWS) {
            candidates.add(new Baseline(Baseline.Kind.ROLLING, k));
        }
        candidates.add(new Baseline(Baseline.Kind.SEASON_TO_DATE, 0));
        for (double h : HALF_LIVES) {
            candidates.add(new Baseline(Baseline.Kind.EWMA, h));
        }
        for (Baseline b : candidates) {
            grid.add(b.label(), project(val.cases(), b), null);
        }
        Baseline rolling = bestOf(grid, candidates, Baseline.Kind.ROLLING, ppr);
        Baseline ewma = bestOf(grid, candidates, Baseline.Kind.EWMA, ppr);
        Baseline best = null;
        for (Baseline b : List.of(candidates.get(0), rolling,
                new Baseline(Baseline.Kind.SEASON_TO_DATE, 0), ewma)) {
            if (best == null || grid.fantasy(b.label(), ppr, P).mae()
                    < grid.fantasy(best.label(), ppr, P).mae()) {
                best = b;
            }
        }

        // 2. Features at the chosen half-life, priors from the training seasons only.
        double halfLife = ewma.parameter();
        Priors priors = Priors.from(data, TRAIN_FOR_VALIDATION);
        List<Case> train = new ArrayList<>();
        for (int season : TRAIN_FOR_VALIDATION) {
            train.addAll(seasons.get(season).where(P));
        }
        featurize(train, priors, halfLife);
        featurize(val.cases(), priors, halfLife);
        List<Case> valP = val.where(P);

        // 3. λ per ridge model and feature group, by validation RMSE of that model's stat.
        Set<ProjectionModel.Key> keys = new LinkedHashSet<>(ProjectionModel.keys(false, false));
        keys.addAll(ProjectionModel.keys(true, true));
        Map<String, Map<String, Double>> lambdas = new LinkedHashMap<>();
        for (ProjectionModel.Group g : ProjectionModel.Group.values()) {
            Map<String, Double> chosen = new LinkedHashMap<>();
            for (ProjectionModel.Key key : keys) {
                double bestLambda = LAMBDAS[0];
                double bestRmse = Double.MAX_VALUE;
                for (double lambda : LAMBDAS) {
                    Ridge ridge = ProjectionModel.fitKey(key, g, train, lambda);
                    int[][] design = ProjectionModel.design(key, g);
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
                    double rmse = Math.sqrt(sq / n);
                    if (rmse < bestRmse) {
                        bestRmse = rmse;
                        bestLambda = lambda;
                    }
                }
                chosen.put(key.toString(), bestLambda);
            }
            lambdas.put(g.name(), chosen);
        }

        // 4. Feature group × pooling. Complexity has to earn its place: start from the
        //    simplest model and adopt a richer group, or pooling, only when its paired PPR
        //    error against the current choice has a bootstrap CI entirely below zero. A
        //    minimum-MAE pick chose G4 over G3 on a 0.003 edge in the second validation run;
        //    that is choosing noise, and it pulled pooled rushing in with it.
        Evaluation configs = new Evaluation(valP, leagues);
        List<String[]> configRows = new ArrayList<>();
        Map<String, ProjectionModel.Config> byName = new LinkedHashMap<>();
        for (ProjectionModel.Group g : ProjectionModel.Group.values()) {
            for (boolean pooledRec : new boolean[] {false, true}) {
                for (boolean pooledRush : new boolean[] {false, true}) {
                    ProjectionModel.Config config =
                            new ProjectionModel.Config(g, pooledRec, pooledRush, lambdas.get(g.name()));
                    String name = configName(config);
                    configs.add(name, predict(ProjectionModel.fit(train, config, priors), valP), null);
                    byName.put(name, config);
                    Evaluation.Summary s = configs.fantasy(name, ppr, c -> true);
                    configRows.add(new String[] {g.name(), pooledRec ? "pooled" : "per position",
                            pooledRush ? "pooled" : "per position", f3(s.mae()), f3(s.rmse()),
                            f3(s.bias())});
                }
            }
        }
        List<String[]> steps = new ArrayList<>();
        ProjectionModel.Config chosen = byName.get(configName(
                new ProjectionModel.Config(ProjectionModel.Group.G1, false, false, Map.of())));
        List<ProjectionModel.Config> challengers = new ArrayList<>();
        for (ProjectionModel.Group g : ProjectionModel.Group.values()) {
            if (g != ProjectionModel.Group.G1) {
                challengers.add(new ProjectionModel.Config(g, false, false, Map.of()));
            }
        }
        for (ProjectionModel.Config challenger : challengers) {
            chosen = challenge(configs, byName, chosen, challenger, ppr, steps);
        }
        chosen = challenge(configs, byName, chosen,
                new ProjectionModel.Config(chosen.group(), true, chosen.pooledRushing(), Map.of()), ppr, steps);
        chosen = challenge(configs, byName, chosen,
                new ProjectionModel.Config(chosen.group(), chosen.pooledReceiving(), true, Map.of()), ppr, steps);

        Frozen frozen = new Frozen(best.kind(), best.parameter(), (int) rolling.parameter(),
                ewma.parameter(), chosen.group(), chosen.pooledReceiving(), chosen.pooledRushing(),
                lambdas);
        Files.createDirectories(FROZEN.getParent());
        Files.writeString(FROZEN, JSON.writeValueAsString(frozen) + "\n");

        // 5. The full report on 2024 with the frozen choices -- the same tables the test
        //    season will get, so the two can be read side by side.
        Run run = run(data, leagues, frozen, TRAIN_FOR_VALIDATION, VALIDATION, seasons);
        StringBuilder md = new StringBuilder();
        md.append("# Projection backtest -- validation (").append(VALIDATION).append(")\n\n");
        md.append("Every choice below was made on this season. Its numbers are optimistic by "
                + "construction; the test season is the one that counts.\n\n");
        header(md, data, VALIDATION);

        md.append("## Baseline grid (").append(VALIDATION).append(", P, PPR)\n\n");
        List<String[]> rows = new ArrayList<>();
        for (Baseline b : candidates) {
            Evaluation.Summary s = grid.fantasy(b.label(), ppr, P);
            rows.add(new String[] {b.label(), f3(s.mae()), f3(s.rmse()), f3(s.bias())});
        }
        table(md, new String[] {"Baseline", "MAE", "RMSE", "Bias"}, rows);
        md.append("\nChosen: rolling k = ").append((int) rolling.parameter())
                .append(", EWMA half-life = ").append(ewma.parameter())
                .append(" games, best baseline = **").append(best.label()).append("**.\n\n");

        md.append("## Feature group × pooling (").append(VALIDATION).append(", P, PPR)\n\n");
        table(md, new String[] {"Group", "Receiving", "Rushing", "MAE", "RMSE", "Bias"}, configRows);
        md.append("\nHow the choice was made -- each challenger against the choice so far, paired "
                + "PPR absolute error, 95% CI from ").append(REPS).append(" week resamples:\n\n");
        table(md, new String[] {"Challenger", "Against", "Diff", "95% CI", "Adopted"}, steps);
        md.append("\nChosen: **").append(chosen.group()).append("**, receiving ")
                .append(chosen.pooledReceiving() ? "pooled" : "per position").append(", rushing ")
                .append(chosen.pooledRushing() ? "pooled" : "per position").append(".\n\n");

        md.append("## λ chosen per ridge model (validation RMSE of its own stat)\n\n");
        List<String[]> lrows = new ArrayList<>();
        for (ProjectionModel.Key key : keys) {
            List<String> row = new ArrayList<>(List.of(key.toString()));
            for (ProjectionModel.Group g : ProjectionModel.Group.values()) {
                row.add(g(lambdas.get(g.name()).get(key.toString())));
            }
            lrows.add(row.toArray(String[]::new));
        }
        table(md, new String[] {"Model", "G1", "G2", "G3", "G4"}, lrows);
        md.append('\n');

        body(md, run, false);
        Files.writeString(OUT.resolve("validate-results.md"), md.toString());
        writeFeatures(run, train);
        writePredictions(run, "validate");
        System.out.println(md);
        System.out.println("wrote " + FROZEN + " and " + OUT.resolve("validate-results.md"));
    }

    private static void confirm(BacktestData data, List<League> leagues, String frozenCommit,
            List<Integer> trainSeasons, int evalSeason, String stage) throws IOException {
        Frozen frozen = JSON.readValue(FROZEN.toFile(), Frozen.class);
        Map<Integer, Population.Season> seasons = new TreeMap<>();
        for (int season : trainSeasons) {
            seasons.put(season, Population.build(data, season));
        }
        seasons.put(evalSeason, Population.build(data, evalSeason));
        Run run = run(data, leagues, frozen, trainSeasons, evalSeason, seasons);

        StringBuilder md = new StringBuilder();
        md.append("# Projection backtest -- ").append(stage).append(" (").append(evalSeason)
                .append(")\n\n");
        md.append("Choices frozen in `frozen-config.json`")
                .append(frozenCommit.isBlank() ? "" : " at commit `" + frozenCommit + "`")
                .append("; refit on ").append(trainSeasons.get(0)).append("–")
                .append(trainSeasons.get(trainSeasons.size() - 1)).append(" and evaluated on ")
                .append(evalSeason).append(".\n\n");
        header(md, data, evalSeason);
        body(md, run, "test".equals(stage));
        Files.writeString(OUT.resolve(stage + "-results.md"), md.toString());
        writePredictions(run, stage);
        System.out.println(md);
        System.out.println("wrote " + OUT.resolve(stage + "-results.md"));
    }

    // ---- one evaluation --------------------------------------------------------------

    record Run(Frozen frozen, int season, Map<Integer, Population.Counts> counts,
            Evaluation eval, List<String> methods, String best, String model,
            ProjectionModel chosen, Map<String, Double> shrink, BonusOdds odds) {}

    private static Run run(BacktestData data, List<League> leagues, Frozen frozen,
            List<Integer> trainSeasons, int evalSeason, Map<Integer, Population.Season> seasons) {
        Priors priors = Priors.from(data, trainSeasons);
        List<Case> train = new ArrayList<>();
        for (int season : trainSeasons) {
            train.addAll(seasons.get(season).where(P));
        }
        featurize(train, priors, frozen.ewmaHalfLife());
        Population.Season eval = seasons.get(evalSeason);
        featurize(eval.cases(), priors, frozen.ewmaHalfLife());

        Evaluation e = new Evaluation(eval.cases(), leagues);
        List<String> methods = new ArrayList<>();
        for (Baseline b : frozen.baselines()) {
            e.add(b.label(), project(eval.cases(), b), null);
            methods.add(b.label());
        }
        ProjectionModel chosen = null;
        String modelName = null;
        BonusOdds chosenOdds = null;
        for (ProjectionModel.Group g : ProjectionModel.Group.values()) {
            ProjectionModel model = ProjectionModel.fit(train, frozen.config(g), priors);
            String name = "Model " + g.name();
            BonusOdds odds = g == frozen.group() ? BonusOdds.fit(train, model::predict) : null;
            double[][] lines = predict(model, eval.cases());
            e.add(name, lines, odds);
            methods.add(name);
            if (g == frozen.group()) {
                chosen = model;
                modelName = name;
                int bonus = e.league("Bonus league");
                ExpectedPoints scorer = leagues.get(bonus).points();
                double[] onMean = new double[eval.cases().size()];
                double[] ignored = new double[eval.cases().size()];
                for (int i = 0; i < onMean.length; i++) {
                    StatLine mean = new StatLine(eval.cases().get(i).position, Quantity.scorable(lines[i]));
                    onMean[i] = scorer.stepped(mean);
                    ignored[i] = scorer.ignoringBonuses(mean);
                }
                e.addPoints(name + " · bonus on the mean line", bonus, onMean);
                e.addPoints(name + " · bonus ignored", bonus, ignored);
                chosenOdds = odds;
            }
        }
        Map<Integer, Population.Counts> counts = new TreeMap<>();
        seasons.forEach((season, s) -> counts.put(season, s.counts()));
        return new Run(frozen, evalSeason, counts, e, methods, frozen.best().label(), modelName,
                chosen, chosen.shrinkage(), chosenOdds);
    }

    static void featurize(List<Case> cases, Priors priors, double halfLife) {
        for (Case c : cases) {
            c.features = Features.compute(c, priors, halfLife);
        }
    }

    static double[][] project(List<Case> cases, Baseline baseline) {
        double[][] out = new double[cases.size()][];
        for (int i = 0; i < cases.size(); i++) {
            out[i] = baseline.project(cases.get(i).history, cases.get(i).season);
        }
        return out;
    }

    static double[][] predict(ProjectionModel model, List<Case> cases) {
        double[][] out = new double[cases.size()][];
        for (int i = 0; i < cases.size(); i++) {
            out[i] = model.predict(cases.get(i));
        }
        return out;
    }

    private static String configName(ProjectionModel.Config config) {
        return config.group() + (config.pooledReceiving() ? " rec-pooled" : "")
                + (config.pooledRushing() ? " rush-pooled" : "");
    }

    /** Adopts the challenger only if it is closer than the incumbent beyond resampling noise. */
    private static ProjectionModel.Config challenge(Evaluation configs,
            Map<String, ProjectionModel.Config> byName, ProjectionModel.Config incumbent,
            ProjectionModel.Config challenger, int league, List<String[]> steps) {
        String c = configName(challenger);
        String i = configName(incumbent);
        Evaluation.Interval interval = configs.paired(c, i, league, x -> true, REPS, SEED);
        boolean adopt = interval.hi() < 0;
        steps.add(new String[] {c, i, f3(interval.diff()),
            "[" + f3(interval.lo()) + ", " + f3(interval.hi()) + "]", adopt ? "yes" : "no"});
        return adopt ? byName.get(c) : incumbent;
    }

    private static Baseline bestOf(Evaluation grid, List<Baseline> candidates, Baseline.Kind kind,
            int league) {
        Baseline best = null;
        for (Baseline b : candidates) {
            if (b.kind() == kind && (best == null
                    || grid.fantasy(b.label(), league, P).mae() < grid.fantasy(best.label(), league, P).mae())) {
                best = b;
            }
        }
        return best;
    }

    // ---- the report ------------------------------------------------------------------

    /**
     * The data fingerprint, with every season after {@code throughSeason} withheld. The first
     * version printed all seasons, so the 2024 validation report showed 2025's league totals
     * -- a stage must not display the season it is not allowed to see. The sha256 still covers
     * every row, so the fingerprint stays complete without being readable.
     */
    static void header(StringBuilder md, BacktestData data, int throughSeason) {
        md.append("## Data\n\n");
        md.append("Local database, regular season only, QB/RB/WR/TE rows. Fingerprint (totals for "
                + "seasons after ").append(throughSeason).append(" withheld):\n\n");
        for (String line : data.fingerprint()) {
            boolean season = line.length() > 4 && line.charAt(4) == ':'
                    && line.substring(0, 4).chars().allMatch(Character::isDigit);
            if (!season || Integer.parseInt(line.substring(0, 4)) <= throughSeason) {
                md.append("- ").append(line).append('\n');
            }
        }
        String head = System.getenv("BACKTEST_HEAD");
        if (head != null && !head.isBlank()) {
            md.append("- code: ").append(head).append('\n');
        }
        String since = System.getenv("BACKTEST_SINCE_FREEZE");
        if (since != null && !since.isBlank()) {
            md.append("- harness commits since the freeze: ").append(since).append('\n');
        }
        md.append('\n');
    }


    private static void body(StringBuilder md, Run run, boolean binding) {
        Evaluation e = run.eval();
        int ppr = e.league(League.PPR);
        String best = run.best();
        String model = run.model();

        md.append("## Population\n\n");
        List<String[]> rows = new ArrayList<>();
        run.counts().forEach((season, c) -> rows.add(new String[] {
            Integer.toString(season), Integer.toString(c.weeks()), n(c.p()),
            n(c.p0()) + " (" + n(c.p0DidNotPlay()) + " did not play)", n(c.pAll()),
            n(c.debuts()), n(c.teamChanges()), n(c.noLine())}));
        table(md, new String[] {"Season", "Weeks", "P", "P0", "P-all", "Debuts excluded",
            "Played for a new team", "Rows with no line"}, rows);
        md.append("\nP is the primary population and every number below is over it unless "
                + "labelled otherwise. Debuts have no history to project from.\n\n");

        md.append("## Fantasy points, every method (").append(run.season()).append(", P)\n\n");
        List<String> leagues = List.of("0 PPR", "Half PPR", "PPR", "My league");
        rows.clear();
        for (String m : run.methods()) {
            List<String> row = new ArrayList<>(List.of(m));
            for (String league : leagues) {
                row.add(f3(e.fantasy(m, e.league(league), P).mae()));
            }
            Evaluation.Summary s = e.fantasy(m, ppr, P);
            row.add(f3(s.rmse()));
            row.add(f3(s.bias()));
            row.add(f3(e.spearman(m, ppr, P)));
            rows.add(row.toArray(String[]::new));
        }
        table(md, new String[] {"Method", "MAE 0 PPR", "MAE Half", "MAE PPR", "MAE My league",
            "RMSE PPR", "Bias PPR", "Spearman PPR"}, rows);
        md.append("\nBest baseline (chosen on ").append(VALIDATION).append("): **").append(best)
                .append("**. Model: **").append(model).append("**. Spearman is the mean rank "
                        + "correlation within each position-week.\n\n");

        Evaluation.Interval overall = e.paired(model, best, ppr, P, REPS, SEED);
        md.append("## Model against the best baseline\n\n");
        md.append("Paired difference in absolute PPR error (model − baseline; negative means the "
                + "model is closer), 95% CI from ").append(REPS)
                .append(" bootstrap resamples of whole weeks.\n\n");
        rows.clear();
        rows.add(intervalRow("All", overall));
        Map<String, Evaluation.Interval> byPosition = new LinkedHashMap<>();
        for (String position : Priors.POSITIONS) {
            Evaluation.Interval i = e.paired(model, best, ppr, P.and(c -> position.equals(c.position)),
                    REPS, SEED);
            byPosition.put(position, i);
            rows.add(intervalRow(position, i));
        }
        for (int[] bucket : new int[][] {{1, 3}, {4, 9}, {10, 22}}) {
            Evaluation.Interval i = e.paired(model, best, ppr,
                    P.and(c -> c.week >= bucket[0] && c.week <= bucket[1]), REPS, SEED);
            rows.add(intervalRow("Weeks " + bucket[0] + "–" + Math.min(bucket[1], 18), i));
        }
        table(md, new String[] {"Slice", "n", "Baseline MAE", "Model MAE", "Diff", "95% CI",
            "Improvement"}, rows);
        md.append('\n');

        md.append("### Every league, same comparison\n\n");
        rows.clear();
        for (String league : leagues) {
            rows.add(intervalRow(league, e.paired(model, best, e.league(league), P, REPS, SEED)));
        }
        table(md, new String[] {"League", "n", "Baseline MAE", "Model MAE", "Diff", "95% CI",
            "Improvement"}, rows);
        md.append('\n');

        Evaluation.Summary modelPpr = e.fantasy(model, ppr, P);
        md.append("### The pre-registered ship rule").append(binding ? "" : " (informative only here)")
                .append("\n\n");
        boolean c1 = overall.hi() < 0;
        boolean c2 = overall.relative() >= MIN_RELATIVE_IMPROVEMENT;
        boolean c3 = byPosition.values().stream().allMatch(i -> i.lo() <= 0);
        boolean c4 = Math.abs(modelPpr.bias()) <= MAX_ABS_BIAS;
        rows.clear();
        rows.add(new String[] {"1", "PPR MAE CI entirely below 0",
            "[" + f3(overall.lo()) + ", " + f3(overall.hi()) + "]", pass(c1)});
        rows.add(new String[] {"2", "Improvement ≥ " + pct(MIN_RELATIVE_IMPROVEMENT),
            pct(overall.relative()), pass(c2)});
        rows.add(new String[] {"3", "No position's CI entirely above 0",
            byPosition.entrySet().stream().map(x -> x.getKey() + " " + f3(x.getValue().lo()))
                    .collect(Collectors.joining(", ")), pass(c3)});
        rows.add(new String[] {"4", "Absolute bias ≤ " + MAX_ABS_BIAS + " PPR pts", f3(modelPpr.bias()), pass(c4)});
        table(md, new String[] {"#", "Criterion", "Measured", "Result"}, rows);
        md.append("\n**").append(c1 && c2 && c3 && c4 ? "All four pass." : "Not all four pass.")
                .append("**\n\n");

        md.append("## Fantasy points by position (PPR, P)\n\n");
        rows.clear();
        for (String position : Priors.POSITIONS) {
            Predicate<Case> f = P.and(c -> position.equals(c.position));
            for (String m : run.methods()) {
                Evaluation.Summary s = e.fantasy(m, ppr, f);
                rows.add(new String[] {position, m, n(s.n()), f3(s.mae()), f3(s.rmse()),
                    f3(s.bias()), f3(e.spearman(m, ppr, f))});
            }
        }
        table(md, new String[] {"Position", "Method", "n", "MAE", "RMSE", "Bias", "Spearman"}, rows);
        md.append('\n');

        md.append("## Raw stats by position (P): best baseline against the model\n\n");
        md.append("RMSE is the proper score for a mean projection. MAE rewards the median, which "
                + "is 0 for touchdowns, so read MAE on TDs with that in mind.\n\n");
        rows.clear();
        for (String position : Priors.POSITIONS) {
            Predicate<Case> f = P.and(c -> position.equals(c.position));
            for (int q : statsShown(position)) {
                Evaluation.Summary b = e.stat(best, q, f);
                Evaluation.Summary m = e.stat(model, q, f);
                rows.add(new String[] {position, Quantity.name(q), f3(meanActual(e, q, f)),
                    f3(b.mae()), f3(m.mae()), f3(b.rmse()), f3(m.rmse()),
                    pct((b.rmse() - m.rmse()) / b.rmse()), f3(m.bias())});
            }
        }
        table(md, new String[] {"Pos", "Stat", "Actual mean", "MAE base", "MAE model",
            "RMSE base", "RMSE model", "RMSE better by", "Model bias"}, rows);
        md.append('\n');

        md.append("## Availability and the board's population (PPR)\n\n");
        rows.clear();
        for (Object[] pop : new Object[][] {
                {"P (played)", P}, {"P0 (played team's last game; DNP = 0)", (Predicate<Case>) c -> c.inP0},
                {"P-all (every player with history who played)", (Predicate<Case>) c -> c.inPAll}}) {
            @SuppressWarnings("unchecked")
            Predicate<Case> f = (Predicate<Case>) pop[1];
            Evaluation.Summary b = e.fantasy(best, ppr, f);
            Evaluation.Summary m = e.fantasy(model, ppr, f);
            rows.add(new String[] {(String) pop[0], n(m.n()), f3(b.mae()), f3(m.mae()),
                f3(b.bias()), f3(m.bias())});
        }
        table(md, new String[] {"Population", "n", "Baseline MAE", "Model MAE", "Baseline bias",
            "Model bias"}, rows);
        md.append('\n');
        // What availability costs, measured directly: a P0 player who then sat scores 0, so
        // every point projected for him is error that knowing he was out would remove. (P0
        // minus P is not this number -- the two are different sets of players.)
        int n0 = 0;
        int sat = 0;
        int satWeek1 = 0;
        double satBase = 0;
        double satModel = 0;
        for (int i = 0; i < e.cases.size(); i++) {
            Case c = e.cases.get(i);
            if (!c.inP0) {
                continue;
            }
            n0++;
            if (c.actual == null) {
                sat++;
                satWeek1 += c.week == 1 ? 1 : 0;
                satBase += Math.abs(e.points(best, ppr)[i]);
                satModel += Math.abs(e.points(model, ppr)[i]);
            }
        }
        md.append("In P0, ").append(n(sat)).append(" of ").append(n(n0))
                .append(" players then did not play (").append(n(satWeek1))
                .append(" of them in week 1, which includes offseason retirements and releases -- "
                        + "roster news, not injury news). They were projected ")
                .append(f3(satModel / Math.max(1, sat))).append(" PPR points each by the model, so "
                        + "**knowing who is active would remove ").append(f3(satModel / n0))
                .append(" points of MAE per P0 player-week** (").append(f3(satBase / n0))
                .append(" for the baseline).\n\n");

        bonusSection(md, run);

        md.append("## Rare-stat shrinkage (pseudo-games, fitted on training rows)\n\n");
        md.append(run.shrink().entrySet().stream().map(x -> x.getKey() + " " + g(x.getValue()))
                .collect(Collectors.joining(" · "))).append("\n");
    }

    private static void bonusSection(StringBuilder md, Run run) {
        Evaluation e = run.eval();
        int bonus = e.league("Bonus league");
        String model = run.model();
        md.append("## Threshold bonuses: expected value against the step\n\n");
        md.append("Bonus league = Half PPR + 3 pts each for 100 rushing yards, 100 receiving "
                + "yards and 300 passing yards. Same projected lines, three ways to score them.\n\n");
        List<String[]> rows = new ArrayList<>();
        for (String[] t : new String[][] {{model, "Expected bonus (the brief's decision)"},
                {model + " · bonus on the mean line", "Bonus applied to the mean line"},
                {model + " · bonus ignored", "Bonus ignored"}}) {
            double[] p = e.points(t[0], bonus);
            double[] a = e.actual(bonus);
            double abs = 0;
            double sq = 0;
            double bias = 0;
            int n = 0;
            for (int i = 0; i < e.cases.size(); i++) {
                if (e.cases.get(i).inP) {
                    double err = p[i] - a[i];
                    abs += Math.abs(err);
                    sq += err * err;
                    bias += err;
                    n++;
                }
            }
            rows.add(new String[] {t[1], f3(abs / n), f3(Math.sqrt(sq / n)), f3(bias / n)});
        }
        table(md, new String[] {"Treatment", "MAE", "RMSE", "Bias"}, rows);

        BonusOdds odds = run.odds();
        md.append("\n### Are the probabilities calibrated?\n\n");
        rows.clear();
        int[] count = new int[10];
        double[] sumP = new double[10];
        double[] hits = new double[10];
        double[][] lines = e.lines(model);
        for (Bonus b : e.leagues.get(bonus).points().bonuses()) {
            for (String position : Priors.POSITIONS) {
                if (!odds.measured(position, b.stat())) {
                    continue;
                }
                double brier = 0;
                double brierStep = 0;
                double rate = 0;
                double meanP = 0;
                int n = 0;
                for (int i = 0; i < e.cases.size(); i++) {
                    Case c = e.cases.get(i);
                    if (!c.inP || !position.equals(c.position)) {
                        continue;
                    }
                    double projected = lines[i][b.stat().index()];
                    double prob = odds.probability(position, b.stat(), b.gte(), projected);
                    double hit = c.actualLine()[b.stat().index()] >= b.gte() ? 1 : 0;
                    double step = projected >= b.gte() ? 1 : 0;
                    brier += (prob - hit) * (prob - hit);
                    brierStep += (step - hit) * (step - hit);
                    rate += hit;
                    meanP += prob;
                    n++;
                    int bin = Math.min(9, (int) (prob * 10));
                    count[bin]++;
                    sumP[bin] += prob;
                    hits[bin] += hit;
                }
                if (rate == 0) {
                    continue;
                }
                rows.add(new String[] {b.stat().json() + " ≥ " + b.gte(), position, n(n),
                    share(rate / n), share(meanP / n), f4(brier / n), f4(brierStep / n)});
            }
        }
        table(md, new String[] {"Bonus", "Pos", "n", "Hit rate", "Mean P", "Brier (expected)",
            "Brier (step on mean)"}, rows);
        md.append("\nReliability, every bonus and position pooled:\n\n");
        rows.clear();
        for (int b = 0; b < 10; b++) {
            if (count[b] > 0) {
                rows.add(new String[] {String.format(Locale.ROOT, "%.1f–%.1f", b / 10.0, (b + 1) / 10.0),
                    n(count[b]), share(sumP[b] / count[b]), share(hits[b] / count[b])});
            }
        }
        table(md, new String[] {"P bin", "n", "Mean P", "Observed"}, rows);
        md.append('\n');
    }

    private static double meanActual(Evaluation e, int q, Predicate<Case> f) {
        double sum = 0;
        int n = 0;
        for (Case c : e.cases) {
            if (f.test(c)) {
                sum += c.actualLine()[q];
                n++;
            }
        }
        return sum / n;
    }

    /** The stats worth a row for a position: its ridge stats plus fumbles and TDs. */
    private static List<Integer> statsShown(String position) {
        List<Integer> out = new ArrayList<>(ProjectionModel.RIDGE.get(position));
        out.add(Quantity.of(StatKey.FUM_LOST));
        return out;
    }

    static String[] intervalRow(String label, Evaluation.Interval i) {
        return new String[] {label, n(i.n()), f3(i.baseMae()), f3(i.modelMae()), f3(i.diff()),
            "[" + f3(i.lo()) + ", " + f3(i.hi()) + "]", pct(i.relative())};
    }

    // ---- files -----------------------------------------------------------------------

    private static void writePredictions(Run run, String stage) throws IOException {
        Evaluation e = run.eval();
        int ppr = e.league(League.PPR);
        StringBuilder csv = new StringBuilder("season,week,player_id,position,in_p,in_p0,in_p_all,actual_ppr");
        for (String m : run.methods()) {
            csv.append(',').append(m.replace(',', ' '));
        }
        csv.append('\n');
        for (int i = 0; i < e.cases.size(); i++) {
            Case c = e.cases.get(i);
            csv.append(c.season).append(',').append(c.week).append(',').append(c.playerId)
                    .append(',').append(c.position).append(',').append(c.inP).append(',')
                    .append(c.inP0).append(',').append(c.inPAll).append(',')
                    .append(f4(e.actual(ppr)[i]));
            for (String m : run.methods()) {
                csv.append(',').append(f4(e.points(m, ppr)[i]));
            }
            csv.append('\n');
        }
        Files.writeString(OUT.resolve(stage + "-predictions.csv"), csv.toString());
    }

    /** Training and validation rows with every feature and the actual line, for a tree probe. */
    private static void writeFeatures(Run run, List<Case> train) throws IOException {
        StringBuilder csv = new StringBuilder("split,season,week,player_id,position");
        for (String name : Features.NAMES) {
            csv.append(',').append(name);
        }
        for (int q = 0; q < Quantity.COUNT; q++) {
            csv.append(",y:").append(Quantity.name(q));
        }
        csv.append('\n');
        List<Case> rows = new ArrayList<>(train);
        rows.addAll(run.eval().cases.stream().filter(P).toList());
        for (Case c : rows) {
            csv.append(c.season == run.season() ? "validation" : "train").append(',')
                    .append(c.season).append(',').append(c.week).append(',').append(c.playerId)
                    .append(',').append(c.position);
            for (double v : c.features) {
                csv.append(',').append(f4(v));
            }
            for (double v : c.actualLine()) {
                csv.append(',').append(f4(v));
            }
            csv.append('\n');
        }
        Files.writeString(OUT.resolve("features.csv"), csv.toString());
    }

    // ---- formatting ------------------------------------------------------------------

    static void table(StringBuilder md, String[] header, List<String[]> rows) {
        md.append("| ").append(String.join(" | ", header)).append(" |\n|");
        for (int i = 0; i < header.length; i++) {
            md.append(i == 0 ? "---|" : "---:|");
        }
        md.append('\n');
        for (String[] row : rows) {
            md.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
    }

    static String f3(double v) {
        return String.format(Locale.ROOT, "%.3f", v);
    }

    static String f4(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    static String g(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : Double.toString(v);
    }

    static String n(int v) {
        return String.format(Locale.ROOT, "%,d", v);
    }

    static String pct(double v) {
        return String.format(Locale.ROOT, "%+.1f%%", 100 * v);
    }

    static String share(double v) {
        return String.format(Locale.ROOT, "%.1f%%", 100 * v);
    }

    static String pass(boolean ok) {
        return ok ? "**pass**" : "**FAIL**";
    }
}
