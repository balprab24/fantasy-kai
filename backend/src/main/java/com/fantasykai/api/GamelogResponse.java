package com.fantasykai.api;

import java.util.List;

/**
 * {@code /players/{id}/gamelog}.
 *
 * @param totalPoints the rounded sum of the unrounded weeks -- not the sum of
 *                    the rounded {@code points} on each {@link GamelogWeek},
 *                    which is a different number
 */
public record GamelogResponse(long playerId, String name, String position, Integer season,
        long profileId, int gamesPlayed, double totalPoints, List<GamelogWeek> weeks) {}
