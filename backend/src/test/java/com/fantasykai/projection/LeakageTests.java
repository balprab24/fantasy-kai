package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Projecting week N reads nothing from week N or later (Phase 6 brief, acceptance 5).
 *
 * <p>Each test builds the same league twice: once ending at week 3, and once carrying weeks
 * 4-6 whose every number is a 9999 sentinel -- the player's lines, his team's volume and his
 * opponent's. Week 4's features, baselines and population membership must come out
 * identical. Mutating {@link Timeline#asOf}'s {@code <} to {@code <=} admits week 4 and fails
 * every test here; that mutation was run, and it did.
 */
class LeakageTests {

    private static final int SEASON = 2024;
    private static final double SENTINEL = 9999;

    private static BacktestData league(boolean withFuture) {
        Fixture f = new Fixture().schedule(SEASON - 1, 16, 17).schedule(SEASON, 1, withFuture ? 6 : 4);
        f.wr(7, Fixture.HOME, SEASON - 1, 16, 4, 50, 7).wr(7, Fixture.HOME, SEASON - 1, 17, 6, 80, 9);
        f.wr(8, Fixture.AWAY, SEASON - 1, 16, 3, 30, 5).wr(8, Fixture.AWAY, SEASON - 1, 17, 2, 20, 4);
        for (int week = 1; week <= 3; week++) {
            f.wr(7, Fixture.HOME, SEASON, week, 5 + week, 60 + week, 8 + week);
            f.wr(8, Fixture.AWAY, SEASON, week, 2 + week, 25 + week, 4 + week);
        }
        if (withFuture) {
            for (int week = 4; week <= 6; week++) {
                f.volume(Fixture.HOME, SEASON, week, 9999, 9999, 9999);
                f.volume(Fixture.AWAY, SEASON, week, 9999, 9999, 9999);
                f.wr(7, Fixture.HOME, SEASON, week, SENTINEL, SENTINEL, SENTINEL);
                f.wr(8, Fixture.AWAY, SEASON, week, SENTINEL, SENTINEL, SENTINEL);
            }
        } else {
            // Week 4 is still played -- the case needs a game -- but carries no row, so the
            // only thing the two leagues can differ in is the future itself.
            f.wr(7, Fixture.HOME, SEASON, 4, 1, 1, 1).wr(8, Fixture.AWAY, SEASON, 4, 1, 1, 1);
        }
        return f.build();
    }

    private static Case week4(BacktestData data, long player) {
        return Population.build(data, SEASON).cases().stream()
                .filter(c -> c.week == 4 && c.playerId == player).findFirst().orElseThrow();
    }

    @Test
    void asOfIsStrictlyBeforeTheWeek() {
        Timeline<PlayerGame> timeline = league(true).players().get(7L);
        List<PlayerGame> history = timeline.asOf(SEASON, 4);
        assertThat(history).extracting(PlayerGame::week).containsExactly(16, 17, 1, 2, 3);
        assertThat(timeline.at(SEASON, 4)).isPresent();
    }

    @Test
    void featuresForWeekNIgnoreWeekNAndLater() {
        BacktestData clean = league(false);
        BacktestData future = league(true);
        Priors priors = Priors.from(clean, List.of(SEASON - 1, SEASON));

        double[] expected = Features.compute(week4(clean, 7), priors, 4);
        double[] actual = Features.compute(week4(future, 7), priors, 4);

        assertThat(actual).containsExactly(expected);
        for (double value : actual) {
            assertThat(Math.abs(value)).isLessThan(SENTINEL);
        }
    }

    @Test
    void baselinesForWeekNIgnoreWeekNAndLater() {
        Case clean = week4(league(false), 7);
        Case future = week4(league(true), 7);
        for (Baseline baseline : List.of(new Baseline(Baseline.Kind.PREVIOUS_GAME, 0),
                new Baseline(Baseline.Kind.ROLLING, 3), new Baseline(Baseline.Kind.SEASON_TO_DATE, 0),
                new Baseline(Baseline.Kind.EWMA, 4))) {
            assertThat(baseline.project(future.history, SEASON))
                    .as(baseline.label())
                    .containsExactly(baseline.project(clean.history, SEASON));
        }
    }

    @Test
    void theLabelIsTheOnlyThingWeekNSupplies() {
        Case future = week4(league(true), 7);
        assertThat(future.actualLine()[Quantity.TARGETS]).isEqualTo(SENTINEL);
        assertThat(future.history).allMatch(g -> g.key() < Dated.key(SEASON, 4));
        assertThat(future.teamHistory).allMatch(g -> g.key() < Dated.key(SEASON, 4));
        assertThat(future.opponentHistory).allMatch(g -> g.key() < Dated.key(SEASON, 4));
    }
}
