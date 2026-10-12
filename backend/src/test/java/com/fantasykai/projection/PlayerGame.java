package com.fantasykai.projection;

/**
 * One skill player's stored stat line for one regular-season game.
 *
 * @param position today's {@code players.position} -- an anachronism for a player who
 *                 changed position, documented in the accuracy report rather than hidden
 * @param snapPct  offensive snap share in percent, {@code NaN} when not recorded. Never 0
 *                 for "missing": a missing reading is not a benching
 * @param line     {@link Quantity#COUNT} values: the scorable stats, then usage
 * @param returns  punt plus kick returns, the volume a return touchdown comes from
 * @param team     the player's team's totals in this same game, for his shares
 */
record PlayerGame(long playerId, String position, long gameId, int season, int week,
        int teamId, double snapPct, double[] line, int returns, TeamGame team) implements Dated {

    PlayerGame {
        if (line.length != Quantity.COUNT) {
            throw new IllegalArgumentException(
                    "expected " + Quantity.COUNT + " values, got " + line.length);
        }
    }
}
