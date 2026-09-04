package com.fantasykai.scoring;

import static com.fantasykai.scoring.ScoringEngine.roundForDisplay;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The evaluator against real 2025 box scores.
 *
 * <p>Three genuine stat lines, hand-computed under all four presets. §6: if
 * Chase's line does not come out to the right number under full PPR, nothing
 * downstream is trustworthy. Pure -- no Spring, no container, no database.
 *
 * <p>Every assertion goes through {@link ScoringEngine#roundForDisplay} because
 * that is what the API will do. Comparing raw doubles here would be asserting
 * against binary floating point rather than against the box score: 0.04 has no
 * exact double representation, so 394 passing yards is 15.760000000000002
 * points before rounding and no interviewer cares.
 */
class ScoringEngineTests {

    private static final ResolvedRuleset STANDARD = ResolvedRuleset.compile(Presets.standard());
    private static final ResolvedRuleset HALF_PPR = ResolvedRuleset.compile(Presets.halfPpr());
    private static final ResolvedRuleset FULL_PPR = ResolvedRuleset.compile(Presets.fullPpr());
    private static final ResolvedRuleset TE_PREMIUM = ResolvedRuleset.compile(Presets.tePremium());

    /** Josh Allen, 2025 week 1: 394 pass yd, 2 pass TD, 30 rush yd, 2 rush TD. */
    private static final StatLine ALLEN = StatLine.of("QB", Map.of(
            StatKey.PASS_YD, 394, StatKey.PASS_TD, 2,
            StatKey.RUSH_YD, 30, StatKey.RUSH_TD, 2));

    /** Juwan Johnson, 2025 week 1: 8 receptions, 76 yards. A TE, so the override bites. */
    private static final StatLine JOHNSON = StatLine.of("TE", Map.of(
            StatKey.REC, 8, StatKey.REC_YD, 76));

    /** Derrick Henry, 2025 week 1: 169 rush yd, 2 rush TD, 1-13 receiving, one fumble lost. */
    private static final StatLine HENRY = StatLine.of("RB", Map.of(
            StatKey.RUSH_YD, 169, StatKey.RUSH_TD, 2,
            StatKey.REC, 1, StatKey.REC_YD, 13,
            StatKey.FUM_LOST, 1));

    @Test
    void aLineWithNoReceptionsScoresTheSameUnderEveryPreset() {
        // 394x0.04 + 2x4 + 30x0.1 + 2x6 = 15.76 + 8 + 3 + 12
        for (ResolvedRuleset preset : List.of(STANDARD, HALF_PPR, FULL_PPR, TE_PREMIUM)) {
            assertThat(roundForDisplay(ScoringEngine.score(ALLEN, preset))).isEqualTo(38.76);
        }
    }

    @Test
    void receptionRateIsTheOnlyDifferenceBetweenTheFirstThreePresets() {
        // 76x0.1 = 7.6, then 8 receptions at 0, 0.5 and 1.0.
        assertThat(roundForDisplay(ScoringEngine.score(JOHNSON, STANDARD))).isEqualTo(7.6);
        assertThat(roundForDisplay(ScoringEngine.score(JOHNSON, HALF_PPR))).isEqualTo(11.6);
        assertThat(roundForDisplay(ScoringEngine.score(JOHNSON, FULL_PPR))).isEqualTo(15.6);
    }

    @Test
    void tePremiumRaisesReceptionsForTightEndsOnly() {
        // Johnson is a TE: 8 receptions at 1.5 instead of 1.0, so +4 over full PPR.
        assertThat(roundForDisplay(ScoringEngine.score(JOHNSON, TE_PREMIUM))).isEqualTo(19.6);

        // Henry is a RB. The override must not reach him.
        assertThat(roundForDisplay(ScoringEngine.score(HENRY, TE_PREMIUM)))
                .isEqualTo(roundForDisplay(ScoringEngine.score(HENRY, FULL_PPR)))
                .isEqualTo(29.2);
    }

    @Test
    void anOverrideReplacesOneRateRatherThanTheWholeTable() {
        // The obvious way to get this wrong is to let {"TE": {"rec": 1.5}} become
        // a TE's entire rate table, silently zeroing touchdowns and yards.
        StatLine scoringTe = StatLine.of("TE", Map.of(
                StatKey.REC, 3, StatKey.REC_YD, 40, StatKey.REC_TD, 1));

        // 3x1.5 + 40x0.1 + 6 = 4.5 + 4 + 6
        assertThat(roundForDisplay(ScoringEngine.score(scoringTe, TE_PREMIUM))).isEqualTo(14.5);
    }

    @Test
    void aLostFumbleIsSubtracted() {
        // 169x0.1 + 2x6 + 13x0.1 - 2 = 16.9 + 12 + 1.3 - 2
        assertThat(roundForDisplay(ScoringEngine.score(HENRY, STANDARD))).isEqualTo(28.2);
        assertThat(roundForDisplay(ScoringEngine.score(HENRY, HALF_PPR))).isEqualTo(28.7);
        assertThat(roundForDisplay(ScoringEngine.score(HENRY, FULL_PPR))).isEqualTo(29.2);
    }

    @Test
    void aBonusFiresAtItsThresholdAndNotBelow() {
        ResolvedRuleset withBonuses = ResolvedRuleset.compile(new Ruleset(1,
                Presets.fullPpr().base(), Map.of(),
                List.of(new Bonus(StatKey.RUSH_YD, 100, 3), new Bonus(StatKey.REC_YD, 100, 3))));

        // Bijan Robinson, 2025 week 1: 24 rush yd, 6-100-1 receiving. Exactly 100
        // receiving yards, which is the boundary the bonus is written on.
        StatLine atThreshold = StatLine.of("RB", Map.of(
                StatKey.RUSH_YD, 24, StatKey.REC, 6, StatKey.REC_YD, 100, StatKey.REC_TD, 1));
        assertThat(roundForDisplay(ScoringEngine.score(atThreshold, FULL_PPR))).isEqualTo(24.4);
        assertThat(roundForDisplay(ScoringEngine.score(atThreshold, withBonuses))).isEqualTo(27.4);

        StatLine oneShort = StatLine.of("RB", Map.of(
                StatKey.RUSH_YD, 24, StatKey.REC, 6, StatKey.REC_YD, 99, StatKey.REC_TD, 1));
        assertThat(roundForDisplay(ScoringEngine.score(oneShort, withBonuses))).isEqualTo(24.3);

        // Henry's 169 rushing yards clear the other bonus; both are all-or-nothing,
        // so a 169-yard game earns the same 3 points a 100-yard game does.
        assertThat(roundForDisplay(ScoringEngine.score(HENRY, withBonuses))).isEqualTo(32.2);
    }

    @Test
    void anUnratedStatContributesNothing() {
        // ret_td is in the presets; a ruleset that omits a key must treat it as
        // zero rather than throwing or defaulting to something surprising.
        ResolvedRuleset receivingOnly = ResolvedRuleset.compile(
                new Ruleset(1, Map.of(StatKey.REC, 1.0), Map.of(), List.of()));

        assertThat(roundForDisplay(ScoringEngine.score(HENRY, receivingOnly))).isEqualTo(1.0);
    }

    @Test
    void anUnknownPositionFallsBackToTheBaseRates() {
        StatLine punter = StatLine.of("P", Map.of(StatKey.REC, 2, StatKey.REC_YD, 20));

        assertThat(roundForDisplay(ScoringEngine.score(punter, TE_PREMIUM))).isEqualTo(4.0);
    }
}
