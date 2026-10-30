package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.scoring.StatKey;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Experiment v2's new inputs see nothing from week N or later, and the environment sees only
 * the season it projects.
 *
 * <p>The league is built twice: once ending at week 3, and once carrying weeks 4-6 whose air
 * yards, team passing air yards and lines are 9999 sentinels -- which also flood the league's
 * weekly totals. Week 4's air-yard and environment features must not move. Mutating
 * {@link Timeline#asOf} to {@code <=} fails these; so does dropping the environment's
 * same-season filter (run, and it did).
 */
class V2LeakageTests {

    private static final int SEASON = 2024;
    private static final double SENTINEL = 9999;
    private static final List<String> V2 = List.of("ewma:air_yards", "ayshare", "x_air_yards", "racr",
            "env:rec_yd", "env:targets", "env:pass_yd");

    private static BacktestData league(boolean withFuture) {
        Fixture f = new Fixture().schedule(SEASON - 1, 16, 17).schedule(SEASON, 1, withFuture ? 6 : 4);
        f.wrAir(7, Fixture.HOME, SEASON - 1, 16, 4, 50, 7, 70).wrAir(7, Fixture.HOME, SEASON - 1, 17, 6, 80, 9, 90);
        f.wrAir(8, Fixture.AWAY, SEASON - 1, 16, 3, 30, 5, 40).wrAir(8, Fixture.AWAY, SEASON - 1, 17, 2, 20, 4, 25);
        for (int week = 1; week <= 3; week++) {
            f.volume(Fixture.HOME, SEASON, week, 30, 25, 30, 200 + week);
            f.volume(Fixture.AWAY, SEASON, week, 30, 25, 30, 180 + week);
            f.wrAir(7, Fixture.HOME, SEASON, week, 5 + week, 60 + week, 8 + week, 75 + week);
            f.wrAir(8, Fixture.AWAY, SEASON, week, 2 + week, 25 + week, 4 + week, 35 + week);
        }
        if (withFuture) {
            for (int week = 4; week <= 6; week++) {
                f.volume(Fixture.HOME, SEASON, week, 9999, 9999, 9999, SENTINEL);
                f.volume(Fixture.AWAY, SEASON, week, 9999, 9999, 9999, SENTINEL);
                f.wrAir(7, Fixture.HOME, SEASON, week, SENTINEL, SENTINEL, SENTINEL, SENTINEL);
                f.wrAir(8, Fixture.AWAY, SEASON, week, SENTINEL, SENTINEL, SENTINEL, SENTINEL);
            }
        } else {
            f.wrAir(7, Fixture.HOME, SEASON, 4, 1, 1, 1, 1).wrAir(8, Fixture.AWAY, SEASON, 4, 1, 1, 1, 1);
        }
        return f.build();
    }

    private static Case week(BacktestData data, int week, long player) {
        return Population.build(data, SEASON).cases().stream()
                .filter(c -> c.week == week && c.playerId == player).findFirst().orElseThrow();
    }

    @Test
    void airYardsAndEnvironmentForWeekNIgnoreWeekNAndLater() {
        BacktestData clean = league(false);
        BacktestData future = league(true);
        Priors priors = Priors.from(clean, List.of(SEASON - 1));
        double[] expected = Features.compute(week(clean, 4, 7), priors, 4);
        double[] actual = Features.compute(week(future, 4, 7), priors, 4);
        for (String name : V2) {
            int i = Features.index(name);
            assertThat(actual[i]).as(name).isEqualTo(expected[i]);
            assertThat(Math.abs(actual[i])).as(name).isLessThan(SENTINEL);
        }
    }

    @Test
    void airYardShareIsHisAirYardsOverHisTeamsPassingAirYards() {
        // One game of history (2024 week 1 for a debut-free check): share = 76 / 201.
        Fixture f = new Fixture().schedule(SEASON, 1, 2);
        f.volume(Fixture.HOME, SEASON, 1, 30, 25, 30, 201);
        f.wrAir(7, Fixture.HOME, SEASON, 1, 6, 61, 9, 76).wrAir(7, Fixture.HOME, SEASON, 2, 1, 1, 1, 1);
        BacktestData data = f.build();
        Case c = week(data, 2, 7);
        double[] features = Features.compute(c, Priors.from(data, List.of(SEASON)), 4);
        assertThat(features[Features.index("ayshare")]).isCloseTo(76.0 / 201, within(1e-12));
        assertThat(features[Features.index("ewma:air_yards")]).isCloseTo(76, within(1e-12));
    }

    @Test
    void theEnvironmentStartsAtOneAndReadsOnlyThisSeason() {
        BacktestData data = league(false);
        Priors priors = Priors.from(data, List.of(SEASON - 1));
        // Week 1: nothing of this season is known yet, whatever last season did.
        Case first = week(data, 1, 7);
        assertThat(Features.environment(first, priors)).containsOnly(1.0);

        // Week 3: this season's weeks 1 and 2 only, blended with 32 team-games at the prior.
        Case third = week(data, 3, 7);
        int q = Quantity.of(StatKey.REC_YD);
        double seasonSoFar = (61 + 26) + (62 + 27); // both receivers, weeks 1 and 2
        double base = priors.leaguePerTeamGame(q);
        double expected = ((seasonSoFar + 32 * base) / (4 + 32)) / base;
        assertThat(Features.environment(third, priors)[q]).isCloseTo(expected, within(1e-12));
    }
}
