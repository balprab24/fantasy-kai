package com.fantasykai.projection;

import java.util.List;
import java.util.Locale;

/**
 * The simple projections a model has to beat. Each projects every quantity from the player's
 * own history and nothing else -- no team, no opponent, no line -- and is scored through the
 * same {@code ScoringEngine} as the model, over the same population.
 *
 * <p>{@code parameter} is the rolling window or the half-life, chosen on the validation
 * season and frozen before the test season is read.
 */
record Baseline(Kind kind, double parameter) {

    enum Kind {
        /** A: the player's most recent game, which in week 1 is last season's finale. */
        PREVIOUS_GAME,
        /** B: mean of the last k games played. */
        ROLLING,
        /** C: season-to-date per game; before his first game this season, his last season's. */
        SEASON_TO_DATE,
        /** D: exponentially weighted mean across games, crossing seasons. */
        EWMA
    }

    double[] project(List<PlayerGame> history, int season) {
        if (history.isEmpty()) {
            throw new IllegalArgumentException("a baseline needs at least one prior game");
        }
        return switch (kind) {
            case PREVIOUS_GAME -> history.get(history.size() - 1).line().clone();
            case ROLLING -> History.lastK(history, (int) parameter);
            case SEASON_TO_DATE -> History.seasonToDate(history, season);
            case EWMA -> History.ewma(history, parameter);
        };
    }

    String label() {
        return switch (kind) {
            case PREVIOUS_GAME -> "A previous game";
            case ROLLING -> "B last " + (int) parameter + " games";
            case SEASON_TO_DATE -> "C season to date";
            case EWMA -> String.format(Locale.ROOT, "D EWMA h=%s", trim(parameter));
        };
    }

    private static String trim(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }
}
