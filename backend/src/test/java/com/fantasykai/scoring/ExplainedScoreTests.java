package com.fantasykai.scoring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ExplainedScoreTests {

    private static StatLine line(String position, Map<StatKey, Double> values) {
        double[] stats = new double[StatKey.COUNT];
        values.forEach((k, v) -> stats[k.index()] = v);
        return new StatLine(position, stats);
    }

    private static ResolvedRuleset rules(Map<StatKey, Double> base, Map<String, Map<StatKey, Double>> overrides,
            List<Bonus> bonuses) {
        return ResolvedRuleset.compile(new Ruleset(1, base, overrides, bonuses));
    }

    /** The explanation's own sum, in the engine's order: the parts, then the bonuses that were reached. */
    private static double sumOfParts(ExplainedScore explained) {
        double sum = 0;
        for (ExplainedScore.Part part : explained.parts()) {
            sum += part.points();
        }
        for (ExplainedScore.BonusLine bonus : explained.bonuses()) {
            if (bonus.triggered()) {
                sum += bonus.points();
            }
        }
        return sum;
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
        assertThat(sumOfParts(explained)).isCloseTo(explained.total(), within(1e-9));
        assertThat(explained.parts()).extracting(ExplainedScore.Part::stat)
                .containsExactly("pass_yd", "pass_td", "rush_yd", "rush_td");
        assertThat(explained.bonuses()).as("a ruleset with no bonus lists none").isEmpty();
        assertThat(explained.bonusPoints()).isZero();
    }

    @Test
    void aBonusIsALineOfItsOwnAndAnOverrideRateIsThePositions() {
        ResolvedRuleset rules = rules(Map.of(StatKey.REC, 1.0, StatKey.RUSH_YD, 0.1),
                Map.of("TE", Map.of(StatKey.REC, 1.5)), List.of(new Bonus(StatKey.RUSH_YD, 100, 3)));

        ExplainedScore back = ExplainedScore.of(line("RB", Map.of(StatKey.RUSH_YD, 120.0)), rules);
        ExplainedScore end = ExplainedScore.of(line("TE", Map.of(StatKey.REC, 6.0)), rules);

        assertThat(back.bonuses()).singleElement().isEqualTo(
                new ExplainedScore.BonusLine("rush_yd", 100, 120.0, 3.0, true));
        assertThat(back.bonusPoints()).isEqualTo(3.0);
        assertThat(back.total()).isCloseTo(15.0, within(1e-12));
        assertThat(end.parts()).filteredOn(p -> p.stat().equals("rec")).singleElement()
                .satisfies(p -> assertThat(p.rate()).isEqualTo(1.5));
        assertThat(end.bonuses()).as("a tight end with no rushing line has no rushing bonus to explain")
                .isEmpty();
    }

    /**
     * A bonus is a line when the line has some of its stat (so a near miss shows how far short it
     * fell) or when it was paid -- the rule {@code parts} already follows. One on a stat the line
     * has none of explains nothing and is left out; the total is unaffected, since it was not paid.
     */
    @Test
    void aBonusOnAStatTheLineHasNoneOfIsNotListedUnlessItWasPaid() {
        ResolvedRuleset rules = rules(Map.of(StatKey.REC_YD, 0.1), Map.of(), List.of(
                new Bonus(StatKey.PASS_YD, 300, 3), new Bonus(StatKey.REC_YD, 100, 2.5),
                new Bonus(StatKey.REC, 0, 1)));

        ExplainedScore explained = ExplainedScore.of(line("RB", Map.of(StatKey.REC_YD, 40.0)), rules);

        assertThat(explained.bonuses()).containsExactly(
                new ExplainedScore.BonusLine("rec_yd", 100, 40.0, 2.5, false),
                new ExplainedScore.BonusLine("rec", 0, 0.0, 1.0, true));
        assertThat(explained.total()).isEqualTo(5.0);
        assertThat(sumOfParts(explained)).isEqualTo(explained.total());
    }

    /** A bonus pays at its threshold and not before it; the line says so, and so does the total. */
    @Test
    void aLineAtExactlyTheThresholdTriggersAndOneShortOfItDoesNot() {
        ResolvedRuleset rules = rules(Map.of(StatKey.RUSH_YD, 0.1), Map.of(),
                List.of(new Bonus(StatKey.RUSH_YD, 100, 3)));

        ExplainedScore at = ExplainedScore.of(line("RB", Map.of(StatKey.RUSH_YD, 100.0)), rules);
        ExplainedScore justShort = ExplainedScore.of(line("RB", Map.of(StatKey.RUSH_YD, 99.99)), rules);
        ExplainedScore over = ExplainedScore.of(line("RB", Map.of(StatKey.RUSH_YD, 100.01)), rules);

        assertThat(at.bonuses().getFirst().triggered()).as("at the threshold").isTrue();
        assertThat(justShort.bonuses().getFirst().triggered()).as("just short").isFalse();
        assertThat(over.bonuses().getFirst().triggered()).as("past it").isTrue();
        assertThat(at.total()).isCloseTo(13.0, within(1e-12));
        assertThat(justShort.total()).isCloseTo(9.999, within(1e-12));
        assertThat(sumOfParts(at)).isEqualTo(at.total());
        assertThat(sumOfParts(justShort)).isEqualTo(justShort.total());
    }

    /**
     * Every bonus of the ruleset is a line, in the ruleset's order, each against its own stat; a
     * negative bonus is a line like any other.
     */
    @Test
    void everyBonusIsListedInTheRulesetsOrderWhetherOrNotItWasReached() {
        ResolvedRuleset rules = rules(Map.of(StatKey.PASS_YD, 0.04, StatKey.REC_YD, 0.1), Map.of(), List.of(
                new Bonus(StatKey.PASS_YD, 300, 3), new Bonus(StatKey.REC_YD, 100, 2.5),
                new Bonus(StatKey.PASS_INT, 3, -2.5)));

        ExplainedScore explained = ExplainedScore.of(line("QB", Map.of(StatKey.PASS_YD, 312.0,
                StatKey.REC_YD, 8.0, StatKey.PASS_INT, 3.0)), rules);

        assertThat(explained.bonuses()).containsExactly(
                new ExplainedScore.BonusLine("pass_yd", 300, 312.0, 3.0, true),
                new ExplainedScore.BonusLine("rec_yd", 100, 8.0, 2.5, false),
                new ExplainedScore.BonusLine("pass_int", 3, 3.0, -2.5, true));
        assertThat(explained.bonusPoints()).as("only the ones reached, a penalty included").isEqualTo(0.5);
        assertThat(sumOfParts(explained)).isEqualTo(explained.total());
    }

    /** A bonus worth zero is not in the ruleset once compiled, so there is no line for it. */
    @Test
    void aBonusThatPaysNothingIsNotListed() {
        ResolvedRuleset rules = rules(Map.of(StatKey.REC, 1.0), Map.of(),
                List.of(new Bonus(StatKey.REC, 5, 0), new Bonus(StatKey.REC, 10, 2)));

        ExplainedScore explained = ExplainedScore.of(line("WR", Map.of(StatKey.REC, 6.0)), rules);

        assertThat(explained.bonuses()).singleElement()
                .isEqualTo(new ExplainedScore.BonusLine("rec", 10, 6.0, 2.0, false));
    }

    /**
     * The parts and the bonuses that were reached add to the engine's total with no remainder --
     * the engine's own sum, so its exact bits, over 300 fractional lines under a ruleset with a
     * position override and four bonuses (a penalty among them). Every bonus is reached by some
     * lines and missed by others, so a comparison that is wrong in either direction cannot hide.
     */
    @Test
    void thePartsAndTheReachedBonusesAreTheEnginesTotalBitForBit() {
        ResolvedRuleset rules = rules(Map.of(StatKey.PASS_YD, 0.04, StatKey.PASS_TD, 4.0, StatKey.PASS_INT, -2.0,
                        StatKey.RUSH_YD, 0.1, StatKey.RUSH_TD, 6.0, StatKey.REC, 0.5, StatKey.REC_YD, 0.1,
                        StatKey.REC_TD, 6.0, StatKey.FUM_LOST, -2.0),
                Map.of("TE", Map.of(StatKey.REC, 1.5)),
                List.of(new Bonus(StatKey.PASS_YD, 300, 3), new Bonus(StatKey.RUSH_YD, 100, 3),
                        new Bonus(StatKey.REC_YD, 100, 2.5), new Bonus(StatKey.PASS_INT, 3, -2.5)));
        Random random = new Random(20261123L);
        String[] positions = {"QB", "RB", "WR", "TE"};
        Map<String, Integer> reached = new TreeMap<>();
        for (int i = 0; i < 300; i++) {
            Map<StatKey, Double> values = new EnumMap<>(StatKey.class);
            for (StatKey key : StatKey.values()) {
                if (random.nextBoolean()) {
                    // One decimal, as a projected line carries, on a scale a game could have (so
                    // some lines fall short of a threshold with something to show), and a quarter
                    // of them dead on a threshold.
                    double value = Math.round(random.nextDouble() * scaleFor(key) * 10) / 10.0;
                    values.put(key, random.nextInt(4) == 0 ? thresholdFor(key, value) : value);
                }
            }
            ExplainedScore explained = ExplainedScore.of(line(positions[i % 4], values), rules);

            assertThat(Double.doubleToRawLongBits(sumOfParts(explained)))
                    .as("line %d: %s", i, values).isEqualTo(Double.doubleToRawLongBits(explained.total()));
            for (ExplainedScore.BonusLine bonus : explained.bonuses()) {
                reached.merge(bonus.stat() + (bonus.triggered() ? ":reached" : ":missed"), 1, Integer::sum);
            }
        }
        for (String stat : List.of("pass_yd", "rush_yd", "rec_yd", "pass_int")) {
            assertThat(reached).as("%s was reached by some lines", stat).containsKey(stat + ":reached");
            assertThat(reached).as("%s was missed by some lines", stat).containsKey(stat + ":missed");
        }
    }

    private static double scaleFor(StatKey key) {
        return switch (key) {
            case PASS_YD -> 400;
            case RUSH_YD, REC_YD -> 150;
            case PASS_INT -> 5;
            default -> 10;
        };
    }

    private static double thresholdFor(StatKey key, double fallback) {
        return switch (key) {
            case PASS_YD -> 300;
            case RUSH_YD, REC_YD -> 100;
            case PASS_INT -> 3;
            default -> fallback;
        };
    }
}
