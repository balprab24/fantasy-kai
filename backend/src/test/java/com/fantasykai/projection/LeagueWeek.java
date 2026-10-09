package com.fantasykai.projection;

/**
 * The league's skill-position production in one regular-season week: per-quantity totals
 * over every stored row, and how many team-games produced them. The scoring environment a
 * week-N projection may see is built from these, through {@link Timeline#asOf}, and only
 * from the season being projected.
 */
record LeagueWeek(int season, int week, int teamGames, double[] totals) implements Dated {}
