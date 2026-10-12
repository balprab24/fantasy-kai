package com.fantasykai.projections;

import static com.fantasykai.projections.AvailabilityStatus.AVAILABLE;
import static com.fantasykai.projections.AvailabilityStatus.DOUBTFUL;
import static com.fantasykai.projections.AvailabilityStatus.NOT_ON_ROSTER;
import static com.fantasykai.projections.AvailabilityStatus.OUT;
import static com.fantasykai.projections.AvailabilityStatus.QUESTIONABLE;
import static com.fantasykai.projections.AvailabilityStatus.RESERVE;
import static com.fantasykai.projections.AvailabilityStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Known availability from a roster status and an injury designation -- the approved V1 rule,
 * every source value it names, and its precedence. Values are nflverse's own.
 */
class AvailabilityRulesTests {

    @Test
    void anOfficialDesignationDecidesOnAnActiveRoster() {
        assertThat(AvailabilityRules.classify("ACT", "Out")).isEqualTo(OUT);
        assertThat(AvailabilityRules.classify("ACT", "Doubtful")).isEqualTo(DOUBTFUL);
        assertThat(AvailabilityRules.classify("ACT", "Questionable")).isEqualTo(QUESTIONABLE);
        assertThat(AvailabilityRules.classify("ACT", null)).isEqualTo(AVAILABLE);
    }

    @Test
    void aReserveOrExemptListIsUnavailableWithOrWithoutAnInjuryRow() {
        assertThat(AvailabilityRules.classify("RES", null)).isEqualTo(RESERVE);
        assertThat(AvailabilityRules.classify("EXE", null)).isEqualTo(RESERVE);
    }

    /** Trey Lance, 2026 week 5: on injured reserve and listed Doubtful. The roster wins. */
    @Test
    void aReserveListOutranksAnyDesignation() {
        assertThat(AvailabilityRules.classify("RES", "Doubtful")).isEqualTo(RESERVE);
        assertThat(AvailabilityRules.classify("RES", "Questionable")).isEqualTo(RESERVE);
        assertThat(AvailabilityRules.classify("RES", "Out")).isEqualTo(RESERVE);
    }

    @Test
    void practiceSquadReleasedAndRetiredAreNotOnTheRoster() {
        assertThat(AvailabilityRules.classify("DEV", null)).isEqualTo(NOT_ON_ROSTER);
        assertThat(AvailabilityRules.classify("CUT", null)).isEqualTo(NOT_ON_ROSTER);
        assertThat(AvailabilityRules.classify("RET", null)).isEqualTo(NOT_ON_ROSTER);
        assertThat(AvailabilityRules.classify("DEV", "Questionable")).isEqualTo(NOT_ON_ROSTER);
    }

    /**
     * INA is the game-day inactive list, written ~90 minutes before kickoff: a morning pull never
     * sees it, so it must never make anyone unavailable -- and reading it in a backtest would be
     * reading the future.
     */
    @Test
    void theGameDayInactiveListIsNeverAReasonToDropAPlayer() {
        assertThat(AvailabilityRules.classify("INA", null)).isEqualTo(AVAILABLE);
        assertThat(AvailabilityRules.classify("INA", null).ranked()).isTrue();
        assertThat(AvailabilityRules.classify("INA", "Questionable")).isEqualTo(QUESTIONABLE);
    }

    /** A missing feed is not news about a player: nothing known is ranked. */
    @Test
    void nothingKnownIsUnknownAndStillRanked() {
        assertThat(AvailabilityRules.classify(null, null)).isEqualTo(UNKNOWN);
        assertThat(UNKNOWN.ranked()).isTrue();
    }

    @Test
    void aDesignationWithNoRosterRowStillCounts() {
        assertThat(AvailabilityRules.classify(null, "Out")).isEqualTo(OUT);
        assertThat(AvailabilityRules.classify(null, "Questionable")).isEqualTo(QUESTIONABLE);
    }

    /** A value with no meaning yet -- 'Note' (2024), a transaction code -- is no information. */
    @Test
    void anUnrecognisedValueIsNoInformationNeverUnavailability() {
        assertThat(AvailabilityRules.classify("ACT", "Note")).isEqualTo(AVAILABLE);
        assertThat(AvailabilityRules.classify("TRD", null)).isEqualTo(UNKNOWN);
        assertThat(AvailabilityRules.classify("TRC", "Questionable")).isEqualTo(QUESTIONABLE);
        assertThat(AvailabilityRules.classify("ACT", "out")).as("values are matched exactly, as shipped")
                .isEqualTo(AVAILABLE);
    }

    /** The approved V1 board rule: Out, reserve and off-roster are not ranked; the rest are. */
    @Test
    void onlyOutReserveAndOffRosterLeaveTheBoard() {
        assertThat(OUT.ranked()).isFalse();
        assertThat(RESERVE.ranked()).isFalse();
        assertThat(NOT_ON_ROSTER.ranked()).isFalse();
        assertThat(DOUBTFUL.ranked()).isTrue();
        assertThat(QUESTIONABLE.ranked()).isTrue();
        assertThat(AVAILABLE.ranked()).isTrue();
    }

    /** Projected means rostered, available or not: Out and reserve players keep a raw line. */
    @Test
    void reserveAndActivePlayersAreProjectedOffRosterOnesAreNot() {
        assertThat(AvailabilityRules.PROJECTED_ROSTER_STATUSES).containsExactlyInAnyOrder("ACT", "INA", "RES", "EXE")
                .doesNotContain("DEV", "CUT", "RET");
    }

    /** Two reports for one player-week (2024, Cade Stover: Questionable then Out) keep the stricter. */
    @Test
    void theMoreRestrictiveOfTwoReportsWins() {
        assertThat(AvailabilityRules.restrictiveness("Out")).isGreaterThan(AvailabilityRules.restrictiveness("Doubtful"));
        assertThat(AvailabilityRules.restrictiveness("Doubtful")).isGreaterThan(AvailabilityRules.restrictiveness("Questionable"));
        assertThat(AvailabilityRules.restrictiveness("Questionable")).isGreaterThan(AvailabilityRules.restrictiveness(null));
        assertThat(AvailabilityRules.restrictiveness("Note")).isZero();
    }
}
