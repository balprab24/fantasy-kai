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
 * @param rank        1-based place in the week's whole ranked set under the member's ruleset, in
 *                    the board's order: before the position filter and the name search, so it does
 *                    not restart under either. Null for a line the board does not rank (Out, on a
 *                    reserve list, off the roster), on the player's own page too: his line is
 *                    shown and he has no place
 * @param positionRank likewise among his own position's ranked lines; null when {@code rank} is
 * @param usage       the volume the line was projected from, as stored and never scored
 * @param rawPoints   the unrounded total the board sorts on; never serialized
 * @param dataThrough the newest game this line was built from; never serialized on a row, only
 *                    summarised on the board's envelope
 * @param stats       the stored raw line, indexed by {@code StatKey}: what the preset strip scores,
 *                    so it is exactly the line the member was shown. Never serialized, never
 *                    modified
 */
public record ProjectionRow(long playerId, String name, String position, String team, String opponent,
        boolean home, Instant kickoff, boolean locked, double points, int gamesUsed,
        String history, Availability availability, String espnId, Instant generatedAt,
        List<ExplainedScore.Part> parts, List<ExplainedScore.BonusLine> bonuses, int season, int week,
        Integer rank, Integer positionRank, Usage usage,
        @JsonIgnore double rawPoints, @JsonIgnore SeasonWeek dataThrough, @JsonIgnore double[] stats) {

    /**
     * Projected volume, display only: the stored {@code pass_att}, {@code rush_att} and
     * {@code targets}, unrounded like {@code parts}. No rate applies to them and no ruleset reads
     * them. Named as the frontend's {@code Usage} type names them, without completions, which a
     * projection does not carry.
     */
    public record Usage(double passAtt, double rushAtt, double targets) {}

    /** The same line with its place in the ranked week; the line itself is untouched. */
    ProjectionRow withRanks(int rank, int positionRank) {
        return new ProjectionRow(playerId, name, position, team, opponent, home, kickoff, locked, points,
                gamesUsed, history, availability, espnId, generatedAt, parts, bonuses, season, week,
                rank, positionRank, usage, rawPoints, dataThrough, stats);
    }

    /** A copy: the row's line is read, never modified, by whoever asks. */
    @Override
    @JsonIgnore
    public double[] stats() {
        return stats == null ? null : stats.clone();
    }
}
