package com.fantasykai.projection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One player's or one team's games in order, and the only door a feature has into them.
 *
 * <p>{@link #asOf} is the backtest's leakage boundary. Projecting week N may read games
 * strictly before week N and nothing else -- not week N's own row, not a later week, not a
 * season total. Every feature and every baseline is computed from what this returns, so
 * the boundary is one comparison in one method, and {@code LeakageTests} fails the moment
 * that comparison admits week N.
 *
 * <p>{@link #at} is the evaluator's door, not the model's: it fetches the label.
 */
final class Timeline<T extends Dated> {

    private final List<T> games;

    Timeline(List<T> games) {
        List<T> sorted = new ArrayList<>(games);
        sorted.sort(Comparator.comparingInt(Dated::key));
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).key() == sorted.get(i - 1).key()) {
                throw new IllegalArgumentException("two games in one week: " + sorted.get(i));
            }
        }
        this.games = List.copyOf(sorted);
    }

    /** Every game strictly before {@code (season, week)}, oldest first. */
    List<T> asOf(int season, int week) {
        int cutoff = Dated.key(season, week);
        int lo = 0;
        int hi = games.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (games.get(mid).key() < cutoff) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return games.subList(0, lo);
    }

    /** The game in exactly {@code (season, week)}, if there was one. For labels only. */
    Optional<T> at(int season, int week) {
        int key = Dated.key(season, week);
        for (T game : games) {
            if (game.key() == key) {
                return Optional.of(game);
            }
        }
        return Optional.empty();
    }

    List<T> all() {
        return games;
    }
}
