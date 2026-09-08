package com.fantasykai.query;

import com.fantasykai.scoring.StatLine;

/**
 * One player-week, carrying the identity the ranking renders and the raw stats
 * it scores. Raw stats only -- points depend on a ruleset this type knows
 * nothing about.
 *
 * @param week the regular-season week, kept so {@code last4} can window on it
 */
public record ScorableRow(long playerId, String name, String position, String team,
        int week, StatLine line) {}
