package com.fantasykai.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * One player's points over a run of games, summed <em>in week order</em>.
 *
 * <p>The order is the point. Floating-point addition is not associative, so
 * summing the same weeks in two orders can differ in the last bit -- and the
 * rows behind a ranking arrive in whatever order the sequential scan meets them,
 * which moves with every upsert and with Postgres's synchronized scans. Two
 * players level on screen could then swap places between one request and the
 * next, and a career's positional rank could disagree with the board's. Summing
 * in a fixed order makes a total a function of the weeks alone.
 *
 * <p>Unrounded throughout. Rounding happens once, at the API boundary.
 */
final class PointsTally {

    /** Season points, highest first; ties broken by id so pages never overlap. */
    static final Comparator<PointsTally> BY_POINTS =
            Comparator.comparingDouble(PointsTally::points).reversed()
                    .thenComparingLong(PointsTally::playerId);

    static final Comparator<PointsTally> BY_PER_GAME =
            Comparator.comparingDouble(PointsTally::perGame).reversed()
                    .thenComparingLong(PointsTally::playerId);

    private static final Comparator<Game> IN_WEEK_ORDER =
            Comparator.comparingInt(Game::week).thenComparingDouble(Game::points);

    private record Game(int week, double points) {}

    private final long playerId;
    private final List<Game> games = new ArrayList<>();
    private double total;
    private boolean summed = true;

    PointsTally(long playerId) {
        this.playerId = playerId;
    }

    void add(int week, double points) {
        games.add(new Game(week, points));
        summed = false;
    }

    long playerId() {
        return playerId;
    }

    int games() {
        return games.size();
    }

    double points() {
        if (!summed) {
            games.sort(IN_WEEK_ORDER);
            double sum = 0;
            for (Game game : games) {
                sum += game.points();
            }
            total = sum;
            summed = true;
        }
        return total;
    }

    double perGame() {
        return games.isEmpty() ? 0 : points() / games.size();
    }
}
