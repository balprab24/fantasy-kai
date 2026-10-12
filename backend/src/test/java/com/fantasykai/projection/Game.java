package com.fantasykai.projection;

/**
 * A regular-season game as a projection may see it before kickoff: who plays, where, and
 * the market's line.
 *
 * <p>{@code spread} is positive when the home side is favoured -- measured (+0.45 against
 * the home margin over 2020-25, north-star Phase 6 brief), not read from a data
 * dictionary. nflverse keeps one line per game, very likely the closing one, which a
 * Wednesday projection would not have seen; the backtest measures how much of its gain
 * depends on that.
 *
 * @param played whether the game has a final score. A week is evaluated only once played
 */
record Game(long id, int season, int week, int homeTeamId, int awayTeamId,
        double spread, double total, boolean played) implements Dated {

    boolean involves(int teamId) {
        return teamId == homeTeamId || teamId == awayTeamId;
    }

    boolean isHome(int teamId) {
        return teamId == homeTeamId;
    }

    boolean hasLine() {
        return !Double.isNaN(spread) && !Double.isNaN(total);
    }

    /** The points the market expects this team to score: {@code (total ± spread) / 2}. */
    double impliedTotal(int teamId) {
        return isHome(teamId) ? (total + spread) / 2 : (total - spread) / 2;
    }

    /** The spread from this team's side: positive when it is favoured. */
    double spreadFor(int teamId) {
        return isHome(teamId) ? spread : -spread;
    }
}
