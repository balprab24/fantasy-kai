package com.fantasykai.query;

import com.fantasykai.scoring.StatLine;

/**
 * One regular-season player-week at a position: just enough to score it and to
 * say where it stood. Everything a rank needs and nothing a page renders, so the
 * position-wide scan behind a career stays as narrow as the ranking's own.
 */
public record PositionWeekRow(long playerId, int season, int week, StatLine line) {}
