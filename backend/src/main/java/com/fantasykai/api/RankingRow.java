package com.fantasykai.api;

/**
 * One row of a ranking.
 *
 * @param rank          position in the full ranking, not within the page
 * @param points        total under the requested scope, rounded once here
 * @param pointsPerGame {@code points} divided by {@code gamesPlayed}, rounded once here
 * @param espnId        the player's ESPN id, digits only, or null; identity for
 *                      display (a headshot), fetched for the page after ranking
 */
public record RankingRow(int rank, long playerId, String name, String position, String team,
        int gamesPlayed, double points, double pointsPerGame, String espnId) {}
