package com.fantasykai.projection;

/**
 * What one defense allowed in one game: the summed lines of the opposing skill players,
 * by position ({@link Priors#POSITIONS} order) and quantity. Raw stats, never points.
 */
record DefenseGame(int teamId, long gameId, int season, int week, double[][] allowed)
        implements Dated {}
