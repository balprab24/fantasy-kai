package com.fantasykai.projections;

import java.util.List;

/**
 * A player has two regular-season games in one week, which cannot happen.
 *
 * <p>Thrown on purpose, and it fails the whole projection run (owner decision 2026-10-12).
 * Skipping the player would publish an incomplete board and hide the fault: two games in a week
 * means stored data is wrong -- for instance a played game nflverse re-identified, whose stats
 * then arrived a second time under the new id. Measured 0 such player-weeks on 2026-10-12.
 *
 * <p>The message is the diagnosis, because it is what {@code ingest_runs.error} and
 * {@code ingestFreshness} show: the player, the week, and every conflicting game with where it
 * came from. {@link PlayerHistory#before} refuses the same thing, as a backstop that knows only
 * the week.
 *
 * @param playerId the player, by {@code players.id}
 * @param gsisId   his nflverse id, for looking him up in the source files
 * @param week     the week with more than one game
 * @param games    each conflicting game: its nflverse id and whether it is a stat row or an
 *                 appearance
 * @param others   how many further player-weeks are corrupt the same way
 */
public final class CorruptHistoryException extends IllegalStateException {

    private final long playerId;
    private final SeasonWeek week;
    private final List<String> games;

    public CorruptHistoryException(long playerId, String gsisId, SeasonWeek week, List<String> games,
            int others) {
        super("corrupt history: player " + playerId + " (gsis " + gsisId + ") has " + games.size()
                + " regular-season games in " + week + ": " + String.join(", ", games)
                + ". A player plays once a week, so stored data is wrong; no projection was written"
                + (others > 0 ? ". " + others + " more player-week(s) are corrupt the same way" : ""));
        this.playerId = playerId;
        this.week = week;
        this.games = List.copyOf(games);
    }

    public long playerId() {
        return playerId;
    }

    public SeasonWeek week() {
        return week;
    }

    public List<String> games() {
        return games;
    }
}
