package com.fantasykai.projection;

import com.fantasykai.scoring.StatLine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Scores every method's projected lines and every actual line through {@code ScoringEngine}
 * under each league, then measures the errors.
 *
 * <p>Points are never rounded here -- the API boundary rounds, and an error measured on
 * rounded points would be a different number.
 */
final class Evaluation {

    record Summary(int n, double mae, double rmse, double bias) {}

    /** A paired difference in absolute error, model minus baseline, with a bootstrap CI. */
    record Interval(int n, double baseMae, double modelMae, double diff, double lo, double hi) {

        double relative() {
            return -diff / baseMae;
        }
    }

    final List<Case> cases;
    final List<League> leagues;
    private final double[][] actual;
    private final Map<String, double[][]> lines = new LinkedHashMap<>();
    private final Map<String, double[][]> points = new LinkedHashMap<>();

    Evaluation(List<Case> cases, List<League> leagues) {
        this.cases = cases;
        this.leagues = leagues;
        this.actual = new double[leagues.size()][cases.size()];
        for (int l = 0; l < leagues.size(); l++) {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i);
                actual[l][i] = leagues.get(l).points()
                        .actual(new StatLine(c.position, Quantity.scorable(c.actualLine())));
            }
        }
    }

    /** Adds a method. {@code odds} null means bonuses are scored on the mean line (a step). */
    void add(String method, double[][] projected, BonusOdds odds) {
        double[][] p = new double[leagues.size()][cases.size()];
        for (int l = 0; l < leagues.size(); l++) {
            ExpectedPoints scorer = leagues.get(l).points();
            for (int i = 0; i < cases.size(); i++) {
                StatLine mean = new StatLine(cases.get(i).position, Quantity.scorable(projected[i]));
                p[l][i] = odds == null ? scorer.stepped(mean) : scorer.expected(mean, odds);
            }
        }
        lines.put(method, projected);
        points.put(method, p);
    }

    /** Adds a points-only variant of a method, for the bonus-league treatments. */
    void addPoints(String method, int league, double[] projectedPoints) {
        double[][] p = new double[leagues.size()][];
        p[league] = projectedPoints;
        points.put(method, p);
    }

    double[][] lines(String method) {
        return lines.get(method);
    }

    double[] points(String method, int league) {
        return points.get(method)[league];
    }

    double[] actual(int league) {
        return actual[league];
    }

    int league(String label) {
        for (int l = 0; l < leagues.size(); l++) {
            if (leagues.get(l).label().equals(label)) {
                return l;
            }
        }
        throw new IllegalArgumentException("no league " + label);
    }

    Summary fantasy(String method, int league, Predicate<Case> filter) {
        return summary(points.get(method)[league], actual[league], filter);
    }

    Summary stat(String method, int q, Predicate<Case> filter) {
        double[][] projected = lines.get(method);
        double[] p = new double[cases.size()];
        double[] a = new double[cases.size()];
        for (int i = 0; i < cases.size(); i++) {
            p[i] = projected[i][q];
            a[i] = cases.get(i).actualLine()[q];
        }
        return summary(p, a, filter);
    }

    private Summary summary(double[] projected, double[] truth, Predicate<Case> filter) {
        int n = 0;
        double abs = 0;
        double sq = 0;
        double bias = 0;
        for (int i = 0; i < cases.size(); i++) {
            if (filter.test(cases.get(i))) {
                double e = projected[i] - truth[i];
                abs += Math.abs(e);
                sq += e * e;
                bias += e;
                n++;
            }
        }
        return n == 0 ? new Summary(0, Double.NaN, Double.NaN, Double.NaN)
                : new Summary(n, abs / n, Math.sqrt(sq / n), bias / n);
    }

    /**
     * Mean Spearman rank correlation of projected and actual points within each
     * position-week, weighted by its size -- how well a method orders a board, which is what a
     * member sees. Reported, never optimized.
     */
    double spearman(String method, int league, Predicate<Case> filter) {
        Map<String, List<Integer>> groups = new TreeMap<>();
        for (int i = 0; i < cases.size(); i++) {
            Case c = cases.get(i);
            if (filter.test(c)) {
                groups.computeIfAbsent(c.weekKey() + c.position, k -> new ArrayList<>()).add(i);
            }
        }
        double[] projected = points.get(method)[league];
        double total = 0;
        int weight = 0;
        for (List<Integer> group : groups.values()) {
            if (group.size() < 3) {
                continue;
            }
            double[] rp = ranks(group, projected);
            double[] ra = ranks(group, actual[league]);
            total += group.size() * pearson(rp, ra);
            weight += group.size();
        }
        return total / weight;
    }

    private static double[] ranks(List<Integer> group, double[] values) {
        int n = group.size();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (x, y) -> Double.compare(values[group.get(x)], values[group.get(y)]));
        double[] rank = new double[n];
        int i = 0;
        while (i < n) {
            int j = i;
            while (j + 1 < n && values[group.get(order[j + 1])] == values[group.get(order[i])]) {
                j++;
            }
            double average = (i + j) / 2.0 + 1;
            for (int k = i; k <= j; k++) {
                rank[order[k]] = average;
            }
            i = j + 1;
        }
        return rank;
    }

    private static double pearson(double[] x, double[] y) {
        double mx = Arrays.stream(x).average().orElse(0);
        double my = Arrays.stream(y).average().orElse(0);
        double sxy = 0;
        double sxx = 0;
        double syy = 0;
        for (int i = 0; i < x.length; i++) {
            sxy += (x[i] - mx) * (y[i] - my);
            sxx += (x[i] - mx) * (x[i] - mx);
            syy += (y[i] - my) * (y[i] - my);
        }
        return sxx == 0 || syy == 0 ? 0 : sxy / Math.sqrt(sxx * syy);
    }

    /**
     * Paired absolute-error difference, model minus baseline, with a 95% percentile CI from
     * a bootstrap that resamples whole weeks. A week is the unit because errors within one
     * share its games, its weather and its injuries; resampling player-weeks as if they were
     * independent would make the interval look tighter than the evidence is.
     */
    Interval paired(String model, String baseline, int league, Predicate<Case> filter,
            int reps, long seed) {
        double[] pm = points.get(model)[league];
        double[] pb = points.get(baseline)[league];
        Map<Integer, double[]> weeks = new TreeMap<>();
        double sumModel = 0;
        double sumBase = 0;
        int n = 0;
        for (int i = 0; i < cases.size(); i++) {
            if (!filter.test(cases.get(i))) {
                continue;
            }
            double em = Math.abs(pm[i] - actual[league][i]);
            double eb = Math.abs(pb[i] - actual[league][i]);
            double[] w = weeks.computeIfAbsent(cases.get(i).weekKey(), k -> new double[2]);
            w[0] += em - eb;
            w[1]++;
            sumModel += em;
            sumBase += eb;
            n++;
        }
        double[][] clusters = weeks.values().toArray(new double[0][]);
        double[] stats = new double[reps];
        SplittableRandom random = new SplittableRandom(seed);
        for (int r = 0; r < reps; r++) {
            double diff = 0;
            double count = 0;
            for (int k = 0; k < clusters.length; k++) {
                double[] w = clusters[random.nextInt(clusters.length)];
                diff += w[0];
                count += w[1];
            }
            stats[r] = diff / count;
        }
        Arrays.sort(stats);
        return new Interval(n, sumBase / n, sumModel / n, (sumModel - sumBase) / n,
                stats[(int) Math.floor(0.025 * (reps - 1))],
                stats[(int) Math.ceil(0.975 * (reps - 1))]);
    }
}
