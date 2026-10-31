package com.fantasykai.leagues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The best lineup a roster can field in one week: an exact maximum-weight assignment of players
 * to starting slots (plan A8).
 *
 * <p>Exact because greedy is not. Yahoo's W/R and W/T flexes overlap without one containing the
 * other, so "fill the strictest slot first" can be beaten -- {@code LineupOptimizerTests} has the
 * case. With at most ~25 players and ~12 starting slots the Hungarian method is O(n³) on a tiny
 * n: microseconds.
 *
 * <p>Rules:
 * <ul>
 *   <li>a player starts only where {@link RosterSlot#eligible()} allows;</li>
 *   <li>a slot may stay empty, at 0 -- a short roster never forces a player in out of position,
 *       and a player expected to score below 0 is better left out;</li>
 *   <li>K and DST are not modelled (north-star §8): their slots are listed, unfilled, at the same
 *       0 for every team, so they cancel out of any comparison between lineups;</li>
 *   <li>candidates are ordered by player id before solving, so equal projections always resolve
 *       the same way whatever order they arrived in.</li>
 * </ul>
 */
public final class LineupOptimizer {

    /** @param points expected points this week, unrounded */
    public record Candidate(long playerId, String position, double points) {}

    /**
     * One starting slot, filled or not.
     *
     * @param playerId null for an empty slot or an unmodelled one
     * @param modeled  false for K and DST
     */
    public record Spot(RosterSlot slot, Long playerId, double points, boolean modeled) {}

    public record Lineup(List<Spot> spots, double total) {}

    /** Larger than any possible lineup, finite so the arithmetic stays exact. */
    private static final double FORBIDDEN = 1e12;

    private LineupOptimizer() {}

    /** @param slots starting-slot counts; BENCH and IR are ignored */
    public static Lineup solve(Map<RosterSlot, Integer> slots, List<Candidate> candidates) {
        List<RosterSlot> modeled = new ArrayList<>();
        List<Spot> spots = new ArrayList<>();
        for (RosterSlot slot : RosterSlot.values()) {
            int count = slots.getOrDefault(slot, 0);
            if (!slot.starts() || count <= 0) {
                continue;
            }
            for (int i = 0; i < count; i++) {
                if (slot == RosterSlot.K || slot == RosterSlot.DST) {
                    spots.add(new Spot(slot, null, 0, false));
                } else {
                    modeled.add(slot);
                }
            }
        }
        List<Candidate> players = candidates.stream()
                .sorted(Comparator.comparingLong(Candidate::playerId)).toList();

        int rows = modeled.size();
        int cols = players.size() + rows; // one "nobody" column per slot
        double[][] cost = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < players.size(); c++) {
                Candidate player = players.get(c);
                cost[r][c] = modeled.get(r).eligible().contains(player.position()) && Double.isFinite(player.points())
                        ? -player.points() : FORBIDDEN;
            }
            // columns players.size().. are empty slots, worth 0 anywhere
        }

        int[] column = assign(cost);
        double total = 0;
        for (int r = 0; r < rows; r++) {
            int c = column[r];
            if (c < players.size() && cost[r][c] < FORBIDDEN) {
                Candidate player = players.get(c);
                spots.add(new Spot(modeled.get(r), player.playerId(), player.points(), true));
                total += player.points();
            } else {
                spots.add(new Spot(modeled.get(r), null, 0, true));
            }
        }
        spots.sort(Comparator.comparing(Spot::slot));
        return new Lineup(List.copyOf(spots), total);
    }

    /**
     * The minimum-cost assignment of every row to a distinct column, rows ≤ columns: the
     * Hungarian method with potentials, O(rows² · columns).
     *
     * @return the column each row takes
     */
    static int[] assign(double[][] cost) {
        int n = cost.length;
        if (n == 0) {
            return new int[0];
        }
        int m = cost[0].length;
        double[] u = new double[n + 1];
        double[] v = new double[m + 1];
        int[] p = new int[m + 1];   // p[j]: the row holding column j, 1-based; 0 = free
        int[] way = new int[m + 1];
        for (int i = 1; i <= n; i++) {
            p[0] = i;
            int j0 = 0;
            double[] minv = new double[m + 1];
            Arrays.fill(minv, Double.POSITIVE_INFINITY);
            boolean[] used = new boolean[m + 1];
            do {
                used[j0] = true;
                int i0 = p[j0];
                double delta = Double.POSITIVE_INFINITY;
                int j1 = 0;
                for (int j = 1; j <= m; j++) {
                    if (!used[j]) {
                        double cur = cost[i0 - 1][j - 1] - u[i0] - v[j];
                        if (cur < minv[j]) {
                            minv[j] = cur;
                            way[j] = j0;
                        }
                        if (minv[j] < delta) {
                            delta = minv[j];
                            j1 = j;
                        }
                    }
                }
                for (int j = 0; j <= m; j++) {
                    if (used[j]) {
                        u[p[j]] += delta;
                        v[j] -= delta;
                    } else {
                        minv[j] -= delta;
                    }
                }
                j0 = j1;
            } while (p[j0] != 0);
            do {
                int j1 = way[j0];
                p[j0] = p[j1];
                j0 = j1;
            } while (j0 != 0);
        }
        int[] column = new int[n];
        for (int j = 1; j <= m; j++) {
            if (p[j] != 0) {
                column[p[j] - 1] = j - 1;
            }
        }
        return column;
    }
}
