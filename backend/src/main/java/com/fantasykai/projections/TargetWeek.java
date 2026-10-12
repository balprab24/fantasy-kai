package com.fantasykai.projections;

import java.time.Instant;
import java.util.List;

/**
 * The week the projection job projects, and the regular-season games in it.
 *
 * @param week  the week
 * @param games every regular-season game scheduled in it, kicked off or not
 */
public record TargetWeek(SeasonWeek week, List<Game> games) {

    public TargetWeek {
        games = List.copyOf(games);
    }

    /**
     * One game of the week.
     *
     * @param kickoff when it starts; null when the schedule has no date for it, which leaves it
     *                open -- a game cannot be locked at a time nobody knows
     */
    public record Game(long id, int homeTeamId, int awayTeamId, Instant kickoff) {

        /** Started at or before {@code now}: its projections are the record and never change. */
        public boolean kickedOffBy(Instant now) {
            return kickoff != null && !kickoff.isAfter(now);
        }
    }
}
