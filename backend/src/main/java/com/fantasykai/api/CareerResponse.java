package com.fantasykai.api;

import java.util.List;

/**
 * {@code /players/{id}/career}: every regular season a player has a stat line
 * in, scored under one profile, newest first.
 *
 * @param seasonsPlayed every season with any game at all, playoffs included,
 *                      newest first -- a superset of {@code seasons}. A season
 *                      a player only reached in January (seven player-seasons
 *                      at QB/RB/WR/TE, measured 2026-09-28) has a game log and
 *                      no regular-season line, and would otherwise be
 *                      unreachable from the page
 */
public record CareerResponse(long playerId, long profileId, List<CareerSeason> seasons,
        List<Integer> seasonsPlayed) {}
