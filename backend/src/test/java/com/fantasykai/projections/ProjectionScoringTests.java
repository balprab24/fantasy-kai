package com.fantasykai.projections;

import static com.fantasykai.projections.Games.receiver;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.scoring.Bonus;
import com.fantasykai.scoring.ResolvedRuleset;
import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.ScoringEngine;
import com.fantasykai.scoring.StatKey;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * One projected line, many leagues: scoring is downstream of the provider and never regenerates
 * a projection (the architecture's core invariant, and brief acceptance 3).
 */
class ProjectionScoringTests {

    private static Map<StatKey, Double> base(double rec, double passTd) {
        Map<StatKey, Double> rates = new EnumMap<>(StatKey.class);
        rates.put(StatKey.PASS_YD, 0.04);
        rates.put(StatKey.PASS_TD, passTd);
        rates.put(StatKey.PASS_INT, -2.0);
        rates.put(StatKey.RUSH_YD, 0.1);
        rates.put(StatKey.RUSH_TD, 6.0);
        rates.put(StatKey.REC, rec);
        rates.put(StatKey.REC_YD, 0.1);
        rates.put(StatKey.REC_TD, 6.0);
        rates.put(StatKey.FUM_LOST, -2.0);
        return rates;
    }

    private static ResolvedRuleset rules(Map<StatKey, Double> base, Map<String, Map<StatKey, Double>> overrides,
            List<Bonus> bonuses) {
        return ResolvedRuleset.compile(new Ruleset(1, base, overrides, bonuses));
    }

    @Test
    void oneProjectionScoresUnderEveryLeagueWithoutBeingRemade() {
        RawProjection projection = new WeightedAverageProjectionProvider().project(PlayerHistory.before(
                new SeasonWeek(2025, 4), List.of(receiver(2025, 1, 6, 71, 1, 9), receiver(2025, 2, 3, 38, 0, 5),
                        receiver(2025, 3, 8, 104, 1, 11)))).orElseThrow();
        double[] before = projection.stats();

        double standard = ScoringEngine.score(projection.line("TE"), rules(base(0, 4), Map.of(), List.of()));
        double half = ScoringEngine.score(projection.line("TE"), rules(base(0.5, 4), Map.of(), List.of()));
        double ppr = ScoringEngine.score(projection.line("TE"), rules(base(1.0, 4), Map.of(), List.of()));
        double tePremium = ScoringEngine.score(projection.line("TE"),
                rules(base(1.0, 4), Map.of("TE", Map.of(StatKey.REC, 1.5)), List.of()));
        double custom = ScoringEngine.score(projection.line("TE"),
                rules(base(0.5, 6), Map.of(), List.of(new Bonus(StatKey.REC_YD, 70, 3))));

        double rec = projection.get(StatKey.REC);
        assertThat(ppr - standard).isCloseTo(rec, within(1e-12));
        assertThat(half - standard).isCloseTo(0.5 * rec, within(1e-12));
        assertThat(tePremium - ppr).isCloseTo(0.5 * rec, within(1e-12));
        // The custom league's bonus is applied to the projected line, deterministically (the
        // approved V1 decision): projected receiving yards of 73.8... clear 70, so +3.
        assertThat(projection.get(StatKey.REC_YD)).isGreaterThan(70);
        assertThat(custom - half).isCloseTo(3, within(1e-12));

        assertThat(projection.stats()).containsExactly(before);
    }

    @Test
    void scoringAProjectionIsJustTheEngine() {
        RawProjection projection = new WeightedAverageProjectionProvider().project(PlayerHistory.before(
                new SeasonWeek(2025, 3), List.of(receiver(2025, 1, 5, 61, 1, 8), receiver(2025, 2, 4, 47, 0, 6))))
                .orElseThrow();
        ResolvedRuleset ppr = rules(base(1.0, 4), Map.of(), List.of());
        double viaProjection = ScoringEngine.score(projection.line("WR"), ppr);
        double direct = ScoringEngine.score(new com.fantasykai.scoring.StatLine("WR", projection.stats()), ppr);
        assertThat(Double.doubleToRawLongBits(viaProjection)).isEqualTo(Double.doubleToRawLongBits(direct));
    }

    @Test
    void aProjectionCannotBeChangedThroughWhatItHandsOut() {
        RawProjection projection = new WeightedAverageProjectionProvider().project(PlayerHistory.before(
                new SeasonWeek(2025, 2), List.of(receiver(2025, 1, 5, 61, 1, 8)))).orElseThrow();
        projection.stats()[StatKey.REC.index()] = 99;
        projection.line("WR").values()[StatKey.REC.index()] = 99;
        assertThat(projection.get(StatKey.REC)).isEqualTo(5);
    }
}
