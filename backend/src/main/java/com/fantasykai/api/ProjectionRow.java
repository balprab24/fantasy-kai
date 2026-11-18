package com.fantasykai.api;

import com.fantasykai.projections.Availability;
import com.fantasykai.scoring.ExplainedScore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;

/**
 * A projected line, scored.
 *
 * @param points      the scored total, rounded once, here
 * @param history     {@code LIMITED} under four games, else {@code ESTABLISHED}
 * @param bonusPoints the threshold bonuses' expected value: the total less
 *                    its linear parts, unrounded
 * @param rawPoints   the unrounded total the board sorts on; never serialized
 */
public record ProjectionRow(long playerId, String name, String position, String team, String opponent,
        boolean home, Instant kickoff, boolean locked, double points, int gamesUsed,
        String history, Availability availability, String espnId, Instant generatedAt,
        List<ExplainedScore.Part> parts, double bonusPoints, int season, int week,
        @JsonIgnore double rawPoints) {}
