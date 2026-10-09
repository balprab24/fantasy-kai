package com.fantasykai.projection;

/**
 * One team's volume in one game, summed over every player who recorded a stat for it --
 * all positions, so a fullback's carry and a quarterback's kneel count. Stats aggregated,
 * never points.
 *
 * @param passAirYards the intended air yards of every pass the team threw -- nflverse's
 *                     denominator for air-yard share. {@code NaN} outside the v2 dataset
 */
record TeamGame(int teamId, long gameId, int season, int week,
        int passAtt, int rushAtt, int targets, double passAirYards) implements Dated {}
