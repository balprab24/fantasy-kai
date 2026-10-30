package com.fantasykai.projections;

import static com.fantasykai.projections.Games.receiver;
import static com.fantasykai.projections.Games.receptions;
import static com.fantasykai.projections.Games.w;
import static com.fantasykai.projections.Games.zero;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.scoring.StatKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The frozen formula, case by case, against values worked out by hand. */
class WeightedAverageProjectionProviderTests {

    private static final WeightedAverageProjectionProvider PROVIDER = new WeightedAverageProjectionProvider();

    private static RawProjection project(int season, int week, HistoricalGame... games) {
        return PROVIDER.project(PlayerHistory.before(new SeasonWeek(season, week), List.of(games)))
                .orElseThrow();
    }

    private static double rec(RawProjection p) {
        return p.get(StatKey.REC);
    }

    @Test
    void theWeightHalvesEveryFourGames() {
        assertThat(WeightedAverageProjectionProvider.weight(0)).isEqualTo(1.0);
        assertThat(WeightedAverageProjectionProvider.weight(4)).isEqualTo(0.5);
        assertThat(WeightedAverageProjectionProvider.weight(8)).isEqualTo(0.25);
        assertThat(WeightedAverageProjectionProvider.weight(1)).isCloseTo(Math.pow(2, -0.25), within(1e-15));
    }

    @Test
    void oneGameIsThatGame() {
        RawProjection p = project(2025, 2, receiver(2025, 1, 7, 83, 1, 10));
        assertThat(rec(p)).isEqualTo(7);
        assertThat(p.get(StatKey.REC_YD)).isEqualTo(83);
        assertThat(p.targets()).isEqualTo(10);
        assertThat(p.gamesUsed()).isEqualTo(1);
        assertThat(p.lastGame()).isEqualTo(new SeasonWeek(2025, 1));
    }

    @Test
    void severalGamesByHand() {
        // Four games of 4 receptions, then 14. Weights from newest: 1, 2^-0.25, 2^-0.5, 2^-0.75, 0.5;
        // their sum is 3.642607, so the projection is 4 + 10 / 3.642607 = 6.745287.
        RawProjection p = project(2025, 6, receptions(2025, 1, 4), receptions(2025, 2, 4),
                receptions(2025, 3, 4), receptions(2025, 4, 4), receptions(2025, 5, 14));
        double sum = w(0) + w(1) + w(2) + w(3) + w(4);
        assertThat(sum).isCloseTo(3.642607, within(1e-6));
        assertThat(rec(p)).isCloseTo(4 + 10 / sum, within(1e-12));
        assertThat(rec(p)).isCloseTo(6.745287, within(1e-6));
        assertThat(p.gamesUsed()).isEqualTo(5);
    }

    @Test
    void aMissedWeekOrAByeAddsNoDecay() {
        // Weeks 1 and 3 with week 2 missed weigh exactly like weeks 1 and 2.
        RawProjection gap = project(2025, 4, receptions(2025, 1, 10), receptions(2025, 3, 2));
        RawProjection consecutive = project(2025, 3, receptions(2025, 1, 10), receptions(2025, 2, 2));
        assertThat(rec(gap)).isEqualTo(rec(consecutive));
        assertThat(rec(gap)).isCloseTo((10 * w(1) + 2) / (w(1) + 1), within(1e-12));
    }

    @Test
    void historyCrossesSeasonsWithNoResetAndNoGap() {
        RawProjection acrossSeasons = project(2026, 2,
                receptions(2025, 17, 3), receptions(2025, 18, 5), receptions(2026, 1, 9));
        RawProjection oneSeason = project(2025, 4,
                receptions(2025, 1, 3), receptions(2025, 2, 5), receptions(2025, 3, 9));
        assertThat(rec(acrossSeasons)).isEqualTo(rec(oneSeason));

        // Week 1 projects from last season alone.
        RawProjection weekOne = project(2026, 1, receptions(2025, 17, 3), receptions(2025, 18, 5));
        assertThat(weekOne.gamesUsed()).isEqualTo(2);
        assertThat(weekOne.lastGame()).isEqualTo(new SeasonWeek(2025, 18));
    }

    @Test
    void nothingBefore2020Counts() {
        RawProjection p = project(2020, 2, receptions(2019, 17, 99), receptions(2020, 1, 5));
        assertThat(rec(p)).isEqualTo(5);
        assertThat(p.gamesUsed()).isEqualTo(1);
        assertThat(PROVIDER.project(PlayerHistory.before(new SeasonWeek(2020, 1),
                List.of(receptions(2019, 17, 99))))).isEmpty();
    }

    @Test
    void aTeamChangeDoesNotResetHistory() {
        // Weeks 1-4 for one team, traded, weeks 6-9 for another: a history has no team in it,
        // so all eight games count, oldest to newest, as one run of appearances.
        HistoricalGame[] games = new HistoricalGame[8];
        int[] weeks = {1, 2, 3, 4, 6, 7, 8, 9};
        double expected = 0;
        double total = 0;
        for (int i = 0; i < 8; i++) {
            games[i] = receptions(2025, weeks[i], i + 1);
            expected += w(7 - i) * (i + 1);
            total += w(7 - i);
        }
        RawProjection p = project(2025, 10, games);
        assertThat(p.gamesUsed()).isEqualTo(8);
        assertThat(rec(p)).isCloseTo(expected / total, within(1e-12));
    }

    @Test
    void aZeroStatAppearanceIsAGameAndNoRowIsNot() {
        RawProjection played = project(2025, 3, receptions(2025, 1, 6), zero(2025, 2));
        RawProjection absent = project(2025, 3, receptions(2025, 1, 6));
        assertThat(played.gamesUsed()).isEqualTo(2);
        assertThat(rec(played)).isCloseTo(6 * w(1) / (w(1) + 1), within(1e-12));
        assertThat(absent.gamesUsed()).isEqualTo(1);
        assertThat(rec(absent)).isEqualTo(6);
    }

    @Test
    void noHistoryNoProjection() {
        assertThat(PROVIDER.project(PlayerHistory.before(new SeasonWeek(2025, 1), List.of()))).isEmpty();
        // Games only at or after the target are not history either.
        assertThat(PROVIDER.project(PlayerHistory.before(new SeasonWeek(2025, 5),
                List.of(receptions(2025, 5, 8), receptions(2025, 6, 8))))).isEmpty();
    }

    @Test
    void limitedHistoryUsesTheSameFormula() {
        RawProjection p = project(2025, 4, receptions(2025, 1, 2), receptions(2025, 2, 3),
                receptions(2025, 3, 7));
        assertThat(p.gamesUsed()).isEqualTo(3);
        assertThat(rec(p)).isCloseTo((2 * w(2) + 3 * w(1) + 7) / (w(2) + w(1) + 1), within(1e-12));
    }

    @Test
    void valuesStayFractionalAndUnrounded() {
        RawProjection p = project(2025, 3, receiver(2025, 1, 5, 61, 1, 8), receiver(2025, 2, 4, 47, 0, 6));
        double td = p.get(StatKey.REC_TD);
        assertThat(td).isCloseTo(w(1) / (w(1) + 1), within(1e-12));
        assertThat(td).isNotEqualTo(Math.round(td * 100) / 100.0);
    }

    @Test
    void theSourceIsStableAndVersioned() {
        ProjectionSource source = PROVIDER.source();
        assertThat(source.id()).isEqualTo("weighted-average-v1");
        assertThat(source.label()).isEqualTo("Fantasy-Kai Baseline");
        assertThat(source.description()).doesNotContainIgnoringCase("AI").doesNotContainIgnoringCase("model");
        assertThat(project(2025, 2, receptions(2025, 1, 1)).source()).isEqualTo(source);
    }
}
