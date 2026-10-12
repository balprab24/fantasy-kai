package com.fantasykai.projection;

import java.util.List;

/**
 * Per-game summaries of a history, which is always a {@link Timeline#asOf} slice: oldest
 * first, nothing from the week being projected.
 *
 * <p>Ages are counted in games played, not weeks, so a bye or an injury absence does not
 * age a player's form. Sums run oldest to newest in a fixed order so a result never depends
 * on how rows arrived ({@code PointsTally}'s lesson).
 */
final class History {

    private History() {}

    /** Weight of a game {@code age} games ago under a half-life of {@code halfLife} games. */
    static double weight(int age, double halfLife) {
        return Math.pow(0.5, age / halfLife);
    }

    /** Exponentially weighted per-game mean of every quantity. Empty history gives NaN. */
    static double[] ewma(List<PlayerGame> h, double halfLife) {
        double[] sum = new double[Quantity.COUNT];
        double total = 0;
        int n = h.size();
        for (int i = 0; i < n; i++) {
            double w = weight(n - 1 - i, halfLife);
            double[] line = h.get(i).line();
            for (int q = 0; q < Quantity.COUNT; q++) {
                sum[q] += w * line[q];
            }
            total += w;
        }
        return divide(sum, total);
    }

    /** Plain per-game mean over {@code h[from, to)}. Empty range gives NaN. */
    static double[] mean(List<PlayerGame> h, int from, int to) {
        double[] sum = new double[Quantity.COUNT];
        for (int i = from; i < to; i++) {
            double[] line = h.get(i).line();
            for (int q = 0; q < Quantity.COUNT; q++) {
                sum[q] += line[q];
            }
        }
        return divide(sum, to - from);
    }

    /** Per-game mean of the last {@code k} games played. */
    static double[] lastK(List<PlayerGame> h, int k) {
        return mean(h, Math.max(0, h.size() - k), h.size());
    }

    /**
     * Per-game mean of the games played in {@code season} so far; before his first game of
     * the season, of the most recent season he played. Week 1 therefore reads last year.
     */
    static double[] seasonToDate(List<PlayerGame> h, int season) {
        if (h.isEmpty()) {
            return divide(new double[Quantity.COUNT], 0);
        }
        int from = firstOfSeason(h, season);
        if (from < h.size()) {
            return mean(h, from, h.size());
        }
        return mean(h, firstOfSeason(h, h.get(h.size() - 1).season()), h.size());
    }

    /** Games played in {@code season} so far. */
    static int gamesInSeason(List<PlayerGame> h, int season) {
        return h.size() - firstOfSeason(h, season);
    }

    /** Per-game mean of the most recent season before {@code season}, NaN if none. */
    static double[] priorSeason(List<PlayerGame> h, int season) {
        int end = firstOfSeason(h, season);
        if (end == 0) {
            return divide(new double[Quantity.COUNT], 0);
        }
        int priorSeason = h.get(end - 1).season();
        return mean(h, firstOfSeason(h, priorSeason), end);
    }

    /** Index of the first game in {@code season} or later; {@code h.size()} if none. */
    private static int firstOfSeason(List<PlayerGame> h, int season) {
        int i = h.size();
        while (i > 0 && h.get(i - 1).season() >= season) {
            i--;
        }
        return i;
    }

    /** Weighted snap share over the games that recorded one. NaN if none did. */
    static double ewmaSnap(List<PlayerGame> h, double halfLife) {
        double sum = 0;
        double total = 0;
        int n = h.size();
        for (int i = 0; i < n; i++) {
            double snap = h.get(i).snapPct();
            if (!Double.isNaN(snap)) {
                double w = weight(n - 1 - i, halfLife);
                sum += w * snap;
                total += w;
            }
        }
        return total == 0 ? Double.NaN : sum / total;
    }

    /** Most recent recorded snap share. NaN if none was. */
    static double lastSnap(List<PlayerGame> h) {
        for (int i = h.size() - 1; i >= 0; i--) {
            if (!Double.isNaN(h.get(i).snapPct())) {
                return h.get(i).snapPct();
            }
        }
        return Double.NaN;
    }

    /**
     * Pre-game opportunity, the population's ranking key: mean targets plus carries over the
     * last four games (attempts plus carries for a quarterback). It ranks players; it is not
     * any candidate's prediction, so no method can tilt the population toward itself.
     */
    static double trailingOpportunity(List<PlayerGame> h, String position) {
        int from = Math.max(0, h.size() - 4);
        double sum = 0;
        for (int i = from; i < h.size(); i++) {
            double[] line = h.get(i).line();
            sum += line[Quantity.RUSH_ATT]
                    + ("QB".equals(position) ? line[Quantity.PASS_ATT] : line[Quantity.TARGETS]);
        }
        return h.size() == from ? 0 : sum / (h.size() - from);
    }

    private static double[] divide(double[] sum, double by) {
        double[] out = new double[sum.length];
        for (int q = 0; q < sum.length; q++) {
            out[q] = by == 0 ? Double.NaN : sum[q] / by;
        }
        return out;
    }
}
