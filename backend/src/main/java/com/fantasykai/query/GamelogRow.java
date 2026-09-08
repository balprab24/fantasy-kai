package com.fantasykai.query;

import com.fantasykai.scoring.StatLine;

/**
 * One week of a player's game log: the matchup context the UI shows alongside
 * the raw stats that get scored.
 *
 * @param snapPct null for the 74 rows of 112,319 where nflverse published no snap count
 */
public record GamelogRow(int season, int week, String seasonType, String opponent,
        Double snapPct, StatLine line) {}
