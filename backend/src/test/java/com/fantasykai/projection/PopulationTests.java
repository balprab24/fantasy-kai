package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Who is projected is decided before kickoff, and every exclusion is counted. */
class PopulationTests {

    private static Population.Season season() {
        Fixture f = new Fixture().schedule(2024, 1, 3);
        // 1 plays every week. 2 plays weeks 1 and 2, then sits week 3. 3 debuts in week 2.
        // 4 played week 1 only, so he missed his team's last game before week 3.
        f.wr(1, Fixture.HOME, 2024, 1, 5, 50, 8).wr(1, Fixture.HOME, 2024, 2, 5, 50, 8)
                .wr(1, Fixture.HOME, 2024, 3, 5, 50, 8);
        f.wr(2, Fixture.HOME, 2024, 1, 4, 40, 6).wr(2, Fixture.HOME, 2024, 2, 4, 40, 6);
        f.wr(3, Fixture.AWAY, 2024, 2, 3, 30, 5).wr(3, Fixture.AWAY, 2024, 3, 3, 30, 5);
        f.wr(4, Fixture.AWAY, 2024, 1, 1, 10, 2);
        return Population.build(f.build(), 2024);
    }

    private static Case week3(Population.Season s, long player) {
        return s.cases().stream().filter(c -> c.week == 3 && c.playerId == player)
                .findFirst().orElse(null);
    }

    @Test
    void aPlayerWhoSitsIsInTheAvailabilitySetAsAZeroAndNotInP() {
        Case sat = week3(season(), 2);
        assertThat(sat.actual).isNull();
        assertThat(sat.inP0).isTrue();
        assertThat(sat.inP).isFalse();
        assertThat(sat.actualLine()).containsOnly(0);
    }

    @Test
    void aPlayerWhoMissedHisTeamsLastGameIsNotProjectedAsActive() {
        assertThat(week3(season(), 4)).isNull();
    }

    @Test
    void aDebutHasNoHistoryAndIsCountedNotProjected() {
        Population.Season s = season();
        assertThat(s.cases()).noneMatch(c -> c.playerId == 3 && c.week == 2);
        assertThat(s.counts().debuts()).isEqualTo(4); // 1, 2 and 4 in week 1; 3 in week 2
        assertThat(week3(s, 3).inP).isTrue();
    }

    @Test
    void membershipNeverReadsTheWeekItRanks() {
        List<Case> week3 = season().cases().stream().filter(c -> c.week == 3).toList();
        assertThat(week3).allMatch(c -> c.history.stream().allMatch(g -> g.key() < Dated.key(2024, 3)));
    }
}
