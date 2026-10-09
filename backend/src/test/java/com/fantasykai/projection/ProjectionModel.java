package com.fantasykai.projection;

import com.fantasykai.scoring.StatKey;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Model v1: a projected raw stat line, every quantity under an explicit rule.
 *
 * <ul>
 *   <li><b>Ridge</b> -- one regression per (position, stat) pair that carries real volume,
 *       or one per stat family pooled across positions when the validation season says
 *       pooling wins. Predictions are clamped at zero.</li>
 *   <li><b>Shrunk player rate</b> -- every other (position, stat) pair, e.g. a quarterback's
 *       receptions: his long-weighted per-game rate blended with the positional rate.</li>
 *   <li><b>Per touch</b> -- {@code fum_lost} and the three 2-point stats: a positional rate
 *       times the projected touches.</li>
 *   <li><b>Per return</b> -- {@code ret_td}: his return volume times the positional rate.</li>
 * </ul>
 *
 * <p>Nothing is left to default to zero; {@link #predict} fills all {@link Quantity#COUNT}.
 * It projects a stat line and never a point value -- points come from {@code ScoringEngine}
 * under whatever ruleset the line is scored against, so one model serves every league.
 */
final class ProjectionModel {

    enum Group {
        /** The player's own history: volume, efficiency, snaps. */
        G1,
        /** Plus shares of his team's volume and that volume. */
        G2,
        /** Plus the game's line: implied team total, spread, home. */
        G3,
        /** Plus the opponent: what it allowed to this position, as a multiple of the league. */
        G4
    }

    record Config(Group group, boolean pooledReceiving, boolean pooledRushing,
            Map<String, Double> lambdas) {}

    /** A ridge model: a position (or a pooled family of positions) and the stat it projects. */
    record Key(String scope, int q) {

        @Override
        public String toString() {
            return scope + ":" + Quantity.name(q);
        }
    }

    static final Map<String, List<Integer>> RIDGE = Map.of(
            "QB", List.of(Quantity.PASS_ATT, q(StatKey.PASS_YD), q(StatKey.PASS_TD),
                    q(StatKey.PASS_INT), Quantity.RUSH_ATT, q(StatKey.RUSH_YD), q(StatKey.RUSH_TD)),
            "RB", List.of(Quantity.RUSH_ATT, q(StatKey.RUSH_YD), q(StatKey.RUSH_TD),
                    Quantity.TARGETS, q(StatKey.REC), q(StatKey.REC_YD), q(StatKey.REC_TD)),
            "WR", List.of(Quantity.TARGETS, q(StatKey.REC), q(StatKey.REC_YD), q(StatKey.REC_TD),
                    Quantity.RUSH_ATT, q(StatKey.RUSH_YD)),
            "TE", List.of(Quantity.TARGETS, q(StatKey.REC), q(StatKey.REC_YD), q(StatKey.REC_TD)));

    static final String POOLED_RECEIVING = "REC*";
    static final String POOLED_RUSHING = "RUSH*";
    private static final List<String> RECEIVING_FAMILY = List.of("RB", "WR", "TE");
    private static final List<String> RUSHING_FAMILY = List.of("QB", "RB", "WR");
    private static final Set<Integer> RECEIVING = Set.of(Quantity.TARGETS, q(StatKey.REC),
            q(StatKey.REC_YD), q(StatKey.REC_TD));
    private static final Set<Integer> RUSHING = Set.of(Quantity.RUSH_ATT, q(StatKey.RUSH_YD),
            q(StatKey.RUSH_TD));

    private static final Set<Integer> PER_TOUCH = Set.of(q(StatKey.FUM_LOST), q(StatKey.PASS_2PT),
            q(StatKey.RUSH_2PT), q(StatKey.REC_2PT));
    private static final int RET_TD = q(StatKey.RET_TD);

    static final double[] SHRINK_GRID = {1, 4, 16, 64, 256, 1024};

    private final Config config;
    private final Priors priors;
    private final Map<String, Ridge> ridges;
    private final Map<String, int[][]> designs;
    private final Map<String, Double> shrink;

    private ProjectionModel(Config config, Priors priors, Map<String, Ridge> ridges,
            Map<String, int[][]> designs, Map<String, Double> shrink) {
        this.config = config;
        this.priors = priors;
        this.ridges = ridges;
        this.designs = designs;
        this.shrink = shrink;
    }

    private static int q(StatKey stat) {
        return Quantity.of(stat);
    }

    // ---- structure -------------------------------------------------------------------

    static Key key(String position, int q, boolean pooledReceiving, boolean pooledRushing) {
        if (pooledReceiving && RECEIVING.contains(q) && RECEIVING_FAMILY.contains(position)) {
            return new Key(POOLED_RECEIVING, q);
        }
        if (pooledRushing && RUSHING.contains(q) && RUSHING_FAMILY.contains(position)) {
            return new Key(POOLED_RUSHING, q);
        }
        return new Key(position, q);
    }

    /** Every ridge model a configuration needs, in a fixed order. */
    static List<Key> keys(boolean pooledReceiving, boolean pooledRushing) {
        Set<Key> keys = new LinkedHashSet<>();
        for (String position : Priors.POSITIONS) {
            for (int q : RIDGE.get(position)) {
                keys.add(key(position, q, pooledReceiving, pooledRushing));
            }
        }
        return List.copyOf(keys);
    }

    /** Whether a case is one of the rows a key is fitted on and predicts. */
    static boolean covers(Key key, Case c) {
        if (!RIDGE.get(c.position).contains(key.q())) {
            return false;
        }
        return switch (key.scope()) {
            case POOLED_RECEIVING -> RECEIVING_FAMILY.contains(c.position);
            case POOLED_RUSHING -> RUSHING_FAMILY.contains(c.position);
            default -> key.scope().equals(c.position);
        };
    }

    /**
     * The design columns for one stat. Each is a feature or a product of features
     * ({@code "ewma:targets*yards_per_target"}), so opportunity times efficiency -- the
     * brief's {@code player_share × team_volume × efficiency} -- is a column the regression
     * can weight rather than a formula fixed in advance.
     */
    static List<String> columns(int q, Group group, boolean pooled) {
        String n = Quantity.name(q);
        Rate efficiency = efficiencyOf(q);
        Set<String> cols = new LinkedHashSet<>(List.of("ewma:" + n, "std:" + n, "last:" + n,
                "prior:" + n, "ewma:pass_att", "ewma:rush_att", "ewma:targets", "snap_ewma",
                "snap_last", "log_games", "no_season_games"));
        if (efficiency != null) {
            cols.add("ewma:" + Quantity.name(efficiency.denominator) + "*" + efficiency.feature());
        }
        if (group.compareTo(Group.G2) >= 0) {
            cols.addAll(List.of("tshare", "cshare", "pshare", "team_pass", "team_rush",
                    "team_tgt", "x_targets", "x_carries", "x_pass_att"));
            if (efficiency != null) {
                cols.add(expected(efficiency.denominator) + "*" + efficiency.feature());
            }
        }
        if (group.compareTo(Group.G3) >= 0) {
            cols.addAll(List.of("implied_total", "team_spread", "home",
                    "ewma:" + n + "*implied_total", "ewma:" + n + "*team_spread"));
            if (efficiency != null) {
                cols.add("ewma:" + Quantity.name(efficiency.denominator) + "*"
                        + efficiency.feature() + "*implied_total");
            }
        }
        if (group.compareTo(Group.G4) >= 0) {
            cols.addAll(List.of("opp:" + n, "ewma:" + n + "*opp:" + n));
            if (efficiency != null) {
                cols.add("ewma:" + Quantity.name(efficiency.denominator) + "*"
                        + efficiency.feature() + "*opp:" + n);
            }
        }
        if (pooled) {
            cols.addAll(List.of("pos:QB", "pos:RB", "pos:WR"));
        }
        return List.copyOf(cols);
    }

    private static Rate efficiencyOf(int q) {
        for (Rate rate : Rate.values()) {
            if (rate.numerator == q) {
                return rate;
            }
        }
        return null;
    }

    private static String expected(int opportunity) {
        if (opportunity == Quantity.TARGETS) {
            return "x_targets";
        }
        if (opportunity == Quantity.RUSH_ATT) {
            return "x_carries";
        }
        return "x_pass_att";
    }

    static int[][] compile(List<String> columns) {
        int[][] design = new int[columns.size()][];
        for (int j = 0; j < columns.size(); j++) {
            String[] factors = columns.get(j).split("\\*");
            design[j] = new int[factors.length];
            for (int k = 0; k < factors.length; k++) {
                design[j][k] = Features.index(factors[k]);
            }
        }
        return design;
    }

    static double[] row(double[] features, int[][] design) {
        double[] x = new double[design.length];
        for (int j = 0; j < design.length; j++) {
            double value = 1;
            for (int index : design[j]) {
                value *= features[index];
            }
            x[j] = value;
        }
        return x;
    }

    static int[][] design(Key key, Group group) {
        boolean pooled = key.scope().endsWith("*");
        return compile(columns(key.q(), group, pooled));
    }

    /** Fits one key's ridge on the rows it covers. Exposed for the validation-season λ search. */
    static Ridge fitKey(Key key, Group group, List<Case> train, double lambda) {
        int[][] design = design(key, group);
        List<Case> rows = train.stream().filter(c -> covers(key, c)).toList();
        double[][] x = new double[rows.size()][];
        double[] y = new double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            x[i] = row(rows.get(i).features, design);
            y[i] = rows.get(i).actualLine()[key.q()];
        }
        return Ridge.fit(x, y, lambda);
    }

    // ---- fit and predict -------------------------------------------------------------

    static ProjectionModel fit(List<Case> train, Config config, Priors priors) {
        Map<String, Ridge> ridges = new LinkedHashMap<>();
        Map<String, int[][]> designs = new LinkedHashMap<>();
        for (Key key : keys(config.pooledReceiving(), config.pooledRushing())) {
            Double lambda = config.lambdas().get(key.toString());
            if (lambda == null) {
                throw new IllegalArgumentException("no λ chosen for " + key);
            }
            ridges.put(key.toString(), fitKey(key, config.group(), train, lambda));
            designs.put(key.toString(), design(key, config.group()));
        }

        // The shrinkage for each rare pair is chosen on the training rows themselves: each
        // row's features already stop at its own week, so this is an out-of-time fit.
        Map<String, Double> shrink = new LinkedHashMap<>();
        for (String position : Priors.POSITIONS) {
            for (int q : shrunkQuantities(position)) {
                double best = SHRINK_GRID[0];
                double bestError = Double.MAX_VALUE;
                for (double k : SHRINK_GRID) {
                    double error = 0;
                    for (Case c : train) {
                        if (position.equals(c.position)) {
                            double e = shrunk(c, q, k, priors) - c.actualLine()[q];
                            error += e * e;
                        }
                    }
                    if (error < bestError) {
                        bestError = error;
                        best = k;
                    }
                }
                shrink.put(position + ":" + Quantity.name(q), best);
            }
        }
        return new ProjectionModel(config, priors, ridges, designs, shrink);
    }

    /** The (position, quantity) pairs the shrunk-rate rule covers. */
    static List<Integer> shrunkQuantities(String position) {
        List<Integer> out = new ArrayList<>();
        for (int q = 0; q < Quantity.COUNT; q++) {
            if (!RIDGE.get(position).contains(q) && !PER_TOUCH.contains(q) && q != RET_TD) {
                out.add(q);
            }
        }
        return out;
    }

    private static double shrunk(Case c, int q, double k, Priors priors) {
        double[] f = c.features;
        return (f[Features.index("lsum:" + Quantity.name(q))] + k * priors.perGame(c.position, q))
                / (f[Features.index("lweight")] + k);
    }

    double[] predict(Case c) {
        double[] line = new double[Quantity.COUNT];
        for (int q : RIDGE.get(c.position)) {
            String key = key(c.position, q, config.pooledReceiving(), config.pooledRushing()).toString();
            line[q] = Math.max(0, ridges.get(key).predict(row(c.features, designs.get(key))));
        }
        for (int q : shrunkQuantities(c.position)) {
            line[q] = shrunk(c, q, shrink.get(c.position + ":" + Quantity.name(q)), priors);
        }
        double touches = line[Quantity.PASS_ATT] + line[Quantity.RUSH_ATT] + line[q(StatKey.REC)];
        line[q(StatKey.FUM_LOST)] = priors.perTouch(c.position, Priors.FUMBLE_PER_TOUCH) * touches;
        line[q(StatKey.PASS_2PT)] = priors.perTouch(c.position, Priors.PASS_2PT_PER_ATT)
                * line[Quantity.PASS_ATT];
        line[q(StatKey.RUSH_2PT)] = priors.perTouch(c.position, Priors.RUSH_2PT_PER_ATT)
                * line[Quantity.RUSH_ATT];
        line[q(StatKey.REC_2PT)] = priors.perTouch(c.position, Priors.REC_2PT_PER_TARGET)
                * line[Quantity.TARGETS];
        line[RET_TD] = priors.perTouch(c.position, Priors.RET_TD_PER_RETURN)
                * c.features[Features.index("returns")];
        return line;
    }

    Map<String, Double> shrinkage() {
        return shrink;
    }

    Map<String, Ridge> ridges() {
        return ridges;
    }
}
