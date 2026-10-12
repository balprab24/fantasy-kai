package com.fantasykai.projection;

/**
 * One skill player's line for one regular-season game.
 *
 * @param position this game's position -- in the v2 dataset, nflverse's week-by-week
 *                 designation; in v1's, today's {@code players.position}
 * @param snapPct  offensive snap share in percent, {@code NaN} when not recorded. Never 0
 *                 for "missing": a missing reading is not a benching
 * @param line     {@link Quantity#COUNT} values: the scorable stats, then usage
 * @param returns  punt plus kick returns, the volume a return touchdown comes from
 * @param team     the player's team's totals in this same game, for his shares
 * @param airYards receiving air yards (intended, so negative is possible); {@code NaN} when
 *                 the v2 dataset could not join nflverse's row, and in v1's dataset
 * @param snapOnly true for an appearance with offensive snaps and no stat row -- the active,
 *                 zero-stat game our ingest does not store. Its line is all zeros
 */
record PlayerGame(long playerId, String position, long gameId, int season, int week,
        int teamId, double snapPct, double[] line, int returns, TeamGame team,
        double airYards, boolean snapOnly) implements Dated {

    PlayerGame {
        if (line.length != Quantity.COUNT) {
            throw new IllegalArgumentException(
                    "expected " + Quantity.COUNT + " values, got " + line.length);
        }
    }
}
