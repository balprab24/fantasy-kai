package com.fantasykai.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.fantasykai.scoring.Ruleset;
import com.fantasykai.scoring.StatKey;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationTests {

    private static List<League> ppr() {
        Map<StatKey, Double> rates = new EnumMap<>(StatKey.class);
        rates.put(StatKey.REC, 1.0);
        rates.put(StatKey.REC_YD, 0.1);
        Ruleset ruleset = new Ruleset(1, rates, Map.of(), List.of());
        return List.of(new League(League.PPR, ruleset, new ExpectedPoints(ruleset)));
    }

    /** Four weeks, two receivers each, with known receptions and yards. */
    private static List<Case> cases() {
        Fixture f = new Fixture().schedule(2024, 1, 5);
        for (int week = 1; week <= 5; week++) {
            f.wr(1, Fixture.HOME, 2024, week, week, 10 * week, 8);
            f.wr(2, Fixture.AWAY, 2024, week, 2, 20, 4);
        }
        return Population.build(f.build(), 2024).cases();
    }

    private static double[][] lines(List<Case> cases, double recShift) {
        double[][] out = new double[cases.size()][];
        for (int i = 0; i < cases.size(); i++) {
            out[i] = cases.get(i).actualLine().clone();
            out[i][Quantity.of(StatKey.REC)] += recShift;
        }
        return out;
    }

    @Test
    void errorsAreMeasuredOnUnroundedPoints() {
        List<Case> cases = cases();
        Evaluation e = new Evaluation(cases, ppr());
        e.add("exact", lines(cases, 0), null);
        e.add("high", lines(cases, 0.25), null);
        assertThat(e.fantasy("exact", 0, c -> true).mae()).isZero();
        Evaluation.Summary high = e.fantasy("high", 0, c -> true);
        assertThat(high.mae()).isCloseTo(0.25, within(1e-12));
        assertThat(high.bias()).isCloseTo(0.25, within(1e-12));
        assertThat(e.spearman("exact", 0, c -> true)).isNaN(); // fewer than 3 per position-week
    }

    @Test
    void thePairedBootstrapIsReproducibleAndCentredOnTheDifference() {
        List<Case> cases = cases();
        Evaluation e = new Evaluation(cases, ppr());
        e.add("exact", lines(cases, 0), null);
        e.add("high", lines(cases, 0.5), null);
        Evaluation.Interval a = e.paired("exact", "high", 0, c -> true, 500, 7);
        Evaluation.Interval b = e.paired("exact", "high", 0, c -> true, 500, 7);
        assertThat(a).isEqualTo(b);
        // Every pair differs by exactly 0.5, so no resample of weeks can move the mean.
        assertThat(a.diff()).isCloseTo(-0.5, within(1e-12));
        assertThat(a.lo()).isCloseTo(-0.5, within(1e-12));
        assertThat(a.hi()).isCloseTo(-0.5, within(1e-12));
    }
}
