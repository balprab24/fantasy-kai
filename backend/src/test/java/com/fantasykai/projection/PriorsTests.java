package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Priors are fitted on the training seasons and on nothing else: rows from the season being
 * evaluated -- here a 9999 sentinel season -- must not move a single positional rate. The
 * as-of door guards a player's own history; this guards the means he is shrunk toward.
 */
class PriorsTests {

    private static BacktestData league(boolean withEvaluatedSeason) {
        Fixture f = new Fixture().schedule(2023, 1, 3).schedule(2024, 1, 3);
        for (int week = 1; week <= 3; week++) {
            f.wr(1, Fixture.HOME, 2023, week, 4 + week, 50 + week, 7 + week);
            f.wr(2, Fixture.AWAY, 2023, week, 2, 20 + week, 4);
            if (withEvaluatedSeason) {
                f.wr(1, Fixture.HOME, 2024, week, 9999, 9999, 9999);
                f.volume(Fixture.AWAY, 2024, week, 9999, 9999, 9999);
            }
        }
        return f.build();
    }

    @Test
    void anEvaluatedSeasonMovesNoPrior() {
        Priors clean = Priors.from(league(false), List.of(2023));
        Priors withFuture = Priors.from(league(true), List.of(2023));
        for (int q = 0; q < Quantity.COUNT; q++) {
            assertThat(withFuture.perGame("WR", q)).as(Quantity.name(q)).isEqualTo(clean.perGame("WR", q));
            assertThat(withFuture.allowed("WR", q)).as(Quantity.name(q)).isEqualTo(clean.allowed("WR", q));
        }
        for (Rate rate : Rate.values()) {
            assertThat(withFuture.rate("WR", rate)).as(rate.name()).isEqualTo(clean.rate("WR", rate));
        }
        for (int slot = 0; slot < 3; slot++) {
            assertThat(withFuture.teamVolume(slot)).isEqualTo(clean.teamVolume(slot));
        }
        assertThat(withFuture.impliedTotal()).isEqualTo(clean.impliedTotal());
    }
}
