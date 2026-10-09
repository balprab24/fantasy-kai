package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.StatKey;
import com.fantasykai.scoring.StatLine;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The expected-points boundary around ScoringEngine (Phase 6 brief, acceptance 2 and 3). */
class ExpectedPointsTests {

    private static Map<StatKey, Double> base(double rec) {
        Map<StatKey, Double> rates = new EnumMap<>(StatKey.class);
        rates.put(StatKey.PASS_YD, 0.04);
        rates.put(StatKey.PASS_TD, 4.0);
        rates.put(StatKey.PASS_INT, -2.0);
        rates.put(StatKey.RUSH_YD, 0.1);
        rates.put(StatKey.RUSH_TD, 6.0);
        rates.put(StatKey.REC, rec);
        rates.put(StatKey.REC_YD, 0.1);
        rates.put(StatKey.REC_TD, 6.0);
        rates.put(StatKey.FUM_LOST, -2.0);
        return rates;
    }

    /** A projection: fractional, as a mean line always is. */
    private static StatLine projected() {
        return StatLine.of("RB", Map.of(StatKey.RUSH_YD, 71.37, StatKey.RUSH_TD, 0.4413,
                StatKey.REC, 3.217, StatKey.REC_YD, 24.9, StatKey.REC_TD, 0.118,
                StatKey.FUM_LOST, 0.071));
    }

    @Test
    void withNoBonusesItIsTheEngineBitForBit() {
        Ruleset ppr = new Ruleset(1, base(1.0), Map.of(), List.of());
        double engine = ScoringEngine.score(projected(), ResolvedRuleset.compile(ppr));
        double expected = new ExpectedPoints(ppr).expected(projected(), null);
        assertThat(Double.doubleToRawLongBits(expected)).isEqualTo(Double.doubleToRawLongBits(engine));
    }

    @Test
    void pprMinusZeroPprIsExactlyTheProjectedReceptions() {
        double ppr = new ExpectedPoints(new Ruleset(1, base(1.0), Map.of(), List.of()))
                .expected(projected(), null);
        double zero = new ExpectedPoints(new Ruleset(1, base(0.0), Map.of(), List.of()))
                .expected(projected(), null);
        assertThat(ppr - zero).isCloseTo(projected().get(StatKey.REC) * 1.0, within(1e-12));
    }

    @Test
    void aBonusAddsItsPointsTimesTheProbabilityNotAStep() {
        Ruleset bonus = new Ruleset(1, base(0.5), Map.of(),
                List.of(new Bonus(StatKey.RUSH_YD, 100, 3)));
        Ruleset none = new Ruleset(1, base(0.5), Map.of(), List.of());
        ExpectedPoints points = new ExpectedPoints(bonus);
        double linear = ScoringEngine.score(projected(), ResolvedRuleset.compile(none));

        // 71.37 projected yards: the step gives nothing, the expectation gives 3 x 0.18.
        ExpectedPoints.Odds odds = (position, stat, gte, projectedValue) -> 0.18;
        assertThat(points.expected(projected(), odds)).isCloseTo(linear + 3 * 0.18, within(1e-12));
        assertThat(points.stepped(projected())).isEqualTo(linear);
        assertThat(points.ignoringBonuses(projected())).isEqualTo(linear);
    }

    @Test
    void anActualGameIsScoredByTheUnchangedEngine() {
        Ruleset bonus = new Ruleset(1, base(0.5), Map.of(),
                List.of(new Bonus(StatKey.RUSH_YD, 100, 3)));
        StatLine game = StatLine.of("RB", Map.of(StatKey.RUSH_YD, 104, StatKey.REC, 2));
        assertThat(new ExpectedPoints(bonus).actual(game))
                .isEqualTo(ScoringEngine.score(game, ResolvedRuleset.compile(bonus)));
    }
}
