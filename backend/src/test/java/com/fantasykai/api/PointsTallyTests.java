package com.fantasykai.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A tally is a function of its weeks, not of the order the rows arrived in.
 * No Spring, no database: this is arithmetic, and a small fixture served by a
 * sequential scan could never show it -- the scan's order moves with upserts
 * and synchronized scans, not with anything a test controls.
 */
class PointsTallyTests {

    @Test
    void theSameWeeksInAnyOrderSumToTheSameDouble() {
        // The textbook non-associative triple.
        assertThat(0.1 + 0.2 + 0.3)
                .as("arrival-order summation really does differ, or this test proves nothing")
                .isNotEqualTo(0.3 + 0.2 + 0.1);

        PointsTally inOrder = new PointsTally(1);
        inOrder.add(1, 0.1);
        inOrder.add(2, 0.2);
        inOrder.add(3, 0.3);

        PointsTally reversed = new PointsTally(2);
        reversed.add(3, 0.3);
        reversed.add(2, 0.2);
        reversed.add(1, 0.1);

        assertThat(Double.doubleToLongBits(reversed.points()))
                .isEqualTo(Double.doubleToLongBits(inOrder.points()));
    }

    /** Adding after a total has been read must not return the stale total. */
    @Test
    void aWeekAddedAfterReadingIsCounted() {
        PointsTally tally = new PointsTally(1);
        tally.add(1, 10);
        assertThat(tally.points()).isEqualTo(10);
        tally.add(2, 5);
        assertThat(tally.points()).isEqualTo(15);
        assertThat(tally.games()).isEqualTo(2);
        assertThat(tally.perGame()).isEqualTo(7.5);
    }

    /** Level on points: the lower id ranks first, as a ranking's page boundary needs. */
    @Test
    void levelTotalsOrderByPlayerId() {
        PointsTally high = new PointsTally(9);
        high.add(1, 12);
        PointsTally levelLowId = new PointsTally(3);
        levelLowId.add(1, 12);
        PointsTally below = new PointsTally(1);
        below.add(1, 11);

        assertThat(java.util.stream.Stream.of(high, below, levelLowId)
                .sorted(PointsTally.BY_POINTS).map(PointsTally::playerId).toList())
                .containsExactly(3L, 9L, 1L);
    }

    @Test
    void noGamesIsZeroPerGameNotNaN() {
        assertThat(new PointsTally(1).perGame()).isZero();
    }
}
