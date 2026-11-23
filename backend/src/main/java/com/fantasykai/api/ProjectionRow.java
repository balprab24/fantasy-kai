package com.fantasykai.api;

import com.fantasykai.projections.Availability;
import com.fantasykai.projections.SeasonWeek;
import com.fantasykai.scoring.ExplainedScore;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;

/**
 * A projected line, scored.
 *
 * @param points      the scored total, rounded once, here
 * @param history     {@code LIMITED} under four games, else {@code ESTABLISHED}
 * @param parts       each stat as value x rate = points
 * @param bonuses     the threshold bonuses that bear on this line (it has some of the stat, or
 *                    the bonus was paid), each with whether it was reached. The parts plus the
 *                    points of the bonuses that were reached are the total, which is then
 *                    rounded once, in {@code points}. On a projected line a bonus is
 *                    all-or-nothing at the line's mean, a stated approximation and not an
 *                    expected value
 * @param rawPoints   the unrounded total the board sorts on; never serialized
 * @param dataThrough the newest game this line was built from; never serialized on a row, only
 *                    summarised on the board's envelope
 */
public record ProjectionRow(long playerId, String name, String position, String team, String opponent,
        boolean home, Instant kickoff, boolean locked, double points, int gamesUsed,
        String history, Availability availability, String espnId, Instant generatedAt,
        List<ExplainedScore.Part> parts, List<ExplainedScore.BonusLine> bonuses, int season, int week,
        @JsonIgnore double rawPoints, @JsonIgnore SeasonWeek dataThrough) {}
