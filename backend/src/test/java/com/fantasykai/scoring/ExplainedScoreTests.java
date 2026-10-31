package com.fantasykai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExplainedScoreTests {

    private static StatLine line(String position, Map<StatKey, Double> values) {
        double[] stats = new double[StatKey.COUNT];
        values.forEach((k, v) -> stats[k.index()] = v);
        return new StatLine(position, stats);
    }

    /** §6's hand-computed line: Josh Allen, 2025 week 1, under the §6 ruleset -- 38.76. */
    @Test
    void theTotalIsTheEnginesAndThePartsAddUpToIt() {
        ResolvedRuleset rules = ResolvedRuleset.compile(new Ruleset(1, Map.of(StatKey.PASS_YD, 0.04,
                StatKey.PASS_TD, 4.0, StatKey.RUSH_YD, 0.1, StatKey.RUSH_TD, 6.0), Map.of(), List.of()));
        StatLine allen = line("QB", Map.of(StatKey.PASS_YD, 394.0, StatKey.PASS_TD, 2.0,
                StatKey.RUSH_YD, 30.0, StatKey.RUSH_TD, 2.0));

        ExplainedScore explained = ExplainedScore.of(allen, rules);

        assertThat(explained.total()).isEqualTo(ScoringEngine.score(allen, rules)).isCloseTo(38.76, within(1e-9));
        assertThat(explained.parts().stream().mapToDouble(ExplainedScore.Part::points).sum()
                + explained.bonusPoints()).isCloseTo(explained.total(), within(1e-9));
        assertThat(explained.parts()).extracting(ExplainedScore.Part::stat)
                .containsExactly("pass_yd", "pass_td", "rush_yd", "rush_td");
        assertThat(explained.bonusPoints()).isCloseTo(0, within(1e-12));
    }

    @Test
    void aBonusIsTheRemainderAndAnOverrideRateIsThePositions() {
        ResolvedRuleset rules = ResolvedRuleset.compile(new Ruleset(1, Map.of(StatKey.REC, 1.0, StatKey.RUSH_YD, 0.1),
                Map.of("TE", Map.of(StatKey.REC, 1.5)), List.of(new Bonus(StatKey.RUSH_YD, 100, 3))));

        ExplainedScore back = ExplainedScore.of(line("RB", Map.of(StatKey.RUSH_YD, 120.0)), rules);
        ExplainedScore end = ExplainedScore.of(line("TE", Map.of(StatKey.REC, 6.0)), rules);

        assertThat(back.bonusPoints()).isCloseTo(3.0, within(1e-12));
        assertThat(back.total()).isCloseTo(15.0, within(1e-12));
        assertThat(end.parts()).filteredOn(p -> p.stat().equals("rec")).singleElement()
                .satisfies(p -> assertThat(p.rate()).isEqualTo(1.5));
    }

    @Test
    void aWeekNotPlayedScalesEveryPartToZero() {
        ResolvedRuleset rules = ResolvedRuleset.compile(new Ruleset(1, Map.of(StatKey.REC, 1.0), Map.of(), List.of()));
        ExplainedScore zero = ExplainedScore.of(line("WR", Map.of(StatKey.REC, 5.0)), rules).times(0);

        assertThat(zero.total()).isZero();
        assertThat(zero.parts()).allSatisfy(p -> assertThat(p.points()).isZero());
    }
}
