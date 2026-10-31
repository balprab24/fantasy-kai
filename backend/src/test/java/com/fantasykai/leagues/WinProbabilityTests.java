package com.fantasykai.leagues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.leagues.WinProbability.Starter;
import com.fantasykai.leagues.WinProbability.TeamWeek;
import java.util.List;
import org.junit.jupiter.api.Test;

class WinProbabilityTests {

    @Test
    void theNormalCdfIsRightToSixPlaces() {
        assertThat(WinProbability.phi(0)).isCloseTo(0.5, within(1e-7));
        assertThat(WinProbability.phi(1)).isCloseTo(0.8413447, within(1e-6));
        assertThat(WinProbability.phi(-1.96)).isCloseTo(0.0249979, within(1e-6));
        assertThat(WinProbability.phi(3)).isCloseTo(0.9986501, within(1e-6));
    }

    @Test
    void aTeamsWeekSumsMeansAndVariances() {
        TeamWeek week = WinProbability.team(List.of(new Starter("RB", 1, 10), new Starter("WR", 2, 10)));

        double rb = ProjectionUncertainty.sigma("RB", 10);
        double wr = ProjectionUncertainty.sigma("WR", 10);
        assertThat(week.mean()).isEqualTo(20.0);
        assertThat(week.variance()).isCloseTo(rb * rb + wr * wr, within(1e-9));
    }

    /** A quarterback and his own receiver move together: 2·r·σ·σ more variance. Another team's do not. */
    @Test
    void aStackAddsCovarianceAndOnlyASameTeamStack() {
        List<Starter> stacked = List.of(new Starter("QB", 7, 20), new Starter("WR", 7, 12));
        List<Starter> apart = List.of(new Starter("QB", 7, 20), new Starter("WR", 8, 12));
        double qb = ProjectionUncertainty.sigma("QB", 20);
        double wr = ProjectionUncertainty.sigma("WR", 12);

        assertThat(WinProbability.team(stacked).variance() - WinProbability.team(apart).variance())
                .isCloseTo(2 * 0.37 * qb * wr, within(1e-9));
    }

    @Test
    void aPlayerKnownNotToPlayAddsNoUncertainty_andUnmodelledPositionsAddNone() {
        TeamWeek week = WinProbability.team(List.of(new Starter("WR", 1, 0), new Starter("K", 1, 8)));

        assertThat(week.variance()).isZero();
        assertThat(week.mean()).isEqualTo(8.0);
    }

    @Test
    void probabilitiesAreSymmetricAndFollowTheGap() {
        TeamWeek strong = new TeamWeek(110, 100);
        TeamWeek weak = new TeamWeek(100, 100);

        double p = WinProbability.beats(strong, weak);
        assertThat(p).isCloseTo(WinProbability.phi(10 / Math.sqrt(200)), within(1e-12)).isGreaterThan(0.5);
        assertThat(p + WinProbability.beats(weak, strong)).isCloseTo(1, within(1e-9));
        assertThat(WinProbability.beats(new TeamWeek(5, 0), new TeamWeek(5, 0))).isEqualTo(0.5);
        assertThat(WinProbability.beats(new TeamWeek(6, 0), new TeamWeek(5, 0))).isEqualTo(1.0);
    }
}
