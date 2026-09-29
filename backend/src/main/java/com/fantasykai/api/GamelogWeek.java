package com.fantasykai.api;

import com.fantasykai.query.Usage;
import java.util.Map;

/**
 * One scored week of a game log.
 *
 * @param team   the player's team that week
 * @param home   designated home side; null when the row's team is neither side
 * @param usage  attempts, carries and targets -- volume, never scored
 * @param stats  raw scorable stat line, keyed by the ruleset's own stat names
 * @param points scored under the requested profile and rounded here, because a
 *               week is a number the API hands out and therefore a boundary
 */
public record GamelogWeek(int season, int week, String seasonType, String team,
        String opponent, Boolean home, Double snapPct, Usage usage,
        Map<String, Double> stats, double points) {}
