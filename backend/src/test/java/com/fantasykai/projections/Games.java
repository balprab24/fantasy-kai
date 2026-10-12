package com.fantasykai.projections;

import com.fantasykai.scoring.StatKey;

/** Small, hand-checkable histories for the projection tests. */
final class Games {

    private Games() {}

    /** A receiver's game: receptions, receiving yards, a receiving TD count and targets. */
    static HistoricalGame receiver(int season, int week, double rec, double recYd, double recTd,
            double targets) {
        double[] stats = new double[StatKey.COUNT];
        stats[StatKey.REC.index()] = rec;
        stats[StatKey.REC_YD.index()] = recYd;
        stats[StatKey.REC_TD.index()] = recTd;
        return new HistoricalGame(new SeasonWeek(season, week), stats, 0, 0, targets);
    }

    static HistoricalGame receptions(int season, int week, double rec) {
        return receiver(season, week, rec, 0, 0, 0);
    }

    /** Played and recorded nothing: a real appearance. */
    static HistoricalGame zero(int season, int week) {
        return new HistoricalGame(new SeasonWeek(season, week), new double[StatKey.COUNT], 0, 0, 0);
    }

    static double w(int age) {
        return Math.pow(2, -age / 4.0);
    }
}
