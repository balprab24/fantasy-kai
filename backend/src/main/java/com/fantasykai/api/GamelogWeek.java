package com.fantasykai.api;

import java.util.Map;

/**
 * One scored week of a game log.
 *
 * @param stats  raw stat line, keyed by the ruleset's own stat names
 * @param points scored under the requested profile and rounded here, because a
 *               week is a number the API hands out and therefore a boundary
 */
public record GamelogWeek(int season, int week, String seasonType, String opponent,
        Double snapPct, Map<String, Double> stats, double points) {}
