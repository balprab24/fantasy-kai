package com.fantasykai.query;

import com.fantasykai.scoring.StatLine;

/**
 * One week of a player's game log: the matchup context the UI shows alongside
 * the raw stats that get scored.
 *
 * @param team    the player's team that week -- not his current one, which is
 *                what differs for a player traded mid-season
 * @param home    whether {@code team} was the designated home side; null if the
 *                stat row's team is neither side of the game, which is a data
 *                fault (0 of 115,590 rows on 2026-09-28) and must not quietly
 *                read as "away". Neutral-site games are not distinguished.
 * @param snapPct null for the 74 rows of 112,319 where nflverse published no snap count
 * @param usage   volume, kept apart from the scorable {@code line}
 */
public record GamelogRow(int season, int week, String seasonType, String team,
        String opponent, Boolean home, Double snapPct, Usage usage, StatLine line) {}
