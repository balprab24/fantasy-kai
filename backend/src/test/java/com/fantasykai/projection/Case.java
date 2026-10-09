package com.fantasykai.projection;

import java.util.List;

/**
 * One player-week to project: what was knowable before kickoff, and -- kept apart from it --
 * what happened.
 *
 * <p>The feature builder receives {@link #history}, {@link #teamHistory} and the pre-game
 * context (team, game, line). It never receives {@link #actual}, which only the evaluator
 * reads. The week-N team is taken from the week-N row when there is one: that is roster
 * information, public before kickoff, not an outcome.
 */
final class Case {

    final long playerId;
    final String position;
    final int season;
    final int week;
    final int teamId;
    final Game game;
    /** The week's stored line, or null when the player did not play (population P0 only). */
    final PlayerGame actual;
    final List<PlayerGame> history;
    final List<TeamGame> teamHistory;
    /** What this week's opponent allowed, game by game, cut at week N like everything else. */
    final List<DefenseGame> opponentHistory;
    /** The league's weeks before week N -- every season; the environment reads only this one. */
    final List<LeagueWeek> leagueHistory;
    final double trailingOpportunity;

    /** Primary population: top-N by trailing opportunity among players who played. */
    boolean inP;
    /** Availability sensitivity: top-N among players who played their team's last game. */
    boolean inP0;
    /** Everyone with history who played -- the board's population. */
    boolean inPAll;

    double[] features;

    Case(long playerId, String position, int season, int week, int teamId, Game game,
            PlayerGame actual, List<PlayerGame> history, List<TeamGame> teamHistory,
            List<DefenseGame> opponentHistory, List<LeagueWeek> leagueHistory) {
        this.playerId = playerId;
        this.position = position;
        this.season = season;
        this.week = week;
        this.teamId = teamId;
        this.game = game;
        this.actual = actual;
        this.history = history;
        this.teamHistory = teamHistory;
        this.opponentHistory = opponentHistory;
        this.leagueHistory = leagueHistory;
        this.trailingOpportunity = History.trailingOpportunity(history, position);
    }

    /** What happened: the stored line, or a line of zeros for a player who did not play. */
    double[] actualLine() {
        return actual != null ? actual.line() : new double[Quantity.COUNT];
    }

    int weekKey() {
        return Dated.key(season, week);
    }
}
