package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.scoring.StatKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Each baseline against a hand-computed value on one receiver's five games. */
class BaselineTests {

    private static final int REC = Quantity.of(StatKey.REC);

    /** Receptions 2, 4, 6, 8 in 2023 weeks 15-18, then 10 in 2024 week 1. */
    private static List<PlayerGame> history(int throughSeason) {
        Fixture f = new Fixture().schedule(2023, 15, 18).schedule(2024, 1, 2);
        f.wr(1, Fixture.HOME, 2023, 15, 2, 0, 0).wr(1, Fixture.HOME, 2023, 16, 4, 0, 0)
                .wr(1, Fixture.HOME, 2023, 17, 6, 0, 0).wr(1, Fixture.HOME, 2023, 18, 8, 0, 0)
                .wr(1, Fixture.HOME, 2024, 1, 10, 0, 0);
        Timeline<PlayerGame> timeline = f.build().players().get(1L);
        return throughSeason == 2024 ? timeline.asOf(2024, 2) : timeline.asOf(2024, 1);
    }

    private static double project(Baseline.Kind kind, double parameter, List<PlayerGame> h) {
        return new Baseline(kind, parameter).project(h, 2024)[REC];
    }

    @Test
    void previousGameIsTheMostRecentOneEvenAcrossASeason() {
        assertThat(project(Baseline.Kind.PREVIOUS_GAME, 0, history(2024))).isEqualTo(10);
        assertThat(project(Baseline.Kind.PREVIOUS_GAME, 0, history(2023))).isEqualTo(8);
    }

    @Test
    void rollingAveragesTheLastKGamesPlayed() {
        assertThat(project(Baseline.Kind.ROLLING, 3, history(2024))).isEqualTo(8);
        assertThat(project(Baseline.Kind.ROLLING, 5, history(2024))).isEqualTo(6);
    }

    @Test
    void seasonToDateFallsBackToLastSeasonBeforeAGameIsPlayed() {
        assertThat(project(Baseline.Kind.SEASON_TO_DATE, 0, history(2024))).isEqualTo(10);
        assertThat(project(Baseline.Kind.SEASON_TO_DATE, 0, history(2023))).isEqualTo(5);
    }

    @Test
    void ewmaWeighsByGamesAgo() {
        // half-life 1: weights 1/16, 1/8, 1/4, 1/2, 1 over 2, 4, 6, 8, 10.
        double expected = (2 / 16.0 + 4 / 8.0 + 6 / 4.0 + 8 / 2.0 + 10) / (1 / 16.0 + 1 / 8.0 + 1 / 4.0 + 1 / 2.0 + 1);
        assertThat(project(Baseline.Kind.EWMA, 1, history(2024))).isCloseTo(expected, within(1e-12));
    }
}
